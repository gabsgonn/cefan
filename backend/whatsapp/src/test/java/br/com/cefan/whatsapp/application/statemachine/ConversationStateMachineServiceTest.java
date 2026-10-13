package br.com.cefan.whatsapp.application.statemachine;

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ConversationStateMachineService stateMachineService;
    private MenuStateHandler menuStateHandler;
    private Customer customer;

    @BeforeEach
    void setUp() {
        menuStateHandler = new MenuStateHandler(evolutionApiClient);
        OrcamentoStateHandler orcamentoStateHandler = new OrcamentoStateHandler(
                evolutionApiClient, geminiVisionClient, tenantConfigRepository, objectMapper);
        AgendamentoStateHandler agendamentoStateHandler = new AgendamentoStateHandler(
                evolutionApiClient, appointmentRepository, paymentClient, objectMapper);
        TransbordoHumanoStateHandler transbordoHumanoStateHandler = new TransbordoHumanoStateHandler();

        List<ConversationStateHandler> handlers = List.of(
                menuStateHandler,
                orcamentoStateHandler,
                agendamentoStateHandler,
                transbordoHumanoStateHandler
        );

        stateMachineService = new ConversationStateMachineService(sessionRepository, menuStateHandler, handlers);

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
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511999999999"), contains("foto da tatuagem"));
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
}
