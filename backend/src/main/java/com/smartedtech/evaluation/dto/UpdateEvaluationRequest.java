package com.smartedtech.evaluation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class UpdateEvaluationRequest {

    @NotNull(message = "Score is required")
    @DecimalMin(value = "0.0", message = "Score cannot be negative")
    private BigDecimal score;

    private String feedback;
    private String strengths;
    private String missingConcepts;

    public UpdateEvaluationRequest() {
    }

    public UpdateEvaluationRequest(BigDecimal score, String feedback, String strengths, String missingConcepts) {
        this.score = score;
        this.feedback = feedback;
        this.strengths = strengths;
        this.missingConcepts = missingConcepts;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
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
}
