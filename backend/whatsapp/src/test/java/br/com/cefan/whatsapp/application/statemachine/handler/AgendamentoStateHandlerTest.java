package br.com.cefan.whatsapp.application.statemachine.handler;

import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import br.com.cefan.whatsapp.domain.model.ConversationSession;
import br.com.cefan.whatsapp.domain.model.ConversationState;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.repository.AppointmentRepository;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AgendamentoStateHandler handler;
    private Customer customer;
    private ConversationSession session;

    @BeforeEach
    void setUp() {
        handler = new AgendamentoStateHandler(evolutionApiClient, appointmentRepository, paymentClient, objectMapper);

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
}
