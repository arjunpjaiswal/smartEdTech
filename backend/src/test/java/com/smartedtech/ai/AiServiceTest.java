package com.smartedtech.ai;

import com.smartedtech.common.IntegrationNotConfiguredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AiServiceTest {

    @Mock
    private GeminiProvider geminiProvider;

    @Mock
    private GroqProvider groqProvider;

    @Test
    @DisplayName("Throws 503 INTEGRATION_NOT_CONFIGURED when neither provider is configured")
    void missingKeys_ThrowsIntegrationNotConfigured() {
        when(geminiProvider.isConfigured()).thenReturn(false);
        when(groqProvider.isConfigured()).thenReturn(false);

        AiService aiService = new AiService(geminiProvider, groqProvider);

        assertThatThrownBy(() -> aiService.evaluateSubjective("rubric", "q", "ans", "student"))
                .isInstanceOf(IntegrationNotConfiguredException.class)
                .hasMessageContaining("AI generation is not configured");
    }

    @Test
    @DisplayName("Gemini transient failure (429/5xx) triggers failover to Groq")
    void geminiTransientError_FailsOverToGroq() {
        when(geminiProvider.isConfigured()).thenReturn(true);
        when(groqProvider.isConfigured()).thenReturn(true);

        when(geminiProvider.evaluateSubjective(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new AiTransientException("GEMINI", 429, "Rate limited"));

        AiResponse groqResponse = new AiResponse("{\"suggestedMarks\": 5.0}", "GROQ");
        when(groqProvider.evaluateSubjective(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(groqResponse);

        AiService aiService = new AiService(geminiProvider, groqProvider);
        AiResponse result = aiService.evaluateSubjective("rubric", "q", "ans", "student");

        assertThat(result).isNotNull();
        assertThat(result.providerUsed()).isEqualTo("GROQ");
        verify(groqProvider, times(1)).evaluateSubjective(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Gemini permanent error (400) does NOT fail over to Groq and surfaces immediately")
    void geminiPermanentError_DoesNotFailOver() {
        when(geminiProvider.isConfigured()).thenReturn(true);

        when(geminiProvider.evaluateSubjective(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new AiPermanentException("GEMINI", 400, "Invalid request"));

        AiService aiService = new AiService(geminiProvider, groqProvider);

        assertThatThrownBy(() -> aiService.evaluateSubjective("rubric", "q", "ans", "student"))
                .isInstanceOf(AiPermanentException.class)
                .hasMessageContaining("Invalid request");

        verify(groqProvider, never()).evaluateSubjective(anyString(), anyString(), anyString(), anyString());
    }
}
