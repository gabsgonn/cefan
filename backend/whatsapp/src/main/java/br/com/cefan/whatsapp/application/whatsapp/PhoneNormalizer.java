package br.com.cefan.whatsapp.application.whatsapp;

/**
 * Normalizador único de telefones para formato internacional sem '+' (somente dígitos).
 *
 * <p>Para números do Brasil (+55), a Meta Cloud API (wa_id) frequentemente entrega celulares
 * com 12 dígitos (sem o 9º dígito após o DDD). Este normalizador padroniza celulares brasileiros
 * de 12 dígitos (55 + DDD + [6-9]XXXXXXX) para 13 dígitos (55 + DDD + 9 + 8 dígitos),
 * preservando telefones fixos (iniciados por 2..5) e números de outros países.</p>
 */
public final class PhoneNormalizer {

    private PhoneNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String withoutJid = raw.trim().split("@")[0];
        String withoutDevice = withoutJid.split(":")[0];
        String digits = withoutDevice.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return null;
        }

        // Brasil (+55): celular sem o 9º dígito -> 55 (2) + DDD (2) + número (8, começando com 6..9) = 12 dígitos
        if (digits.startsWith("55") && digits.length() == 12) {
            char firstSubscriberDigit = digits.charAt(4);
            if (firstSubscriberDigit >= '6' && firstSubscriberDigit <= '9') {
                return digits.substring(0, 4) + "9" + digits.substring(4);
            }
        }
        return digits;
    }

    public static boolean isSamePhone(String phoneA, String phoneB) {
        String normA = normalize(phoneA);
        String normB = normalize(phoneB);
        return normA != null && normA.equals(normB);
    }
}
