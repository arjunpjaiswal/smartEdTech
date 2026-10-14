package com.smartedtech.assessment.dto;

public class OptionRequest {

    private Long id;
    private String optionText;
    private boolean isCorrect;
    private int orderIndex;

    public OptionRequest() {
    }

    public OptionRequest(Long id, String optionText, boolean isCorrect, int orderIndex) {
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
