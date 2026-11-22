package br.com.cefan.whatsapp.infrastructure.ai;

import br.com.cefan.whatsapp.domain.model.TattooSizeOption;
import br.com.cefan.whatsapp.domain.model.User;
import br.com.cefan.whatsapp.domain.model.UserRole;
import br.com.cefan.whatsapp.domain.repository.UserRepository;
import br.com.cefan.whatsapp.infrastructure.ai.dto.GeminiGenerateContentRequest;
import br.com.cefan.whatsapp.infrastructure.ai.dto.GeminiGenerateContentResponse;
import br.com.cefan.whatsapp.infrastructure.ai.dto.TattooAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class GeminiVisionClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final UserRepository userRepository;

    public GeminiVisionClient(
            ObjectMapper objectMapper,
            String apiKey,
            String model,
            String baseUrl) {
        this(objectMapper, apiKey, model, baseUrl, null);
    }

    @Autowired
    public GeminiVisionClient(
            ObjectMapper objectMapper,
            @Value("${gemini.api.key:}") String apiKey,
            @Value("${gemini.api.model:gemini-2.5-flash}") String model,
            @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            UserRepository userRepository) {
        this.objectMapper = objectMapper;
        this.apiKey = (apiKey != null) ? apiKey.trim() : "";
        this.model = model;
        this.userRepository = userRepository;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public TattooAnalysisResult analyzeTattoo(String base64Image, String mimeType, String customerNotes) {
        if (apiKey.isBlank()) {
            log.warn("GEMINI_FALLBACK ativado [motivo=MISSING_API_KEY, model={}]: GEMINI_API_KEY não configurada.", model);
            return createFallbackResult(customerNotes);
        }

        if (base64Image == null || base64Image.isBlank()) {
            log.warn("GEMINI_FALLBACK ativado [motivo=EMPTY_IMAGE, model={}]: base64 da imagem ausente/vazio.", model);
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

            log.info("Enviando imagem para análise com Google Gemini Vision ({}, {} bytes base64)", model, sanitizedBase64.length());

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
                    result.setFallback(false);
                    log.info("GEMINI_VISION sucesso [model={}]: Estilo=[{}], Tamanho=[{}], Horas=[{}], Complexidade=[{}]",
                            model, result.getStyle(), result.getSizeCategory(), result.getEstimatedHours(), result.getComplexityLevel());
                    return result;
                }
            }

            log.warn("GEMINI_FALLBACK ativado [motivo=EMPTY_RESPONSE, model={}]: resposta vazia da API Gemini.", model);
            return createFallbackResult(customerNotes);
        } catch (Exception e) {
            log.warn("GEMINI_FALLBACK ativado [motivo=API_ERROR, model={}]: {}", model, e.getMessage(), e);
            return createFallbackResult(customerNotes);
        }
    }

    private String buildPrompt(String customerNotes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Você é o orçamentista especialista com IA do renomado estúdio 'Vício Tattoo CWB'.\n");

        List<User> activeArtists = (userRepository != null)
                ? userRepository.findAll().stream()
                        .filter(u -> u.getRole() == UserRole.ARTIST && Boolean.TRUE.equals(u.getIsActive()))
                        .toList()
                : List.of();

        if (!activeArtists.isEmpty()) {
            sb.append("A equipe do estúdio é composta por ").append(activeArtists.size()).append(" profissionais especialistas:\n");
            int idx = 1;
            for (User artist : activeArtists) {
                String spec = (artist.getSpecialties() != null && !artist.getSpecialties().isBlank())
                        ? artist.getSpecialties()
                        : "Tatuagem Autoral";
                sb.append(idx++).append(". ").append(artist.getName()).append(" (Especialista em ").append(spec).append(")\n");
            }
            sb.append("\n");
        } else {
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
        }

        sb.append("Analise a imagem da tatuagem ou arte de referência enviada pelo cliente ");
        if (customerNotes != null && !customerNotes.isBlank()) {
            sb.append("e considere as informações adicionais do cliente: \"").append(customerNotes).append("\".\n");
        } else {
            sb.append(".\n");
        }

        sb.append("Classifique o tamanho dentro das opções estruturadas do estúdio e calcule o tempo de agulha REALISTA:\n");
        sb.append("- FLASH (Flash / Mini - até 5 cm, traço simples/fineline): 0.5 a 1.0 hora.\n");
        sb.append("- PEQUENA (Pequena - 6 a 10 cm): 1.0 a 1.5 horas.\n");
        sb.append("- MEDIA (Média - 11 a 18 cm, sombreamento moderado): 2.0 a 3.0 horas.\n");
        sb.append("- GRANDE (Grande - 19 a 25 cm, alta densidade): 3.5 a 5.0 horas.\n");
        sb.append("- FECHAMENTO (Fechamento - 26 cm+ / Braço, Perna, Costas / Projeto Amplo): 6.0+ horas (Valor a combinar com o tatuador).\n\n");

        sb.append("Responda ESTRITAMENTE em formato JSON com as seguintes chaves:\n");
        sb.append("{\n");
        sb.append("  \"style\": \"Estilo predominante identificado\",\n");
        sb.append("  \"sizeCategory\": \"FLASH, PEQUENA, MEDIA, GRANDE ou FECHAMENTO\",\n");
        sb.append("  \"estimatedSizeCm\": \"Flash / Mini (até 5 cm), Pequena (6 a 10 cm), Média (11 a 18 cm), Grande (19 a 25 cm) ou Fechamento (26 cm+ / Projeto Amplo)\",\n");
        sb.append("  \"complexityLevel\": \"BAIXA, MEDIA ou ALTA\",\n");
        sb.append("  \"complexityMultiplier\": multiplicador decimal (1.0 a 1.1 para traço simples; 1.2 a 1.4 para sombreado/blackwork; 1.5 a 1.8 para realismo detalhado),\n");
        sb.append("  \"estimatedHours\": número decimal com estimativa realista de horas (ex: 1.0, 1.5, 2.5, 4.0, 6.0),\n");
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
        TattooAnalysisResult result = buildFallbackResult(customerNotes);
        result.setFallback(true);
        return result;
    }

    private TattooAnalysisResult buildFallbackResult(String customerNotes) {
        String lower = extractEffectiveStyleNotes(customerNotes);
        Optional<TattooSizeOption> sizeOpt = TattooSizeOption.fromInput(customerNotes);

        if (lower.contains("piercing") || lower.contains("furar") || lower.contains("joia")) {
            TattooSizeOption resolvedSize = sizeOpt.orElse(TattooSizeOption.FLASH);
            return TattooAnalysisResult.builder()
                    .style("Body Piercing")
                    .sizeCategory(resolvedSize.getCategoryKey())
                    .estimatedSizeCm("Perfuração padrão")
                    .complexityLevel("BAIXA")
                    .complexityMultiplier(1.0)
                    .estimatedHours(0.5)
                    .suggestedBodyLocation("Orelha (Hélice/Tragus/Lóbulo) ou Nariz")
                    .technicalNotes("Procedimento asséptico com cateter descartável e joia de titânio grau implante.")
                    .recommendedArtists(List.of("Larissa Piercing", "Julia Piercer"))
                    .primaryColors(new ArrayList<>(List.of("Prata / Titânio")))
                    .build();
        } else if (lower.contains("realismo") || lower.contains("retrato") || lower.contains("leão") || lower.contains("leao") || lower.contains("lobo")) {
            TattooSizeOption resolvedSize = sizeOpt.orElse(TattooSizeOption.MEDIA);
            double hours = sizeOpt.map(TattooSizeOption::getDefaultHours).orElse(3.5);
            return TattooAnalysisResult.builder()
                    .style("Realismo Preto e Cinza")
                    .sizeCategory(resolvedSize.getCategoryKey())
                    .estimatedSizeCm(resolvedSize.getFormattedLabel())
                    .complexityLevel("ALTA")
                    .complexityMultiplier(1.4)
                    .estimatedHours(hours)
                    .suggestedBodyLocation("Antebraço, Bíceps ou Panturrilha")
                    .technicalNotes("Obra com contraste dramático, degradê suave e texturas com agulhas Magnum. Realismo de alto impacto.")
                    .recommendedArtists(List.of("Carlos Silveira"))
                    .primaryColors(new ArrayList<>(List.of("Preto", "Cinza")))
                    .build();
        } else if (lower.contains("blackwork") || lower.contains("tribal") || lower.contains("geométrico") || lower.contains("geometrico")) {
            TattooSizeOption resolvedSize = sizeOpt.orElse(TattooSizeOption.MEDIA);
            return TattooAnalysisResult.builder()
                    .style("Blackwork / Geométrico")
                    .sizeCategory(resolvedSize.getCategoryKey())
                    .estimatedSizeCm(resolvedSize.getFormattedLabel())
                    .complexityLevel("MEDIA")
                    .complexityMultiplier(1.25)
                    .estimatedHours(resolvedSize.getDefaultHours())
                    .suggestedBodyLocation("Antebraço, Ombro ou Panturrilha")
                    .technicalNotes("Preenchimento sólido em preto intenso e linhas geométricas precisas.")
                    .recommendedArtists(List.of("Lucas 'Blade'"))
                    .primaryColors(new ArrayList<>(List.of("Preto")))
                    .build();
        } else if (lower.contains("aquarela") || lower.contains("colorid") || lower.contains("botânico") || lower.contains("botanico")) {
            TattooSizeOption resolvedSize = sizeOpt.orElse(TattooSizeOption.MEDIA);
            return TattooAnalysisResult.builder()
                    .style("Aquarela & Floral")
                    .sizeCategory(resolvedSize.getCategoryKey())
                    .estimatedSizeCm(resolvedSize.getFormattedLabel())
                    .complexityLevel("MEDIA")
                    .complexityMultiplier(1.25)
                    .estimatedHours(resolvedSize.getDefaultHours())
                    .suggestedBodyLocation("Antebraço, Ombro ou Costela")
                    .technicalNotes("Transições fluidas de cor e pigmentação vibrante com agulhas Round Shader e Magnum.")
                    .recommendedArtists(List.of("Beatriz Lima"))
                    .primaryColors(new ArrayList<>(List.of("Colorido")))
                    .build();
        } else if (lower.contains("old school") || lower.contains("tradicional")) {
            TattooSizeOption resolvedSize = sizeOpt.orElse(TattooSizeOption.MEDIA);
            return TattooAnalysisResult.builder()
                    .style("Old School / Tradicional")
                    .sizeCategory(resolvedSize.getCategoryKey())
                    .estimatedSizeCm(resolvedSize.getFormattedLabel())
                    .complexityLevel("MEDIA")
                    .complexityMultiplier(1.2)
                    .estimatedHours(resolvedSize.getDefaultHours())
                    .suggestedBodyLocation("Braço, Antebraço ou Panturrilha")
                    .technicalNotes("Traços firmes (Bold Line) e paleta clássica de alta durabilidade.")
                    .recommendedArtists(List.of("Rodrigo 'Old' Santos"))
                    .primaryColors(new ArrayList<>(List.of("Preto", "Vermelho", "Amarelo")))
                    .build();
        } else if (lower.contains("oriental") || lower.contains("irezumi") || lower.contains("anime") || lower.contains("dragão") || lower.contains("dragao") || lower.contains("neo tradicional")) {
            TattooSizeOption resolvedSize = sizeOpt.orElse(TattooSizeOption.GRANDE);
            return TattooAnalysisResult.builder()
                    .style("Oriental / Neo Tradicional")
                    .sizeCategory(resolvedSize.getCategoryKey())
                    .estimatedSizeCm(resolvedSize.getFormattedLabel())
                    .complexityLevel("ALTA")
                    .complexityMultiplier(1.35)
                    .estimatedHours(resolvedSize.getDefaultHours())
                    .suggestedBodyLocation("Braço, Costas ou Coxa")
                    .technicalNotes("Composição fluida respeitando a anatomia muscular com contraste marcante.")
                    .recommendedArtists(List.of("Gabriel Moura"))
                    .primaryColors(new ArrayList<>(List.of("Preto", "Colorido")))
                    .build();
        } else if (lower.contains("pontilhismo") || lower.contains("mandala")) {
            TattooSizeOption resolvedSize = sizeOpt.orElse(TattooSizeOption.MEDIA);
            return TattooAnalysisResult.builder()
                    .style("Pontilhismo & Mandala")
                    .sizeCategory(resolvedSize.getCategoryKey())
                    .estimatedSizeCm(resolvedSize.getFormattedLabel())
                    .complexityLevel("MEDIA")
                    .complexityMultiplier(1.2)
                    .estimatedHours(resolvedSize.getDefaultHours())
                    .suggestedBodyLocation("Antebraço, Esterno ou Ombro")
                    .technicalNotes("Simetria geométrica e sombreamento em pontilhismo fino.")
                    .recommendedArtists(List.of("Felipe Alcantara"))
                    .primaryColors(new ArrayList<>(List.of("Preto")))
                    .build();
        } else if (lower.contains("dark") || lower.contains("lettering") || lower.contains("chicano") || lower.contains("gótico") || lower.contains("gotico")) {
            TattooSizeOption resolvedSize = sizeOpt.orElse(TattooSizeOption.MEDIA);
            return TattooAnalysisResult.builder()
                    .style("Dark Art & Lettering")
                    .sizeCategory(resolvedSize.getCategoryKey())
                    .estimatedSizeCm(resolvedSize.getFormattedLabel())
                    .complexityLevel("MEDIA")
                    .complexityMultiplier(1.2)
                    .estimatedHours(resolvedSize.getDefaultHours())
                    .suggestedBodyLocation("Antebraço, Peito ou Panturrilha")
                    .technicalNotes("Traços expressivos e alto contraste em preto e cinza.")
                    .recommendedArtists(List.of("Thiago 'Dark' Souza"))
                    .primaryColors(new ArrayList<>(List.of("Preto")))
                    .build();
        } else if (lower.contains("fineline") || lower.contains("traço fino") || lower.contains("traco fino") || lower.contains("delicada") || lower.contains("frase")
                || (sizeOpt.isPresent() && (sizeOpt.get() == TattooSizeOption.FLASH || sizeOpt.get() == TattooSizeOption.PEQUENA))) {
            TattooSizeOption resolvedSize = sizeOpt.orElse(TattooSizeOption.PEQUENA);
            double hours = (resolvedSize == TattooSizeOption.FLASH) ? 1.0 : resolvedSize.getDefaultHours();
            return TattooAnalysisResult.builder()
                    .style("Fineline / Traço Fino")
                    .sizeCategory(resolvedSize.getCategoryKey())
                    .estimatedSizeCm(resolvedSize.getFormattedLabel())
                    .complexityLevel("BAIXA")
                    .complexityMultiplier(1.05)
                    .estimatedHours(hours)
                    .suggestedBodyLocation("Antebraço, Pulso ou Tornozelo")
                    .technicalNotes("Traço fino e delicado com agulha 03RL. Excelente cicatrização e definição para artes minimalistas.")
                    .recommendedArtists(List.of("Mariana Rocha"))
                    .primaryColors(new ArrayList<>(List.of("Preto")))
                    .build();
        } else if (sizeOpt.isPresent() && sizeOpt.get() == TattooSizeOption.FECHAMENTO) {
            return TattooAnalysisResult.builder()
                    .style("Projeto de Fechamento Autoral")
                    .sizeCategory(TattooSizeOption.FECHAMENTO.getCategoryKey())
                    .estimatedSizeCm(TattooSizeOption.FECHAMENTO.getFormattedLabel())
                    .complexityLevel("ALTA")
                    .complexityMultiplier(1.5)
                    .estimatedHours(TattooSizeOption.FECHAMENTO.getDefaultHours())
                    .suggestedBodyLocation("Braço, Perna ou Costas (A combinar)")
                    .technicalNotes("Projeto amplo de fechamento. Avaliação anatômica, encaixe e sessões definidos diretamente com o tatuador especialista.")
                    .recommendedArtists(List.of("Carlos Silveira"))
                    .primaryColors(new ArrayList<>(List.of("Preto", "Cinza")))
                    .build();
        }

        TattooSizeOption resolvedSize = sizeOpt.orElse(TattooSizeOption.MEDIA);
        return TattooAnalysisResult.builder()
                .style("Arte Personalizada")
                .sizeCategory(resolvedSize.getCategoryKey())
                .estimatedSizeCm(resolvedSize.getFormattedLabel())
                .complexityLevel("MEDIA")
                .complexityMultiplier(1.2)
                .estimatedHours(resolvedSize.getDefaultHours())
                .suggestedBodyLocation("Antebraço ou Panturrilha")
                .technicalNotes("Tatuagem com traços e sombreamento padrão do estúdio. Detalhes finais ajustados com o tatuador na sessão.")
                .recommendedArtists(List.of("Lucas 'Blade'", "Mariana Rocha"))
                .primaryColors(new ArrayList<>(List.of("Preto", "Cinza")))
                .build();
    }

    private String extractEffectiveStyleNotes(String customerNotes) {
        if (customerNotes == null || customerNotes.isBlank()) {
            return "";
        }
        if (customerNotes.contains("|")) {
            String[] segments = customerNotes.split("\\|");
            for (int i = segments.length - 1; i >= 0; i--) {
                String seg = segments[i].trim().toLowerCase();
                if (hasStyleKeyword(seg)) {
                    return seg;
                }
            }
        }
        return customerNotes.toLowerCase();
    }

    private boolean hasStyleKeyword(String lower) {
        return lower.contains("piercing") || lower.contains("furar") || lower.contains("joia")
                || lower.contains("realismo") || lower.contains("retrato") || lower.contains("leão") || lower.contains("leao") || lower.contains("lobo")
                || lower.contains("blackwork") || lower.contains("tribal") || lower.contains("geométrico") || lower.contains("geometrico")
                || lower.contains("aquarela") || lower.contains("colorid") || lower.contains("botânico") || lower.contains("botanico")
                || lower.contains("old school") || lower.contains("tradicional")
                || lower.contains("oriental") || lower.contains("irezumi") || lower.contains("anime") || lower.contains("dragão") || lower.contains("dragao") || lower.contains("neo tradicional")
                || lower.contains("pontilhismo") || lower.contains("mandala")
                || lower.contains("dark") || lower.contains("lettering") || lower.contains("chicano") || lower.contains("gótico") || lower.contains("gotico")
                || lower.contains("fineline") || lower.contains("traço fino") || lower.contains("traco fino") || lower.contains("delicada") || lower.contains("frase");
    }
}
