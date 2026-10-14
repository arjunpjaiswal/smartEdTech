package com.smartedtech.analytics.dto;

import java.time.Instant;

public class WeakConceptDto {

    private Long id;
    private Long studentId;
    private Long classroomId;
    private Long assessmentId;
    private Long questionId;
    private String conceptName;
    private String severity;
    private String explanation;
    private Instant createdAt;

    public WeakConceptDto() {
    }

    public WeakConceptDto(Long id, Long studentId, Long classroomId, Long assessmentId,
                          Long questionId, String conceptName, String severity,
                          String explanation, Instant createdAt) {
        this.id = id;
        this.studentId = studentId;
        this.classroomId = classroomId;
        this.assessmentId = assessmentId;
        this.questionId = questionId;
        this.conceptName = conceptName;
        this.severity = severity;
        this.explanation = explanation;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getClassroomId() {
        return classroomId;
    }

    public void setClassroomId(Long classroomId) {
        this.classroomId = classroomId;
    }

    public Long getAssessmentId() {
        return assessmentId;
    }

    public void setAssessmentId(Long assessmentId) {
        this.assessmentId = assessmentId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public String getConceptName() {
        return conceptName;
    }

    public void setConceptName(String conceptName) {
        this.conceptName = conceptName;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
