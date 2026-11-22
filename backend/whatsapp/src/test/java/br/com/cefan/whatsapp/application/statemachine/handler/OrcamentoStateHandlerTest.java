package br.com.cefan.whatsapp.application.statemachine.handler;

import br.com.cefan.whatsapp.application.service.ChatService;
import br.com.cefan.whatsapp.domain.model.ConversationSession;
import br.com.cefan.whatsapp.domain.model.ConversationState;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.model.TenantConfig;
import br.com.cefan.whatsapp.domain.repository.TenantConfigRepository;
import br.com.cefan.whatsapp.infrastructure.ai.GeminiVisionClient;
import br.com.cefan.whatsapp.infrastructure.ai.dto.TattooAnalysisResult;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import br.com.cefan.whatsapp.infrastructure.evolution.dto.EvolutionWebhookRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrcamentoStateHandlerTest {

    @Mock
    private EvolutionApiClient evolutionApiClient;

    @Mock
    private GeminiVisionClient geminiVisionClient;

    @Mock
    private TenantConfigRepository tenantConfigRepository;

    @Mock
    private ChatService chatService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OrcamentoStateHandler handler;
    private Customer customer;
    private ConversationSession session;

    @BeforeEach
    void setUp() {
        handler = new OrcamentoStateHandler(evolutionApiClient, geminiVisionClient, tenantConfigRepository, objectMapper, chatService);

        customer = Customer.builder()
                .id(UUID.randomUUID())
                .name("Mariana")
                .phone("5511988888888")
                .build();

        session = ConversationSession.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .currentState(ConversationState.ORCAMENTO_AGUARDANDO_FOTO)
                .lastInteractionAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void shouldProcessImageAndCalculateAccuratePriceRange() {
        TenantConfig config = TenantConfig.builder()
                .studioName("Vício Tattoo")
                .defaultHourlyRate(new BigDecimal("200.00")) // R$ 200/h
                .fixedSetupCost(new BigDecimal("100.00"))    // R$ 100 montagem
                .build();
        when(tenantConfigRepository.findAll()).thenReturn(List.of(config));

        TattooAnalysisResult analysis = TattooAnalysisResult.builder()
                .style("Realismo Preto e Cinza")
                .complexityLevel("ALTA")
                .complexityMultiplier(1.5) // (3.0h * 200 * 1.5) + 100 = 900 + 100 = 1000
                .estimatedHours(3.0)
                .suggestedBodyLocation("Braço ou Coxa")
                .technicalNotes("Exige sombreamento suave em múltiplas camadas.")
                .build();
        when(geminiVisionClient.analyzeTattoo(any(), any(), any())).thenReturn(analysis);

        EvolutionWebhookRequest webhook = new EvolutionWebhookRequest();
        webhook.setInstance("cefan-studio");
        EvolutionWebhookRequest.WebhookData data = new EvolutionWebhookRequest.WebhookData();
        EvolutionWebhookRequest.MessageContent content = new EvolutionWebhookRequest.MessageContent();
        content.setImageMessage(new EvolutionWebhookRequest.ImageMessage());
        data.setMessage(content);
        webhook.setData(data);

        handler.handle(customer, session, webhook, "Gostaria de saber o valor para essa arte no braço");

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511988888888"), messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertTrue(message.contains("Realismo Preto e Cinza"));
        assertTrue(message.contains("3,0h de agulha") || message.contains("3.0h de agulha"));
        assertTrue(message.contains("ALTA"));
        // 1000 * 0.9 = 900, 1000 * 1.15 = 1150
        assertTrue(message.contains("900") || message.contains("1150"));
        assertFalse(message.contains("Digite *A*"), "Pós-orçamento não deve repetir o bloco de tamanhos A-E");
        assertTrue(message.contains("1️⃣ *Agendar sessão*"));
        assertTrue(message.contains("2️⃣ *Falar com o tatuador*"));
        assertTrue(message.contains("3️⃣ *Voltar ao menu*"));
        assertNotNull(session.getContextData());
        assertTrue(session.getContextData().contains("Realismo Preto e Cinza"));
    }

    @Test
    void shouldAskForPhotoWhenOnlyTextIsReceived() {
        EvolutionWebhookRequest webhook = new EvolutionWebhookRequest();
        webhook.setInstance("cefan-studio");
        EvolutionWebhookRequest.WebhookData data = new EvolutionWebhookRequest.WebhookData();
        EvolutionWebhookRequest.MessageContent content = new EvolutionWebhookRequest.MessageContent();
        content.setConversation("Quero tatuar um leão");
        data.setMessage(content);
        webhook.setData(data);

        handler.handle(customer, session, webhook, "Quero tatuar um leão");

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511988888888"), messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertTrue(message.contains("foto de referência"));
        assertFalse(message.contains("Digite *A*"), "Pedido de foto não deve exibir escolha de tamanho A-E");
        verifyNoInteractions(geminiVisionClient);
    }

    @Test
    void shouldTransitionToHumanHandoffWhenUserSelectsOption2OrPollLabelAfterQuote() {
        session.setContextData("{\"style\":\"Fineline\",\"estimatedHours\":2.0}");

        EvolutionWebhookRequest webhook = new EvolutionWebhookRequest();
        webhook.setInstance("cefan-studio");
        EvolutionWebhookRequest.WebhookData data = new EvolutionWebhookRequest.WebhookData();
        EvolutionWebhookRequest.MessageContent content = new EvolutionWebhookRequest.MessageContent();
        content.setConversation("2");
        data.setMessage(content);
        webhook.setData(data);

        handler.handle(customer, session, webhook, "2");

        assertEquals(ConversationState.TRANSBORDO_HUMANO, session.getCurrentState());
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511988888888"), messageCaptor.capture());
        assertTrue(messageCaptor.getValue().contains("tatuadores"));
    }

    @Test
    void shouldDownloadMediaWhenBase64IsNotDirectlyInWebhook() {
        TenantConfig config = TenantConfig.builder()
                .studioName("Vício Tattoo")
                .defaultHourlyRate(new BigDecimal("150.00"))
                .fixedSetupCost(new BigDecimal("50.00"))
                .build();
        when(tenantConfigRepository.findAll()).thenReturn(List.of(config));

        when(evolutionApiClient.getBase64FromMediaMessage(eq("cefan-studio"), eq("MSG123"), eq("5511988888888@s.whatsapp.net"), eq(false)))
                .thenReturn(java.util.Optional.of("DOWNLOADED_BASE64"));

        TattooAnalysisResult analysis = TattooAnalysisResult.builder()
                .style("Fineline")
                .complexityLevel("BAIXA")
                .complexityMultiplier(1.1)
                .estimatedHours(1.5)
                .suggestedBodyLocation("Pulso")
                .technicalNotes("Traço fino e delicado.")
                .build();
        when(geminiVisionClient.analyzeTattoo(eq("DOWNLOADED_BASE64"), any(), any())).thenReturn(analysis);

        EvolutionWebhookRequest webhook = new EvolutionWebhookRequest();
        webhook.setInstance("cefan-studio");
        EvolutionWebhookRequest.WebhookData data = new EvolutionWebhookRequest.WebhookData();
        EvolutionWebhookRequest.MessageKey key = new EvolutionWebhookRequest.MessageKey();
        key.setId("MSG123");
        key.setRemoteJid("5511988888888@s.whatsapp.net");
        key.setFromMe(false);
        data.setKey(key);

        EvolutionWebhookRequest.MessageContent content = new EvolutionWebhookRequest.MessageContent();
        EvolutionWebhookRequest.ImageMessage img = new EvolutionWebhookRequest.ImageMessage();
        img.setMimetype("image/jpeg");
        content.setImageMessage(img);
        data.setMessage(content);
        webhook.setData(data);

        handler.handle(customer, session, webhook, "Quero fazer essa no pulso");

        verify(evolutionApiClient).getBase64FromMediaMessage("cefan-studio", "MSG123", "5511988888888@s.whatsapp.net", false);
        verify(geminiVisionClient).analyzeTattoo(eq("DOWNLOADED_BASE64"), eq("image/jpeg"), eq("Quero fazer essa no pulso"));
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511988888888"), contains("Fineline"));
    }

    @Test
    void shouldTransitionToSchedulingWhenUserSendsOption1AfterQuote() {
        TenantConfig config = TenantConfig.builder()
                .studioName("Vício Tattoo")
                .agendamentoIntroMessage("Informe o tatuador e data desejada na grade do Vício Tattoo:")
                .build();
        when(tenantConfigRepository.findAll()).thenReturn(List.of(config));
        session.setContextData("{\"style\":\"Fineline\",\"estimatedHours\":2.0}");

        EvolutionWebhookRequest webhook = new EvolutionWebhookRequest();
        webhook.setInstance("cefan-studio");
        EvolutionWebhookRequest.WebhookData data = new EvolutionWebhookRequest.WebhookData();
        EvolutionWebhookRequest.MessageContent content = new EvolutionWebhookRequest.MessageContent();
        content.setConversation("1");
        data.setMessage(content);
        webhook.setData(data);

        handler.handle(customer, session, webhook, "1");

        assertEquals(ConversationState.AGENDAMENTO_SELECAO_DATA, session.getCurrentState());
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511988888888"), messageCaptor.capture());
        assertTrue(messageCaptor.getValue().contains("Informe o tatuador e data desejada na grade do Vício Tattoo:"));
    }

    @Test
    void shouldRecalculateQuoteWhenCustomerAdjustsSizeInCmAfterSendingPhoto() {
        TenantConfig config = TenantConfig.builder()
                .studioName("Vício Tattoo")
                .defaultHourlyRate(new BigDecimal("150.00"))
                .fixedSetupCost(new BigDecimal("50.00"))
                .build();
        when(tenantConfigRepository.findAll()).thenReturn(List.of(config));

        // 1. Cliente já enviou a foto e recebeu análise inicial (salva no contextData)
        session.setContextData("{\"style\":\"Blackwork / Geométrico\",\"complexityLevel\":\"MEDIA\",\"complexityMultiplier\":1.2,\"estimatedHours\":2.5,\"sizeCategory\":\"MEDIA\",\"estimatedSizeCm\":\"Média (11 a 18 cm)\",\"recommendedArtists\":[\"Lucas 'Blade'\"]}");

        EvolutionWebhookRequest webhook = new EvolutionWebhookRequest();
        webhook.setInstance("cefan-studio");

        // 2. Cliente ajusta a estimativa informando medida em cm ("4cm" -> Flash / Mini - 1.0h)
        handler.handle(customer, session, webhook, "4cm");

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511988888888"), messageCaptor.capture());

        String reply = messageCaptor.getValue();
        assertTrue(reply.contains("Flash / Mini (até 5 cm)"));
        assertTrue(reply.contains("1,0h de agulha") || reply.contains("1.0h de agulha"));
        assertFalse(reply.contains("Digite *A*"));
        assertTrue(reply.contains("1️⃣ *Agendar sessão*"));
        assertTrue(session.getContextData().contains("\"sizeCategory\":\"FLASH\""));
    }

    @Test
    void shouldReplyFriendlyAndResendThreeActionsOnInvalidInputAfterQuoteWithoutChangingState() {
        String originalContext = "{\"style\":\"Blackwork / Geométrico\",\"complexityLevel\":\"MEDIA\",\"complexityMultiplier\":1.2,\"estimatedHours\":2.5}";
        session.setContextData(originalContext);

        EvolutionWebhookRequest webhook = new EvolutionWebhookRequest();
        webhook.setInstance("cefan-studio");

        handler.handle(customer, session, webhook, "quero 2 tatuagens");
        handler.handle(customer, session, webhook, "oi");

        assertEquals(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, session.getCurrentState());
        assertEquals(originalContext, session.getContextData());

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(evolutionApiClient, times(2)).sendTextMessage(eq("cefan-studio"), eq("5511988888888"), messageCaptor.capture());
        for (String reply : messageCaptor.getAllValues()) {
            assertTrue(reply.contains("Não entendi sua resposta"));
            assertTrue(reply.contains("1️⃣ *Agendar sessão*"));
            assertTrue(reply.contains("2️⃣ *Falar com o tatuador*"));
            assertTrue(reply.contains("3️⃣ *Voltar ao menu*"));
        }
    }

    @Test
    void shouldSetPriceACombinarAndForwardToSpecialistArtistWhenFechamentoIsSelected() {
        br.com.cefan.whatsapp.domain.repository.UserRepository userRepository = mock(br.com.cefan.whatsapp.domain.repository.UserRepository.class);
        br.com.cefan.whatsapp.api.controller.StudioAdminController adminController = mock(br.com.cefan.whatsapp.api.controller.StudioAdminController.class);

        br.com.cefan.whatsapp.domain.model.User carlos = br.com.cefan.whatsapp.domain.model.User.builder()
                .id(UUID.randomUUID())
                .name("Carlos Silveira")
                .role(br.com.cefan.whatsapp.domain.model.UserRole.ARTIST)
                .specialties("Realismo Preto e Cinza, Retratos")
                .isActive(true)
                .build();
        when(userRepository.findAll()).thenReturn(List.of(carlos));

        OrcamentoStateHandler handlerWithUsers = new OrcamentoStateHandler(
                evolutionApiClient, geminiVisionClient, tenantConfigRepository, objectMapper, chatService, adminController, userRepository);

        session.setContextData("{\"style\":\"Realismo Preto e Cinza\",\"complexityLevel\":\"ALTA\",\"complexityMultiplier\":1.4,\"estimatedHours\":3.5,\"recommendedArtists\":[\"Carlos Silveira\"]}");

        EvolutionWebhookRequest webhook = new EvolutionWebhookRequest();
        webhook.setInstance("cefan-studio");

        // Cliente escolhe "Fechamento" (ou "E")
        handlerWithUsers.handle(customer, session, webhook, "Fechamento");

        assertEquals(ConversationState.TRANSBORDO_HUMANO, session.getCurrentState());
        assertEquals(carlos, session.getAssignedUser());
        assertEquals("Carlos Silveira", session.getAssignedArtistName());
        assertTrue(session.getContextData().contains("\"priceDisplay\":\"A combinar\""));
        assertTrue(session.getContextData().contains("\"isFechamento\":true"));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511988888888"), messageCaptor.capture());

        String reply = messageCaptor.getValue();
        assertTrue(reply.contains("Fechamento (26 cm+ / Projeto Amplo)"));
        assertTrue(reply.contains("*A combinar*"));
        assertTrue(reply.contains("Carlos Silveira"));
        verify(adminController).publishNotification(eq("HUMAN_REQUEST"), contains("Carlos Silveira"), contains("A combinar"), eq("5511988888888"));
    }

    @Test
    void shouldApplyLatestSizeWhenCustomerChangesSizeBeforeSendingPhoto() {
        TenantConfig config = TenantConfig.builder()
                .studioName("Vício Tattoo")
                .defaultHourlyRate(new BigDecimal("150.00"))
                .fixedSetupCost(new BigDecimal("50.00"))
                .build();
        when(tenantConfigRepository.findAll()).thenReturn(List.of(config));

        EvolutionWebhookRequest textWebhook = new EvolutionWebhookRequest();
        textWebhook.setInstance("cefan-studio");

        // 1. Cliente escolhe "Flash" inicialmente e depois troca para "Grande" antes de enviar a foto
        handler.handle(customer, session, textWebhook, "Flash");
        handler.handle(customer, session, textWebhook, "Grande");

        TattooAnalysisResult analysis = TattooAnalysisResult.builder()
                .style("Blackwork / Geométrico")
                .sizeCategory("PEQUENA")
                .estimatedSizeCm("6x4 cm")
                .complexityLevel("MEDIA")
                .complexityMultiplier(1.2)
                .estimatedHours(1.5)
                .recommendedArtists(List.of("Lucas 'Blade'"))
                .build();
        when(geminiVisionClient.analyzeTattoo(any(), any(), any())).thenReturn(analysis);

        EvolutionWebhookRequest imageWebhook = new EvolutionWebhookRequest();
        imageWebhook.setInstance("cefan-studio");
        EvolutionWebhookRequest.WebhookData data = new EvolutionWebhookRequest.WebhookData();
        EvolutionWebhookRequest.MessageContent content = new EvolutionWebhookRequest.MessageContent();
        content.setImageMessage(new EvolutionWebhookRequest.ImageMessage());
        data.setMessage(content);
        imageWebhook.setData(data);

        // 2. Cliente envia a foto sem legenda -> deve prevalecer "Grande" (4.0h) e não "Flash" ou "Pequena"
        handler.handle(customer, session, imageWebhook, "");

        assertTrue(session.getContextData().contains("\"sizeCategory\":\"GRANDE\""));
        assertTrue(session.getContextData().contains("\"estimatedHours\":4.0"));
    }

    @Test
    void shouldRouteToUpdatedSpecialistWhenCustomerSwitchesStyleBeforeSelectingFechamento() {
        br.com.cefan.whatsapp.domain.repository.UserRepository userRepository = mock(br.com.cefan.whatsapp.domain.repository.UserRepository.class);
        br.com.cefan.whatsapp.api.controller.StudioAdminController adminController = mock(br.com.cefan.whatsapp.api.controller.StudioAdminController.class);

        br.com.cefan.whatsapp.domain.model.User mariana = br.com.cefan.whatsapp.domain.model.User.builder()
                .id(UUID.randomUUID())
                .name("Mariana Rocha")
                .role(br.com.cefan.whatsapp.domain.model.UserRole.ARTIST)
                .specialties("Fineline, Traço Fino, Delicadas")
                .isActive(true)
                .build();
        br.com.cefan.whatsapp.domain.model.User carlos = br.com.cefan.whatsapp.domain.model.User.builder()
                .id(UUID.randomUUID())
                .name("Carlos Silveira")
                .role(br.com.cefan.whatsapp.domain.model.UserRole.ARTIST)
                .specialties("Realismo Preto e Cinza, Retratos")
                .isActive(true)
                .build();
        when(userRepository.findAll()).thenReturn(List.of(mariana, carlos));

        OrcamentoStateHandler handlerWithUsers = new OrcamentoStateHandler(
                evolutionApiClient, geminiVisionClient, tenantConfigRepository, objectMapper, chatService, adminController, userRepository);

        // 1. Foto 1 era Fineline (Mariana Rocha)
        session.setContextData("{\"style\":\"Fineline / Traço Fino\",\"complexityLevel\":\"BAIXA\",\"complexityMultiplier\":1.05,\"estimatedHours\":1.0,\"recommendedArtists\":[\"Mariana Rocha\"]}");

        // 2. Cliente decide fechar o braço em Realismo
        EvolutionWebhookRequest webhook = new EvolutionWebhookRequest();
        webhook.setInstance("cefan-studio");
        handlerWithUsers.handle(customer, session, webhook, "Quero fechamento de braço em realismo");

        assertEquals(ConversationState.TRANSBORDO_HUMANO, session.getCurrentState());
        assertEquals(carlos, session.getAssignedUser());
        assertEquals("Carlos Silveira", session.getAssignedArtistName());
        assertTrue(session.getContextData().contains("\"style\":\"Realismo Preto e Cinza\""));
        assertTrue(session.getContextData().contains("\"priceDisplay\":\"A combinar\""));
    }

    @Test
    void shouldAskToConfirmSuggestedArtistWhenTransitioningFromQuoteToScheduling() {
        br.com.cefan.whatsapp.domain.repository.UserRepository userRepository = mock(br.com.cefan.whatsapp.domain.repository.UserRepository.class);
        br.com.cefan.whatsapp.domain.model.User marianaArtist = br.com.cefan.whatsapp.domain.model.User.builder()
                .id(UUID.randomUUID())
                .name("Mariana Rocha")
                .role(br.com.cefan.whatsapp.domain.model.UserRole.ARTIST)
                .specialties("Fineline, Traço Fino")
                .isActive(true)
                .build();
        when(userRepository.findAll()).thenReturn(List.of(marianaArtist));

        TenantConfig config = TenantConfig.builder()
                .studioName("Vício Tattoo")
                .openingTime("10:00")
                .closingTime("20:00")
                .workingDays("SEG-SAB")
                .build();
        when(tenantConfigRepository.findAll()).thenReturn(List.of(config));

        OrcamentoStateHandler handlerWithUsers = new OrcamentoStateHandler(
                evolutionApiClient, geminiVisionClient, tenantConfigRepository, objectMapper, chatService, null, userRepository);

        session.setContextData("{\"style\":\"Fineline\",\"estimatedHours\":2.0,\"recommendedArtists\":[\"Mariana Rocha\"]}");

        EvolutionWebhookRequest webhook = new EvolutionWebhookRequest();
        webhook.setInstance("cefan-studio");
        handlerWithUsers.handle(customer, session, webhook, "1");

        assertEquals(ConversationState.AGENDAMENTO_SELECAO_DATA, session.getCurrentState());
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511988888888"), messageCaptor.capture());
        String reply = messageCaptor.getValue();
        assertTrue(reply.contains("Etapa 1 de 3 — Profissional"));
        assertTrue(reply.contains("Mariana Rocha"));
        assertTrue(reply.contains("sim"));
    }

    @Test
    void shouldRouteToHumanHandoffOnOption1WhenEstimatedSessionExceedsOperatingHours() {
        br.com.cefan.whatsapp.domain.repository.UserRepository userRepository = mock(br.com.cefan.whatsapp.domain.repository.UserRepository.class);
        br.com.cefan.whatsapp.api.controller.StudioAdminController adminController = mock(br.com.cefan.whatsapp.api.controller.StudioAdminController.class);
        br.com.cefan.whatsapp.domain.model.User carlos = br.com.cefan.whatsapp.domain.model.User.builder()
                .id(UUID.randomUUID())
                .name("Carlos Silveira")
                .role(br.com.cefan.whatsapp.domain.model.UserRole.ARTIST)
                .specialties("Realismo Preto e Cinza")
                .isActive(true)
                .build();
        when(userRepository.findAll()).thenReturn(List.of(carlos));

        TenantConfig config = TenantConfig.builder()
                .studioName("Vício Tattoo")
                .openingTime("10:00")
                .closingTime("20:00")
                .workingDays("SEG-SAB")
                .build();
        when(tenantConfigRepository.findAll()).thenReturn(List.of(config));

        OrcamentoStateHandler handlerWithUsers = new OrcamentoStateHandler(
                evolutionApiClient, geminiVisionClient, tenantConfigRepository, objectMapper, chatService, adminController, userRepository);

        session.setContextData("{\"style\":\"Realismo Preto e Cinza\",\"estimatedHours\":11.0,\"recommendedArtists\":[\"Carlos Silveira\"]}");

        EvolutionWebhookRequest webhook = new EvolutionWebhookRequest();
        webhook.setInstance("cefan-studio");
        handlerWithUsers.handle(customer, session, webhook, "1");

        assertEquals(ConversationState.TRANSBORDO_HUMANO, session.getCurrentState());
        verify(adminController).publishNotification(eq("HUMAN_REQUEST"), contains("11h"), anyString(), eq("5511988888888"));
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511988888888"), contains("ultrapassa nosso expediente"));
    }
}
