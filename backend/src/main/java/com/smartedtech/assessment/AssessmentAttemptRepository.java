package com.smartedtech.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt, Long> {

    Optional<AssessmentAttempt> findByAssessmentIdAndStudentIdAndAttemptNumber(Long assessmentId, Long studentId, int attemptNumber);

    List<AssessmentAttempt> findByAssessmentIdAndStudentIdOrderByAttemptNumberDesc(Long assessmentId, Long studentId);

    Optional<AssessmentAttempt> findByAssessmentIdAndStudentIdAndStatus(Long assessmentId, Long studentId, String status);

    long countByAssessmentIdAndStudentId(Long assessmentId, Long studentId);
}
