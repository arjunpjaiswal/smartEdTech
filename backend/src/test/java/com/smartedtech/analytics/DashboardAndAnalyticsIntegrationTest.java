package com.smartedtech.analytics;

import com.smartedtech.TestDatabaseCleaner;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DashboardAndAnalyticsIntegrationTest {

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
    private StudyMaterialRepository materialRepository;

    @Autowired
    private WeakConceptRepository weakConceptRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    private User admin;
    private User teacher;
    private User studentA;
    private User studentB;

    private String adminToken;
    private String teacherToken;
    private String studentAToken;
    private String studentBToken;

    private Classroom classroom1;
    private Classroom classroom2;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();

        admin = userRepository.save(new User("admin@test.com", passwordEncoder.encode("Pass12345678!"), "Admin User", Role.ADMIN));
        teacher = userRepository.save(new User("teacher@test.com", passwordEncoder.encode("Pass12345678!"), "Teacher User", Role.TEACHER));
        studentA = userRepository.save(new User("student.a@test.com", passwordEncoder.encode("Pass12345678!"), "Student Alpha", Role.STUDENT));
        studentB = userRepository.save(new User("student.b@test.com", passwordEncoder.encode("Pass12345678!"), "Student Beta", Role.STUDENT));

        adminToken = jwtService.generateAccessToken(admin);
        teacherToken = jwtService.generateAccessToken(teacher);
        studentAToken = jwtService.generateAccessToken(studentA);
        studentBToken = jwtService.generateAccessToken(studentB);

        classroom1 = classroomRepository.save(new Classroom("Algorithms 101", "CS Algorithms", "ALG10101", teacher));
        classroom2 = classroomRepository.save(new Classroom("Database Systems", "SQL & Relational", "DBS20202", teacher));

        enrollmentRepository.save(new ClassroomEnrollment(classroom1, studentA));
        enrollmentRepository.save(new ClassroomEnrollment(classroom1, studentB));
        enrollmentRepository.save(new ClassroomEnrollment(classroom2, studentA));

        // Upcoming sessions for classroom1 and classroom2
        sessionRepository.save(new LiveSession(
                classroom1, "Binary Trees", "Lecture 1",
                Instant.now().plus(2, ChronoUnit.HOURS),
                Instant.now().plus(3, ChronoUnit.HOURS),
                "ALG-TREE-1"
        ));
        sessionRepository.save(new LiveSession(
                classroom2, "Normalization", "Lecture 2",
                Instant.now().plus(4, ChronoUnit.HOURS),
                Instant.now().plus(5, ChronoUnit.HOURS),
                "DBS-NORM-1"
        ));

        // Study material for classroom1
        materialRepository.save(new StudyMaterial(
                classroom1, teacher, "Trees Slide Deck", "Slides",
                "trees.pdf", "trees-uuid.pdf", 2048L, "application/pdf"
        ));
    }

    @Test
    @DisplayName("Teacher dashboard summary returns exact expected counts from real database")
    void testTeacherDashboardSummary() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.stat1").value(2)) // 2 classrooms
                .andExpect(jsonPath("$.stat1Label").value("Classrooms"))
                .andExpect(jsonPath("$.stat2").value(3)) // 3 total enrollments across classrooms
                .andExpect(jsonPath("$.stat2Label").value("Enrolled Students"))
                .andExpect(jsonPath("$.stat3").value(2)) // 2 upcoming sessions
                .andExpect(jsonPath("$.stat3Label").value("Upcoming Sessions"))
                .andExpect(jsonPath("$.stat4").value(1)) // 1 study material
                .andExpect(jsonPath("$.stat4Label").value("Study Materials"))
                .andExpect(jsonPath("$.classrooms", hasSize(2)))
                .andExpect(jsonPath("$.upcomingSessions", hasSize(2)));
    }

    @Test
    @DisplayName("Student dashboard summary returns exact expected counts from real database")
    void testStudentDashboardSummary() throws Exception {
        // Student A is enrolled in both classroom1 and classroom2
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.stat1").value(2)) // 2 enrolled classes
                .andExpect(jsonPath("$.stat1Label").value("Enrolled Classes"))
                .andExpect(jsonPath("$.stat2").value(2)) // 2 upcoming sessions across both classes
                .andExpect(jsonPath("$.stat2Label").value("Upcoming Sessions"))
                .andExpect(jsonPath("$.stat3").value(1)) // 1 accessible material
                .andExpect(jsonPath("$.stat3Label").value("Accessible Materials"))
                .andExpect(jsonPath("$.stat4").value(0)) // 0 completed quizzes
                .andExpect(jsonPath("$.stat4Label").value("Completed Quizzes"))
                .andExpect(jsonPath("$.classrooms", hasSize(2)))
                .andExpect(jsonPath("$.upcomingSessions", hasSize(2)))
                .andExpect(jsonPath("$.recentMaterials", hasSize(1)));

        // Student B is enrolled only in classroom1 (has 1 class, 1 upcoming session, 1 material)
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stat1").value(1))
                .andExpect(jsonPath("$.stat2").value(1))
                .andExpect(jsonPath("$.stat3").value(1));
    }

    @Test
    @DisplayName("Admin dashboard summary returns total system counts from real database")
    void testAdminDashboardSummary() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.stat1").value(4)) // 4 total users (admin, teacher, studentA, studentB)
                .andExpect(jsonPath("$.stat1Label").value("Total Users"))
                .andExpect(jsonPath("$.stat2").value(2)) // 2 classrooms
                .andExpect(jsonPath("$.stat2Label").value("Total Classrooms"))
                .andExpect(jsonPath("$.stat3").value(2)) // 2 sessions
                .andExpect(jsonPath("$.stat3Label").value("Total Sessions"))
                .andExpect(jsonPath("$.stat4").value(1)) // 1 material
                .andExpect(jsonPath("$.stat4Label").value("Study Materials"));
    }

    @Test
    @DisplayName("Analytics: Student and Teacher can query weak concepts with known data, foreign student blocked")
    void testWeakConceptsAnalyticsWithKnownData() throws Exception {
        // Persist known weak concept rows
        weakConceptRepository.save(new WeakConcept(
                studentA,
                classroom1,
                100L,
                200L,
                "Binary Search Trees",
                "HIGH",
                "Struggled with tree rotations and balance factor calculation"
        ));
        weakConceptRepository.save(new WeakConcept(
                studentA,
                classroom1,
                101L,
                201L,
                "Dynamic Programming",
                "MODERATE",
                "Missed overlapping subproblem memoization"
        ));

        // 1. Student A queries own weak concepts
        mockMvc.perform(get("/api/v1/students/" + studentA.getId() + "/weak-concepts?classroomId=" + classroom1.getId())
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].conceptName").value("Binary Search Trees"))
                .andExpect(jsonPath("$[0].severity").value("HIGH"))
                .andExpect(jsonPath("$[0].explanation").value("Struggled with tree rotations and balance factor calculation"))
                .andExpect(jsonPath("$[1].conceptName").value("Dynamic Programming"))
                .andExpect(jsonPath("$[1].severity").value("MODERATE"));

        // 2. Teacher queries student A's weak concepts in Teacher's classroom
        mockMvc.perform(get("/api/v1/students/" + studentA.getId() + "/weak-concepts?classroomId=" + classroom1.getId())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].conceptName").value("Binary Search Trees"));

        // 3. Student B is forbidden from inspecting Student A's weak concepts
        mockMvc.perform(get("/api/v1/students/" + studentA.getId() + "/weak-concepts?classroomId=" + classroom1.getId())
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden());
    }
}
