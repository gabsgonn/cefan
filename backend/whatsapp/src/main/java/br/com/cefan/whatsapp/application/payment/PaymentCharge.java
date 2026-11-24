package br.com.cefan.whatsapp.application.payment;

import java.time.OffsetDateTime;

public record PaymentCharge(
        String provider,
        String reference,
        String paymentUrl,
        String pixCode,
        String qrBase64,
        OffsetDateTime expiresAt
) {}
