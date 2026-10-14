package com.smartedtech.analytics;

import com.smartedtech.classroom.Classroom;
import com.smartedtech.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "weak_concepts")
public class WeakConcept {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id", nullable = false)
    private Classroom classroom;

    @Column(name = "assessment_id")
    private Long assessmentId;

    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "concept_name", nullable = false)
    private String conceptName;

    @Column(nullable = false, length = 50)
    private String severity = "MODERATE"; // LOW, MODERATE, HIGH

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public WeakConcept() {
    }

    public WeakConcept(User student, Classroom classroom, Long assessmentId, Long questionId,
                       String conceptName, String severity, String explanation) {
        this.student = student;
        this.classroom = classroom;
        this.assessmentId = assessmentId;
        this.questionId = questionId;
        this.conceptName = conceptName;
        this.severity = severity != null ? severity : "MODERATE";
        this.explanation = explanation;
    }

    public Long getId() {
        return id;
    }

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    public Classroom getClassroom() {
        return classroom;
    }

    public void setClassroom(Classroom classroom) {
        this.classroom = classroom;
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
}
