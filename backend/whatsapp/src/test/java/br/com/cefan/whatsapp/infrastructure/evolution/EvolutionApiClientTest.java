package br.com.cefan.whatsapp.infrastructure.evolution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EvolutionApiClientTest {

    @Test
    void shouldNormalizeLiteralNewlinesWithoutBreakingRealOnes() {
        assertEquals(
                "Transbordo humano\n\nUm tatuador vai responder.\nLinha 3",
                EvolutionApiClient.normalizeNewlines("Transbordo humano\\n\\nUm tatuador vai responder.\\r\\nLinha 3")
        );
        assertEquals("Já tem\nquebra real", EvolutionApiClient.normalizeNewlines("Já tem\nquebra real"));
        assertEquals("", EvolutionApiClient.normalizeNewlines(""));
        assertNull(EvolutionApiClient.normalizeNewlines(null));
    }
}
