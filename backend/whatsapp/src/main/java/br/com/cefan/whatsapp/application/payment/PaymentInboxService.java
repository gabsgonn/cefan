package br.com.cefan.whatsapp.application.payment;

import br.com.cefan.whatsapp.api.controller.StudioAdminController;
import br.com.cefan.whatsapp.application.service.ChatService;
import br.com.cefan.whatsapp.application.service.TenantResolver;
import br.com.cefan.whatsapp.application.statemachine.handler.AgendamentoStateHandler;
import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import br.com.cefan.whatsapp.domain.model.PaymentInboxEvent;
import br.com.cefan.whatsapp.domain.model.TenantConfig;
import br.com.cefan.whatsapp.domain.repository.AppointmentRepository;
import br.com.cefan.whatsapp.domain.repository.ConversationSessionRepository;
import br.com.cefan.whatsapp.domain.repository.PaymentInboxEventRepository;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentInboxService {

    private static final int MAX_ATTEMPTS = 6;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("pt-BR"));

    private final PaymentInboxEventRepository inboxRepository;
    private final AppointmentRepository appointmentRepository;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentConfirmationService confirmationService;
    private final TenantResolver tenantResolver;
    private final EvolutionApiClient evolutionApiClient;
    private final StudioAdminController studioAdminController;
    private final ConversationSessionRepository sessionRepository;
    private final ChatService chatService;

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
            PaymentInboxEvent found = existing.get();
            boolean changed = false;
            if ((found.getInvoiceSlug() == null || found.getInvoiceSlug().isBlank()) && invoiceSlug != null && !invoiceSlug.isBlank()) {
                found.setInvoiceSlug(invoiceSlug.trim());
                changed = true;
            }
            if ((found.getCaptureMethod() == null || found.getCaptureMethod().isBlank()) && captureMethod != null && !captureMethod.isBlank()) {
                found.setCaptureMethod(captureMethod.trim());
                changed = true;
            }
            if (found.getAppointmentId() == null && appointmentId != null) {
                found.setAppointmentId(appointmentId);
                changed = true;
            }
            if (found.getOrderNsu() == null && orderNsu != null) {
                found.setOrderNsu(orderNsu);
                changed = true;
            }
            return changed ? inboxRepository.save(found) : found;
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

    /**
     * Processa o evento imediatamente (sem esperar o worker), tentando até 2 vezes.
     * Usado pelo retorno do checkout e pelo webhook para avisar o cliente no WhatsApp na hora.
     */
    public void processNowWithRetry(PaymentInboxEvent event) {
        if (event == null || event.getId() == null) {
            return;
        }
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                PaymentInboxEvent current = inboxRepository.findById(event.getId()).orElse(null);
                if (current == null || current.getStatus() != PaymentInboxEvent.Status.PENDING) {
                    return;
                }
                processSingleEvent(current, OffsetDateTime.now(AgendamentoStateHandler.STUDIO_ZONE));
                PaymentInboxEvent after = inboxRepository.findById(event.getId()).orElse(null);
                if (after == null || after.getStatus() != PaymentInboxEvent.Status.PENDING) {
                    return;
                }
            } catch (Exception e) {
                log.warn("Tentativa {} de processar pagamento imediatamente falhou (tx={}): {}",
                        attempt, event.getTransactionNsu(), e.getMessage());
            }
            if (attempt < 2) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Transactional
    public void processSingleEvent(PaymentInboxEvent event, OffsetDateTime now) {
        UUID lookupId = event.getAppointmentId() != null ? event.getAppointmentId() : event.getOrderNsu();
        Appointment appointment = (lookupId != null)
                ? appointmentRepository.findByIdWithCustomerAndArtist(lookupId)
                        .or(() -> appointmentRepository.findById(lookupId))
                        .orElse(null)
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

        String slugToVerify = event.getInvoiceSlug();
        if ((slugToVerify == null || slugToVerify.isBlank()) && appointment.getPaymentUrl() != null) {
            slugToVerify = extractSlugFromPaymentUrl(appointment.getPaymentUrl());
        }

        PaymentEvidence evidence = new PaymentEvidence(
                provider,
                appointment.getPaymentReference(),
                appointment.getId(),
                event.getTransactionNsu(),
                slugToVerify
        );
        PaymentVerificationResult verification = gateway.verify(tenantConfig, evidence);
        PaymentConfirmationService.Decision decision = confirmationService.evaluate(appointment, verification, now);
        log.info("Avaliação de pagamento InfinitePay para reserva [{}] (tx={}, slug={}): status={}, decision={}, detail={}",
                appointment.getId(), event.getTransactionNsu(), slugToVerify, verification.status(), decision, verification.detail());

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
                    sendRichConfirmationAndRecord(appointment, tenantConfig);
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
            case RETRY_LATER, NOT_PAID -> {
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

    private void sendRichConfirmationAndRecord(Appointment appointment, TenantConfig config) {
        String customerPhone = appointment.getCustomer().getPhone();
        String customerName = appointment.getCustomer().getName();
        ZonedDateTime startBr = appointment.getScheduledStart() != null
                ? appointment.getScheduledStart().atZoneSameInstant(AgendamentoStateHandler.STUDIO_ZONE)
                : ZonedDateTime.now(AgendamentoStateHandler.STUDIO_ZONE);
        String dateFormatted = startBr.format(DATE_FORMATTER);
        String timeFormatted = startBr.format(TIME_FORMATTER);

        String customHeader = (config != null && config.getPixConfirmedMessage() != null && !config.getPixConfirmedMessage().isBlank())
                ? config.getPixConfirmedMessage() + "\n\n"
                : "";
        String studioName = (config != null && config.getStudioName() != null && !config.getStudioName().isBlank())
                ? config.getStudioName()
                : "Estúdio Vício Tattoo";

        String depositFormatted = AgendamentoStateHandler.formatBrl(appointment.getDepositAmount());
        String confirmationMessage = String.format(
                "%s🎉 *Pagamento do Sinal Confirmado!* 🎉\n\n" +
                "Olá, %s! Seu pagamento de *R$ %s* foi confirmado com sucesso!\n\n" +
                "🗓️ *Sua sessão está 100%% garantida!*\n" +
                "📅 *Data:* %s às %sh\n" +
                "📍 *Local:* %s\n\n" +
                "💡 *Dicas importantes:*\n" +
                "• Venha bem alimentado(a) e hidratado(a);\n" +
                "• Vista roupas confortáveis que facilitem o acesso ao local da arte;\n" +
                "• Evite consumir álcool nas 24h anteriores.\n\n" +
                "Nos vemos em breve! Qualquer dúvida estamos por aqui. 🎨✨",
                customHeader,
                customerName,
                depositFormatted,
                dateFormatted,
                timeFormatted,
                studioName
        );

        evolutionApiClient.sendTextMessage(customerPhone, confirmationMessage);

        if (sessionRepository != null) {
            try {
                sessionRepository.findByCustomer(appointment.getCustomer())
                        .ifPresent(session -> {
                            if (appointment.getArtist() != null) {
                                session.setAssignedUser(appointment.getArtist());
                                session.setAssignedArtistName(appointment.getArtist().getName());
                                sessionRepository.save(session);
                            }
                            if (chatService != null) {
                                chatService.recordBotMessage(session, appointment.getCustomer(), confirmationMessage);
                            }
                        });
            } catch (Exception e) {
                log.warn("Falha ao registrar mensagem de confirmação no histórico do chat: {}", e.getMessage());
            }
        }

        if (studioAdminController != null) {
            try {
                studioAdminController.publishNotification(
                        "PAYMENT_RECEIVED",
                        "Sinal Confirmado!",
                        "Pagamento de R$ " + depositFormatted + " aprovado para " + customerName + ".",
                        customerPhone
                );
            } catch (Exception e) {
                log.warn("Falha ao publicar notificação de pagamento no painel: {}", e.getMessage());
            }
        }
    }

    private String extractSlugFromPaymentUrl(String paymentUrl) {
        if (paymentUrl == null || paymentUrl.isBlank()) {
            return null;
        }
        String clean = paymentUrl.trim();
        int qIdx = clean.indexOf('?');
        if (qIdx >= 0) {
            clean = clean.substring(0, qIdx);
        }
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        int lastSlash = clean.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < clean.length() - 1) {
            return clean.substring(lastSlash + 1);
        }
        return null;
    }
}
