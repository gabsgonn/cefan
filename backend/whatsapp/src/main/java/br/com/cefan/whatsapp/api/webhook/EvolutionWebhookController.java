package br.com.cefan.whatsapp.api.webhook;

import br.com.cefan.whatsapp.application.service.MessageHandlingService;
import br.com.cefan.whatsapp.infrastructure.evolution.dto.EvolutionWebhookRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/evolution")
public class EvolutionWebhookController {

    private static final int MAX_RAW_LOG_LENGTH = 2000;
    private static final Pattern JID_PHONE_PATTERN = Pattern.compile("(\\d{4})\\d{4,12}(@(?:s\\.whatsapp\\.net|lid|g\\.us))");
    private static final Pattern JSON_NUMBER_PATTERN = Pattern.compile("(\"(?:number|sender|phone)\"\\s*:\\s*\")(\\d{4})\\d{4,12}(\")");

    private final MessageHandlingService messageHandlingService;
    private final ObjectMapper objectMapper;
    private final boolean debugRawEnabled;

    public EvolutionWebhookController(MessageHandlingService messageHandlingService) {
        this(messageHandlingService, new ObjectMapper(), false);
    }

    @Autowired
    public EvolutionWebhookController(
            MessageHandlingService messageHandlingService,
            ObjectMapper objectMapper,
            @Value("${evolution.webhook.debug-raw.enabled:false}") boolean debugRawEnabled) {
        this.messageHandlingService = messageHandlingService;
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
        this.debugRawEnabled = debugRawEnabled;
    }

    @PostMapping
    public ResponseEntity<Void> receiveWebhook(@RequestBody(required = false) String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return ResponseEntity.ok().build();
        }

        EvolutionWebhookRequest request;
        try {
            request = objectMapper.readValue(rawPayload, EvolutionWebhookRequest.class);
        } catch (Exception ex) {
            log.warn("Payload de webhook não legível da Evolution API: {}", ex.getMessage());
            return ResponseEntity.ok().build();
        }

        if (request != null) {
            log.info("Webhook recebido: evento=[{}], instancia=[{}]", request.getEvent(), request.getInstance());

            boolean isUpsert = "messages.upsert".equalsIgnoreCase(request.getEvent()) || "MESSAGES_UPSERT".equalsIgnoreCase(request.getEvent());
            boolean isUpdate = "messages.update".equalsIgnoreCase(request.getEvent()) || "MESSAGES_UPDATE".equalsIgnoreCase(request.getEvent());

            if (debugRawEnabled) {
                if (isUpdate && (request.isFromMe() || rawPayload.contains("\"fromMe\":true") || rawPayload.contains("\"fromMe\": true"))) {
                    log.debug("EVOLUTION_WEBHOOK_RAW [event=messages.update, fromMe=true]: {}",
                            sanitizeAndTruncateRawPayload(rawPayload));
                } else if (isUpsert && rawPayload.contains("pollUpdateMessage")) {
                    log.debug("EVOLUTION_WEBHOOK_RAW [event=messages.upsert, pollUpdateMessage=true]: {}",
                            sanitizeAndTruncateRawPayload(rawPayload));
                }
            }

            if (isUpsert) {
                messageHandlingService.handleIncomingMessage(request);
            } else if (isUpdate) {
                messageHandlingService.handleMessageUpdate(request);
            }
        }

        return ResponseEntity.ok().build();
    }

    public static String sanitizeAndTruncateRawPayload(String rawPayload) {
        if (rawPayload == null) {
            return "";
        }
        String masked = JID_PHONE_PATTERN.matcher(rawPayload).replaceAll("$1*******$2");
        masked = JSON_NUMBER_PATTERN.matcher(masked).replaceAll("$1$2*******$3");
        if (masked.length() > MAX_RAW_LOG_LENGTH) {
            return masked.substring(0, MAX_RAW_LOG_LENGTH) + "... [truncado]";
        }
        return masked;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Payload de webhook não legível da Evolution API: {}", ex.getMessage());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Webhook endpoint operacional!");
    }
}
