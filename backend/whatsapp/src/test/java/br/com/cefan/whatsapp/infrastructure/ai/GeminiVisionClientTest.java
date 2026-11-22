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
        assertTrue(result.isFallback());
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
                "gemini-2.5-flash",
                "https://generativelanguage.googleapis.com/v1beta"
        );

        TattooAnalysisResult result = client.analyzeTattoo("", "image/jpeg", "Quero no braço");

        assertNotNull(result);
        assertTrue(result.isFallback());
        assertEquals("Arte Personalizada", result.getStyle());
    }

    @Test
    void shouldReturnFallbackOnApiErrorAndKeepFallbackFalseOnValidGeminiJson() throws Exception {
        GeminiVisionClient client = new GeminiVisionClient(
                objectMapper,
                "fake-api-key",
                "gemini-2.5-flash",
                "http://127.0.0.1:1"
        );

        TattooAnalysisResult apiErrorResult = client.analyzeTattoo("aGVsbG8=", "image/jpeg", "leão no antebraço");
        assertNotNull(apiErrorResult);
        assertTrue(apiErrorResult.isFallback());

        TattooAnalysisResult parsedGemini = objectMapper.readValue(
                "{\"style\":\"Old School\",\"sizeCategory\":\"MEDIA\",\"estimatedHours\":2.0,\"complexityLevel\":\"MEDIA\"}",
                TattooAnalysisResult.class
        );
        assertFalse(parsedGemini.isFallback());
        assertEquals("Old School", parsedGemini.getStyle());
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

    @Test
    void shouldClassifyStructuredSizeCategoriesFromCustomerNotes() {
        GeminiVisionClient client = new GeminiVisionClient(
                objectMapper,
                "",
                "gemini-1.5-flash",
                "https://generativelanguage.googleapis.com/v1beta"
        );

        TattooAnalysisResult flash = client.createFallbackResult("Quero um flash de 4cm no pulso");
        assertEquals("FLASH", flash.getSizeCategory());
        assertTrue(flash.getEstimatedSizeCm().contains("Flash / Mini"));

        TattooAnalysisResult media = client.createFallbackResult("Tattoo média no antebraço em blackwork");
        assertEquals("MEDIA", media.getSizeCategory());
        assertTrue(media.getEstimatedSizeCm().contains("Média"));

        TattooAnalysisResult fechamento = client.createFallbackResult("Quero um fechamento de braço todo em realismo");
        assertEquals("FECHAMENTO", fechamento.getSizeCategory());
        assertTrue(fechamento.getEstimatedSizeCm().contains("Fechamento"));
        assertTrue(fechamento.getRecommendedArtists().contains("Carlos Silveira"));

        // Não deve dar falso-positivo para palavras que contêm substrings "mini" ou "media" (ex: "feminino", "imediato")
        assertTrue(br.com.cefan.whatsapp.domain.model.TattooSizeOption.fromInput("Quero um leão com traço feminino no braço").isEmpty());
        assertTrue(br.com.cefan.whatsapp.domain.model.TattooSizeOption.fromInput("Preciso de atendimento imediato").isEmpty());

        // Deve reconhecer variações naturais como "fechar o braço" e "15 centímetros"
        assertEquals(
                br.com.cefan.whatsapp.domain.model.TattooSizeOption.FECHAMENTO,
                br.com.cefan.whatsapp.domain.model.TattooSizeOption.fromInput("Quero fechar o braço").orElse(null)
        );
        assertEquals(
                br.com.cefan.whatsapp.domain.model.TattooSizeOption.MEDIA,
                br.com.cefan.whatsapp.domain.model.TattooSizeOption.fromInput("Tatuagem de 15 centímetros").orElse(null)
        );
        assertEquals(
                br.com.cefan.whatsapp.domain.model.TattooSizeOption.GRANDE,
                br.com.cefan.whatsapp.domain.model.TattooSizeOption.fromInput("Flash | Grande").orElse(null)
        );
    }
}
