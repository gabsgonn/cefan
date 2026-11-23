package br.com.cefan.whatsapp.infrastructure.mercadopago;

import br.com.cefan.whatsapp.application.statemachine.handler.AgendamentoStateHandler;
import br.com.cefan.whatsapp.infrastructure.mercadopago.dto.MercadoPagoCreatePaymentRequest;
import br.com.cefan.whatsapp.infrastructure.mercadopago.dto.MercadoPagoPaymentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Component
public class MercadoPagoPaymentClient {

    private final RestClient restClient;
    private final String accessToken;
    private final String notificationUrl;
    private final boolean sandboxEnabled;

    public MercadoPagoPaymentClient(
            String accessToken,
            String apiUrl,
            String notificationUrl) {
        this(accessToken, apiUrl, notificationUrl, false);
    }

    @Autowired
    public MercadoPagoPaymentClient(
            @Value("${mercadopago.access.token:}") String accessToken,
            @Value("${mercadopago.api.url:https://api.mercadopago.com/v1}") String apiUrl,
            @Value("${mercadopago.notification.url:}") String notificationUrl,
            @Value("${mercadopago.sandbox.enabled:false}") boolean sandboxEnabled) {
        this.accessToken = (accessToken != null) ? accessToken.trim() : "";
        this.notificationUrl = (notificationUrl != null) ? notificationUrl.trim() : "";
        this.sandboxEnabled = sandboxEnabled;
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .build();
    }

    public boolean isSandboxEnabled() {
        return sandboxEnabled;
    }

    public MercadoPagoPaymentResponse createPixPayment(
            BigDecimal amount,
            String description,
            String payerEmail,
            String payerName,
            OffsetDateTime expirationDate) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new MercadoPagoPaymentException("Valor do pagamento PIX inválido: " + amount);
        }

        if (sandboxEnabled) {
            log.warn("Modo sandbox do Mercado Pago ativado explicitamente (mercadopago.sandbox.enabled=true). Gerando pagamento PIX simulado.");
            return createSimulatedPixPayment(amount);
        }

        if (accessToken.isBlank()) {
            log.error("MERCADOPAGO_ACCESS_TOKEN não configurado e modo sandbox desativado. Não é possível gerar pagamento PIX.");
            throw new MercadoPagoPaymentException("Gateway de pagamento PIX não configurado (MERCADOPAGO_ACCESS_TOKEN ausente).");
        }

        try {
            DateTimeFormatter mpFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
            OffsetDateTime effectiveExpiration = (expirationDate != null)
                    ? expirationDate
                    : OffsetDateTime.now(AgendamentoStateHandler.STUDIO_ZONE).plusHours(2);
            String expirationIso = effectiveExpiration
                    .atZoneSameInstant(AgendamentoStateHandler.STUDIO_ZONE)
                    .format(mpFormatter);

            String email = (payerEmail != null && !payerEmail.isBlank())
                    ? payerEmail
                    : "cliente." + UUID.randomUUID().toString().substring(0, 8) + "@cefan.app";

            MercadoPagoCreatePaymentRequest request = MercadoPagoCreatePaymentRequest.builder()
                    .transactionAmount(amount)
                    .description(description != null ? description : "Sinal de Agendamento - Vício Tattoo")
                    .paymentMethodId("pix")
                    .payer(MercadoPagoCreatePaymentRequest.Payer.builder()
                            .email(email)
                            .firstName(payerName != null ? payerName : "Cliente")
                            .build())
                    .notificationUrl(!notificationUrl.isBlank() ? notificationUrl : null)
                    .dateOfExpiration(expirationIso)
                    .build();

            log.info("Criando cobrança PIX no Mercado Pago no valor de R$ {}", amount);

            MercadoPagoPaymentResponse response = restClient.post()
                    .uri("/payments")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MercadoPagoPaymentResponse.class);

            if (response == null || response.getId() == null
                    || response.extractPixCopyPaste() == null || response.extractPixCopyPaste().isBlank()) {
                log.error("Resposta incompleta do Mercado Pago ao criar pagamento PIX (response={}).", response);
                throw new MercadoPagoPaymentException("Resposta incompleta do Mercado Pago ao gerar código PIX.");
            }

            return response;
        } catch (MercadoPagoPaymentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao gerar pagamento PIX no Mercado Pago: {}", e.getMessage(), e);
            throw new MercadoPagoPaymentException("Falha na comunicação com o Mercado Pago ao gerar PIX.", e);
        }
    }

    public MercadoPagoPaymentResponse getPayment(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            return null;
        }

        if (sandboxEnabled) {
            log.warn("Consultando pagamento simulado [{}] em modo sandbox (aprovado para testes).", paymentId);
            long parsedId = parseNumericIdOrDefault(paymentId, 123456789L);
            return MercadoPagoPaymentResponse.builder()
                    .id(parsedId)
                    .status("approved")
                    .statusDetail("accredited")
                    .build();
        }

        if (accessToken.isBlank()) {
            log.error("MERCADOPAGO_ACCESS_TOKEN não configurado e modo sandbox desativado. Não é possível consultar pagamento [{}].", paymentId);
            return null;
        }

        try {
            log.info("Consultando status do pagamento [{}] na API do Mercado Pago", paymentId);
            return restClient.get()
                    .uri("/payments/{id}", paymentId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(MercadoPagoPaymentResponse.class);
        } catch (Exception e) {
            log.error("Erro ao consultar pagamento [{}] no Mercado Pago: {}", paymentId, e.getMessage(), e);
            return null;
        }
    }

    private long parseNumericIdOrDefault(String paymentId, long fallback) {
        try {
            return Long.parseLong(paymentId.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private MercadoPagoPaymentResponse createSimulatedPixPayment(BigDecimal amount) {
        long simulatedId = Math.abs(UUID.randomUUID().getMostSignificantBits() % 1000000000L);
        return MercadoPagoPaymentResponse.builder()
                .id(simulatedId)
                .status("pending")
                .statusDetail("waiting_transfer")
                .transactionAmount(amount)
                .pointOfInteraction(MercadoPagoPaymentResponse.PointOfInteraction.builder()
                        .transactionData(MercadoPagoPaymentResponse.TransactionData.builder()
                                .qrCode("00020101021226840014br.gov.bcb.pix2562pix.mercadopago.com/qr/sim_cefan_" + simulatedId)
                                .qrCodeBase64("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
                                .ticketUrl("https://www.mercadopago.com.br/payments/sim_" + simulatedId + "/ticket")
                                .build())
                        .build())
                .build();
    }
}
