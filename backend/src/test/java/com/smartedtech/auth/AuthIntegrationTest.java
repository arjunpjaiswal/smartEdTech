package com.smartedtech.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedtech.security.JwtService;
import com.smartedtech.security.RateLimitService;
import com.smartedtech.user.Role;
import com.smartedtech.user.User;
import com.smartedtech.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.smartedtech.TestDatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        rateLimitService.reset();
    }

    @Test
    @DisplayName("Registration succeeds for STUDENT and TEACHER, stores hashed password")
    void testRegisterSuccessStudentAndTeacher() throws Exception {
        RegisterRequest studentReq = new RegisterRequest(
                "student1@example.com",
                "Password123!",
                "Alice Student",
                Role.STUDENT
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(studentReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.user.email").value("student1@example.com"))
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andReturn();

        // Verify refreshToken cookie
        Cookie cookie = result.getResponse().getCookie("refreshToken");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");

        // Verify database entry
        User savedUser = userRepository.findByEmail("student1@example.com").orElseThrow();
        assertThat(savedUser.getFullName()).isEqualTo("Alice Student");
        assertThat(passwordEncoder.matches("Password123!", savedUser.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Public registration forbids ADMIN role")
    void testRegisterAdminForbidden() throws Exception {
        RegisterRequest adminReq = new RegisterRequest(
                "hacker@example.com",
                "Password123!",
                "Fake Admin",
                Role.ADMIN
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Public registration allows only STUDENT or TEACHER roles"));
    }

    @Test
    @DisplayName("Registration rejects duplicate email with 409 Conflict")
    void testRegisterDuplicateEmailConflict() throws Exception {
        RegisterRequest req1 = new RegisterRequest("dup@example.com", "Password123!", "User 1", Role.STUDENT);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk());

        RegisterRequest req2 = new RegisterRequest("dup@example.com", "Password123!", "User 2", Role.TEACHER);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("Login succeeds with valid credentials")
    void testLoginSuccess() throws Exception {
        User user = new User("login@example.com", passwordEncoder.encode("Secret123!"), "Login User", Role.TEACHER);
        userRepository.save(user);

        LoginRequest loginReq = new LoginRequest("login@example.com", "Secret123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.user.email").value("login@example.com"))
                .andExpect(jsonPath("$.user.role").value("TEACHER"));
    }

    @Test
    @DisplayName("Login fails with wrong password returning 401")
    void testLoginWrongPassword() throws Exception {
        User user = new User("wrongpass@example.com", passwordEncoder.encode("RealSecret123!"), "Wrong Pass", Role.STUDENT);
        userRepository.save(user);

        LoginRequest loginReq = new LoginRequest("wrongpass@example.com", "WrongSecret123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("JWT validation and expiry check")
    void testJwtExpiry() {
        User user = new User("jwt@example.com", "hash", "JWT User", Role.STUDENT);
        user.setId(999L);

        String token = jwtService.generateAccessToken(user);
        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractEmail(token)).isEqualTo("jwt@example.com");
        assertThat(jwtService.extractRole(token)).isEqualTo(Role.STUDENT);

        // Invalid token returns false
        assertThat(jwtService.isTokenValid("invalid.token.structure")).isFalse();
    }

    @Test
    @DisplayName("Refresh token rotates on use and revokes previous token")
    void testRefreshRotation() throws Exception {
        User user = new User("rotate@example.com", passwordEncoder.encode("Pass1234!"), "Rotate User", Role.STUDENT);
        userRepository.save(user);

        LoginRequest loginReq = new LoginRequest("rotate@example.com", "Pass1234!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie originalCookie = loginResult.getResponse().getCookie("refreshToken");
        assertThat(originalCookie).isNotNull();

        // Perform refresh with X-Requested-With and Origin
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(originalCookie)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn();

        Cookie rotatedCookie = refreshResult.getResponse().getCookie("refreshToken");
        assertThat(rotatedCookie).isNotNull();
        assertThat(rotatedCookie.getValue()).isNotEqualTo(originalCookie.getValue());

        // Check DB: original token is revoked
        String originalHash = AuthService.hashToken(originalCookie.getValue());
        RefreshToken oldTokenInDb = refreshTokenRepository.findByTokenHash(originalHash).orElseThrow();
        assertThat(oldTokenInDb.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("Refresh token reuse detection revokes entire token family")
    void testRefreshReuseDetection() throws Exception {
        User user = new User("reuse@example.com", passwordEncoder.encode("Pass1234!"), "Reuse User", Role.STUDENT);
        userRepository.save(user);

        LoginRequest loginReq = new LoginRequest("reuse@example.com", "Pass1234!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie initialCookie = loginResult.getResponse().getCookie("refreshToken");

        // First refresh: legit rotation
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(initialCookie)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie secondaryCookie = refreshResult.getResponse().getCookie("refreshToken");

        // Attacker / replay: reuses initialCookie
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(initialCookie)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Refresh token reuse detected")));

        // Verify that secondaryCookie is also now revoked
        String secondaryHash = AuthService.hashToken(secondaryCookie.getValue());
        RefreshToken secondaryTokenInDb = refreshTokenRepository.findByTokenHash(secondaryHash).orElseThrow();
        assertThat(secondaryTokenInDb.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("Role checks: TEACHER/ADMIN can access /api/v1/system/integrations, STUDENT cannot")
    void testRoleChecksSystemIntegrations() throws Exception {
        User student = new User("student@test.com", "hash", "Student", Role.STUDENT);
        userRepository.save(student);
        String studentJwt = jwtService.generateAccessToken(student);

        User teacher = new User("teacher@test.com", "hash", "Teacher", Role.TEACHER);
        userRepository.save(teacher);
        String teacherJwt = jwtService.generateAccessToken(teacher);

        // Student receives 403 Forbidden
        mockMvc.perform(get("/api/v1/system/integrations")
                        .header("Authorization", "Bearer " + studentJwt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // Teacher receives 200 OK
        mockMvc.perform(get("/api/v1/system/integrations")
                        .header("Authorization", "Bearer " + teacherJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gemini").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.groq").value("NOT_CONFIGURED"));

        // Unauthenticated receives 401
        mockMvc.perform(get("/api/v1/system/integrations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("CSRF protection blocks cookie endpoints without X-Requested-With header")
    void testCsrfProtection() throws Exception {
        Cookie dummyCookie = new Cookie("refreshToken", "sample-token");
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(dummyCookie)
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_VERIFICATION_FAILED"));
    }

    @Test
    @DisplayName("Rate limit blocks requests when exceeding 10 per minute")
    void testRateLimitExceeded() throws Exception {
        LoginRequest req = new LoginRequest("ratelimit@example.com", "BadPass");

        // Send 10 requests: all return 401 (not rate limited yet)
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }

        // 11th request triggers 429 Too Many Requests
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }
}
