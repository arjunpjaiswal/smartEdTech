package com.smartedtech.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedtech.assessment.dto.*;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AssessmentIntegrationTest {

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
    private AssessmentAttemptRepository attemptRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.smartedtech.TestDatabaseCleaner databaseCleaner;

    private User teacher;
    private User student;
    private Classroom classroom;
    private String teacherToken;
    private String studentToken;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();

        teacher = userRepository.save(new User("teacher@test.com", passwordEncoder.encode("Password123!"), "Teacher Smith", Role.TEACHER));
        student = userRepository.save(new User("student@test.com", passwordEncoder.encode("Password123!"), "Student Bob", Role.STUDENT));

        teacherToken = jwtService.generateAccessToken(teacher);
        studentToken = jwtService.generateAccessToken(student);

        classroom = classroomRepository.save(new Classroom("Physics 101", "Introduction to Physics", "PHY101CD", teacher));
        enrollmentRepository.save(new ClassroomEnrollment(classroom, student));
    }

    @Test
    @DisplayName("Publishing assessment without questions is blocked with 400 Bad Request")
    void publishAssessment_BlockedWhenNoQuestions() throws Exception {
        Assessment assessment = assessmentRepository.save(new Assessment(
                classroom, teacher, "Empty Quiz", "Desc", "Instructions", 30, Instant.now().plus(2, ChronoUnit.DAYS), 1
        ));

        mockMvc.perform(put("/api/v1/assessments/" + assessment.getId() + "/publish")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot publish assessment: at least one valid question is required"));
    }

    @Test
    @DisplayName("Students cannot view DRAFT assessments (403 Forbidden)")
    void studentCannotAccessDraftAssessment() throws Exception {
        Assessment assessment = assessmentRepository.save(new Assessment(
                classroom, teacher, "Draft Quiz", "Desc", "Instructions", 30, Instant.now().plus(2, ChronoUnit.DAYS), 1
        ));

        mockMvc.perform(get("/api/v1/assessments/" + assessment.getId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Students never receive answer keys, model answers, or rubrics on published assessments")
    void studentsNeverReceiveAnswerKeys() throws Exception {
        Assessment assessment = assessmentRepository.save(new Assessment(
                classroom, teacher, "Mechanics Test", "Desc", "Instructions", 45, Instant.now().plus(2, ChronoUnit.DAYS), 1
        ));
        assessment.setStatus("PUBLISHED");
        assessment = assessmentRepository.save(assessment);

        Question q = questionRepository.save(new Question(
                assessment, "What is Newton's First Law?", "MCQ", BigDecimal.valueOf(5.0), 1, "Law of inertia", "Give complete definition"
        ));
        AnswerOption opt1 = answerOptionRepository.save(new AnswerOption(q, "Law of Inertia", true, 1));
        AnswerOption opt2 = answerOptionRepository.save(new AnswerOption(q, "F=ma", false, 2));

        // When student views assessment
        MvcResult studentResult = mockMvc.perform(get("/api/v1/assessments/" + assessment.getId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode studentJson = objectMapper.readTree(studentResult.getResponse().getContentAsString());
        JsonNode studentQuestion = studentJson.get("questions").get(0);
        assertThat(studentQuestion.get("modelAnswer").isNull()).isTrue();
        assertThat(studentQuestion.get("rubric").isNull()).isTrue();
        for (JsonNode opt : studentQuestion.get("options")) {
            assertThat(opt.get("isCorrect").isNull()).isTrue();
        }

        // When teacher views assessment, answer keys and rubrics are present
        MvcResult teacherResult = mockMvc.perform(get("/api/v1/assessments/" + assessment.getId())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode teacherJson = objectMapper.readTree(teacherResult.getResponse().getContentAsString());
        JsonNode teacherQuestion = teacherJson.get("questions").get(0);
        assertThat(teacherQuestion.get("modelAnswer").asText()).isEqualTo("Law of inertia");
        assertThat(teacherQuestion.get("rubric").asText()).isEqualTo("Give complete definition");
        assertThat(teacherQuestion.get("options").get(0).get("isCorrect").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Server-side objective grading calculates exact score without trusting client input")
    void objectiveGrading_CalculatesCorrectScore() throws Exception {
        Assessment assessment = createPublishedQuizWithTwoMcqs();

        // 1. Student starts attempt
        MvcResult startResult = mockMvc.perform(post("/api/v1/assessments/" + assessment.getId() + "/start")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andReturn();

        Long attemptId = objectMapper.readTree(startResult.getResponse().getContentAsString()).get("id").asLong();

        // Get questions and option IDs
        List<Question> questions = questionRepository.findByAssessmentIdOrderByOrderIndexAsc(assessment.getId());
        Question q1 = questions.get(0);
        Question q2 = questions.get(1);

        AnswerOption q1Correct = answerOptionRepository.findByQuestionIdOrderByOrderIndexAsc(q1.getId())
                .stream().filter(AnswerOption::isCorrect).findFirst().get();
        AnswerOption q2Wrong = answerOptionRepository.findByQuestionIdOrderByOrderIndexAsc(q2.getId())
                .stream().filter(o -> !o.isCorrect()).findFirst().get();

        // 2. Student saves answers (Q1 correct, Q2 wrong)
        mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/answers")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveAnswerRequest(q1.getId(), q1Correct.getId(), null))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/answers")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveAnswerRequest(q2.getId(), q2Wrong.getId(), null))))
                .andExpect(status().isOk());

        // 3. Student submits attempt
        MvcResult submitResult = mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EVALUATED"))
                .andReturn();

        JsonNode submitJson = objectMapper.readTree(submitResult.getResponse().getContentAsString());
        assertThat(submitJson.get("totalScore").asDouble()).isEqualTo(5.0);
        assertThat(submitJson.get("maxScore").asDouble()).isEqualTo(10.0);
        assertThat(submitJson.get("evaluations")).hasSize(2);

        // Verify Evaluations in database
        var evalQ1 = submitJson.get("evaluations").get(0);
        var evalQ2 = submitJson.get("evaluations").get(1);
        assertThat(evalQ1.get("score").asDouble()).isEqualTo(5.0);
        assertThat(evalQ1.get("status").asText()).isEqualTo("AUTO_GRADED");
        assertThat(evalQ2.get("score").asDouble()).isEqualTo(0.0);
        assertThat(evalQ2.get("status").asText()).isEqualTo("AUTO_GRADED");
    }

    @Test
    @DisplayName("Submit is idempotent: submitting twice does not recalculate or duplicate evaluations")
    void duplicateSubmit_IsIdempotent() throws Exception {
        Assessment assessment = createPublishedQuizWithTwoMcqs();

        MvcResult startResult = mockMvc.perform(post("/api/v1/assessments/" + assessment.getId() + "/start")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn();

        Long attemptId = objectMapper.readTree(startResult.getResponse().getContentAsString()).get("id").asLong();

        // First submit
        MvcResult firstSubmit = mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();

        // Second duplicate submit
        MvcResult secondSubmit = mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json1 = objectMapper.readTree(firstSubmit.getResponse().getContentAsString());
        JsonNode json2 = objectMapper.readTree(secondSubmit.getResponse().getContentAsString());

        assertThat(json1.get("totalScore").asDouble()).isEqualTo(json2.get("totalScore").asDouble());
        assertThat(json1.get("status").asText()).isEqualTo(json2.get("status").asText());
    }

    @Test
    @DisplayName("Attempt limits are strictly enforced: second attempt blocked when maxAttempts=1")
    void attemptLimits_Enforced() throws Exception {
        Assessment assessment = createPublishedQuizWithTwoMcqs(); // maxAttempts = 1

        // Start and submit attempt 1
        MvcResult startResult = mockMvc.perform(post("/api/v1/assessments/" + assessment.getId() + "/start")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn();

        Long attemptId = objectMapper.readTree(startResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/v1/attempts/" + attemptId + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        // Attempt 2 start should fail
        mockMvc.perform(post("/api/v1/assessments/" + assessment.getId() + "/start")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Maximum attempt limit reached (1)"));
    }

    @Test
    @DisplayName("Deadline enforcement: student cannot start attempt if due date has passed")
    void deadlineChecks_RejectsStartAfterDueDate() throws Exception {
        Assessment assessment = assessmentRepository.save(new Assessment(
                classroom, teacher, "Expired Quiz", "Desc", "Instructions", 30, Instant.now().minus(1, ChronoUnit.HOURS), 1
        ));
        assessment.setStatus("PUBLISHED");
        assessment = assessmentRepository.save(assessment);

        Question q = questionRepository.save(new Question(
                assessment, "What is 1+1?", "MCQ", BigDecimal.valueOf(5.0), 1, "2", "Exact match"
        ));
        answerOptionRepository.save(new AnswerOption(q, "2", true, 1));
        answerOptionRepository.save(new AnswerOption(q, "3", false, 2));

        mockMvc.perform(post("/api/v1/assessments/" + assessment.getId() + "/start")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Assessment deadline has passed")));
    }

    private Assessment createPublishedQuizWithTwoMcqs() {
        Assessment assessment = assessmentRepository.save(new Assessment(
                classroom, teacher, "Physics Quiz 1", "Basic kinematics", "Answer all questions", 60,
                Instant.now().plus(3, ChronoUnit.DAYS), 1
        ));
        assessment.setStatus("PUBLISHED");
        assessment = assessmentRepository.save(assessment);

        Question q1 = questionRepository.save(new Question(
                assessment, "Unit of force?", "MCQ", BigDecimal.valueOf(5.0), 1, "Newton", "Correct unit"
        ));
        answerOptionRepository.save(new AnswerOption(q1, "Newton", true, 1));
        answerOptionRepository.save(new AnswerOption(q1, "Joule", false, 2));

        Question q2 = questionRepository.save(new Question(
                assessment, "Unit of energy?", "MCQ", BigDecimal.valueOf(5.0), 2, "Joule", "Correct unit"
        ));
        answerOptionRepository.save(new AnswerOption(q2, "Joule", true, 1));
        answerOptionRepository.save(new AnswerOption(q2, "Newton", false, 2));

        assessment.setTotalMarks(BigDecimal.valueOf(10.0));
        return assessmentRepository.save(assessment);
    }
}
