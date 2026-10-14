package com.smartedtech.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RemediationPlanRepository extends JpaRepository<RemediationPlan, Long> {

    Optional<RemediationPlan> findByAttemptId(Long attemptId);
}
