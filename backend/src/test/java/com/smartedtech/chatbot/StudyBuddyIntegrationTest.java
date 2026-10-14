package com.smartedtech.chatbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedtech.TestDatabaseCleaner;
import com.smartedtech.ai.AiResponse;
import com.smartedtech.ai.AiService;
import com.smartedtech.ai.AiTransientException;
import com.smartedtech.chatbot.dto.CreateConversationRequest;
import com.smartedtech.chatbot.dto.SendMessageRequest;
import com.smartedtech.classroom.Classroom;
import com.smartedtech.classroom.ClassroomEnrollment;
import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.classroom.ClassroomRepository;
import com.smartedtech.common.IntegrationNotConfiguredException;
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
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class StudyBuddyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomEnrollmentRepository enrollmentRepository;

    @Autowired
    private ChatConversationRepository conversationRepository;

    @Autowired
    private ChatMessageRepository messageRepository;

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

    private String studentAToken;
    private String studentBToken;

    private Classroom classroom;
    private ChatConversation conversationA;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();

        teacher = userRepository.save(new User("teacher@test.com", passwordEncoder.encode("Pass12345678!"), "Teacher User", Role.TEACHER));
        studentA = userRepository.save(new User("student.a@test.com", passwordEncoder.encode("Pass12345678!"), "Student Alpha", Role.STUDENT));
        studentB = userRepository.save(new User("student.b@test.com", passwordEncoder.encode("Pass12345678!"), "Student Beta", Role.STUDENT));

        studentAToken = jwtService.generateAccessToken(studentA);
        studentBToken = jwtService.generateAccessToken(studentB);

        classroom = classroomRepository.save(new Classroom("Physics 101", "Intro Physics", "PHY10101", teacher));
        enrollmentRepository.save(new ClassroomEnrollment(classroom, studentA));
        enrollmentRepository.save(new ClassroomEnrollment(classroom, studentB));

        conversationA = conversationRepository.save(new ChatConversation(studentA, classroom, null, "Kinematics Help"));
    }

    @Test
    @DisplayName("Study Buddy: Conversation isolation blocks other students from reading or sending messages")
    void testConversationIsolation() throws Exception {
        // Student B tries to get Student A's conversation details -> 404 Not Found (scoped by studentId)
        mockMvc.perform(get("/api/v1/chat/conversations/" + conversationA.getId())
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isNotFound());

        // Student B tries to view Student A's messages -> 404 Not Found
        mockMvc.perform(get("/api/v1/chat/conversations/" + conversationA.getId() + "/messages")
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isNotFound());

        // Student B tries to send message into Student A's conversation -> 404 Not Found
        SendMessageRequest sendReq = new SendMessageRequest("Attempting intrusion");
        mockMvc.perform(post("/api/v1/chat/conversations/" + conversationA.getId() + "/messages")
                        .header("Authorization", "Bearer " + studentBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendReq)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Study Buddy: Message persistence verifies both student and assistant turns are stored in MySQL")
    void testMessagePersistenceAndChatHistory() throws Exception {
        when(aiService.generateChatResponse(any(), any(), any(), any()))
                .thenReturn(new AiResponse("Acceleration is the rate of change of velocity with respect to time.", "GEMINI"));

        SendMessageRequest sendReq = new SendMessageRequest("Can you explain acceleration simply?");
        mockMvc.perform(post("/api/v1/chat/conversations/" + conversationA.getId() + "/messages")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Acceleration is the rate of change of velocity with respect to time."))
                .andExpect(jsonPath("$.senderRole").value("ASSISTANT"))
                .andExpect(jsonPath("$.providerUsed").value("GEMINI"));

        // Verify MySQL persistence via GET /messages
        mockMvc.perform(get("/api/v1/chat/conversations/" + conversationA.getId() + "/messages")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].senderRole").value("USER"))
                .andExpect(jsonPath("$[0].content").value("Can you explain acceleration simply?"))
                .andExpect(jsonPath("$[1].senderRole").value("ASSISTANT"))
                .andExpect(jsonPath("$[1].content").value("Acceleration is the rate of change of velocity with respect to time."));

        assertThat(messageRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Study Buddy: Returns 503 INTEGRATION_NOT_CONFIGURED when AI keys are unset")
    void testMissingKeysReturns503() throws Exception {
        when(aiService.generateChatResponse(any(), any(), any(), any()))
                .thenThrow(new IntegrationNotConfiguredException("Neither GEMINI_API_KEY nor GROQ_API_KEY is configured"));

        SendMessageRequest sendReq = new SendMessageRequest("Hello");
        mockMvc.perform(post("/api/v1/chat/conversations/" + conversationA.getId() + "/messages")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendReq)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INTEGRATION_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Neither GEMINI_API_KEY nor GROQ_API_KEY is configured")));
    }

    @Test
    @DisplayName("Study Buddy: Provider failure returns 502 UPSTREAM_AI_ERROR with real error description")
    void testProviderFailureReturns502() throws Exception {
        when(aiService.generateChatResponse(any(), any(), any(), any()))
                .thenThrow(new AiTransientException("GEMINI", 503, "Both Gemini and Groq upstream providers timed out"));

        SendMessageRequest sendReq = new SendMessageRequest("Explain inertia");
        mockMvc.perform(post("/api/v1/chat/conversations/" + conversationA.getId() + "/messages")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendReq)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_AI_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Both Gemini and Groq upstream providers timed out")));
    }
}
