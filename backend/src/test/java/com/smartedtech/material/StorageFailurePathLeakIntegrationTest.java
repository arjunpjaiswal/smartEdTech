package com.smartedtech.material;

import com.smartedtech.TestDatabaseCleaner;
import com.smartedtech.classroom.Classroom;
import com.smartedtech.classroom.ClassroomRepository;
import com.smartedtech.security.JwtService;
import com.smartedtech.user.Role;
import com.smartedtech.user.User;
import com.smartedtech.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class StorageFailurePathLeakIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    @Value("${app.file-storage.path}")
    private String storagePathString;

    private User teacher;
    private Classroom classroom;
    private String teacherToken;
    private Path storagePath;

    @BeforeEach
    void setUp() throws IOException {
        databaseCleaner.clean();

        teacher = userRepository.save(new User("storage.teacher@test.com", passwordEncoder.encode("Password123!"), "Storage Teacher", Role.TEACHER));
        classroom = classroomRepository.save(new Classroom("Storage Class", "Room", "STORE101", teacher));
        teacherToken = jwtService.generateAccessToken(teacher);

        storagePath = Paths.get(storagePathString).toAbsolutePath().normalize();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.isRegularFile(storagePath)) {
            Files.delete(storagePath);
        }
        if (!Files.exists(storagePath)) {
            Files.createDirectories(storagePath);
        }
    }

    @Test
    @DisplayName("Forced storage failure returns 500 without leaking filesystem path, IOException, or stack trace")
    void forcedStorageFailureDoesNotLeakPathOrStackTrace() throws Exception {
        if (Files.exists(storagePath)) {
            if (Files.isDirectory(storagePath)) {
                try (var stream = Files.walk(storagePath)) {
                    stream.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            } else {
                Files.delete(storagePath);
            }
        }
        Files.createFile(storagePath);

        byte[] validPdfBytes = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "storage-test.pdf", "application/pdf", validPdfBytes);

        MvcResult result = mockMvc.perform(multipart("/api/v1/classrooms/" + classroom.getId() + "/materials")
                        .file(file)
                        .param("title", "Lecture Test")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        assertThat(responseBody).doesNotContain(storagePath.toString());
        assertThat(responseBody).doesNotContain("test-uploads");
        assertThat(responseBody).doesNotContain("build");
        assertThat(responseBody).doesNotContain("Users");
        assertThat(responseBody).doesNotContain("C:\\");
        assertThat(responseBody).doesNotContain("/home/");

        assertThat(responseBody).doesNotContain("IOException");
        assertThat(responseBody).doesNotContain("FileSystemException");
        assertThat(responseBody).doesNotContain("RuntimeException");
        assertThat(responseBody).doesNotContain(".java:");
        assertThat(responseBody).doesNotContain("at com.smartedtech");
    }
}
