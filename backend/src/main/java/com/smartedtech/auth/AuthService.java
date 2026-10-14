package com.smartedtech.auth;

import com.smartedtech.common.BadRequestException;
import com.smartedtech.common.ConflictException;
import com.smartedtech.common.ResourceNotFoundException;
import com.smartedtech.common.UnauthorizedException;
import com.smartedtech.security.JwtService;
import com.smartedtech.user.Role;
import com.smartedtech.user.User;
import com.smartedtech.user.UserDto;
import com.smartedtech.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.jwt.refresh-token-expiration-ms:604800000}")
    private long refreshTokenExpirationMs;

    @Value("${app.jwt.access-token-expiration-ms:900000}")
    private long accessTokenExpirationMs;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public record AuthResult(String accessToken, String rawRefreshToken, long expiresInSeconds, UserDto user) {}

    @Transactional
    public AuthResult register(RegisterRequest request) {
        if (request.role() == Role.ADMIN) {
            throw new BadRequestException("Public registration allows only STUDENT or TEACHER roles");
        }

        if (userRepository.existsByEmail(request.email().trim().toLowerCase())) {
            throw new ConflictException("User with email '" + request.email() + "' already exists");
        }

        User user = new User(
                request.email().trim().toLowerCase(),
                passwordEncoder.encode(request.password()),
                request.fullName().trim(),
                request.role()
        );
        user = userRepository.save(user);

        return issueNewSession(user);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        if (!user.isEnabled()) {
            throw new UnauthorizedException("Account is disabled");
        }

        return issueNewSession(user);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("Refresh token is required");
        }

        String tokenHash = hashToken(rawRefreshToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        // Reuse detection: If an already-revoked token is used, revoke the entire family
        if (token.isRevoked()) {
            refreshTokenRepository.revokeAllByFamilyId(token.getFamilyId());
            throw new UnauthorizedException("Refresh token reuse detected. All sessions in this token family have been revoked.");
        }

        if (token.getExpiresAt().isBefore(Instant.now())) {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            throw new UnauthorizedException("Refresh token has expired");
        }

        if (!token.getUser().isEnabled()) {
            throw new UnauthorizedException("Account is disabled");
        }

        // Rotate current token: mark as revoked
        token.setRevoked(true);
        refreshTokenRepository.save(token);

        // Issue new token in same family
        String newRawToken = generateSecureToken();
        String newTokenHash = hashToken(newRawToken);
        RefreshToken newToken = new RefreshToken(
                token.getUser(),
                newTokenHash,
                token.getFamilyId(),
                Instant.now().plusMillis(refreshTokenExpirationMs)
        );
        refreshTokenRepository.save(newToken);

        String accessToken = jwtService.generateAccessToken(token.getUser());
        long expiresInSeconds = accessTokenExpirationMs / 1000;

        return new AuthResult(accessToken, newRawToken, expiresInSeconds, UserDto.from(token.getUser()));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = hashToken(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            });
        }
    }

    @Transactional(readOnly = true)
    public UserDto getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return UserDto.from(user);
    }

    private AuthResult issueNewSession(User user) {
        String familyId = UUID.randomUUID().toString();
        String rawRefreshToken = generateSecureToken();
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken refreshToken = new RefreshToken(
                user,
                tokenHash,
                familyId,
                Instant.now().plusMillis(refreshTokenExpirationMs)
        );
        refreshTokenRepository.save(refreshToken);

        String accessToken = jwtService.generateAccessToken(user);
        long expiresInSeconds = accessTokenExpirationMs / 1000;

        return new AuthResult(accessToken, rawRefreshToken, expiresInSeconds, UserDto.from(user));
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public static String hashToken(String rawToken) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
