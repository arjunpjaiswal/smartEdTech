package com.smartedtech.security;

import com.smartedtech.TestDatabaseCleaner;
import com.smartedtech.assessment.*;
import com.smartedtech.classroom.Classroom;
import com.smartedtech.classroom.ClassroomEnrollment;
import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.classroom.ClassroomRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class StudentAnswerKeyLeakIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomEnrollmentRepository enrollmentRepository;

    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private AnswerOptionRepository answerOptionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    private User teacher;
    private User student;
    private Classroom classroom;
    private Assessment assessment;
    private String teacherToken;
    private String studentToken;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();

        teacher = userRepository.save(new User("prof.security@test.com", passwordEncoder.encode("Password123!"), "Prof Security", Role.TEACHER));
        student = userRepository.save(new User("student.security@test.com", passwordEncoder.encode("Password123!"), "Security Student", Role.STUDENT));

        classroom = classroomRepository.save(new Classroom("Cybersecurity 101", "InfoSec", "SEC101", teacher));
        enrollmentRepository.save(new ClassroomEnrollment(classroom, student));

        teacherToken = jwtService.generateAccessToken(teacher);
        studentToken = jwtService.generateAccessToken(student);

        // Create published assessment
        assessment = new Assessment(
                classroom,
                teacher,
                "Network Security Exam",
                "Mid-term exam on cryptography and access control",
                "No outside materials allowed",
                60,
                Instant.now().plus(7, ChronoUnit.DAYS),
                1
        );
        assessment.setStatus("PUBLISHED");
        assessment = assessmentRepository.save(assessment);

        // Question 1: MCQ with confidential isCorrect
        Question mcq = new Question(
                assessment,
                "What algorithm provides symmetric encryption?",
                "MCQ",
                new BigDecimal("5.00"),
                0,
                null,
                null
        );
        mcq = questionRepository.save(mcq);

        AnswerOption opt1 = new AnswerOption(mcq, "AES", true, 0);
        AnswerOption opt2 = new AnswerOption(mcq, "RSA", false, 1);
        AnswerOption opt3 = new AnswerOption(mcq, "Diffie-Hellman", false, 2);
        answerOptionRepository.saveAll(List.of(opt1, opt2, opt3));

        // Question 2: Subjective with confidential model answer and rubric
        Question subj = new Question(
                assessment,
                "Explain the principle of least privilege.",
                "SUBJECTIVE",
                new BigDecimal("10.00"),
                1,
                "This is the secret model answer: users should only possess the minimum credentials necessary.",
                "This is the grading rubric: 5 points for definition, 5 points for practical example."
        );
        questionRepository.save(subj);
    }

    @Test
    @DisplayName("Task 4a: Student fetching assessment never receives answer keys, model answers, or rubrics")
    void studentNeverReceivesAnswerKeysOrRubrics() throws Exception {
        MvcResult studentResult = mockMvc.perform(get("/api/v1/assessments/" + assessment.getId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].options[0].isCorrect").doesNotExist())
                .andExpect(jsonPath("$.questions[0].options[1].isCorrect").doesNotExist())
                .andExpect(jsonPath("$.questions[1].modelAnswer").doesNotExist())
                .andExpect(jsonPath("$.questions[1].rubric").doesNotExist())
                .andReturn();

        String body = studentResult.getResponse().getContentAsString();

        // Must not contain answer keys, model answers, rubrics, or explanations
        assertThat(body).doesNotContain("\"correctAnswer\"");
        assertThat(body).doesNotContain("\"explanation\"");
        assertThat(body).doesNotContain("\"isCorrect\":true");
        assertThat(body).doesNotContain("\"isCorrect\":false");
        assertThat(body).doesNotContain("This is the secret model answer");
        assertThat(body).doesNotContain("This is the grading rubric");

        // Now student starts an attempt and fetches the attempt details before results release
        MvcResult startResult = mockMvc.perform(post("/api/v1/assessments/" + assessment.getId() + "/start")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn();

        String startBody = startResult.getResponse().getContentAsString();
        assertThat(startBody).doesNotContain("\"correctAnswer\"");
        assertThat(startBody).doesNotContain("\"isCorrect\"");
        assertThat(startBody).doesNotContain("This is the secret model answer");
        assertThat(startBody).doesNotContain("This is the grading rubric");
        assertThat(startBody).doesNotContain("\"explanation\"");

        // In contrast, teacher CAN see model answer, rubric, and isCorrect
        MvcResult teacherResult = mockMvc.perform(get("/api/v1/assessments/" + assessment.getId())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andReturn();

        String teacherBody = teacherResult.getResponse().getContentAsString();
        assertThat(teacherBody).contains("This is the secret model answer");
        assertThat(teacherBody).contains("This is the grading rubric");
        assertThat(teacherBody).contains("\"isCorrect\":true");
    }
}
