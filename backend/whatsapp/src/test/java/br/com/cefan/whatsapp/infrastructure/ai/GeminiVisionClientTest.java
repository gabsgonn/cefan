package br.com.cefan.whatsapp.infrastructure.ai;

import br.com.cefan.whatsapp.infrastructure.ai.dto.TattooAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeminiVisionClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReturnFallbackWhenApiKeyIsBlank() {
        GeminiVisionClient client = new GeminiVisionClient(
                objectMapper,
                "",
                "gemini-1.5-flash",
                "https://generativelanguage.googleapis.com/v1beta"
        );

        TattooAnalysisResult result = client.analyzeTattoo("base64data", "image/jpeg", "Quero no braço");

        assertNotNull(result);
        assertEquals("Arte Personalizada", result.getStyle());
        assertEquals("MEDIA", result.getComplexityLevel());
        assertEquals(1.2, result.getComplexityMultiplier());
        assertEquals(2.5, result.getEstimatedHours());
    }

    @Test
    void shouldReturnFallbackWhenImageIsBlank() {
        GeminiVisionClient client = new GeminiVisionClient(
                objectMapper,
                "fake-api-key",
                "gemini-1.5-flash",
                "https://generativelanguage.googleapis.com/v1beta"
        );

        TattooAnalysisResult result = client.analyzeTattoo("", "image/jpeg", "Quero no braço");

        assertNotNull(result);
        assertEquals("Arte Personalizada", result.getStyle());
    }

    @Test
    void shouldCreateFallbackWithExpectedDefaults() {
        GeminiVisionClient client = new GeminiVisionClient(
                objectMapper,
                "",
                "gemini-1.5-flash",
                "https://generativelanguage.googleapis.com/v1beta"
        );

        TattooAnalysisResult result = client.createFallbackResult("detalhes");

        assertNotNull(result);
        assertNotNull(result.getPrimaryColors());
        assertTrue(result.getPrimaryColors().contains("Preto"));
    }
}
