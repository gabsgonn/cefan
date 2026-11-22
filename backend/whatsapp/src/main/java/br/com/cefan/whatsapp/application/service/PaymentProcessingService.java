package br.com.cefan.whatsapp.application.service;

import br.com.cefan.whatsapp.api.controller.StudioAdminController;
import br.com.cefan.whatsapp.application.statemachine.handler.AgendamentoStateHandler;
import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import br.com.cefan.whatsapp.domain.model.ConversationState;
import br.com.cefan.whatsapp.domain.model.TenantConfig;
import br.com.cefan.whatsapp.domain.repository.AppointmentRepository;
import br.com.cefan.whatsapp.domain.repository.ConversationSessionRepository;
import br.com.cefan.whatsapp.domain.repository.TenantConfigRepository;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import br.com.cefan.whatsapp.infrastructure.mercadopago.MercadoPagoPaymentClient;
import br.com.cefan.whatsapp.infrastructure.mercadopago.dto.MercadoPagoPaymentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class PaymentProcessingService {

    private final AppointmentRepository appointmentRepository;
    private final MercadoPagoPaymentClient paymentClient;
    private final EvolutionApiClient evolutionApiClient;
    private final ConversationSessionRepository sessionRepository;
    private final TenantConfigRepository tenantConfigRepository;
    private final ChatService chatService;
    private final StudioAdminController studioAdminController;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("pt-BR"));

    public PaymentProcessingService(
            AppointmentRepository appointmentRepository,
            MercadoPagoPaymentClient paymentClient,
            EvolutionApiClient evolutionApiClient) {
        this(appointmentRepository, paymentClient, evolutionApiClient, null, null, null, null);
    }

    public PaymentProcessingService(
            AppointmentRepository appointmentRepository,
            MercadoPagoPaymentClient paymentClient,
            EvolutionApiClient evolutionApiClient,
            ConversationSessionRepository sessionRepository,
            TenantConfigRepository tenantConfigRepository,
            ChatService chatService) {
        this(appointmentRepository, paymentClient, evolutionApiClient, sessionRepository, tenantConfigRepository, chatService, null);
    }

    @Autowired
    public PaymentProcessingService(
            AppointmentRepository appointmentRepository,
            MercadoPagoPaymentClient paymentClient,
            EvolutionApiClient evolutionApiClient,
            ConversationSessionRepository sessionRepository,
            TenantConfigRepository tenantConfigRepository,
            ChatService chatService,
            StudioAdminController studioAdminController) {
        this.appointmentRepository = appointmentRepository;
        this.paymentClient = paymentClient;
        this.evolutionApiClient = evolutionApiClient;
        this.sessionRepository = sessionRepository;
        this.tenantConfigRepository = tenantConfigRepository;
        this.chatService = chatService;
        this.studioAdminController = studioAdminController;
    }

    @Transactional
    public boolean handlePaymentNotification(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            log.warn("Notificação recebida com paymentId vazio.");
            return false;
        }

        log.info("Processando notificação de pagamento do Mercado Pago para ID=[{}]", paymentId);

        Appointment appointment = appointmentRepository.findByMpPaymentIdForUpdate(paymentId)
                .or(() -> appointmentRepository.findByMpPaymentId(paymentId))
                .orElse(null);

        if (appointment == null) {
            log.warn("Nenhum agendamento encontrado no banco para o mpPaymentId=[{}]", paymentId);
            return false;
        }

        if (appointment.getStatus() == AppointmentStatus.CONFIRMADO) {
            log.info("Agendamento [{}] já estava confirmado. Ignorando reenvio de webhook.", appointment.getId());
            return true;
        }

        if (Boolean.TRUE.equals(appointment.getLatePaymentNotified())) {
            log.info("Notificação de pagamento tardio/conflitante já foi enviada para o agendamento [{}]. Ignorando reenvio.", appointment.getId());
            return false;
        }

        MercadoPagoPaymentResponse paymentResponse = paymentClient.getPayment(paymentId);
        if (paymentResponse != null && paymentResponse.isApproved()) {
            OffsetDateTime now = OffsetDateTime.now(AgendamentoStateHandler.STUDIO_ZONE);
            boolean isAlreadyInactive = appointment.getStatus() == AppointmentStatus.EXPIRADO
                    || appointment.getStatus() == AppointmentStatus.CANCELADO;
            boolean isOverduePending = appointment.getStatus() == AppointmentStatus.AGUARDANDO_PAGAMENTO
                    && appointment.getExpiresAt() != null
                    && !appointment.getExpiresAt().isAfter(now);

            if (isAlreadyInactive || isOverduePending) {
                log.warn("Pagamento [{}] aprovado APÓS vencimento/cancelamento do agendamento [{}] (status={}, expiresAt={}). Não confirmando automaticamente.",
                        paymentId, appointment.getId(), appointment.getStatus(), appointment.getExpiresAt());
                if (isOverduePending) {
                    appointment.setStatus(AppointmentStatus.EXPIRADO);
                }
                appointment.setLatePaymentNotified(true);
                appointmentRepository.save(appointment);
                handleLateOrConflictingPayment(appointment, paymentId, "prazo de reserva expirado");
                return false;
            }

            if (hasActiveOverlapWithAnotherAppointment(appointment, now)) {
                log.warn("Pagamento [{}] aprovado para agendamento [{}], mas o horário já está ocupado por outra reserva ativa. Encaminhando para humano.",
                        paymentId, appointment.getId());
                appointment.setStatus(AppointmentStatus.EXPIRADO);
                appointment.setLatePaymentNotified(true);
                appointmentRepository.save(appointment);
                handleLateOrConflictingPayment(appointment, paymentId, "horário ocupado por outra reserva");
                return false;
            }

            log.info("Pagamento [{}] APROVADO! Confirmando agendamento [{}]", paymentId, appointment.getId());
            appointment.setStatus(AppointmentStatus.CONFIRMADO);
            appointmentRepository.save(appointment);

            String customerPhone = appointment.getCustomer().getPhone();
            String customerName = appointment.getCustomer().getName();
            ZonedDateTime startBr = appointment.getScheduledStart().atZoneSameInstant(AgendamentoStateHandler.STUDIO_ZONE);
            String dateFormatted = startBr.format(DATE_FORMATTER);
            String timeFormatted = startBr.format(TIME_FORMATTER);

            TenantConfig config = (tenantConfigRepository != null)
                    ? tenantConfigRepository.findAll().stream().findFirst().orElse(null)
                    : null;
            String customHeader = (config != null && config.getPixConfirmedMessage() != null && !config.getPixConfirmedMessage().isBlank())
                    ? config.getPixConfirmedMessage() + "\n\n"
                    : "";

            String depositFormatted = AgendamentoStateHandler.formatBrl(appointment.getDepositAmount());
            String confirmationMessage = String.format(
                    "%s🎉 *Pagamento do Sinal Confirmado!* 🎉\n\n" +
                    "Olá, %s! Seu pagamento de *R$ %s* foi confirmado com sucesso!\n\n" +
                    "🗓️ *Sua sessão está 100%% garantida!*\n" +
                    "📅 *Data:* %s às %sh\n" +
                    "📍 *Local:* Estúdio Vício Tattoo\n\n" +
                    "💡 *Dicas importantes:*\n" +
                    "• Venha bem alimentado(a) e hidratado(a);\n" +
                    "• Vista roupas confortáveis que facilitem o acesso ao local da arte;\n" +
                    "• Evite consumir álcool nas 24h anteriores.\n\n" +
                    "Nos vemos em breve! Qualquer dúvida estamos por aqui. 🎨✨",
                    customHeader,
                    customerName,
                    depositFormatted,
                    dateFormatted,
                    timeFormatted
            );

            evolutionApiClient.sendTextMessage(customerPhone, confirmationMessage);

            if (sessionRepository != null) {
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
            }

            if (studioAdminController != null) {
                studioAdminController.publishNotification(
                        "PAYMENT_RECEIVED",
                        "Sinal PIX Confirmado!",
                        "Pagamento de R$ " + depositFormatted + " aprovado para " + customerName + ".",
                        customerPhone
                );
            }

            return true;
        } else {
            log.info("Pagamento [{}] ainda não está com status 'approved' (Status atual: [{}])",
                    paymentId, paymentResponse != null ? paymentResponse.getStatus() : "nulo");
            return false;
        }
    }

    private boolean hasActiveOverlapWithAnotherAppointment(Appointment appointment, OffsetDateTime now) {
        if (appointment.getArtist() == null || appointment.getArtist().getId() == null || appointment.getScheduledStart() == null) {
            return false;
        }
        OffsetDateTime slotStart = appointment.getScheduledStart();
        OffsetDateTime slotEnd = appointment.getScheduledEnd() != null
                ? appointment.getScheduledEnd()
                : slotStart.plusHours(2);
        List<Appointment> nearby = appointmentRepository.findByArtistIdAndScheduledStartBetween(
                appointment.getArtist().getId(),
                slotStart.minusDays(1),
                slotEnd.plusDays(1));
        if (nearby == null || nearby.isEmpty()) {
            return false;
        }
        for (Appointment other : nearby) {
            if (other.getId() != null && other.getId().equals(appointment.getId())) {
                continue;
            }
            if (other.getStatus() == AppointmentStatus.CANCELADO || other.getStatus() == AppointmentStatus.EXPIRADO) {
                continue;
            }
            if (other.getStatus() == AppointmentStatus.AGUARDANDO_PAGAMENTO
                    && other.getExpiresAt() != null
                    && !other.getExpiresAt().isAfter(now)) {
                continue;
            }
            if (other.getScheduledStart() == null) {
                continue;
            }
            OffsetDateTime otherEnd = other.getScheduledEnd() != null
                    ? other.getScheduledEnd()
                    : other.getScheduledStart().plusHours(2);
            if (other.getScheduledStart().isBefore(slotEnd) && otherEnd.isAfter(slotStart)) {
                return true;
            }
        }
        return false;
    }

    private void handleLateOrConflictingPayment(Appointment appointment, String paymentId, String reason) {
        String customerPhone = appointment.getCustomer().getPhone();
        String customerName = appointment.getCustomer().getName();
        ZonedDateTime startBr = appointment.getScheduledStart().atZoneSameInstant(AgendamentoStateHandler.STUDIO_ZONE);
        String dateFormatted = startBr.format(DATE_FORMATTER);
        String timeFormatted = startBr.format(TIME_FORMATTER);

        String depositFormatted = AgendamentoStateHandler.formatBrl(appointment.getDepositAmount());
        String lateMessage = String.format(
                "⚠️ *Pagamento Recebido Após o Prazo de Reserva*\n\n" +
                "Olá, %s! Identificamos o seu pagamento de *R$ %s*, mas o prazo de reserva do horário de *%s às %sh* já havia expirado e a vaga foi liberada na agenda.\n\n" +
                "Fique tranquilo(a): já acionei nossa equipe aqui no chat para escolher um novo horário com você ou realizar o estorno do sinal! 🎨",
                customerName,
                depositFormatted,
                dateFormatted,
                timeFormatted
        );

        evolutionApiClient.sendTextMessage(customerPhone, lateMessage);

        if (sessionRepository != null) {
            sessionRepository.findByCustomer(appointment.getCustomer())
                    .ifPresent(session -> {
                        session.setCurrentState(ConversationState.TRANSBORDO_HUMANO);
                        if (appointment.getArtist() != null) {
                            session.setAssignedUser(appointment.getArtist());
                            session.setAssignedArtistName(appointment.getArtist().getName());
                        }
                        sessionRepository.save(session);
                        if (chatService != null) {
                            chatService.recordBotMessage(session, appointment.getCustomer(), lateMessage);
                        }
                    });
        }

        if (studioAdminController != null) {
            studioAdminController.publishNotification(
                    "HUMAN_REQUEST",
                    "PIX Tardio / Conflito (" + paymentId + ")",
                    customerName + " pagou R$ " + depositFormatted + " com " + reason
                            + " (horário original " + dateFormatted + " às " + timeFormatted + "). Reagendar ou estornar.",
                    customerPhone
            );
        }
    }
}
