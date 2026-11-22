package br.com.cefan.whatsapp.application.statemachine;

import br.com.cefan.whatsapp.domain.model.ConversationState;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * Normaliza entradas brutas do usuário (texto digitado ou rótulos de enquete)
 * em intenções canônicas ({@link UserIntent}) por comparação EXATA (sem {@code contains}),
 * evitando que texto livre contendo palavras como "menu" ou "voltar" dispare ações acidentais.
 */
@Component
public class UserIntentNormalizer {

    private static final Set<String> GLOBAL_BACK_EXACT = Set.of(
            "0", "menu", "voltar", "voltar ao menu", "voltar ao menu inicial", "inicio", "reiniciar"
    );

    private static final Set<String> MENU_ORCAMENTO_EXACT = Set.of(
            "1", "orcamento", "fazer orcamento", "solicitar orcamento", "solicitar orcamento com ia"
    );

    private static final Set<String> MENU_AGENDAR_EXACT = Set.of(
            "2", "agendar", "agendamento", "agendar sessao", "agendar horario"
    );

    private static final Set<String> MENU_FALAR_EXACT = Set.of(
            "3", "tatuador", "humano", "atendente", "falar",
            "falar com tatuador", "falar com o tatuador", "falar com um tatuador", "atendimento humano"
    );

    private static final Set<String> MENU_ARTISTAS_EXACT = Set.of(
            "4", "artistas", "equipe", "portfolio",
            "nossos artistas", "artistas e portfolio", "nossos artistas e portfolio"
    );

    private static final Set<String> POST_QUOTE_AGENDAR_EXACT = Set.of(
            "1", "agendar", "agendamento", "agendar sessao", "agendar horario", "agendar minha sessao"
    );

    private static final Set<String> POST_QUOTE_FALAR_EXACT = Set.of(
            "2", "tatuador", "humano", "atendente", "falar",
            "falar com tatuador", "falar com o tatuador", "falar com um tatuador"
    );

    private static final Set<String> POST_QUOTE_VOLTAR_EXACT = Set.of(
            "3", "voltar", "voltar ao menu", "voltar ao menu inicial", "menu"
    );

    private static final Set<String> PRE_PHOTO_FALAR_EXACT = Set.of(
            "tatuador", "humano", "atendente", "falar",
            "falar com tatuador", "falar com o tatuador", "falar com atendente"
    );

    private static final Set<String> PRE_PHOTO_AGENDAR_EXACT = Set.of(
            "agendar", "agendar sessao", "agendar horario", "quero agendar"
    );

    public UserIntent normalize(ConversationState state, boolean hasQuoteContext, String rawInput) {
        String clean = stripPunctuation(normalizeText(rawInput));
        if (clean.isBlank()) {
            return UserIntent.DESCONHECIDA;
        }

        // 1. Comando global de retorno ao menu — comparação estritamente exata
        if (GLOBAL_BACK_EXACT.contains(clean)) {
            return UserIntent.VOLTAR;
        }

        if (state == null) {
            return UserIntent.DESCONHECIDA;
        }

        return switch (state) {
            case MENU -> normalizeInMenu(clean);
            case ORCAMENTO_AGUARDANDO_FOTO -> normalizeInOrcamento(clean, hasQuoteContext);
            case AGENDAMENTO_SELECAO_DATA, TRANSBORDO_HUMANO -> UserIntent.DESCONHECIDA;
        };
    }

    private UserIntent normalizeInMenu(String clean) {
        if (MENU_ORCAMENTO_EXACT.contains(clean)) {
            return UserIntent.ORCAMENTO;
        }
        if (MENU_AGENDAR_EXACT.contains(clean)) {
            return UserIntent.AGENDAR;
        }
        if (MENU_FALAR_EXACT.contains(clean)) {
            return UserIntent.FALAR_TATUADOR;
        }
        if (MENU_ARTISTAS_EXACT.contains(clean)) {
            return UserIntent.VER_ARTISTAS;
        }
        return UserIntent.DESCONHECIDA;
    }

    private UserIntent normalizeInOrcamento(String clean, boolean hasQuoteContext) {
        // Pós-orçamento (3 ações fixas): 1 = Agendar, 2 = Falar com o tatuador, 3 = Voltar
        if (hasQuoteContext) {
            if (POST_QUOTE_AGENDAR_EXACT.contains(clean)) {
                return UserIntent.AGENDAR;
            }
            if (POST_QUOTE_FALAR_EXACT.contains(clean)) {
                return UserIntent.FALAR_TATUADOR;
            }
            if (POST_QUOTE_VOLTAR_EXACT.contains(clean)) {
                return UserIntent.VOLTAR;
            }
            return UserIntent.DESCONHECIDA;
        }

        // Pré-foto: aceita apenas comandos exatos de atalho (nunca números soltos)
        if (PRE_PHOTO_FALAR_EXACT.contains(clean)) {
            return UserIntent.FALAR_TATUADOR;
        }
        if (PRE_PHOTO_AGENDAR_EXACT.contains(clean)) {
            return UserIntent.AGENDAR;
        }
        return UserIntent.DESCONHECIDA;
    }

    private static String normalizeText(String raw) {
        if (raw == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return decomposed.toLowerCase(Locale.ROOT).trim();
    }

    private static String stripPunctuation(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
