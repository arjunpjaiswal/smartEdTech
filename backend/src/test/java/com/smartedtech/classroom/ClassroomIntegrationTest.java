package com.smartedtech.classroom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedtech.classroom.dto.CreateClassroomRequest;
import com.smartedtech.classroom.dto.EnrollStudentRequest;
import com.smartedtech.classroom.dto.JoinClassroomRequest;
import com.smartedtech.classroom.dto.UpdateClassroomRequest;
import com.smartedtech.security.JwtService;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ClassroomIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomEnrollmentRepository enrollmentRepository;

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

        tokenTeacherA = jwtService.generateAccessToken(teacherA);
        tokenTeacherB = jwtService.generateAccessToken(teacherB);
        tokenStudent1 = jwtService.generateAccessToken(student1);
        tokenStudent2 = jwtService.generateAccessToken(student2);
    }

    @Test
    @DisplayName("Teacher can create classroom and gets unique join code")
    void teacherCanCreateClassroom() throws Exception {
        CreateClassroomRequest request = new CreateClassroomRequest("Mathematics 101", "Introductory calculus");

        MvcResult result = mockMvc.perform(post("/api/v1/classrooms")
                        .header("Authorization", "Bearer " + tokenTeacherA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Mathematics 101"))
                .andExpect(jsonPath("$.joinCode").isNotEmpty())
                .andExpect(jsonPath("$.teacherName").value("Teacher Alpha"))
                .andReturn();

        JsonNode responseNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String joinCode = responseNode.get("joinCode").asText();
        assertThat(joinCode).hasSize(8);
    }

    @Test
    @DisplayName("Student cannot create classroom returning 403")
    void studentCannotCreateClassroom() throws Exception {
        CreateClassroomRequest request = new CreateClassroomRequest("Biology 101", "Introductory biology");

        mockMvc.perform(post("/api/v1/classrooms")
                        .header("Authorization", "Bearer " + tokenStudent1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Student can join classroom by join code and duplicate enrollment returns 409")
    void studentJoinByCodeAndDuplicateFails() throws Exception {
        Classroom classroom = classroomRepository.save(new Classroom("Physics 101", "Mechanics", "PHY10101", teacherA));

        JoinClassroomRequest joinRequest = new JoinClassroomRequest("PHY10101");

        // First join succeeds
        mockMvc.perform(post("/api/v1/classrooms/join")
                        .header("Authorization", "Bearer " + tokenStudent1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(joinRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Physics 101"))
                .andExpect(jsonPath("$.studentCount").value(1));

        assertThat(enrollmentRepository.existsByClassroomIdAndStudentId(classroom.getId(), student1.getId())).isTrue();

        // Duplicate join returns 409 Conflict
        mockMvc.perform(post("/api/v1/classrooms/join")
                        .header("Authorization", "Bearer " + tokenStudent1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(joinRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Already enrolled in this classroom"));
    }

    @Test
    @DisplayName("Teacher A cannot edit or archive Teacher B's classroom (403 Forbidden)")
    void teacherCannotModifyOthersClassroom() throws Exception {
        Classroom classB = classroomRepository.save(new Classroom("Chemistry", "Organic", "CHEM2001", teacherB));

        UpdateClassroomRequest updateReq = new UpdateClassroomRequest("Hacked Chemistry", "Hacked");

        mockMvc.perform(put("/api/v1/classrooms/" + classB.getId())
                        .header("Authorization", "Bearer " + tokenTeacherA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/classrooms/" + classB.getId() + "/archive")
                        .header("Authorization", "Bearer " + tokenTeacherA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("archived", true))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Student cannot view details of unenrolled classroom (403 Forbidden)")
    void studentCannotAccessUnenrolledClassroom() throws Exception {
        Classroom classroom = classroomRepository.save(new Classroom("History 201", "European History", "HIST2001", teacherA));

        // Unenrolled student1 receives 403 Forbidden
        mockMvc.perform(get("/api/v1/classrooms/" + classroom.getId())
                        .header("Authorization", "Bearer " + tokenStudent1))
                .andExpect(status().isForbidden());

        // Enroll student1
        enrollmentRepository.save(new ClassroomEnrollment(classroom, student1));

        // Enrolled student1 can now access details
        mockMvc.perform(get("/api/v1/classrooms/" + classroom.getId())
                        .header("Authorization", "Bearer " + tokenStudent1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("History 201"));
    }

    @Test
    @DisplayName("Teacher can enroll and remove student, and duplicate manual enroll returns 409")
    void teacherCanManageStudents() throws Exception {
        Classroom classroom = classroomRepository.save(new Classroom("Literature", "World Literature", "LIT10001", teacherA));

        EnrollStudentRequest enrollRequest = new EnrollStudentRequest(student1.getEmail(), null);

        // Teacher enrolls student1
        mockMvc.perform(post("/api/v1/classrooms/" + classroom.getId() + "/students")
                        .header("Authorization", "Bearer " + tokenTeacherA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enrollRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentEmail").value("student1@test.com"));

        // Duplicate manual enroll returns 409
        mockMvc.perform(post("/api/v1/classrooms/" + classroom.getId() + "/students")
                        .header("Authorization", "Bearer " + tokenTeacherA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enrollRequest)))
                .andExpect(status().isConflict());

        // View student list
        mockMvc.perform(get("/api/v1/classrooms/" + classroom.getId() + "/students")
                        .header("Authorization", "Bearer " + tokenTeacherA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].studentEmail").value("student1@test.com"));

        // Remove student
        mockMvc.perform(delete("/api/v1/classrooms/" + classroom.getId() + "/students/" + student1.getId())
                        .header("Authorization", "Bearer " + tokenTeacherA))
                .andExpect(status().isNoContent());

        assertThat(enrollmentRepository.existsByClassroomIdAndStudentId(classroom.getId(), student1.getId())).isFalse();
    }
}
