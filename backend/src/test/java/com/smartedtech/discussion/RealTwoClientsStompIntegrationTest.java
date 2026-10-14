package com.smartedtech.discussion;

import com.smartedtech.TestDatabaseCleaner;
import com.smartedtech.classroom.Classroom;
import com.smartedtech.classroom.ClassroomEnrollment;
import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.classroom.ClassroomRepository;
import com.smartedtech.discussion.dto.DiscussionMessageDto;
import com.smartedtech.discussion.dto.SendDiscussionRequest;
import com.smartedtech.poll.PollService;
import com.smartedtech.poll.dto.CastVoteRequest;
import com.smartedtech.poll.dto.CreatePollRequest;
import com.smartedtech.poll.dto.PollDto;
import com.smartedtech.security.JwtService;
import com.smartedtech.security.UserPrincipal;
import com.smartedtech.session.LiveSession;
import com.smartedtech.session.LiveSessionRepository;
import com.smartedtech.user.Role;
import com.smartedtech.user.User;
import com.smartedtech.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class RealTwoClientsStompIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomEnrollmentRepository enrollmentRepository;

    @Autowired
    private LiveSessionRepository sessionRepository;

    @Autowired
    private PollService pollService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    private User teacher;
    private User student;
    private String teacherToken;
    private String studentToken;
    private Classroom classroom;
    private LiveSession session;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();

        teacher = userRepository.save(new User(
                "teacher.stomp@school.edu",
                passwordEncoder.encode("Password123!"),
                "Prof. Stomp",
                Role.TEACHER
        ));
        teacherToken = jwtService.generateAccessToken(teacher);

        student = userRepository.save(new User(
                "student.stomp@school.edu",
                passwordEncoder.encode("Password123!"),
                "Student Stomp",
                Role.STUDENT
        ));
        studentToken = jwtService.generateAccessToken(student);

        classroom = classroomRepository.save(new Classroom(
                "Distributed Systems",
                "STOMP Protocols",
                "DIST1010",
                teacher
        ));

        enrollmentRepository.save(new ClassroomEnrollment(classroom, student));

        Instant now = Instant.now();
        session = sessionRepository.save(new LiveSession(
                classroom,
                "Realtime STOMP Lecture",
                "Live testing with 2 STOMP clients",
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(55, ChronoUnit.MINUTES),
                "room-stomp-dist"
        ));
        session.setStatus("ACTIVE");
        sessionRepository.save(session);
    }

    @Test
    @DisplayName("Verify two real clients over STOMP WebSocket: Student sends chat, Teacher receives live; Teacher opens poll, Student votes")
    void verifyTwoRealClientsLiveCommunication() throws Exception {
        String wsUrl = "ws://localhost:" + port + "/ws";

        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);

        WebSocketStompClient teacherClient = new WebSocketStompClient(new StandardWebSocketClient());
        teacherClient.setMessageConverter(converter);

        WebSocketStompClient studentClient = new WebSocketStompClient(new StandardWebSocketClient());
        studentClient.setMessageConverter(converter);

        // Queues to capture frames on each client
        BlockingQueue<DiscussionMessageDto> teacherReceivedMessages = new LinkedBlockingQueue<>();
        BlockingQueue<PollDto> studentReceivedPolls = new LinkedBlockingQueue<>();
        BlockingQueue<PollDto> teacherReceivedPolls = new LinkedBlockingQueue<>();

        // 1. Connect Client 1 (Teacher) with JWT in CONNECT headers
        StompHeaders teacherConnectHeaders = new StompHeaders();
        teacherConnectHeaders.add("Authorization", "Bearer " + teacherToken);

        StompSession teacherSession = teacherClient.connectAsync(
                wsUrl,
                new WebSocketHttpHeaders(),
                teacherConnectHeaders,
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);

        assertThat(teacherSession.isConnected()).isTrue();

        // Teacher subscribes to discussion and polls
        teacherSession.subscribe(
                "/topic/sessions/" + session.getId() + "/discussion",
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return DiscussionMessageDto.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        System.out.println("Teacher received discussion: " + payload);
                        teacherReceivedMessages.offer((DiscussionMessageDto) payload);
                    }
                }
        );

        teacherSession.subscribe(
                "/topic/sessions/" + session.getId() + "/polls/teacher",
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return PollDto.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        System.out.println("Teacher received poll: " + payload);
                        teacherReceivedPolls.offer((PollDto) payload);
                    }
                }
        );

        // 2. Connect Client 2 (Student) with JWT in CONNECT headers
        StompHeaders studentConnectHeaders = new StompHeaders();
        studentConnectHeaders.add("Authorization", "Bearer " + studentToken);

        StompSession studentSession = studentClient.connectAsync(
                wsUrl,
                new WebSocketHttpHeaders(),
                studentConnectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        System.out.println("Student frame: " + headers);
                    }
                    @Override
                    public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                        System.err.println("Student STOMP exception: " + exception.getMessage());
                    }
                }
        ).get(5, TimeUnit.SECONDS);

        assertThat(studentSession.isConnected()).isTrue();

        // Student subscribes to polls
        studentSession.subscribe(
                "/topic/sessions/" + session.getId() + "/polls",
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return PollDto.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        System.out.println("Student received poll: " + payload);
                        studentReceivedPolls.offer((PollDto) payload);
                    }
                }
        );

        // 3. Client 2 (Student) sends a message over STOMP to /app/sessions/{id}/discussion
        SendDiscussionRequest chatRequest = new SendDiscussionRequest("Hello Prof! Asking a question via STOMP.");
        studentSession.send("/app/sessions/" + session.getId() + "/discussion", chatRequest);

        // 4. Verify Client 1 (Teacher) receives the live message
        DiscussionMessageDto receivedMsg = teacherReceivedMessages.poll(5, TimeUnit.SECONDS);
        assertThat(receivedMsg).isNotNull();
        assertThat(receivedMsg.getContent()).isEqualTo("Hello Prof! Asking a question via STOMP.");
        assertThat(receivedMsg.getSenderName()).isEqualTo("Student Stomp");
        assertThat(receivedMsg.getRole()).isEqualTo(Role.STUDENT);

        // 5. Teacher launches a live poll
        UserPrincipal teacherPrincipal = UserPrincipal.from(teacher);
        PollDto createdPoll = pollService.createPoll(
                session.getId(),
                new CreatePollRequest("Is WebSocket full-duplex?", List.of("Yes", "No"), true),
                teacherPrincipal
        );
        pollService.openPoll(session.getId(), createdPoll.getId(), teacherPrincipal);

        // Student receives the poll over STOMP
        PollDto studentPoll = studentReceivedPolls.poll(5, TimeUnit.SECONDS);
        assertThat(studentPoll).isNotNull();
        assertThat(studentPoll.getQuestion()).isEqualTo("Is WebSocket full-duplex?");

        // Drain earlier teacher poll notifications
        System.out.println("Draining teacher polls, currently in queue: " + teacherReceivedPolls.size());
        teacherReceivedPolls.clear();

        // 6. Student casts a vote
        UserPrincipal studentPrincipal = UserPrincipal.from(student);
        Long yesOptionId = createdPoll.getOptions().get(0).getId();
        PollDto votedPollResult = pollService.vote(session.getId(), createdPoll.getId(), new CastVoteRequest(yesOptionId), studentPrincipal);
        System.out.println("Vote executed, votedPollResult totalVotes: " + votedPollResult.getTotalVotes());

        // Teacher receives updated vote tally over STOMP
        PollDto updatedPollForTeacher = null;
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            PollDto p = teacherReceivedPolls.poll(1, TimeUnit.SECONDS);
            if (p != null && p.getTotalVotes() != null && p.getTotalVotes() == 1L) {
                updatedPollForTeacher = p;
                break;
            }
        }
        assertThat(updatedPollForTeacher).isNotNull();
        assertThat(updatedPollForTeacher.getTotalVotes()).isEqualTo(1L);

        // Disconnect sessions cleanly
        teacherSession.disconnect();
        studentSession.disconnect();
    }
}
