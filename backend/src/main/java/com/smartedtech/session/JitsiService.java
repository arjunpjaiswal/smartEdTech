package com.smartedtech.session;

import com.smartedtech.common.IntegrationNotConfiguredException;
import com.smartedtech.user.User;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class JitsiService {

    @Value("${app.integrations.jitsi.app-id:}")
    private String appId;

    @Value("${app.integrations.jitsi.kid:}")
    private String kid;

    @Value("${app.integrations.jitsi.private-key-path:}")
    private String privateKeyPath;

    public boolean isConfigured() {
        if (appId == null || appId.isBlank()) return false;
        if (kid == null || kid.isBlank()) return false;
        if (privateKeyPath == null || privateKeyPath.isBlank()) return false;
        return Files.exists(Paths.get(privateKeyPath));
    }

    public String generateToken(User user, String roomName, boolean isModerator) {
        List<String> missing = new ArrayList<>();
        if (appId == null || appId.isBlank()) missing.add("JITSI_APP_ID");
        if (kid == null || kid.isBlank()) missing.add("JITSI_KID");
        if (privateKeyPath == null || privateKeyPath.isBlank()) {
            missing.add("JITSI_PRIVATE_KEY_PATH");
        } else if (!Files.exists(Paths.get(privateKeyPath))) {
            missing.add("JITSI_PRIVATE_KEY_PATH (file not found: " + privateKeyPath + ")");
        }

        if (!missing.isEmpty()) {
            throw new IntegrationNotConfiguredException(
                    "Jitsi integration is not configured. Missing or invalid: " + String.join(", ", missing)
            );
        }

        PrivateKey privateKey = loadPrivateKey(privateKeyPath);

        Map<String, Object> userContext = new HashMap<>();
        userContext.put("id", user.getId().toString());
        userContext.put("name", user.getFullName());
        userContext.put("email", user.getEmail());
        userContext.put("moderator", isModerator);

        Map<String, Object> featuresContext = new HashMap<>();
        featuresContext.put("recording", isModerator);
        featuresContext.put("livestreaming", false);
        featuresContext.put("screen-sharing", true);

        Map<String, Object> context = new HashMap<>();
        context.put("user", userContext);
        context.put("features", featuresContext);

        Instant now = Instant.now();

        return Jwts.builder()
                .header()
                    .keyId(kid.trim())
                    .type("JWT")
                    .and()
                .issuer("chat")
                .subject(appId.trim())
                .audience().add("jitsi").and()
                .claim("room", roomName)
                .claim("context", context)
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now.minusSeconds(10)))
                .expiration(Date.from(now.plus(3, ChronoUnit.HOURS)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String getAppId() {
        return appId != null ? appId.trim() : "";
    }

    private PrivateKey loadPrivateKey(String pathString) {
        try {
            Path path = Paths.get(pathString);
            String pem = Files.readString(path);

            String privateKeyPEM = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] decoded = Base64.getDecoder().decode(privateKeyPEM);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read Jitsi RSA private key: " + e.getMessage(), e);
        }
    }
}
