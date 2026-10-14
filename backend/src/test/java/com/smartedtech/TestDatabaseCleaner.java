package com.smartedtech;

import com.smartedtech.analytics.RemediationPlanRepository;
import com.smartedtech.analytics.WeakConceptRepository;
import com.smartedtech.assessment.AnswerOptionRepository;
import com.smartedtech.assessment.AssessmentAttemptRepository;
import com.smartedtech.assessment.AssessmentRepository;
import com.smartedtech.assessment.GenerationJobRepository;
import com.smartedtech.assessment.QuestionRepository;
import com.smartedtech.assessment.StudentAnswerRepository;
import com.smartedtech.attendance.AttendanceRepository;
import com.smartedtech.auth.RefreshTokenRepository;
import com.smartedtech.chatbot.ChatConversationRepository;
import com.smartedtech.chatbot.ChatMessageRepository;
import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.classroom.ClassroomRepository;
import com.smartedtech.discussion.DiscussionMessageRepository;
import com.smartedtech.evaluation.EvaluationRepository;
import com.smartedtech.material.StudyMaterialRepository;
import com.smartedtech.notification.NotificationRepository;
import com.smartedtech.poll.PollOptionRepository;
import com.smartedtech.poll.PollRepository;
import com.smartedtech.poll.PollVoteRepository;
import com.smartedtech.session.LiveSessionRepository;
import com.smartedtech.user.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class TestDatabaseCleaner {

    private final RemediationPlanRepository remediationPlanRepository;
    private final EvaluationRepository evaluationRepository;
    private final WeakConceptRepository weakConceptRepository;
    private final StudentAnswerRepository studentAnswerRepository;
    private final AssessmentAttemptRepository assessmentAttemptRepository;
    private final AnswerOptionRepository answerOptionRepository;
    private final QuestionRepository questionRepository;
    private final GenerationJobRepository generationJobRepository;
    private final AssessmentRepository assessmentRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatConversationRepository chatConversationRepository;
    private final PollVoteRepository pollVoteRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollRepository pollRepository;
    private final DiscussionMessageRepository discussionMessageRepository;
    private final AttendanceRepository attendanceRepository;
    private final LiveSessionRepository sessionRepository;
    private final StudyMaterialRepository materialRepository;
    private final NotificationRepository notificationRepository;
    private final ClassroomEnrollmentRepository enrollmentRepository;
    private final ClassroomRepository classroomRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final javax.sql.DataSource dataSource;

    public TestDatabaseCleaner(
            RemediationPlanRepository remediationPlanRepository,
            EvaluationRepository evaluationRepository,
            WeakConceptRepository weakConceptRepository,
            StudentAnswerRepository studentAnswerRepository,
            AssessmentAttemptRepository assessmentAttemptRepository,
            AnswerOptionRepository answerOptionRepository,
            QuestionRepository questionRepository,
            GenerationJobRepository generationJobRepository,
            AssessmentRepository assessmentRepository,
            ChatMessageRepository chatMessageRepository,
            ChatConversationRepository chatConversationRepository,
            PollVoteRepository pollVoteRepository,
            PollOptionRepository pollOptionRepository,
            PollRepository pollRepository,
            DiscussionMessageRepository discussionMessageRepository,
            AttendanceRepository attendanceRepository,
            LiveSessionRepository sessionRepository,
            StudyMaterialRepository materialRepository,
            NotificationRepository notificationRepository,
            ClassroomEnrollmentRepository enrollmentRepository,
            ClassroomRepository classroomRepository,
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            javax.sql.DataSource dataSource) {
        this.remediationPlanRepository = remediationPlanRepository;
        this.evaluationRepository = evaluationRepository;
        this.weakConceptRepository = weakConceptRepository;
        this.studentAnswerRepository = studentAnswerRepository;
        this.assessmentAttemptRepository = assessmentAttemptRepository;
        this.answerOptionRepository = answerOptionRepository;
        this.questionRepository = questionRepository;
        this.generationJobRepository = generationJobRepository;
        this.assessmentRepository = assessmentRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatConversationRepository = chatConversationRepository;
        this.pollVoteRepository = pollVoteRepository;
        this.pollOptionRepository = pollOptionRepository;
        this.pollRepository = pollRepository;
        this.discussionMessageRepository = discussionMessageRepository;
        this.attendanceRepository = attendanceRepository;
        this.sessionRepository = sessionRepository;
        this.materialRepository = materialRepository;
        this.notificationRepository = notificationRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.classroomRepository = classroomRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.dataSource = dataSource;
    }

    public void clean() {
        verifyTestDatabase();
        chatMessageRepository.deleteAll();
        chatConversationRepository.deleteAll();
        remediationPlanRepository.deleteAll();
        evaluationRepository.deleteAll();
        weakConceptRepository.deleteAll();
        studentAnswerRepository.deleteAll();
        assessmentAttemptRepository.deleteAll();
        answerOptionRepository.deleteAll();
        questionRepository.deleteAll();
        generationJobRepository.deleteAll();
        assessmentRepository.deleteAll();
        pollVoteRepository.deleteAll();
        pollOptionRepository.deleteAll();
        pollRepository.deleteAll();
        discussionMessageRepository.deleteAll();
        attendanceRepository.deleteAll();
        sessionRepository.deleteAll();
        materialRepository.deleteAll();
        notificationRepository.deleteAll();
        enrollmentRepository.deleteAll();
        classroomRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    public void verifyTestDatabase() {
        if (dataSource == null) {
            throw new IllegalStateException("SAFETY GUARD: DataSource is null in TestDatabaseCleaner");
        }
        try (java.sql.Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            String catalog = connection.getCatalog();
            if (!isTestSchema(url, catalog)) {
                throw new IllegalStateException(
                        "SAFETY GUARD REJECTION: TestDatabaseCleaner refused to run on non-test database! " +
                        "Schema: '" + catalog + "', URL: '" + url + "'. Schema must end with '_test'."
                );
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to verify test database safety guard", e);
        }
    }

    public static boolean isTestSchema(String url, String catalog) {
        String target = catalog != null ? catalog.trim() : "";
        if (target.isEmpty() && url != null) {
            int qIdx = url.indexOf('?');
            String cleanUrl = qIdx != -1 ? url.substring(0, qIdx) : url;
            int slashIdx = cleanUrl.lastIndexOf('/');
            if (slashIdx != -1) {
                target = cleanUrl.substring(slashIdx + 1).trim();
            }
        }
        return target.toLowerCase().endsWith("_test");
    }
}
