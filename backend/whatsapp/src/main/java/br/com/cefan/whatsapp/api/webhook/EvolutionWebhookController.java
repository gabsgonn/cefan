package br.com.cefan.whatsapp.api.webhook;

import br.com.cefan.whatsapp.application.service.MessageHandlingService;
import br.com.cefan.whatsapp.infrastructure.evolution.dto.EvolutionWebhookRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/evolution")
@RequiredArgsConstructor
public class EvolutionWebhookController {

    private final MessageHandlingService messageHandlingService;

    @PostMapping
    public ResponseEntity<Void> receiveWebhook(@RequestBody EvolutionWebhookRequest request) {
        log.info("Webhook recebido: evento=[{}], instancia=[{}]", request.getEvent(), request.getInstance());

        if ("messages.upsert".equalsIgnoreCase(request.getEvent()) || "MESSAGES_UPSERT".equalsIgnoreCase(request.getEvent())) {
            messageHandlingService.handleIncomingMessage(request);
        }

        return ResponseEntity.ok().build();
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Webhook endpoint operacional!");
    }
}
