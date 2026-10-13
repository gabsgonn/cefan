package br.com.cefan.whatsapp.infrastructure.mercadopago.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MercadoPagoCreatePaymentRequest {

    @JsonProperty("transaction_amount")
    private BigDecimal transactionAmount;

    private String description;

    @JsonProperty("payment_method_id")
    private String paymentMethodId;

    private Payer payer;

    @JsonProperty("notification_url")
    private String notificationUrl;

    @JsonProperty("date_of_expiration")
    private String dateOfExpiration;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payer {
        private String email;
        @JsonProperty("first_name")
        private String firstName;
    }
}
