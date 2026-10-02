package br.com.cefan.whatsapp.api.webhook;

import br.com.cefan.whatsapp.application.service.MessageHandlingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EvolutionWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessageHandlingService messageHandlingService;

    @Test
    void shouldReturnHealthCheck() throws Exception {
        mockMvc.perform(get("/api/v1/webhooks/evolution/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Webhook endpoint operacional!"));
    }

    @Test
    void shouldProcessIncomingMessageWebhook() throws Exception {
        String jsonPayload = """
                {
                  "event": "messages.upsert",
                  "instance": "cefan-studio",
                  "data": {
                    "key": {
                      "remoteJid": "5511999999999@s.whatsapp.net",
                      "fromMe": false
                    },
                    "pushName": "Gabs",
                    "message": {
                      "conversation": "Olá, gostaria de saber sobre orçamentos"
                    }
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/webhooks/evolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk());

        verify(messageHandlingService).handleIncomingMessage(any());
    }
}
