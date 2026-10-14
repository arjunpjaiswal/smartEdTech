package com.smartedtech.poll.dto;

public class PollOptionDto {

    private Long id;
    private String optionText;
    private int orderIndex;
    private Long voteCount;
    private Double percentage;

    public PollOptionDto() {
    }

    public PollOptionDto(Long id, String optionText, int orderIndex, Long voteCount, Double percentage) {
        this.id = id;
        this.optionText = optionText;
        this.orderIndex = orderIndex;
        this.voteCount = voteCount;
        this.percentage = percentage;
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

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public Long getVoteCount() {
        return voteCount;
    }

    public void setVoteCount(Long voteCount) {
        this.voteCount = voteCount;
    }

    public Double getPercentage() {
        return percentage;
    }

    public void setPercentage(Double percentage) {
        this.percentage = percentage;
    }
}
