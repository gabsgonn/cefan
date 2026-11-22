package br.com.cefan.whatsapp.application.statemachine.handler;

import br.com.cefan.whatsapp.domain.model.TattooSizeOption;
import br.com.cefan.whatsapp.domain.model.TenantConfig;
import br.com.cefan.whatsapp.infrastructure.ai.dto.TattooAnalysisResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Montador puro da mensagem de orçamento enviada via WhatsApp.
 * Centraliza a formatação e os 11 toggles de exibição por tenant,
 * além do modo de contingência (fallback == true -> ignora toggles e omite preço/tempo/tatuadores).
 */
public final class QuoteMessageBuilder {

    public static final String HEADER = "🎨 *Orçamento Estimado com Inteligência Artificial* 🎨";
    public static final String ESTIMATE_DISCLAIMER =
            "⚠️ _Estimativa preliminar calculada por IA — o valor final será confirmado pelo tatuador._";
    public static final String MATERIALS_NOTE =
            "_(Inclui materiais 100% descartáveis, esterilizados e biossegurança)_";
    public static final String ADJUST_HINT =
            "💡 _Quer ajustar a estimativa? Informe a medida em cm (ex: 10cm) ou \"fechamento\"._";
    public static final String POST_QUOTE_SEPARATOR = "━━━━━━━━━━━━━━━━━━━━";
    public static final String POST_QUOTE_PROMPT = "👉 _Digite o número da opção desejada._";

    private QuoteMessageBuilder() {
    }

