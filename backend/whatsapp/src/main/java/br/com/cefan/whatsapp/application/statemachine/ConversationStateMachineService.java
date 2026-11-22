package br.com.cefan.whatsapp.application.statemachine;

import br.com.cefan.whatsapp.application.service.ChatService;
import br.com.cefan.whatsapp.application.service.WhatsAppPollService;
import br.com.cefan.whatsapp.application.statemachine.handler.AgendamentoStateHandler;
import br.com.cefan.whatsapp.application.statemachine.handler.MenuStateHandler;
import br.com.cefan.whatsapp.domain.model.ConversationSession;
import br.com.cefan.whatsapp.domain.model.ConversationState;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.repository.ConversationSessionRepository;
import br.com.cefan.whatsapp.infrastructure.evolution.dto.EvolutionWebhookRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ConversationStateMachineService {

    private static final ObjectMapper CONTEXT_MAPPER = new ObjectMapper();

    private final ConversationSessionRepository sessionRepository;
    private final MenuStateHandler menuStateHandler;
    private final ChatService chatService;
    private final UserIntentNormalizer intentNormalizer;
    private final WhatsAppPollService pollService;
    private final Map<ConversationState, ConversationStateHandler> handlers = new EnumMap<>(ConversationState.class);

    public ConversationStateMachineService(
            ConversationSessionRepository sessionRepository,
            MenuStateHandler menuStateHandler,
            ChatService chatService,
            List<ConversationStateHandler> handlerList) {
        this(sessionRepository, menuStateHandler, chatService, new UserIntentNormalizer(), null, handlerList);
    }

    public ConversationStateMachineService(
            ConversationSessionRepository sessionRepository,
            MenuStateHandler menuStateHandler,
            ChatService chatService,
            UserIntentNormalizer intentNormalizer,
            List<ConversationStateHandler> handlerList) {
        this(sessionRepository, menuStateHandler, chatService, intentNormalizer, null, handlerList);
    }

    @Autowired
    public ConversationStateMachineService(
            ConversationSessionRepository sessionRepository,
            MenuStateHandler menuStateHandler,
            ChatService chatService,
            UserIntentNormalizer intentNormalizer,
            WhatsAppPollService pollService,
            List<ConversationStateHandler> handlerList) {
        this.sessionRepository = sessionRepository;
        this.menuStateHandler = menuStateHandler;
        this.chatService = chatService;
        this.intentNormalizer = (intentNormalizer != null) ? intentNormalizer : new UserIntentNormalizer();
        this.pollService = pollService;
        for (ConversationStateHandler handler : handlerList) {
            handlers.put(handler.getSupportedState(), handler);
        }
    }

    @Transactional
    public void process(Customer customer, EvolutionWebhookRequest webhook) {
        String messageText = webhook.extractMessageText();
        boolean hasImage = webhook.hasImage();

        if ((messageText == null || messageText.isBlank()) && !hasImage) {
            log.debug("Nenhum texto ou imagem processável para o cliente [{}]", customer.getPhone());
            return;
        }

        ConversationSession session = sessionRepository.findByCustomer(customer)
                .orElseGet(() -> {
                    log.info("Iniciando nova sessão de conversa para o cliente [{}]", customer.getPhone());
                    return sessionRepository.save(ConversationSession.builder()
                            .customer(customer)
                            .currentState(ConversationState.MENU)
                            .lastInteractionAt(OffsetDateTime.now(AgendamentoStateHandler.STUDIO_ZONE))
                            .build());
                });

        // 1. Registra mensagem recebida do cliente no histórico de chat
        String mediaUrl = null;
        String mimeType = webhook.extractImageMimeType();
        if (hasImage) {
            String rawBase64 = webhook.extractBase64();
            if (rawBase64 != null && !rawBase64.isBlank()) {
                mediaUrl = rawBase64.startsWith("data:") || rawBase64.startsWith("http")
                        ? rawBase64
                        : "data:" + (mimeType != null ? mimeType : "image/jpeg") + ";base64," + rawBase64.trim();
            } else {
                mediaUrl = "[Mídia/Imagem]";
            }
        }
        chatService.recordIncomingMessage(
                session,
                customer,
                messageText != null ? messageText : "[Imagem recebida]",
                mediaUrl,
                mimeType
        );

        // 2. Verificação de expiração por inatividade (24 horas)
        if (session.getLastInteractionAt() != null &&
                session.getLastInteractionAt().isBefore(OffsetDateTime.now(AgendamentoStateHandler.STUDIO_ZONE).minusHours(24))) {
            log.info("Sessão inativa há mais de 24h para cliente [{}]. Resetando para MENU.", customer.getPhone());
            session.setCurrentState(ConversationState.MENU);
        }

        // 3. Se o cliente enviou uma imagem diretamente no MENU, encaminha direto para o orçamento com IA
        if (session.getCurrentState() == ConversationState.MENU && hasImage) {
            log.info("Imagem recebida diretamente no estado MENU do cliente [{}]. Encaminhando para ORCAMENTO_AGUARDANDO_FOTO.", customer.getPhone());
            session.setCurrentState(ConversationState.ORCAMENTO_AGUARDANDO_FOTO);
        }

        // 4. Desativa enquetes ativas anteriores quando o cliente opta por responder via texto/imagem (fallback paralelo)
        if (pollService != null) {
            pollService.deactivateActivePolls(customer);
        }

        // 5. Normaliza a entrada em intenção canônica antes de chegar aos handlers
        boolean hasQuoteContext = hasExistingQuote(session.getContextData());
        UserIntent intent = hasImage
                ? UserIntent.DESCONHECIDA
                : intentNormalizer.normalize(session.getCurrentState(), hasQuoteContext, messageText);

        dispatchIntent(customer, session, webhook, messageText, intent);
    }

    /**
     * Processa um voto em enquete (sendPoll) já mapeado por ÍNDICE para {@link UserIntent}.
     * Reutiliza exatamente o mesmo despacho de intenções do fluxo de texto.
     */
    @Transactional
    public void processPollVote(String pollMessageId, int votedIndex, EvolutionWebhookRequest webhook) {
        if (pollService == null) {
            log.debug("WhatsAppPollService não configurado. Ignorando voto na enquete [{}].", pollMessageId);
            return;
        }
        pollService.resolveAndConsumeVote(pollMessageId, votedIndex).ifPresent(resolved -> {
            ConversationSession session = resolved.poll().getSession();
            Customer customer = resolved.poll().getCustomer();
            String label = resolved.label();
            UserIntent intent = resolved.intent();

            log.info("Voto na enquete [{}] do cliente [{}]: índice={} -> intenção={} ('{}')",
                    pollMessageId, customer.getPhone(), resolved.index(), intent, label);

            chatService.recordIncomingMessage(session, customer, "[Voto na enquete: " + label + "]", null, null);
            dispatchIntent(customer, session, webhook, label, intent);
        });
    }

    private void dispatchIntent(
            Customer customer,
            ConversationSession session,
            EvolutionWebhookRequest webhook,
            String messageText,
            UserIntent intent) {

        // Intenção VOLTAR ("0", "menu", "voltar", "Voltar ao menu", ou opção 3 / voto VOLTAR no pós-orçamento)
        if (intent == UserIntent.VOLTAR) {
            log.info("Intenção VOLTAR recebida de [{}] ('{}'). Resetando para MENU.", customer.getPhone(), messageText);
            session.setCurrentState(ConversationState.MENU);
            session.setContextData(null);
            session.setLastInteractionAt(OffsetDateTime.now(AgendamentoStateHandler.STUDIO_ZONE));
            sessionRepository.save(session);
            menuStateHandler.sendWelcomeMenu(customer, session, webhook.getInstance());
            return;
        }

        ConversationStateHandler handler = handlers.get(session.getCurrentState());
        if (handler != null) {
            handler.handle(customer, session, webhook, messageText, intent);
        } else {
            log.warn("Nenhum handler encontrado para o estado [{}]. Resetando para MENU.", session.getCurrentState());
            session.setCurrentState(ConversationState.MENU);
            menuStateHandler.sendWelcomeMenu(customer, session, webhook.getInstance());
        }

        session.setLastInteractionAt(OffsetDateTime.now(AgendamentoStateHandler.STUDIO_ZONE));
        sessionRepository.save(session);
    }

    private boolean hasExistingQuote(String contextData) {
        if (contextData == null || contextData.isBlank()) {
            return false;
        }
        try {
            JsonNode node = CONTEXT_MAPPER.readTree(contextData);
            return node.hasNonNull("style") && !node.get("style").asText().isBlank();
        } catch (Exception e) {
            return false;
        }
    }
}
