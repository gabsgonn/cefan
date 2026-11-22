package br.com.cefan.whatsapp.infrastructure.evolution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EvolutionApiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    @Test
    void shouldDeserializeRealEvolution237SendPollResponseAndExtractKeyIdWithoutJacksonConflict() throws Exception {
        String realEvolution237SendPollResponse = """
                {
                  "key": {
                    "remoteJid": "55419XXXXXXXX@s.whatsapp.net",
                    "fromMe": true,
                    "id": "3EB0F1A2B3C4D5E6F78901"
                  },
                  "pushName": "",
                  "status": "PENDING",
                  "message": {
                    "messageContextInfo": {
                      "messageSecret": "rK8vN1pX2mQ5zL9wT4yB7cH0jF3dG6sA8eU1iO4pP7k="
                    },
                    "pollCreationMessageV3": {
                      "name": "O que você gostaria de fazer agora? (ou digite 1, 2 ou 3)",
                      "options": [
                        { "optionName": "Agendar sessão" },
                        { "optionName": "Falar com o tatuador" },
                        { "optionName": "Voltar ao menu" }
                      ],
                      "selectableOptionsCount": 1
                    }
                  },
                  "contextInfo": null,
                  "messageType": "pollCreationMessageV3",
                  "messageTimestamp": 1763851200,
                  "instanceId": "c8b2c4e1-9f3a-4d1c-8e7b-1a2b3c4d5e6f",
                  "source": "unknown"
                }
                """;

        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedApiKey = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/message/sendPoll/cefan-studio", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedApiKey.set(exchange.getRequestHeaders().getFirst("apikey"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] responseBytes = realEvolution237SendPollResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            EvolutionApiClient client = new EvolutionApiClient(
                    "http://127.0.0.1:" + port,
                    "evo-test-key",
                    "cefan-studio",
                    true,
                    objectMapper
            );

            Optional<String> pollIdOpt = client.sendPollMessage(
                    "cefan-studio",
                    "55419XXXXXXXX",
                    "O que você gostaria de fazer agora? (ou digite 1, 2 ou 3)",
                    List.of("Agendar sessão", "Falar com o tatuador", "Voltar ao menu"),
                    1
            );

            assertTrue(pollIdOpt.isPresent(), "Deve extrair key.id da resposta real da Evolution 2.3.7 sem erro de Jackson");
            assertEquals("3EB0F1A2B3C4D5E6F78901", pollIdOpt.get());
            assertEquals("/message/sendPoll/cefan-studio", capturedPath.get());
            assertEquals("evo-test-key", capturedApiKey.get());
            assertTrue(capturedBody.get().contains("\"selectableCount\":1"));
            assertTrue(capturedBody.get().contains("Agendar sessão"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnEmptyAndNotThrowWhenSendPollResponseHasNoKeyIdOrIsMalformed() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/message/sendPoll/cefan-studio", exchange -> {
            byte[] responseBytes = "{\"status\":\"PENDING\",\"message\":{}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            EvolutionApiClient client = new EvolutionApiClient(
                    "http://127.0.0.1:" + port,
                    "evo-test-key",
                    "cefan-studio",
                    true,
                    objectMapper
            );

            Optional<String> pollIdOpt = client.sendPollMessage(
                    "cefan-studio",
                    "55419XXXXXXXX",
                    "Menu",
                    List.of("Opção 1", "Opção 2"),
                    1
            );

            assertTrue(pollIdOpt.isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldDeserializeGetBase64AndConnectionStateWithoutJacksonConflict() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/getBase64FromMediaMessage/cefan-studio", exchange -> {
            byte[] responseBytes = "{\"base64\":\"aGVsbG8tdGF0dG9v\",\"mimetype\":\"image/jpeg\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });
        server.createContext("/instance/connectionState/cefan-studio", exchange -> {
            byte[] responseBytes = "{\"instance\":{\"instanceName\":\"cefan-studio\",\"state\":\"open\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            EvolutionApiClient client = new EvolutionApiClient(
                    "http://127.0.0.1:" + port,
                    "evo-test-key",
                    "cefan-studio",
                    true,
                    objectMapper
            );

            Optional<String> base64Opt = client.getBase64FromMediaMessage(
                    "cefan-studio", "MSG-001", "55419XXXXXXXX@s.whatsapp.net", false);
            assertTrue(base64Opt.isPresent());
            assertEquals("aGVsbG8tdGF0dG9v", base64Opt.get());

            assertTrue(client.checkConnectionState("cefan-studio"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldDeserializeRealSendPollResponseViaMockRestServiceServer() {
        String realEvolution237SendPollResponse = """
                {
                  "key": {
                    "remoteJid": "55419XXXXXXXX@s.whatsapp.net",
                    "fromMe": true,
                    "id": "3EB0MOCKREST9876543210"
                  },
                  "pushName": "",
                  "status": "PENDING",
                  "message": {
                    "messageContextInfo": {
                      "messageSecret": "rK8vN1pX2mQ5zL9wT4yB7cH0jF3dG6sA8eU1iO4pP7k="
                    },
                    "pollCreationMessageV3": {
                      "name": "Como posso te ajudar hoje? (ou digite o número acima)",
                      "options": [
                        { "optionName": "Fazer orçamento" },
                        { "optionName": "Agendar sessão" },
                        { "optionName": "Falar com o tatuador" },
                        { "optionName": "Nossos artistas e portfólio" }
                      ],
                      "selectableOptionsCount": 1
                    }
                  },
                  "contextInfo": null,
                  "messageType": "pollCreationMessageV3",
                  "messageTimestamp": 1763851200,
                  "instanceId": "c8b2c4e1-9f3a-4d1c-8e7b-1a2b3c4d5e6f",
                  "source": "unknown"
                }
                """;

        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:8081")
                .defaultHeader("apikey", "evo-test-key");
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();

        mockServer.expect(requestTo("http://localhost:8081/message/sendPoll/cefan-studio"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", "evo-test-key"))
                .andRespond(withSuccess(realEvolution237SendPollResponse, MediaType.APPLICATION_JSON));

        EvolutionApiClient client = new EvolutionApiClient(
                "http://localhost:8081", "evo-test-key", "cefan-studio", true, objectMapper);
        ReflectionTestUtils.setField(client, "restClient", builder.build());

        Optional<String> pollId = client.sendPollMessage(
                "cefan-studio",
                "55419XXXXXXXX",
                "Como posso te ajudar hoje? (ou digite o número acima)",
                List.of("Fazer orçamento", "Agendar sessão", "Falar com o tatuador", "Nossos artistas e portfólio"),
                1
        );

        mockServer.verify();
        assertTrue(pollId.isPresent());
        assertEquals("3EB0MOCKREST9876543210", pollId.get());
    }
}
