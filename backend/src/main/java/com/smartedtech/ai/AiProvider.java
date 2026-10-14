package com.smartedtech.ai;

public interface AiProvider {

    String getProviderName();

    boolean isConfigured();

    AiResponse generateQuestionsFromPdf(byte[] pdfBytes, String prompt);

    AiResponse generateQuestionsFromText(String text, String prompt);

    AiResponse evaluateSubjective(String rubric, String questionText, String modelAnswer, String studentAnswer);

    AiResponse generateRemediation(String prompt);

    AiResponse generateChatResponse(String systemPrompt, String contextText, java.util.List<ChatHistoryItem> history, String userMessage);
}
