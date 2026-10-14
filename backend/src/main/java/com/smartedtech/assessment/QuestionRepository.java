package com.smartedtech.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByAssessmentIdOrderByOrderIndexAsc(Long assessmentId);

    long countByAssessmentId(Long assessmentId);
}
