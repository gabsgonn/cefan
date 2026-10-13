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
        private String base64;
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
        private ImageMessage imageMessage;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtendedTextMessage {
        private String text;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImageMessage {
        private String caption;
        private String mimetype;
        private String url;
        private String mediaKey;
        private String base64;
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
        if (data.message.imageMessage != null && data.message.imageMessage.caption != null) {
            return data.message.imageMessage.caption;
        }
        return null;
    }

    public boolean hasImage() {
        return (data != null && data.message != null && data.message.imageMessage != null) ||
               (data != null && "imageMessage".equalsIgnoreCase(data.messageType));
    }

    public ImageMessage getImageMessage() {
        return (data != null && data.message != null) ? data.message.imageMessage : null;
    }

    public String extractMessageId() {
        return (data != null && data.key != null) ? data.key.id : null;
    }

    public String extractImageMimeType() {
        if (data != null && data.message != null && data.message.imageMessage != null && data.message.imageMessage.mimetype != null) {
            return data.message.imageMessage.mimetype;
        }
        return "image/jpeg";
    }

    public String extractBase64() {
        if (data != null && data.message != null && data.message.imageMessage != null && data.message.imageMessage.base64 != null) {
            return data.message.imageMessage.base64;
        }
        if (data != null && data.base64 != null) {
            return data.base64;
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
