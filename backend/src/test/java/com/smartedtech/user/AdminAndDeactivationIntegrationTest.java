package com.smartedtech.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedtech.auth.AuthService;
import com.smartedtech.auth.LoginRequest;
import com.smartedtech.auth.RefreshTokenRepository;
import com.smartedtech.security.JwtService;
import com.smartedtech.user.dto.UpdateUserStatusRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminAndDeactivationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private com.smartedtech.TestDatabaseCleaner databaseCleaner;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    private User admin;
    private User teacher;
    private User student;

    private String tokenAdmin;
    private String tokenTeacher;
    private String tokenStudent;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();

        admin = userRepository.save(new User("admin@test.com", passwordEncoder.encode("AdminPass123!"), "Platform Admin", Role.ADMIN));
        teacher = userRepository.save(new User("teacher@test.com", passwordEncoder.encode("TeacherPass123!"), "Dr. Teacher", Role.TEACHER));
        student = userRepository.save(new User("student@test.com", passwordEncoder.encode("StudentPass123!"), "Jane Student", Role.STUDENT));

        tokenAdmin = jwtService.generateAccessToken(admin);
        tokenTeacher = jwtService.generateAccessToken(teacher);
        tokenStudent = jwtService.generateAccessToken(student);
    }

    @Test
    @DisplayName("Admin can search users and view platform stats")
    void adminCanSearchAndGetStats() throws Exception {
        // Search users
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("search", "Student")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("student@test.com"));

        // Platform stats
        mockMvc.perform(get("/api/v1/admin/stats")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(3))
                .andExpect(jsonPath("$.totalStudents").value(1))
                .andExpect(jsonPath("$.totalTeachers").value(1))
                .andExpect(jsonPath("$.totalAdmins").value(1));
    }

    @Test
    @DisplayName("Teacher and Student cannot access admin endpoints (403 Forbidden)")
    void nonAdminCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + tokenTeacher))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/stats")
                        .header("Authorization", "Bearer " + tokenStudent))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Deactivated user cannot login, refresh token, or make authenticated requests")
    void deactivatedUserIsCompletelyBlocked() throws Exception {
        // 1. Student logs in initially
        AuthService.AuthResult loginResult = authService.login(new LoginRequest("student@test.com", "StudentPass123!"));
        String rawRefreshToken = loginResult.rawRefreshToken();
        String activeAccessToken = loginResult.accessToken();

        // 2. Admin deactivates student
        UpdateUserStatusRequest deactReq = new UpdateUserStatusRequest(false);
        mockMvc.perform(patch("/api/v1/admin/users/" + student.getId() + "/status")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        User updatedStudent = userRepository.findById(student.getId()).orElseThrow();
        assertThat(updatedStudent.isEnabled()).isFalse();

        // 3. Deactivated user cannot log in (401 Unauthorized)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("student@test.com", "StudentPass123!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Account is disabled"));

        // 4. Deactivated user cannot refresh token (401 Unauthorized)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", rawRefreshToken))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());

        // 5. Existing access token is rejected on authenticated endpoints (401 Unauthorized)
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + activeAccessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Admin cannot deactivate themselves (400 Bad Request)")
    void adminCannotDeactivateSelf() throws Exception {
        UpdateUserStatusRequest deactReq = new UpdateUserStatusRequest(false);
        mockMvc.perform(patch("/api/v1/admin/users/" + admin.getId() + "/status")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot change your own account status"));
    }
}
