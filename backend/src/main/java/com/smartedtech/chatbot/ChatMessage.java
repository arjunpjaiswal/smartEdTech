package com.smartedtech.chatbot;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ChatConversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false, length = 20)
    private SenderRole senderRole;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(nullable = false)
    private boolean truncated = false;

    @Column(name = "truncation_notice", length = 255)
    private String truncationNotice;

    @Column(name = "provider_used", length = 50)
    private String providerUsed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ChatMessage() {
    }

    public ChatMessage(ChatConversation conversation, SenderRole senderRole, String content, boolean truncated, String truncationNotice, String providerUsed) {
        this.conversation = conversation;
        this.senderRole = senderRole;
        this.content = content;
        this.truncated = truncated;
        this.truncationNotice = truncationNotice;
        this.providerUsed = providerUsed;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public ChatConversation getConversation() {
        return conversation;
    }

    public void setConversation(ChatConversation conversation) {
        this.conversation = conversation;
    }

    public SenderRole getSenderRole() {
        return senderRole;
    }

    public void setSenderRole(SenderRole senderRole) {
        this.senderRole = senderRole;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public String getTruncationNotice() {
        return truncationNotice;
    }

    public void setTruncationNotice(String truncationNotice) {
        this.truncationNotice = truncationNotice;
    }

    public String getProviderUsed() {
        return providerUsed;
    }

    public void setProviderUsed(String providerUsed) {
        this.providerUsed = providerUsed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
