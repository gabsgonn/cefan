package br.com.cefan.whatsapp.infrastructure.evolution.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EvolutionWebhookRequestDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeImageMessageWithBufferMediaKey() throws Exception {
        String json = """
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
                        "caption": "Minha tatuagem de referência",
                        "fileSha256": {
                          "type": "Buffer",
                          "data": [10, 20, 30]
                        },
                        "fileLength": "123456",
                        "height": 1080,
                        "width": 1920,
                        "mediaKey": {
                          "type": "Buffer",
                          "data": [1, 2, 3, 4, 5]
                        },
                        "base64": "data:image/jpeg;base64,/9j/4AAQSkZJRg=="
                      }
                    },
                    "messageType": "imageMessage",
                    "messageTimestamp": 1710000000
                  },
                  "sender": "554199074863@s.whatsapp.net"
                }
                """;

        EvolutionWebhookRequest request = objectMapper.readValue(json, EvolutionWebhookRequest.class);

        assertNotNull(request);
        assertEquals("messages.upsert", request.getEvent());
        assertEquals("cefan-studio", request.getInstance());
        assertNotNull(request.getData());
        assertEquals("554199074863", request.extractSenderPhone());
        assertEquals("554199074863@s.whatsapp.net", request.extractRemoteJid());
        assertEquals("BAE5ABC123456", request.extractMessageId());
        assertTrue(request.hasImage());
        assertEquals("Minha tatuagem de referência", request.extractMessageText());
        assertEquals("image/jpeg", request.extractImageMimeType());
        assertEquals("data:image/jpeg;base64,/9j/4AAQSkZJRg==", request.extractBase64());
        assertNotNull(request.getData().getMessage().getImageMessage().getMediaKey());
    }

    @Test
    void shouldDeserializeLongJsTimestampAndBufferHashes() throws Exception {
        String json = """
                {
                  "event": "messages.upsert",
                  "instance": "cefan-studio",
                  "data": {
                    "key": {
                      "remoteJid": "554199074863:2@s.whatsapp.net",
                      "fromMe": false,
                      "id": "BAE5HASH123"
                    },
                    "pushName": {
                      "name": "Gabriel Gonçalves"
                    },
                    "message": {
                      "imageMessage": {
                        "mimetype": "image/jpeg",
                        "caption": "Tattoo leão",
                        "fileSha256": {
                          "type": "Buffer",
                          "data": [1, 2, 3]
                        },
                        "fileEncSha256": {
                          "type": "Buffer",
                          "data": [4, 5, 6]
                        },
                        "mediaKey": {
                          "type": "Buffer",
                          "data": [7, 8, 9]
                        },
                        "jpegThumbnail": {
                          "type": "Buffer",
                          "data": [10, 11]
                        },
                        "fileLength": {
                          "low": 45000,
                          "high": 0,
                          "unsigned": true
                        }
                      }
                    },
                    "messageTimestamp": {
                      "low": 1710500000,
                      "high": 0,
                      "unsigned": true
                    }
                  }
                }
                """;

        EvolutionWebhookRequest request = objectMapper.readValue(json, EvolutionWebhookRequest.class);

        assertNotNull(request);
        assertEquals("554199074863", request.extractSenderPhone());
        assertEquals("554199074863:2@s.whatsapp.net", request.extractRemoteJid());
        assertTrue(request.hasImage());
        assertEquals("Tattoo leão", request.extractMessageText());
        assertEquals(1710500000L, request.getData().getMessageTimestamp());
        assertEquals("Gabriel Gonçalves", request.getData().getPushName());
    }

    @Test
    void shouldDeserializeDocumentImageMessage() throws Exception {
        String json = """
                {
                  "event": "messages.upsert",
                  "instance": "cefan-studio",
                  "data": {
                    "key": {
                      "remoteJid": "554199074863@s.whatsapp.net",
                      "fromMe": false,
                      "id": "DOC123"
                    },
                    "message": {
                      "documentMessage": {
                        "mimetype": "image/png",
                        "fileName": "referencia-tattoo.png",
                        "caption": "Quero no braço",
                        "mediaKey": {
                          "type": "Buffer",
                          "data": [1, 2, 3]
                        },
                        "base64": "ABC123BASE64"
                      }
                    }
                  }
                }
                """;

        EvolutionWebhookRequest request = objectMapper.readValue(json, EvolutionWebhookRequest.class);

        assertNotNull(request);
        assertTrue(request.hasImage());
        assertEquals("image/png", request.extractImageMimeType());
        assertEquals("Quero no braço", request.extractMessageText());
        assertEquals("ABC123BASE64", request.extractBase64());
    }

    @Test
    void shouldDeserializeStringMessageContent() throws Exception {
        String json = """
                {
                  "event": "messages.upsert",
                  "instance": "cefan-studio",
                  "data": {
                    "key": {
                      "remoteJid": "554199074863@s.whatsapp.net",
                      "fromMe": false,
                      "id": "TXT123"
                    },
                    "message": "Quero agendar uma tattoo"
                  }
                }
                """;

        EvolutionWebhookRequest request = objectMapper.readValue(json, EvolutionWebhookRequest.class);

        assertNotNull(request);
        assertEquals("Quero agendar uma tattoo", request.extractMessageText());
        assertFalse(request.hasImage());
    }

    @Test
    void shouldHandleLidAndParticipant() throws Exception {
        String json = """
                {
                  "event": "messages.upsert",
                  "instance": "cefan-studio",
                  "data": {
                    "key": {
                      "remoteJid": "554199074863@lid",
                      "fromMe": "false",
                      "id": "LID123"
                    },
                    "message": {
                      "conversation": "Olá"
                    }
                  }
                }
                """;

        EvolutionWebhookRequest request = objectMapper.readValue(json, EvolutionWebhookRequest.class);

        assertNotNull(request);
        assertEquals("554199074863", request.extractSenderPhone());
        assertFalse(request.isFromMe());
    }

    @Test
    void shouldDeserializeContactsUpdateWithDataAsArray() throws Exception {
        String json = """
                {
                  "event": "contacts.update",
                  "instance": "cefan-studio",
                  "data": [
                    {
                      "id": "554199074863@s.whatsapp.net",
                      "name": "Gabriel",
                      "notify": "Gabs"
                    }
                  ],
                  "sender": "554199074863@s.whatsapp.net"
                }
                """;

        EvolutionWebhookRequest request = objectMapper.readValue(json, EvolutionWebhookRequest.class);

        assertNotNull(request);
        assertEquals("contacts.update", request.getEvent());
        assertEquals("cefan-studio", request.getInstance());
        assertNotNull(request.getData());
    }

    @Test
    void shouldDeserializeMessagesUpdateWithDataAsArray() throws Exception {
        String json = """
                {
                  "event": "messages.update",
                  "instance": "cefan-studio",
                  "data": [
                    {
                      "key": {
                        "remoteJid": "554199074863@s.whatsapp.net",
                        "id": "BAE5ABC123456",
                        "fromMe": true
                      },
                      "status": "DELIVERY_ACK"
                    }
                  ],
                  "sender": "554199074863@s.whatsapp.net"
                }
                """;

        EvolutionWebhookRequest request = objectMapper.readValue(json, EvolutionWebhookRequest.class);

        assertNotNull(request);
        assertEquals("messages.update", request.getEvent());
        assertNotNull(request.getData());
        assertTrue(request.isFromMe());
    }

    @Test
    void shouldDeserializeWebhookWithEmptyDataArray() throws Exception {
        String json = """
                {
                  "event": "contacts.update",
                  "instance": "cefan-studio",
                  "data": []
                }
                """;

        EvolutionWebhookRequest request = objectMapper.readValue(json, EvolutionWebhookRequest.class);

        assertNotNull(request);
        assertEquals("contacts.update", request.getEvent());
        assertNull(request.getData());
    }

    @Test
    void shouldDeserializeWebhookWithSenderAsObject() throws Exception {
        String json = """
                {
                  "event": "messages.upsert",
                  "instance": "cefan-studio",
                  "data": {
                    "message": {
                      "conversation": "Olá"
                    }
                  },
                  "sender": {
                    "id": "554199074863@s.whatsapp.net",
                    "name": "Gabriel"
                  }
                }
                """;

        EvolutionWebhookRequest request = objectMapper.readValue(json, EvolutionWebhookRequest.class);

        assertNotNull(request);
        assertEquals("554199074863", request.extractSenderPhone());
        assertEquals("Olá", request.extractMessageText());
    }

    @Test
    void shouldUnwrapViewOnceImageMessage() throws Exception {
        String json = """
                {
                  "event": "messages.upsert",
                  "instance": "cefan-studio",
                  "data": {
                    "key": {
                      "remoteJid": "554199074863@s.whatsapp.net",
                      "fromMe": false,
                      "id": "VIEWONCE123"
                    },
                    "message": {
                      "viewOnceMessage": {
                        "message": {
                          "imageMessage": {
                            "mimetype": "image/png",
                            "caption": "Tattoo temporária",
                            "mediaKey": {
                              "type": "Buffer",
                              "data": [99, 88]
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """;

        EvolutionWebhookRequest request = objectMapper.readValue(json, EvolutionWebhookRequest.class);

        assertNotNull(request);
        assertTrue(request.hasImage());
        assertEquals("Tattoo temporária", request.extractMessageText());
        assertEquals("image/png", request.extractImageMimeType());
    }

    @Test
    void shouldExtractBase64FromMetadataObject() throws Exception {
        String json = """
                {
                  "event": "messages.upsert",
                  "instance": "cefan-studio",
                  "data": {
                    "key": {
                      "remoteJid": "554199074863@s.whatsapp.net",
                      "fromMe": false,
                      "id": "IMG123"
                    },
                    "message": {
                      "imageMessage": {
                        "mimetype": "image/jpeg",
                        "mediaKey": "direct-string-key"
                      }
                    },
                    "base64": {
                      "base64": "ABCDEF1234567890",
                      "mimetype": "image/jpeg"
                    }
                  }
                }
                """;

        EvolutionWebhookRequest request = objectMapper.readValue(json, EvolutionWebhookRequest.class);

        assertNotNull(request);
        assertTrue(request.hasImage());
        assertEquals("ABCDEF1234567890", request.extractBase64());
    }
}