    /**
     * Função pura: (análise + config + dados de preço/cliente) -> texto formatado do orçamento.
     *
     * <p>Regras:</p>
     * <ul>
     *   <li>Se {@code analysis.isFallback() == true}, ignora os toggles e NÃO mostra preço, tempo ou tatuadores
     *       (modo transbordo / contingência).</li>
     *   <li>Campo vazio/nulo omite a linha inteira.</li>
     *   <li>Não parametrizáveis: aviso de estimativa ("estimativa, valor final confirmado pelo tatuador") sempre que
     *       o preço aparecer, e as 3 ações pós-orçamento.</li>
     *   <li>{@code orcamentoShowDescription} exibe apenas {@code customerDescription} (nunca {@code technicalNotes}).</li>
     *   <li>Local exibe {@code detectedBodyLocation} (se detectado na foto) e no máximo 2 sugestões de {@code suggestedBodyLocation}.</li>
     * </ul>
     */
    public static String build(
            String customerName,
            TattooAnalysisResult analysis,
            TenantConfig config,
            TattooSizeOption resolvedSize,
            BigDecimal minPrice,
            BigDecimal maxPrice) {

        String safeName = (customerName != null && !customerName.isBlank()) ? customerName.trim() : "Cliente";

        if (analysis == null || analysis.isFallback()) {
            return buildFallbackMessage(safeName, analysis);
        }

        boolean showGreeting = config == null || !Boolean.FALSE.equals(config.getOrcamentoShowGreeting());
        boolean showStyle = config == null || !Boolean.FALSE.equals(config.getOrcamentoShowStyle());
        boolean showSize = config == null || !Boolean.FALSE.equals(config.getOrcamentoShowSize());
        boolean showDuration = config == null || !Boolean.FALSE.equals(config.getOrcamentoShowDuration());
        boolean showComplexity = config == null || !Boolean.FALSE.equals(config.getOrcamentoShowComplexity());
        boolean showLocation = config == null || !Boolean.FALSE.equals(config.getOrcamentoShowLocation());
        boolean showArtists = config == null || !Boolean.FALSE.equals(config.getOrcamentoShowArtists());
        boolean showDescription = config == null || !Boolean.FALSE.equals(config.getOrcamentoShowDescription());
        boolean showPrice = config == null || !Boolean.FALSE.equals(config.getOrcamentoShowPrice());
        boolean showMaterialsNote = config == null || !Boolean.FALSE.equals(config.getOrcamentoShowMaterialsNote());
        boolean showAdjustHint = config == null || !Boolean.FALSE.equals(config.getOrcamentoShowAdjustHint());

        List<String> blocks = new ArrayList<>();
        blocks.add(HEADER);

        if (showGreeting) {
            blocks.add(String.format("Olá, %s! Analisamos os detalhes da sua arte de referência:", safeName));
        }

        List<String> metricLines = new ArrayList<>();

        if (showStyle && hasText(analysis.getStyle())) {
            metricLines.add("📌 *Estilo:* " + analysis.getStyle().trim());
        }

        if (showSize) {
            String sizeInfo = formatSizeInfo(analysis, resolvedSize);
            if (hasText(sizeInfo)) {
                metricLines.add("📏 *Tamanho Estimado:* " + sizeInfo);
            }
        }

        if (showDuration && analysis.getEstimatedHours() != null && analysis.getEstimatedHours() > 0) {
            String hoursFormatted = String.format(Locale.forLanguageTag("pt-BR"), "%.1f", analysis.getEstimatedHours());
            metricLines.add("⏱️ *Tempo Estimado:* ~" + hoursFormatted + "h de agulha");
        }

        if (showComplexity && hasText(analysis.getComplexityLevel())) {
            metricLines.add("🔍 *Complexidade:* " + analysis.getComplexityLevel().trim());
        }

        if (showLocation) {
            String locationLine = formatLocationLine(analysis.getDetectedBodyLocation(), analysis.getSuggestedBodyLocation());
            if (hasText(locationLine)) {
                metricLines.add(locationLine);
            }
        }

        if (showArtists && analysis.getRecommendedArtists() != null) {
            List<String> nonBlankArtists = analysis.getRecommendedArtists().stream()
                    .filter(QuoteMessageBuilder::hasText)
                    .map(String::trim)
                    .toList();
            if (!nonBlankArtists.isEmpty()) {
                metricLines.add("👨‍🎨 *Tatuador Especialista:* " + String.join(", ", nonBlankArtists));
            }
        }

        if (!metricLines.isEmpty()) {
            blocks.add(String.join("\n", metricLines));
        }

        // orcamentoShowDescription controla estritamente customerDescription (technicalNotes nunca vai para o cliente)
        if (showDescription && hasText(analysis.getCustomerDescription())) {
            blocks.add("📝 *Sobre a sua arte:*\n" + analysis.getCustomerDescription().trim());
        }

        boolean priceShown = false;
        if (showPrice && minPrice != null && maxPrice != null) {
            priceShown = true;
            StringBuilder priceBlock = new StringBuilder();
            priceBlock.append("💰 *Faixa de Preço Estimada:*\n");
            priceBlock.append("*R$ ").append(minPrice).append(" a R$ ").append(maxPrice).append("*\n");
            priceBlock.append(ESTIMATE_DISCLAIMER);
            if (showMaterialsNote) {
                priceBlock.append("\n").append(MATERIALS_NOTE);
            }
            blocks.add(priceBlock.toString());
        } else if (showMaterialsNote) {
            blocks.add(MATERIALS_NOTE);
        }

        if (showAdjustHint) {
            blocks.add(ADJUST_HINT);
        }

        // Não parametrizável: 3 ações pós-orçamento sempre presentes
        blocks.add(POST_QUOTE_SEPARATOR + "\n"
                + OrcamentoStateHandler.buildPostQuoteActionsBlock() + "\n\n"
                + POST_QUOTE_PROMPT);

        return String.join("\n\n", blocks);
    }

