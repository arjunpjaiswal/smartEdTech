package com.smartedtech.poll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PollRepository extends JpaRepository<Poll, Long> {

    List<Poll> findBySessionIdOrderByCreatedAtDesc(Long sessionId);

    Optional<Poll> findByIdAndSessionId(Long id, Long sessionId);
}
