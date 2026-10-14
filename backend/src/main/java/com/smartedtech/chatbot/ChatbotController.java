package com.smartedtech.chatbot;

import com.smartedtech.chatbot.dto.ChatConversationDto;
import com.smartedtech.chatbot.dto.ChatMessageDto;
import com.smartedtech.chatbot.dto.CreateConversationRequest;
import com.smartedtech.chatbot.dto.SendMessageRequest;
import com.smartedtech.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat/conversations")
@PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @GetMapping
    public ResponseEntity<List<ChatConversationDto>> listConversations(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(chatbotService.listConversations(principal));
    }

    @PostMapping
    public ResponseEntity<ChatConversationDto> createConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateConversationRequest request) {
        ChatConversationDto dto = chatbotService.createConversation(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChatConversationDto> getConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(chatbotService.getConversation(principal, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        chatbotService.deleteConversation(principal, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<ChatMessageDto>> getMessages(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(chatbotService.getMessages(principal, id));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<ChatMessageDto> sendMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody SendMessageRequest request) {
        ChatMessageDto reply = chatbotService.sendMessage(principal, id, request);
        return ResponseEntity.ok(reply);
    }
}
