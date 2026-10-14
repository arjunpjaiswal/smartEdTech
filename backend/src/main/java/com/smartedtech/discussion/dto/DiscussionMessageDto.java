package com.smartedtech.discussion.dto;

import com.smartedtech.discussion.DiscussionMessage;
import com.smartedtech.user.Role;

import java.time.Instant;

public class DiscussionMessageDto {

    private Long id;
    private Long sessionId;
    private Long senderId;
    private String senderName;
    private Role senderRole;
    private String content;
    private Instant createdAt;

    public DiscussionMessageDto() {
    }

    public DiscussionMessageDto(Long id, Long sessionId, Long senderId, String senderName, Role senderRole, String content, Instant createdAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.senderRole = senderRole;
        this.content = content;
        this.createdAt = createdAt;
    }

    public static DiscussionMessageDto from(DiscussionMessage message) {
        return new DiscussionMessageDto(
                message.getId(),
                message.getSession().getId(),
                message.getUser().getId(),
                message.getUser().getFullName(),
                message.getUser().getRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public Role getRole() {
        return senderRole;
    }

    public void setRole(Role senderRole) {
        this.senderRole = senderRole;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
