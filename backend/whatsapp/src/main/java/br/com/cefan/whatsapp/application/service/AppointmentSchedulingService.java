package br.com.cefan.whatsapp.application.service;

import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import br.com.cefan.whatsapp.domain.repository.AppointmentRepository;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentSchedulingService {

    private final AppointmentRepository appointmentRepository;
    private final EvolutionApiClient evolutionApiClient;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("pt-BR"));

    /**
     * Rotina assíncrona que roda a cada 5 minutos verificando agendamentos que expiraram (2 horas sem pagamento do sinal).
     */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void expireUnpaidAppointments() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Appointment> expired = appointmentRepository.findByStatusAndExpiresAtBefore(
                AppointmentStatus.AGUARDANDO_PAGAMENTO, now);

        if (!expired.isEmpty()) {
            log.info("Encontrados [{}] agendamentos expirados para cancelamento automático.", expired.size());
        }

        for (Appointment appointment : expired) {
            appointment.setStatus(AppointmentStatus.EXPIRADO);
            appointmentRepository.save(appointment);

            String customerPhone = appointment.getCustomer().getPhone();
            String customerName = appointment.getCustomer().getName();

            log.info("Agendamento [{}] do cliente [{}] expirado por falta de pagamento.",
                    appointment.getId(), customerPhone);

            String message = String.format(
                    "⏰ *Aviso de Expiração do Horário* ⏰\n\n" +
                    "Olá, %s! Seu pré-agendamento de sessão não foi concluído devido ao tempo limite de 2 horas para o pagamento do sinal via PIX.\n\n" +
                    "A grade de horários foi liberada. Caso ainda deseje agendar sua sessão, basta digitar *2* para escolher uma nova data a qualquer momento! 🎨",
                    customerName
            );

            evolutionApiClient.sendTextMessage(customerPhone, message);
        }
    }

    /**
     * Rotina diária (às 09:00) que envia lembrete 24h antes da sessão para agendamentos confirmados.
     */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void send24hReminders() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime next26Hours = now.plusHours(26);

        List<Appointment> upcoming = appointmentRepository.findByStatusAndScheduledStartBetweenAndReminder24hSentFalse(
                AppointmentStatus.CONFIRMADO, now, next26Hours);

        if (!upcoming.isEmpty()) {
            log.info("Encontrados [{}] agendamentos confirmados elegíveis para lembrete de 24h.", upcoming.size());
        }

        for (Appointment appointment : upcoming) {
            String customerPhone = appointment.getCustomer().getPhone();
            String customerName = appointment.getCustomer().getName();
            String dateFormatted = appointment.getScheduledStart().format(DATE_FORMATTER);
            String timeFormatted = appointment.getScheduledStart().format(TIME_FORMATTER);

            String reminderMessage = String.format(
                    "🔔 *Lembrete da sua Sessão de Tatuagem!* 🔔\n\n" +
                    "Olá, %s! Lembramos que sua sessão no *Vício Tattoo* está confirmada para %s às %sh! 🎨\n\n" +
                    "Recomendações importantes:\n" +
                    "• Mantenha-se bem alimentado(a) e hidratado(a);\n" +
                    "• Use roupas confortáveis;\n" +
                    "• Evite ingestão de bebidas alcoólicas antes da sessão.\n\n" +
                    "Estamos ansiosos para criar sua tatuagem! Nos vemos no estúdio. ✨",
                    customerName,
                    dateFormatted,
                    timeFormatted
            );

            evolutionApiClient.sendTextMessage(customerPhone, reminderMessage);
            appointment.setReminder24hSent(true);
            appointmentRepository.save(appointment);
        }
    }
}
