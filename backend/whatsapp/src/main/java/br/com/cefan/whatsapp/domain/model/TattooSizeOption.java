package br.com.cefan.whatsapp.domain.model;

import java.text.Normalizer;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum TattooSizeOption {

    FLASH("A", "FLASH", "Flash / Mini", "até 5 cm", 1.0, 1.05, false),
    PEQUENA("B", "PEQUENA", "Pequena", "6 a 10 cm", 1.5, 1.10, false),
    MEDIA("C", "MEDIA", "Média", "11 a 18 cm", 2.5, 1.20, false),
    GRANDE("D", "GRANDE", "Grande", "19 a 25 cm", 4.0, 1.35, false),
    FECHAMENTO("E", "FECHAMENTO", "Fechamento", "26 cm+ / Projeto Amplo", 6.0, 1.50, true);

    private static final Pattern CM_PATTERN = Pattern.compile("(\\d{1,3})(?:\\s*(?:[xX]|por)\\s*(\\d{1,3}))?\\s*(?:cm|cms|centimetro|centimetros)\\b");
    private static final Pattern FECHAMENTO_PATTERN = Pattern.compile(
            "\\b(?:fechamento|full\\s*sleeve|projeto\\s+amplo)\\b"
            + "|\\bfechar\\s+(?:o\\s+|a\\s+|as\\s+|os\\s+|meu\\s+|minha\\s+)?(?:braco|antebraco|perna|costas|peito|corpo|ombro)\\b"
            + "|\\b(?:braco|antebraco|perna|costas)\\s+(?:todo|toda|todos|todas|inteiro|inteira|inteiros|inteiras|fechado|fechada|fechados|fechadas)\\b"
    );
    private static final Pattern FLASH_WORD_PATTERN = Pattern.compile("\\b(?:flash|mini)\\b|\\bate\\s*5\\b");
    private static final Pattern PEQUENA_WORD_PATTERN = Pattern.compile("\\b(?:pequena|pequeno)\\b|\\b6\\s*a\\s*10\\b");
    private static final Pattern MEDIA_WORD_PATTERN = Pattern.compile("\\b(?:media|medio)\\b|\\b11\\s*a\\s*(?:18|15)\\b");
    private static final Pattern GRANDE_WORD_PATTERN = Pattern.compile("\\b(?:grande|grandes)\\b|\\b(?:19|16)\\s*a\\s*25\\b");

    private final String optionCode;
    private final String categoryKey;
    private final String displayName;
    private final String measurementLabel;
    private final double defaultHours;
    private final double defaultMultiplier;
    private final boolean priceACombinar;

    TattooSizeOption(
            String optionCode,
            String categoryKey,
            String displayName,
            String measurementLabel,
            double defaultHours,
            double defaultMultiplier,
            boolean priceACombinar) {
        this.optionCode = optionCode;
        this.categoryKey = categoryKey;
        this.displayName = displayName;
        this.measurementLabel = measurementLabel;
        this.defaultHours = defaultHours;
        this.defaultMultiplier = defaultMultiplier;
        this.priceACombinar = priceACombinar;
    }

    public String getOptionCode() {
        return optionCode;
    }

    public String getCategoryKey() {
        return categoryKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getMeasurementLabel() {
        return measurementLabel;
    }

    public String getFormattedLabel() {
        return displayName + " (" + measurementLabel + ")";
    }

    public double getDefaultHours() {
        return defaultHours;
    }

    public double getDefaultMultiplier() {
        return defaultMultiplier;
    }

    public boolean isPriceACombinar() {
        return priceACombinar;
    }

    public static String buildSizeMenuBlock() {
        return "📏 *Opções de Tamanho da Tatuagem:*\n" +
               "• Digite *A* (ou *Flash*) — *Flash / Mini* (até 5 cm)\n" +
               "• Digite *B* (ou *Pequena*) — *Pequena* (6 a 10 cm)\n" +
               "• Digite *C* (ou *Média*) — *Média* (11 a 18 cm)\n" +
               "• Digite *D* (ou *Grande*) — *Grande* (19 a 25 cm)\n" +
               "• Digite *E* (ou *Fechamento*) — *Fechamento* (26 cm+ / Projeto Amplo — _Valor a combinar_)";
    }

    public static Optional<TattooSizeOption> fromCategoryKey(String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return Optional.empty();
        }
        String upper = normalizeText(rawCategory).toUpperCase();
        if (upper.contains("FECHAMENTO")) {
            return Optional.of(FECHAMENTO);
        }
        if (upper.contains("FLASH") || upper.contains("MINI")) {
            return Optional.of(FLASH);
        }
        if (upper.contains("PEQUEN")) {
            return Optional.of(PEQUENA);
        }
        if (upper.contains("MEDI")) {
            return Optional.of(MEDIA);
        }
        if (upper.contains("GRAND")) {
            return Optional.of(GRANDE);
        }
        return Optional.empty();
    }

    public static Optional<TattooSizeOption> fromInput(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeText(rawInput);

        // Se houver múltiplos segmentos separados por "|", prioriza o segmento mais recente que contenha tamanho
        if (normalized.contains("|")) {
            String[] segments = rawInput.split("\\|");
            for (int i = segments.length - 1; i >= 0; i--) {
                Optional<TattooSizeOption> segmentMatch = parseSingleSegment(normalizeText(segments[i]));
                if (segmentMatch.isPresent()) {
                    return segmentMatch;
                }
            }
        }

        return parseSingleSegment(normalized);
    }

    /**
     * Reconhece apenas ajustes explícitos de tamanho por palavra natural ("flash", "pequena",
     * "média", "grande", "fechamento") ou medida em centímetros ("10cm", "20x15cm").
     * Não aceita letras isoladas (A-E) nem números isolados (1-5), evitando colisão com ações do menu.
     */
    public static Optional<TattooSizeOption> fromExplicitAdjustment(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return Optional.empty();
        }
        String clean = normalizeText(rawInput);
        if (clean.isBlank()) {
            return Optional.empty();
        }
        if (FECHAMENTO_PATTERN.matcher(clean).find()) {
            return Optional.of(FECHAMENTO);
        }
        if (GRANDE_WORD_PATTERN.matcher(clean).find()) {
            return Optional.of(GRANDE);
        }
        if (MEDIA_WORD_PATTERN.matcher(clean).find()) {
            return Optional.of(MEDIA);
        }
        if (PEQUENA_WORD_PATTERN.matcher(clean).find()) {
            return Optional.of(PEQUENA);
        }
        if (FLASH_WORD_PATTERN.matcher(clean).find()) {
            return Optional.of(FLASH);
        }
        return parseCentimeters(clean);
    }

    private static Optional<TattooSizeOption> parseCentimeters(String clean) {
        Matcher matcher = CM_PATTERN.matcher(clean);
        if (matcher.find()) {
            try {
                int d1 = Integer.parseInt(matcher.group(1));
                int d2 = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
                int maxCm = Math.max(d1, d2);
                if (maxCm >= 26) {
                    return Optional.of(FECHAMENTO);
                } else if (maxCm >= 19) {
                    return Optional.of(GRANDE);
                } else if (maxCm >= 11) {
                    return Optional.of(MEDIA);
                } else if (maxCm >= 6) {
                    return Optional.of(PEQUENA);
                } else if (maxCm >= 1) {
                    return Optional.of(FLASH);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return Optional.empty();
    }

    private static Optional<TattooSizeOption> parseSingleSegment(String clean) {
        if (clean == null || clean.isBlank()) {
            return Optional.empty();
        }

        // 1. Códigos diretos de opção (A, B, C, D, E ou "opcao X" / "tamanho X" / "letra X")
        if (clean.equals("a") || clean.equals("opcao a") || clean.equals("tamanho a") || clean.equals("letra a")
                || clean.equals("1") || clean.equals("opcao 1") || clean.equals("tamanho 1")
                || clean.matches("^a\\s*[-:).]\\s*.*")) {
            return Optional.of(FLASH);
        }
        if (clean.equals("b") || clean.equals("opcao b") || clean.equals("tamanho b") || clean.equals("letra b")
                || clean.equals("opcao 2") || clean.equals("tamanho 2")
                || clean.matches("^b\\s*[-:).]\\s*.*")) {
            return Optional.of(PEQUENA);
        }
        if (clean.equals("c") || clean.equals("opcao c") || clean.equals("tamanho c") || clean.equals("letra c")
                || clean.equals("opcao 3") || clean.equals("tamanho 3")
                || clean.matches("^c\\s*[-:).]\\s*.*")) {
            return Optional.of(MEDIA);
        }
        if (clean.equals("d") || clean.equals("opcao d") || clean.equals("tamanho d") || clean.equals("letra d")
                || clean.equals("4") || clean.equals("opcao 4") || clean.equals("tamanho 4")
                || clean.matches("^d\\s*[-:).]\\s*.*")) {
            return Optional.of(GRANDE);
        }
        if (clean.equals("e") || clean.equals("opcao e") || clean.equals("tamanho e") || clean.equals("letra e")
                || clean.equals("5") || clean.equals("opcao 5") || clean.equals("tamanho 5")
                || clean.matches("^e\\s*[-:).]\\s*.*")) {
            return Optional.of(FECHAMENTO);
        }

        // 2. Palavras-chave explícitas de tamanho com fronteira de palavra (evita falso-positivo em "feminino", "imediato", etc.)
        if (FECHAMENTO_PATTERN.matcher(clean).find()) {
            return Optional.of(FECHAMENTO);
        }
        if (GRANDE_WORD_PATTERN.matcher(clean).find()) {
            return Optional.of(GRANDE);
        }
        if (MEDIA_WORD_PATTERN.matcher(clean).find()) {
            return Optional.of(MEDIA);
        }
        if (PEQUENA_WORD_PATTERN.matcher(clean).find()) {
            return Optional.of(PEQUENA);
        }
        if (FLASH_WORD_PATTERN.matcher(clean).find()) {
            return Optional.of(FLASH);
        }

        // 3. Medida explícita em centímetros (ex: 4cm, 7cm, 15cm, 20x15cm, 30cm, 15 centímetros)
        Matcher matcher = CM_PATTERN.matcher(clean);
        if (matcher.find()) {
            try {
                int d1 = Integer.parseInt(matcher.group(1));
                int d2 = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
                int maxCm = Math.max(d1, d2);
                if (maxCm >= 26) {
                    return Optional.of(FECHAMENTO);
                } else if (maxCm >= 19) {
                    return Optional.of(GRANDE);
                } else if (maxCm >= 11) {
                    return Optional.of(MEDIA);
                } else if (maxCm >= 6) {
                    return Optional.of(PEQUENA);
                } else if (maxCm >= 1) {
                    return Optional.of(FLASH);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return Optional.empty();
    }

    public static String normalizeText(String input) {
        if (input == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(input.trim().toLowerCase(), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
