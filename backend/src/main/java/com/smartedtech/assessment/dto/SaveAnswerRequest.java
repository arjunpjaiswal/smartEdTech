package com.smartedtech.assessment.dto;

import jakarta.validation.constraints.NotNull;

public class SaveAnswerRequest {

    @NotNull(message = "Question ID is required")
    private Long questionId;

    private Long selectedOptionId;
    private String answerText;

    public SaveAnswerRequest() {
    }

    public SaveAnswerRequest(Long questionId, Long selectedOptionId, String answerText) {
        this.questionId = questionId;
        this.selectedOptionId = selectedOptionId;
        this.answerText = answerText;
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
