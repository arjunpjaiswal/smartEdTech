package com.smartedtech.analytics;

import com.smartedtech.assessment.Assessment;
import com.smartedtech.assessment.AssessmentAttempt;
import com.smartedtech.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "remediation_plans", uniqueConstraints = {
        @UniqueConstraint(name = "uk_remediation_attempt", columnNames = {"attempt_id"})
})
public class RemediationPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private AssessmentAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String summary;

    @Column(name = "recommendations_json", columnDefinition = "JSON", nullable = false)
    private String recommendationsJson;

    @Column(name = "provider_used", nullable = false, length = 50)
    private String providerUsed;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public RemediationPlan() {
    }

    public RemediationPlan(AssessmentAttempt attempt, User student, Assessment assessment,
                           String summary, String recommendationsJson, String providerUsed) {
        this.attempt = attempt;
        this.student = student;
        this.assessment = assessment;
        this.summary = summary;
        this.recommendationsJson = recommendationsJson;
        this.providerUsed = providerUsed;
    }

    public Long getId() {
        return id;
    }

    public AssessmentAttempt getAttempt() {
        return attempt;
    }

    public void setAttempt(AssessmentAttempt attempt) {
        this.attempt = attempt;
    }

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    public Assessment getAssessment() {
        return assessment;
    }

    public void setAssessment(Assessment assessment) {
        this.assessment = assessment;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getRecommendationsJson() {
        return recommendationsJson;
    }

    public void setRecommendationsJson(String recommendationsJson) {
        this.recommendationsJson = recommendationsJson;
    }

    public String getProviderUsed() {
        return providerUsed;
    }

    public void setProviderUsed(String providerUsed) {
        this.providerUsed = providerUsed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
