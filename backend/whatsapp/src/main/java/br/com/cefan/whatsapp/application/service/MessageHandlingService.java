package br.com.cefan.whatsapp.application.service;

import br.com.cefan.whatsapp.application.statemachine.ConversationStateMachineService;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.repository.CustomerRepository;
import br.com.cefan.whatsapp.infrastructure.evolution.dto.EvolutionWebhookRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageHandlingService {

    private final CustomerRepository customerRepository;
    private final ConversationStateMachineService stateMachineService;

    @Transactional
    public void handleIncomingMessage(EvolutionWebhookRequest webhook) {
        if (webhook.isFromMe()) {
            log.debug("Ignorando mensagem enviada por nós mesmos.");
            return;
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
}
