package com.smartedtech.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WeakConceptRepository extends JpaRepository<WeakConcept, Long> {

    List<WeakConcept> findByStudentIdAndClassroomId(Long studentId, Long classroomId);

    List<WeakConcept> findByStudentId(Long studentId);

    List<WeakConcept> findByStudentIdAndAssessmentId(Long studentId, Long assessmentId);
}
