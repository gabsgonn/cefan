package br.com.cefan.whatsapp.application.service;

import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.repository.AppointmentRepository;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentSchedulingServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private EvolutionApiClient evolutionApiClient;

    private AppointmentSchedulingService service;
    private Customer customer;

    @BeforeEach
    void setUp() {
        service = new AppointmentSchedulingService(appointmentRepository, evolutionApiClient);

        customer = Customer.builder()
                .id(UUID.randomUUID())
                .name("Carlos")
                .phone("5511966666666")
                .build();
    }

    @Test
    void shouldExpireAppointmentsAndSendWhatsAppWhenTimeLimitExceeded() {
        Appointment expiredAppointment = Appointment.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .status(AppointmentStatus.AGUARDANDO_PAGAMENTO)
                .expiresAt(OffsetDateTime.now().minusMinutes(5))
                .scheduledStart(OffsetDateTime.now().plusDays(2))
                .scheduledEnd(OffsetDateTime.now().plusDays(2).plusHours(2))
                .depositAmount(new BigDecimal("80.00"))
                .build();

        when(appointmentRepository.findByStatusAndExpiresAtBefore(eq(AppointmentStatus.AGUARDANDO_PAGAMENTO), any()))
                .thenReturn(List.of(expiredAppointment));

        service.expireUnpaidAppointments();

        assertEquals(AppointmentStatus.EXPIRADO, expiredAppointment.getStatus());
        verify(appointmentRepository).save(expiredAppointment);
        verify(evolutionApiClient).sendTextMessage(eq("5511966666666"), contains("Aviso de Expiração"));
    }

    @Test
    void shouldSend24hReminderForUpcomingConfirmedAppointments() {
        Appointment confirmedAppointment = Appointment.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .status(AppointmentStatus.CONFIRMADO)
                .scheduledStart(OffsetDateTime.now().plusHours(20))
                .scheduledEnd(OffsetDateTime.now().plusHours(22))
                .reminder24hSent(false)
                .depositAmount(new BigDecimal("80.00"))
                .build();

        when(appointmentRepository.findByStatusAndScheduledStartBetweenAndReminder24hSentFalse(
                eq(AppointmentStatus.CONFIRMADO), any(), any()))
                .thenReturn(List.of(confirmedAppointment));

        service.send24hReminders();

        assertTrue(confirmedAppointment.getReminder24hSent());
        verify(appointmentRepository).save(confirmedAppointment);
        verify(evolutionApiClient).sendTextMessage(eq("5511966666666"), contains("Lembrete da sua Sessão de Tatuagem"));
    }
}
