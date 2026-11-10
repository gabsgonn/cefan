package br.com.cefan.whatsapp.application.statemachine.handler;

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OrcamentoStateHandler handler;
    private Customer customer;
    private ConversationSession session;

    @BeforeEach
    void setUp() {
        handler = new OrcamentoStateHandler(evolutionApiClient, geminiVisionClient, tenantConfigRepository, objectMapper);

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
        verifyNoInteractions(geminiVisionClient);
    }

    @Test
    void shouldTransitionToHumanHandoffWhenUserSends3() {
        EvolutionWebhookRequest webhook = new EvolutionWebhookRequest();
        webhook.setInstance("cefan-studio");
        EvolutionWebhookRequest.WebhookData data = new EvolutionWebhookRequest.WebhookData();
        EvolutionWebhookRequest.MessageContent content = new EvolutionWebhookRequest.MessageContent();
        content.setConversation("3");
        data.setMessage(content);
        webhook.setData(data);

        handler.handle(customer, session, webhook, "3");

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
    void shouldTransitionToSchedulingWhenUserSends2() {
        EvolutionWebhookRequest webhook = new EvolutionWebhookRequest();
        webhook.setInstance("cefan-studio");
        EvolutionWebhookRequest.WebhookData data = new EvolutionWebhookRequest.WebhookData();
        EvolutionWebhookRequest.MessageContent content = new EvolutionWebhookRequest.MessageContent();
        content.setConversation("2");
        data.setMessage(content);
        webhook.setData(data);

        handler.handle(customer, session, webhook, "2");

        assertEquals(ConversationState.AGENDAMENTO_SELECAO_DATA, session.getCurrentState());
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(evolutionApiClient).sendTextMessage(eq("cefan-studio"), eq("5511988888888"), messageCaptor.capture());
        assertTrue(messageCaptor.getValue().contains("agendamento"));
    }
}
