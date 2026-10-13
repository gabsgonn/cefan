package br.com.cefan.whatsapp.infrastructure.ai;

import br.com.cefan.whatsapp.infrastructure.ai.dto.GeminiGenerateContentRequest;
import br.com.cefan.whatsapp.infrastructure.ai.dto.GeminiGenerateContentResponse;
import br.com.cefan.whatsapp.infrastructure.ai.dto.TattooAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class GeminiVisionClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiVisionClient(
            ObjectMapper objectMapper,
            @Value("${gemini.api.key:}") String apiKey,
            @Value("${gemini.api.model:gemini-1.5-flash}") String model,
            @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.apiKey = (apiKey != null) ? apiKey.trim() : "";
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public TattooAnalysisResult analyzeTattoo(String base64Image, String mimeType, String customerNotes) {
        if (apiKey.isBlank()) {
            log.info("Chave GEMINI_API_KEY não configurada. Aplicando estimativa padrão para a arte.");
            return createFallbackResult(customerNotes);
        }

        if (base64Image == null || base64Image.isBlank()) {
            log.warn("Nenhum dado de imagem em base64 fornecido. Aplicando estimativa padrão.");
            return createFallbackResult(customerNotes);
        }

        try {
            String sanitizedBase64 = sanitizeBase64(base64Image);
            String effectiveMimeType = (mimeType != null && !mimeType.isBlank()) ? mimeType : "image/jpeg";

            String prompt = buildPrompt(customerNotes);

            GeminiGenerateContentRequest.Part textPart = GeminiGenerateContentRequest.Part.builder()
                    .text(prompt)
                    .build();

            GeminiGenerateContentRequest.Part imagePart = GeminiGenerateContentRequest.Part.builder()
                    .inlineData(GeminiGenerateContentRequest.InlineData.builder()
                            .mimeType(effectiveMimeType)
                            .data(sanitizedBase64)
                            .build())
                    .build();

            GeminiGenerateContentRequest requestBody = GeminiGenerateContentRequest.builder()
                    .contents(List.of(
                            GeminiGenerateContentRequest.Content.builder()
                                    .parts(List.of(textPart, imagePart))
                                    .build()
                    ))
                    .generationConfig(GeminiGenerateContentRequest.GenerationConfig.builder()
                            .responseMimeType("application/json")
                            .temperature(0.2)
                            .build())
                    .build();

            log.info("Enviando imagem para análise com Google Gemini Vision ({})", model);

            GeminiGenerateContentResponse response = restClient.post()
                    .uri("/models/{model}:generateContent?key={apiKey}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(GeminiGenerateContentResponse.class);

            if (response != null) {
                String rawJson = response.extractFirstText();
                if (rawJson != null && !rawJson.isBlank()) {
                    String cleanJson = cleanJsonString(rawJson);
                    TattooAnalysisResult result = objectMapper.readValue(cleanJson, TattooAnalysisResult.class);
                    log.info("Arte analisada com sucesso: Estilo=[{}], Horas=[{}], Complexidade=[{}]",
                            result.getStyle(), result.getEstimatedHours(), result.getComplexityLevel());
                    return result;
                }
            }

            log.warn("Resposta vazia da API Gemini. Utilizando estimativa de segurança.");
            return createFallbackResult(customerNotes);
        } catch (Exception e) {
            log.error("Erro ao processar imagem no Gemini Vision: {}. Retornando estimativa padrão.", e.getMessage(), e);
            return createFallbackResult(customerNotes);
        }
    }

    private String buildPrompt(String customerNotes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Você é um mestre tatuador e orçamentista profissional especialista em arte corporal.\n");
        sb.append("Analise a imagem da tatuagem fornecida como referência ");
        if (customerNotes != null && !customerNotes.isBlank()) {
            sb.append("e leve em consideração a observação enviada pelo cliente: \"").append(customerNotes).append("\".\n");
        } else {
            sb.append(".\n");
        }
        sb.append("Responda ESTRITAMENTE em formato JSON com as seguintes chaves:\n");
        sb.append("{\n");
        sb.append("  \"style\": \"Nome do estilo predominante (ex: Fineline / Traço Fino, Blackwork, Realismo Preto e Cinza, Tradicional / Old School, Aquarela, Minimalista, Tribal / Geométrico)\",\n");
        sb.append("  \"complexityLevel\": \"BAIXA, MEDIA ou ALTA\",\n");
        sb.append("  \"complexityMultiplier\": multiplicador decimal entre 1.0 e 2.0 (1.0 a 1.1 para traço simples minimalista; 1.2 a 1.4 para sombreamento moderado/blackwork; 1.5 a 2.0 para realismo detalhado ou artes complexas),\n");
        sb.append("  \"estimatedHours\": estimativa realista de horas de sessão de agulha (número decimal entre 1.0 e 8.0 horas),\n");
        sb.append("  \"suggestedBodyLocation\": \"locais do corpo mais recomendados para esse estilo (ex: Antebraço, Panturrilha, Costela)\",\n");
        sb.append("  \"technicalNotes\": \"breve explicação amigável (2 a 3 frases em português) sobre as características da arte (agulhas sugeridas, sombreamento, proporção) para apresentar ao cliente\",\n");
        sb.append("  \"primaryColors\": [\"Preto\", \"Cinza\"]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String sanitizeBase64(String base64) {
        if (base64.contains(",")) {
            return base64.substring(base64.indexOf(",") + 1).trim();
        }
        return base64.trim();
    }

    private String cleanJsonString(String rawJson) {
        String clean = rawJson.trim();
        if (clean.startsWith("```json")) {
            clean = clean.substring(7);
        } else if (clean.startsWith("```")) {
            clean = clean.substring(3);
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length() - 3);
        }
        return clean.trim();
    }

    public TattooAnalysisResult createFallbackResult(String customerNotes) {
        return TattooAnalysisResult.builder()
                .style("Arte Personalizada")
                .complexityLevel("MEDIA")
                .complexityMultiplier(1.2)
                .estimatedHours(2.5)
                .suggestedBodyLocation("Antebraço ou Panturrilha")
                .technicalNotes("Tatuagem com traços e sombreamento padrão do estúdio. Detalhes finais ajustados com o tatuador na sessão.")
                .primaryColors(new ArrayList<>(List.of("Preto", "Cinza")))
                .build();
    }
}
