package com.smartedtech.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedtech.TestDatabaseCleaner;
import com.smartedtech.classroom.Classroom;
import com.smartedtech.classroom.ClassroomEnrollment;
import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.classroom.ClassroomRepository;
import com.smartedtech.classroom.dto.EnrollStudentRequest;
import com.smartedtech.security.JwtService;
import com.smartedtech.session.dto.CreateSessionRequest;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class NotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomEnrollmentRepository enrollmentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    private User teacher;
    private User studentA;
    private User studentB;

    private String teacherToken;
    private String studentAToken;
    private String studentBToken;

    private Classroom classroom;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();

        teacher = userRepository.save(new User("teacher@test.com", passwordEncoder.encode("Pass12345678!"), "Teacher User", Role.TEACHER));
        studentA = userRepository.save(new User("student.a@test.com", passwordEncoder.encode("Pass12345678!"), "Student Alpha", Role.STUDENT));
        studentB = userRepository.save(new User("student.b@test.com", passwordEncoder.encode("Pass12345678!"), "Student Beta", Role.STUDENT));

        teacherToken = jwtService.generateAccessToken(teacher);
        studentAToken = jwtService.generateAccessToken(studentA);
        studentBToken = jwtService.generateAccessToken(studentB);

        classroom = classroomRepository.save(new Classroom("Operating Systems", "OS Concepts", "OS100001", teacher));
        // Enroll studentA upfront
        enrollmentRepository.save(new ClassroomEnrollment(classroom, studentA));
    }

    @Test
    @DisplayName("Notifications are created on session scheduled, material uploaded, and enrollment, scoped per user")
    void testNotificationCreationAndUserScoping() throws Exception {
        // 1. Teacher schedules a live session -> studentA gets notified
        CreateSessionRequest sessionReq = new CreateSessionRequest(
                "Memory Management Lecture",
                "Virtual Memory",
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS)
        );
        mockMvc.perform(post("/api/v1/classrooms/" + classroom.getId() + "/sessions")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sessionReq)))
                .andExpect(status().isCreated());

        // 2. Teacher uploads study material -> studentA gets notified
        byte[] pdfBytes = "%PDF-1.4\nLecture notes on paging".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "paging.pdf", "application/pdf", pdfBytes);
        mockMvc.perform(multipart("/api/v1/classrooms/" + classroom.getId() + "/materials")
                        .file(file)
                        .param("title", "Paging Architecture")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isCreated());

        // 3. Teacher enrolls studentB -> both studentB and teacher receive notification
        EnrollStudentRequest enrollReq = new EnrollStudentRequest(null, studentB.getId());
        mockMvc.perform(post("/api/v1/classrooms/" + classroom.getId() + "/students")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enrollReq)))
                .andExpect(status().isCreated());

        // Check studentA's unread notifications: should have received session and material notifications (2)
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].type", org.hamcrest.Matchers.hasItems("MATERIAL_UPLOADED", "SESSION_SCHEDULED")));

        // Check studentB's notifications: only received enrollment notification (1)
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        // Scoping check: studentB does NOT see studentA's session or material notifications
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].type").value("ENROLLMENT"));
    }

    @Test
    @DisplayName("Mark as read and mark all as read endpoints update state and decrement unread counter")
    void testMarkAsReadLifecycle() throws Exception {
        // Create 2 notifications directly for studentA
        Notification n1 = notificationRepository.save(new Notification(studentA, "ALERT", "Notice 1", "Body 1"));
        Notification n2 = notificationRepository.save(new Notification(studentA, "ALERT", "Notice 2", "Body 2"));

        // Initial unread count = 2
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2));

        // Mark n1 as read
        mockMvc.perform(patch("/api/v1/notifications/" + n1.getId() + "/read")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isNoContent());

        // Unread count should now be 1
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        assertThat(notificationRepository.findById(n1.getId()).orElseThrow().isReadFlag()).isTrue();
        assertThat(notificationRepository.findById(n2.getId()).orElseThrow().isReadFlag()).isFalse();

        // Mark all as read
        mockMvc.perform(patch("/api/v1/notifications/read-all")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isNoContent());

        // Unread count should now be 0
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));

        assertThat(notificationRepository.findById(n2.getId()).orElseThrow().isReadFlag()).isTrue();
    }
}
