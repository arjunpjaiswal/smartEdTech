package com.smartedtech.evaluation;

import com.smartedtech.assessment.AssessmentAttempt;
import com.smartedtech.assessment.Question;
import com.smartedtech.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "evaluations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_evaluations_attempt_question", columnNames = {"attempt_id", "question_id"})
})
public class Evaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private AssessmentAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal score = BigDecimal.ZERO;

    @Column(name = "max_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxMarks;

    @Column(nullable = false, length = 50)
    private String status; // AUTO_GRADED, AI_SUGGESTED, ACCEPTED, OVERRIDDEN

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(name = "missing_concepts", columnDefinition = "TEXT")
    private String missingConcepts;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluated_by_id")
    private User evaluatedBy;

    @Column(name = "provider_used", length = 50)
    private String providerUsed;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public Evaluation() {
    }

    public Evaluation(AssessmentAttempt attempt, Question question, BigDecimal score,
                      BigDecimal maxMarks, String status, String feedback, String strengths,
                      String missingConcepts, User evaluatedBy, String providerUsed) {
        this.attempt = attempt;
        this.question = question;
        this.score = score;
        this.maxMarks = maxMarks;
        this.status = status;
        this.feedback = feedback;
        this.strengths = strengths;
        this.missingConcepts = missingConcepts;
        this.evaluatedBy = evaluatedBy;
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

    public Question getQuestion() {
        return question;
    }

    public void setQuestion(Question question) {
        this.question = question;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public BigDecimal getMaxMarks() {
        return maxMarks;
    }

    public void setMaxMarks(BigDecimal maxMarks) {
        this.maxMarks = maxMarks;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public String getStrengths() {
        return strengths;
    }

    public void setStrengths(String strengths) {
        this.strengths = strengths;
    }

    public String getMissingConcepts() {
        return missingConcepts;
    }

    public void setMissingConcepts(String missingConcepts) {
        this.missingConcepts = missingConcepts;
    }

    public User getEvaluatedBy() {
        return evaluatedBy;
    }

    public void setEvaluatedBy(User evaluatedBy) {
        this.evaluatedBy = evaluatedBy;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
