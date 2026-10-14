package com.smartedtech.assessment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

    Page<GenerationJob> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<GenerationJob> findByIdAndUserId(Long id, Long userId);
}
