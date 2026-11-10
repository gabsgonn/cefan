package br.com.cefan.whatsapp.api.webhook;

import br.com.cefan.whatsapp.application.service.MessageHandlingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EvolutionWebhookControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MessageHandlingService messageHandlingService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new EvolutionWebhookController(messageHandlingService)).build();
    }

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

    @Test
    void shouldProcessIncomingImageMessageWithBufferMediaKey() throws Exception {
        String jsonPayload = """
                {
                  "event": "messages.upsert",
                  "instance": "cefan-studio",
                  "data": {
                    "key": {
                      "remoteJid": "554199074863@s.whatsapp.net",
                      "fromMe": false,
                      "id": "BAE5ABC123456"
                    },
                    "pushName": "Gabriel",
                    "message": {
                      "imageMessage": {
                        "url": "https://mmg.whatsapp.net/v/t24/image.enc",
                        "mimetype": "image/jpeg",
                        "caption": "Quero essa tatuagem",
                        "mediaKey": {
                          "type": "Buffer",
                          "data": [1, 2, 3, 4, 5]
                        },
                        "base64": "data:image/jpeg;base64,ABCDEF1234"
                      }
                    },
                    "messageType": "imageMessage",
                    "messageTimestamp": 1710000000
                  },
                  "sender": "554199074863@s.whatsapp.net"
                }
                """;

        mockMvc.perform(post("/api/v1/webhooks/evolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk());

        verify(messageHandlingService).handleIncomingMessage(any());
    }

    @Test
    void shouldProcessIncomingDocumentImageMessage() throws Exception {
        String jsonPayload = """
                {
                  "event": "messages.upsert",
                  "instance": "cefan-studio",
                  "data": {
                    "key": {
                      "remoteJid": "554199074863@s.whatsapp.net",
                      "fromMe": false,
                      "id": "DOC999"
                    },
                    "message": {
                      "documentMessage": {
                        "mimetype": "image/jpeg",
                        "fileName": "tattoo.jpg",
                        "mediaKey": {
                          "type": "Buffer",
                          "data": [1, 2, 3]
                        }
                      }
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

    @Test
    void shouldHandleContactsUpdateWithDataArray() throws Exception {
        String jsonPayload = """
                {
                  "event": "contacts.update",
                  "instance": "cefan-studio",
                  "data": [
                    {
                      "id": "554199074863@s.whatsapp.net",
                      "name": "Gabriel"
                    }
                  ],
                  "sender": "554199074863@s.whatsapp.net"
                }
                """;

        mockMvc.perform(post("/api/v1/webhooks/evolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk());

        verify(messageHandlingService, never()).handleIncomingMessage(any());
    }

    @Test
    void shouldHandleMessagesUpdateWithDataArray() throws Exception {
        String jsonPayload = """
                {
                  "event": "messages.update",
                  "instance": "cefan-studio",
                  "data": [
                    {
                      "remoteJid": "554199074863@s.whatsapp.net",
                      "id": "BAE5ABC123456",
                      "status": "DELIVERY_ACK"
                    }
                  ],
                  "sender": "554199074863@s.whatsapp.net"
                }
                """;

        mockMvc.perform(post("/api/v1/webhooks/evolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk());

        verify(messageHandlingService, never()).handleIncomingMessage(any());
    }

    @Test
    void shouldHandleEmptyDataArrayGracefully() throws Exception {
        String jsonPayload = """
                {
                  "event": "contacts.update",
                  "instance": "cefan-studio",
                  "data": []
                }
                """;

        mockMvc.perform(post("/api/v1/webhooks/evolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk());

        verify(messageHandlingService, never()).handleIncomingMessage(any());
    }

    @Test
    void shouldProcessIncomingMessageWebhookWithDataArray() throws Exception {
        String jsonPayload = """
                {
                  "event": "messages.upsert",
                  "instance": "cefan-studio",
                  "data": [
                    {
                      "key": {
                        "remoteJid": "5511999999999@s.whatsapp.net",
                        "fromMe": false
                      },
                      "pushName": "Gabs",
                      "message": {
                        "conversation": "Olá via array"
                      }
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/webhooks/evolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk());

        verify(messageHandlingService).handleIncomingMessage(any());
    }
}
