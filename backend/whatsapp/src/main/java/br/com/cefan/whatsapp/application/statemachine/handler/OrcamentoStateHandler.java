package br.com.cefan.whatsapp.application.statemachine.handler;

import br.com.cefan.whatsapp.api.controller.StudioAdminController;
import br.com.cefan.whatsapp.application.service.ChatService;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class OrcamentoStateHandler implements ConversationStateHandler {

    private final EvolutionApiClient evolutionApiClient;
    private final GeminiVisionClient geminiVisionClient;
    private final TenantConfigRepository tenantConfigRepository;
    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final StudioAdminController studioAdminController;

    public OrcamentoStateHandler(
            EvolutionApiClient evolutionApiClient,
            GeminiVisionClient geminiVisionClient,
            TenantConfigRepository tenantConfigRepository,
            ObjectMapper objectMapper,
            ChatService chatService) {
        this(evolutionApiClient, geminiVisionClient, tenantConfigRepository, objectMapper, chatService, null);
    }

    @Autowired
    public OrcamentoStateHandler(
            EvolutionApiClient evolutionApiClient,
            GeminiVisionClient geminiVisionClient,
            TenantConfigRepository tenantConfigRepository,
            ObjectMapper objectMapper,
            ChatService chatService,
            StudioAdminController studioAdminController) {
        this.evolutionApiClient = evolutionApiClient;
        this.geminiVisionClient = geminiVisionClient;
        this.tenantConfigRepository = tenantConfigRepository;
        this.objectMapper = objectMapper;
        this.chatService = chatService;
        this.studioAdminController = studioAdminController;
    }

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
                        webhook.getInstance(),
                        webhook.extractMessageId(),
                        webhook.extractRemoteJid(),
                        webhook.isFromMe()
                );
                base64Image = downloaded.orElse(null);
            }

            // 2. Análise com IA de Visão (Gemini Vision)
            String mimeType = webhook.extractImageMimeType();
            String referenceImageUrl = null;
            if (base64Image != null && !base64Image.isBlank()) {
                referenceImageUrl = base64Image.startsWith("data:") || base64Image.startsWith("http")
                        ? base64Image.trim()
                        : "data:" + (mimeType != null ? mimeType : "image/jpeg") + ";base64," + base64Image.trim();
                chatService.attachMediaToLatestCustomerMessage(session.getId(), referenceImageUrl, mimeType);
            }

            TattooAnalysisResult analysis = geminiVisionClient.analyzeTattoo(base64Image, mimeType, messageText);

            // 3. Obtenção de parâmetros de precificação do estúdio
            TenantConfig tenantConfig = tenantConfigRepository.findAll().stream()
                    .findFirst()
                    .orElseGet(this::createDefaultTenantConfig);

            BigDecimal hourlyRate = tenantConfig.getDefaultHourlyRate();
            BigDecimal setupCost = tenantConfig.getFixedSetupCost();
            BigDecimal hours = BigDecimal.valueOf(analysis.getEstimatedHours() != null ? analysis.getEstimatedHours() : 1.5);
            BigDecimal multiplier = BigDecimal.valueOf(analysis.getComplexityMultiplier() != null ? analysis.getComplexityMultiplier() : 1.15);

            // Fórmula: Preço = (horas * taxa_hora * multiplicador) + custo_montagem
            BigDecimal basePrice = hours.multiply(hourlyRate).multiply(multiplier).add(setupCost).setScale(2, RoundingMode.HALF_UP);
            // Piso mínimo seguro para tatuagem pequena (evita valores absurdos ou irreais)
            if (hours.compareTo(new BigDecimal("1.0")) <= 0 && basePrice.compareTo(new BigDecimal("150.00")) < 0) {
                basePrice = new BigDecimal("150.00");
            }
            BigDecimal minPrice = basePrice.multiply(BigDecimal.valueOf(0.90)).setScale(0, RoundingMode.HALF_UP);
            BigDecimal maxPrice = basePrice.multiply(BigDecimal.valueOf(1.15)).setScale(0, RoundingMode.HALF_UP);

            // 4. Salva os dados de contexto na sessão
            saveContextData(session, analysis, basePrice, minPrice, maxPrice, referenceImageUrl);

            // 5. Montagem da resposta para o WhatsApp
            String hoursFormatted = (analysis.getEstimatedHours() != null)
                    ? String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f", analysis.getEstimatedHours())
                    : "1,5";

            String recommended = (analysis.getRecommendedArtists() != null && !analysis.getRecommendedArtists().isEmpty())
                    ? String.join(", ", analysis.getRecommendedArtists())
                    : "Carlos Silveira ou Mariana Rocha";

            String sizeInfo = (analysis.getEstimatedSizeCm() != null)
                    ? analysis.getEstimatedSizeCm() + " (" + (analysis.getSizeCategory() != null ? analysis.getSizeCategory() : "Proporcional") + ")"
                    : "Tamanho proporcional à referência";

            String reply = String.format(
                    "🎨 *Orçamento Estimado com Inteligência Artificial* 🎨\n\n" +
                    "Olá, %s! Analisamos os detalhes da sua arte de referência:\n\n" +
                    "📌 *Estilo:* %s\n" +
                    "📏 *Tamanho Estimado:* %s\n" +
                    "⏱️ *Tempo Estimado:* ~%sh de agulha\n" +
                    "🔍 *Complexidade:* %s\n" +
                    "📍 *Local Sugerido:* %s\n" +
                    "👨‍🎨 *Tatuador Especialista:* %s\n\n" +
                    "📝 *Detalhes da Arte:*\n%s\n\n" +
                    "💰 *Faixa de Preço Estimada:*\n*R$ %s a R$ %s*\n" +
                    "_(Inclui materiais 100%% descartáveis, esterilizados e biossegurança)_\n\n" +
                    "━━━━━━━━━━━━━━━━━━━━\n" +
                    "👉 Digite *2* para *Agendar sua Sessão*\n" +
                    "👉 Digite *3* para *Falar com o Tatuador*\n" +
                    "👉 Digite *0* para voltar ao menu inicial",
                    customer.getName(),
                    analysis.getStyle(),
                    sizeInfo,
                    hoursFormatted,
                    analysis.getComplexityLevel(),
                    analysis.getSuggestedBodyLocation() != null ? analysis.getSuggestedBodyLocation() : "A combinar com o tatuador",
                    recommended,
                    analysis.getTechnicalNotes(),
                    minPrice,
                    maxPrice
            );

            evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
            chatService.recordBotMessage(session, customer, reply);
        } else {
            // Cliente enviou apenas texto sem imagem
            String input = (messageText != null) ? messageText.trim().toLowerCase() : "";
            TenantConfig config = tenantConfigRepository.findAll().stream().findFirst().orElse(null);

            if (input.equals("3") || input.contains("tatuador") || input.contains("humano") || input.contains("falar")) {
                session.setCurrentState(ConversationState.TRANSBORDO_HUMANO);
                String customTemplate = (config != null && config.getHumanTransferMessage() != null && !config.getHumanTransferMessage().isBlank())
                        ? config.getHumanTransferMessage()
                        : "Notifiquei nossa equipe e um dos nossos tatuadores irá te responder diretamente por aqui em instantes.";
                String absenceNotice = AgendamentoStateHandler.buildAbsenceNoticeIfOutsideHours(config);
                String reply = String.format(
                        "Entendido, %s! 👨‍🎨\n\n" +
                        "%s%s\n\n" +
                        "💡 _Se quiser reiniciar o assistente automático, basta enviar 0 a qualquer momento._",
                        customer.getName(),
                        customTemplate,
                        absenceNotice
                );
                evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
                chatService.recordBotMessage(session, customer, reply);
                if (studioAdminController != null) {
                    studioAdminController.publishNotification(
                            "HUMAN_REQUEST",
                            "Aguardando Tatuador",
                            customer.getName() + " solicitou atendimento humano no WhatsApp.",
                            customer.getPhone()
                    );
                }
                return;
            }

            if (input.equals("2") || input.contains("agendar") || input.contains("agendamento")) {
                session.setCurrentState(ConversationState.AGENDAMENTO_SELECAO_DATA);
                String customTemplate = (config != null && config.getAgendamentoIntroMessage() != null && !config.getAgendamentoIntroMessage().isBlank())
                        ? config.getAgendamentoIntroMessage()
                        : "Vamos iniciar seu agendamento com base no seu orçamento.\n\n" +
                          "Nossa equipe conta com 11 artistas especialistas. Por favor, me informe:\n" +
                          "1. O tatuador de sua preferência (ou digite *qualquer* se preferir o especialista indicado)\n" +
                          "2. A data ou dia da semana desejado (Ex: sexta-feira à tarde, ou dia 20/11)";
                String reply = String.format(
                        "Ótimo, %s! 🗓️ %s\n\n" +
                        "💡 _Digite 0 a qualquer momento para voltar ao menu inicial._",
                        customer.getName(),
                        customTemplate
                );
                evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
                chatService.recordBotMessage(session, customer, reply);
                return;
            }

            log.info("Descrição em texto recebida no estado de orçamento do cliente [{}]", customer.getPhone());
            String reply = String.format(
                    "Recebemos sua descrição: \"%s\" 📝\n\n" +
                    "📸 Para calcularmos a estimativa de preço precisa com nossa IA, por favor envie uma *foto de referência* da tatuagem que você deseja fazer.\n\n" +
                    "👉 Ou digite *3* para falar diretamente com nosso tatuador.\n" +
                    "👉 Ou digite *0* para voltar ao menu inicial.",
                    (messageText != null ? messageText : "")
            );
            evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
            chatService.recordBotMessage(session, customer, reply);
        }
    }

    private void saveContextData(ConversationSession session, TattooAnalysisResult analysis,
                                 BigDecimal basePrice, BigDecimal minPrice, BigDecimal maxPrice,
                                 String referenceImageUrl) {
        try {
            Map<String, Object> context = new HashMap<>();
            context.put("style", analysis.getStyle());
            context.put("complexityLevel", analysis.getComplexityLevel());
            context.put("estimatedHours", analysis.getEstimatedHours());
            context.put("sizeCategory", analysis.getSizeCategory());
            context.put("estimatedSizeCm", analysis.getEstimatedSizeCm());
            context.put("recommendedArtists", analysis.getRecommendedArtists());
            context.put("suggestedBodyLocation", analysis.getSuggestedBodyLocation());
            context.put("technicalNotes", analysis.getTechnicalNotes());
            context.put("basePrice", basePrice);
            context.put("minPrice", minPrice);
            context.put("maxPrice", maxPrice);
            if (referenceImageUrl != null && !referenceImageUrl.isBlank()) {
                context.put("referenceImageUrl", referenceImageUrl);
            }
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
