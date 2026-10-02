package br.com.cefan.whatsapp.infrastructure.evolution.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvolutionWebhookRequest {
    private String event;
    private String instance;
    private WebhookData data;
    private String sender;
    private String serverUrl;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookData {
        private MessageKey key;
        private String pushName;
        private MessageContent message;
        private String messageType;
        private Long messageTimestamp;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageKey {
        private String remoteJid;
        private Boolean fromMe;
        private String id;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageContent {
        private String conversation;
        private ExtendedTextMessage extendedTextMessage;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtendedTextMessage {
        private String text;
    }

    public String extractMessageText() {
        if (data == null || data.message == null) {
            return null;
        }
        if (data.message.conversation != null && !data.message.conversation.isBlank()) {
            return data.message.conversation;
        }
        if (data.message.extendedTextMessage != null && data.message.extendedTextMessage.text != null) {
            return data.message.extendedTextMessage.text;
        }
        return null;
    }

    public String extractSenderPhone() {
        if (data != null && data.key != null && data.key.remoteJid != null) {
            // Remove sufixos como @s.whatsapp.net ou @g.us
            return data.key.remoteJid.split("@")[0];
        }
        if (sender != null) {
            return sender.split("@")[0];
        }
        return null;
    }

    public boolean isFromMe() {
        return data != null && data.key != null && Boolean.TRUE.equals(data.key.fromMe);
    }
}
