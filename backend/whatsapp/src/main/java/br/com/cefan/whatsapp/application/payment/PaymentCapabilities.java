package br.com.cefan.whatsapp.application.payment;

public record PaymentCapabilities(
        boolean providesPixCode,
        boolean providesPaymentUrl,
        boolean canQueryByReference,
        boolean canRefund,
        boolean webhookIsSigned,
        boolean supportsExpiration
) {
    public static PaymentCapabilities mercadoPago() {
        return new PaymentCapabilities(true, false, true, true, false, true);
    }

    public static PaymentCapabilities infinitePay() {
        return new PaymentCapabilities(false, true, false, false, false, true);
    }
}
