package br.com.cefan.whatsapp.application.statemachine;

import br.com.cefan.whatsapp.domain.model.ConversationSession;
import br.com.cefan.whatsapp.domain.model.ConversationState;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.infrastructure.evolution.dto.EvolutionWebhookRequest;

public interface ConversationStateHandler {
    ConversationState getSupportedState();
    void handle(Customer customer, ConversationSession session, EvolutionWebhookRequest webhook, String messageText);
}
