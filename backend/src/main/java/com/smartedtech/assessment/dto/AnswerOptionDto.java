package com.smartedtech.assessment.dto;

public class AnswerOptionDto {

    private Long id;
    private String optionText;
    private Boolean isCorrect;
    private int orderIndex;

    public AnswerOptionDto() {
    }

    public AnswerOptionDto(Long id, String optionText, Boolean isCorrect, int orderIndex) {
        this.id = id;
        this.optionText = optionText;
        this.isCorrect = isCorrect;
        this.orderIndex = orderIndex;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOptionText() {
        return optionText;
    }

    public void setOptionText(String optionText) {
        this.optionText = optionText;
    }

    public Boolean getIsCorrect() {
        return isCorrect;
    }

    public void setIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }
}
