package br.com.cefan.whatsapp.application.statemachine;

import br.com.cefan.whatsapp.domain.model.ConversationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserIntentNormalizerTest {

    private final UserIntentNormalizer normalizer = new UserIntentNormalizer();

    @Test
    void shouldNormalizeMenuTextNumbersAndPollLabels() {
        assertEquals(UserIntent.ORCAMENTO, normalizer.normalize(ConversationState.MENU, false, "1"));
        assertEquals(UserIntent.ORCAMENTO, normalizer.normalize(ConversationState.MENU, false, "Fazer orçamento"));
        assertEquals(UserIntent.AGENDAR, normalizer.normalize(ConversationState.MENU, false, "2"));
        assertEquals(UserIntent.AGENDAR, normalizer.normalize(ConversationState.MENU, false, "Agendar sessão"));
        assertEquals(UserIntent.FALAR_TATUADOR, normalizer.normalize(ConversationState.MENU, false, "3"));
        assertEquals(UserIntent.FALAR_TATUADOR, normalizer.normalize(ConversationState.MENU, false, "Falar com o tatuador"));
        assertEquals(UserIntent.VER_ARTISTAS, normalizer.normalize(ConversationState.MENU, false, "4"));
        assertEquals(UserIntent.VER_ARTISTAS, normalizer.normalize(ConversationState.MENU, false, "Nossos artistas e portfólio"));
        assertEquals(UserIntent.DESCONHECIDA, normalizer.normalize(ConversationState.MENU, false, "oi"));
    }

    @Test
    void shouldNormalizePostQuoteThreeActionsAndRejectUnknownInputs() {
        assertEquals(UserIntent.AGENDAR, normalizer.normalize(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, true, "1"));
        assertEquals(UserIntent.AGENDAR, normalizer.normalize(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, true, "Agendar sessão"));
        assertEquals(UserIntent.FALAR_TATUADOR, normalizer.normalize(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, true, "2"));
        assertEquals(UserIntent.FALAR_TATUADOR, normalizer.normalize(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, true, "Falar com o tatuador"));
        assertEquals(UserIntent.VOLTAR, normalizer.normalize(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, true, "3"));
        assertEquals(UserIntent.VOLTAR, normalizer.normalize(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, true, "Voltar ao menu"));
        assertEquals(UserIntent.VOLTAR, normalizer.normalize(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, true, "0"));
        assertEquals(UserIntent.DESCONHECIDA, normalizer.normalize(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, true, "quero 2 tatuagens"));
        assertEquals(UserIntent.DESCONHECIDA, normalizer.normalize(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, true, "10cm"));
    }

    @Test
    void shouldNotTreatBareNumbersAsPostQuoteActionsBeforePhotoIsSent() {
        assertEquals(UserIntent.DESCONHECIDA, normalizer.normalize(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, false, "1"));
        assertEquals(UserIntent.DESCONHECIDA, normalizer.normalize(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, false, "2"));
        assertEquals(UserIntent.FALAR_TATUADOR, normalizer.normalize(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, false, "Falar com o tatuador"));
        assertEquals(UserIntent.VOLTAR, normalizer.normalize(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, false, "0"));
    }

    @Test
    void shouldUseExactMatchOnlyAndNeverTreat3AsVoltarOutsidePostQuote() {
        // No estado de agendamento (com orçamento no contexto), "3" NÃO pode virar VOLTAR
        assertEquals(UserIntent.DESCONHECIDA,
                normalizer.normalize(ConversationState.AGENDAMENTO_SELECAO_DATA, true, "3"));

        // Texto livre contendo "voltar" ou "menu" NÃO pode disparar VOLTAR
        assertEquals(UserIntent.DESCONHECIDA,
                normalizer.normalize(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, true, "quero voltar semana que vem"));
        assertEquals(UserIntent.DESCONHECIDA,
                normalizer.normalize(ConversationState.AGENDAMENTO_SELECAO_DATA, true, "não vi no menu esse horário"));
        assertEquals(UserIntent.DESCONHECIDA,
                normalizer.normalize(ConversationState.MENU, false, "quero falar sobre 2 tatuagens"));
        assertEquals(UserIntent.DESCONHECIDA,
                normalizer.normalize(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, true, "não sei se quero agendar agora"));
    }
}
