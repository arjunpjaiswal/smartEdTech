package com.smartedtech.assessment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class UpdateQuestionRequest {

    @NotBlank(message = "Question text is required")
    private String questionText;

    @NotBlank(message = "Question type is required")
    private String questionType;

    @NotNull(message = "Marks is required")
    @DecimalMin(value = "0.25", message = "Marks must be positive")
    private BigDecimal marks;

    private int orderIndex;
    private String modelAnswer;
    private String rubric;
    private List<OptionRequest> options = new ArrayList<>();

    public UpdateQuestionRequest() {
    }

    public UpdateQuestionRequest(String questionText, String questionType, BigDecimal marks,
                                 int orderIndex, String modelAnswer, String rubric, List<OptionRequest> options) {
        this.questionText = questionText;
        this.questionType = questionType;
        this.marks = marks;
        this.orderIndex = orderIndex;
        this.modelAnswer = modelAnswer;
        this.rubric = rubric;
        this.options = options != null ? options : new ArrayList<>();
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

    public List<OptionRequest> getOptions() {
        return options;
    }

    public void setOptions(List<OptionRequest> options) {
        this.options = options;
    }
}
