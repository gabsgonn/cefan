package br.com.cefan.whatsapp.infrastructure.evolution;

import br.com.cefan.whatsapp.infrastructure.evolution.dto.SendTextMessageRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class EvolutionApiClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String defaultInstance;

    public EvolutionApiClient(
            @Value("${evolution.api.url:http://localhost:8080}") String apiUrl,
            @Value("${evolution.api.key}") String apiKey,
            @Value("${evolution.api.instance-name:cefan-studio}") String defaultInstance) {
        this.apiKey = apiKey;
        this.defaultInstance = defaultInstance;
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("apikey", apiKey)
                .build();
    }

    public void sendTextMessage(String instance, String recipientNumber, String text) {
        String targetInstance = (instance != null && !instance.isBlank()) ? instance : defaultInstance;
        log.info("Enviando mensagem via Evolution API para [{}] na instância [{}]: {}", recipientNumber, targetInstance, text);
        try {
            SendTextMessageRequest request = SendTextMessageRequest.builder()
                    .number(recipientNumber)
                    .text(text)
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
}
