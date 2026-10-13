package br.com.cefan.whatsapp.application.service;

import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.repository.AppointmentRepository;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import br.com.cefan.whatsapp.infrastructure.mercadopago.MercadoPagoPaymentClient;
import br.com.cefan.whatsapp.infrastructure.mercadopago.dto.MercadoPagoPaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentProcessingServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private MercadoPagoPaymentClient paymentClient;

    @Mock
    private EvolutionApiClient evolutionApiClient;

    private PaymentProcessingService service;
    private Customer customer;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        service = new PaymentProcessingService(appointmentRepository, paymentClient, evolutionApiClient);

        customer = Customer.builder()
                .id(UUID.randomUUID())
                .name("Larissa")
                .phone("5511977777777")
                .build();

        appointment = Appointment.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .scheduledStart(OffsetDateTime.now().plusDays(1).withHour(15).withMinute(0))
                .scheduledEnd(OffsetDateTime.now().plusDays(1).withHour(17).withMinute(0))
                .status(AppointmentStatus.AGUARDANDO_PAGAMENTO)
                .depositAmount(new BigDecimal("100.00"))
                .totalPrice(new BigDecimal("350.00"))
                .mpPaymentId("987654321")
                .build();
    }

    @Test
    void shouldConfirmAppointmentAndSendWhatsAppWhenPaymentIsApproved() {
        when(appointmentRepository.findByMpPaymentId("987654321")).thenReturn(Optional.of(appointment));

        MercadoPagoPaymentResponse paymentResponse = MercadoPagoPaymentResponse.builder()
                .id(987654321L)
                .status("approved")
                .statusDetail("accredited")
                .build();
        when(paymentClient.getPayment("987654321")).thenReturn(paymentResponse);

        boolean result = service.handlePaymentNotification("987654321");

        assertTrue(result);
        assertEquals(AppointmentStatus.CONFIRMADO, appointment.getStatus());
        verify(appointmentRepository).save(appointment);
        verify(evolutionApiClient).sendTextMessage(eq("5511977777777"), contains("Pagamento do Sinal Confirmado"));
    }

    @Test
    void shouldNotConfirmAppointmentWhenPaymentIsNotApproved() {
        when(appointmentRepository.findByMpPaymentId("987654321")).thenReturn(Optional.of(appointment));

        MercadoPagoPaymentResponse paymentResponse = MercadoPagoPaymentResponse.builder()
                .id(987654321L)
                .status("pending")
                .build();
        when(paymentClient.getPayment("987654321")).thenReturn(paymentResponse);

        boolean result = service.handlePaymentNotification("987654321");

        assertFalse(result);
        assertEquals(AppointmentStatus.AGUARDANDO_PAGAMENTO, appointment.getStatus());
        verify(appointmentRepository, never()).save(any());
        verifyNoInteractions(evolutionApiClient);
    }

    @Test
    void shouldReturnFalseWhenAppointmentNotFound() {
        when(appointmentRepository.findByMpPaymentId("non_existent")).thenReturn(Optional.empty());

        boolean result = service.handlePaymentNotification("non_existent");

        assertFalse(result);
        verifyNoInteractions(paymentClient);
        verifyNoInteractions(evolutionApiClient);
    }
}
