package br.com.cefan.whatsapp.application.payment;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateChargeRequest(
        UUID appointmentId,
        long amountCents,
        String description,
        String customerName,
        String customerPhone,
        String customerEmail,
        OffsetDateTime expiresAt,
        String webhookUrl,
        String redirectUrl
) {}
