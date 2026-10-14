package com.smartedtech.chatbot;

import com.smartedtech.ai.AiResponse;
import com.smartedtech.ai.AiService;
import com.smartedtech.ai.ChatHistoryItem;
import com.smartedtech.chatbot.dto.ChatConversationDto;
import com.smartedtech.chatbot.dto.ChatMessageDto;
import com.smartedtech.chatbot.dto.CreateConversationRequest;
import com.smartedtech.chatbot.dto.SendMessageRequest;
import com.smartedtech.classroom.Classroom;
import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.classroom.ClassroomRepository;
import com.smartedtech.common.BadRequestException;
import com.smartedtech.common.ResourceNotFoundException;
import com.smartedtech.material.FileStorage;
import com.smartedtech.material.StudyMaterial;
import com.smartedtech.material.StudyMaterialRepository;
import com.smartedtech.security.UserPrincipal;
import com.smartedtech.user.Role;
import com.smartedtech.user.User;
import com.smartedtech.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ChatbotService {

    private static final Logger log = LoggerFactory.getLogger(ChatbotService.class);
    private static final int MAX_CONTEXT_LENGTH = 8000;

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomEnrollmentRepository enrollmentRepository;
    private final StudyMaterialRepository materialRepository;
    private final FileStorage fileStorage;
    private final AiService aiService;

    public ChatbotService(
            ChatConversationRepository conversationRepository,
            ChatMessageRepository messageRepository,
            UserRepository userRepository,
            ClassroomRepository classroomRepository,
            ClassroomEnrollmentRepository enrollmentRepository,
            StudyMaterialRepository materialRepository,
            FileStorage fileStorage,
            AiService aiService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.classroomRepository = classroomRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.materialRepository = materialRepository;
        this.fileStorage = fileStorage;
        this.aiService = aiService;
    }

    @Transactional(readOnly = true)
    public List<ChatConversationDto> listConversations(UserPrincipal principal) {
        List<ChatConversation> convs = conversationRepository.findByStudentIdOrderByUpdatedAtDesc(principal.getId());
        return convs.stream().map(c -> {
            String preview = messageRepository.findTop1ByConversationIdOrderByCreatedAtDesc(c.getId())
                    .map(m -> m.getContent().length() > 80 ? m.getContent().substring(0, 80) + "..." : m.getContent())
                    .orElse(null);
            return ChatConversationDto.from(c, preview);
        }).toList();
    }

    @Transactional
    public ChatConversationDto createConversation(UserPrincipal principal, CreateConversationRequest req) {
        User student = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Classroom classroom = null;
        StudyMaterial material = null;

        if (req.materialId() != null) {
            material = materialRepository.findById(req.materialId())
                    .orElseThrow(() -> new ResourceNotFoundException("Study material not found: " + req.materialId()));

            classroom = material.getClassroom();
            // Verify student is enrolled in classroom
            if (principal.getRole() == Role.STUDENT &&
                    !enrollmentRepository.existsByClassroomIdAndStudentId(classroom.getId(), student.getId())) {
                throw new AccessDeniedException("You are not enrolled in the classroom for this study material");
            }
        } else if (req.classroomId() != null) {
            classroom = classroomRepository.findById(req.classroomId())
                    .orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + req.classroomId()));

            if (principal.getRole() == Role.STUDENT &&
                    !enrollmentRepository.existsByClassroomIdAndStudentId(classroom.getId(), student.getId())) {
                throw new AccessDeniedException("You are not enrolled in this classroom");
            }
        }

        String title = (req.title() != null && !req.title().trim().isBlank()) ? req.title().trim() : "Study Session";
        ChatConversation conversation = new ChatConversation(student, classroom, material, title);
        ChatConversation saved = conversationRepository.save(conversation);

        return ChatConversationDto.from(saved, null);
    }

    @Transactional(readOnly = true)
    public ChatConversationDto getConversation(UserPrincipal principal, Long convId) {
        ChatConversation conversation = conversationRepository.findByIdWithDetailsAndStudentId(convId, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + convId));

        String preview = messageRepository.findTop1ByConversationIdOrderByCreatedAtDesc(conversation.getId())
                .map(m -> m.getContent().length() > 80 ? m.getContent().substring(0, 80) + "..." : m.getContent())
                .orElse(null);

        return ChatConversationDto.from(conversation, preview);
    }

    @Transactional
    public void deleteConversation(UserPrincipal principal, Long convId) {
        ChatConversation conversation = conversationRepository.findByIdAndStudentId(convId, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + convId));

        conversationRepository.delete(conversation);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessages(UserPrincipal principal, Long convId) {
        ChatConversation conversation = conversationRepository.findByIdAndStudentId(convId, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + convId));

        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId())
                .stream()
                .map(ChatMessageDto::from)
                .toList();
    }

    @Transactional
    public ChatMessageDto sendMessage(UserPrincipal principal, Long convId, SendMessageRequest req) {
        ChatConversation conversation = conversationRepository.findByIdWithDetailsAndStudentId(convId, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + convId));

        String studentInput = req.content() != null ? req.content().trim() : "";
        if (studentInput.isBlank()) {
            throw new BadRequestException("Message content cannot be blank");
        }
        if (studentInput.length() > 4000) {
            throw new BadRequestException("Message content exceeds 4000 characters limit");
        }

        // Optional plain text context injection from attached study material
        String contextText = null;
        boolean contextTruncated = false;
        String truncationNotice = null;

        if (conversation.getMaterial() != null) {
            StudyMaterial mat = conversation.getMaterial();
            if (principal.getRole() == Role.STUDENT &&
                    !enrollmentRepository.existsByClassroomIdAndStudentId(mat.getClassroom().getId(), principal.getId())) {
                throw new AccessDeniedException("Access revoked: You are no longer enrolled in the material's classroom");
            }

            try {
                Resource resource = fileStorage.loadAsResource(mat.getStoredFilename());
                byte[] bytes = resource.getContentAsByteArray();
                String rawText;

                if (mat.getContentType() != null && mat.getContentType().toLowerCase().contains("pdf")) {
                    rawText = aiService.extractTextFromPdf(bytes);
                } else {
                    rawText = new String(bytes, StandardCharsets.UTF_8);
                }

                if (rawText != null && !rawText.isBlank()) {
                    if (rawText.length() > MAX_CONTEXT_LENGTH) {
                        contextText = rawText.substring(0, MAX_CONTEXT_LENGTH);
                        contextTruncated = true;
                        truncationNotice = "Reference material exceeded length limit and was truncated to the first " + MAX_CONTEXT_LENGTH + " characters for processing.";
                    } else {
                        contextText = rawText;
                    }
                }
            } catch (IOException e) {
                log.error("Failed to read study material file for context injection: {}", e.getMessage());
                // Proceed without material context if file read fails
            }
        }

        // Fetch recent conversation history (last 10 turns)
        List<ChatMessage> recentList = new ArrayList<>(messageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(conversation.getId()));
        Collections.reverse(recentList);

        List<ChatHistoryItem> historyItems = recentList.stream()
                .map(m -> new ChatHistoryItem(
                        m.getSenderRole() == SenderRole.USER ? "user" : "assistant",
                        m.getContent()
                ))
                .toList();

        String systemPrompt = """
                You are an encouraging, insightful, and pedagogical AI Study Buddy for SmartEdTech.
                Help the student understand academic concepts thoroughly through step-by-step explanations,
                intuitive analogies, and Socratic questioning rather than just giving direct answers.
                If reference study material excerpt is provided below, ground your explanations in that material.
                Never claim to use RAG or external retrieval.
                """;

        // Dispatch to AI service with failover
        AiResponse aiResponse = aiService.generateChatResponse(systemPrompt, contextText, historyItems, studentInput);

        // Persist student message
        ChatMessage userMsg = new ChatMessage(
                conversation,
                SenderRole.USER,
                studentInput,
                false,
                null,
                null
        );
        messageRepository.save(userMsg);

        // Persist AI response
        ChatMessage assistantMsg = new ChatMessage(
                conversation,
                SenderRole.ASSISTANT,
                aiResponse.content(),
                contextTruncated,
                truncationNotice,
                aiResponse.providerUsed()
        );
        ChatMessage savedAssistantMsg = messageRepository.save(assistantMsg);

        // Update conversation timestamp
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        return ChatMessageDto.from(savedAssistantMsg);
    }
}
