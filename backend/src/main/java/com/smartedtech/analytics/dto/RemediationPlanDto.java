package com.smartedtech.analytics.dto;

import java.time.Instant;

public class RemediationPlanDto {

    private Long id;
    private Long attemptId;
    private Long studentId;
    private Long assessmentId;
    private String assessmentTitle;
    private String summary;
    private String recommendationsJson;
    private String providerUsed;
    private Instant createdAt;

    public RemediationPlanDto() {
    }

    public RemediationPlanDto(Long id, Long attemptId, Long studentId, Long assessmentId,
                              String assessmentTitle, String summary, String recommendationsJson,
                              String providerUsed, Instant createdAt) {
        this.id = id;
        this.attemptId = attemptId;
        this.studentId = studentId;
        this.assessmentId = assessmentId;
        this.assessmentTitle = assessmentTitle;
        this.summary = summary;
        this.recommendationsJson = recommendationsJson;
        this.providerUsed = providerUsed;
        this.createdAt = createdAt;
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

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getAssessmentId() {
        return assessmentId;
    }

    public void setAssessmentId(Long assessmentId) {
        this.assessmentId = assessmentId;
    }

    public String getAssessmentTitle() {
        return assessmentTitle;
    }

    public void setAssessmentTitle(String assessmentTitle) {
        this.assessmentTitle = assessmentTitle;
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

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
