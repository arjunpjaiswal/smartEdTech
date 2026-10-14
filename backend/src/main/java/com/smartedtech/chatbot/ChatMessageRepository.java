package com.smartedtech.chatbot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    List<ChatMessage> findTop10ByConversationIdOrderByCreatedAtDesc(Long conversationId);

    Optional<ChatMessage> findTop1ByConversationIdOrderByCreatedAtDesc(Long conversationId);
}
