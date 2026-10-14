package com.smartedtech.assessment.dto;

public class StudentAnswerDto {

    private Long id;
    private Long questionId;
    private Long selectedOptionId;
    private String answerText;

    public StudentAnswerDto() {
    }

    public StudentAnswerDto(Long id, Long questionId, Long selectedOptionId, String answerText) {
        this.id = id;
        this.questionId = questionId;
        this.selectedOptionId = selectedOptionId;
        this.answerText = answerText;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public Long getSelectedOptionId() {
        return selectedOptionId;
    }

    public void setSelectedOptionId(Long selectedOptionId) {
        this.selectedOptionId = selectedOptionId;
    }

    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
    }
}
