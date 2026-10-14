package com.smartedtech.discussion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscussionMessageRepository extends JpaRepository<DiscussionMessage, Long> {

    Page<DiscussionMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId, Pageable pageable);

    Page<DiscussionMessage> findBySessionIdOrderByCreatedAtDesc(Long sessionId, Pageable pageable);
}
