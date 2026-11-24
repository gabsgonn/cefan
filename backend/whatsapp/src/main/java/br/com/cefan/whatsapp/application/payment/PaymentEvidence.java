package br.com.cefan.whatsapp.application.payment;

import java.util.UUID;

public record PaymentEvidence(
        String provider,
        String paymentId,
        UUID orderNsu,
        String transactionNsu,
        String invoiceSlug
) {
    public static PaymentEvidence forMercadoPago(String paymentId) {
        return new PaymentEvidence("mercadopago", paymentId, null, null, null);
    }
}
