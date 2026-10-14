package com.smartedtech.material;

import com.smartedtech.TestDatabaseCleaner;
import com.smartedtech.classroom.Classroom;
import com.smartedtech.classroom.ClassroomRepository;
import com.smartedtech.common.BadRequestException;
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PathTraversalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private FileStorage fileStorage;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    private User teacher;
    private Classroom classroom;
    private String teacherToken;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();

        teacher = userRepository.save(new User("teacher.traversal@test.com", passwordEncoder.encode("Password123!"), "Teacher Traversal", Role.TEACHER));
        classroom = classroomRepository.save(new Classroom("Security Test Classroom", "Path Traversal", "TRAV101", teacher));
        teacherToken = jwtService.generateAccessToken(teacher);
    }

    @Test
    @DisplayName("Task 4b: Uploading with path traversal filename '../evil.pdf' is safely sanitized into storage root")
    void uploadWithTraversalFilenameIsSanitized() throws Exception {
        byte[] validPdfBytes = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "../../evil.pdf", "application/pdf", validPdfBytes);

        mockMvc.perform(multipart("/api/v1/classrooms/" + classroom.getId() + "/materials")
                        .file(file)
                        .param("title", "Traversal Test Slide")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("../../evil.pdf"));

        // Verify no file named "evil.pdf" exists in current working directory or parent directories
        assertThat(Files.exists(Paths.get("evil.pdf"))).isFalse();
        assertThat(Files.exists(Paths.get("../evil.pdf"))).isFalse();
        assertThat(Files.exists(Paths.get("../../evil.pdf"))).isFalse();
    }

    @Test
    @DisplayName("Task 4b: Attempting to download or load outside storage root throws BadRequestException")
    void loadWithCraftedStoredNameIsBlocked() {
        // Direct storage attempt with ../ traversal
        assertThatThrownBy(() -> fileStorage.loadAsResource("../../etc/passwd"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid file path");

        assertThatThrownBy(() -> fileStorage.loadAsResource("..\\..\\windows\\win.ini"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid file path");

        assertThatThrownBy(() -> fileStorage.loadAsResource("../application.yml"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid file path");
    }
}
