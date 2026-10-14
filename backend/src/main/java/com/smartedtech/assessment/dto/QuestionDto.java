package com.smartedtech.assessment.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class QuestionDto {

    private Long id;
    private Long assessmentId;
    private String questionText;
    private String questionType;
    private BigDecimal marks;
    private int orderIndex;
    private String modelAnswer;
    private String rubric;
    private List<AnswerOptionDto> options = new ArrayList<>();

    public QuestionDto() {
    }

    public QuestionDto(Long id, Long assessmentId, String questionText, String questionType,
                       BigDecimal marks, int orderIndex, String modelAnswer, String rubric,
                       List<AnswerOptionDto> options) {
        this.id = id;
        this.assessmentId = assessmentId;
        this.questionText = questionText;
        this.questionType = questionType;
        this.marks = marks;
        this.orderIndex = orderIndex;
        this.modelAnswer = modelAnswer;
        this.rubric = rubric;
        this.options = options != null ? options : new ArrayList<>();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAssessmentId() {
        return assessmentId;
    }

    public void setAssessmentId(Long assessmentId) {
        this.assessmentId = assessmentId;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getQuestionType() {
        return questionType;
    }

    public void setQuestionType(String questionType) {
        this.questionType = questionType;
    }

    public BigDecimal getMarks() {
        return marks;
    }

    public void setMarks(BigDecimal marks) {
        this.marks = marks;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public String getModelAnswer() {
        return modelAnswer;
    }

    public void setModelAnswer(String modelAnswer) {
        this.modelAnswer = modelAnswer;
    }

    public String getRubric() {
        return rubric;
    }

    public void setRubric(String rubric) {
        this.rubric = rubric;
    }

    public List<AnswerOptionDto> getOptions() {
        return options;
    }

    public void setOptions(List<AnswerOptionDto> options) {
        this.options = options;
    }
}
