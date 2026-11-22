package br.com.cefan.whatsapp.application.statemachine.handler;

import br.com.cefan.whatsapp.api.controller.StudioAdminController;
import br.com.cefan.whatsapp.application.service.ChatService;
import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import br.com.cefan.whatsapp.domain.model.ConversationSession;
import br.com.cefan.whatsapp.domain.model.ConversationState;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.model.TenantConfig;
import br.com.cefan.whatsapp.domain.model.User;
import br.com.cefan.whatsapp.domain.model.UserRole;
import br.com.cefan.whatsapp.domain.repository.AppointmentRepository;
import br.com.cefan.whatsapp.domain.repository.TenantConfigRepository;
import br.com.cefan.whatsapp.domain.repository.UserRepository;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import br.com.cefan.whatsapp.infrastructure.evolution.dto.EvolutionWebhookRequest;
import br.com.cefan.whatsapp.infrastructure.mercadopago.MercadoPagoPaymentClient;
import br.com.cefan.whatsapp.infrastructure.mercadopago.MercadoPagoPaymentException;
import br.com.cefan.whatsapp.infrastructure.mercadopago.dto.MercadoPagoPaymentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgendamentoStateHandlerTest {

    // Segunda-feira, 23/11/2026 às 12:30 (-03:00)
    private static final ZoneOffset BRT = ZoneOffset.ofHours(-3);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-11-23T15:30:00Z"), BRT);

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

    @Mock
    private StudioAdminController studioAdminController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AgendamentoStateHandler handler;
    private Customer customer;
    private ConversationSession session;
    private TenantConfig defaultConfig;
    private User mariana;
    private User carlos;

    @BeforeEach
    void setUp() {
        handler = new AgendamentoStateHandler(
                evolutionApiClient,
                appointmentRepository,
                paymentClient,
                objectMapper,
                chatService,
                tenantConfigRepository,
                userRepository,
                studioAdminController,
                FIXED_CLOCK
        );

        customer = Customer.builder()
                .id(UUID.randomUUID())
                .name("Fernanda")
                .phone("5511955555555")
                .build();

        defaultConfig = TenantConfig.builder()
                .studioName("Vício Tattoo CWB")
                .defaultHourlyRate(new BigDecimal("180.00"))
                .fixedSetupCost(new BigDecimal("60.00"))
                .signalPercentage(new BigDecimal("30.00"))
                .minDepositAmount(new BigDecimal("50.00"))
                .pixExpirationMinutes(120)
                .openingTime("10:00")
                .closingTime("20:00")
                .workingDays("SEG-SAB")
                .build();

        mariana = User.builder()
                .id(UUID.randomUUID())
                .name("Mariana Rocha")
                .role(UserRole.ARTIST)
                .specialties("Fineline, Traço Fino, Minimalista, Florais")
                .isActive(true)
                .build();

        carlos = User.builder()
                .id(UUID.randomUUID())
                .name("Carlos Silveira")
                .role(UserRole.ARTIST)
                .specialties("Realismo Preto e Cinza, Retratos")
                .isActive(true)
                .build();

        session = ConversationSession.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .currentState(ConversationState.AGENDAMENTO_SELECAO_DATA)
                .contextData("{\"basePrice\":\"400.00\"}")
                .lastInteractionAt(OffsetDateTime.now(FIXED_CLOCK))
                .build();
    }

    private EvolutionWebhookRequest createWebhook() {
        EvolutionWebhookRequest webhook = new EvolutionWebhookRequest();
        webhook.setInstance("cefan-studio");
        return webhook;
    }

    @Test
    void shouldCalculateDurationAsCeilOfEstimatedHoursWithMinimumTwoHours() {
        assertEquals(2, AgendamentoStateHandler.resolveDurationHours(null, objectMapper));
        assertEquals(2, AgendamentoStateHandler.resolveDurationHours("{}", objectMapper));
        assertEquals(2, AgendamentoStateHandler.resolveDurationHours("{\"estimatedHours\":1.0}", objectMapper));
        assertEquals(2, AgendamentoStateHandler.resolveDurationHours("{\"estimatedHours\":1.5}", objectMapper));
        assertEquals(2, AgendamentoStateHandler.resolveDurationHours("{\"estimatedHours\":2.0}", objectMapper));
        assertEquals(3, AgendamentoStateHandler.resolveDurationHours("{\"estimatedHours\":2.1}", objectMapper));
        assertEquals(3, AgendamentoStateHandler.resolveDurationHours("{\"estimatedHours\":2.5}", objectMapper));
        assertEquals(4, AgendamentoStateHandler.resolveDurationHours("{\"estimatedHours\":3.2}", objectMapper));
        assertEquals(4, AgendamentoStateHandler.resolveDurationHours("{\"estimatedHours\":4.0}", objectMapper));
    }

    @Test
    void shouldRouteToHumanHandoffWhenSessionDurationExceedsOperatingHours() {
        assertTrue(AgendamentoStateHandler.doesSessionFitInOperatingHours(10, defaultConfig));
        assertFalse(AgendamentoStateHandler.doesSessionFitInOperatingHours(11, defaultConfig));

        when(tenantConfigRepository.findAll()).thenReturn(List.of(defaultConfig));
        when(userRepository.findAll()).thenReturn(List.of(carlos, mariana));
        session.setContextData("{\"basePrice\":\"1800.00\",\"estimatedHours\":10.5,\"recommendedArtists\":[\"Carlos Silveira\"]}");

        handler.handle(customer, session, createWebhook(), "sim");

        assertEquals(ConversationState.TRANSBORDO_HUMANO, session.getCurrentState());
        assertEquals("Carlos Silveira", session.getAssignedArtistName());
        verify(appointmentRepository, never()).save(any());
        verifyNoInteractions(paymentClient);
        verify(studioAdminController).publishNotification(eq("HUMAN_REQUEST"), contains("11h"), anyString(), eq(customer.getPhone()));
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq(customer.getPhone()), contains("ultrapassa nosso expediente"));
    }

    @Test
    void shouldCompleteGuidedFlowConfirmingRecommendedArtistAndSendingUnifiedSummaryPlusSeparatePixCode() {
        when(tenantConfigRepository.findAll()).thenReturn(List.of(defaultConfig));
        when(userRepository.findAll()).thenReturn(List.of(mariana, carlos));
        when(appointmentRepository.findByArtistIdAndScheduledStartBetween(eq(mariana.getId()), any(), any()))
                .thenReturn(List.of());

        session.setContextData("{\"basePrice\":\"400.00\",\"estimatedHours\":2.5,\"recommendedArtists\":[\"Mariana Rocha\"]}");

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

        EvolutionWebhookRequest webhook = createWebhook();

        // Etapa 1: Confirma artista sugerido no orçamento
        handler.handle(customer, session, webhook, "sim");
        assertEquals(mariana.getId(), session.getAssignedUser().getId());

        // Etapa 2: Informa data futura válida (quarta 25/11/2026) -> deve listar slots de 3h (10:00 até 17:00)
        handler.handle(customer, session, webhook, "25/11/2026");

        // Etapa 3: Escolhe horário disponível (14h -> bloco 14:00 às 17:00)
        handler.handle(customer, session, webhook, "14h");

        // Valida ordem segura: lock no artista -> salva reserva ANTES de criar PIX -> vincula dados do PIX
        InOrder inOrder = inOrder(userRepository, appointmentRepository, paymentClient);
        inOrder.verify(userRepository).findByIdForUpdate(mariana.getId());
        inOrder.verify(appointmentRepository).saveAndFlush(any(Appointment.class));
        ArgumentCaptor<OffsetDateTime> pixExpiryCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        inOrder.verify(paymentClient).createPixPayment(any(), any(), any(), any(), pixExpiryCaptor.capture());
        inOrder.verify(appointmentRepository).saveAndFlush(any(Appointment.class));

        ArgumentCaptor<Appointment> appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository, times(2)).saveAndFlush(appointmentCaptor.capture());

        Appointment saved = appointmentCaptor.getAllValues().get(1);
        assertEquals(AppointmentStatus.AGUARDANDO_PAGAMENTO, saved.getStatus());
        assertEquals(mariana.getId(), saved.getArtist().getId());
        assertEquals(LocalDate.of(2026, 11, 25), saved.getScheduledStart().toLocalDate());
        assertEquals(14, saved.getScheduledStart().getHour());
        assertEquals(17, saved.getScheduledEnd().getHour()); // ceil(2.5) = 3h
        assertEquals("123456789", saved.getMpPaymentId());
        assertEquals(new BigDecimal("400.00"), saved.getTotalPrice());
        assertEquals(new BigDecimal("120.00"), saved.getDepositAmount());
        assertNotNull(saved.getExpiresAt());
        assertEquals(saved.getExpiresAt(), pixExpiryCaptor.getValue(),
                "date_of_expiration do PIX deve ser exatamente igual ao expires_at da reserva");

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        // 1 (confirma artista) + 1 (lista horários) + 1 (resumo unificado) + 1 (código PIX separado) = 4 mensagens
        verify(evolutionApiClient, times(4)).sendTextMessage(eq("cefan-studio"), eq("5511955555555"), messageCaptor.capture());
        List<String> sent = messageCaptor.getAllValues();

        String slotsMsg = sent.get(1);
        assertTrue(slotsMsg.contains("*1)* *10:00* (10:00 às 13:00)"));
        assertTrue(slotsMsg.contains("*8)* *17:00* (17:00 às 20:00)"));
        assertFalse(slotsMsg.contains("*18:00*"), "18:00 + 3h = 21:00 ultrapassa fechamento às 20:00");

        String summaryMsg = sent.get(2);
        String pixOnlyMsg = sent.get(3);

        assertTrue(summaryMsg.contains("Pré-Agendamento Criado"));
        assertEquals(1, summaryMsg.split("Pré-Agendamento Criado", -1).length - 1, "Introdução não pode ser duplicada");
        assertTrue(summaryMsg.contains("14:00 às 17:00"));
        assertTrue(summaryMsg.contains("R$ 120,00"));
        assertTrue(summaryMsg.contains("Saldo Restante Estimado:") && summaryMsg.contains("R$ 280,00"));
        assertFalse(summaryMsg.contains("00020126580014br.gov.bcb.pix0136test-pix-code"), "Código PIX deve vir apenas na mensagem separada");

        assertEquals("00020126580014br.gov.bcb.pix0136test-pix-code", pixOnlyMsg);
    }

    @Test
    void shouldNotDuplicateIntroWhenCustomPixPaymentMessageIsConfigured() {
        defaultConfig.setPixExpirationMinutes(60);
        defaultConfig.setPixPaymentMessage("⚡ Reserva expressa Vício Tattoo!");
        when(tenantConfigRepository.findAll()).thenReturn(List.of(defaultConfig));
        when(userRepository.findAll()).thenReturn(List.of(mariana));
        when(appointmentRepository.findByArtistIdAndScheduledStartBetween(eq(mariana.getId()), any(), any()))
                .thenReturn(List.of());

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

        EvolutionWebhookRequest webhook = createWebhook();
        handler.handle(customer, session, webhook, "Mariana");
        handler.handle(customer, session, webhook, "amanhã");
        handler.handle(customer, session, webhook, "10:00");

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(evolutionApiClient, times(4)).sendTextMessage(eq("cefan-studio"), eq("5511955555555"), messageCaptor.capture());

        String summaryMsg = messageCaptor.getAllValues().get(2);
        String pixMsg = messageCaptor.getAllValues().get(3);

        assertTrue(summaryMsg.startsWith("⚡ Reserva expressa Vício Tattoo!"));
        assertFalse(summaryMsg.contains("Pré-Agendamento Criado"), "Não deve duplicar cabeçalho quando pixPaymentMessage customizado está preenchido");
        assertTrue(summaryMsg.contains("60 minutos"));
        assertFalse(summaryMsg.contains("000201PIX60MIN"));
        assertEquals("000201PIX60MIN", pixMsg);
    }

    @Test
    void shouldSearchByStyleWithoutListingAllElevenArtistsAndDeferAnyAvailable() {
        List<User> elevenArtists = List.of(
                carlos,
                mariana,
                User.builder().id(UUID.randomUUID()).name("Lucas 'Blade'").role(UserRole.ARTIST).specialties("Blackwork, Tribal, Geométrico").isActive(true).build(),
                User.builder().id(UUID.randomUUID()).name("Beatriz Lima").role(UserRole.ARTIST).specialties("Aquarela, Floral, Botânico").isActive(true).build(),
                User.builder().id(UUID.randomUUID()).name("Rodrigo 'Old' Santos").role(UserRole.ARTIST).specialties("Old School, Tradicional Americano").isActive(true).build(),
                User.builder().id(UUID.randomUUID()).name("Felipe Alcantara").role(UserRole.ARTIST).specialties("Geométrico, Pontilhismo, Mandala").isActive(true).build(),
                User.builder().id(UUID.randomUUID()).name("Camila Fernandes").role(UserRole.ARTIST).specialties("Microrealismo, Caligrafia, Delicadas").isActive(true).build(),
                User.builder().id(UUID.randomUUID()).name("Gabriel Moura").role(UserRole.ARTIST).specialties("Neo Tradicional, Oriental, Anime").isActive(true).build(),
                User.builder().id(UUID.randomUUID()).name("Larissa Piercing").role(UserRole.ARTIST).specialties("Body Piercing, Aplicações & Joias").isActive(true).build(),
                User.builder().id(UUID.randomUUID()).name("Thiago 'Dark' Souza").role(UserRole.ARTIST).specialties("Dark Art, Lettering, Chicano").isActive(true).build(),
                User.builder().id(UUID.randomUUID()).name("Julia Piercer").role(UserRole.ARTIST).specialties("Body Piercing, Microdermal, Mini Tattoos").isActive(true).build()
        );
        when(tenantConfigRepository.findAll()).thenReturn(List.of(defaultConfig));
        when(userRepository.findAll()).thenReturn(elevenArtists);

        // Prompt de entrada sem orçamento não deve listar os 11 artistas
        String entryPrompt = AgendamentoStateHandler.buildSchedulingEntryPrompt(
                customer, session, defaultConfig, elevenArtists, objectMapper, false);
        assertFalse(entryPrompt.contains("Rodrigo"));
        assertFalse(entryPrompt.contains("Thiago"));

        EvolutionWebhookRequest webhook = createWebhook();

        // "qualquer disponível" fica para depois -> não seleciona sozinho
        handler.handle(customer, session, webhook, "qualquer disponível");
        assertNull(session.getAssignedUser());

        // Busca por estilo "piercing" -> retorna apenas as 2 especialistas de piercing, nunca os 11
        handler.handle(customer, session, webhook, "piercing");

        // Seleciona opção "1" -> Larissa Piercing
        handler.handle(customer, session, webhook, "1");
        assertNotNull(session.getAssignedUser());
        assertEquals("Larissa Piercing", session.getAssignedUser().getName());

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(evolutionApiClient, times(3)).sendTextMessage(eq("cefan-studio"), eq(customer.getPhone()), messageCaptor.capture());

        assertTrue(messageCaptor.getAllValues().get(0).contains("ainda não está ativa"));
        String styleReply = messageCaptor.getAllValues().get(1);
        assertTrue(styleReply.contains("Larissa Piercing"));
        assertTrue(styleReply.contains("Julia Piercer"));
        assertFalse(styleReply.contains("Carlos Silveira"));
        assertFalse(styleReply.contains("Rodrigo"));
        assertTrue(messageCaptor.getAllValues().get(2).contains("Etapa 2 de 3 — Data"));
    }

    @Test
    void shouldRejectInvalidPastAndNonWorkingDayDatesWithoutDefaulting() {
        when(tenantConfigRepository.findAll()).thenReturn(List.of(defaultConfig));
        when(userRepository.findAll()).thenReturn(List.of(mariana));

        EvolutionWebhookRequest webhook = createWebhook();
        // Etapa 1: seleciona Mariana
        handler.handle(customer, session, webhook, "Mariana");

        // Etapa 2: entradas inválidas, passadas e dia fora do expediente (domingo em SEG-SAB)
        handler.handle(customer, session, webhook, "31/02/2026");
        handler.handle(customer, session, webhook, "20/11/2026");
        handler.handle(customer, session, webhook, "20/11");
        handler.handle(customer, session, webhook, "29/11/2026"); // Domingo
        handler.handle(customer, session, webhook, "banana");

        verify(appointmentRepository, never()).save(any());
        verifyNoInteractions(paymentClient);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(evolutionApiClient, times(6)).sendTextMessage(eq("cefan-studio"), eq(customer.getPhone()), messageCaptor.capture());
        List<String> msgs = messageCaptor.getAllValues();

        assertTrue(msgs.get(1).contains("não existe no calendário"));
        assertTrue(msgs.get(2).contains("já passou"));
        assertTrue(msgs.get(3).contains("já passou"));
        assertTrue(msgs.get(4).contains("não tem expediente em *Domingo*"));
        assertTrue(msgs.get(5).contains("Não consegui entender a data"));
        assertTrue(msgs.get(5).contains("Etapa 2 de 3 — Data"));
    }

    @Test
    void shouldExcludePastTodaySlotsOverlappingBookingsAndBlocksExceedingClosingWhileIgnoringCancelledAndExpired() {
        OffsetDateTime refNow = OffsetDateTime.now(FIXED_CLOCK); // 2026-11-23T12:30-03:00
        LocalDate today = refNow.toLocalDate();

        Appointment confirmed = Appointment.builder()
                .id(UUID.randomUUID())
                .artist(mariana)
                .status(AppointmentStatus.CONFIRMADO)
                .scheduledStart(today.atTime(14, 0).atOffset(BRT))
                .scheduledEnd(today.atTime(16, 0).atOffset(BRT))
                .build();

        Appointment cancelled = Appointment.builder()
                .id(UUID.randomUUID())
                .artist(mariana)
                .status(AppointmentStatus.CANCELADO)
                .scheduledStart(today.atTime(16, 0).atOffset(BRT))
                .scheduledEnd(today.atTime(18, 0).atOffset(BRT))
                .build();

        Appointment expiredStatus = Appointment.builder()
                .id(UUID.randomUUID())
                .artist(mariana)
                .status(AppointmentStatus.EXPIRADO)
                .scheduledStart(today.atTime(17, 0).atOffset(BRT))
                .scheduledEnd(today.atTime(19, 0).atOffset(BRT))
                .build();

        Appointment expiredPendingPix = Appointment.builder()
                .id(UUID.randomUUID())
                .artist(mariana)
                .status(AppointmentStatus.AGUARDANDO_PAGAMENTO)
                .scheduledStart(today.atTime(16, 0).atOffset(BRT))
                .scheduledEnd(today.atTime(18, 0).atOffset(BRT))
                .expiresAt(refNow.minusMinutes(5))
                .build();

        when(appointmentRepository.findByArtistIdAndScheduledStartBetween(eq(mariana.getId()), any(), any()))
                .thenReturn(List.of(confirmed, cancelled, expiredStatus, expiredPendingPix));

        // Duração 3h, expediente 10:00-20:00, agora é 12:30:
        // 10h, 11h, 12h já passaram;
        // 13h (13-16) sobrepõe 14-16; 14h (14-17) e 15h (15-18) sobrepõem 14-16;
        // 16h (16-19) e 17h (17-20) cabem inteiros e não sobrepõem (CANCELADO/EXPIRADO ignorados);
        // 18h (18-21) ultrapassa 20:00.
        List<LocalTime> slots = handler.findAvailableSlots(mariana, today, 3, defaultConfig, refNow);

        assertEquals(List.of(LocalTime.of(16, 0), LocalTime.of(17, 0)), slots);
    }

    @Test
    void shouldRejectInvalidOrUnavailableTimeAndResendOptions() {
        when(tenantConfigRepository.findAll()).thenReturn(List.of(defaultConfig));
        when(userRepository.findAll()).thenReturn(List.of(mariana));

        LocalDate wed = LocalDate.of(2026, 11, 25);
        Appointment middayBlock = Appointment.builder()
                .id(UUID.randomUUID())
                .artist(mariana)
                .status(AppointmentStatus.CONFIRMADO)
                .scheduledStart(wed.atTime(12, 0).atOffset(BRT))
                .scheduledEnd(wed.atTime(15, 0).atOffset(BRT))
                .build();
        when(appointmentRepository.findByArtistIdAndScheduledStartBetween(eq(mariana.getId()), any(), any()))
                .thenReturn(List.of(middayBlock));

        EvolutionWebhookRequest webhook = createWebhook();
        handler.handle(customer, session, webhook, "Mariana");
        handler.handle(customer, session, webhook, "25/11/2026");

        // 12:00 está ocupado; 15:30 não é hora cheia oferecida; "qualquer hora" é inválido
        handler.handle(customer, session, webhook, "12:00");
        handler.handle(customer, session, webhook, "15:30");
        handler.handle(customer, session, webhook, "qualquer hora");

        verify(appointmentRepository, never()).save(any());
        verifyNoInteractions(paymentClient);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(evolutionApiClient, times(5)).sendTextMessage(eq("cefan-studio"), eq(customer.getPhone()), messageCaptor.capture());

        assertTrue(messageCaptor.getAllValues().get(2).contains("não está entre as opções disponíveis"));
        assertTrue(messageCaptor.getAllValues().get(3).contains("não está entre as opções disponíveis"));
        assertTrue(messageCaptor.getAllValues().get(4).contains("Não consegui identificar o horário"));
    }

    @Test
    void shouldRecheckAvailabilityBeforeCreatingPixAndRejectStolenSlot() {
        when(tenantConfigRepository.findAll()).thenReturn(List.of(defaultConfig));
        when(userRepository.findAll()).thenReturn(List.of(mariana));

        LocalDate wed = LocalDate.of(2026, 11, 25);
        Appointment stolenSlot = Appointment.builder()
                .id(UUID.randomUUID())
                .artist(mariana)
                .status(AppointmentStatus.AGUARDANDO_PAGAMENTO)
                .scheduledStart(wed.atTime(14, 0).atOffset(BRT))
                .scheduledEnd(wed.atTime(16, 0).atOffset(BRT))
                .expiresAt(OffsetDateTime.now(FIXED_CLOCK).plusHours(2))
                .build();

        // 1ª chamada (Etapa DATA): livre; 2ª (expireOverdue) + 3ª (rechecagem sob lock) + 4ª (novos slots): 14h ocupado
        when(appointmentRepository.findByArtistIdAndScheduledStartBetween(eq(mariana.getId()), any(), any()))
                .thenReturn(List.of())
                .thenReturn(List.of(stolenSlot))
                .thenReturn(List.of(stolenSlot))
                .thenReturn(List.of(stolenSlot));

        EvolutionWebhookRequest webhook = createWebhook();
        handler.handle(customer, session, webhook, "Mariana");
        handler.handle(customer, session, webhook, "25/11/2026");
        handler.handle(customer, session, webhook, "14:00");

        verify(appointmentRepository, never()).saveAndFlush(any());
        verify(appointmentRepository, never()).save(any());
        verifyNoInteractions(paymentClient);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(evolutionApiClient, times(3)).sendTextMessage(eq("cefan-studio"), eq(customer.getPhone()), messageCaptor.capture());
        String conflictMsg = messageCaptor.getAllValues().get(2);
        assertTrue(conflictMsg.contains("acabou de ser reservado"));
        assertTrue(conflictMsg.contains("*16:00*"));
        assertFalse(conflictMsg.contains("*14:00* (14:00 às 16:00)"));
    }

    @Test
    void shouldNeverCreatePixWhenDatabaseConstraintRejectsOverlappingReservation() {
        when(tenantConfigRepository.findAll()).thenReturn(List.of(defaultConfig));
        when(userRepository.findAll()).thenReturn(List.of(mariana));

        LocalDate wed = LocalDate.of(2026, 11, 25);
        Appointment concurrentBooking = Appointment.builder()
                .id(UUID.randomUUID())
                .artist(mariana)
                .status(AppointmentStatus.CONFIRMADO)
                .scheduledStart(wed.atTime(14, 0).atOffset(BRT))
                .scheduledEnd(wed.atTime(16, 0).atOffset(BRT))
                .build();

        // 1ª (DATA), 2ª (expireOverdue), 3ª (rechecagem): livre; saveAndFlush dispara constraint EXCLUDE; 4ª (novos slots): ocupado
        when(appointmentRepository.findByArtistIdAndScheduledStartBetween(eq(mariana.getId()), any(), any()))
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of(concurrentBooking));
        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenThrow(new DataIntegrityViolationException("excl_appointments_no_overlap_per_artist"));

        EvolutionWebhookRequest webhook = createWebhook();
        handler.handle(customer, session, webhook, "Mariana");
        handler.handle(customer, session, webhook, "25/11/2026");
        handler.handle(customer, session, webhook, "14:00");

        // Como a reserva é gravada ANTES do PIX, falha na constraint nunca gera cobrança órfã no Mercado Pago
        verifyNoInteractions(paymentClient);
        verify(evolutionApiClient, times(3)).sendTextMessage(eq("cefan-studio"), eq(customer.getPhone()), anyString());
    }

    @Test
    void shouldCancelTemporaryReservationAndHandoffToHumanWhenPixCreationFails() {
        when(tenantConfigRepository.findAll()).thenReturn(List.of(defaultConfig));
        when(userRepository.findAll()).thenReturn(List.of(mariana));
        when(appointmentRepository.findByArtistIdAndScheduledStartBetween(eq(mariana.getId()), any(), any()))
                .thenReturn(List.of());
        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(paymentClient.createPixPayment(any(), any(), any(), any(), any()))
                .thenThrow(new MercadoPagoPaymentException("API Mercado Pago indisponível"));

        EvolutionWebhookRequest webhook = createWebhook();
        handler.handle(customer, session, webhook, "Mariana");
        handler.handle(customer, session, webhook, "25/11/2026");
        handler.handle(customer, session, webhook, "14:00");

        assertEquals(ConversationState.TRANSBORDO_HUMANO, session.getCurrentState());
        assertEquals("Mariana Rocha", session.getAssignedArtistName());

        ArgumentCaptor<Appointment> savedCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository, times(2)).saveAndFlush(savedCaptor.capture());
        // 1º saveAndFlush cria temporariamente; 2º cancela imediatamente liberando a vaga
        assertEquals(AppointmentStatus.CANCELADO, savedCaptor.getAllValues().get(1).getStatus());
        verify(studioAdminController).publishNotification(eq("HUMAN_REQUEST"), contains("Falha ao gerar PIX"), anyString(), eq(customer.getPhone()));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(evolutionApiClient, times(3)).sendTextMessage(eq("cefan-studio"), eq(customer.getPhone()), messageCaptor.capture());
        String errorReply = messageCaptor.getAllValues().get(2);
        assertTrue(errorReply.contains("instabilidade temporária"));
        assertTrue(errorReply.contains("nenhum pré-agendamento ou cobrança foi criado"));
    }

    @Test
    void shouldCancelTemporaryReservationAndHandoffToHumanWhenPixResponseHasNoQrCode() {
        when(tenantConfigRepository.findAll()).thenReturn(List.of(defaultConfig));
        when(userRepository.findAll()).thenReturn(List.of(mariana));
        when(appointmentRepository.findByArtistIdAndScheduledStartBetween(eq(mariana.getId()), any(), any()))
                .thenReturn(List.of());
        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(paymentClient.createPixPayment(any(), any(), any(), any(), any()))
                .thenReturn(MercadoPagoPaymentResponse.builder().id(999L).status("pending").build());

        EvolutionWebhookRequest webhook = createWebhook();
        handler.handle(customer, session, webhook, "Mariana");
        handler.handle(customer, session, webhook, "25/11/2026");
        handler.handle(customer, session, webhook, "14:00");

        assertEquals(ConversationState.TRANSBORDO_HUMANO, session.getCurrentState());
        ArgumentCaptor<Appointment> savedCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository, times(2)).saveAndFlush(savedCaptor.capture());
        assertEquals(AppointmentStatus.CANCELADO, savedCaptor.getAllValues().get(1).getStatus());
    }

    @Test
    void shouldCancelReservationAndHandoffToHumanWhenAttachPixDetailsFails() {
        br.com.cefan.whatsapp.application.service.AppointmentReservationService mockReservationService =
                mock(br.com.cefan.whatsapp.application.service.AppointmentReservationService.class);

        AgendamentoStateHandler handlerWithMockReservation = new AgendamentoStateHandler(
                evolutionApiClient, appointmentRepository, paymentClient, objectMapper, chatService,
                tenantConfigRepository, userRepository, studioAdminController, mockReservationService, FIXED_CLOCK);

        when(tenantConfigRepository.findAll()).thenReturn(List.of(defaultConfig));
        when(userRepository.findAll()).thenReturn(List.of(mariana));
        when(appointmentRepository.findByArtistIdAndScheduledStartBetween(eq(mariana.getId()), any(), any()))
                .thenReturn(List.of());

        Appointment reserved = Appointment.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .artist(mariana)
                .status(AppointmentStatus.AGUARDANDO_PAGAMENTO)
                .scheduledStart(LocalDate.of(2026, 11, 25).atTime(14, 0).atOffset(BRT))
                .scheduledEnd(LocalDate.of(2026, 11, 25).atTime(16, 0).atOffset(BRT))
                .expiresAt(OffsetDateTime.now(FIXED_CLOCK).plusHours(2))
                .build();
        when(mockReservationService.reserveSlotAtomically(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(java.util.Optional.of(reserved));

        MercadoPagoPaymentResponse mpResponse = MercadoPagoPaymentResponse.builder()
                .id(777888L)
                .status("pending")
                .pointOfInteraction(MercadoPagoPaymentResponse.PointOfInteraction.builder()
                        .transactionData(MercadoPagoPaymentResponse.TransactionData.builder()
                                .qrCode("000201ATTACHFAIL")
                                .build())
                        .build())
                .build();
        when(paymentClient.createPixPayment(any(), any(), any(), any(), any())).thenReturn(mpResponse);
        doThrow(new RuntimeException("Erro de banco ao anexar PIX"))
                .when(mockReservationService).attachPixDetails(eq(reserved), anyString(), anyString(), any(), any());

        EvolutionWebhookRequest webhook = createWebhook();
        handlerWithMockReservation.handle(customer, session, webhook, "Mariana");
        handlerWithMockReservation.handle(customer, session, webhook, "25/11/2026");
        handlerWithMockReservation.handle(customer, session, webhook, "14h");

        verify(mockReservationService).cancelTemporaryReservation(reserved);
        assertEquals(ConversationState.TRANSBORDO_HUMANO, session.getCurrentState());
        verify(studioAdminController).publishNotification(eq("HUMAN_REQUEST"), contains("Falha ao gerar PIX"), anyString(), eq(customer.getPhone()));
        verify(evolutionApiClient, never()).sendTextMessage(anyString(), anyString(), eq("000201ATTACHFAIL"));
    }

    @Test
    void shouldTransitionToHumanHandoffWhenUserSends3DuringScheduling() {
        handler.handle(customer, session, createWebhook(), "3");

        assertEquals(ConversationState.TRANSBORDO_HUMANO, session.getCurrentState());
        verify(appointmentRepository, never()).save(any());
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511955555555"), contains("tatuadores"));
    }

    @Test
    void shouldAllowNavigatingBackBetweenStepsAndRejectInvalidProfessionalInput() {
        when(tenantConfigRepository.findAll()).thenReturn(List.of(defaultConfig));
        when(userRepository.findAll()).thenReturn(List.of(mariana, carlos));
        when(appointmentRepository.findByArtistIdAndScheduledStartBetween(eq(mariana.getId()), any(), any()))
                .thenReturn(List.of());

        EvolutionWebhookRequest webhook = createWebhook();

        // Entrada inválida na Etapa 1 -> não seleciona ninguém
        handler.handle(customer, session, webhook, "xyzabc123");
        assertNull(session.getAssignedUser());

        // Seleciona Mariana -> Etapa 2 -> escolhe data -> Etapa 3
        handler.handle(customer, session, webhook, "Mariana");
        handler.handle(customer, session, webhook, "25/11/2026");

        // Volta para escolher outra data
        handler.handle(customer, session, webhook, "outra data");
        // Volta para trocar profissional
        handler.handle(customer, session, webhook, "trocar profissional");

        verify(appointmentRepository, never()).save(any());
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(evolutionApiClient, times(5)).sendTextMessage(eq("cefan-studio"), eq(customer.getPhone()), messageCaptor.capture());

        assertTrue(messageCaptor.getAllValues().get(0).contains("Não consegui identificar esse profissional"));
        assertTrue(messageCaptor.getAllValues().get(3).contains("Etapa 2 de 3 — Data"));
        assertTrue(messageCaptor.getAllValues().get(4).contains("Etapa 1 de 3 — Profissional"));
    }

    @Test
    void shouldSupportCustomWorkingDaysFormatsIncludingSegSexAndTerSab() {
        TenantConfig segSex = TenantConfig.builder().workingDays("SEG-SEX").build();
        assertTrue(AgendamentoStateHandler.artistWorksOnDay(mariana, java.time.DayOfWeek.FRIDAY, segSex));
        assertFalse(AgendamentoStateHandler.artistWorksOnDay(mariana, java.time.DayOfWeek.SATURDAY, segSex));
        assertFalse(AgendamentoStateHandler.artistWorksOnDay(mariana, java.time.DayOfWeek.SUNDAY, segSex));

        TenantConfig terSab = TenantConfig.builder().workingDays("TER-SAB").build();
        assertFalse(AgendamentoStateHandler.artistWorksOnDay(mariana, java.time.DayOfWeek.MONDAY, terSab));
        assertTrue(AgendamentoStateHandler.artistWorksOnDay(mariana, java.time.DayOfWeek.TUESDAY, terSab));
        assertTrue(AgendamentoStateHandler.artistWorksOnDay(mariana, java.time.DayOfWeek.SATURDAY, terSab));

        User inactiveArtist = User.builder().id(UUID.randomUUID()).name("Inativo").role(UserRole.ARTIST).isActive(false).build();
        assertFalse(AgendamentoStateHandler.artistWorksOnDay(inactiveArtist, java.time.DayOfWeek.WEDNESDAY, defaultConfig));
    }

    @Test
    void shouldAcceptPunctuatedConfirmationsAndQuoteArtistPhraseWithoutRestartingQuote() {
        when(tenantConfigRepository.findAll()).thenReturn(List.of(defaultConfig));
        when(userRepository.findAll()).thenReturn(List.of(mariana, carlos));

        EvolutionWebhookRequest webhook = createWebhook();

        // "Sim!" com pontuação confirma artista sugerido
        session.setContextData("{\"basePrice\":\"400.00\",\"estimatedHours\":2.0,\"recommendedArtists\":[\"Mariana Rocha\"]}");
        handler.handle(customer, session, webhook, "Sim!");
        assertEquals(mariana.getId(), session.getAssignedUser().getId());

        // "Sim, a mesma do orçamento" confirma artista e NÃO reinicia orçamento
        session.setAssignedUser(null);
        session.setCurrentState(ConversationState.AGENDAMENTO_SELECAO_DATA);
        session.setContextData("{\"basePrice\":\"400.00\",\"estimatedHours\":2.0,\"recommendedArtists\":[\"Mariana Rocha\"]}");
        handler.handle(customer, session, webhook, "Sim, a mesma do orçamento");
        assertEquals(ConversationState.AGENDAMENTO_SELECAO_DATA, session.getCurrentState());
        assertEquals(mariana.getId(), session.getAssignedUser().getId());

        // Pedido explícito de novo orçamento continua redirecionando para ORCAMENTO_AGUARDANDO_FOTO
        handler.handle(customer, session, webhook, "quero fazer novo orçamento");
        assertEquals(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, session.getCurrentState());
    }

    @Test
    void shouldIgnoreLegacyVerboseV4PixPaymentMessageAndReturnSessionToMenuAfterBooking() {
        defaultConfig.setPixExpirationMinutes(90);
        defaultConfig.setPixPaymentMessage(
                "🗓️ *Pré-Agendamento Criado!*\n\nBloqueamos seu horário na grade. Para confirmar, efetue o pagamento do sinal de 30% via PIX copia e cola abaixo. O horário fica reservado por 2 horas.");
        when(tenantConfigRepository.findAll()).thenReturn(List.of(defaultConfig));
        when(userRepository.findAll()).thenReturn(List.of(mariana));
        when(appointmentRepository.findByArtistIdAndScheduledStartBetween(eq(mariana.getId()), any(), any()))
                .thenReturn(List.of());

        MercadoPagoPaymentResponse mpResponse = MercadoPagoPaymentResponse.builder()
                .id(888999L)
                .status("pending")
                .transactionAmount(new BigDecimal("120.00"))
                .pointOfInteraction(MercadoPagoPaymentResponse.PointOfInteraction.builder()
                        .transactionData(MercadoPagoPaymentResponse.TransactionData.builder()
                                .qrCode("000201LEGACYV4DEDUP")
                                .build())
                        .build())
                .build();
        when(paymentClient.createPixPayment(any(), any(), any(), any(), any())).thenReturn(mpResponse);

        EvolutionWebhookRequest webhook = createWebhook();
        handler.handle(customer, session, webhook, "Mariana");
        handler.handle(customer, session, webhook, "25/11/2026");
        handler.handle(customer, session, webhook, "14:00");

        assertEquals(ConversationState.MENU, session.getCurrentState(),
                "Após criar o pré-agendamento, sessão deve voltar para MENU para evitar reabrir agendamento ao receber 'ok'/'obrigado'");

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(evolutionApiClient, times(4)).sendTextMessage(eq("cefan-studio"), eq(customer.getPhone()), messageCaptor.capture());

        String summaryMsg = messageCaptor.getAllValues().get(2);
        String pixMsg = messageCaptor.getAllValues().get(3);

        assertFalse(summaryMsg.contains("Bloqueamos seu horário na grade"), "Não deve duplicar texto legado da V4");
        assertFalse(summaryMsg.contains("2 horas"), "Não deve contradizer a validade configurada de 90 minutos");
        assertTrue(summaryMsg.contains("90 minutos"));
        assertFalse(summaryMsg.contains("000201LEGACYV4DEDUP"));
        assertEquals("000201LEGACYV4DEDUP", pixMsg);
    }

    @Test
    void shouldParsePortugueseMonthNamesLeapYearsAndRejectMalformedDates() {
        LocalDate nov23 = LocalDate.of(2026, 11, 23);
        OffsetDateTime nov23Noon = nov23.atTime(12, 30).atOffset(BRT);

        // "dia 25 de dezembro" NÃO pode cair em 25/11!
        var dec25 = handler.parseDateInput("dia 25 de dezembro", nov23, nov23Noon, defaultConfig);
        assertEquals(AgendamentoStateHandler.DateParseStatus.VALID, dec25.status());
        assertEquals(LocalDate.of(2026, 12, 25), dec25.date());

        var dec5 = handler.parseDateInput("dia 5 de dezembro", nov23, nov23Noon, defaultConfig);
        assertEquals(AgendamentoStateHandler.DateParseStatus.VALID, dec5.status());
        assertEquals(LocalDate.of(2026, 12, 5), dec5.date());

        var nov25 = handler.parseDateInput("25 de novembro", nov23, nov23Noon, defaultConfig);
        assertEquals(AgendamentoStateHandler.DateParseStatus.VALID, nov25.status());
        assertEquals(LocalDate.of(2026, 11, 25), nov25.date());

        // "ontem" -> PAST_DATE
        var yesterday = handler.parseDateInput("ontem", nov23, nov23Noon, defaultConfig);
        assertEquals(AgendamentoStateHandler.DateParseStatus.PAST_DATE, yesterday.status());
        assertEquals(LocalDate.of(2026, 11, 22), yesterday.date());

        // ISO yyyy-MM-dd
        var iso = handler.parseDateInput("2026-11-25", nov23, nov23Noon, defaultConfig);
        assertEquals(AgendamentoStateHandler.DateParseStatus.VALID, iso.status());
        assertEquals(LocalDate.of(2026, 11, 25), iso.date());

        // Ano malformado e decimal NÃO podem casar como data curta
        assertEquals(AgendamentoStateHandler.DateParseStatus.UNRECOGNIZED,
                handler.parseDateInput("25/11/20266", nov23, nov23Noon, defaultConfig).status());
        assertEquals(AgendamentoStateHandler.DateParseStatus.UNRECOGNIZED,
                handler.parseDateInput("2.5 horas", nov23, nov23Noon, defaultConfig).status());

        // Bissexto: 29/02 em 01/03/2024 (passado em ano bissexto) -> PAST_DATE, não INVALID_CALENDAR_DATE
        LocalDate mar1Leap = LocalDate.of(2024, 3, 1);
        var pastLeap = handler.parseDateInput("29/02", mar1Leap, mar1Leap.atTime(12, 0).atOffset(BRT), defaultConfig);
        assertEquals(AgendamentoStateHandler.DateParseStatus.PAST_DATE, pastLeap.status());
        assertEquals(LocalDate.of(2024, 2, 29), pastLeap.date());

        // Bissexto virada de ano: 29/02 em 15/12/2027 -> VALID (29/02/2028)
        LocalDate dec15PreLeap = LocalDate.of(2027, 12, 15);
        var nextYearLeap = handler.parseDateInput("29/02", dec15PreLeap, dec15PreLeap.atTime(12, 0).atOffset(BRT), defaultConfig);
        assertEquals(AgendamentoStateHandler.DateParseStatus.VALID, nextYearLeap.status());
        assertEquals(LocalDate.of(2028, 2, 29), nextYearLeap.date());

        // 29/02 em 2026 (nem 2026 nem 2027 é bissexto) -> INVALID_CALENDAR_DATE
        assertEquals(AgendamentoStateHandler.DateParseStatus.INVALID_CALENDAR_DATE,
                handler.parseDateInput("29/02", nov23, nov23Noon, defaultConfig).status());
    }

    @Test
    void shouldDisambiguateListOptionNumbersFromExplicitTimesAndNeverSilentlyBookEndTime() {
        List<LocalTime> offered = List.of(LocalTime.of(10, 0), LocalTime.of(12, 0), LocalTime.of(14, 0), LocalTime.of(16, 0));

        // Regra de prioridade: número simples = opção da lista (1..N)
        assertEquals(java.util.Optional.of(LocalTime.of(10, 0)), handler.parseTimeSelection("1", offered));
        assertEquals(java.util.Optional.of(LocalTime.of(12, 0)), handler.parseTimeSelection("2", offered),
                "Número simples '2' deve escolher opção 2 da lista (12:00), nunca 02:00 ou 14:00");
        assertEquals(java.util.Optional.of(LocalTime.of(12, 0)), handler.parseTimeSelection("2.", offered));
        assertEquals(java.util.Optional.of(LocalTime.of(14, 0)), handler.parseTimeSelection("3", offered));

        // Número simples fora do intervalo da lista (ex: "14" sem 'h' ou ':') é rejeitado em vez de adivinhar hora
        assertEquals(java.util.Optional.empty(), handler.parseTimeSelection("14", offered));
        assertEquals(java.util.Optional.empty(), handler.parseTimeSelection("14.", offered));

        // Hora explícita com 'h', ':' ou qualificador ("da tarde") resolve para o horário exato
        assertEquals(java.util.Optional.of(LocalTime.of(14, 0)), handler.parseTimeSelection("14h", offered));
        assertEquals(java.util.Optional.of(LocalTime.of(14, 0)), handler.parseTimeSelection("14:00", offered));
        assertEquals(java.util.Optional.of(LocalTime.of(14, 0)), handler.parseTimeSelection("2h da tarde", offered));
        assertEquals(java.util.Optional.of(LocalTime.of(14, 0)), handler.parseTimeSelection("quero 2h às 14h", offered));
        assertEquals(java.util.Optional.of(LocalTime.of(15, 0)), handler.parseTimeSelection("3 da tarde", offered));

        // Se 14:00 NÃO está disponível mas 16:00 está, "14h às 16h" deve retornar 14:00 (para ser rejeitado) e NÃO 16:00!
        List<LocalTime> onlyLater = List.of(LocalTime.of(16, 0), LocalTime.of(17, 0));
        assertEquals(java.util.Optional.of(LocalTime.of(14, 0)), handler.parseTimeSelection("14h às 16h", onlyLater));
    }

    @Test
    void shouldSelectThirdTimeSlotOn3InsteadOfTriggeringHumanHandoff() {
        when(tenantConfigRepository.findAll()).thenReturn(List.of(defaultConfig));
        when(userRepository.findAll()).thenReturn(List.of(mariana));
        when(appointmentRepository.findByArtistIdAndScheduledStartBetween(eq(mariana.getId()), any(), any()))
                .thenReturn(List.of());
        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MercadoPagoPaymentResponse mpResponse = MercadoPagoPaymentResponse.builder()
                .id(333444L)
                .status("pending")
                .pointOfInteraction(MercadoPagoPaymentResponse.PointOfInteraction.builder()
                        .transactionData(MercadoPagoPaymentResponse.TransactionData.builder()
                                .qrCode("000201OPT3SLOT")
                                .build())
                        .build())
                .build();
        when(paymentClient.createPixPayment(any(), any(), any(), any(), any())).thenReturn(mpResponse);

        EvolutionWebhookRequest webhook = createWebhook();
        handler.handle(customer, session, webhook, "Mariana");
        handler.handle(customer, session, webhook, "25/11/2026"); // slots: 1) 10:00, 2) 11:00, 3) 12:00...
        handler.handle(customer, session, webhook, "3");

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository, times(2)).saveAndFlush(captor.capture());
        assertEquals(12, captor.getAllValues().get(1).getScheduledStart().getHour());
        assertEquals(ConversationState.MENU, session.getCurrentState());
    }

    @Test
    void shouldAlwaysUseAmericaSaoPauloTimezoneEvenWhenServerClockIsUtcNearMidnight() {
        // 2026-11-24T01:30:00Z em UTC == 2026-11-23 às 22:30 em America/Sao_Paulo!
        Clock utcClockNearMidnight = Clock.fixed(Instant.parse("2026-11-24T01:30:00Z"), ZoneOffset.UTC);
        AgendamentoStateHandler utcServerHandler = new AgendamentoStateHandler(
                evolutionApiClient, appointmentRepository, paymentClient, objectMapper, chatService,
                tenantConfigRepository, userRepository, studioAdminController, utcClockNearMidnight);

        when(tenantConfigRepository.findAll()).thenReturn(List.of(defaultConfig));
        when(userRepository.findAll()).thenReturn(List.of(mariana));
        when(appointmentRepository.findByArtistIdAndScheduledStartBetween(eq(mariana.getId()), any(), any()))
                .thenReturn(List.of());

        EvolutionWebhookRequest webhook = createWebhook();
        utcServerHandler.handle(customer, session, webhook, "Mariana");
        // Em America/Sao_Paulo ainda é 23/11, então "amanhã" DEVE ser 24/11/2026 (não 25/11!)
        utcServerHandler.handle(customer, session, webhook, "amanhã");

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(evolutionApiClient, times(2)).sendTextMessage(eq("cefan-studio"), eq(customer.getPhone()), messageCaptor.capture());
        assertTrue(messageCaptor.getAllValues().get(1).contains("24/11/2026"));

        // 21:00 UTC == 18:00 BRT -> dentro do expediente 10:00–20:00
        assertFalse(AgendamentoStateHandler.isOutsideOperatingHours(
                OffsetDateTime.parse("2026-11-23T21:00:00Z"), defaultConfig));
        // 23:30 UTC == 20:30 BRT -> fora do expediente 10:00–20:00
        assertTrue(AgendamentoStateHandler.isOutsideOperatingHours(
                OffsetDateTime.parse("2026-11-23T23:30:00Z"), defaultConfig));
    }

    @Test
    void shouldSelectThirdRecommendedArtistOn3EvenBeforeCandidateIdsWereSaved() {
        User lucas = User.builder().id(UUID.randomUUID()).name("Lucas 'Blade'").role(UserRole.ARTIST)
                .specialties("Blackwork, Tribal").isActive(true).build();
        when(tenantConfigRepository.findAll()).thenReturn(List.of(defaultConfig));
        when(userRepository.findAll()).thenReturn(List.of(carlos, mariana, lucas));

        session.setContextData("{\"recommendedArtists\":[\"Carlos Silveira\",\"Mariana Rocha\",\"Lucas 'Blade'\"]}");

        handler.handle(customer, session, createWebhook(), "3");

        assertEquals(ConversationState.AGENDAMENTO_SELECAO_DATA, session.getCurrentState());
        assertNotNull(session.getAssignedUser());
        assertEquals("Lucas 'Blade'", session.getAssignedUser().getName());
    }
}
