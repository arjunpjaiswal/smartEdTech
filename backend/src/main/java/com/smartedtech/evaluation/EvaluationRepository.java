package com.smartedtech.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

    Optional<Evaluation> findByAttemptIdAndQuestionId(Long attemptId, Long questionId);

    List<Evaluation> findByAttemptId(Long attemptId);
}
