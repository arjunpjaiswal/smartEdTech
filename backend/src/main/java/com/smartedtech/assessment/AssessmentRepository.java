package com.smartedtech.assessment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    Page<Assessment> findByClassroomId(Long classroomId, Pageable pageable);

    Page<Assessment> findByClassroomIdAndStatus(Long classroomId, String status, Pageable pageable);

    long countByClassroomId(Long classroomId);
}
