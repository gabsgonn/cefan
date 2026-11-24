package br.com.cefan.whatsapp.infrastructure.ai;

import br.com.cefan.whatsapp.domain.model.TattooSizeOption;
import br.com.cefan.whatsapp.domain.model.User;
import br.com.cefan.whatsapp.domain.model.UserRole;
import br.com.cefan.whatsapp.domain.repository.UserRepository;
import br.com.cefan.whatsapp.infrastructure.ai.dto.GeminiGenerateContentRequest;
import br.com.cefan.whatsapp.infrastructure.ai.dto.GeminiGenerateContentResponse;
import br.com.cefan.whatsapp.infrastructure.ai.dto.TattooAnalysisResult;
import br.com.cefan.whatsapp.application.statemachine.handler.QuoteMessageBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class GeminiVisionClient {

    private final RestClient restClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    @Getter
    private final String model;
    @Getter
    private final String fallbackModel;
    private final List<String> configuredModelChain;
    private final Map<String, Integer> modelTimeoutsMs;
    private final Map<Integer, RestClient> clientsByTimeout = new ConcurrentHashMap<>();
    @Getter
    private final int maxAttemptsPerModel;
    @Getter
    private final long initialBackoffMs;
    @Getter
    private final int timeoutMs;
    @Getter
    private final String thinkingLevel;
    private final UserRepository userRepository;

    public GeminiVisionClient(
            ObjectMapper objectMapper,
            String apiKey,
            String model,
            String baseUrl) {
        this(objectMapper, apiKey, model, null, baseUrl, null, 2, 300L, 18000, "low", null, null);
    }

    public GeminiVisionClient(
            ObjectMapper objectMapper,
            String apiKey,
            String model,
            String baseUrl,
            UserRepository userRepository) {
        this(objectMapper, apiKey, model, null, baseUrl, userRepository, 2, 300L, 18000, "low", null, null);
    }

    public GeminiVisionClient(
            ObjectMapper objectMapper,
            String apiKey,
            String model,
            String fallbackModel,
            String baseUrl,
            UserRepository userRepository,
            int maxAttemptsPerModel,
            long initialBackoffMs,
            int timeoutMs,
            String thinkingLevel) {
        this(objectMapper, apiKey, model, fallbackModel, baseUrl, userRepository,
                maxAttemptsPerModel, initialBackoffMs, timeoutMs, thinkingLevel, null, null);
    }

    @Autowired
    public GeminiVisionClient(
            ObjectMapper objectMapper,
            @Value("${gemini.api.key:}") String apiKey,
            @Value("${gemini.api.model:gemini-3.5-flash}") String model,
            @Value("${gemini.api.fallback-model:gemini-3.5-flash-lite}") String fallbackModel,
            @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            UserRepository userRepository,
            @Value("${gemini.api.max-attempts:2}") int maxAttemptsPerModel,
            @Value("${gemini.api.backoff-ms:500}") long initialBackoffMs,
            @Value("${gemini.api.timeout-ms:18000}") int timeoutMs,
            @Value("${gemini.api.thinking-level:low}") String thinkingLevel,
            @Value("${gemini.api.model-chain:}") String modelChainCsv,
            @Value("${gemini.api.model-timeouts-ms:}") String modelTimeoutsCsv) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.apiKey = (apiKey != null) ? apiKey.trim() : "";
        this.model = (model != null && !model.isBlank()) ? model.trim() : "gemini-3.5-flash";
        this.fallbackModel = resolveFallbackModel(this.model, fallbackModel);
        this.configuredModelChain = parseModelChain(modelChainCsv);
        this.modelTimeoutsMs = parseModelTimeouts(modelTimeoutsCsv);
        this.userRepository = userRepository;
        this.maxAttemptsPerModel = Math.max(1, maxAttemptsPerModel);
        this.initialBackoffMs = Math.max(0L, initialBackoffMs);
        this.timeoutMs = Math.max(1000, timeoutMs);
        this.thinkingLevel = (thinkingLevel != null) ? thinkingLevel.trim() : "";
        this.restClient = buildRestClient(baseUrl, this.timeoutMs);
    }

    private static RestClient buildRestClient(String baseUrl, int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(Math.min(5000, readTimeoutMs)));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    private static List<String> parseModelChain(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        Set<String> models = new LinkedHashSet<>();
        for (String token : csv.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                models.add(trimmed);
            }
        }
        return new ArrayList<>(models);
    }

    private static Map<String, Integer> parseModelTimeouts(String csv) {
        if (csv == null || csv.isBlank()) {
            return Map.of();
        }
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String entry : csv.split(",")) {
            String[] kv = entry.split("=");
            if (kv.length == 2) {
                String k = kv[0].trim();
                try {
                    int ms = Integer.parseInt(kv[1].trim());
                    if (!k.isEmpty() && ms >= 1000) {
                        map.put(k, ms);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return map;
    }

    public int resolveTimeoutMsForModel(String candidateModel) {
        if (candidateModel != null && modelTimeoutsMs.containsKey(candidateModel)) {
            return modelTimeoutsMs.get(candidateModel);
        }
        return timeoutMs;
    }

    private RestClient clientForModel(String candidateModel) {
        int modelTimeout = resolveTimeoutMsForModel(candidateModel);
        if (modelTimeout == this.timeoutMs) {
            return this.restClient;
        }
        return clientsByTimeout.computeIfAbsent(modelTimeout, t -> buildRestClient(this.baseUrl, t));
    }

    private static String resolveFallbackModel(String primaryModel, String configuredFallback) {
        if (configuredFallback != null && !configuredFallback.isBlank()) {
            return configuredFallback.trim();
        }
        if (primaryModel != null && !primaryModel.endsWith("-lite")) {
            return primaryModel + "-lite";
        }
        return "gemini-3.5-flash-lite";
    }

    public List<String> getModelChain() {
        if (!configuredModelChain.isEmpty()) {
            return new ArrayList<>(configuredModelChain);
        }
        Set<String> chain = new LinkedHashSet<>();
        if (model != null && !model.isBlank()) {
            chain.add(model);
        }
        if (fallbackModel != null && !fallbackModel.isBlank()) {
            chain.add(fallbackModel);
        }
        return new ArrayList<>(chain);
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

        List<String> modelsToTry = getModelChain();
        boolean sawTimeout = false;

        for (int modelIdx = 0; modelIdx < modelsToTry.size(); modelIdx++) {
            String candidateModel = modelsToTry.get(modelIdx);
            GeminiGenerateContentRequest requestBody = buildRequestForModel(textPart, imagePart, candidateModel);

            for (int attempt = 1; attempt <= maxAttemptsPerModel; attempt++) {
                long startNs = System.nanoTime();
                int effectiveTimeoutMs = resolveTimeoutMsForModel(candidateModel);
                log.info("GEMINI_STEP [action=REQUEST, model={}, attempt={}/{}, timeoutMs={}, base64Bytes={}, thinking={}]",
                        candidateModel, attempt, maxAttemptsPerModel, effectiveTimeoutMs, sanitizedBase64.length(),
                        resolveEffectiveThinkingDescription(candidateModel));

                Integer lastStatus = null;
                String lastContentType = null;
                String lastBodyPreview = null;
                try {
                    ResponseEntity<byte[]> entity = clientForModel(candidateModel).post()
                            .uri("/models/{model}:generateContent?key={apiKey}", candidateModel, apiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(requestBody)
                            .retrieve()
                            .toEntity(byte[].class);

                    long elapsedMs = Duration.ofNanos(System.nanoTime() - startNs).toMillis();
                    lastStatus = entity.getStatusCode().value();
                    lastContentType = entity.getHeaders().getContentType() != null
                            ? entity.getHeaders().getContentType().toString()
                            : "none";
                    byte[] rawBytes = entity.getBody();
                    String rawBody = (rawBytes != null) ? new String(rawBytes, StandardCharsets.UTF_8) : "";
                    lastBodyPreview = previewBodySafely(rawBody, apiKey);

                    if (!rawBody.isBlank()) {
                        GeminiGenerateContentResponse response = objectMapper.readValue(rawBody, GeminiGenerateContentResponse.class);
                        if (response != null) {
                            String rawJson = response.extractFirstText();
                            if (rawJson != null && !rawJson.isBlank()) {
                                String cleanJson = cleanJsonString(rawJson);
                                TattooAnalysisResult result = objectMapper.readValue(cleanJson, TattooAnalysisResult.class);
                                postProcessResult(result);
                                result.setFallback(false);
                                log.info("GEMINI_STEP [action=SUCCESS, model={}, attempt={}/{}, latencyMs={}, style={}, size={}, hours={}, artists={}]",
                                        candidateModel, attempt, maxAttemptsPerModel, elapsedMs,
                                        result.getStyle(), result.getSizeCategory(), result.getEstimatedHours(), result.getRecommendedArtists());
                                return result;
                            }
                        }
                    }

                    log.warn("GEMINI_STEP [action=EMPTY_RESPONSE, model={}, attempt={}/{}, status={}, contentType={}, latencyMs={}, bodyPreview={}]",
                            candidateModel, attempt, maxAttemptsPerModel, lastStatus, lastContentType, elapsedMs, lastBodyPreview);
                    break; // Sem resposta útil deste modelo -> avança para próximo modelo da cadeia
                } catch (HttpStatusCodeException httpEx) {
                    long elapsedMs = Duration.ofNanos(System.nanoTime() - startNs).toMillis();
                    int statusCode = httpEx.getStatusCode().value();
                    String ct = (httpEx.getResponseHeaders() != null && httpEx.getResponseHeaders().getContentType() != null)
                            ? httpEx.getResponseHeaders().getContentType().toString()
                            : "none";
                    String preview = previewBodySafely(httpEx.getResponseBodyAsString(), apiKey);
                    boolean retryableStatus = (statusCode == 429 || statusCode == 503);

                    if (retryableStatus && attempt < maxAttemptsPerModel) {
                        long backoff = initialBackoffMs * (1L << (attempt - 1));
                        log.warn("GEMINI_STEP [action=RETRY_BACKOFF, model={}, attempt={}/{}, status={}, contentType={}, backoffMs={}, latencyMs={}, bodyPreview={}]",
                                candidateModel, attempt, maxAttemptsPerModel, statusCode, ct, backoff, elapsedMs, preview);
                        sleepQuietly(backoff);
                        continue;
                    }

                    log.warn("GEMINI_STEP [action=MODEL_FAILED, model={}, attempt={}/{}, status={}, contentType={}, latencyMs={}, bodyPreview={}]",
                            candidateModel, attempt, maxAttemptsPerModel, statusCode, ct, elapsedMs, preview);
                    break;
                } catch (ResourceAccessException ioEx) {
                    long elapsedMs = Duration.ofNanos(System.nanoTime() - startNs).toMillis();
                    boolean isTimeout = isTimeoutException(ioEx);
                    if (isTimeout) {
                        sawTimeout = true;
                    }
                    log.warn("GEMINI_STEP [action={}, model={}, attempt={}/{}, latencyMs={}]: {}",
                            isTimeout ? "TIMEOUT" : "NETWORK_ERROR",
                            candidateModel, attempt, maxAttemptsPerModel, elapsedMs, previewBodySafely(ioEx.getMessage(), apiKey));
                    break;
                } catch (Exception e) {
                    long elapsedMs = Duration.ofNanos(System.nanoTime() - startNs).toMillis();
                    log.warn("GEMINI_STEP [action=UNEXPECTED_ERROR, model={}, attempt={}/{}, status={}, contentType={}, latencyMs={}, bodyPreview={}]: {}",
                            candidateModel, attempt, maxAttemptsPerModel, lastStatus, lastContentType, elapsedMs,
                            lastBodyPreview, previewBodySafely(e.getMessage(), apiKey));
                    break;
                }
            }

            if (modelIdx + 1 < modelsToTry.size()) {
                log.info("GEMINI_STEP [action=NEXT_MODEL_IN_CHAIN, failedModel={}, nextModel={}]",
                        candidateModel, modelsToTry.get(modelIdx + 1));
            }
        }

        String fallbackReason = sawTimeout ? "TIMEOUT_CHAIN_EXHAUSTED" : "CHAIN_EXHAUSTED";
        log.warn("GEMINI_FALLBACK ativado [motivo={}, modelsTried={}]: encaminhando para transbordo sem preço.",
                fallbackReason, modelsToTry);
        return createFallbackResult(customerNotes);
    }

    static String previewBodySafely(String body, String secretKey) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String sanitized = (secretKey != null && !secretKey.isBlank())
                ? body.replace(secretKey, "***")
                : body;
        sanitized = sanitized.replaceAll("key=[^&\\s\"']+", "key=***");
        return sanitized.length() > 300 ? sanitized.substring(0, 300) + "..." : sanitized;
    }

    private GeminiGenerateContentRequest buildRequestForModel(
            GeminiGenerateContentRequest.Part textPart,
            GeminiGenerateContentRequest.Part imagePart,
            String targetModel) {

        GeminiGenerateContentRequest.ThinkingConfig thinkingCfg = buildThinkingConfigForModel(targetModel);

        return GeminiGenerateContentRequest.builder()
                .contents(List.of(
                        GeminiGenerateContentRequest.Content.builder()
                                .parts(List.of(textPart, imagePart))
                                .build()
                ))
                .generationConfig(GeminiGenerateContentRequest.GenerationConfig.builder()
                        .responseMimeType("application/json")
                        .temperature(0.2)
                        .thinkingConfig(thinkingCfg)
                        .build())
                .build();
    }

    /**
     * Constrói o parâmetro de controle de raciocínio compatível com a geração do modelo:
     * - Gemini 3 / 3.5: {@code thinkingConfig.thinkingLevel} ("minimal", "low", "medium", "high")
     * - Gemini 2.5: {@code thinkingConfig.thinkingBudget} (0 para low/minimal, 1024 para medium)
     */
    GeminiGenerateContentRequest.ThinkingConfig buildThinkingConfigForModel(String targetModel) {
        if (thinkingLevel == null || thinkingLevel.isBlank() || "none".equalsIgnoreCase(thinkingLevel) || "default".equalsIgnoreCase(thinkingLevel)) {
            return null;
        }
        String normModel = (targetModel != null) ? targetModel.toLowerCase(Locale.ROOT) : "";
        String normLevel = thinkingLevel.toLowerCase(Locale.ROOT);

        if (normModel.contains("gemini-3") || normModel.contains("gemini-4")) {
            return GeminiGenerateContentRequest.ThinkingConfig.builder()
                    .thinkingLevel(normLevel)
                    .build();
        }
        if (normModel.contains("gemini-2.5")) {
            int budget = switch (normLevel) {
                case "minimal", "low", "0" -> 0;
                case "medium" -> 512;
                default -> 1024;
            };
            return GeminiGenerateContentRequest.ThinkingConfig.builder()
                    .thinkingBudget(budget)
                    .build();
        }
        return null;
    }

    private String resolveEffectiveThinkingDescription(String targetModel) {
        GeminiGenerateContentRequest.ThinkingConfig cfg = buildThinkingConfigForModel(targetModel);
        if (cfg == null) {
            return "default";
        }
        if (cfg.getThinkingLevel() != null) {
            return "thinkingLevel=" + cfg.getThinkingLevel();
        }
        return "thinkingBudget=" + cfg.getThinkingBudget();
    }

    private void postProcessResult(TattooAnalysisResult result) {
        if (result == null) {
            return;
        }
        // Limita sugestões de local a no máximo 2
        if (result.getSuggestedBodyLocation() != null && !result.getSuggestedBodyLocation().isBlank()) {
            List<String> limited = QuoteMessageBuilder.limitSuggestions(result.getSuggestedBodyLocation(), 2);
            if (!limited.isEmpty()) {
                result.setSuggestedBodyLocation(String.join(" ou ", limited));
            }
        }
        // Garante que customerDescription não fique vazia se a IA só preencheu um resumo e evita vazar technicalNotes
        if ((result.getCustomerDescription() == null || result.getCustomerDescription().isBlank())
                && result.getStyle() != null && !result.getStyle().isBlank()) {
            result.setCustomerDescription("Arte no estilo " + result.getStyle().trim()
                    + ", com composição e encaixe pensados para valorizar o traço na pele.");
        }
    }

    private boolean isTimeoutException(Throwable t) {
        Throwable curr = t;
        while (curr != null) {
            if (curr instanceof SocketTimeoutException) {
                return true;
            }
            String msg = curr.getMessage();
            if (msg != null && (msg.toLowerCase(Locale.ROOT).contains("timed out") || msg.toLowerCase(Locale.ROOT).contains("timeout"))) {
                return true;
            }
            curr = curr.getCause();
        }
        return false;
    }

    private void sleepQuietly(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public String buildPrompt(String customerNotes) {
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
        sb.append("  \"style\": \"Estilo predominante identificado (ex: Old School / Tradicional, Realismo Preto e Cinza, Fineline / Traço Fino, Blackwork / Geométrico, Aquarela & Floral, Oriental / Irezumi)\",\n");
        sb.append("  \"sizeCategory\": \"FLASH, PEQUENA, MEDIA, GRANDE ou FECHAMENTO\",\n");
        sb.append("  \"estimatedSizeCm\": \"Flash / Mini (até 5 cm), Pequena (6 a 10 cm), Média (11 a 18 cm), Grande (19 a 25 cm) ou Fechamento (26 cm+ / Projeto Amplo)\",\n");
        sb.append("  \"complexityLevel\": \"BAIXA, MEDIA ou ALTA\",\n");
        sb.append("  \"complexityMultiplier\": multiplicador decimal (1.0 a 1.1 para traço simples; 1.2 a 1.4 para sombreado/blackwork/old school; 1.5 a 1.8 para realismo detalhado),\n");
        sb.append("  \"estimatedHours\": número decimal com estimativa realista de horas (ex: 1.0, 1.5, 2.5, 4.0, 6.0),\n");
        sb.append("  \"detectedBodyLocation\": \"Parte do corpo visível na foto caso seja uma tatuagem na pele (ex: Antebraço, Panturrilha), ou null se for flash/desenho isolado no papel/fundo\",\n");
        sb.append("  \"suggestedBodyLocation\": \"NO MÁXIMO 2 sugestões de locais anatômicos ideais separados por 'ou' (ex: 'Antebraço ou Panturrilha') — nunca liste mais de 2\",\n");
        sb.append("  \"customerDescription\": \"1 a 2 frases curtas em linguagem simples e acolhedora para o cliente descrevendo o visual da arte (PROIBIDO mencionar códigos de agulhas como 03RL/Magnum ou jargões técnicos aqui)\",\n");
        sb.append("  \"technicalNotes\": \"2 a 3 frases técnicas exclusivas para o tatuador no painel interno (agulhas recomendadas ex: RL/RS/Magnum, técnica de pigmentação, sombreamento e cuidados)\",\n");
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
                    .suggestedBodyLocation("Orelha ou Nariz")
                    .customerDescription("Aplicação delicada com joia biocompatível e acabamento seguro.")
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
                    .suggestedBodyLocation("Antebraço ou Panturrilha")
                    .customerDescription("Arte realista em preto e cinza com riqueza de detalhes e profundidade visual.")
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
                    .suggestedBodyLocation("Antebraço ou Panturrilha")
                    .customerDescription("Composição marcante em preto sólido com contraste forte e linhas bem definidas.")
                    .technicalNotes("Preenchimento sólido em preto intenso e linhas geométricas precisas com RL e Magnum.")
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
                    .suggestedBodyLocation("Antebraço ou Ombro")
                    .customerDescription("Arte leve e vibrante com efeito fluido de cores que valoriza o movimento natural.")
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
                    .suggestedBodyLocation("Antebraço ou Panturrilha")
                    .customerDescription("Visual clássico tradicional com contornos marcantes e cores vibrantes de longa duração.")
                    .technicalNotes("Traços firmes (Bold Line com 09RL/11RL) e paleta clássica saturada com Magnum.")
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
                    .suggestedBodyLocation("Braço ou Costas")
                    .customerDescription("Arte expressiva com composição fluida que acompanha o formato do corpo.")
                    .technicalNotes("Composição fluida respeitando a anatomia muscular com contraste marcante e sombreado oriental.")
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
                    .suggestedBodyLocation("Antebraço ou Ombro")
                    .customerDescription("Desenho simétrico com efeito delicado de sombras feitas ponto a ponto.")
                    .technicalNotes("Simetria geométrica e sombreamento em pontilhismo fino com 03RL/05RL.")
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
                    .suggestedBodyLocation("Antebraço ou Panturrilha")
                    .customerDescription("Arte de personalidade forte com sombras intensas e visual expressivo.")
                    .technicalNotes("Traços expressivos e alto contraste em preto e cinza com whip shading.")
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
                    .suggestedBodyLocation("Antebraço ou Pulso")
                    .customerDescription("Arte minimalista de traço fino e delicado, ideal para um visual leve e elegante.")
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
                    .suggestedBodyLocation("Braço ou Costas")
                    .customerDescription("Projeto amplo pensado sob medida para cobrir a região escolhida com harmonia.")
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
                .customerDescription("Arte personalizada com composição equilibrada e acabamento pensado para a sua pele.")
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
