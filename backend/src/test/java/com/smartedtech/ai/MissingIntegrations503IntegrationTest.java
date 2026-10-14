package com.smartedtech.ai;

import com.smartedtech.TestDatabaseCleaner;
import com.smartedtech.chatbot.dto.CreateConversationRequest;
import com.smartedtech.classroom.Classroom;
import com.smartedtech.classroom.ClassroomEnrollment;
import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.classroom.ClassroomRepository;
import com.smartedtech.material.StudyMaterial;
import com.smartedtech.material.StudyMaterialRepository;
import com.smartedtech.security.JwtService;
import com.smartedtech.session.LiveSession;
import com.smartedtech.session.LiveSessionRepository;
import com.smartedtech.user.Role;
import com.smartedtech.user.User;
import com.smartedtech.user.UserRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class MissingIntegrations503IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomEnrollmentRepository enrollmentRepository;

    @Autowired
    private StudyMaterialRepository materialRepository;

    @Autowired
    private LiveSessionRepository sessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    private User teacher;
    private User student;
    private Classroom classroom;
    private StudyMaterial material;
    private LiveSession session;
    private String teacherToken;
    private String studentToken;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();

        teacher = userRepository.save(new User("prof.missing@test.com", passwordEncoder.encode("Password123!"), "Prof Missing", Role.TEACHER));
        student = userRepository.save(new User("student.missing@test.com", passwordEncoder.encode("Password123!"), "Student Missing", Role.STUDENT));

        classroom = classroomRepository.save(new Classroom("AI Test Classroom", "Testing missing keys", "AIMISS101", teacher));
        enrollmentRepository.save(new ClassroomEnrollment(classroom, student));

        material = materialRepository.save(new StudyMaterial(classroom, teacher, "Syllabus", "Syllabus details", "syllabus.pdf", "syllabus-uuid.pdf", 1024L, "application/pdf"));

        session = sessionRepository.save(new LiveSession(classroom, "Live Lecture", "Demo Session", Instant.now().plus(1, ChronoUnit.HOURS), Instant.now().plus(2, ChronoUnit.HOURS), "room-101"));

        teacherToken = jwtService.generateAccessToken(teacher);
        studentToken = jwtService.generateAccessToken(student);
    }

    @Test
    @DisplayName("Task 4c: AI generation endpoint returns 503 INTEGRATION_NOT_CONFIGURED naming missing GEMINI_API_KEY / GROQ_API_KEY")
    void aiQuestionGenerationReturns503WhenKeysUnset() throws Exception {
        String body = """
                {
                  "materialId": %d,
                  "count": 5,
                  "difficulty": "MEDIUM",
                  "questionTypes": "MIXED"
                }
                """.formatted(material.getId());

        mockMvc.perform(post("/api/v1/ai/generate-questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("INTEGRATION_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("GEMINI_API_KEY")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("GROQ_API_KEY")));
    }

    @Test
    @DisplayName("Task 4c: Study buddy endpoint returns 503 INTEGRATION_NOT_CONFIGURED naming missing AI variables")
    void studyBuddyReturns503WhenKeysUnset() throws Exception {
        // Create conversation
        String convBody = """
                {
                  "classroomId": %d,
                  "title": "Study Help"
                }
                """.formatted(classroom.getId());

        MvcResult createRes = mockMvc.perform(post("/api/v1/chat/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(convBody)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn();

        Long convId = ((Number) com.jayway.jsonpath.JsonPath.read(createRes.getResponse().getContentAsString(), "$.id")).longValue();

        // Send message
        String msgBody = """
                {
                  "content": "Can you explain Dijkstra's algorithm step by step?"
                }
                """;

        mockMvc.perform(post("/api/v1/chat/conversations/" + convId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(msgBody)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("INTEGRATION_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("GEMINI_API_KEY")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("GROQ_API_KEY")));
    }

    @Test
    @DisplayName("Task 4c: /sessions/{id}/join returns 503 INTEGRATION_NOT_CONFIGURED naming missing Jitsi variables")
    void jitsiJoinReturns503WhenVariablesUnset() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/join")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("INTEGRATION_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("JITSI_APP_ID")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("JITSI_KID")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("JITSI_PRIVATE_KEY_PATH")));
    }
}
