package br.com.cefan.whatsapp.application.service;

import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.repository.CustomerRepository;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
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
    private final EvolutionApiClient evolutionApiClient;

    @Transactional
    public void handleIncomingMessage(EvolutionWebhookRequest webhook) {
        if (webhook.isFromMe()) {
            log.debug("Ignorando mensagem enviada por nós mesmos.");
            return;
        }

        String phone = webhook.extractSenderPhone();
        String messageText = webhook.extractMessageText();

        if (phone == null || messageText == null || messageText.isBlank()) {
            log.debug("Mensagem sem texto ou remetente inválido.");
            return;
        }

        String pushName = (webhook.getData() != null && webhook.getData().getPushName() != null)
                ? webhook.getData().getPushName()
                : "Cliente";

        log.info("Mensagem recebida de [{}] ({}): {}", pushName, phone, messageText);

        // Encontra ou cria o cliente no banco de dados do CEFAN
        Customer customer = customerRepository.findByPhone(phone)
                .orElseGet(() -> {
                    log.info("Cadastrando novo cliente no CEFAN: {} ({})", pushName, phone);
                    return customerRepository.save(Customer.builder()
                            .name(pushName)
                            .phone(phone)
                            .build());
                });

        // Resposta básica de boas-vindas / recepção (Fundação Semana 1)
        String replyText = String.format(
                "Olá, %s! 🎨 Bem-vindo ao estúdio!\n\n" +
                "Recebemos sua mensagem: \"%s\"\n\n" +
                "Em breve nosso assistente estará com o fluxo completo de orçamentos e agendamentos ativo.",
                customer.getName(), messageText
        );

        evolutionApiClient.sendTextMessage(webhook.getInstance(), phone, replyText);
    }
}
