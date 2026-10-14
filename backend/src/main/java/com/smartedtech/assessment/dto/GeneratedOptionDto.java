package com.smartedtech.assessment.dto;

public class GeneratedOptionDto {

    private String optionText;
    private boolean isCorrect;
    private int orderIndex;

    public GeneratedOptionDto() {
    }

    public GeneratedOptionDto(String optionText, boolean isCorrect, int orderIndex) {
        this.optionText = optionText;
        this.isCorrect = isCorrect;
        this.orderIndex = orderIndex;
    }

    public String getOptionText() {
        return optionText;
    }

    public void setOptionText(String optionText) {
        this.optionText = optionText;
    }

    public boolean isCorrect() {
        return isCorrect;
    }

    public void setCorrect(boolean correct) {
        isCorrect = correct;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }
}
