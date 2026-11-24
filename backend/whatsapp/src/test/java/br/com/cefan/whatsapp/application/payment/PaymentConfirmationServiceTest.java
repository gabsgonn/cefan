package br.com.cefan.whatsapp.application.payment;

import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentConfirmationServiceTest {

    private final PaymentConfirmationService service = new PaymentConfirmationService();
    private final OffsetDateTime now = OffsetDateTime.of(2026, 11, 24, 14, 0, 0, 0, ZoneOffset.ofHours(-3));

    private Appointment pendingAppointment() {
        return Appointment.builder()
                .id(UUID.randomUUID())
                .status(AppointmentStatus.AGUARDANDO_PAGAMENTO)
                .depositAmount(new BigDecimal("63.75"))
                .expiresAt(now.plusHours(2))
                .build();
    }

    @Test
    void shouldConfirmWhenPaidAmountMatchesAndWithinDeadline() {
        PaymentVerificationResult paid = PaymentVerificationResult.paid("paid", 6375L, 6375L, "pix", "tx-1", "slug-1");
        assertEquals(PaymentConfirmationService.Decision.CONFIRM, service.evaluate(pendingAppointment(), paid, now));
    }

    @Test
    void shouldConfirmWhenPaidAmountIsHigherDueToInstallmentFees() {
        // Parcelado com taxa repassada: amount == 6375 (sinal), paidAmount == 6800 (> sinal) -> confirma!
        PaymentVerificationResult paid = PaymentVerificationResult.paid("paid", 6375L, 6800L, "credit_card", "tx-2", "slug-2");
        assertEquals(PaymentConfirmationService.Decision.CONFIRM, service.evaluate(pendingAppointment(), paid, now));
    }

    @Test
    void shouldFlagManualReviewWhenPaidAmountIsLessThanExpected() {
        PaymentVerificationResult underpaid = PaymentVerificationResult.paid("paid", 6375L, 5000L, "pix", "tx-3", "slug-3");
        assertEquals(PaymentConfirmationService.Decision.NEEDS_MANUAL_REVIEW, service.evaluate(pendingAppointment(), underpaid, now));
    }

    @Test
    void shouldReturnAmountMismatchLatePaymentRetryLaterAndNotPaid() {
        PaymentVerificationResult wrongCharge = PaymentVerificationResult.paid("paid", 1000L, 1000L, "pix", "tx-4", "slug-4");
        assertEquals(PaymentConfirmationService.Decision.AMOUNT_MISMATCH, service.evaluate(pendingAppointment(), wrongCharge, now));

        Appointment expired = pendingAppointment();
        expired.setExpiresAt(now.minusMinutes(5));
        PaymentVerificationResult paid = PaymentVerificationResult.paid("paid", 6375L, 6375L, "pix", "tx-5", "slug-5");
        assertEquals(PaymentConfirmationService.Decision.LATE_PAYMENT, service.evaluate(expired, paid, now));

        assertEquals(PaymentConfirmationService.Decision.RETRY_LATER,
                service.evaluate(pendingAppointment(), PaymentVerificationResult.unverifiable("timeout"), now));
        assertEquals(PaymentConfirmationService.Decision.NOT_PAID,
                service.evaluate(pendingAppointment(), PaymentVerificationResult.notPaid("pending", "aguardando"), now));
    }
}
