package br.com.cefan.whatsapp.application.statemachine;

/**
 * Intenções canônicas normalizadas antes de chegar aos handlers de estado.
 * Permite aceitar tanto entradas de texto (números/palavras) quanto respostas
 * de enquetes (sendPoll) sem reescrever a máquina de estados.
 */
public enum UserIntent {
    ORCAMENTO,
    AGENDAR,
    FALAR_TATUADOR,
    VER_ARTISTAS,
    VOLTAR,
    DESCONHECIDA
}
