package br.com.cefan.whatsapp.api.webhook;

import br.com.cefan.whatsapp.application.service.PaymentProcessingService;
import br.com.cefan.whatsapp.infrastructure.mercadopago.dto.MercadoPagoWebhookNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/mercadopago")
@RequiredArgsConstructor
public class MercadoPagoWebhookController {

    private final PaymentProcessingService paymentProcessingService;

    @PostMapping
    public ResponseEntity<Void> receiveWebhook(
            @RequestBody(required = false) MercadoPagoWebhookNotification notification,
            @RequestParam(value = "id", required = false) String queryId,
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam(value = "data.id", required = false) String dataId) {

        String paymentId = null;

        if (notification != null && notification.extractPaymentId() != null) {
            paymentId = notification.extractPaymentId();
        } else if (dataId != null && !dataId.isBlank()) {
            paymentId = dataId;
        } else if (queryId != null && !queryId.isBlank()) {
            paymentId = queryId;
        }

        log.info("Webhook recebido do Mercado Pago. paymentId=[{}], topic=[{}]", paymentId, topic);

        if (paymentId != null) {
            paymentProcessingService.handlePaymentNotification(paymentId);
        }

        return ResponseEntity.ok().build();
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Mercado Pago webhook operacional!");
    }
}
