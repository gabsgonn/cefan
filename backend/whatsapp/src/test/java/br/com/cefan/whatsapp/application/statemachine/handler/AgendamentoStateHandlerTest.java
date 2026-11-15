package br.com.cefan.whatsapp.application.statemachine.handler;

import br.com.cefan.whatsapp.application.service.ChatService;
import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import br.com.cefan.whatsapp.domain.model.ConversationSession;
import br.com.cefan.whatsapp.domain.model.ConversationState;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.repository.AppointmentRepository;
import br.com.cefan.whatsapp.domain.repository.TenantConfigRepository;
import br.com.cefan.whatsapp.domain.repository.UserRepository;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import br.com.cefan.whatsapp.infrastructure.evolution.dto.EvolutionWebhookRequest;
import br.com.cefan.whatsapp.infrastructure.mercadopago.MercadoPagoPaymentClient;
import br.com.cefan.whatsapp.infrastructure.mercadopago.dto.MercadoPagoPaymentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgendamentoStateHandlerTest {

    @Mock
    private EvolutionApiClient evolutionApiClient;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private MercadoPagoPaymentClient paymentClient;

    @Mock
    private ChatService chatService;

    @Mock
    private TenantConfigRepository tenantConfigRepository;

    @Mock
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AgendamentoStateHandler handler;
    private Customer customer;
    private ConversationSession session;

    @BeforeEach
    void setUp() {
        handler = new AgendamentoStateHandler(
                evolutionApiClient,
                appointmentRepository,
                paymentClient,
                objectMapper,
                chatService,
                tenantConfigRepository,
                userRepository
        );

        customer = Customer.builder()
                .id(UUID.randomUUID())
                .name("Fernanda")
                .phone("5511955555555")
                .build();

        session = ConversationSession.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .currentState(ConversationState.AGENDAMENTO_SELECAO_DATA)
                .contextData("{\"basePrice\":\"400.00\"}")
                .lastInteractionAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void shouldCreateAppointmentAndSendPixCopyPasteToCustomer() {
        MercadoPagoPaymentResponse mpResponse = MercadoPagoPaymentResponse.builder()
                .id(123456789L)
                .status("pending")
                .transactionAmount(new BigDecimal("120.00"))
                .pointOfInteraction(MercadoPagoPaymentResponse.PointOfInteraction.builder()
                        .transactionData(MercadoPagoPaymentResponse.TransactionData.builder()
                                .qrCode("00020126580014br.gov.bcb.pix0136test-pix-code")
                                .build())
                        .build())
                .build();

        when(paymentClient.createPixPayment(any(), any(), any(), any(), any())).thenReturn(mpResponse);

        EvolutionWebhookRequest webhook = new EvolutionWebhookRequest();
        webhook.setInstance("cefan-studio");
        EvolutionWebhookRequest.WebhookData data = new EvolutionWebhookRequest.WebhookData();
        EvolutionWebhookRequest.MessageContent content = new EvolutionWebhookRequest.MessageContent();
        content.setConversation("Quero agendar para sexta à tarde");
        data.setMessage(content);
        webhook.setData(data);

        handler.handle(customer, session, webhook, "Quero agendar para sexta à tarde");

        ArgumentCaptor<Appointment> appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(appointmentCaptor.capture());

        Appointment saved = appointmentCaptor.getValue();
        assertEquals(AppointmentStatus.AGUARDANDO_PAGAMENTO, saved.getStatus());
        assertEquals("123456789", saved.getMpPaymentId());
        assertEquals(new BigDecimal("400.00"), saved.getTotalPrice());
        assertEquals(new BigDecimal("120.00"), saved.getDepositAmount());
        assertNotNull(saved.getExpiresAt());

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511955555555"), messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertTrue(message.contains("Pré-Agendamento Criado"));
        assertTrue(message.contains("00020126580014br.gov.bcb.pix0136test-pix-code"));
        assertTrue(message.contains("120.00"));
    }

    @Test
    void shouldParseExplicitHourAndShiftSlotWhenArtistHasConflict() {
        br.com.cefan.whatsapp.domain.model.User carlos = br.com.cefan.whatsapp.domain.model.User.builder()
                .id(UUID.randomUUID())
                .name("Carlos Silveira")
                .role(br.com.cefan.whatsapp.domain.model.UserRole.ARTIST)
                .isActive(true)
                .build();
        when(userRepository.findAll()).thenReturn(java.util.List.of(carlos));

        Appointment existingConflict = Appointment.builder()
                .id(UUID.randomUUID())
                .artist(carlos)
                .status(AppointmentStatus.CONFIRMADO)
                .scheduledStart(OffsetDateTime.now().plusDays(1).withHour(15).withMinute(0))
                .scheduledEnd(OffsetDateTime.now().plusDays(1).withHour(17).withMinute(0))
                .build();

        // First call has conflict at 15h, second call at 18h is free
        when(appointmentRepository.findByArtistIdAndScheduledStartBetween(eq(carlos.getId()), any(), any()))
                .thenReturn(java.util.List.of(existingConflict))
                .thenReturn(java.util.List.of());

        MercadoPagoPaymentResponse mpResponse = MercadoPagoPaymentResponse.builder()
                .id(999888L)
                .status("pending")
                .transactionAmount(new BigDecimal("120.00"))
                .build();
        when(paymentClient.createPixPayment(any(), any(), any(), any(), any())).thenReturn(mpResponse);

        EvolutionWebhookRequest webhook = new EvolutionWebhookRequest();
        webhook.setInstance("cefan-studio");

        handler.handle(customer, session, webhook, "Quero com o Carlos amanhã às 15h");

        ArgumentCaptor<Appointment> appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(appointmentCaptor.capture());

        Appointment saved = appointmentCaptor.getValue();
        assertEquals(carlos.getId(), saved.getArtist().getId());
        assertEquals(18, saved.getScheduledStart().getHour());

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511955555555"), messageCaptor.capture());
        String msg = messageCaptor.getValue();
        assertTrue(msg.contains("já estava ocupado na agenda de *Carlos Silveira*"));
        assertTrue(msg.contains("Pré-Agendamento Criado"));
    }

    @Test
    void shouldTransitionToHumanHandoffWhenUserSends3DuringScheduling() {
        EvolutionWebhookRequest webhook = new EvolutionWebhookRequest();
        webhook.setInstance("cefan-studio");

        handler.handle(customer, session, webhook, "3");

        assertEquals(ConversationState.TRANSBORDO_HUMANO, session.getCurrentState());
        verify(appointmentRepository, never()).save(any());
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511955555555"), contains("tatuadores"));
    }

    @Test
    void shouldParseMinutesAndHandleHojeSafelyWithoutDateTimeException() {
        OffsetDateTime slotWithMinutes = handler.parseRequestedSlot("Quero agendar amanhã às 15:30");
        assertEquals(15, slotWithMinutes.getHour());
        assertEquals(30, slotWithMinutes.getMinute());

        OffsetDateTime slotWithHMinutes = handler.parseRequestedSlot("Quero amanhã às 16h45");
        assertEquals(16, slotWithHMinutes.getHour());
        assertEquals(45, slotWithHMinutes.getMinute());

        OffsetDateTime slotHoje = assertDoesNotThrow(() -> handler.parseRequestedSlot("Quero agendar hoje"));
        assertNotNull(slotHoje);
        assertTrue(slotHoje.getHour() >= 8 && slotHoje.getHour() <= 20);
    }

    @Test
    void shouldRespectOperatingHoursWorkingDaysAndCustomPixExpiration() {
        br.com.cefan.whatsapp.domain.model.TenantConfig config = br.com.cefan.whatsapp.domain.model.TenantConfig.builder()
                .studioName("Vício Tattoo CWB")
                .defaultHourlyRate(new BigDecimal("180.00"))
                .fixedSetupCost(new BigDecimal("60.00"))
                .signalPercentage(new BigDecimal("30.00"))
                .minDepositAmount(new BigDecimal("50.00"))
                .pixExpirationMinutes(60)
                .openingTime("10:00")
                .closingTime("20:00")
                .workingDays("SEG-SAB")
                .pixPaymentMessage("⚡ Reserva expressa Vício Tattoo!")
                .build();
        when(tenantConfigRepository.findAll()).thenReturn(java.util.List.of(config));

        MercadoPagoPaymentResponse mpResponse = MercadoPagoPaymentResponse.builder()
                .id(777666L)
                .status("pending")
                .transactionAmount(new BigDecimal("120.00"))
                .pointOfInteraction(MercadoPagoPaymentResponse.PointOfInteraction.builder()
                        .transactionData(MercadoPagoPaymentResponse.TransactionData.builder()
                                .qrCode("000201PIX60MIN")
                                .build())
                        .build())
                .build();
        when(paymentClient.createPixPayment(any(), any(), any(), any(), any())).thenReturn(mpResponse);

        EvolutionWebhookRequest webhook = new EvolutionWebhookRequest();
        webhook.setInstance("cefan-studio");

        // Solicita no domingo às 08h -> deve avançar para segunda-feira (SEG-SAB) às 10h (openingTime)
        handler.handle(customer, session, webhook, "Quero agendar no domingo às 8h");

        ArgumentCaptor<Appointment> appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(appointmentCaptor.capture());

        Appointment saved = appointmentCaptor.getValue();
        assertEquals(java.time.DayOfWeek.MONDAY, saved.getScheduledStart().getDayOfWeek());
        assertEquals(10, saved.getScheduledStart().getHour());

        // Verifica expiração parametrizada de 60 minutos e cabeçalho customizado
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511955555555"), messageCaptor.capture());
        String msg = messageCaptor.getValue();
        assertTrue(msg.contains("Reserva expressa Vício Tattoo!"));
        assertTrue(msg.contains("60 minutos"));
        assertTrue(msg.contains("000201PIX60MIN"));
    }

    @Test
    void shouldRespectFractionalOperatingHoursAndMinutesAfterClosingOrBeforeOpening() {
        br.com.cefan.whatsapp.domain.model.TenantConfig config = br.com.cefan.whatsapp.domain.model.TenantConfig.builder()
                .studioName("Vício Tattoo CWB")
                .openingTime("10:30")
                .closingTime("20:00")
                .workingDays("SEG-SAB")
                .build();

        // 1. Solicita sexta às 20:30 (após fechamento às 20:00) -> avança para sábado às 10:30
        OffsetDateTime afterClosing = handler.parseRequestedSlot("Quero agendar na sexta às 20:30", config);
        assertEquals(java.time.DayOfWeek.SATURDAY, afterClosing.getDayOfWeek());
        assertEquals(10, afterClosing.getHour());
        assertEquals(30, afterClosing.getMinute());

        // 2. Solicita sexta às 10:00 (antes da abertura às 10:30) -> ajusta para sexta às 10:30
        OffsetDateTime beforeOpening = handler.parseRequestedSlot("Quero agendar na sexta às 10:00", config);
        assertEquals(java.time.DayOfWeek.FRIDAY, beforeOpening.getDayOfWeek());
        assertEquals(10, beforeOpening.getHour());
        assertEquals(30, beforeOpening.getMinute());

        // 3. Solicita sexta às 22h -> avança para sábado às 10:30
        OffsetDateTime lateNight = handler.parseRequestedSlot("Quero agendar na sexta às 22h", config);
        assertEquals(java.time.DayOfWeek.SATURDAY, lateNight.getDayOfWeek());
        assertEquals(10, lateNight.getHour());
        assertEquals(30, lateNight.getMinute());

        // 4. Solicita sexta às 7h -> ajusta para sexta às 10:30
        OffsetDateTime earlyMorning = handler.parseRequestedSlot("Quero agendar na sexta às 7h", config);
        assertEquals(java.time.DayOfWeek.FRIDAY, earlyMorning.getDayOfWeek());
        assertEquals(10, earlyMorning.getHour());
        assertEquals(30, earlyMorning.getMinute());
    }
}
