package com.smartedtech.evaluation.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class EvaluationDto {

    private Long id;
    private Long attemptId;
    private Long questionId;
    private String questionText;
    private BigDecimal score;
    private BigDecimal maxMarks;
    private String status; // AUTO_GRADED, AI_SUGGESTED, ACCEPTED, OVERRIDDEN
    private String feedback;
    private String strengths;
    private String missingConcepts;
    private Long evaluatedById;
    private String evaluatedByName;
    private String providerUsed;
    private Instant createdAt;
    private Instant updatedAt;

    public EvaluationDto() {
    }

    public EvaluationDto(Long id, Long attemptId, Long questionId, String questionText,
                         BigDecimal score, BigDecimal maxMarks, String status, String feedback,
                         String strengths, String missingConcepts, Long evaluatedById,
                         String evaluatedByName, String providerUsed, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.attemptId = attemptId;
        this.questionId = questionId;
        this.questionText = questionText;
        this.score = score;
        this.maxMarks = maxMarks;
        this.status = status;
        this.feedback = feedback;
        this.strengths = strengths;
        this.missingConcepts = missingConcepts;
        this.evaluatedById = evaluatedById;
        this.evaluatedByName = evaluatedByName;
        this.providerUsed = providerUsed;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(Long attemptId) {
        this.attemptId = attemptId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
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

    public Long getEvaluatedById() {
        return evaluatedById;
    }

    public void setEvaluatedById(Long evaluatedById) {
        this.evaluatedById = evaluatedById;
    }

    public String getEvaluatedByName() {
        return evaluatedByName;
    }

    public void setEvaluatedByName(String evaluatedByName) {
        this.evaluatedByName = evaluatedByName;
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

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
