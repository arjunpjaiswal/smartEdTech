package com.smartedtech.poll.dto;

import jakarta.validation.constraints.NotNull;

public class CastVoteRequest {

    @NotNull(message = "Option ID is required")
    private Long optionId;

    public CastVoteRequest() {
    }

    public CastVoteRequest(Long optionId) {
        this.optionId = optionId;
    }

    public Long getOptionId() {
        return optionId;
    }

    public void setOptionId(Long optionId) {
        this.optionId = optionId;
    }
}
