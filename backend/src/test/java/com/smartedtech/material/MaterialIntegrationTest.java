package com.smartedtech.material;

import com.smartedtech.classroom.Classroom;
import com.smartedtech.classroom.ClassroomEnrollment;
import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.classroom.ClassroomRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class MaterialIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

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

        classroom = classroomRepository.save(new Classroom("Data Structures", "Course Materials", "DS100001", teacherA));
        enrollmentRepository.save(new ClassroomEnrollment(classroom, student1));

        tokenTeacherA = jwtService.generateAccessToken(teacherA);
        tokenTeacherB = jwtService.generateAccessToken(teacherB);
        tokenStudent1 = jwtService.generateAccessToken(student1);
        tokenStudent2 = jwtService.generateAccessToken(student2);
    }

    @Test
    @DisplayName("Teacher can upload valid PDF file with real magic header bytes")
    void teacherCanUploadValidPdf() throws Exception {
        byte[] validPdfBytes = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "lecture1.pdf", "application/pdf", validPdfBytes);

        mockMvc.perform(multipart("/api/v1/classrooms/" + classroom.getId() + "/materials")
                        .file(file)
                        .param("title", "Lecture 1 Slides")
                        .param("description", "Introduction to data structures")
                        .header("Authorization", "Bearer " + tokenTeacherA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Lecture 1 Slides"))
                .andExpect(jsonPath("$.originalFilename").value("lecture1.pdf"))
                .andExpect(jsonPath("$.fileSize").value(validPdfBytes.length));

        assertThat(materialRepository.countByClassroomId(classroom.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Upload rejects file with mismatched/fake extension (e.g. exe renamed to pdf)")
    void uploadRejectsInvalidMagicBytes() throws Exception {
        byte[] fakePdfBytes = new byte[]{0x4D, 0x5A, 0x00, 0x00, 0x00}; // Windows MZ executable header
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf", fakePdfBytes);

        mockMvc.perform(multipart("/api/v1/classrooms/" + classroom.getId() + "/materials")
                        .file(file)
                        .param("title", "Malicious File")
                        .header("Authorization", "Bearer " + tokenTeacherA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("File content does not match PDF format"));
    }

    @Test
    @DisplayName("Teacher B or Student cannot upload material into Teacher A's classroom (403 Forbidden)")
    void cannotUploadMaterialWithoutOwnership() throws Exception {
        byte[] validPdfBytes = "%PDF-1.4\ncontent".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "notes.pdf", "application/pdf", validPdfBytes);

        // Teacher B blocked
        mockMvc.perform(multipart("/api/v1/classrooms/" + classroom.getId() + "/materials")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenTeacherB))
                .andExpect(status().isForbidden());

        // Student blocked
        mockMvc.perform(multipart("/api/v1/classrooms/" + classroom.getId() + "/materials")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenStudent1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Enrolled student can download material; unenrolled student receives 403")
    void enrolledStudentCanDownload() throws Exception {
        byte[] validPdfBytes = "%PDF-1.4\nTest lecture content".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "syllabus.pdf", "application/pdf", validPdfBytes);

        // Upload as teacher
        mockMvc.perform(multipart("/api/v1/classrooms/" + classroom.getId() + "/materials")
                        .file(file)
                        .param("title", "Syllabus")
                        .header("Authorization", "Bearer " + tokenTeacherA))
                .andExpect(status().isCreated());

        StudyMaterial material = materialRepository.findByClassroomIdOrderByCreatedAtDesc(classroom.getId(), null)
                .getContent().get(0);

        // Unenrolled student receives 403 Forbidden
        mockMvc.perform(get("/api/v1/materials/" + material.getId() + "/download")
                        .header("Authorization", "Bearer " + tokenStudent2))
                .andExpect(status().isForbidden());

        // Enrolled student can download
        mockMvc.perform(get("/api/v1/materials/" + material.getId() + "/download")
                        .header("Authorization", "Bearer " + tokenStudent1))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("syllabus.pdf")))
                .andExpect(content().bytes(validPdfBytes));
    }
}
