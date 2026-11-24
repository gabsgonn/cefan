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
            log.info("Webhook InfinitePay recebido para appointmentId=[{}]: {}", appointmentId, rawBody);
            JsonNode json = objectMapper.readTree(rawBody);
            String transactionNsu = firstNonBlank(json, "transaction_nsu", "transactionNsu", "transaction_id");
            String invoiceSlug = firstNonBlank(json, "invoice_slug", "slug", "invoiceSlug");
            String captureMethod = firstNonBlank(json, "capture_method", "captureMethod", "payment_method");
            UUID orderNsu = appointmentId;
            String orderNsuRaw = firstNonBlank(json, "order_nsu", "orderNsu");
            if (orderNsuRaw != null) {
                try {
                    orderNsu = UUID.fromString(orderNsuRaw);
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

    private String firstNonBlank(JsonNode json, String... fields) {
        if (json == null) return null;
        for (String field : fields) {
            if (json.hasNonNull(field)) {
                String val = json.get(field).asText().trim();
                if (!val.isBlank()) {
                    return val;
                }
            }
        }
        return null;
    }
}
