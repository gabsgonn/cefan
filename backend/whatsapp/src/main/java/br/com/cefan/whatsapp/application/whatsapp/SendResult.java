package br.com.cefan.whatsapp.application.whatsapp;

public record SendResult(
        SendStatus status,
        String providerMessageId,
        String errorDetail
) {
    public static SendResult sent(String providerMessageId) {
        return new SendResult(SendStatus.SENT, providerMessageId, null);
    }

    public static SendResult templateRequired(String reason) {
        return new SendResult(SendStatus.TEMPLATE_REQUIRED, null, reason);
    }

    public static SendResult failed(String reason) {
        return new SendResult(SendStatus.FAILED, null, reason);
    }

    public boolean isSent() {
        return status == SendStatus.SENT;
    }
}
