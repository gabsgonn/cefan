package br.com.cefan.whatsapp.infrastructure.evolution;

import br.com.cefan.whatsapp.infrastructure.evolution.dto.SendPollMessageRequest;
import br.com.cefan.whatsapp.infrastructure.evolution.dto.SendTextMessageRequest;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class EvolutionApiClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String defaultInstance;
    private final boolean pollsEnabled;

    public EvolutionApiClient(
            String apiUrl,
            String apiKey,
            String defaultInstance) {
        this(apiUrl, apiKey, defaultInstance, true);
    }

    @Autowired
    public EvolutionApiClient(
            @Value("${evolution.api.url:http://localhost:8081}") String apiUrl,
            @Value("${evolution.api.key}") String apiKey,
            @Value("${evolution.api.instance-name:cefan-studio}") String defaultInstance,
            @Value("${evolution.api.polls.enabled:true}") boolean pollsEnabled) {
        this.apiKey = apiKey;
        this.defaultInstance = defaultInstance;
        this.pollsEnabled = pollsEnabled;
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("apikey", apiKey)
                .build();
    }

    public static String normalizeNewlines(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.replace("\\r\\n", "\n").replace("\\n", "\n");
    }

    public void sendTextMessage(String instance, String recipientNumber, String text) {
        String targetInstance = (instance != null && !instance.isBlank()) ? instance : defaultInstance;
        String normalizedText = normalizeNewlines(text);
        log.info("Enviando mensagem via Evolution API para [{}] na instância [{}]: {}", recipientNumber, targetInstance, normalizedText);
        try {
            SendTextMessageRequest request = SendTextMessageRequest.builder()
                    .number(recipientNumber)
                    .text(normalizedText)
                    .build();

            restClient.post()
                    .uri("/message/sendText/{instance}", targetInstance)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Mensagem enviada com sucesso para [{}]", recipientNumber);
        } catch (Exception e) {
            log.error("Erro ao enviar mensagem via Evolution API para [{}]: {}", recipientNumber, e.getMessage(), e);
        }
    }

    public void sendTextMessage(String recipientNumber, String text) {
        sendTextMessage(defaultInstance, recipientNumber, text);
    }

    /**
     * Envia estado de presença ("composing" = digitando, "recording", "paused")
     * para o cliente via Evolution API (/chat/sendPresence/{instance}).
     */
    public void sendPresence(String instance, String recipientNumber, String presence, int delayMs) {
        String targetInstance = (instance != null && !instance.isBlank()) ? instance : defaultInstance;
        String safePresence = (presence != null && !presence.isBlank()) ? presence.trim() : "composing";
        int safeDelay = Math.max(0, delayMs);
        log.info("Enviando presença [{}] ({} ms) via Evolution API para [{}] na instância [{}]",
                safePresence, safeDelay, recipientNumber, targetInstance);
        try {
            Map<String, Object> body = Map.of(
                    "number", recipientNumber,
                    "presence", safePresence,
                    "delay", safeDelay
            );
            restClient.post()
                    .uri("/chat/sendPresence/{instance}", targetInstance)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Falha ao enviar presença [{}] via Evolution API para [{}]: {}",
                    safePresence, recipientNumber, e.getMessage());
        }
    }

    public void sendTypingPresence(String instance, String recipientNumber, int delayMs) {
        sendPresence(instance, recipientNumber, "composing", delayMs);
    }

    /**
     * Envia uma enquete nativa do WhatsApp via Evolution API (/message/sendPoll/{instance})
     * e retorna o key.id da enquete criada para rastreamento de votos.
     */
    public Optional<String> sendPollMessage(
            String instance,
            String recipientNumber,
            String name,
            List<String> values,
            int selectableCount) {
        if (!pollsEnabled || values == null || values.isEmpty()) {
            return Optional.empty();
        }
        String targetInstance = (instance != null && !instance.isBlank()) ? instance : defaultInstance;
        log.info("Enviando enquete via Evolution API para [{}] na instância [{}]: {} ({} opções)",
                recipientNumber, targetInstance, name, values.size());
        try {
            SendPollMessageRequest request = SendPollMessageRequest.builder()
                    .number(recipientNumber)
                    .name(normalizeNewlines(name))
                    .selectableCount(Math.max(1, selectableCount))
                    .values(values)
                    .build();

            JsonNode response = restClient.post()
                    .uri("/message/sendPoll/{instance}", targetInstance)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);

            if (response != null) {
                if (response.has("key") && response.get("key").hasNonNull("id")) {
                    String id = response.get("key").get("id").asText();
                    if (!id.isBlank()) {
                        return Optional.of(id);
                    }
                }
                if (response.hasNonNull("id")) {
                    String id = response.get("id").asText();
                    if (!id.isBlank()) {
                        return Optional.of(id);
                    }
                }
            }
            log.warn("Evolution API enviou enquete para [{}], mas resposta não retornou key.id: {}", recipientNumber, response);
        } catch (Exception e) {
            log.warn("Falha ao enviar enquete via Evolution API para [{}] (fallback de texto permanece ativo): {}",
                    recipientNumber, e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<String> getBase64FromMediaMessage(String instance, String messageId) {
        return getBase64FromMediaMessage(instance, messageId, null, null);
    }

    public Optional<String> getBase64FromMediaMessage(String instance, String messageId, String remoteJid, Boolean fromMe) {
        String targetInstance = (instance != null && !instance.isBlank()) ? instance : defaultInstance;
        if (messageId == null || messageId.isBlank()) {
            return Optional.empty();
        }
        try {
            java.util.Map<String, Object> keyMap = new java.util.HashMap<>();
            keyMap.put("id", messageId);
            if (remoteJid != null && !remoteJid.isBlank()) {
                keyMap.put("remoteJid", remoteJid);
            }
            if (fromMe != null) {
                keyMap.put("fromMe", fromMe);
            }

            Map<String, Object> body = Map.of(
                    "message", Map.of("key", keyMap),
                    "convertToMp4", false
            );

            log.info("Buscando base64 da mídia da mensagem [{}] na instância [{}]", messageId, targetInstance);

            JsonNode response = restClient.post()
                    .uri("/chat/getBase64FromMediaMessage/{instance}", targetInstance)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (response != null) {
                if (response.has("base64")) {
                    JsonNode b64Node = response.get("base64");
                    if (b64Node.isTextual()) {
                        String base64 = b64Node.asText();
                        if (base64 != null && !base64.isBlank()) {
                            return Optional.of(base64);
                        }
                    } else if (b64Node.isObject() && b64Node.has("base64")) {
                        String base64 = b64Node.get("base64").asText();
                        if (base64 != null && !base64.isBlank()) {
                            return Optional.of(base64);
                        }
                    }
                }
                if (response.has("data") && response.get("data").isTextual()) {
                    String base64 = response.get("data").asText();
                    if (base64 != null && !base64.isBlank()) {
                        return Optional.of(base64);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Mídia da mensagem [{}] não pôde ser baixada via Evolution API: {}", messageId, e.getMessage());
        }
        return Optional.empty();
    }

    public boolean checkConnectionState(String instance) {
        String targetInstance = (instance != null && !instance.isBlank()) ? instance : defaultInstance;
        try {
            JsonNode response = restClient.get()
                    .uri("/instance/connectionState/{instance}", targetInstance)
                    .retrieve()
                    .body(JsonNode.class);
            if (response != null) {
                JsonNode stateNode = response.has("instance") ? response.get("instance").get("state") : response.get("state");
                if (stateNode != null && stateNode.isTextual()) {
                    String state = stateNode.asText().toLowerCase();
                    return "open".equals(state) || "connected".equals(state);
                }
            }
            return true;
        } catch (Exception e) {
            log.debug("Evolution API connectionState indisponível no momento para [{}]: {}", targetInstance, e.getMessage());
            return false;
        }
    }

    public boolean checkConnectionState() {
        return checkConnectionState(defaultInstance);
    }

    public boolean restartInstance(String instance) {
        String targetInstance = (instance != null && !instance.isBlank()) ? instance : defaultInstance;
        log.info("Solicitando reinicialização da instância [{}] na Evolution API", targetInstance);
        try {
            restClient.put()
                    .uri("/instance/restart/{instance}", targetInstance)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Instância [{}] reiniciada com sucesso na Evolution API.", targetInstance);
            return true;
        } catch (Exception e) {
            log.warn("Não foi possível reiniciar a instância [{}] via PUT /instance/restart ({}). Tentando reconexão GET /instance/connect...",
                    targetInstance, e.getMessage());
            try {
                restClient.get()
                        .uri("/instance/connect/{instance}", targetInstance)
                        .retrieve()
                        .toBodilessEntity();
                return true;
            } catch (Exception ex) {
                log.warn("Reconexão da instância [{}] falhou (serviço externo offline ou em modo dev): {}", targetInstance, ex.getMessage());
                return false;
            }
        }
    }

    public boolean restartInstance() {
        return restartInstance(defaultInstance);
    }

    public String getDefaultInstance() {
        return defaultInstance;
    }
}
