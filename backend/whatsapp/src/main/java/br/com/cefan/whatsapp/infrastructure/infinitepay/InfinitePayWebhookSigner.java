package br.com.cefan.whatsapp.infrastructure.infinitepay;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Assina e valida URLs de webhook por reserva: /api/v1/webhooks/infinitepay/{appointmentId}/{hmac}.
 */
@Component
public class InfinitePayWebhookSigner {

    private final byte[] secretBytes;

    public InfinitePayWebhookSigner(
            @Value("${infinitepay.webhook.secret:${security.jwt.secret}}") String secret) {
        this.secretBytes = (secret != null ? secret : "").getBytes(StandardCharsets.UTF_8);
    }

    public String sign(UUID appointmentId) {
        if (appointmentId == null) {
            throw new IllegalArgumentException("appointmentId é obrigatório para assinar webhook");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            byte[] raw = mac.doFinal(appointmentId.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException("Erro ao calcular HMAC do webhook InfinitePay", e);
        }
    }

    public boolean verify(UUID appointmentId, String providedHmac) {
        if (appointmentId == null || providedHmac == null || providedHmac.isBlank()) {
            return false;
        }
        String expected = sign(appointmentId);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedHmac.trim().getBytes(StandardCharsets.UTF_8)
        );
    }
}
