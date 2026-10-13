package br.com.cefan.whatsapp.application.statemachine;

import br.com.cefan.whatsapp.application.statemachine.handler.MenuStateHandler;
import br.com.cefan.whatsapp.domain.model.ConversationSession;
import br.com.cefan.whatsapp.domain.model.ConversationState;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.repository.ConversationSessionRepository;
import br.com.cefan.whatsapp.infrastructure.evolution.dto.EvolutionWebhookRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ConversationStateMachineService {

    private final ConversationSessionRepository sessionRepository;
    private final MenuStateHandler menuStateHandler;
    private final Map<ConversationState, ConversationStateHandler> handlers = new EnumMap<>(ConversationState.class);

    public ConversationStateMachineService(
            ConversationSessionRepository sessionRepository,
            MenuStateHandler menuStateHandler,
            List<ConversationStateHandler> handlerList) {
        this.sessionRepository = sessionRepository;
        this.menuStateHandler = menuStateHandler;
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
                            .lastInteractionAt(OffsetDateTime.now())
                            .build());
                });

        // 1. Verificação de expiração por inatividade (24 horas)
        if (session.getLastInteractionAt() != null &&
                session.getLastInteractionAt().isBefore(OffsetDateTime.now().minusHours(24))) {
            log.info("Sessão inativa há mais de 24h para cliente [{}]. Resetando para MENU.", customer.getPhone());
            session.setCurrentState(ConversationState.MENU);
        }

        String trimmed = (messageText != null) ? messageText.trim() : "";

        // 2. Comandos globais de reset/menu ("0", "menu", "voltar", "início", "inicio")
        if (isResetCommand(trimmed)) {
            log.info("Comando de reset global recebido de [{}] ('{}'). Resetando para MENU.", customer.getPhone(), trimmed);
            session.setCurrentState(ConversationState.MENU);
            session.setLastInteractionAt(OffsetDateTime.now());
            sessionRepository.save(session);
            menuStateHandler.sendWelcomeMenu(customer, webhook.getInstance());
            return;
        }

        // 3. Execução do handler do estado atual
        ConversationStateHandler handler = handlers.get(session.getCurrentState());
        if (handler != null) {
            handler.handle(customer, session, webhook, messageText);
        } else {
            log.warn("Nenhum handler encontrado para o estado [{}]. Resetando para MENU.", session.getCurrentState());
            session.setCurrentState(ConversationState.MENU);
            menuStateHandler.sendWelcomeMenu(customer, webhook.getInstance());
        }

        session.setLastInteractionAt(OffsetDateTime.now());
        sessionRepository.save(session);
    }

    private boolean isResetCommand(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.equals("0") ||
               lower.equals("menu") ||
               lower.equals("voltar") ||
               lower.equals("inicio") ||
               lower.equals("início") ||
               lower.equals("reiniciar");
    }
}
