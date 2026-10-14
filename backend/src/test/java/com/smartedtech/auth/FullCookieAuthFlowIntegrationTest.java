package com.smartedtech.auth;

import com.smartedtech.TestDatabaseCleaner;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class FullCookieAuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
    }

    @Test
    @DisplayName("Task 4d: Full cookie auth flow: register, login, cookie flags, /auth/me, refresh with header, CSRF rejection, logout, refresh after logout rejected")
    void fullCookieAuthLifecycle() throws Exception {
        // 1. Register
        String registerBody = """
                {
                  "email": "student.cookie@test.com",
                  "password": "Password123!",
                  "fullName": "Cookie Student",
                  "role": "STUDENT"
                }
                """;

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("student.cookie@test.com"))
                .andReturn();

        String regCookieHeader = regResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(regCookieHeader).isNotNull();
        assertThat(regCookieHeader).contains("refreshToken=");
        assertThat(regCookieHeader).contains("HttpOnly");
        assertThat(regCookieHeader).contains("SameSite=Lax");
        assertThat(regCookieHeader).contains("Path=/api/v1/auth");

        // 2. Login
        String loginBody = """
                {
                  "email": "student.cookie@test.com",
                  "password": "Password123!"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String accessToken = com.jayway.jsonpath.JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
        String setCookieHeader = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);

        // Verify Set-Cookie flags: HttpOnly, SameSite=Lax, Path=/api/v1/auth
        assertThat(setCookieHeader).isNotNull();
        assertThat(setCookieHeader).contains("refreshToken=");
        assertThat(setCookieHeader).contains("HttpOnly");
        assertThat(setCookieHeader).contains("SameSite=Lax");
        assertThat(setCookieHeader).contains("Path=/api/v1/auth");

        // Extract cookie value
        Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");
        assertThat(refreshCookie).isNotNull();

        // 3. /auth/me with Bearer token
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("student.cookie@test.com"))
                .andExpect(jsonPath("$.fullName").value("Cookie Student"))
                .andExpect(jsonPath("$.role").value("STUDENT"));

        // 4. CSRF check: refresh WITHOUT X-Requested-With header must be rejected with 403
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("missing X-Requested-With header")));

        // 5. CSRF check: refresh with bad Origin must be rejected with 403
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Origin", "https://malicious-attacker-website.com"))
                .andExpect(status().isForbidden());

        // 6. Refresh with valid cookie, required header, and allowed origin -> succeeds with rotated cookie
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String rotatedCookieHeader = refreshResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(rotatedCookieHeader).contains("refreshToken=");
        assertThat(rotatedCookieHeader).contains("HttpOnly");
        assertThat(rotatedCookieHeader).contains("SameSite=Lax");
        assertThat(rotatedCookieHeader).contains("Path=/api/v1/auth");

        Cookie newRefreshCookie = refreshResult.getResponse().getCookie("refreshToken");
        assertThat(newRefreshCookie).isNotNull();
        // Cookie rotation: new cookie value must be different
        assertThat(newRefreshCookie.getValue()).isNotEqualTo(refreshCookie.getValue());

        // 7. Logout with valid cookie and header
        MvcResult logoutResult = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(newRefreshCookie)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Successfully logged out"))
                .andReturn();

        String logoutCookieHeader = logoutResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(logoutCookieHeader).contains("Max-Age=0");

        // 8. Refresh AFTER logout with the old or revoked cookie must be rejected
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(newRefreshCookie)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
