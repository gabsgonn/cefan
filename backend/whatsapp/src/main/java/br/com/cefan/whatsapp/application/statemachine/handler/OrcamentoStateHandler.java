package br.com.cefan.whatsapp.application.statemachine.handler;

import br.com.cefan.whatsapp.api.controller.StudioAdminController;
import br.com.cefan.whatsapp.application.service.ChatService;
import br.com.cefan.whatsapp.application.service.WhatsAppPollService;
import br.com.cefan.whatsapp.application.statemachine.ConversationStateHandler;
import br.com.cefan.whatsapp.application.statemachine.UserIntent;
import br.com.cefan.whatsapp.application.statemachine.UserIntentNormalizer;
import br.com.cefan.whatsapp.domain.model.ConversationSession;
import br.com.cefan.whatsapp.domain.model.ConversationState;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.model.TattooSizeOption;
import br.com.cefan.whatsapp.domain.model.TenantConfig;
import br.com.cefan.whatsapp.domain.model.User;
import br.com.cefan.whatsapp.domain.model.UserRole;
import br.com.cefan.whatsapp.domain.repository.TenantConfigRepository;
import br.com.cefan.whatsapp.domain.repository.UserRepository;
import br.com.cefan.whatsapp.infrastructure.ai.GeminiVisionClient;
import br.com.cefan.whatsapp.infrastructure.ai.dto.TattooAnalysisResult;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import br.com.cefan.whatsapp.infrastructure.evolution.dto.EvolutionWebhookRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private final UserRepository userRepository;
    private final WhatsAppPollService pollService;

    public OrcamentoStateHandler(
            EvolutionApiClient evolutionApiClient,
            GeminiVisionClient geminiVisionClient,
            TenantConfigRepository tenantConfigRepository,
            ObjectMapper objectMapper,
            ChatService chatService) {
        this(evolutionApiClient, geminiVisionClient, tenantConfigRepository, objectMapper, chatService, null, null, null);
    }

    public OrcamentoStateHandler(
            EvolutionApiClient evolutionApiClient,
            GeminiVisionClient geminiVisionClient,
            TenantConfigRepository tenantConfigRepository,
            ObjectMapper objectMapper,
            ChatService chatService,
            StudioAdminController studioAdminController) {
        this(evolutionApiClient, geminiVisionClient, tenantConfigRepository, objectMapper, chatService, studioAdminController, null, null);
    }

    public OrcamentoStateHandler(
            EvolutionApiClient evolutionApiClient,
            GeminiVisionClient geminiVisionClient,
            TenantConfigRepository tenantConfigRepository,
            ObjectMapper objectMapper,
            ChatService chatService,
            StudioAdminController studioAdminController,
            UserRepository userRepository) {
        this(evolutionApiClient, geminiVisionClient, tenantConfigRepository, objectMapper, chatService, studioAdminController, userRepository, null);
    }

    @Autowired
    public OrcamentoStateHandler(
            EvolutionApiClient evolutionApiClient,
            GeminiVisionClient geminiVisionClient,
            TenantConfigRepository tenantConfigRepository,
            ObjectMapper objectMapper,
            ChatService chatService,
            StudioAdminController studioAdminController,
            UserRepository userRepository,
            WhatsAppPollService pollService) {
        this.evolutionApiClient = evolutionApiClient;
        this.geminiVisionClient = geminiVisionClient;
        this.tenantConfigRepository = tenantConfigRepository;
        this.objectMapper = objectMapper;
        this.chatService = chatService;
        this.studioAdminController = studioAdminController;
        this.userRepository = userRepository;
        this.pollService = pollService;
    }

    private static final UserIntentNormalizer FALLBACK_NORMALIZER = new UserIntentNormalizer();

    @Override
    public ConversationState getSupportedState() {
        return ConversationState.ORCAMENTO_AGUARDANDO_FOTO;
    }

    @Override
    public void handle(Customer customer, ConversationSession session, EvolutionWebhookRequest webhook, String messageText) {
        boolean hasQuote = hasExistingImageAnalysis(session.getContextData());
        UserIntent intent = webhook.hasImage()
                ? UserIntent.DESCONHECIDA
                : FALLBACK_NORMALIZER.normalize(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, hasQuote, messageText);
        handle(customer, session, webhook, messageText, intent);
    }

    public static final String PHOTO_RECEIVED_ACK_MESSAGE =
            "📸 Recebi sua foto! Estou analisando os detalhes da arte com nossa IA, só um instante... ✨";
    private static final int TYPING_PRESENCE_DELAY_MS = 15000;

    @Override
    public void handle(Customer customer, ConversationSession session, EvolutionWebhookRequest webhook, String messageText, UserIntent intent) {
        boolean hasImage = webhook.hasImage();
        TenantConfig tenantConfig = tenantConfigRepository.findAll().stream()
                .findFirst()
                .orElseGet(this::createDefaultTenantConfig);

        if (hasImage) {
            log.info("Processando imagem de referência para o cliente [{}]", customer.getPhone());

            // Feedback imediato + presença "digitando" (mitiga a percepção de latência de ~14s do Gemini)
            evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), PHOTO_RECEIVED_ACK_MESSAGE);
            chatService.recordBotMessage(session, customer, PHOTO_RECEIVED_ACK_MESSAGE);
            evolutionApiClient.sendTypingPresence(webhook.getInstance(), customer.getPhone(), TYPING_PRESENCE_DELAY_MS);

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

            String pendingNotes = extractStringFromContext(session.getContextData(), "pendingNotes");
            String preSelectedSize = extractStringFromContext(session.getContextData(), "selectedSizeCategory");
            String combinedNotes = buildCombinedNotes(pendingNotes, messageText, preSelectedSize);

            TattooAnalysisResult rawAnalysis = geminiVisionClient.analyzeTattoo(base64Image, mimeType, combinedNotes);
            final TattooAnalysisResult analysis = (rawAnalysis != null) ? rawAnalysis : createLocalFallbackAnalysis(combinedNotes);
            if (analysis.isFallback()) {
                log.warn("Orçamento gerado via FALLBACK_LOCAL para cliente [{}]: estilo=[{}], horas=[{}]",
                        customer.getPhone(), analysis.getStyle(), analysis.getEstimatedHours());
            } else {
                log.info("Orçamento gerado via GEMINI_VISION para cliente [{}]: estilo=[{}], horas=[{}]",
                        customer.getPhone(), analysis.getStyle(), analysis.getEstimatedHours());
            }

            // A IA estima o tamanho pela foto; só sobrescreve se o cliente informou medida/categoria explícita
            Optional<TattooSizeOption> userChosenSize = TattooSizeOption.fromExplicitAdjustment(messageText)
                    .or(() -> TattooSizeOption.fromCategoryKey(preSelectedSize))
                    .or(() -> TattooSizeOption.fromExplicitAdjustment(pendingNotes));

            TattooSizeOption resolvedSize = userChosenSize
                    .or(() -> TattooSizeOption.fromCategoryKey(analysis.getSizeCategory()))
                    .orElse(TattooSizeOption.MEDIA);

            if (resolvedSize == TattooSizeOption.FECHAMENTO) {
                handleFechamentoQuote(customer, session, webhook, analysis, referenceImageUrl, tenantConfig, combinedNotes);
                return;
            }

            if (userChosenSize.isPresent()) {
                boolean categoryChanged = analysis.getSizeCategory() == null
                        || !resolvedSize.getCategoryKey().equalsIgnoreCase(analysis.getSizeCategory().trim());
                analysis.setSizeCategory(resolvedSize.getCategoryKey());
                analysis.setEstimatedSizeCm(resolvedSize.getFormattedLabel());
                if (analysis.getEstimatedHours() == null || categoryChanged) {
                    analysis.setEstimatedHours(resolvedSize.getDefaultHours());
                }
            } else {
                analysis.setSizeCategory(resolvedSize.getCategoryKey());
                if (analysis.getEstimatedSizeCm() == null || analysis.getEstimatedSizeCm().isBlank()) {
                    analysis.setEstimatedSizeCm(resolvedSize.getFormattedLabel());
                }
            }

            sendStandardQuoteReply(customer, session, webhook, analysis, resolvedSize, referenceImageUrl, tenantConfig);
            return;
        }

        // Cliente enviou apenas texto sem imagem -> usa intenção normalizada (texto ou sendPoll)
        boolean hasQuote = hasExistingImageAnalysis(session.getContextData());
        UserIntent resolvedIntent = (intent != null)
                ? intent
                : FALLBACK_NORMALIZER.normalize(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, hasQuote, messageText);

        switch (resolvedIntent) {
            case AGENDAR -> {
                handleStartScheduling(customer, session, webhook, messageText, tenantConfig);
                return;
            }
            case FALAR_TATUADOR -> {
                handleHumanTransfer(customer, session, webhook, messageText, tenantConfig);
                return;
            }
            case VOLTAR -> {
                session.setCurrentState(ConversationState.MENU);
                session.setContextData(null);
                String reply = String.format(
                        "Voltando ao menu inicial, %s! 🔄\n\n" +
                        "1️⃣ - *Fazer Orçamento* (com foto de referência)\n" +
                        "2️⃣ - *Agendar Sessão*\n" +
                        "3️⃣ - *Falar com o Tatuador*\n" +
                        "4️⃣ - *Nossos Artistas e Portfólio*\n\n" +
                        "👉 _Digite o número da opção desejada._",
                        customer.getName());
                evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
                chatService.recordBotMessage(session, customer, reply);
                return;
            }
            default -> {
                // Segue para ajuste de estimativa, texto pré-foto ou tratamento de entrada inválida
            }
        }

        // Ajuste simples de estimativa por medida em cm (ex: "10cm") ou nome natural ("pequena", "grande", "fechamento")
        Optional<TattooSizeOption> sizeAdjustment = TattooSizeOption.fromExplicitAdjustment(messageText);
        if (sizeAdjustment.isPresent()) {
            TattooSizeOption sizeOption = sizeAdjustment.get();
            if (sizeOption == TattooSizeOption.FECHAMENTO) {
                TattooAnalysisResult existingOrFallback = reconstructAnalysisFromContext(session.getContextData(), messageText);
                String existingRefImage = extractStringFromContext(session.getContextData(), "referenceImageUrl");
                handleFechamentoQuote(customer, session, webhook, existingOrFallback, existingRefImage, tenantConfig, messageText);
                return;
            }
            if (hasQuote) {
                TattooAnalysisResult analysis = reconstructAnalysisFromContext(session.getContextData(), messageText);
                analysis.setSizeCategory(sizeOption.getCategoryKey());
                analysis.setEstimatedSizeCm(sizeOption.getFormattedLabel());
                if (!analysis.isFallback()) {
                    analysis.setEstimatedHours(sizeOption.getDefaultHours());
                }
                String existingRefImage = extractStringFromContext(session.getContextData(), "referenceImageUrl");
                sendStandardQuoteReply(customer, session, webhook, analysis, sizeOption, existingRefImage, tenantConfig);
                return;
            }
        }

        // Pós-orçamento com entrada inválida -> resposta amigável + reenvio das 3 ações, sem mudar de estado
        if (hasQuote) {
            String reply = "Não entendi sua resposta. 😅\n\n" +
                    "Escolha uma das opções abaixo para continuar com seu orçamento:\n\n" +
                    buildPostQuoteActionsBlock() + "\n\n" +
                    "💡 _Para ajustar a estimativa, informe o tamanho em cm (ex: 10cm), \"fechamento\" ou envie outra foto._";
            evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
            chatService.recordBotMessage(session, customer, reply);
            return;
        }

        // Pré-foto: descrição livre -> salva notas (e eventual tamanho citado) e pede a foto de referência sem bloco A-E
        log.info("Descrição em texto recebida no estado de orçamento do cliente [{}]", customer.getPhone());
        savePendingPreferences(session, sizeAdjustment.map(TattooSizeOption::getCategoryKey).orElse(null), messageText);
        String reply = String.format(
                "Recebemos sua descrição: \"%s\" 📝\n\n" +
                "📸 Para calcularmos a estimativa com nossa IA, envie uma *foto de referência* da tatuagem que você deseja fazer.\n\n" +
                "👉 Ou digite *falar com tatuador* para atendimento humano.\n" +
                "👉 Ou digite *0* para voltar ao menu inicial.",
                (messageText != null ? messageText : "")
        );
        evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
        chatService.recordBotMessage(session, customer, reply);
    }

    private void handleHumanTransfer(
            Customer customer,
            ConversationSession session,
            EvolutionWebhookRequest webhook,
            String messageText,
            TenantConfig tenantConfig) {
        session.setCurrentState(ConversationState.TRANSBORDO_HUMANO);
        assignRecommendedArtistIfPresent(session, messageText);

        String customTemplate = (tenantConfig != null && tenantConfig.getHumanTransferMessage() != null && !tenantConfig.getHumanTransferMessage().isBlank())
                ? tenantConfig.getHumanTransferMessage()
                : "Notifiquei nossa equipe e um dos nossos tatuadores irá te responder diretamente por aqui em instantes.";
        String absenceNotice = AgendamentoStateHandler.buildAbsenceNoticeIfOutsideHours(tenantConfig);
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
    }

    private void handleStartScheduling(
            Customer customer,
            ConversationSession session,
            EvolutionWebhookRequest webhook,
            String messageText,
            TenantConfig tenantConfig) {
        int durationHours = AgendamentoStateHandler.resolveDurationHours(session.getContextData(), objectMapper);
        if (!AgendamentoStateHandler.doesSessionFitInOperatingHours(durationHours, tenantConfig)) {
            session.setCurrentState(ConversationState.TRANSBORDO_HUMANO);
            assignRecommendedArtistIfPresent(session, messageText);
            String artistName = (session.getAssignedArtistName() != null && !session.getAssignedArtistName().isBlank())
                    ? session.getAssignedArtistName()
                    : "nossa equipe especialista";
            String absenceNotice = AgendamentoStateHandler.buildAbsenceNoticeIfOutsideHours(tenantConfig);
            String reply = String.format(
                    "🎨 *Sessão Especial (%dh) — Atendimento Personalizado* 🎨\n\n" +
                    "Olá, %s! Como o tempo estimado da sua sessão (*%dh*) ultrapassa nosso expediente diário (*%s às %s*), encaminhei sua conversa diretamente para *%s* organizar a melhor divisão de sessões e horários com você!%s\n\n" +
                    "💡 _Se quiser reiniciar o assistente automático, basta enviar 0 a qualquer momento._",
                    durationHours,
                    customer.getName(),
                    durationHours,
                    AgendamentoStateHandler.resolveOpeningTime(tenantConfig),
                    AgendamentoStateHandler.resolveClosingTime(tenantConfig),
                    artistName,
                    absenceNotice
            );
            evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
            chatService.recordBotMessage(session, customer, reply);
            if (studioAdminController != null) {
                studioAdminController.publishNotification(
                        "HUMAN_REQUEST",
                        "Sessão Longa (" + durationHours + "h)",
                        customer.getName() + " possui sessão estimada em " + durationHours + "h (maior que o expediente). Encaminhado para " + artistName + ".",
                        customer.getPhone()
                );
            }
            return;
        }

        session.setCurrentState(ConversationState.AGENDAMENTO_SELECAO_DATA);
        List<User> activeArtists = (userRepository != null)
                ? userRepository.findAll().stream()
                        .filter(u -> u.getRole() == UserRole.ARTIST && Boolean.TRUE.equals(u.getIsActive()))
                        .toList()
                : List.of();
        String reply = AgendamentoStateHandler.buildSchedulingEntryPrompt(
                customer, session, tenantConfig, activeArtists, objectMapper, true);
        evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
        chatService.recordBotMessage(session, customer, reply);
    }

    public static String buildPostQuoteActionsBlock() {
        return "1️⃣ *Agendar sessão*\n" +
               "2️⃣ *Falar com o tatuador*\n" +
               "3️⃣ *Voltar ao menu*";
    }

    private void sendStandardQuoteReply(
            Customer customer,
            ConversationSession session,
            EvolutionWebhookRequest webhook,
            TattooAnalysisResult analysis,
            TattooSizeOption resolvedSize,
            String referenceImageUrl,
            TenantConfig tenantConfig) {

        if (analysis != null && analysis.isFallback()) {
            saveContextData(session, analysis, null, null, null, referenceImageUrl, false, "A combinar");
            String fallbackReply = QuoteMessageBuilder.build(
                    customer.getName(), analysis, tenantConfig, resolvedSize, null, null);
            evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), fallbackReply);
            chatService.recordBotMessage(session, customer, fallbackReply);
            if (studioAdminController != null) {
                studioAdminController.publishNotification(
                        "HUMAN_REQUEST",
                        "Orçamento sem preço (Contingência IA)",
                        customer.getName() + " enviou foto de referência, mas a IA entrou em contingência (sem preço/tempo automático).",
                        customer.getPhone()
                );
            }
            sendPostQuotePollIfEnabled(customer, session, webhook);
            return;
        }

        // Quando a IA falhou ou o modelo retornou nulo, evitamos expor/persistir preço ou permitir recálculo por cm
        boolean showPrice = tenantConfig == null || !Boolean.FALSE.equals(tenantConfig.getOrcamentoShowPrice());

        BigDecimal hourlyRate = tenantConfig.getDefaultHourlyRate() != null
                ? tenantConfig.getDefaultHourlyRate()
                : new BigDecimal("150.00");
        BigDecimal setupCost = tenantConfig.getFixedSetupCost() != null
                ? tenantConfig.getFixedSetupCost()
                : new BigDecimal("50.00");
        BigDecimal hours = BigDecimal.valueOf(analysis.getEstimatedHours() != null
                ? analysis.getEstimatedHours()
                : resolvedSize.getDefaultHours());
        BigDecimal multiplier = BigDecimal.valueOf(analysis.getComplexityMultiplier() != null
                ? analysis.getComplexityMultiplier()
                : resolvedSize.getDefaultMultiplier());

        // Fórmula: Preço = (horas * taxa_hora * multiplicador) + custo_montagem
        BigDecimal basePrice = hours.multiply(hourlyRate).multiply(multiplier).add(setupCost).setScale(2, RoundingMode.HALF_UP);
        // Piso mínimo seguro para tatuagem pequena
        if (hours.compareTo(new BigDecimal("1.0")) <= 0 && basePrice.compareTo(new BigDecimal("150.00")) < 0) {
            basePrice = new BigDecimal("150.00");
        }
        BigDecimal minPrice = basePrice.multiply(BigDecimal.valueOf(0.90)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal maxPrice = basePrice.multiply(BigDecimal.valueOf(1.15)).setScale(0, RoundingMode.HALF_UP);

        if (analysis.getEstimatedHours() == null) {
            analysis.setEstimatedHours(hours.doubleValue());
        }

        // Se orcamentoShowPrice = false, ainda calculamos basePrice normalmente para uso no painel e no sinal PIX;
        // a ocultação do saldo restante no pré-agendamento/PIX é feita pelo AgendamentoStateHandler
        saveContextData(session, analysis, basePrice, minPrice, maxPrice, referenceImageUrl, false,
                "R$ " + minPrice.toPlainString().replace('.', ',') + " a R$ " + maxPrice.toPlainString().replace('.', ','));

        String reply = QuoteMessageBuilder.build(
                customer.getName(),
                analysis,
                tenantConfig,
                resolvedSize,
                minPrice,
                maxPrice
        );

        evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
        chatService.recordBotMessage(session, customer, reply);
        sendPostQuotePollIfEnabled(customer, session, webhook);
    }

    private void sendPostQuotePollIfEnabled(
            Customer customer,
            ConversationSession session,
            EvolutionWebhookRequest webhook) {
        if (pollService != null) {
            pollService.sendAndTrackPoll(
                    webhook.getInstance(),
                    customer,
                    session,
                    ConversationState.ORCAMENTO_AGUARDANDO_FOTO,
                    "O que você gostaria de fazer agora? (ou digite 1, 2 ou 3)",
                    List.of(
                            new WhatsAppPollService.PollOption(0, "Agendar sessão", UserIntent.AGENDAR),
                            new WhatsAppPollService.PollOption(1, "Falar com o tatuador", UserIntent.FALAR_TATUADOR),
                            new WhatsAppPollService.PollOption(2, "Voltar ao menu", UserIntent.VOLTAR)
                    )
            );
        }
    }

    private void handleFechamentoQuote(
            Customer customer,
            ConversationSession session,
            EvolutionWebhookRequest webhook,
            TattooAnalysisResult analysis,
            String referenceImageUrl,
            TenantConfig tenantConfig,
            String customerNotes) {

        if (analysis == null) {
            analysis = createLocalFallbackAnalysis(customerNotes);
        }
        analysis.setSizeCategory(TattooSizeOption.FECHAMENTO.getCategoryKey());
        analysis.setEstimatedSizeCm(TattooSizeOption.FECHAMENTO.getFormattedLabel());
        if (analysis.getComplexityLevel() == null || analysis.getComplexityLevel().isBlank()) {
            analysis.setComplexityLevel("ALTA");
        }
        if (analysis.getEstimatedHours() == null) {
            analysis.setEstimatedHours(TattooSizeOption.FECHAMENTO.getDefaultHours());
        }

        User assignedArtist = resolveSpecialistArtist(analysis, customerNotes);
        String artistName = resolveArtistDisplayName(assignedArtist, analysis);

        if (assignedArtist != null) {
            session.setAssignedUser(assignedArtist);
            analysis.setRecommendedArtists(List.of(artistName));
        } else if (analysis.getRecommendedArtists() == null || analysis.getRecommendedArtists().isEmpty()) {
            analysis.setRecommendedArtists(List.of(artistName));
        }
        session.setAssignedArtistName(artistName);
        session.setCurrentState(ConversationState.TRANSBORDO_HUMANO);

        saveContextData(session, analysis, null, null, null, referenceImageUrl, true, "A combinar");

        String absenceNotice = AgendamentoStateHandler.buildAbsenceNoticeIfOutsideHours(tenantConfig);
        String customerDesc = (analysis.getCustomerDescription() != null && !analysis.getCustomerDescription().isBlank())
                ? analysis.getCustomerDescription().trim()
                : "Projeto amplo de fechamento com avaliação anatômica, encaixe e planejamento de sessões diretamente com o especialista.";
        String locationInfo = QuoteMessageBuilder.formatLocationLine(
                analysis.getDetectedBodyLocation(),
                analysis.getSuggestedBodyLocation() != null ? analysis.getSuggestedBodyLocation() : "Braço ou Costas"
        );

        boolean showGreeting = tenantConfig == null || !Boolean.FALSE.equals(tenantConfig.getOrcamentoShowGreeting());
        boolean showStyle = tenantConfig == null || !Boolean.FALSE.equals(tenantConfig.getOrcamentoShowStyle());
        boolean showSize = tenantConfig == null || !Boolean.FALSE.equals(tenantConfig.getOrcamentoShowSize());
        boolean showComplexity = tenantConfig == null || !Boolean.FALSE.equals(tenantConfig.getOrcamentoShowComplexity());
        boolean showLocation = tenantConfig == null || !Boolean.FALSE.equals(tenantConfig.getOrcamentoShowLocation());
        boolean showArtists = tenantConfig == null || !Boolean.FALSE.equals(tenantConfig.getOrcamentoShowArtists());
        boolean showDescription = tenantConfig == null || !Boolean.FALSE.equals(tenantConfig.getOrcamentoShowDescription());
        boolean showPrice = tenantConfig == null || !Boolean.FALSE.equals(tenantConfig.getOrcamentoShowPrice());

        List<String> blocks = new ArrayList<>();
        blocks.add("🎨 *Orçamento de Projeto - Fechamento* 🎨");

        if (showGreeting) {
            blocks.add(String.format("Olá, %s! Identificamos que você escolheu a opção *Fechamento*:", customer.getName()));
        }

        List<String> metricLines = new ArrayList<>();
        if (showStyle) {
            metricLines.add("📌 *Estilo:* " + (analysis.getStyle() != null ? analysis.getStyle() : "Projeto de Fechamento Autoral"));
        }
        if (showSize) {
            metricLines.add("📏 *Tamanho Escolhido:* " + TattooSizeOption.FECHAMENTO.getFormattedLabel());
        }
        if (showComplexity && analysis.getComplexityLevel() != null && !analysis.getComplexityLevel().isBlank()) {
            metricLines.add("🔍 *Complexidade:* " + analysis.getComplexityLevel());
        }
        if (showLocation) {
            metricLines.add(locationInfo != null ? locationInfo : "📍 *Local Sugerido:* Braço ou Costas");
        }
        if (showArtists) {
            metricLines.add("👨‍🎨 *Tatuador Especialista:* " + artistName);
        }
        if (!metricLines.isEmpty()) {
            blocks.add(String.join("\n", metricLines));
        }

        if (showDescription) {
            blocks.add("📝 *Sobre a sua arte:*\n" + customerDesc);
        }

        if (showPrice) {
            blocks.add("💰 *Valor Estimado:*\n*A combinar*\n_(Projetos de fechamento possuem orçamento personalizado conforme anatomia e número de sessões)_");
        }

        blocks.add(String.format(
                "━━━━━━━━━━━━━━━━━━━━\n" +
                "🤝 *Encaminhado ao Tatuador Especialista:*\n" +
                "Sua conversa já foi vinculada e encaminhada diretamente para *%s*, que irá te responder por aqui em instantes para alinhar os detalhes do seu projeto!%s\n\n" +
                "💡 _Se quiser reiniciar o assistente automático, basta enviar 0 a qualquer momento._",
                artistName,
                absenceNotice
        ));

        String reply = String.join("\n\n", blocks);

        evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
        chatService.recordBotMessage(session, customer, reply);

        if (studioAdminController != null) {
            studioAdminController.publishNotification(
                    "HUMAN_REQUEST",
                    "Fechamento - " + artistName,
                    customer.getName() + " escolheu tamanho Fechamento (A combinar). Encaminhado para " + artistName + ".",
                    customer.getPhone()
            );
        }
    }

    private String formatSizeInfo(TattooAnalysisResult analysis, TattooSizeOption resolvedSize) {
        String rawCm = analysis.getEstimatedSizeCm();
        if (rawCm != null && !rawCm.isBlank()) {
            if (TattooSizeOption.normalizeText(rawCm).contains(TattooSizeOption.normalizeText(resolvedSize.getDisplayName()))) {
                return rawCm;
            }
            return resolvedSize.getFormattedLabel() + " — Ref: " + rawCm;
        }
        return resolvedSize.getFormattedLabel();
    }

    private User resolveSpecialistArtist(TattooAnalysisResult analysis, String customerNotes) {
        if (userRepository == null) {
            return null;
        }
        List<User> activeArtists = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.ARTIST && Boolean.TRUE.equals(u.getIsActive()))
                .toList();
        if (activeArtists.isEmpty()) {
            return null;
        }

        String latestNotes = extractMostRecentSegment(customerNotes).toLowerCase();

        // 1. Verifica se o cliente citou explicitamente o nome de um artista ou estilo na mensagem atual
        if (!latestNotes.isBlank()) {
            for (User artist : activeArtists) {
                String firstToken = artist.getName().split(" ")[0].toLowerCase().replace("'", "");
                if (latestNotes.contains(artist.getName().toLowerCase()) || latestNotes.contains(firstToken)) {
                    return artist;
                }
            }
            User byLatestStyle = matchArtistByStyleKeywords(activeArtists, latestNotes);
            if (byLatestStyle != null) {
                return byLatestStyle;
            }
        }

        // 2. Verifica recomendação direta da IA (da foto ou contexto reconstruído)
        if (analysis != null && analysis.getRecommendedArtists() != null) {
            for (String rec : analysis.getRecommendedArtists()) {
                if (rec == null || rec.isBlank()) continue;
                String recLower = rec.toLowerCase();
                for (User artist : activeArtists) {
                    String firstToken = artist.getName().split(" ")[0].toLowerCase().replace("'", "");
                    if (recLower.contains(artist.getName().toLowerCase()) || recLower.contains(firstToken)) {
                        return artist;
                    }
                }
            }
        }

        // 3. Verifica menção no estilo da análise + notas acumuladas
        String combined = ((analysis != null && analysis.getStyle() != null ? analysis.getStyle() : "")
                + " " + (customerNotes != null ? customerNotes : "")).toLowerCase();

        for (User artist : activeArtists) {
            String firstToken = artist.getName().split(" ")[0].toLowerCase().replace("'", "");
            if (combined.contains(firstToken)) {
                return artist;
            }
        }

        User byCombinedStyle = matchArtistByStyleKeywords(activeArtists, combined);
        if (byCombinedStyle != null) {
            return byCombinedStyle;
        }

        return activeArtists.get(0);
    }

    private User matchArtistByStyleKeywords(List<User> activeArtists, String textLower) {
        if (textLower == null || textLower.isBlank()) {
            return null;
        }
        for (User artist : activeArtists) {
            String spec = artist.getSpecialties() != null ? artist.getSpecialties().toLowerCase() : "";
            if ((textLower.contains("realismo") || textLower.contains("retrato") || textLower.contains("leão") || textLower.contains("leao") || textLower.contains("lobo")) && spec.contains("realismo")) {
                return artist;
            }
            if ((textLower.contains("fineline") || textLower.contains("traço fino") || textLower.contains("traco fino") || textLower.contains("delicada")) && spec.contains("fineline")) {
                return artist;
            }
            if ((textLower.contains("blackwork") || textLower.contains("tribal") || textLower.contains("geométrico") || textLower.contains("geometrico")) && spec.contains("blackwork")) {
                return artist;
            }
            if ((textLower.contains("aquarela") || textLower.contains("floral") || textLower.contains("botânico") || textLower.contains("botanico") || textLower.contains("colorid")) && spec.contains("aquarela")) {
                return artist;
            }
            if ((textLower.contains("old school") || textLower.contains("tradicional")) && spec.contains("old school")) {
                return artist;
            }
            if ((textLower.contains("oriental") || textLower.contains("irezumi") || textLower.contains("anime") || textLower.contains("dragão") || textLower.contains("dragao") || textLower.contains("neo tradicional")) && spec.contains("oriental")) {
                return artist;
            }
            if ((textLower.contains("pontilhismo") || textLower.contains("mandala")) && spec.contains("pontilhismo")) {
                return artist;
            }
            if ((textLower.contains("dark") || textLower.contains("lettering") || textLower.contains("chicano") || textLower.contains("gótico") || textLower.contains("gotico")) && spec.contains("dark")) {
                return artist;
            }
        }
        return null;
    }

    private String resolveArtistDisplayName(User assignedArtist, TattooAnalysisResult analysis) {
        if (assignedArtist != null && assignedArtist.getName() != null && !assignedArtist.getName().isBlank()) {
            return assignedArtist.getName();
        }
        if (analysis != null && analysis.getRecommendedArtists() != null && !analysis.getRecommendedArtists().isEmpty()) {
            String first = analysis.getRecommendedArtists().get(0);
            if (first != null && !first.isBlank()) {
                int parenIdx = first.indexOf('(');
                return parenIdx > 0 ? first.substring(0, parenIdx).trim() : first.trim();
            }
        }
        return "Carlos Silveira";
    }

    private void assignRecommendedArtistIfPresent(ConversationSession session, String customerNotes) {
        if (session.getAssignedUser() != null || (session.getAssignedArtistName() != null && !session.getAssignedArtistName().isBlank())) {
            return;
        }
        String pendingNotes = extractStringFromContext(session.getContextData(), "pendingNotes");
        if (!hasExistingImageAnalysis(session.getContextData()) && (pendingNotes == null || pendingNotes.isBlank()) && (customerNotes == null || customerNotes.isBlank())) {
            return;
        }
        TattooAnalysisResult analysis = reconstructAnalysisFromContext(session.getContextData(), customerNotes);
        User artist = resolveSpecialistArtist(analysis, customerNotes);
        if (artist != null) {
            session.setAssignedUser(artist);
            session.setAssignedArtistName(artist.getName());
        } else if (analysis.getRecommendedArtists() != null && !analysis.getRecommendedArtists().isEmpty()) {
            session.setAssignedArtistName(resolveArtistDisplayName(null, analysis));
        }
    }

    private boolean hasExistingImageAnalysis(String contextData) {
        if (contextData == null || contextData.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(contextData);
            return node.hasNonNull("style") && !node.get("style").asText().isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    private String extractStringFromContext(String contextData, String key) {
        if (contextData == null || contextData.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(contextData);
            if (node.hasNonNull(key)) {
                String val = node.get(key).asText();
                return (val != null && !val.isBlank()) ? val : null;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String buildCombinedNotes(String pendingNotes, String currentText, String preSelectedSize) {
        StringBuilder sb = new StringBuilder();
        if (pendingNotes != null && !pendingNotes.isBlank()) {
            sb.append(pendingNotes.trim());
        }
        if (currentText != null && !currentText.isBlank()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(currentText.trim());
        }
        if (preSelectedSize != null && !preSelectedSize.isBlank()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("Tamanho escolhido: ").append(preSelectedSize);
        }
        return sb.toString();
    }

    private void savePendingPreferences(ConversationSession session, String selectedSizeCategory, String notes) {
        try {
            Map<String, Object> context = new HashMap<>();
            if (session.getContextData() != null && !session.getContextData().isBlank()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> existing = objectMapper.readValue(session.getContextData(), Map.class);
                    context.putAll(existing);
                } catch (Exception ignored) {
                }
            }
            if (selectedSizeCategory != null && !selectedSizeCategory.isBlank()) {
                context.put("selectedSizeCategory", selectedSizeCategory);
                TattooSizeOption.fromCategoryKey(selectedSizeCategory).ifPresent(opt -> {
                    context.put("sizeCategory", opt.getCategoryKey());
                    context.put("estimatedSizeCm", opt.getFormattedLabel());
                });
            }
            if (notes != null && !notes.isBlank() && !isBareSizeChoice(notes)) {
                String existingNotes = context.get("pendingNotes") instanceof String s ? s : null;
                context.put("pendingNotes", (existingNotes != null && !existingNotes.isBlank())
                        ? existingNotes + " | " + notes.trim()
                        : notes.trim());
            }
            session.setContextData(objectMapper.writeValueAsString(context));
        } catch (Exception e) {
            log.warn("Erro ao salvar preferências pendentes de orçamento: {}", e.getMessage());
        }
    }

    private boolean isBareSizeChoice(String notes) {
        String norm = TattooSizeOption.normalizeText(notes);
        return norm.equals("a") || norm.equals("b") || norm.equals("c") || norm.equals("d") || norm.equals("e")
                || norm.equals("1") || norm.equals("4") || norm.equals("5")
                || norm.equals("flash") || norm.equals("mini") || norm.equals("pequena") || norm.equals("pequeno")
                || norm.equals("media") || norm.equals("medio") || norm.equals("grande") || norm.equals("fechamento")
                || norm.matches("^(?:opcao|tamanho|letra)\\s+[a-e1-5]$");
    }

    private TattooAnalysisResult reconstructAnalysisFromContext(String contextData, String customerNotes) {
        if (contextData != null && !contextData.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(contextData);
                if (node.hasNonNull("style")) {
                    List<String> recArtists = new ArrayList<>();
                    if (node.has("recommendedArtists") && node.get("recommendedArtists").isArray()) {
                        for (JsonNode item : node.get("recommendedArtists")) {
                            recArtists.add(item.asText());
                        }
                    }
                    TattooAnalysisResult reconstructed = TattooAnalysisResult.builder()
                            .style(node.path("style").asText("Arte Personalizada"))
                            .complexityLevel(node.path("complexityLevel").asText("MEDIA"))
                            .complexityMultiplier(node.hasNonNull("complexityMultiplier") ? node.get("complexityMultiplier").asDouble() : 1.2)
                            .estimatedHours(node.hasNonNull("estimatedHours") ? node.get("estimatedHours").asDouble() : 2.5)
                            .sizeCategory(node.path("sizeCategory").asText(null))
                            .estimatedSizeCm(node.path("estimatedSizeCm").asText(null))
                            .detectedBodyLocation(node.path("detectedBodyLocation").asText(null))
                            .suggestedBodyLocation(node.path("suggestedBodyLocation").asText("Antebraço ou Panturrilha"))
                            .customerDescription(node.path("customerDescription").asText(null))
                            .technicalNotes(node.path("technicalNotes").asText("Avaliação técnica realizada pela IA do estúdio."))
                            .recommendedArtists(recArtists)
                            .fallback(node.path("fallback").asBoolean(false))
                            .build();

                    applyExplicitStyleOverrideIfPresent(reconstructed, customerNotes);
                    return reconstructed;
                }
            } catch (Exception ignored) {
            }
        }
        String pendingNotes = extractStringFromContext(contextData, "pendingNotes");
        String combined = buildCombinedNotes(pendingNotes, customerNotes, null);
        TattooAnalysisResult fromClient = (geminiVisionClient != null)
                ? geminiVisionClient.createFallbackResult(combined)
                : null;
        TattooAnalysisResult resolved = fromClient != null ? fromClient : createLocalFallbackAnalysis(combined);
        applyExplicitStyleOverrideIfPresent(resolved, combined);
        return resolved;
    }

    private void applyExplicitStyleOverrideIfPresent(TattooAnalysisResult target, String notes) {
        if (target == null || notes == null || notes.isBlank()) {
            return;
        }
        String[] segments = notes.split("\\|");
        for (int i = segments.length - 1; i >= 0; i--) {
            String lower = segments[i].trim().toLowerCase();
            if (lower.contains("realismo") || lower.contains("retrato") || lower.contains("leão") || lower.contains("leao") || lower.contains("lobo")) {
                target.setStyle("Realismo Preto e Cinza");
                target.setRecommendedArtists(List.of("Carlos Silveira"));
                return;
            }
            if (lower.contains("fineline") || lower.contains("traço fino") || lower.contains("traco fino") || lower.contains("delicada")) {
                target.setStyle("Fineline / Traço Fino");
                target.setRecommendedArtists(List.of("Mariana Rocha"));
                return;
            }
            if (lower.contains("blackwork") || lower.contains("tribal") || lower.contains("geométrico") || lower.contains("geometrico")) {
                target.setStyle("Blackwork / Geométrico");
                target.setRecommendedArtists(List.of("Lucas 'Blade'"));
                return;
            }
            if (lower.contains("aquarela") || lower.contains("floral") || lower.contains("botânico") || lower.contains("botanico") || lower.contains("colorid")) {
                target.setStyle("Aquarela & Floral");
                target.setRecommendedArtists(List.of("Beatriz Lima"));
                return;
            }
            if (lower.contains("old school") || lower.contains("tradicional")) {
                target.setStyle("Old School / Tradicional");
                target.setRecommendedArtists(List.of("Rodrigo 'Old' Santos"));
                return;
            }
            if (lower.contains("oriental") || lower.contains("irezumi") || lower.contains("anime") || lower.contains("dragão") || lower.contains("dragao") || lower.contains("neo tradicional")) {
                target.setStyle("Oriental / Neo Tradicional");
                target.setRecommendedArtists(List.of("Gabriel Moura"));
                return;
            }
            if (lower.contains("pontilhismo") || lower.contains("mandala")) {
                target.setStyle("Pontilhismo & Mandala");
                target.setRecommendedArtists(List.of("Felipe Alcantara"));
                return;
            }
            if (lower.contains("dark") || lower.contains("lettering") || lower.contains("chicano") || lower.contains("gótico") || lower.contains("gotico")) {
                target.setStyle("Dark Art & Lettering");
                target.setRecommendedArtists(List.of("Thiago 'Dark' Souza"));
                return;
            }
        }
    }

    private String extractMostRecentSegment(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (text.contains("|")) {
            String[] parts = text.split("\\|");
            for (int i = parts.length - 1; i >= 0; i--) {
                String p = parts[i].trim();
                if (!p.isEmpty() && !p.toLowerCase().startsWith("tamanho escolhido:")) {
                    return p;
                }
            }
        }
        return text.trim();
    }

    private TattooAnalysisResult createLocalFallbackAnalysis(String notes) {
        TattooAnalysisResult base = TattooAnalysisResult.builder()
                .style("Projeto de Fechamento Autoral")
                .sizeCategory(TattooSizeOption.FECHAMENTO.getCategoryKey())
                .estimatedSizeCm(TattooSizeOption.FECHAMENTO.getFormattedLabel())
                .complexityLevel("ALTA")
                .complexityMultiplier(1.4)
                .estimatedHours(6.0)
                .suggestedBodyLocation("Braço, Perna ou Costas (A combinar)")
                .technicalNotes("Projeto personalizado. Anatomia, encaixe e sessões serão definidos diretamente com o tatuador especialista.")
                .recommendedArtists(List.of("Carlos Silveira"))
                .fallback(true)
                .build();
        applyExplicitStyleOverrideIfPresent(base, notes);
        return base;
    }

    private void saveContextData(ConversationSession session, TattooAnalysisResult analysis,
                                 BigDecimal basePrice, BigDecimal minPrice, BigDecimal maxPrice,
                                 String referenceImageUrl, boolean isFechamento, String priceDisplay) {
        try {
            Map<String, Object> context = new HashMap<>();
            context.put("style", analysis.getStyle());
            context.put("complexityLevel", analysis.getComplexityLevel());
            context.put("complexityMultiplier", analysis.getComplexityMultiplier());
            context.put("estimatedHours", analysis.getEstimatedHours());
            context.put("sizeCategory", analysis.getSizeCategory());
            context.put("estimatedSizeCm", analysis.getEstimatedSizeCm());
            context.put("recommendedArtists", analysis.getRecommendedArtists());
            context.put("detectedBodyLocation", analysis.getDetectedBodyLocation());
            context.put("suggestedBodyLocation", analysis.getSuggestedBodyLocation());
            context.put("customerDescription", analysis.getCustomerDescription());
            context.put("technicalNotes", analysis.getTechnicalNotes());
            context.put("fallback", analysis.isFallback());
            context.put("isFechamento", isFechamento);
            context.put("priceDisplay", priceDisplay);
            if (basePrice != null) {
                context.put("basePrice", basePrice);
            }
            if (minPrice != null) {
                context.put("minPrice", minPrice);
            }
            if (maxPrice != null) {
                context.put("maxPrice", maxPrice);
            }
            if (referenceImageUrl != null && !referenceImageUrl.isBlank()) {
                context.put("referenceImageUrl", referenceImageUrl);
            }
            if (session.getAssignedArtistName() != null && !session.getAssignedArtistName().isBlank()) {
                context.put("assignedArtistName", session.getAssignedArtistName());
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
