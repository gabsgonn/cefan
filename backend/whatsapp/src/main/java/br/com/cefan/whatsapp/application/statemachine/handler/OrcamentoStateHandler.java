package br.com.cefan.whatsapp.application.statemachine.handler;

import br.com.cefan.whatsapp.application.statemachine.ConversationStateHandler;
import br.com.cefan.whatsapp.domain.model.ConversationSession;
import br.com.cefan.whatsapp.domain.model.ConversationState;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.model.TenantConfig;
import br.com.cefan.whatsapp.domain.repository.TenantConfigRepository;
import br.com.cefan.whatsapp.infrastructure.ai.GeminiVisionClient;
import br.com.cefan.whatsapp.infrastructure.ai.dto.TattooAnalysisResult;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import br.com.cefan.whatsapp.infrastructure.evolution.dto.EvolutionWebhookRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrcamentoStateHandler implements ConversationStateHandler {

    private final EvolutionApiClient evolutionApiClient;
    private final GeminiVisionClient geminiVisionClient;
    private final TenantConfigRepository tenantConfigRepository;
    private final ObjectMapper objectMapper;

    @Override
    public ConversationState getSupportedState() {
        return ConversationState.ORCAMENTO_AGUARDANDO_FOTO;
    }

    @Override
    public void handle(Customer customer, ConversationSession session, EvolutionWebhookRequest webhook, String messageText) {
        boolean hasImage = webhook.hasImage();

        if (hasImage) {
            log.info("Processando imagem de referência para o cliente [{}]", customer.getPhone());

            // 1. Obtenção do Base64 da imagem
            String base64Image = webhook.extractBase64();
            if (base64Image == null || base64Image.isBlank()) {
                Optional<String> downloaded = evolutionApiClient.getBase64FromMediaMessage(
                        webhook.getInstance(), webhook.extractMessageId());
                base64Image = downloaded.orElse(null);
            }

            // 2. Análise com IA de Visão (Gemini Vision)
            String mimeType = webhook.extractImageMimeType();
            TattooAnalysisResult analysis = geminiVisionClient.analyzeTattoo(base64Image, mimeType, messageText);

            // 3. Obtenção de parâmetros de precificação do estúdio
            TenantConfig tenantConfig = tenantConfigRepository.findAll().stream()
                    .findFirst()
                    .orElseGet(this::createDefaultTenantConfig);

            BigDecimal hourlyRate = tenantConfig.getDefaultHourlyRate();
            BigDecimal setupCost = tenantConfig.getFixedSetupCost();
            BigDecimal hours = BigDecimal.valueOf(analysis.getEstimatedHours() != null ? analysis.getEstimatedHours() : 2.0);
            BigDecimal multiplier = BigDecimal.valueOf(analysis.getComplexityMultiplier() != null ? analysis.getComplexityMultiplier() : 1.2);

            // Fórmula: Preço = (horas * taxa_hora * multiplicador) + custo_montagem
            BigDecimal basePrice = hours.multiply(hourlyRate).multiply(multiplier).add(setupCost).setScale(2, RoundingMode.HALF_UP);
            BigDecimal minPrice = basePrice.multiply(BigDecimal.valueOf(0.90)).setScale(0, RoundingMode.HALF_UP);
            BigDecimal maxPrice = basePrice.multiply(BigDecimal.valueOf(1.15)).setScale(0, RoundingMode.HALF_UP);

            // 4. Salva os dados de contexto na sessão
            saveContextData(session, analysis, basePrice, minPrice, maxPrice);

            // 5. Montagem da resposta para o WhatsApp
            String hoursFormatted = (analysis.getEstimatedHours() != null)
                    ? String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f", analysis.getEstimatedHours())
                    : "2,0";

            String reply = String.format(
                    "🎨 *Orçamento Estimado com Inteligência Artificial* 🎨\n\n" +
                    "Olá, %s! Analisamos os detalhes da sua arte de referência:\n\n" +
                    "📌 *Estilo:* %s\n" +
                    "⏱️ *Tempo Estimado:* ~%sh de agulha\n" +
                    "🔍 *Complexidade:* %s\n" +
                    "📍 *Local Sugerido:* %s\n\n" +
                    "📝 *Detalhes da Arte:*\n%s\n\n" +
                    "💰 *Faixa de Preço Estimada:*\n*R$ %s a R$ %s*\n" +
                    "_(Inclui materiais 100%% esterilizados, tintas regulamentadas e biossegurança)_\n\n" +
                    "━━━━━━━━━━━━━━━━━━━━\n" +
                    "👉 Digite *2* para *Agendar sua Sessão*\n" +
                    "👉 Digite *3* para *Falar com o Tatuador*\n" +
                    "👉 Digite *0* para voltar ao menu inicial",
                    customer.getName(),
                    analysis.getStyle(),
                    hoursFormatted,
                    analysis.getComplexityLevel(),
                    analysis.getSuggestedBodyLocation() != null ? analysis.getSuggestedBodyLocation() : "A combinar com o tatuador",
                    analysis.getTechnicalNotes(),
                    minPrice,
                    maxPrice
            );

            evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
        } else {
            // Cliente enviou apenas texto sem imagem
            log.info("Descrição em texto recebida no estado de orçamento do cliente [{}]", customer.getPhone());
            String reply = String.format(
                    "Recebemos sua descrição: \"%s\" 📝\n\n" +
                    "📸 Para calcularmos a estimativa de preço precisa com nossa IA, por favor envie uma *foto de referência* da tatuagem que você deseja fazer.\n\n" +
                    "👉 Ou digite *3* para falar diretamente com nosso tatuador.\n" +
                    "👉 Ou digite *0* para voltar ao menu inicial.",
                    (messageText != null ? messageText : "")
            );
            evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
        }
    }

    private void saveContextData(ConversationSession session, TattooAnalysisResult analysis,
                                 BigDecimal basePrice, BigDecimal minPrice, BigDecimal maxPrice) {
        try {
            Map<String, Object> context = new HashMap<>();
            context.put("style", analysis.getStyle());
            context.put("complexityLevel", analysis.getComplexityLevel());
            context.put("estimatedHours", analysis.getEstimatedHours());
            context.put("basePrice", basePrice);
            context.put("minPrice", minPrice);
            context.put("maxPrice", maxPrice);
            session.setContextData(objectMapper.writeValueAsString(context));
        } catch (Exception e) {
            log.warn("Erro ao serializar contextData para a sessão do cliente: {}", e.getMessage());
        }
    }

    private TenantConfig createDefaultTenantConfig() {
        return TenantConfig.builder()
                .studioName("Vício Tattoo")
                .defaultHourlyRate(new BigDecimal("150.00"))
                .fixedSetupCost(new BigDecimal("50.00"))
                .build();
    }
}
