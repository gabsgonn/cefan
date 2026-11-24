package br.com.cefan.whatsapp.application.payment;

import br.com.cefan.whatsapp.api.controller.StudioAdminController;
import br.com.cefan.whatsapp.application.service.TenantResolver;
import br.com.cefan.whatsapp.application.statemachine.handler.AgendamentoStateHandler;
import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import br.com.cefan.whatsapp.domain.model.PaymentInboxEvent;
import br.com.cefan.whatsapp.domain.model.TenantConfig;
import br.com.cefan.whatsapp.domain.repository.AppointmentRepository;
import br.com.cefan.whatsapp.domain.repository.PaymentInboxEventRepository;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentInboxService {

    private static final int MAX_ATTEMPTS = 6;

    private final PaymentInboxEventRepository inboxRepository;
    private final AppointmentRepository appointmentRepository;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentConfirmationService confirmationService;
    private final TenantResolver tenantResolver;
    private final EvolutionApiClient evolutionApiClient;
    private final StudioAdminController studioAdminController;

    @Transactional
    public PaymentInboxEvent enqueueIdempotent(
            String provider,
            UUID appointmentId,
            UUID orderNsu,
            String transactionNsu,
            String invoiceSlug,
            String captureMethod,
            String source) {
        Optional<PaymentInboxEvent> existing = inboxRepository.findByProviderAndTransactionNsu(provider, transactionNsu);
        if (existing.isPresent()) {
            return existing.get();
        }
        OffsetDateTime now = OffsetDateTime.now(AgendamentoStateHandler.STUDIO_ZONE);
        return inboxRepository.save(PaymentInboxEvent.builder()
                .provider(provider)
                .appointmentId(appointmentId != null ? appointmentId : orderNsu)
                .orderNsu(orderNsu != null ? orderNsu : appointmentId)
                .transactionNsu(transactionNsu)
                .invoiceSlug(invoiceSlug)
                .captureMethod(captureMethod)
                .source(source)
                .status(PaymentInboxEvent.Status.PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .build());
    }

    @Scheduled(fixedDelayString = "${infinitepay.inbox.poll-ms:15000}")
    @Transactional
    public void processPendingEvents() {
        OffsetDateTime now = OffsetDateTime.now(AgendamentoStateHandler.STUDIO_ZONE);
        List<PaymentInboxEvent> pending = inboxRepository
                .findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(PaymentInboxEvent.Status.PENDING, now);
        for (PaymentInboxEvent event : pending) {
            processSingleEvent(event, now);
        }
    }

    public void processSingleEvent(PaymentInboxEvent event, OffsetDateTime now) {
        UUID lookupId = event.getAppointmentId() != null ? event.getAppointmentId() : event.getOrderNsu();
        Appointment appointment = (lookupId != null)
                ? appointmentRepository.findById(lookupId).orElse(null)
                : null;

        // Checa o banco ANTES de chamar payment_check
        if (appointment == null) {
            event.setStatus(PaymentInboxEvent.Status.IGNORED);
            event.setLastError("Reserva não encontrada para order_nsu=" + lookupId);
            event.setProcessedAt(now);
            inboxRepository.save(event);
            return;
        }

        if (appointment.getStatus() == AppointmentStatus.CONFIRMADO) {
            event.setStatus(PaymentInboxEvent.Status.PROCESSED);
            event.setProcessedAt(now);
            inboxRepository.save(event);
            return;
        }

        String provider = (appointment.getPaymentProvider() != null && !appointment.getPaymentProvider().isBlank())
                ? appointment.getPaymentProvider()
                : event.getProvider();

        PaymentGateway gateway = gatewayRegistry.byProvider(provider).orElse(null);
        TenantConfig tenantConfig = tenantResolver.resolvePrimaryTenant().orElse(null);
        if (gateway == null) {
            event.setStatus(PaymentInboxEvent.Status.FAILED);
            event.setLastError("Provedor não registrado: " + provider);
            event.setProcessedAt(now);
            inboxRepository.save(event);
            return;
        }

        PaymentEvidence evidence = new PaymentEvidence(
                provider,
                appointment.getPaymentReference(),
                appointment.getId(),
                event.getTransactionNsu(),
                event.getInvoiceSlug()
        );
        PaymentVerificationResult verification = gateway.verify(tenantConfig, evidence);
        PaymentConfirmationService.Decision decision = confirmationService.evaluate(appointment, verification, now);

        switch (decision) {
            case CONFIRM -> {
                appointment.setStatus(AppointmentStatus.CONFIRMADO);
                appointment.setPaidAmountCents(verification.paidAmountCents());
                appointment.setCaptureMethod(verification.captureMethod());
                appointment.setTransactionNsu(verification.transactionNsu());
                appointment.setInvoiceSlug(verification.invoiceSlug());
                appointmentRepository.save(appointment);

                event.setStatus(PaymentInboxEvent.Status.PROCESSED);
                event.setProcessedAt(now);
                inboxRepository.save(event);

                if (appointment.getCustomer() != null) {
                    String msg = "🎉 *Pagamento do Sinal Confirmado!*\n\nSeu agendamento está 100% garantido! Nos vemos no estúdio. ⚡";
                    evolutionApiClient.sendTextMessage(appointment.getCustomer().getPhone(), msg);
                }
            }
            case NEEDS_MANUAL_REVIEW -> {
                appointment.setNeedsManualReview(true);
                appointment.setPaidAmountCents(verification.paidAmountCents());
                appointment.setTransactionNsu(verification.transactionNsu());
                appointment.setInvoiceSlug(verification.invoiceSlug());
                appointmentRepository.save(appointment);

                event.setStatus(PaymentInboxEvent.Status.PROCESSED);
                event.setLastError("paid_amount < amount: encaminhado para revisão manual");
                event.setProcessedAt(now);
                inboxRepository.save(event);

                studioAdminController.publishNotification(
                        "PAYMENT_REVIEW",
                        "Pagamento para Revisão Manual",
                        "Reserva " + appointment.getId() + " recebeu valor pago menor que o sinal. Verifique no app.",
                        appointment.getCustomer() != null ? appointment.getCustomer().getPhone() : null
                );
            }
            case LATE_PAYMENT -> {
                if (!Boolean.TRUE.equals(appointment.getLatePaymentNotified())) {
                    appointment.setLatePaymentNotified(true);
                    appointment.setNeedsManualReview(true);
                    appointment.setTransactionNsu(verification.transactionNsu());
                    appointmentRepository.save(appointment);
                    if (appointment.getCustomer() != null) {
                        evolutionApiClient.sendTextMessage(
                                appointment.getCustomer().getPhone(),
                                "⚠️ Identificamos o seu pagamento, porém o prazo da reserva já havia expirado. Encaminhamos para nossa equipe alinhar um novo horário ou realizar o estorno."
                        );
                    }
                    studioAdminController.publishNotification(
                            "LATE_PAYMENT",
                            "Pagamento Tardio (Estorno Manual)",
                            "Reserva " + appointment.getId() + " paga após expiração (tx=" + verification.transactionNsu() + ").",
                            appointment.getCustomer() != null ? appointment.getCustomer().getPhone() : null
                    );
                }
                event.setStatus(PaymentInboxEvent.Status.PROCESSED);
                event.setProcessedAt(now);
                inboxRepository.save(event);
            }
            case RETRY_LATER -> {
                int nextAttempt = (event.getAttempts() != null ? event.getAttempts() : 0) + 1;
                event.setAttempts(nextAttempt);
                event.setLastError(verification.detail());
                if (nextAttempt >= MAX_ATTEMPTS) {
                    event.setStatus(PaymentInboxEvent.Status.FAILED);
                    event.setProcessedAt(now);
                } else {
                    long delaySeconds = 15L * (1L << Math.min(nextAttempt, 5));
                    event.setNextAttemptAt(now.plusSeconds(delaySeconds));
                }
                inboxRepository.save(event);
            }
            default -> {
                event.setStatus(PaymentInboxEvent.Status.IGNORED);
                event.setLastError("Decisão: " + decision + " (" + verification.detail() + ")");
                event.setProcessedAt(now);
                inboxRepository.save(event);
            }
        }
    }
}
