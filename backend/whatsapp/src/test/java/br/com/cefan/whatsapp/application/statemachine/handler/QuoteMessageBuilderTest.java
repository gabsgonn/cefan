package br.com.cefan.whatsapp.application.statemachine.handler;

import br.com.cefan.whatsapp.domain.model.TattooSizeOption;
import br.com.cefan.whatsapp.domain.model.TenantConfig;
import br.com.cefan.whatsapp.infrastructure.ai.dto.TattooAnalysisResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuoteMessageBuilderTest {

    private TattooAnalysisResult sampleAnalysis() {
        return TattooAnalysisResult.builder()
                .style("Old School / Tradicional")
                .sizeCategory("MEDIA")
                .estimatedSizeCm("Média (11 a 18 cm)")
                .estimatedHours(2.5)
                .complexityLevel("MEDIA")
                .complexityMultiplier(1.2)
                .detectedBodyLocation("Antebraço")
                .suggestedBodyLocation("Panturrilha, Braço, Costela, Coxa, Ombro")
                .customerDescription("Pantera tradicional com contornos marcantes e cores vibrantes de longa duração.")
                .technicalNotes("Agulhas 09RL para contorno bold line e Magnum 11MG para preenchimento sólido.")
                .recommendedArtists(List.of("Rodrigo Old School"))
                .fallback(false)
                .build();
    }

    private TenantConfig allTrueConfig() {
        return TenantConfig.builder()
                .studioName("Vício Tattoo")
                .build();
    }

    @Test
    void toggle1_orcamentoShowGreeting_shouldIncludeOrOmitGreetingLine() {
        TenantConfig cfg = allTrueConfig();
        String withGreeting = QuoteMessageBuilder.build(
                "Gabriel", sampleAnalysis(), cfg, TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));
        assertTrue(withGreeting.contains("Olá, Gabriel! Analisamos os detalhes da sua arte de referência:"));

        cfg.setOrcamentoShowGreeting(false);
        String withoutGreeting = QuoteMessageBuilder.build(
                "Gabriel", sampleAnalysis(), cfg, TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));
        assertFalse(withoutGreeting.contains("Olá, Gabriel!"));
        assertTrue(withoutGreeting.contains("📌 *Estilo:* Old School / Tradicional"));
    }

    @Test
    void toggle2_orcamentoShowStyle_shouldIncludeOrOmitStyleLine() {
        TenantConfig cfg = allTrueConfig();
        String withStyle = QuoteMessageBuilder.build(
                "Gabriel", sampleAnalysis(), cfg, TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));
        assertTrue(withStyle.contains("📌 *Estilo:* Old School / Tradicional"));

        cfg.setOrcamentoShowStyle(false);
        String withoutStyle = QuoteMessageBuilder.build(
                "Gabriel", sampleAnalysis(), cfg, TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));
        assertFalse(withoutStyle.contains("📌 *Estilo:*"));
        assertFalse(withoutStyle.contains("Old School / Tradicional"));
    }

    @Test
    void toggle3_orcamentoShowSize_shouldIncludeOrOmitSizeLine() {
        TenantConfig cfg = allTrueConfig();
        String withSize = QuoteMessageBuilder.build(
                "Gabriel", sampleAnalysis(), cfg, TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));
        assertTrue(withSize.contains("📏 *Tamanho Estimado:* Média (11 a 18 cm)"));

        cfg.setOrcamentoShowSize(false);
        String withoutSize = QuoteMessageBuilder.build(
                "Gabriel", sampleAnalysis(), cfg, TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));
        assertFalse(withoutSize.contains("📏 *Tamanho Estimado:*"));
    }

    @Test
    void toggle4_orcamentoShowDuration_shouldIncludeOrOmitDurationLine() {
        TenantConfig cfg = allTrueConfig();
        String withDuration = QuoteMessageBuilder.build(
                "Gabriel", sampleAnalysis(), cfg, TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));
        assertTrue(withDuration.contains("⏱️ *Tempo Estimado:* ~2,5h de agulha"));

        cfg.setOrcamentoShowDuration(false);
        String withoutDuration = QuoteMessageBuilder.build(
                "Gabriel", sampleAnalysis(), cfg, TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));
        assertFalse(withoutDuration.contains("⏱️ *Tempo Estimado:*"));
        assertFalse(withoutDuration.contains("de agulha"));
    }

    @Test
    void toggle5_orcamentoShowComplexity_shouldIncludeOrOmitComplexityLine() {
        TenantConfig cfg = allTrueConfig();
        String withComplexity = QuoteMessageBuilder.build(
                "Gabriel", sampleAnalysis(), cfg, TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));
        assertTrue(withComplexity.contains("🔍 *Complexidade:* MEDIA"));

        cfg.setOrcamentoShowComplexity(false);
        String withoutComplexity = QuoteMessageBuilder.build(
                "Gabriel", sampleAnalysis(), cfg, TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));
        assertFalse(withoutComplexity.contains("🔍 *Complexidade:*"));
    }

    @Test
    void toggle6_orcamentoShowLocation_shouldIncludeDetectedAndAtMostTwoSuggestionsOrOmitWhenFalse() {
        TenantConfig cfg = allTrueConfig();
        String withLocation = QuoteMessageBuilder.build(
                "Gabriel", sampleAnalysis(), cfg, TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));
        assertTrue(withLocation.contains("📍 *Local Detectado:* Antebraço"));
        assertTrue(withLocation.contains("Panturrilha ou Braço"));
        // Nunca deve mostrar uma lista de 5 locais
        assertFalse(withLocation.contains("Costela"));
        assertFalse(withLocation.contains("Coxa"));
        assertFalse(withLocation.contains("Ombro"));

        cfg.setOrcamentoShowLocation(false);
        String withoutLocation = QuoteMessageBuilder.build(
                "Gabriel", sampleAnalysis(), cfg, TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));
        assertFalse(withoutLocation.contains("📍 *Local"));
    }

    @Test
    void toggle7_orcamentoShowArtists_shouldIncludeOrOmitArtistsLine() {
        TenantConfig cfg = allTrueConfig();
        String withArtists = QuoteMessageBuilder.build(
                "Gabriel", sampleAnalysis(), cfg, TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));
        assertTrue(withArtists.contains("👨‍🎨 *Tatuador Especialista:* Rodrigo Old School"));

        cfg.setOrcamentoShowArtists(false);
        String withoutArtists = QuoteMessageBuilder.build(
                "Gabriel", sampleAnalysis(), cfg, TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));
        assertFalse(withoutArtists.contains("👨‍🎨 *Tatuador Especialista:*"));
        assertFalse(withoutArtists.contains("Rodrigo Old School"));
    }

    @Test
    void toggle8_orcamentoShowDescription_shouldShowCustomerDescriptionOnlyAndNeverLeakTechnicalNotes() {
        TenantConfig cfg = allTrueConfig();
        String withDescription = QuoteMessageBuilder.build(
                "Gabriel", sampleAnalysis(), cfg, TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));
        assertTrue(withDescription.contains("📝 *Sobre a sua arte:*"));
        assertTrue(withDescription.contains("Pantera tradicional com contornos marcantes"));
        // technicalNotes jamais deve ir ao cliente
        assertFalse(withDescription.contains("09RL"));
        assertFalse(withDescription.contains("11MG"));

        cfg.setOrcamentoShowDescription(false);
        String withoutDescription = QuoteMessageBuilder.build(
                "Gabriel", sampleAnalysis(), cfg, TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));
        assertFalse(withoutDescription.contains("📝 *Sobre a sua arte:*"));
        assertFalse(withoutDescription.contains("Pantera tradicional com contornos marcantes"));
        assertFalse(withoutDescription.contains("09RL"));
    }

    @Test
    void toggle9_orcamentoShowPrice_shouldIncludePriceAndMandatoryEstimateDisclaimerOrOmitBothWhenFalse() {
        TenantConfig cfg = allTrueConfig();
        String withPrice = QuoteMessageBuilder.build(
                "Gabriel", sampleAnalysis(), cfg, TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));
        assertTrue(withPrice.contains("💰 *Faixa de Preço Estimada:*"));
        assertTrue(withPrice.contains("*R$ 450 a R$ 575*"));
        // Aviso de estimativa não é parametrizável: sempre que o preço aparecer, ele deve aparecer
        assertTrue(withPrice.contains(QuoteMessageBuilder.ESTIMATE_DISCLAIMER));

        cfg.setOrcamentoShowPrice(false);
        String withoutPrice = QuoteMessageBuilder.build(
                "Gabriel", sampleAnalysis(), cfg, TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));
        assertFalse(withoutPrice.contains("💰 *Faixa de Preço Estimada:*"));
        assertFalse(withoutPrice.contains("R$ 450"));
        assertFalse(withoutPrice.contains(QuoteMessageBuilder.ESTIMATE_DISCLAIMER));
    }

    @Test
    void toggle10_orcamentoShowMaterialsNote_shouldIncludeOrOmitMaterialsNote() {
        TenantConfig cfg = allTrueConfig();
        String withMaterials = QuoteMessageBuilder.build(
                "Gabriel", sampleAnalysis(), cfg, TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));
        assertTrue(withMaterials.contains(QuoteMessageBuilder.MATERIALS_NOTE));

        cfg.setOrcamentoShowMaterialsNote(false);
        String withoutMaterials = QuoteMessageBuilder.build(
                "Gabriel", sampleAnalysis(), cfg, TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));
        assertFalse(withoutMaterials.contains(QuoteMessageBuilder.MATERIALS_NOTE));
        // Preço e aviso de estimativa continuam presentes
        assertTrue(withoutMaterials.contains("*R$ 450 a R$ 575*"));
        assertTrue(withoutMaterials.contains(QuoteMessageBuilder.ESTIMATE_DISCLAIMER));
    }

    @Test
    void toggle11_orcamentoShowAdjustHint_shouldIncludeOrOmitAdjustHint() {
        TenantConfig cfg = allTrueConfig();
        String withHint = QuoteMessageBuilder.build(
                "Gabriel", sampleAnalysis(), cfg, TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));
        assertTrue(withHint.contains(QuoteMessageBuilder.ADJUST_HINT));

        cfg.setOrcamentoShowAdjustHint(false);
        String withoutHint = QuoteMessageBuilder.build(
                "Gabriel", sampleAnalysis(), cfg, TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));
        assertFalse(withoutHint.contains(QuoteMessageBuilder.ADJUST_HINT));
        // As 3 ações pós-orçamento não são parametrizáveis e continuam presentes
        assertTrue(withoutHint.contains("1️⃣ *Agendar sessão*"));
        assertTrue(withoutHint.contains("2️⃣ *Falar com o tatuador*"));
        assertTrue(withoutHint.contains("3️⃣ *Voltar ao menu*"));
    }

    @Test
    void emptyFields_shouldOmitEntireLineEvenWhenToggleIsTrue() {
        TattooAnalysisResult sparse = TattooAnalysisResult.builder()
                .style("   ")
                .sizeCategory(null)
                .estimatedSizeCm("")
                .estimatedHours(null)
                .complexityLevel(null)
                .detectedBodyLocation(null)
                .suggestedBodyLocation("  ")
                .customerDescription("")
                .technicalNotes("Agulha 03RL")
                .recommendedArtists(List.of())
                .fallback(false)
                .build();

        String msg = QuoteMessageBuilder.build(
                "Gabriel", sparse, allTrueConfig(), null, new BigDecimal("200"), new BigDecimal("250"));

        assertFalse(msg.contains("📌 *Estilo:*"));
        assertFalse(msg.contains("📏 *Tamanho Estimado:*"));
        assertFalse(msg.contains("⏱️ *Tempo Estimado:*"));
        assertFalse(msg.contains("🔍 *Complexidade:*"));
        assertFalse(msg.contains("📍 *Local"));
        assertFalse(msg.contains("👨‍🎨 *Tatuador Especialista:*"));
        assertFalse(msg.contains("📝 *Sobre a sua arte:*"));
        assertFalse(msg.contains("03RL"));
        assertTrue(msg.contains("*R$ 200 a R$ 250*"));
        assertTrue(msg.contains("1️⃣ *Agendar sessão*"));
    }

    @Test
    void fallbackTrue_shouldIgnoreTogglesAndHidePriceDurationAndArtists() {
        TattooAnalysisResult fallbackAnalysis = sampleAnalysis();
        fallbackAnalysis.setFallback(true);

        String msg = QuoteMessageBuilder.build(
                "Gabriel", fallbackAnalysis, allTrueConfig(), TattooSizeOption.MEDIA, new BigDecimal("450"), new BigDecimal("575"));

        assertFalse(msg.contains("R$ 450"));
        assertFalse(msg.contains("R$ 575"));
        assertFalse(msg.contains("Faixa de Preço"));
        assertFalse(msg.contains("de agulha"));
        assertFalse(msg.contains("Tempo Estimado"));
        assertFalse(msg.contains("Tatuador Especialista"));
        assertFalse(msg.contains("Rodrigo Old School"));
        assertFalse(msg.contains("09RL"));
        // Mantém as 3 ações pós-orçamento
        assertTrue(msg.contains("1️⃣ *Agendar sessão*"));
        assertTrue(msg.contains("2️⃣ *Falar com o tatuador*"));
        assertTrue(msg.contains("3️⃣ *Voltar ao menu*"));
    }
}
