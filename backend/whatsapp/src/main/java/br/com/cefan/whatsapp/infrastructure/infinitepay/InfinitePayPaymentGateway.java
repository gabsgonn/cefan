package br.com.cefan.whatsapp.infrastructure.infinitepay;

import br.com.cefan.whatsapp.application.payment.*;
import br.com.cefan.whatsapp.application.statemachine.handler.AgendamentoStateHandler;
import br.com.cefan.whatsapp.domain.model.TenantConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter oficial do Checkout Integrado da InfinitePay.
 * - Criação de link: POST /links com X-Api-Key, options.valid_until (ISO sem nanossegundos) e options.single_payment=true.
 * - Verificação: POST /payment_check com handle + order_nsu + transaction_nsu + slug.
 * Nunca loga a X-Api-Key.
 */
@Slf4j
@Component
public class InfinitePayPaymentGateway implements PaymentGateway {

    public static final String PROVIDER = "infinitepay";
    private static final DateTimeFormatter VALID_UNTIL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    @Autowired
    public InfinitePayPaymentGateway(
            ObjectMapper objectMapper,
            @Value("${infinitepay.api.url:https://api.checkout.infinitepay.io}") String apiUrl,
            @Value("${infinitepay.api.key:}") String apiKey) {
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
        this.apiKey = (apiKey != null) ? apiKey.trim() : "";
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .build();
    }

    @Override
    public String getProviderName() {
        return PROVIDER;
    }

    @Override
    public PaymentCapabilities capabilities() {
        return PaymentCapabilities.infinitePay();
    }

    @Override
    public boolean isConfiguredFor(TenantConfig tenantConfig) {
        return !apiKey.isBlank()
                && tenantConfig != null
                && tenantConfig.getInfinitepayHandle() != null
                && !tenantConfig.getInfinitepayHandle().isBlank();
    }

    public static String formatValidUntil(OffsetDateTime expiresAt) {
        OffsetDateTime effective = (expiresAt != null)
                ? expiresAt
                : OffsetDateTime.now(AgendamentoStateHandler.STUDIO_ZONE).plusHours(2);
        return effective.atZoneSameInstant(AgendamentoStateHandler.STUDIO_ZONE).format(VALID_UNTIL_FORMATTER);
    }

    @Override
    public PaymentCharge createCharge(TenantConfig tenantConfig, CreateChargeRequest request) {
        if (!isConfiguredFor(tenantConfig)) {
            throw new IllegalStateException("InfinitePay não configurado para o tenant (handle ou INFINITEPAY_API_KEY ausente).");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("handle", tenantConfig.getInfinitepayHandle().trim());
        body.put("order_nsu", request.appointmentId().toString());
        body.put("items", List.of(Map.of(
                "quantity", 1,
                "price", request.amountCents(),
                "description", "Sinal de reserva"
        )));

        if (request.customerName() != null || request.customerPhone() != null) {
            Map<String, Object> customer = new LinkedHashMap<>();
            if (request.customerName() != null && !request.customerName().isBlank()) {
                customer.put("name", request.customerName().trim());
            }
            if (request.customerPhone() != null && !request.customerPhone().isBlank()) {
                customer.put("phone_number", request.customerPhone().trim());
            }
            if (!customer.isEmpty()) {
                body.put("customer", customer);
            }
        }

        if (request.redirectUrl() != null && !request.redirectUrl().isBlank()) {
            body.put("redirect_url", request.redirectUrl().trim());
        }
        if (request.webhookUrl() != null && !request.webhookUrl().isBlank()) {
            body.put("webhook", Map.of(
                    "url", request.webhookUrl().trim(),
                    "events", List.of("paid")
            ));
        }

        body.put("options", Map.of(
                "valid_until", formatValidUntil(request.expiresAt()),
                "single_payment", true
        ));

        String rawResponse = restClient.post()
                .uri("/links")
                .header("X-Api-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        log.info("InfinitePay POST /links resposta recebida para order_nsu=[{}]: {}", request.appointmentId(), rawResponse);

        try {
            JsonNode json = objectMapper.readTree(rawResponse);
            String url = json.hasNonNull("url") ? json.get("url").asText() : null;
            if (url == null || url.isBlank()) {
                throw new IllegalStateException("Resposta da InfinitePay sem campo 'url'");
            }
            return new PaymentCharge(
                    PROVIDER,
                    request.appointmentId().toString(),
                    url.trim(),
                    null,
                    null,
                    request.expiresAt()
            );
        } catch (Exception e) {
            throw new IllegalStateException("Erro ao interpretar resposta de criação de link InfinitePay: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentVerificationResult verify(TenantConfig tenantConfig, PaymentEvidence evidence) {
        if (!isConfiguredFor(tenantConfig)) {
            return PaymentVerificationResult.unverifiable("InfinitePay não configurado para o tenant");
        }
        if (evidence == null || evidence.orderNsu() == null
                || evidence.transactionNsu() == null || evidence.transactionNsu().isBlank()
                || evidence.invoiceSlug() == null || evidence.invoiceSlug().isBlank()) {
            return PaymentVerificationResult.notPaid(null, "Evidência incompleta para payment_check (order_nsu, transaction_nsu e slug são obrigatórios)");
        }

        Map<String, Object> body = Map.of(
                "handle", tenantConfig.getInfinitepayHandle().trim(),
                "order_nsu", evidence.orderNsu().toString(),
                "transaction_nsu", evidence.transactionNsu().trim(),
                "slug", evidence.invoiceSlug().trim()
        );

        try {
            String rawResponse = restClient.post()
                    .uri("/payment_check")
                    .header("X-Api-Key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            log.info("InfinitePay POST /payment_check resposta para order_nsu=[{}]: {}", evidence.orderNsu(), rawResponse);

            JsonNode json = objectMapper.readTree(rawResponse);
            boolean paid = json != null && json.path("paid").asBoolean(false);
            if (!paid) {
                return PaymentVerificationResult.notPaid("unpaid", "payment_check retornou paid=false");
            }
            Long amountCents = json.hasNonNull("amount") ? json.get("amount").asLong() : null;
            Long paidAmountCents = json.hasNonNull("paid_amount") ? json.get("paid_amount").asLong() : amountCents;
            String captureMethod = json.hasNonNull("capture_method") ? json.get("capture_method").asText() : null;
            return PaymentVerificationResult.paid(
                    "paid",
                    amountCents,
                    paidAmountCents,
                    captureMethod,
                    evidence.transactionNsu(),
                    evidence.invoiceSlug()
            );
        } catch (Exception e) {
            log.warn("Falha transitória ao consultar payment_check InfinitePay para order_nsu=[{}]: {}",
                    evidence.orderNsu(), e.getMessage());
            return PaymentVerificationResult.unverifiable(e.getMessage());
        }
    }
}
