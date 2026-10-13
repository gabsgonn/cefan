package br.com.cefan.whatsapp.api.webhook;

import br.com.cefan.whatsapp.application.service.PaymentProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MercadoPagoWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentProcessingService paymentProcessingService;

    @Test
    void shouldReturnHealthCheck() throws Exception {
        mockMvc.perform(get("/api/v1/webhooks/mercadopago/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Mercado Pago webhook operacional!"));
    }

    @Test
    void shouldProcessWebhookNotificationFromPayload() throws Exception {
        String payload = """
                {
                  "action": "payment.updated",
                  "type": "payment",
                  "data": {
                    "id": "123456789"
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/webhooks/mercadopago")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(paymentProcessingService).handlePaymentNotification("123456789");
    }

    @Test
    void shouldProcessWebhookNotificationFromQueryParams() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/mercadopago?topic=payment&id=987654321"))
                .andExpect(status().isOk());

        verify(paymentProcessingService).handlePaymentNotification("987654321");
    }
}
