package com.smartedtech.chatbot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

    List<ChatConversation> findByStudentIdOrderByUpdatedAtDesc(Long studentId);

    Optional<ChatConversation> findByIdAndStudentId(Long id, Long studentId);

    @Query("SELECT c FROM ChatConversation c LEFT JOIN FETCH c.classroom LEFT JOIN FETCH c.material WHERE c.id = :id AND c.student.id = :studentId")
    Optional<ChatConversation> findByIdWithDetailsAndStudentId(@Param("id") Long id, @Param("studentId") Long studentId);
}
