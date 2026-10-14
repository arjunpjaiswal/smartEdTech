package com.smartedtech.ai;

import com.smartedtech.common.IntegrationNotConfiguredException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final GeminiProvider geminiProvider;
    private final GroqProvider groqProvider;

    public AiService(GeminiProvider geminiProvider, GroqProvider groqProvider) {
        this.geminiProvider = geminiProvider;
        this.groqProvider = groqProvider;
    }

    public boolean isAnyConfigured() {
        return geminiProvider.isConfigured() || groqProvider.isConfigured();
    }

    public String extractTextFromPdf(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (IOException e) {
            log.error("PDFBox extraction failed: {}", e.getMessage());
            throw new RuntimeException("Failed to extract text from PDF document: " + e.getMessage(), e);
        }
    }

    public AiResponse generateQuestions(byte[] pdfBytes, String prompt) {
        ensureAiConfigured();

        if (geminiProvider.isConfigured()) {
            try {
                return geminiProvider.generateQuestionsFromPdf(pdfBytes, prompt);
            } catch (AiTransientException e) {
                log.warn("Gemini encountered transient error (status {}). Attempting failover to Groq.", e.getStatusCode());
                if (groqProvider.isConfigured()) {
                    String extractedText = extractTextFromPdf(pdfBytes);
                    return groqProvider.generateQuestionsFromText(extractedText, prompt);
                }
                throw e;
            }
        } else {
            String extractedText = extractTextFromPdf(pdfBytes);
            return groqProvider.generateQuestionsFromText(extractedText, prompt);
        }
    }

    public AiResponse evaluateSubjective(String rubric, String questionText, String modelAnswer, String studentAnswer) {
        ensureAiConfigured();

        if (geminiProvider.isConfigured()) {
            try {
                return geminiProvider.evaluateSubjective(rubric, questionText, modelAnswer, studentAnswer);
            } catch (AiTransientException e) {
                log.warn("Gemini evaluation transient error (status {}). Failing over to Groq.", e.getStatusCode());
                if (groqProvider.isConfigured()) {
                    return groqProvider.evaluateSubjective(rubric, questionText, modelAnswer, studentAnswer);
                }
                throw e;
            }
        } else {
            return groqProvider.evaluateSubjective(rubric, questionText, modelAnswer, studentAnswer);
        }
    }

    public AiResponse generateRemediation(String prompt) {
        ensureAiConfigured();

        if (geminiProvider.isConfigured()) {
            try {
                return geminiProvider.generateRemediation(prompt);
            } catch (AiTransientException e) {
                log.warn("Gemini remediation transient error (status {}). Failing over to Groq.", e.getStatusCode());
                if (groqProvider.isConfigured()) {
                    return groqProvider.generateRemediation(prompt);
                }
                throw e;
            }
        } else {
            return groqProvider.generateRemediation(prompt);
        }
    }

    public AiResponse generateChatResponse(String systemPrompt, String contextText, java.util.List<ChatHistoryItem> history, String userMessage) {
        ensureAiConfigured();

        if (geminiProvider.isConfigured()) {
            try {
                return geminiProvider.generateChatResponse(systemPrompt, contextText, history, userMessage);
            } catch (AiTransientException e) {
                log.warn("Gemini chat transient error (status {}). Failing over to Groq.", e.getStatusCode());
                if (groqProvider.isConfigured()) {
                    return groqProvider.generateChatResponse(systemPrompt, contextText, history, userMessage);
                }
                throw e;
            }
        } else {
            return groqProvider.generateChatResponse(systemPrompt, contextText, history, userMessage);
        }
    }

    private void ensureAiConfigured() {
        if (!isAnyConfigured()) {
            throw new IntegrationNotConfiguredException(
                    "AI generation is not configured. Missing: GEMINI_API_KEY or GROQ_API_KEY environment variables."
            );
        }
    }
}
