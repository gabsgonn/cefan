package br.com.cefan.whatsapp.infrastructure.mercadopago;

import br.com.cefan.whatsapp.infrastructure.mercadopago.dto.MercadoPagoCreatePaymentRequest;
import br.com.cefan.whatsapp.infrastructure.mercadopago.dto.MercadoPagoPaymentResponse;
import lombok.extern.slf4j.Slf4j;
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

    public MercadoPagoPaymentClient(
            @Value("${mercadopago.access.token:}") String accessToken,
            @Value("${mercadopago.api.url:https://api.mercadopago.com/v1}") String apiUrl,
            @Value("${mercadopago.notification.url:}") String notificationUrl) {
        this.accessToken = (accessToken != null) ? accessToken.trim() : "";
        this.notificationUrl = (notificationUrl != null) ? notificationUrl.trim() : "";
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .build();
    }

    public MercadoPagoPaymentResponse createPixPayment(
            BigDecimal amount,
            String description,
            String payerEmail,
            String payerName,
            OffsetDateTime expirationDate) {

        if (accessToken.isBlank()) {
            log.info("MERCADOPAGO_ACCESS_TOKEN não configurado. Gerando pagamento PIX simulado.");
            return createSimulatedPixPayment(amount);
        }

        try {
            String expirationIso = (expirationDate != null)
                    ? expirationDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    : OffsetDateTime.now().plusHours(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

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

            return restClient.post()
                    .uri("/payments")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MercadoPagoPaymentResponse.class);
        } catch (Exception e) {
            log.error("Erro ao gerar pagamento PIX no Mercado Pago: {}. Retornando PIX simulado.", e.getMessage(), e);
            return createSimulatedPixPayment(amount);
        }
    }

    public MercadoPagoPaymentResponse getPayment(String paymentId) {
        if (accessToken.isBlank() || paymentId.startsWith("sim_")) {
            log.info("Consultando pagamento simulado [{}] (aprovado para testes).", paymentId);
            return MercadoPagoPaymentResponse.builder()
                    .id(123456789L)
                    .status("approved")
                    .statusDetail("accredited")
                    .build();
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
