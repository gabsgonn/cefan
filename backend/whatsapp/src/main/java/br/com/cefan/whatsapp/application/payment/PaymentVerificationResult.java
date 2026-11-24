package br.com.cefan.whatsapp.application.payment;

public record PaymentVerificationResult(
        PaymentVerificationStatus status,
        String rawStatus,
        Long amountCents,
        Long paidAmountCents,
        String captureMethod,
        String transactionNsu,
        String invoiceSlug,
        String detail
) {
    public static PaymentVerificationResult paid(
            String rawStatus,
            Long amountCents,
            Long paidAmountCents,
            String captureMethod,
            String transactionNsu,
            String invoiceSlug) {
        return new PaymentVerificationResult(
                PaymentVerificationStatus.PAID, rawStatus, amountCents, paidAmountCents,
                captureMethod, transactionNsu, invoiceSlug, null);
    }

    public static PaymentVerificationResult notPaid(String rawStatus, String detail) {
        return new PaymentVerificationResult(
                PaymentVerificationStatus.NOT_PAID, rawStatus, null, null, null, null, null, detail);
    }

    public static PaymentVerificationResult unverifiable(String detail) {
        return new PaymentVerificationResult(
                PaymentVerificationStatus.UNVERIFIABLE, null, null, null, null, null, null, detail);
    }
}
