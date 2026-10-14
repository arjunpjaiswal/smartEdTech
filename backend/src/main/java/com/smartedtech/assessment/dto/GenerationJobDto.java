package com.smartedtech.assessment.dto;

import java.time.Instant;

public class GenerationJobDto {

    private Long id;
    private String jobType;
    private String status;
    private Long userId;
    private Long materialId;
    private Long assessmentId;
    private Long attemptId;
    private String providerUsed;
    private String resultJson;
    private String errorCode;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;

    public GenerationJobDto() {
    }

    public GenerationJobDto(Long id, String jobType, String status, Long userId, Long materialId,
                            Long assessmentId, Long attemptId, String providerUsed, String resultJson,
                            String errorCode, String errorMessage, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.jobType = jobType;
        this.status = status;
        this.userId = userId;
        this.materialId = materialId;
        this.assessmentId = assessmentId;
        this.attemptId = attemptId;
        this.providerUsed = providerUsed;
        this.resultJson = resultJson;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public Long getAssessmentId() {
        return assessmentId;
    }

    public void setAssessmentId(Long assessmentId) {
        this.assessmentId = assessmentId;
    }

    public Long getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(Long attemptId) {
        this.attemptId = attemptId;
    }

    public String getProviderUsed() {
        return providerUsed;
    }

    public void setProviderUsed(String providerUsed) {
        this.providerUsed = providerUsed;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
