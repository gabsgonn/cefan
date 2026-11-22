package br.com.cefan.whatsapp.application.service;

import br.com.cefan.whatsapp.application.statemachine.ConversationStateMachineService;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.repository.CustomerRepository;
import br.com.cefan.whatsapp.infrastructure.evolution.dto.EvolutionWebhookRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageHandlingService {

    private final CustomerRepository customerRepository;
    private final ConversationStateMachineService stateMachineService;

    // Cache LRU simples para deduplicar webhooks repetidos do WhatsApp/Baileys
    private final Map<String, Long> processedMessagesCache = new ConcurrentHashMap<>();

    @Transactional
    public void handleIncomingMessage(EvolutionWebhookRequest webhook) {
        if (webhook == null) {
            return;
        }
        if (webhook.hasPollUpdates() || webhook.isPollUpdateMessage()) {
            handleMessageUpdate(webhook);
            return;
        }
        if (webhook.isFromMe()) {
            log.debug("Ignorando mensagem enviada por nós mesmos.");
            return;
        }

        String messageId = webhook.extractMessageId();
        if (messageId != null && !messageId.isBlank()) {
            long now = System.currentTimeMillis();
            // Limpa mensagens com mais de 2 minutos
            processedMessagesCache.entrySet().removeIf(entry -> now - entry.getValue() > 120_000);

            if (processedMessagesCache.putIfAbsent(messageId, now) != null) {
                log.info("Mensagem duplicada [{}] ignorada para evitar spam.", messageId);
                return;
            }
        }

        String phone = webhook.extractSenderPhone();
        String messageText = webhook.extractMessageText();
        boolean hasImage = webhook.hasImage();

        if (phone == null || ((messageText == null || messageText.isBlank()) && !hasImage)) {
            log.debug("Mensagem sem texto/mídia ou remetente inválido.");
            return;
        }

        String pushName = (webhook.getData() != null && webhook.getData().getPushName() != null)
                ? webhook.getData().getPushName()
                : "Cliente";

        log.info("Mensagem recebida de [{}] ({}): {}", pushName, phone, messageText != null ? messageText : "[Imagem]");

        // Encontra ou cria o cliente no banco de dados do CEFAN
        Customer customer = customerRepository.findByPhone(phone)
                .orElseGet(() -> {
                    log.info("Cadastrando novo cliente no CEFAN: {} ({})", pushName, phone);
                    return customerRepository.save(Customer.builder()
                            .name(pushName)
                            .phone(phone)
                            .build());
                });

        // Processa a mensagem através da Máquina de Estados da Conversa
        stateMachineService.process(customer, webhook);
    }

    /**
     * Trata eventos messages.update da Evolution API (votos em enquetes já decifrados pela Evolution 2.3.x).
     * Nota: não descarta fromMe=true aqui, pois a enquete original foi enviada pelo próprio bot.
     * Também não usa o remoteJid/sender do votante (que pode vir como @lid) para identificar o cliente —
     * o cliente é sempre identificado pela sessão salva na enquete (whatsapp_polls).
     */
    @Transactional
    public void handleMessageUpdate(EvolutionWebhookRequest webhook) {
        if (webhook == null) {
            log.info("POLL_UPDATE [action=IGNORED, reason=NULL_WEBHOOK, pollId=null, sessionState=N/A, expectedState=N/A]");
            return;
        }

        String pollMessageId = webhook.extractPollUpdateKeyId();
        if (!webhook.hasPollUpdates()) {
            log.info("POLL_UPDATE [action=IGNORED, reason=NO_POLL_UPDATES, pollId={}, sessionState=N/A, expectedState=N/A]",
                    pollMessageId);
            return;
        }

        if (pollMessageId == null || pollMessageId.isBlank()) {
            log.info("POLL_UPDATE [action=IGNORED, reason=MISSING_POLL_ID, pollId={}, sessionState=N/A, expectedState=N/A]",
                    pollMessageId);
            return;
        }

        java.util.OptionalInt votedIndexOpt = webhook.extractVotedPollOptionIndex();
        if (votedIndexOpt.isEmpty()) {
            log.info("POLL_UPDATE [action=IGNORED, reason=EMPTY_VOTE, pollId={}, sessionState=N/A, expectedState=N/A]",
                    pollMessageId);
            return;
        }

        stateMachineService.processPollVote(pollMessageId, votedIndexOpt.getAsInt(), webhook);
    }
}
