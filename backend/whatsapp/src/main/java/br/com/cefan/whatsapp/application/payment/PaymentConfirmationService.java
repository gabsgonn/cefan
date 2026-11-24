package br.com.cefan.whatsapp.application.payment;

import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

/**
 * Centraliza as regras de negócio de confirmação de pagamento fora dos adapters.
 * Os adapters apenas relatam fatos ({@link PaymentVerificationResult}); este serviço decide.
 */
@Service
public class PaymentConfirmationService {

    public enum Decision {
        CONFIRM,
        ALREADY_CONFIRMED,
        LATE_PAYMENT,
        AMOUNT_MISMATCH,
        NEEDS_MANUAL_REVIEW,
        NOT_PAID,
        RETRY_LATER
    }

    public Decision evaluate(Appointment appointment, PaymentVerificationResult verification, OffsetDateTime now) {
        if (appointment == null || verification == null) {
            return Decision.NOT_PAID;
        }
        if (verification.status() == PaymentVerificationStatus.UNVERIFIABLE) {
            return Decision.RETRY_LATER;
        }
        if (verification.status() != PaymentVerificationStatus.PAID) {
            return Decision.NOT_PAID;
        }
        if (appointment.getStatus() == AppointmentStatus.CONFIRMADO) {
            return Decision.ALREADY_CONFIRMED;
        }
        boolean expired = appointment.getExpiresAt() != null && now != null && appointment.getExpiresAt().isBefore(now);
        if (appointment.getStatus() != AppointmentStatus.AGUARDANDO_PAGAMENTO || expired) {
            return Decision.LATE_PAYMENT;
        }

        long expectedCents = toCents(appointment.getDepositAmount());
        if (verification.amountCents() != null && expectedCents > 0 && verification.amountCents() != expectedCents) {
            return Decision.AMOUNT_MISMATCH;
        }
        if (verification.paidAmountCents() != null && expectedCents > 0 && verification.paidAmountCents() < expectedCents) {
            return Decision.NEEDS_MANUAL_REVIEW;
        }
        return Decision.CONFIRM;
    }

    public static long toCents(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }
}
