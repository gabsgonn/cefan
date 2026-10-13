package br.com.cefan.whatsapp.infrastructure.mercadopago.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MercadoPagoWebhookNotification {

    private String id;

    @JsonProperty("live_mode")
    private Boolean liveMode;

    private String type;
    private String action;

    @JsonProperty("date_created")
    private String dateCreated;

    private NotificationData data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NotificationData {
        private String id;
    }

    public String extractPaymentId() {
        if (data != null && data.id != null && !data.id.isBlank()) {
            return data.id;
        }
        return id;
    }
}
