package com.smartedtech.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedtech.attendance.Attendance;
import com.smartedtech.attendance.AttendanceRepository;
import com.smartedtech.classroom.Classroom;
import com.smartedtech.classroom.ClassroomEnrollment;
import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.classroom.ClassroomRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SessionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomEnrollmentRepository enrollmentRepository;

    @Autowired
    private LiveSessionRepository sessionRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.smartedtech.TestDatabaseCleaner databaseCleaner;

    private User teacherA;
    private User teacherB;
    private User student1;
    private User student2;
    private Classroom classroom;

    private String tokenTeacherA;
    private String tokenTeacherB;
    private String tokenStudent1;
    private String tokenStudent2;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();

        teacherA = userRepository.save(new User("teacher.a@test.com", passwordEncoder.encode("Password123!"), "Teacher Alpha", Role.TEACHER));
        teacherB = userRepository.save(new User("teacher.b@test.com", passwordEncoder.encode("Password123!"), "Teacher Beta", Role.TEACHER));
        student1 = userRepository.save(new User("student1@test.com", passwordEncoder.encode("Password123!"), "Student One", Role.STUDENT));
        student2 = userRepository.save(new User("student2@test.com", passwordEncoder.encode("Password123!"), "Student Two", Role.STUDENT));

        classroom = classroomRepository.save(new Classroom("Computer Science 101", "Intro to CS", "CS101001", teacherA));
        enrollmentRepository.save(new ClassroomEnrollment(classroom, student1));

        tokenTeacherA = jwtService.generateAccessToken(teacherA);
        tokenTeacherB = jwtService.generateAccessToken(teacherB);
        tokenStudent1 = jwtService.generateAccessToken(student1);
        tokenStudent2 = jwtService.generateAccessToken(student2);
    }

    @Test
    @DisplayName("Teacher can schedule live session in their classroom")
    void teacherCanScheduleSession() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        CreateSessionRequest request = new CreateSessionRequest("Binary Search Trees", "Tree traversals", start, end);

        mockMvc.perform(post("/api/v1/classrooms/" + classroom.getId() + "/sessions")
                        .header("Authorization", "Bearer " + tokenTeacherA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Binary Search Trees"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.roomName").isNotEmpty());
    }

    @Test
    @DisplayName("Teacher B or Student cannot schedule session in Teacher A's classroom (403 Forbidden)")
    void cannotScheduleSessionWithoutPermission() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        CreateSessionRequest request = new CreateSessionRequest("Hack Class", "Hack", start, end);

        // Teacher B blocked
        mockMvc.perform(post("/api/v1/classrooms/" + classroom.getId() + "/sessions")
                        .header("Authorization", "Bearer " + tokenTeacherB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        // Student blocked
        mockMvc.perform(post("/api/v1/classrooms/" + classroom.getId() + "/sessions")
                        .header("Authorization", "Bearer " + tokenStudent1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unenrolled student cannot view session details (403 Forbidden)")
    void unenrolledStudentCannotViewSession() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        LiveSession session = sessionRepository.save(new LiveSession(classroom, "Graphs", "BFS & DFS", start, end, "room_123"));

        mockMvc.perform(get("/api/v1/sessions/" + session.getId())
                        .header("Authorization", "Bearer " + tokenStudent2))
                .andExpect(status().isForbidden());

        // Enrolled student can view
        mockMvc.perform(get("/api/v1/sessions/" + session.getId())
                        .header("Authorization", "Bearer " + tokenStudent1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Graphs"));
    }

    @Test
    @DisplayName("Joining session without Jitsi credentials returns 503 INTEGRATION_NOT_CONFIGURED and records attendance")
    void joinSessionReturns503WhenJitsiNotConfigured() throws Exception {
        Instant start = Instant.now().minus(10, ChronoUnit.MINUTES);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        LiveSession session = sessionRepository.save(new LiveSession(classroom, "Live Lecture", "Live", start, end, "live_lecture_room"));

        // Enrolled student joins: records attendance then throws 503 INTEGRATION_NOT_CONFIGURED
        mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/join")
                        .header("Authorization", "Bearer " + tokenStudent1))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INTEGRATION_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Jitsi integration is not configured")));

        // Attendance was recorded
        assertThat(attendanceRepository.findBySessionIdAndUserId(session.getId(), student1.getId())).isPresent();

        // Unenrolled student cannot join (returns 403 Forbidden, does not reach Jitsi check)
        mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/join")
                        .header("Authorization", "Bearer " + tokenStudent2))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Heartbeat updates attendance duration and last seen timestamp")
    void attendanceHeartbeatUpdatesDuration() throws Exception {
        Instant start = Instant.now().minus(10, ChronoUnit.MINUTES);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        LiveSession session = sessionRepository.save(new LiveSession(classroom, "Lecture", "Desc", start, end, "room_hb"));

        Attendance attendance = new Attendance(session, student1, Instant.now().minus(120, ChronoUnit.SECONDS));
        attendanceRepository.save(attendance);

        mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/attendance/heartbeat")
                        .header("Authorization", "Bearer " + tokenStudent1))
                .andExpect(status().isNoContent());

        Attendance updated = attendanceRepository.findBySessionIdAndUserId(session.getId(), student1.getId()).orElseThrow();
        assertThat(updated.getDurationSeconds()).isGreaterThanOrEqualTo(100);
    }
}