    private static String buildFallbackMessage(String customerName, TattooAnalysisResult analysis) {
        List<String> blocks = new ArrayList<>();
        blocks.add("🎨 *Recebemos sua Referência de Tatuagem* 🎨");
        blocks.add(String.format(
                "Olá, %s! Recebemos sua imagem, mas nossa análise automática está temporariamente indisponível no momento.",
                customerName));

        if (analysis != null) {
            List<String> basicLines = new ArrayList<>();
            if (hasText(analysis.getStyle()) && !"Arte Personalizada".equalsIgnoreCase(analysis.getStyle().trim())) {
                basicLines.add("📌 *Estilo Identificado:* " + analysis.getStyle().trim());
            }
            String locationLine = formatLocationLine(analysis.getDetectedBodyLocation(), analysis.getSuggestedBodyLocation());
            if (hasText(locationLine)) {
                basicLines.add(locationLine);
            }
            if (!basicLines.isEmpty()) {
                blocks.add(String.join("\n", basicLines));
            }
        }

        blocks.add("Encaminhamos sua foto para avaliação direta da nossa equipe — um tatuador irá confirmar o orçamento, o tempo de sessão e o especialista ideal para você por aqui!");
        blocks.add(POST_QUOTE_SEPARATOR + "\n"
                + OrcamentoStateHandler.buildPostQuoteActionsBlock() + "\n\n"
                + POST_QUOTE_PROMPT);

        return String.join("\n\n", blocks);
    }

    public static String formatSizeInfo(TattooAnalysisResult analysis, TattooSizeOption resolvedSize) {
        String rawCm = analysis != null ? analysis.getEstimatedSizeCm() : null;
        if (hasText(rawCm)) {
            if (resolvedSize == null
                    || TattooSizeOption.normalizeText(rawCm).contains(TattooSizeOption.normalizeText(resolvedSize.getDisplayName()))) {
                return rawCm.trim();
            }
            return resolvedSize.getFormattedLabel() + " — Ref: " + rawCm.trim();
        }
        return resolvedSize != null ? resolvedSize.getFormattedLabel() : null;
    }

    /**
     * Formata a linha de local do corpo: exibe o local detectado na foto (se presente e válido)
     * e no máximo 2 sugestões de local.
     */
    public static String formatLocationLine(String detectedBodyLocation, String suggestedBodyLocation) {
        String cleanDetected = sanitizeDetectedLocation(detectedBodyLocation);
        List<String> topSuggestions = limitSuggestions(suggestedBodyLocation, 2);

        if (cleanDetected != null && !topSuggestions.isEmpty()) {
            // Remove duplicata se a sugestão repetir exatamente o local detectado
            List<String> distinctSuggestions = topSuggestions.stream()
                    .filter(s -> !s.equalsIgnoreCase(cleanDetected))
                    .toList();
            if (distinctSuggestions.isEmpty()) {
                return "📍 *Local Detectado na Foto:* " + cleanDetected;
            }
            return "📍 *Local Detectado:* " + cleanDetected
                    + " _(Sugestões: " + String.join(" ou ", distinctSuggestions) + ")_";
        }
        if (cleanDetected != null) {
            return "📍 *Local Detectado na Foto:* " + cleanDetected;
        }
        if (!topSuggestions.isEmpty()) {
            return "📍 *Local Sugerido:* " + String.join(" ou ", topSuggestions);
        }
        return null;
    }

    private static String sanitizeDetectedLocation(String detected) {
        if (!hasText(detected)) {
            return null;
        }
        String trimmed = detected.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.equals("nenhum")
                || lower.equals("nao identificado")
                || lower.equals("não identificado")
                || lower.equals("nao detectado")
                || lower.equals("não detectado")
                || lower.equals("null")
                || lower.equals("n/a")
                || lower.contains("arte isolada")
                || lower.contains("papel")
                || lower.contains("fundo branco")) {
            return null;
        }
        return trimmed;
    }

    public static List<String> limitSuggestions(String rawSuggested, int maxCount) {
        if (!hasText(rawSuggested) || maxCount <= 0) {
            return List.of();
        }
        String normalized = rawSuggested.trim()
                .replaceAll("(?i)\\s+ou\\s+", ",")
                .replaceAll("(?i)\\s+e\\s+", ",")
                .replaceAll("[/;•]+", ",");
        String[] tokens = normalized.split(",");
        Set<String> unique = new LinkedHashSet<>();
        for (String token : tokens) {
            String cleaned = token.trim();
            if (!cleaned.isEmpty()) {
                unique.add(cleaned);
                if (unique.size() >= maxCount) {
                    break;
                }
            }
        }
        return new ArrayList<>(unique);
    }

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
