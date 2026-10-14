package com.smartedtech.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedtech.TestDatabaseCleaner;
import com.smartedtech.assessment.*;
import com.smartedtech.assessment.dto.CreateAssessmentRequest;
import com.smartedtech.assessment.dto.CreateQuestionRequest;
import com.smartedtech.assessment.dto.GenerateQuestionsRequest;
import com.smartedtech.assessment.dto.UpdateAssessmentRequest;
import com.smartedtech.chatbot.ChatConversation;
import com.smartedtech.chatbot.ChatConversationRepository;
import com.smartedtech.chatbot.dto.CreateConversationRequest;
import com.smartedtech.chatbot.dto.SendMessageRequest;
import com.smartedtech.classroom.Classroom;
import com.smartedtech.classroom.ClassroomEnrollment;
import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.classroom.ClassroomRepository;
import com.smartedtech.classroom.dto.CreateClassroomRequest;
import com.smartedtech.classroom.dto.EnrollStudentRequest;
import com.smartedtech.classroom.dto.UpdateClassroomRequest;
import com.smartedtech.evaluation.Evaluation;
import com.smartedtech.evaluation.EvaluationRepository;
import com.smartedtech.evaluation.dto.UpdateEvaluationRequest;
import com.smartedtech.material.StudyMaterial;
import com.smartedtech.material.StudyMaterialRepository;
import com.smartedtech.material.dto.UpdateMaterialRequest;
import com.smartedtech.poll.Poll;
import com.smartedtech.poll.PollOption;
import com.smartedtech.poll.PollRepository;
import com.smartedtech.poll.dto.CastVoteRequest;
import com.smartedtech.poll.dto.CreatePollRequest;
import com.smartedtech.session.LiveSession;
import com.smartedtech.session.LiveSessionRepository;
import com.smartedtech.session.dto.CreateSessionRequest;
import com.smartedtech.session.dto.UpdateSessionRequest;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthorizationMatrixIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

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
    private AssessmentRepository assessmentRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private AssessmentAttemptRepository attemptRepository;

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private PollRepository pollRepository;

    @Autowired
    private ChatConversationRepository chatConversationRepository;

    @Autowired
    private GenerationJobRepository generationJobRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User admin;
    private User teacherA;
    private User teacherB;
    private User studentA;
    private User studentB;

    private String adminToken;
    private String teacherAToken;
    private String teacherBToken;
    private String studentAToken;
    private String studentBToken;

    private Classroom classroomA;
    private LiveSession sessionA;
    private StudyMaterial materialA;
    private Assessment assessmentA;
    private Question questionA;
    private AssessmentAttempt attemptA;
    private Evaluation evaluationA;
    private Poll pollA;
    private ChatConversation conversationA;
    private GenerationJob jobA;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();

        // Create Users
        admin = userRepository.save(new User("admin@smartedtech.test", passwordEncoder.encode("AdminPass123!"), "Admin User", Role.ADMIN));
        teacherA = userRepository.save(new User("teacherA@smartedtech.test", passwordEncoder.encode("TeachPass123!"), "Teacher A", Role.TEACHER));
        teacherB = userRepository.save(new User("teacherB@smartedtech.test", passwordEncoder.encode("TeachPass123!"), "Teacher B", Role.TEACHER));
        studentA = userRepository.save(new User("studentA@smartedtech.test", passwordEncoder.encode("StudentPass123!"), "Student A", Role.STUDENT));
        studentB = userRepository.save(new User("studentB@smartedtech.test", passwordEncoder.encode("StudentPass123!"), "Student B", Role.STUDENT));

        adminToken = jwtService.generateAccessToken(admin);
        teacherAToken = jwtService.generateAccessToken(teacherA);
        teacherBToken = jwtService.generateAccessToken(teacherB);
        studentAToken = jwtService.generateAccessToken(studentA);
        studentBToken = jwtService.generateAccessToken(studentB);

        // Create Classroom A for Teacher A
        classroomA = classroomRepository.save(new Classroom("Physics 101", "Intro Physics", "PHYS101A", teacherA));

        // Enroll Student A in Classroom A (Student B remains unenrolled)
        enrollmentRepository.save(new ClassroomEnrollment(classroomA, studentA));

        // Create Session A
        sessionA = sessionRepository.save(new LiveSession(
                classroomA,
                "Kinematics Lecture",
                "Kinematics Intro",
                Instant.now().plus(1, ChronoUnit.HOURS),
                Instant.now().plus(2, ChronoUnit.HOURS),
                "PHYS101-KIN-1"
        ));

        // Create Material A
        materialA = materialRepository.save(new StudyMaterial(
                classroomA,
                teacherA,
                "Kinematics Notes",
                "PDF Notes",
                "notes.pdf",
                "safe-stored-notes.pdf",
                1024L,
                "application/pdf"
        ));

        // Create Assessment A
        assessmentA = new Assessment(
                classroomA,
                teacherA,
                "Physics Quiz 1",
                "First Quiz",
                "Answer carefully",
                30,
                Instant.now().plus(2, ChronoUnit.DAYS),
                2
        );
        assessmentA.setStatus("PUBLISHED");
        assessmentA.setTotalMarks(new BigDecimal("10.00"));
        assessmentA = assessmentRepository.save(assessmentA);

        // Create Question A
        questionA = new Question(assessmentA, "What is velocity?", "MCQ", new BigDecimal("5.00"), 1, "Speed in direction", "None");
        questionA = questionRepository.save(questionA);

        // Create Attempt A for Student A
        attemptA = attemptRepository.save(new AssessmentAttempt(assessmentA, studentA, 1));

        // Create Evaluation A
        evaluationA = new Evaluation();
        evaluationA.setAttempt(attemptA);
        evaluationA.setQuestion(questionA);
        evaluationA.setScore(new BigDecimal("4.00"));
        evaluationA.setMaxMarks(new BigDecimal("5.00"));
        evaluationA.setStatus("MANUAL");
        evaluationA.setFeedback("Good attempt");
        evaluationA = evaluationRepository.save(evaluationA);

        // Create Poll A
        pollA = new Poll(sessionA, teacherA, "Is acceleration constant?", false);
        pollA.setStatus("OPEN");
        PollOption opt1 = new PollOption(pollA, "Yes", 0);
        PollOption opt2 = new PollOption(pollA, "No", 1);
        pollA.setOptions(List.of(opt1, opt2));
        pollA = pollRepository.save(pollA);

        // Create Chat Conversation A for Student A
        conversationA = chatConversationRepository.save(new ChatConversation(studentA, classroomA, materialA, "Kinematics Help"));

        // Create GenerationJob A for Teacher A
        jobA = new GenerationJob("QUESTION_GENERATION", teacherA);
        jobA.setMaterial(materialA);
        jobA = generationJobRepository.save(jobA);
    }

    @Test
    @DisplayName("Admin Endpoints: Teachers and Students cannot access (403 Forbidden)")
    void testAdminEndpointsForbiddenForNonAdmin() throws Exception {
        // GET /api/v1/admin/users
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + teacherAToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isForbidden());

        // GET /api/v1/admin/stats
        mockMvc.perform(get("/api/v1/admin/stats")
                        .header("Authorization", "Bearer " + teacherAToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/stats")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isForbidden());

        // Unauthenticated access returns 401
        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("System Integrations: Students cannot access (403 Forbidden)")
    void testSystemIntegrationsForbiddenForStudents() throws Exception {
        mockMvc.perform(get("/api/v1/system/integrations")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isForbidden());

        // Teacher and Admin can access
        mockMvc.perform(get("/api/v1/system/integrations")
                        .header("Authorization", "Bearer " + teacherAToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/system/integrations")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Classrooms: Student cannot create; Teacher B cannot edit or archive Teacher A's classroom")
    void testClassroomWrongRoleAndOwner() throws Exception {
        // Student cannot create classroom
        CreateClassroomRequest req = new CreateClassroomRequest("Biology", "Desc");
        mockMvc.perform(post("/api/v1/classrooms")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        // Teacher B cannot edit Teacher A's classroom
        UpdateClassroomRequest updateReq = new UpdateClassroomRequest("Physics Modified", "New Desc");
        mockMvc.perform(put("/api/v1/classrooms/" + classroomA.getId())
                        .header("Authorization", "Bearer " + teacherBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        // Teacher B cannot archive Teacher A's classroom
        mockMvc.perform(patch("/api/v1/classrooms/" + classroomA.getId() + "/archive")
                        .header("Authorization", "Bearer " + teacherBToken))
                .andExpect(status().isForbidden());

        // Teacher B cannot enroll student into Teacher A's classroom
        EnrollStudentRequest enrollReq = new EnrollStudentRequest(null, studentB.getId());
        mockMvc.perform(post("/api/v1/classrooms/" + classroomA.getId() + "/students")
                        .header("Authorization", "Bearer " + teacherBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enrollReq)))
                .andExpect(status().isForbidden());

        // Teacher B cannot remove student from Teacher A's classroom
        mockMvc.perform(delete("/api/v1/classrooms/" + classroomA.getId() + "/students/" + studentA.getId())
                        .header("Authorization", "Bearer " + teacherBToken))
                .andExpect(status().isForbidden());

        // Student B (unenrolled) cannot view details of Classroom A
        mockMvc.perform(get("/api/v1/classrooms/" + classroomA.getId())
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Live Sessions: Student cannot create; Teacher B cannot modify Teacher A's session or view attendance")
    void testSessionWrongRoleAndOwner() throws Exception {
        // Student cannot create session
        CreateSessionRequest sessionReq = new CreateSessionRequest(
                "Student Session",
                "Desc",
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS)
        );
        mockMvc.perform(post("/api/v1/classrooms/" + classroomA.getId() + "/sessions")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sessionReq)))
                .andExpect(status().isForbidden());

        // Teacher B cannot create session in Teacher A's classroom
        mockMvc.perform(post("/api/v1/classrooms/" + classroomA.getId() + "/sessions")
                        .header("Authorization", "Bearer " + teacherBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sessionReq)))
                .andExpect(status().isForbidden());

        // Teacher B cannot update Teacher A's session
        UpdateSessionRequest updateReq = new UpdateSessionRequest(
                "Updated Kinematics",
                "Updated Desc",
                Instant.now().plus(2, ChronoUnit.DAYS),
                Instant.now().plus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS)
        );
        mockMvc.perform(put("/api/v1/sessions/" + sessionA.getId())
                        .header("Authorization", "Bearer " + teacherBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        // Teacher B cannot cancel Teacher A's session
        mockMvc.perform(patch("/api/v1/sessions/" + sessionA.getId() + "/cancel")
                        .header("Authorization", "Bearer " + teacherBToken))
                .andExpect(status().isForbidden());

        // Teacher B cannot view attendance of Teacher A's session
        mockMvc.perform(get("/api/v1/sessions/" + sessionA.getId() + "/attendance")
                        .header("Authorization", "Bearer " + teacherBToken))
                .andExpect(status().isForbidden());

        // Student B (unenrolled) cannot view or join session
        mockMvc.perform(get("/api/v1/sessions/" + sessionA.getId())
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/sessions/" + sessionA.getId() + "/join")
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Study Materials: Student cannot upload; Teacher B cannot edit or delete Teacher A's material")
    void testMaterialWrongRoleAndOwner() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "%PDF-1.4 sample content".getBytes());

        // Student cannot upload material
        mockMvc.perform(multipart("/api/v1/classrooms/" + classroomA.getId() + "/materials")
                        .file(file)
                        .param("title", "Student Material")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isForbidden());

        // Teacher B cannot upload material to Teacher A's classroom
        mockMvc.perform(multipart("/api/v1/classrooms/" + classroomA.getId() + "/materials")
                        .file(file)
                        .param("title", "Teacher B Material")
                        .header("Authorization", "Bearer " + teacherBToken))
                .andExpect(status().isForbidden());

        // Teacher B cannot update Teacher A's material
        UpdateMaterialRequest updateReq = new UpdateMaterialRequest("Renamed", "New desc");
        mockMvc.perform(put("/api/v1/materials/" + materialA.getId())
                        .header("Authorization", "Bearer " + teacherBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        // Teacher B cannot delete Teacher A's material
        mockMvc.perform(delete("/api/v1/materials/" + materialA.getId())
                        .header("Authorization", "Bearer " + teacherBToken))
                .andExpect(status().isForbidden());

        // Unenrolled Student B cannot view or download Teacher A's material
        mockMvc.perform(get("/api/v1/materials/" + materialA.getId())
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/materials/" + materialA.getId() + "/download")
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Assessments: Student cannot manage; Teacher B cannot modify Teacher A's assessment or questions")
    void testAssessmentWrongRoleAndOwner() throws Exception {
        // Student cannot create assessment
        CreateAssessmentRequest assReq = new CreateAssessmentRequest(
                "Student Assessment",
                "Desc",
                "Instructions",
                30,
                Instant.now().plus(1, ChronoUnit.DAYS),
                2
        );
        mockMvc.perform(post("/api/v1/classrooms/" + classroomA.getId() + "/assessments")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assReq)))
                .andExpect(status().isForbidden());

        // Teacher B cannot update Teacher A's assessment
        UpdateAssessmentRequest updateReq = new UpdateAssessmentRequest(
                "Renamed Assessment",
                "Updated Desc",
                "Updated Inst",
                45,
                Instant.now().plus(2, ChronoUnit.DAYS),
                3,
                "DRAFT"
        );
        mockMvc.perform(put("/api/v1/assessments/" + assessmentA.getId())
                        .header("Authorization", "Bearer " + teacherBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        // Teacher B cannot add question to Teacher A's assessment
        CreateQuestionRequest qReq = new CreateQuestionRequest(
                "New Question",
                "MCQ",
                new BigDecimal("5.00"),
                2,
                null,
                null,
                List.of()
        );
        mockMvc.perform(post("/api/v1/assessments/" + assessmentA.getId() + "/questions")
                        .header("Authorization", "Bearer " + teacherBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(qReq)))
                .andExpect(status().isForbidden());

        // Teacher B cannot publish or close Teacher A's assessment
        mockMvc.perform(put("/api/v1/assessments/" + assessmentA.getId() + "/publish")
                        .header("Authorization", "Bearer " + teacherBToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/assessments/" + assessmentA.getId() + "/archive")
                        .header("Authorization", "Bearer " + teacherBToken))
                .andExpect(status().isForbidden());

        // Unenrolled Student B cannot view Assessment A
        mockMvc.perform(get("/api/v1/assessments/" + assessmentA.getId())
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Attempts & Grading: Teacher cannot start attempt; Student B cannot access Student A's attempt; Teacher B cannot edit evaluation")
    void testAttemptAndEvaluationWrongRoleAndOwner() throws Exception {
        // Teacher cannot start an attempt
        mockMvc.perform(post("/api/v1/assessments/" + assessmentA.getId() + "/start")
                        .header("Authorization", "Bearer " + teacherAToken))
                .andExpect(status().isForbidden());

        // Student B (unenrolled) cannot view or submit Student A's attempt
        mockMvc.perform(get("/api/v1/attempts/" + attemptA.getId())
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/attempts/" + attemptA.getId() + "/submit")
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden());

        // Teacher B cannot view all attempts of Teacher A's assessment
        mockMvc.perform(get("/api/v1/assessments/" + assessmentA.getId() + "/attempts")
                        .header("Authorization", "Bearer " + teacherBToken))
                .andExpect(status().isForbidden());

        // Teacher B cannot update evaluation for Teacher A's student
        UpdateEvaluationRequest evalReq = new UpdateEvaluationRequest(new BigDecimal("5.00"), "Excellent", "All", null);
        mockMvc.perform(put("/api/v1/evaluations/" + evaluationA.getId())
                        .header("Authorization", "Bearer " + teacherBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(evalReq)))
                .andExpect(status().isForbidden());

        // Student cannot update evaluation
        mockMvc.perform(put("/api/v1/evaluations/" + evaluationA.getId())
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(evalReq)))
                .andExpect(status().isForbidden());

        // Student B cannot view Student A's weak concepts
        mockMvc.perform(get("/api/v1/students/" + studentA.getId() + "/weak-concepts")
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Polls: Student cannot create/manage polls; Teacher B cannot control Teacher A's polls")
    void testPollWrongRoleAndOwner() throws Exception {
        // Student cannot create poll
        CreatePollRequest pollReq = new CreatePollRequest("Is physics fun?", List.of("Yes", "No"), true);
        mockMvc.perform(post("/api/v1/sessions/" + sessionA.getId() + "/polls")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pollReq)))
                .andExpect(status().isForbidden());

        // Teacher B cannot create poll in Teacher A's session
        mockMvc.perform(post("/api/v1/sessions/" + sessionA.getId() + "/polls")
                        .header("Authorization", "Bearer " + teacherBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pollReq)))
                .andExpect(status().isForbidden());

        // Teacher B cannot close Teacher A's poll
        mockMvc.perform(patch("/api/v1/sessions/" + sessionA.getId() + "/polls/" + pollA.getId() + "/close")
                        .header("Authorization", "Bearer " + teacherBToken))
                .andExpect(status().isForbidden());

        // Teacher B cannot change results visibility on Teacher A's poll
        mockMvc.perform(patch("/api/v1/sessions/" + sessionA.getId() + "/polls/" + pollA.getId() + "/visibility")
                        .header("Authorization", "Bearer " + teacherBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("visible", true))))
                .andExpect(status().isForbidden());

        // Student B (unenrolled) cannot vote on Teacher A's poll
        CastVoteRequest voteReq = new CastVoteRequest(pollA.getOptions().get(0).getId());
        mockMvc.perform(post("/api/v1/sessions/" + sessionA.getId() + "/polls/" + pollA.getId() + "/vote")
                        .header("Authorization", "Bearer " + studentBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voteReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Chatbot: Teacher cannot use student chatbot; Student B cannot access Student A's conversation")
    void testChatbotWrongRoleAndOwner() throws Exception {
        // Teacher cannot create chatbot conversation
        CreateConversationRequest convReq = new CreateConversationRequest("Teacher Chat", classroomA.getId(), materialA.getId());
        mockMvc.perform(post("/api/v1/chat/conversations")
                        .header("Authorization", "Bearer " + teacherAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(convReq)))
                .andExpect(status().isForbidden());

        // Student B cannot get Student A's conversation
        mockMvc.perform(get("/api/v1/chat/conversations/" + conversationA.getId())
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isNotFound()); // Scoped by studentId returns 404

        // Student B cannot send messages to Student A's conversation
        SendMessageRequest msgReq = new SendMessageRequest("Explain velocity please");
        mockMvc.perform(post("/api/v1/chat/conversations/" + conversationA.getId() + "/messages")
                        .header("Authorization", "Bearer " + studentBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(msgReq)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AI Question Generation: Student cannot request AI generation; Teacher B cannot view Teacher A's job")
    void testAiQuestionGenerationWrongRoleAndOwner() throws Exception {
        // Student cannot call /api/v1/ai/generate-questions
        GenerateQuestionsRequest genReq = new GenerateQuestionsRequest(materialA.getId(), 5, "MCQ", "EASY", null);
        mockMvc.perform(post("/api/v1/ai/generate-questions")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(genReq)))
                .andExpect(status().isForbidden());

        // Teacher B cannot view Teacher A's generation job
        mockMvc.perform(get("/api/v1/ai/jobs/" + jobA.getId())
                        .header("Authorization", "Bearer " + teacherBToken))
                .andExpect(status().isForbidden());
    }
}
