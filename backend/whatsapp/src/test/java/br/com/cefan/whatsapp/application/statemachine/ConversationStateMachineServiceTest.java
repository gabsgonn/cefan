package br.com.cefan.whatsapp.application.statemachine;

import br.com.cefan.whatsapp.application.service.ChatService;
import br.com.cefan.whatsapp.application.statemachine.handler.AgendamentoStateHandler;
import br.com.cefan.whatsapp.application.statemachine.handler.MenuStateHandler;
import br.com.cefan.whatsapp.application.statemachine.handler.OrcamentoStateHandler;
import br.com.cefan.whatsapp.application.statemachine.handler.TransbordoHumanoStateHandler;
import br.com.cefan.whatsapp.domain.model.ConversationSession;
import br.com.cefan.whatsapp.domain.model.ConversationState;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.model.TenantConfig;
import br.com.cefan.whatsapp.domain.repository.AppointmentRepository;
import br.com.cefan.whatsapp.domain.repository.ConversationSessionRepository;
import br.com.cefan.whatsapp.domain.repository.TenantConfigRepository;
import br.com.cefan.whatsapp.domain.repository.UserRepository;
import br.com.cefan.whatsapp.infrastructure.ai.GeminiVisionClient;
import br.com.cefan.whatsapp.infrastructure.ai.dto.TattooAnalysisResult;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import br.com.cefan.whatsapp.infrastructure.evolution.dto.EvolutionWebhookRequest;
import br.com.cefan.whatsapp.infrastructure.mercadopago.MercadoPagoPaymentClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationStateMachineServiceTest {

    @Mock
    private ConversationSessionRepository sessionRepository;

    @Mock
    private EvolutionApiClient evolutionApiClient;

    @Mock
    private GeminiVisionClient geminiVisionClient;

    @Mock
    private TenantConfigRepository tenantConfigRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private MercadoPagoPaymentClient paymentClient;

    @Mock
    private ChatService chatService;

    @Mock
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ConversationStateMachineService stateMachineService;
    private MenuStateHandler menuStateHandler;
    private Customer customer;

    @BeforeEach
    void setUp() {
        menuStateHandler = new MenuStateHandler(evolutionApiClient, chatService, tenantConfigRepository);
        OrcamentoStateHandler orcamentoStateHandler = new OrcamentoStateHandler(
                evolutionApiClient, geminiVisionClient, tenantConfigRepository, objectMapper, chatService);
        AgendamentoStateHandler agendamentoStateHandler = new AgendamentoStateHandler(
                evolutionApiClient, appointmentRepository, paymentClient, objectMapper, chatService, tenantConfigRepository, userRepository);
        TransbordoHumanoStateHandler transbordoHumanoStateHandler = new TransbordoHumanoStateHandler();

        List<ConversationStateHandler> handlers = List.of(
                menuStateHandler,
                orcamentoStateHandler,
                agendamentoStateHandler,
                transbordoHumanoStateHandler
        );

        stateMachineService = new ConversationStateMachineService(sessionRepository, menuStateHandler, chatService, handlers);

        customer = Customer.builder()
                .id(UUID.randomUUID())
                .name("Gabriel")
                .phone("5511999999999")
                .build();
    }

    private EvolutionWebhookRequest createWebhook(String text, boolean withImage) {
        EvolutionWebhookRequest webhook = new EvolutionWebhookRequest();
        webhook.setInstance("cefan-studio");
        EvolutionWebhookRequest.WebhookData data = new EvolutionWebhookRequest.WebhookData();
        EvolutionWebhookRequest.MessageContent content = new EvolutionWebhookRequest.MessageContent();

        if (text != null) {
            content.setConversation(text);
        }
        if (withImage) {
            content.setImageMessage(new EvolutionWebhookRequest.ImageMessage());
        }

        data.setMessage(content);
        webhook.setData(data);
        return webhook;
    }

    @Test
    void shouldSendWelcomeMenuWhenNoSessionAndMessageIsInvalid() {
        when(sessionRepository.findByCustomer(customer)).thenReturn(Optional.empty());
        when(sessionRepository.save(any(ConversationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EvolutionWebhookRequest webhook = createWebhook("Olá", false);

        stateMachineService.process(customer, webhook);

        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511999999999"), contains("Bem-vindo ao *Vício Tattoo*"));
    }

    @Test
    void shouldTransitionToOrcamentoWhenOptionOneIsChosen() {
        ConversationSession session = ConversationSession.builder()
                .customer(customer)
                .currentState(ConversationState.MENU)
                .lastInteractionAt(OffsetDateTime.now())
                .build();

        when(sessionRepository.findByCustomer(customer)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ConversationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EvolutionWebhookRequest webhook = createWebhook("1", false);

        stateMachineService.process(customer, webhook);

        assertEquals(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, session.getCurrentState());
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511999999999"), argThat(msg ->
                msg.contains("foto da tatuagem") && !msg.contains("Digite *A*")));
    }

    @Test
    void shouldTransitionToAgendamentoWhenOptionTwoIsChosen() {
        ConversationSession session = ConversationSession.builder()
                .customer(customer)
                .currentState(ConversationState.MENU)
                .lastInteractionAt(OffsetDateTime.now())
                .build();

        when(sessionRepository.findByCustomer(customer)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ConversationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EvolutionWebhookRequest webhook = createWebhook("2", false);

        stateMachineService.process(customer, webhook);

        assertEquals(ConversationState.AGENDAMENTO_SELECAO_DATA, session.getCurrentState());
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511999999999"), contains("Vamos iniciar seu agendamento"));
    }

    @Test
    void shouldTransitionToTransbordoHumanoWhenOptionThreeIsChosen() {
        ConversationSession session = ConversationSession.builder()
                .customer(customer)
                .currentState(ConversationState.MENU)
                .lastInteractionAt(OffsetDateTime.now())
                .build();

        when(sessionRepository.findByCustomer(customer)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ConversationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EvolutionWebhookRequest webhook = createWebhook("3", false);

        stateMachineService.process(customer, webhook);

        assertEquals(ConversationState.TRANSBORDO_HUMANO, session.getCurrentState());
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511999999999"), contains("Notifiquei nossa equipe"));
    }

    @Test
    void shouldRemainSilentWhenInTransbordoHumanoAndNormalMessageSent() {
        ConversationSession session = ConversationSession.builder()
                .customer(customer)
                .currentState(ConversationState.TRANSBORDO_HUMANO)
                .lastInteractionAt(OffsetDateTime.now())
                .build();

        when(sessionRepository.findByCustomer(customer)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ConversationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EvolutionWebhookRequest webhook = createWebhook("Qual horário vocês abrem?", false);

        stateMachineService.process(customer, webhook);

        assertEquals(ConversationState.TRANSBORDO_HUMANO, session.getCurrentState());
        verifyNoInteractions(evolutionApiClient);
    }

    @Test
    void shouldResetToMenuWhenZeroIsSentEvenInTransbordoHumano() {
        ConversationSession session = ConversationSession.builder()
                .customer(customer)
                .currentState(ConversationState.TRANSBORDO_HUMANO)
                .lastInteractionAt(OffsetDateTime.now())
                .build();

        when(sessionRepository.findByCustomer(customer)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ConversationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EvolutionWebhookRequest webhook = createWebhook("0", false);

        stateMachineService.process(customer, webhook);

        assertEquals(ConversationState.MENU, session.getCurrentState());
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511999999999"), contains("Bem-vindo ao *Vício Tattoo*"));
    }

    @Test
    void shouldHandleImageInOrcamentoState() {
        ConversationSession session = ConversationSession.builder()
                .customer(customer)
                .currentState(ConversationState.ORCAMENTO_AGUARDANDO_FOTO)
                .lastInteractionAt(OffsetDateTime.now())
                .build();

        when(sessionRepository.findByCustomer(customer)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ConversationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TenantConfig config = TenantConfig.builder()
                .studioName("Vício Tattoo")
                .defaultHourlyRate(new BigDecimal("150.00"))
                .fixedSetupCost(new BigDecimal("50.00"))
                .build();
        when(tenantConfigRepository.findAll()).thenReturn(List.of(config));

        TattooAnalysisResult analysis = TattooAnalysisResult.builder()
                .style("Fineline")
                .complexityLevel("BAIXA")
                .complexityMultiplier(1.1)
                .estimatedHours(1.5)
                .suggestedBodyLocation("Antebraço")
                .technicalNotes("Traços finos delicados com agulha 03RL.")
                .build();
        when(geminiVisionClient.analyzeTattoo(any(), any(), any())).thenReturn(analysis);

        EvolutionWebhookRequest webhook = createWebhook(null, true);

        stateMachineService.process(customer, webhook);

        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511999999999"), contains("Orçamento Estimado"));
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511999999999"), contains("Fineline"));
    }

    @Test
    void shouldListArtistsPortfolioWhenOptionFourIsChosen() {
        ConversationSession session = ConversationSession.builder()
                .customer(customer)
                .currentState(ConversationState.MENU)
                .lastInteractionAt(OffsetDateTime.now())
                .build();

        when(sessionRepository.findByCustomer(customer)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ConversationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EvolutionWebhookRequest webhook = createWebhook("4", false);

        stateMachineService.process(customer, webhook);

        assertEquals(ConversationState.MENU, session.getCurrentState());
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511999999999"), contains("Equipe de Especialistas"));
        verify(chatService).recordBotMessage(eq(session), eq(customer), contains("Equipe de Especialistas"));
    }

    @Test
    void shouldAutoTransitionFromMenuToOrcamentoWhenCustomerSendsImageDirectly() {
        ConversationSession session = ConversationSession.builder()
                .customer(customer)
                .currentState(ConversationState.MENU)
                .lastInteractionAt(OffsetDateTime.now())
                .build();

        when(sessionRepository.findByCustomer(customer)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ConversationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TenantConfig config = TenantConfig.builder()
                .studioName("Vício Tattoo")
                .defaultHourlyRate(new BigDecimal("150.00"))
                .fixedSetupCost(new BigDecimal("50.00"))
                .build();
        when(tenantConfigRepository.findAll()).thenReturn(List.of(config));

        TattooAnalysisResult analysis = TattooAnalysisResult.builder()
                .style("Fineline")
                .complexityLevel("BAIXA")
                .complexityMultiplier(1.05)
                .estimatedHours(1.0)
                .suggestedBodyLocation("Antebraço")
                .technicalNotes("Traço delicado.")
                .build();
        when(geminiVisionClient.analyzeTattoo(any(), any(), any())).thenReturn(analysis);

        EvolutionWebhookRequest webhook = createWebhook("Quero no braço, uns 10cm", true);

        stateMachineService.process(customer, webhook);

        assertEquals(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, session.getCurrentState());
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511999999999"), contains("Orçamento Estimado"));
    }

    @Test
    void shouldNormalizePollLabelsAndPostQuoteThreeActionsBeforeHandlers() {
        ConversationSession session = ConversationSession.builder()
                .customer(customer)
                .currentState(ConversationState.ORCAMENTO_AGUARDANDO_FOTO)
                .contextData("{\"style\":\"Fineline\",\"estimatedHours\":2.0}")
                .lastInteractionAt(OffsetDateTime.now())
                .build();

        when(sessionRepository.findByCustomer(customer)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ConversationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 1. Entrada inválida no pós-orçamento mantém estado e reenvia as 3 ações
        stateMachineService.process(customer, createWebhook("quero 2 tatuagens", false));
        assertEquals(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, session.getCurrentState());
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511999999999"), argThat(msg ->
                msg.contains("Não entendi sua resposta")
                        && msg.contains("Agendar sessão")
                        && msg.contains("Falar com o tatuador")
                        && msg.contains("Voltar ao menu")));

        // 2. Opção 3 (ou rótulo de enquete "Voltar ao menu") volta para MENU e limpa contextData
        stateMachineService.process(customer, createWebhook("3", false));
        assertEquals(ConversationState.MENU, session.getCurrentState());
        org.junit.jupiter.api.Assertions.assertNull(session.getContextData());

        // 3. Rótulo de enquete no MENU ("Fazer orçamento") entra no orçamento sem bloco A-E
        stateMachineService.process(customer, createWebhook("Fazer orçamento", false));
        assertEquals(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, session.getCurrentState());
    }

    @Test
    void shouldSelectThirdTimeSlotOn3DuringSchedulingWithQuoteContextAndNotTriggerVoltar() {
        UUID marianaId = UUID.randomUUID();
        br.com.cefan.whatsapp.domain.model.User mariana = br.com.cefan.whatsapp.domain.model.User.builder()
                .id(marianaId)
                .name("Mariana Rocha")
                .role(br.com.cefan.whatsapp.domain.model.UserRole.ARTIST)
                .isActive(true)
                .build();

        // Sessão já está em STEP_HORARIO com orçamento ("style") presente no contextData
        java.time.LocalDate futureDate = java.time.LocalDate.now(AgendamentoStateHandler.STUDIO_ZONE).plusDays(2);
        if (futureDate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            futureDate = futureDate.plusDays(1);
        }
        String ctx = String.format(
                "{\"style\":\"Fineline\",\"basePrice\":\"400.00\",\"estimatedHours\":2.0," +
                "\"schedulingStep\":\"HORARIO\",\"schedulingSelectedArtistId\":\"%s\"," +
                "\"schedulingSelectedArtistName\":\"Mariana Rocha\",\"schedulingSelectedDate\":\"%s\"," +
                "\"schedulingAvailableSlots\":[\"10:00\",\"11:00\",\"12:00\",\"14:00\"],\"schedulingDurationHours\":2}",
                marianaId, futureDate);

        ConversationSession session = ConversationSession.builder()
                .customer(customer)
                .currentState(ConversationState.AGENDAMENTO_SELECAO_DATA)
                .contextData(ctx)
                .lastInteractionAt(OffsetDateTime.now(AgendamentoStateHandler.STUDIO_ZONE))
                .build();

        TenantConfig config = TenantConfig.builder()
                .studioName("Vício Tattoo")
                .openingTime("10:00")
                .closingTime("20:00")
                .workingDays("SEG-SAB")
                .signalPercentage(new BigDecimal("30.00"))
                .minDepositAmount(new BigDecimal("50.00"))
                .pixExpirationMinutes(120)
                .build();

        when(sessionRepository.findByCustomer(customer)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ConversationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tenantConfigRepository.findAll()).thenReturn(List.of(config));
        when(userRepository.findAll()).thenReturn(List.of(mariana));
        when(userRepository.findByIdForUpdate(marianaId)).thenReturn(Optional.of(mariana));
        when(appointmentRepository.findByArtistIdAndScheduledStartBetween(eq(marianaId), any(), any())).thenReturn(List.of());
        when(appointmentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentClient.createPixPayment(any(), any(), any(), any(), any()))
                .thenReturn(br.com.cefan.whatsapp.infrastructure.mercadopago.dto.MercadoPagoPaymentResponse.builder()
                        .id(555111L)
                        .status("pending")
                        .pointOfInteraction(br.com.cefan.whatsapp.infrastructure.mercadopago.dto.MercadoPagoPaymentResponse.PointOfInteraction.builder()
                                .transactionData(br.com.cefan.whatsapp.infrastructure.mercadopago.dto.MercadoPagoPaymentResponse.TransactionData.builder()
                                        .qrCode("000201SLOT3OK")
                                        .build())
                                .build())
                        .build());

        stateMachineService.process(customer, createWebhook("3", false));

        // Deve ter reservado a opção 3 (12:00) e enviado o PIX — NÃO voltado ao menu!
        org.mockito.ArgumentCaptor<br.com.cefan.whatsapp.domain.model.Appointment> apptCaptor =
                org.mockito.ArgumentCaptor.forClass(br.com.cefan.whatsapp.domain.model.Appointment.class);
        verify(appointmentRepository, atLeastOnce()).saveAndFlush(apptCaptor.capture());
        assertEquals(12, apptCaptor.getValue().getScheduledStart().getHour());
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511999999999"), eq("000201SLOT3OK"));
        verify(evolutionApiClient, never()).sendTextMessage(anyString(), anyString(), contains("Bem-vindo ao *Vício Tattoo*"));
    }
}
