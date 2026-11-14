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
        sb.append("Você é o orçamentista especialista com IA do renomado estúdio 'Vício Tattoo CWB'.\n");
        sb.append("A equipe do estúdio é composta por 11 profissionais especialistas:\n");
        sb.append("1. Carlos Silveira (Especialista em Realismo Preto e Cinza, Retratos)\n");
        sb.append("2. Mariana Rocha (Especialista em Fineline / Traço Fino e Florais Delicados)\n");
        sb.append("3. Lucas Blade (Especialista em Blackwork Pesado, Geometria e Tribal)\n");
        sb.append("4. Beatriz Lima (Especialista em Aquarela e Tatuagens Coloridas)\n");
        sb.append("5. Rodrigo Old School (Especialista em Tradicional Americano / Old School e Bold Line)\n");
        sb.append("6. Felipe Neo (Especialista em Neo Traditional e New School)\n");
        sb.append("7. Camila Pontilhismo (Especialista em Pontilhismo e Mandalas)\n");
        sb.append("8. Gabriel Oriental (Especialista em Irezumi e Tatuagem Oriental)\n");
        sb.append("9. Larissa Piercing (Body Piercer, Perfurações Corporais e Microdermal)\n");
        sb.append("10. Thiago Dark (Especialista em Dark Art, Gótico e Lettering Sombrio)\n");
        sb.append("11. Julia Piercer (Body Piercer, Aplicação de Joias em Titânio e Ouro)\n\n");

        sb.append("Analise a imagem da tatuagem ou arte de referência enviada pelo cliente ");
        if (customerNotes != null && !customerNotes.isBlank()) {
            sb.append("e considere as informações adicionais do cliente: \"").append(customerNotes).append("\".\n");
        } else {
            sb.append(".\n");
        }

        sb.append("Calcule o tempo de agulha REALISTA com base no tamanho visual e complexidade do traço:\n");
        sb.append("- Tatuagem Mini/Pequena (até 6cm, traço simples/fineline): 0.5 a 1.2 horas.\n");
        sb.append("- Tatuagem Média (7 a 14cm, sombreamento moderado): 1.5 a 2.5 horas.\n");
        sb.append("- Tatuagem Grande/Complexa (15cm+, realismo ou fechamento): 3.0 a 6.0 horas.\n\n");

        sb.append("Responda ESTRITAMENTE em formato JSON com as seguintes chaves:\n");
        sb.append("{\n");
        sb.append("  \"style\": \"Estilo predominante identificado\",\n");
        sb.append("  \"sizeCategory\": \"MINI (até 5cm), PEQUENA (6-10cm), MEDIA (11-18cm) ou GRANDE (19cm+)\",\n");
        sb.append("  \"estimatedSizeCm\": \"ex: 5x5 cm, 10x8 cm ou 18x12 cm\",\n");
        sb.append("  \"complexityLevel\": \"BAIXA, MEDIA ou ALTA\",\n");
        sb.append("  \"complexityMultiplier\": multiplicador decimal (1.0 a 1.1 para traço simples; 1.2 a 1.4 para sombreado/blackwork; 1.5 a 1.8 para realismo detalhado),\n");
        sb.append("  \"estimatedHours\": número decimal com estimativa realista de horas (ex: 0.8, 1.5, 2.5, 4.0),\n");
        sb.append("  \"suggestedBodyLocation\": \"locais anatômicos ideais (ex: Antebraço, Pulso, Costela, Panturrilha)\",\n");
        sb.append("  \"technicalNotes\": \"2 a 3 frases explicativas sobre agulhas recomendadas, sombreamento e cuidados\",\n");
        sb.append("  \"recommendedArtists\": [\"Nome do tatuador especialista do estúdio mais adequado para esse estilo\"],\n");
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
        String lower = (customerNotes != null) ? customerNotes.toLowerCase() : "";

        if (lower.contains("pequena") || lower.contains("mini") || lower.contains("delicada") || lower.contains("fineline") || lower.contains("traço fino") || lower.contains("5cm") || lower.contains("frase")) {
            return TattooAnalysisResult.builder()
                    .style("Fineline / Traço Fino")
                    .sizeCategory("PEQUENA")
                    .estimatedSizeCm("6x4 cm")
                    .complexityLevel("BAIXA")
                    .complexityMultiplier(1.05)
                    .estimatedHours(1.0)
                    .suggestedBodyLocation("Antebraço, Pulso ou Tornozelo")
                    .technicalNotes("Traço fino e delicado com agulha 03RL. Excelente cicatrização e definição para artes minimalistas.")
                    .recommendedArtists(List.of("Mariana Rocha (Fineline)"))
                    .primaryColors(new ArrayList<>(List.of("Preto")))
                    .build();
        } else if (lower.contains("realismo") || lower.contains("retrato") || lower.contains("leão") || lower.contains("lobo") || lower.contains("braço todo") || lower.contains("fechamento")) {
            return TattooAnalysisResult.builder()
                    .style("Realismo Preto e Cinza")
                    .sizeCategory("MEDIA")
                    .estimatedSizeCm("15x10 cm")
                    .complexityLevel("ALTA")
                    .complexityMultiplier(1.4)
                    .estimatedHours(3.5)
                    .suggestedBodyLocation("Antebraço, Bíceps ou Panturrilha")
                    .technicalNotes("Obra com contraste dramático, degradê suave e texturas com agulhas Magnum. Realismo de alto impacto.")
                    .recommendedArtists(List.of("Carlos Silveira (Realismo)"))
                    .primaryColors(new ArrayList<>(List.of("Preto", "Cinza")))
                    .build();
        } else if (lower.contains("piercing") || lower.contains("furar") || lower.contains("joia")) {
            return TattooAnalysisResult.builder()
                    .style("Body Piercing")
                    .sizeCategory("MINI")
                    .estimatedSizeCm("Perfuração padrão")
                    .complexityLevel("BAIXA")
                    .complexityMultiplier(1.0)
                    .estimatedHours(0.5)
                    .suggestedBodyLocation("Orelha (Hélice/Tragus/Lóbulo) ou Nariz")
                    .technicalNotes("Procedimento asséptico com cateter descartável e joia de titânio grau implante.")
                    .recommendedArtists(List.of("Larissa Piercing", "Julia Piercer"))
                    .primaryColors(new ArrayList<>(List.of("Prata / Titânio")))
                    .build();
        }

        return TattooAnalysisResult.builder()
                .style("Arte Personalizada")
                .sizeCategory("PEQUENA")
                .estimatedSizeCm("10x7 cm")
                .complexityLevel("MEDIA")
                .complexityMultiplier(1.2)
                .estimatedHours(2.5)
                .suggestedBodyLocation("Antebraço ou Panturrilha")
                .technicalNotes("Tatuagem com traços e sombreamento padrão do estúdio. Detalhes finais ajustados com o tatuador na sessão.")
                .recommendedArtists(List.of("Lucas Blade (Blackwork)", "Mariana Rocha"))
                .primaryColors(new ArrayList<>(List.of("Preto", "Cinza")))
                .build();
    }
}
