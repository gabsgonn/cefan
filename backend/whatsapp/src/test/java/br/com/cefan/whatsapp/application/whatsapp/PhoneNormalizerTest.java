package br.com.cefan.whatsapp.application.whatsapp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNormalizerTest {

    @Test
    void shouldNormalizeBrazilianMobileWithOrWithoutNinthDigit() {
        // Meta Cloud API wa_id frequentemente chega sem o 9º dígito (12 dígitos)
        assertEquals("5541999074863", PhoneNormalizer.normalize("554199074863"));
        assertEquals("5541999074863", PhoneNormalizer.normalize("5541999074863"));
        assertEquals("5541999074863", PhoneNormalizer.normalize("+55 (41) 99907-4863"));
        assertEquals("5541999074863", PhoneNormalizer.normalize("554199074863@s.whatsapp.net"));
        assertEquals("5541999074863", PhoneNormalizer.normalize("5541999074863:2@s.whatsapp.net"));
        assertTrue(PhoneNormalizer.isSamePhone("554199074863", "5541999074863"));
    }

    @Test
    void shouldKeepBrazilianLandlineAndInternationalNumbersUntouched() {
        // Fixo brasileiro (começa com 3 após DDD) NÃO deve ganhar o 9º dígito
        assertEquals("554133334444", PhoneNormalizer.normalize("554133334444"));
        // Número internacional (EUA)
        assertEquals("14155552671", PhoneNormalizer.normalize("+1 (415) 555-2671"));
        assertNull(PhoneNormalizer.normalize(null));
        assertNull(PhoneNormalizer.normalize("   "));
    }
}
