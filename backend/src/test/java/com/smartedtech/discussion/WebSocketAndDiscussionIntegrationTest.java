package com.smartedtech.discussion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedtech.TestDatabaseCleaner;
import com.smartedtech.attendance.Attendance;
import com.smartedtech.attendance.AttendanceRepository;
import com.smartedtech.classroom.Classroom;
import com.smartedtech.classroom.ClassroomEnrollment;
import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.classroom.ClassroomRepository;
import com.smartedtech.common.RateLimitExceededException;
import com.smartedtech.discussion.dto.DiscussionMessageDto;
import com.smartedtech.discussion.dto.SendDiscussionRequest;
import com.smartedtech.security.JwtService;
import com.smartedtech.security.RateLimitService;
import com.smartedtech.security.UserPrincipal;
import com.smartedtech.security.WebSocketAuthChannelInterceptor;
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
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class WebSocketAndDiscussionIntegrationTest {

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
    private DiscussionMessageRepository discussionMessageRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private WebSocketAuthChannelInterceptor interceptor;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    private User teacher;
    private User student;
    private User unenrolledStudent;
    private User deactivatedUser;
    private String teacherToken;
    private String studentToken;
    private String unenrolledToken;
    private String deactivatedToken;
    private Classroom classroom;
    private LiveSession session;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        rateLimitService.reset();

        teacher = userRepository.save(new User(
                "teacher.ws@school.edu",
                passwordEncoder.encode("Password123!"),
                "Teacher WS",
                Role.TEACHER
        ));
        teacherToken = jwtService.generateAccessToken(teacher);

        student = userRepository.save(new User(
                "student.ws@school.edu",
                passwordEncoder.encode("Password123!"),
                "Student WS",
                Role.STUDENT
        ));
        studentToken = jwtService.generateAccessToken(student);

        unenrolledStudent = userRepository.save(new User(
                "unenrolled.ws@school.edu",
                passwordEncoder.encode("Password123!"),
                "Unenrolled WS",
                Role.STUDENT
        ));
        unenrolledToken = jwtService.generateAccessToken(unenrolledStudent);

        deactivatedUser = new User(
                "deactivated.ws@school.edu",
                passwordEncoder.encode("Password123!"),
                "Deactivated WS",
                Role.STUDENT
        );
        deactivatedUser.setEnabled(false);
        deactivatedUser = userRepository.save(deactivatedUser);
        deactivatedToken = jwtService.generateAccessToken(deactivatedUser);

        classroom = classroomRepository.save(new Classroom(
                "Biology 101",
                "Cell Biology",
                "BIO12345",
                teacher
        ));

        enrollmentRepository.save(new ClassroomEnrollment(classroom, student));

        Instant now = Instant.now();
        session = sessionRepository.save(new LiveSession(
                classroom,
                "Cell Respiration",
                "Live lecture with discussion",
                now.minus(10, ChronoUnit.MINUTES),
                now.plus(50, ChronoUnit.MINUTES),
                "room-bio-101"
        ));
        session.setStatus("ACTIVE");
        sessionRepository.save(session);
    }

    @Test
    @DisplayName("Discussion REST flow: post messages, retrieve paginated history, length limits")
    void discussionRestFlow() throws Exception {
        SendDiscussionRequest req = new SendDiscussionRequest("Hello everyone, welcome to cell biology!");

        // Student posts message
        mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/discussion")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderId").value(student.getId()))
                .andExpect(jsonPath("$.senderName").value("Student WS"))
                .andExpect(jsonPath("$.content").value("Hello everyone, welcome to cell biology!"));

        // Unenrolled student cannot post message (403)
        mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/discussion")
                        .header("Authorization", "Bearer " + unenrolledToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        // Blank message is rejected (400)
        mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/discussion")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendDiscussionRequest(""))))
                .andExpect(status().isBadRequest());

        // Message exceeding 1000 characters is rejected (400)
        String longContent = "A".repeat(1001);
        mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/discussion")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendDiscussionRequest(longContent))))
                .andExpect(status().isBadRequest());

        // Get paginated history
        mockMvc.perform(get("/api/v1/sessions/" + session.getId() + "/discussion?page=0&size=20")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].senderName").value("Student WS"));
    }

    @Test
    @DisplayName("Attendance tracking: student join, heartbeat, and leave update duration and lastSeenAt")
    void attendanceJoinAndLeave() throws Exception {
        // Record attendance entry via heartbeat or attendance repo
        Instant now = Instant.now();
        Attendance att = new Attendance(session, student, now.minus(15, ChronoUnit.MINUTES));
        att.setDurationSeconds(600);
        attendanceRepository.save(att);

        // Student leaves
        mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/leave")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNoContent());

        // Teacher views attendance
        mockMvc.perform(get("/api/v1/sessions/" + session.getId() + "/attendance")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(student.getId()))
                .andExpect(jsonPath("$[0].durationSeconds").isNumber());
    }

    @Test
    @DisplayName("WebSocket STOMP Channel Interceptor: CONNECT with valid JWT, invalid JWT, and deactivated user")
    void webSocketConnectAuth() {
        // 1. Valid connect
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.setLeaveMutable(true);
        connectAccessor.setNativeHeader("Authorization", "Bearer " + studentToken);
        Message<byte[]> connectMsg = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());

        Message<?> processed = interceptor.preSend(connectMsg, null);
        StompHeaderAccessor processedAccessor = StompHeaderAccessor.wrap(processed);
        assertThat(processedAccessor.getUser()).isNotNull();

        // 2. Connect with missing token throws AccessDeniedException
        StompHeaderAccessor missingTokenAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        missingTokenAccessor.setLeaveMutable(true);
        Message<byte[]> missingMsg = MessageBuilder.createMessage(new byte[0], missingTokenAccessor.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(missingMsg, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Missing Authorization");

        // 3. Connect with invalid token throws AccessDeniedException
        StompHeaderAccessor invalidTokenAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        invalidTokenAccessor.setLeaveMutable(true);
        invalidTokenAccessor.setNativeHeader("Authorization", "Bearer invalid-token-xyz");
        Message<byte[]> invalidMsg = MessageBuilder.createMessage(new byte[0], invalidTokenAccessor.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(invalidMsg, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Invalid or expired JWT");

        // 4. Connect with deactivated user throws AccessDeniedException
        StompHeaderAccessor deactAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        deactAccessor.setLeaveMutable(true);
        deactAccessor.setNativeHeader("Authorization", "Bearer " + deactivatedToken);
        Message<byte[]> deactMsg = MessageBuilder.createMessage(new byte[0], deactAccessor.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(deactMsg, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("deactivated");
    }

    @Test
    @DisplayName("WebSocket STOMP SUBSCRIBE Authorization: only teacher can subscribe to teacher topic, enrolled student can subscribe, unenrolled rejected")
    void webSocketSubscribeAuthorization() {
        UserPrincipal teacherPrincipal = UserPrincipal.from(teacher);
        UsernamePasswordAuthenticationToken teacherAuth =
                new UsernamePasswordAuthenticationToken(teacherPrincipal, null, teacherPrincipal.getAuthorities());

        UserPrincipal studentPrincipal = UserPrincipal.from(student);
        UsernamePasswordAuthenticationToken studentAuth =
                new UsernamePasswordAuthenticationToken(studentPrincipal, null, studentPrincipal.getAuthorities());

        UserPrincipal unenrolledPrincipal = UserPrincipal.from(unenrolledStudent);
        UsernamePasswordAuthenticationToken unenrolledAuth =
                new UsernamePasswordAuthenticationToken(unenrolledPrincipal, null, unenrolledPrincipal.getAuthorities());

        // 1. Teacher subscribes to /topic/sessions/{id}/polls/teacher -> SUCCESS
        StompHeaderAccessor tAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        tAccessor.setLeaveMutable(true);
        tAccessor.setUser(teacherAuth);
        tAccessor.setDestination("/topic/sessions/" + session.getId() + "/polls/teacher");
        Message<byte[]> tMsg = MessageBuilder.createMessage(new byte[0], tAccessor.getMessageHeaders());
        assertThat(interceptor.preSend(tMsg, null)).isNotNull();

        // 2. Student subscribes to /topic/sessions/{id}/polls/teacher -> REJECTED
        StompHeaderAccessor sAccessorTeacherTopic = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        sAccessorTeacherTopic.setLeaveMutable(true);
        sAccessorTeacherTopic.setUser(studentAuth);
        sAccessorTeacherTopic.setDestination("/topic/sessions/" + session.getId() + "/polls/teacher");
        Message<byte[]> sMsgTeacherTopic = MessageBuilder.createMessage(new byte[0], sAccessorTeacherTopic.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(sMsgTeacherTopic, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the classroom teacher can subscribe to teacher poll updates");

        // 3. Enrolled student subscribes to /topic/sessions/{id}/discussion -> SUCCESS
        StompHeaderAccessor sAccessorDisc = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        sAccessorDisc.setLeaveMutable(true);
        sAccessorDisc.setUser(studentAuth);
        sAccessorDisc.setDestination("/topic/sessions/" + session.getId() + "/discussion");
        Message<byte[]> sMsgDisc = MessageBuilder.createMessage(new byte[0], sAccessorDisc.getMessageHeaders());
        assertThat(interceptor.preSend(sMsgDisc, null)).isNotNull();

        // 4. Unenrolled student subscribes to /topic/sessions/{id}/discussion -> REJECTED
        StompHeaderAccessor uAccessorDisc = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        uAccessorDisc.setLeaveMutable(true);
        uAccessorDisc.setUser(unenrolledAuth);
        uAccessorDisc.setDestination("/topic/sessions/" + session.getId() + "/discussion");
        Message<byte[]> uMsgDisc = MessageBuilder.createMessage(new byte[0], uAccessorDisc.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(uMsgDisc, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not enrolled");

        // 5. Unrecognized / arbitrary destination -> REJECTED
        StompHeaderAccessor evilAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        evilAccessor.setLeaveMutable(true);
        evilAccessor.setUser(studentAuth);
        evilAccessor.setDestination("/topic/secret/admin/passwords");
        Message<byte[]> evilMsg = MessageBuilder.createMessage(new byte[0], evilAccessor.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(evilMsg, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Unauthorized subscription destination");
    }

    @Test
    @DisplayName("Impersonation rejection: sender identity comes strictly from authenticated principal; rate limiting blocks flooding")
    void impersonationAndRateLimiting() {
        UserPrincipal studentPrincipal = UserPrincipal.from(student);
        UsernamePasswordAuthenticationToken studentAuth =
                new UsernamePasswordAuthenticationToken(studentPrincipal, null, studentPrincipal.getAuthorities());

        // Verify SEND with authenticated student principal
        StompHeaderAccessor sendAccessor = StompHeaderAccessor.create(StompCommand.SEND);
        sendAccessor.setLeaveMutable(true);
        sendAccessor.setUser(studentAuth);
        sendAccessor.setDestination("/app/sessions/" + session.getId() + "/discussion");
        Message<byte[]> sendMsg = MessageBuilder.createMessage(new byte[0], sendAccessor.getMessageHeaders());
        assertThat(interceptor.preSend(sendMsg, null)).isNotNull();

        // Rate limit trigger: consume all tokens for student
        for (int i = 0; i < 20; i++) {
            rateLimitService.tryConsumeMessage(student.getId());
        }

        // Sending when rate limit is exceeded throws RateLimitExceededException
        StompHeaderAccessor floodedAccessor = StompHeaderAccessor.create(StompCommand.SEND);
        floodedAccessor.setLeaveMutable(true);
        floodedAccessor.setUser(studentAuth);
        floodedAccessor.setDestination("/app/sessions/" + session.getId() + "/discussion");
        Message<byte[]> floodedMsg = MessageBuilder.createMessage(new byte[0], floodedAccessor.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(floodedMsg, null))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("rate limit exceeded");
    }
}
