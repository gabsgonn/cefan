package br.com.cefan.whatsapp.application.statemachine.handler;

import br.com.cefan.whatsapp.application.statemachine.ConversationStateHandler;
import br.com.cefan.whatsapp.domain.model.ConversationSession;
import br.com.cefan.whatsapp.domain.model.ConversationState;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import br.com.cefan.whatsapp.infrastructure.evolution.dto.EvolutionWebhookRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MenuStateHandler implements ConversationStateHandler {

    private final EvolutionApiClient evolutionApiClient;

    @Override
    public ConversationState getSupportedState() {
        return ConversationState.MENU;
    }

    @Override
    public void handle(Customer customer, ConversationSession session, EvolutionWebhookRequest webhook, String messageText) {
        String input = (messageText != null) ? messageText.trim().toLowerCase() : "";

        if (input.equals("1") || input.contains("orçamento") || input.contains("orcamento")) {
            session.setCurrentState(ConversationState.ORCAMENTO_AGUARDANDO_FOTO);
            String reply = String.format(
                    "Perfeito, %s! 🎨\n\n" +
                    "Por favor, envie uma *foto da tatuagem* que você deseja fazer (ou uma referência visual) e nos diga:\n" +
                    "• O local do corpo onde deseja tatuar\n" +
                    "• O tamanho aproximado em centímetros (ex: 10cm x 5cm)\n\n" +
                    "💡 _Você pode digitar 0 a qualquer momento para voltar ao menu inicial._",
                    customer.getName()
            );
            evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
        } else if (input.equals("2") || input.contains("agendar") || input.contains("agendamento")) {
            session.setCurrentState(ConversationState.AGENDAMENTO_SELECAO_DATA);
            String reply = String.format(
                    "Ótimo, %s! 🗓️ Vamos iniciar seu agendamento.\n\n" +
                    "Por favor, me informe:\n" +
                    "1. O tatuador de sua preferência (ou digite *qualquer* se não tiver preferência)\n" +
                    "2. A data e período desejados (Ex: próxima sexta-feira à tarde, ou dia 20/10)\n\n" +
                    "💡 _Digite 0 a qualquer momento para voltar ao menu inicial._",
                    customer.getName()
            );
            evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
        } else if (input.equals("3") || input.contains("humano") || input.contains("tatuador") || input.contains("falar")) {
            session.setCurrentState(ConversationState.TRANSBORDO_HUMANO);
            String reply = String.format(
                    "Entendido, %s! 👨‍🎨\n\n" +
                    "Notifiquei nossa equipe e um dos nossos tatuadores irá te responder diretamente por aqui em instantes.\n\n" +
                    "💡 _Se quiser reiniciar o assistente automático, basta enviar 0 a qualquer momento._",
                    customer.getName()
            );
            evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
        } else {
            sendWelcomeMenu(customer, webhook.getInstance());
        }
    }

    public void sendWelcomeMenu(Customer customer, String instance) {
        String menu = String.format(
                "Olá, %s! 🎨 Bem-vindo ao *Vício Tattoo*!\n\n" +
                "Como posso te ajudar hoje?\n\n" +
                "1️⃣ - *Fazer Orçamento* (com foto de referência)\n" +
                "2️⃣ - *Agendar Sessão*\n" +
                "3️⃣ - *Falar com o Tatuador* (Atendimento Humano)\n\n" +
                "👉 _Digite o número da opção desejada._",
                customer.getName()
        );
        evolutionApiClient.sendTextMessage(instance, customer.getPhone(), menu);
    }
}
