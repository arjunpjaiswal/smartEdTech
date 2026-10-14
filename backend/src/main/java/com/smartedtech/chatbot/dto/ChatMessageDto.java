package com.smartedtech.chatbot.dto;

import com.smartedtech.chatbot.ChatMessage;
import com.smartedtech.chatbot.SenderRole;

import java.time.Instant;

public record ChatMessageDto(
        Long id,
        Long conversationId,
        SenderRole senderRole,
        String content,
        boolean truncated,
        String truncationNotice,
        String providerUsed,
        Instant createdAt
) {
    public static ChatMessageDto from(ChatMessage m) {
        return new ChatMessageDto(
                m.getId(),
                m.getConversation().getId(),
                m.getSenderRole(),
                m.getContent(),
                m.isTruncated(),
                m.getTruncationNotice(),
                m.getProviderUsed(),
                m.getCreatedAt()
        );
    }
}
