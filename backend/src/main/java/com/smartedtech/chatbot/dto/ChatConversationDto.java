package com.smartedtech.chatbot.dto;

import com.smartedtech.chatbot.ChatConversation;

import java.time.Instant;

public record ChatConversationDto(
        Long id,
        String title,
        Long classroomId,
        String classroomName,
        Long materialId,
        String materialTitle,
        String lastMessagePreview,
        Instant createdAt,
        Instant updatedAt
) {
    public static ChatConversationDto from(ChatConversation c, String lastMessagePreview) {
        return new ChatConversationDto(
                c.getId(),
                c.getTitle(),
                c.getClassroom() != null ? c.getClassroom().getId() : null,
                c.getClassroom() != null ? c.getClassroom().getName() : null,
                c.getMaterial() != null ? c.getMaterial().getId() : null,
                c.getMaterial() != null ? c.getMaterial().getTitle() : null,
                lastMessagePreview,
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
