package br.com.cefan.whatsapp.infrastructure.ai;

import br.com.cefan.whatsapp.infrastructure.ai.dto.GeminiGenerateContentRequest;
import br.com.cefan.whatsapp.infrastructure.ai.dto.TattooAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    void shouldRetryWithBackoffOn429Or503ThenFailoverToLiteModelAndSucceedForOldSchoolPanther() throws Exception {
        List<String> requestedPaths = Collections.synchronizedList(new ArrayList<>());
        List<String> requestBodies = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger primaryAttempts = new AtomicInteger(0);
        AtomicInteger liteAttempts = new AtomicInteger(0);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requestedPaths.add(path);
            String reqBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requestBodies.add(reqBody);

            if (path.contains("gemini-3.5-flash-lite")) {
                liteAttempts.incrementAndGet();
                String geminiJson = """
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  {
                                    "text": "{\\"style\\":\\"Old School / Tradicional\\",\\"sizeCategory\\":\\"MEDIA\\",\\"estimatedSizeCm\\":\\"Média (11 a 18 cm)\\",\\"complexityLevel\\":\\"MEDIA\\",\\"complexityMultiplier\\":1.2,\\"estimatedHours\\":2.5,\\"detectedBodyLocation\\":\\"Antebraço\\",\\"suggestedBodyLocation\\":\\"Panturrilha, Braço, Costas, Peito\\",\\"customerDescription\\":\\"Pantera negra tradicional com traços marcantes e cores clássicas.\\",\\"technicalNotes\\":\\"Contorno bold line 11RL e preenchimento Magnum.\\",\\"recommendedArtists\\":[\\"Rodrigo Old School\\"],\\"primaryColors\\":[\\"Preto\\",\\"Vermelho\\",\\"Amarelo\\"]}"
                                  }
                                ]
                              }
                            }
                          ]
                        }
                        """;
                byte[] bytes = geminiJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                int attempt = primaryAttempts.incrementAndGet();
                int status = (attempt == 1) ? 429 : 503;
                byte[] bytes = ("{\"error\":{\"code\":" + status + ",\"message\":\"Rate limit / overloaded\"}}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            GeminiVisionClient client = new GeminiVisionClient(
                    objectMapper,
                    "test-api-key",
                    "gemini-3.5-flash",
                    "gemini-3.5-flash-lite",
                    "http://127.0.0.1:" + port + "/v1beta",
                    null,
                    2,
                    10L,
                    5000,
                    "low"
            );

            TattooAnalysisResult pantherResult = client.analyzeTattoo(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
                    "image/jpeg",
                    "Foto de pantera negra crawling old school"
            );

            // 2 tentativas em gemini-3.5-flash (429 -> backoff -> 503) + 1 tentativa em gemini-3.5-flash-lite (200 OK)
            assertEquals(2, primaryAttempts.get());
            assertEquals(1, liteAttempts.get());
            assertEquals(3, requestedPaths.size());
            assertTrue(requestedPaths.get(0).contains("gemini-3.5-flash:generateContent"));
            assertTrue(requestedPaths.get(1).contains("gemini-3.5-flash:generateContent"));
            assertTrue(requestedPaths.get(2).contains("gemini-3.5-flash-lite:generateContent"));

            // Verifica que o thinkingConfig.thinkingLevel = "low" foi enviado no payload do 3.5
            assertTrue(requestBodies.get(0).contains("\"thinkingLevel\":\"low\""));

            assertNotNull(pantherResult);
            assertFalse(pantherResult.isFallback());
            assertTrue(pantherResult.getStyle().toLowerCase().contains("old school")
                    || pantherResult.getStyle().toLowerCase().contains("tradicional"));
            assertTrue(pantherResult.getRecommendedArtists().contains("Rodrigo Old School"));
            assertEquals("Antebraço", pantherResult.getDetectedBodyLocation());
            // As 4 sugestões retornadas pela IA foram truncadas para no máximo 2
            assertEquals("Panturrilha ou Braço", pantherResult.getSuggestedBodyLocation());
            assertNotNull(pantherResult.getCustomerDescription());
            assertFalse(pantherResult.getCustomerDescription().contains("11RL"));
            assertTrue(pantherResult.getTechnicalNotes().contains("11RL"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnFallbackTrueWhenEntireModelChainFailsWith503() throws Exception {
        AtomicInteger totalCalls = new AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/", exchange -> {
            totalCalls.incrementAndGet();
            byte[] bytes = "{\"error\":{\"code\":503,\"message\":\"Service Unavailable\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(503, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            GeminiVisionClient client = new GeminiVisionClient(
                    objectMapper,
                    "test-api-key",
                    "gemini-3.5-flash",
                    "gemini-3.5-flash-lite",
                    "http://127.0.0.1:" + port + "/v1beta",
                    null,
                    2,
                    5L,
                    5000,
                    "low"
            );

            TattooAnalysisResult fallback = client.analyzeTattoo("aGVsbG8=", "image/jpeg", "pantera old school");
            // 2 tentativas no modelo primário + 2 tentativas no lite = 4 chamadas antes do fallback final
            assertEquals(4, totalCalls.get());
            assertTrue(fallback.isFallback());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldBuildCorrectThinkingConfigParameterForGemini35VsGemini25() {
        GeminiVisionClient client = new GeminiVisionClient(
                objectMapper, "key", "gemini-3.5-flash", "gemini-3.5-flash-lite",
                "http://localhost", null, 2, 10L, 5000, "low");

        GeminiGenerateContentRequest.ThinkingConfig g35Config = client.buildThinkingConfigForModel("gemini-3.5-flash");
        assertNotNull(g35Config);
        assertEquals("low", g35Config.getThinkingLevel());
        assertNull(g35Config.getThinkingBudget());

        GeminiGenerateContentRequest.ThinkingConfig g25Config = client.buildThinkingConfigForModel("gemini-2.5-flash");
        assertNotNull(g25Config);
        assertEquals(0, g25Config.getThinkingBudget());
        assertNull(g25Config.getThinkingLevel());
    }

    @Test
    void shouldParseGeminiResponseRegardlessOfContentTypeApplicationOctetStreamOrJson() {
        String geminiPayload = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"style\\":\\"Old School / Tradicional\\",\\"sizeCategory\\":\\"MEDIA\\",\\"estimatedHours\\":2.5,\\"complexityLevel\\":\\"MEDIA\\",\\"recommendedArtists\\":[\\"Rodrigo Old School\\"]}"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        for (org.springframework.http.MediaType mediaType : List.of(
                org.springframework.http.MediaType.APPLICATION_OCTET_STREAM,
                org.springframework.http.MediaType.APPLICATION_JSON)) {
            org.springframework.web.client.RestClient.Builder builder =
                    org.springframework.web.client.RestClient.builder().baseUrl("http://localhost:9999/v1beta");
            org.springframework.test.web.client.MockRestServiceServer mockServer =
                    org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();

            mockServer.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                            "http://localhost:9999/v1beta/models/gemini-3.5-flash-lite:generateContent?key=secret-key"))
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                            geminiPayload.getBytes(StandardCharsets.UTF_8), mediaType));

            GeminiVisionClient client = new GeminiVisionClient(
                    objectMapper,
                    "secret-key",
                    "gemini-3.5-flash",
                    "gemini-3.5-flash-lite",
                    "http://localhost:9999/v1beta",
                    null,
                    2,
                    10L,
                    18000,
                    "low",
                    "gemini-3.5-flash-lite,gemini-3.5-flash",
                    "gemini-3.5-flash-lite=18000,gemini-3.5-flash=25000"
            );
            org.springframework.test.util.ReflectionTestUtils.setField(client, "restClient", builder.build());

            assertEquals(List.of("gemini-3.5-flash-lite", "gemini-3.5-flash"), client.getModelChain());
            assertEquals(18000, client.resolveTimeoutMsForModel("gemini-3.5-flash-lite"));
            assertEquals(25000, client.resolveTimeoutMsForModel("gemini-3.5-flash"));

            TattooAnalysisResult result = client.analyzeTattoo("aGVsbG8=", "image/jpeg", "pantera");
            mockServer.verify();
            assertNotNull(result);
            assertFalse(result.isFallback());
            assertEquals("Old School / Tradicional", result.getStyle());
        }
    }

    @Test
    void shouldUseTemperatureZeroClosedSchemaDeterministicHoursAndSha256CachePerCustomer() {
        String geminiPayload = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"style\\":\\"Old School / Tradicional\\",\\"sizeCategory\\":\\"MEDIA\\",\\"complexityLevel\\":\\"ALTA\\",\\"suggestedBodyLocation\\":\\"Antebraço\\",\\"customerDescription\\":\\"Pantera clássica.\\",\\"recommendedArtists\\":[\\"Rodrigo Old School\\"]}"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        org.springframework.web.client.RestClient.Builder builder =
                org.springframework.web.client.RestClient.builder().baseUrl("http://localhost:9999/v1beta");
        org.springframework.test.web.client.MockRestServiceServer mockServer =
                org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();

        // Apenas 1 chamada HTTP esperada: a segunda com a mesma foto e mesmo cliente deve vir do cache SHA-256
        mockServer.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                        "http://localhost:9999/v1beta/models/gemini-3.5-flash-lite:generateContent?key=secret-key"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().string(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"temperature\"")),
                                org.hamcrest.Matchers.containsString("\"response_schema\""),
                                org.hamcrest.Matchers.containsString("Old School / Tradicional")
                        )))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        geminiPayload.getBytes(StandardCharsets.UTF_8), org.springframework.http.MediaType.APPLICATION_JSON));

        GeminiVisionClient client = new GeminiVisionClient(
                objectMapper, "secret-key", "gemini-3.5-flash-lite", "gemini-3.5-flash",
                "http://localhost:9999/v1beta", null, 2, 10L, 12000, "low",
                "gemini-3.5-flash-lite,gemini-3.5-flash", "", 25000L);
        org.springframework.test.util.ReflectionTestUtils.setField(client, "restClient", builder.build());

        TattooAnalysisResult first = client.analyzeTattoo("5541999074863", "aGVsbG8=", "image/jpeg", "");
        TattooAnalysisResult second = client.analyzeTattoo("5541999074863", "aGVsbG8=", "image/jpeg", "");

        mockServer.verify();
        assertEquals(3.0, first.getEstimatedHours()); // MEDIA + ALTA -> 3.0h na tabela determinística
        assertEquals(1.40, first.getComplexityMultiplier());
        assertEquals(first.getStyle(), second.getStyle());
        assertEquals(first.getEstimatedHours(), second.getEstimatedHours());
    }
}
