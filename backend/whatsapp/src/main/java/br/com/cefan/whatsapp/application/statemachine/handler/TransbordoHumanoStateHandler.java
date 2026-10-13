package br.com.cefan.whatsapp.application.statemachine.handler;

import br.com.cefan.whatsapp.application.statemachine.ConversationStateHandler;
import br.com.cefan.whatsapp.domain.model.ConversationSession;
import br.com.cefan.whatsapp.domain.model.ConversationState;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.infrastructure.evolution.dto.EvolutionWebhookRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TransbordoHumanoStateHandler implements ConversationStateHandler {

    @Override
    public ConversationState getSupportedState() {
        return ConversationState.TRANSBORDO_HUMANO;
    }

    @Override
    public void handle(Customer customer, ConversationSession session, EvolutionWebhookRequest webhook, String messageText) {
        log.info("Cliente [{}] está em TRANSBORDO_HUMANO. Mensagem recebida sem resposta automática do bot: {}",
                customer.getPhone(), messageText);
        // O bot fica em silêncio enquanto o cliente aguarda atendimento humano (evitando mensagens repetitivas)
    }
}
