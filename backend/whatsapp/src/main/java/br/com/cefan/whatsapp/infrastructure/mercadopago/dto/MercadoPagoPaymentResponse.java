package br.com.cefan.whatsapp.infrastructure.mercadopago.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class MercadoPagoPaymentResponse {

    private Long id;
    private String status;

    @JsonProperty("status_detail")
    private String statusDetail;

    @JsonProperty("transaction_amount")
    private BigDecimal transactionAmount;

    @JsonProperty("point_of_interaction")
    private PointOfInteraction pointOfInteraction;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PointOfInteraction {
        @JsonProperty("transaction_data")
        private TransactionData transactionData;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransactionData {
        @JsonProperty("qr_code")
        private String qrCode;

        @JsonProperty("qr_code_base64")
        private String qrCodeBase64;

        @JsonProperty("ticket_url")
        private String ticketUrl;
    }

    public String extractPixCopyPaste() {
        if (pointOfInteraction != null && pointOfInteraction.transactionData != null) {
            return pointOfInteraction.transactionData.qrCode;
        }
        return null;
    }

    public String extractPixQrCodeBase64() {
        if (pointOfInteraction != null && pointOfInteraction.transactionData != null) {
            return pointOfInteraction.transactionData.qrCodeBase64;
        }
        return null;
    }

    public String extractTicketUrl() {
        if (pointOfInteraction != null && pointOfInteraction.transactionData != null) {
            return pointOfInteraction.transactionData.ticketUrl;
        }
        return null;
    }

    public boolean isApproved() {
        return "approved".equalsIgnoreCase(status);
    }
}
