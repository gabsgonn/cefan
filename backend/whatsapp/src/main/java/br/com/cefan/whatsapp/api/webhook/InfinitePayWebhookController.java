package br.com.cefan.whatsapp.api.webhook;

import br.com.cefan.whatsapp.application.payment.PaymentInboxService;
import br.com.cefan.whatsapp.infrastructure.infinitepay.InfinitePayPaymentGateway;
import br.com.cefan.whatsapp.infrastructure.infinitepay.InfinitePayWebhookSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/infinitepay")
@RequiredArgsConstructor
public class InfinitePayWebhookController {

    private final InfinitePayWebhookSigner webhookSigner;
    private final PaymentInboxService paymentInboxService;
    private final ObjectMapper objectMapper;

    @PostMapping("/{appointmentId}/{hmac}")
    public ResponseEntity<Void> receiveWebhook(
            @PathVariable UUID appointmentId,
            @PathVariable String hmac,
            @RequestBody(required = false) String rawBody) {

        if (!webhookSigner.verify(appointmentId, hmac)) {
            log.warn("Webhook InfinitePay rejeitado por HMAC inválido para appointmentId=[{}]", appointmentId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (rawBody == null || rawBody.isBlank()) {
            return ResponseEntity.ok().build();
        }

        try {
            JsonNode json = objectMapper.readTree(rawBody);
            String transactionNsu = json.hasNonNull("transaction_nsu") ? json.get("transaction_nsu").asText().trim() : null;
            String invoiceSlug = json.hasNonNull("invoice_slug") ? json.get("invoice_slug").asText().trim() : null;
            String captureMethod = json.hasNonNull("capture_method") ? json.get("capture_method").asText().trim() : null;
            UUID orderNsu = appointmentId;
            if (json.hasNonNull("order_nsu")) {
                try {
                    orderNsu = UUID.fromString(json.get("order_nsu").asText().trim());
                } catch (IllegalArgumentException ignored) {
                }
            }

            if (transactionNsu == null || transactionNsu.isBlank()) {
                log.info("Webhook InfinitePay sem transaction_nsu para appointmentId=[{}]. Ignorando.", appointmentId);
                return ResponseEntity.ok().build();
            }

            var event = paymentInboxService.enqueueIdempotent(
                    InfinitePayPaymentGateway.PROVIDER,
                    appointmentId,
                    orderNsu,
                    transactionNsu,
                    invoiceSlug,
                    captureMethod,
                    "WEBHOOK"
            );
            paymentInboxService.processNowWithRetry(event);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Falha ao gravar webhook InfinitePay na inbox para appointmentId=[{}]: {}", appointmentId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
