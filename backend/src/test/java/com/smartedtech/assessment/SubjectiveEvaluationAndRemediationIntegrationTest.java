package com.smartedtech.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedtech.TestDatabaseCleaner;
import com.smartedtech.ai.AiResponse;
import com.smartedtech.ai.AiService;
import com.smartedtech.analytics.RemediationPlan;
import com.smartedtech.analytics.RemediationPlanRepository;
import com.smartedtech.analytics.WeakConceptRepository;
import com.smartedtech.analytics.dto.RemediationPlanDto;
import com.smartedtech.classroom.Classroom;
import com.smartedtech.classroom.ClassroomEnrollment;
import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.classroom.ClassroomRepository;
import com.smartedtech.evaluation.Evaluation;
import com.smartedtech.evaluation.EvaluationRepository;
import com.smartedtech.evaluation.dto.UpdateEvaluationRequest;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SubjectiveEvaluationAndRemediationIntegrationTest {

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
    private AssessmentAttemptRepository attemptRepository;

    @Autowired
    private StudentAnswerRepository studentAnswerRepository;

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private GenerationJobRepository generationJobRepository;

    @Autowired
    private GenerationJobService generationJobService;

    @Autowired
    private RemediationPlanRepository remediationPlanRepository;

    @Autowired
    private WeakConceptRepository weakConceptRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    @MockBean
    private AiService aiService;

    private User teacher;
    private User studentA;
    private User studentB;

    private String teacherToken;
    private String studentAToken;
    private String studentBToken;

    private Classroom classroom;
    private Assessment assessment;
    private Question subjectiveQuestion;
    private AssessmentAttempt attempt;
    private StudentAnswer studentAnswer;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();

        teacher = userRepository.save(new User("teacher@test.com", passwordEncoder.encode("Pass12345678!"), "Teacher User", Role.TEACHER));
        studentA = userRepository.save(new User("student.a@test.com", passwordEncoder.encode("Pass12345678!"), "Student Alpha", Role.STUDENT));
        studentB = userRepository.save(new User("student.b@test.com", passwordEncoder.encode("Pass12345678!"), "Student Beta", Role.STUDENT));

        teacherToken = jwtService.generateAccessToken(teacher);
        studentAToken = jwtService.generateAccessToken(studentA);
        studentBToken = jwtService.generateAccessToken(studentB);

        classroom = classroomRepository.save(new Classroom("Mechanics 201", "Advanced Mechanics", "MECH2011", teacher));
        enrollmentRepository.save(new ClassroomEnrollment(classroom, studentA));

        assessment = new Assessment(
                classroom, teacher, "Newtonian Mechanics Quiz", "Subjective exam",
                "Answer thoroughly", 45, Instant.now().plus(2, ChronoUnit.DAYS), 1
        );
        assessment.setStatus("PUBLISHED");
        assessment.setTotalMarks(new BigDecimal("10.00"));
        assessment = assessmentRepository.save(assessment);

        subjectiveQuestion = new Question(
                assessment,
                "State and explain Newton's Third Law of Motion with an everyday example.",
                "SHORT_ANSWER",
                new BigDecimal("10.00"),
                1,
                "For every action, there is an equal and opposite reaction.",
                "Accuracy: 5 marks, Example: 3 marks, Clarity: 2 marks"
        );
        subjectiveQuestion = questionRepository.save(subjectiveQuestion);

        attempt = new AssessmentAttempt(assessment, studentA, 1);
        attempt.setStatus("SUBMITTED");
        attempt = attemptRepository.save(attempt);

        studentAnswer = new StudentAnswer(attempt, subjectiveQuestion, null, "When a swimmer pushes against the wall, the wall pushes the swimmer forward.");
        studentAnswer = studentAnswerRepository.save(studentAnswer);
        attempt.setAnswers(List.of(studentAnswer));
    }

    @Test
    @DisplayName("Subjective AI evaluation parses valid JSON, clamps score to maxMarks, and marks evaluation AI_SUGGESTED")
    void testSubjectiveAiEvaluationValidFlow() {
        String validAiJson = """
                {
                  "suggestedMarks": 8.5,
                  "explanation": "Correct statement of the law and good swimming pool example.",
                  "strengths": "Clear action-reaction identification",
                  "missingConcepts": "Could mention that forces act on different bodies",
                  "weakConceptTags": ["Action-Reaction Pairs"]
                }
                """;

        when(aiService.evaluateSubjective(any(), any(), any(), any()))
                .thenReturn(new AiResponse(validAiJson, "GEMINI"));
        when(aiService.generateRemediation(any()))
                .thenReturn(new AiResponse("{\"summary\":\"Review action-reaction pair mechanics\",\"recommendations\":[]}", "GEMINI"));

        GenerationJob job = generationJobService.createSubjectiveEvaluationJob(studentA, attempt);
        generationJobService.executeSubjectiveEvaluationAsync(job.getId());
        waitForJob(job.getId());

        // Check evaluation in MySQL
        Evaluation eval = evaluationRepository.findByAttemptIdAndQuestionId(attempt.getId(), subjectiveQuestion.getId())
                .orElseThrow(() -> new AssertionError("Evaluation was not created"));

        assertThat(eval.getScore()).isEqualByComparingTo(new BigDecimal("8.50"));
        assertThat(eval.getMaxMarks()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(eval.getStatus()).isEqualTo("AI_SUGGESTED");
        assertThat(eval.getProviderUsed()).isEqualTo("GEMINI");
        assertThat(eval.getFeedback()).contains("Correct statement");

        // Verify weak concept was recorded
        assertThat(weakConceptRepository.findByStudentIdAndAssessmentId(studentA.getId(), assessment.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Provisional AI_SUGGESTED evaluation stays provisional until teacher accepts or edits it")
    void testTeacherAcceptsOrOverridesAiSuggestedEvaluation() throws Exception {
        // Setup existing provisional AI_SUGGESTED evaluation
        Evaluation eval = new Evaluation();
        eval.setAttempt(attempt);
        eval.setQuestion(subjectiveQuestion);
        eval.setScore(new BigDecimal("8.00"));
        eval.setMaxMarks(new BigDecimal("10.00"));
        eval.setStatus("AI_SUGGESTED");
        eval.setFeedback("Initial AI evaluation");
        eval = evaluationRepository.save(eval);

        // Teacher reviews and edits marks to 9.50
        UpdateEvaluationRequest updateReq = new UpdateEvaluationRequest(
                new BigDecimal("9.50"),
                "Teacher verified: excellent physical intuition demonstrated.",
                "Clear example",
                null
        );

        mockMvc.perform(put("/api/v1/evaluations/" + eval.getId())
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(9.5))
                .andExpect(jsonPath("$.status").value("OVERRIDDEN"))
                .andExpect(jsonPath("$.feedback").value("Teacher verified: excellent physical intuition demonstrated."));

        Evaluation updated = evaluationRepository.findById(eval.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("OVERRIDDEN");
        assertThat(updated.getScore()).isEqualByComparingTo(new BigDecimal("9.50"));

        AssessmentAttempt updatedAttempt = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(updatedAttempt.getStatus()).isEqualTo("EVALUATED");
        assertThat(updatedAttempt.getTotalScore()).isEqualByComparingTo(new BigDecimal("9.50"));
    }

    @Test
    @DisplayName("Score range validation: rejects score exceeding maxMarks with 400 Bad Request")
    void testScoreRangeValidationRejectsExceedingMaxMarks() throws Exception {
        Evaluation eval = new Evaluation();
        eval.setAttempt(attempt);
        eval.setQuestion(subjectiveQuestion);
        eval.setScore(new BigDecimal("5.00"));
        eval.setMaxMarks(new BigDecimal("10.00"));
        eval.setStatus("AI_SUGGESTED");
        eval = evaluationRepository.save(eval);

        // Teacher submits 12.00 on a 10.00 max mark question -> 400 Bad Request
        UpdateEvaluationRequest invalidReq = new UpdateEvaluationRequest(
                new BigDecimal("12.00"),
                "Too high",
                null,
                null
        );

        mockMvc.perform(put("/api/v1/evaluations/" + eval.getId())
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("cannot exceed max marks")));
    }

    @Test
    @DisplayName("Malformed AI provider output sets generation job status to FAILED with error details")
    void testMalformedAiProviderJsonHandledProperly() throws Exception {
        when(aiService.evaluateSubjective(any(), any(), any(), any()))
                .thenReturn(new AiResponse("This is completely unparseable text from upstream model", "GROQ"));

        GenerationJob job = generationJobService.createSubjectiveEvaluationJob(studentA, attempt);
        generationJobService.executeSubjectiveEvaluationAsync(job.getId());
        waitForJob(job.getId());

        GenerationJob updatedJob = generationJobRepository.findById(job.getId()).orElseThrow();
        assertThat(updatedJob.getStatus()).isEqualTo("FAILED");
        assertThat(updatedJob.getErrorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(updatedJob.getErrorMessage()).contains("AI evaluation output is not valid JSON");
    }

    @Test
    @DisplayName("Remediation plan generation and access: student and teacher can access; foreign student blocked")
    void testRemediationPlanAccess() throws Exception {
        RemediationPlan plan = new RemediationPlan();
        plan.setAttempt(attempt);
        plan.setStudent(studentA);
        plan.setAssessment(assessment);
        plan.setSummary("Master action-reaction pairs by working through friction problems.");
        plan.setRecommendationsJson("[{\"action\":\"Read Section 4.2\"}]");
        plan.setProviderUsed("GEMINI");
        remediationPlanRepository.save(plan);

        // 1. Student A can access own remediation plan
        mockMvc.perform(get("/api/v1/attempts/" + attempt.getId() + "/remediation")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Master action-reaction pairs by working through friction problems."))
                .andExpect(jsonPath("$.recommendationsJson").value(org.hamcrest.Matchers.containsString("Read Section 4.2")))
                .andExpect(jsonPath("$.providerUsed").value("GEMINI"));

        // 2. Teacher can access student A's remediation plan
        mockMvc.perform(get("/api/v1/attempts/" + attempt.getId() + "/remediation")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Master action-reaction pairs by working through friction problems."));

        // 3. Foreign student B is blocked (403 Forbidden)
        mockMvc.perform(get("/api/v1/attempts/" + attempt.getId() + "/remediation")
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden());
    }

    private void waitForJob(Long jobId) {
        for (int i = 0; i < 40; i++) {
            GenerationJob job = generationJobRepository.findById(jobId).orElse(null);
            if (job != null && !"PENDING".equalsIgnoreCase(job.getStatus()) && !"RUNNING".equalsIgnoreCase(job.getStatus())) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
