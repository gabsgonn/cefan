package br.com.cefan.whatsapp.infrastructure.evolution.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.Data;

import java.io.IOException;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvolutionWebhookRequest {

    private static final ObjectMapper INTERNAL_MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .findAndAddModules()
            .build();

    private Object event;
    private Object instance;
    private Object data;

    public void setData(Object data) {
        this.data = data;
    }

    public WebhookData getData() {
        if (data == null) return null;
        if (data instanceof WebhookData wd) return wd;
        return parseWebhookDataFromObject(data);
    }

    private static WebhookData parseWebhookDataFromObject(Object obj) {
        if (obj == null) return null;
        if (obj instanceof WebhookData wd) return wd;
        Object target = obj;
        if (obj instanceof java.util.List<?> list) {
            if (list.isEmpty()) return null;
            target = list.get(0);
        }
        if (target instanceof Map<?, ?> map) {
            try {
                return INTERNAL_MAPPER.convertValue(map, WebhookData.class);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private Object sender;
    private Object serverUrl;
    private Object destination;
    private Object apikey;
    private Object base64;

    public String getEvent() {
        return event != null ? event.toString() : null;
    }

    public String getInstance() {
        return instance != null ? instance.toString() : null;
    }

    public static class WebhookDataDeserializer extends JsonDeserializer<WebhookData> {
        @Override
        public WebhookData deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            if (node == null || node.isNull()) {
                return null;
            }
            JsonNode targetNode = node;
            if (node.isArray()) {
                if (node.isEmpty()) {
                    return null;
                }
                targetNode = node.get(0);
            }
            if (targetNode != null && targetNode.isObject()) {
                try {
                    return INTERNAL_MAPPER.treeToValue(targetNode, WebhookData.class);
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        }
    }

    public static class MessageContentDeserializer extends JsonDeserializer<MessageContent> {
        @Override
        public MessageContent deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isTextual()) {
                MessageContent content = new MessageContent();
                content.setConversation(node.asText());
                return content;
            }
            if (node.isObject()) {
                try {
                    return INTERNAL_MAPPER.treeToValue(node, MessageContent.class);
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookData {
        private Object key;
        private Object pushName;
        private Object message;
        private Object messageType;
        private Object messageTimestamp;
        private Object base64;
        private Object id;
        private Object messageId;

        public void setKey(Object key) {
            this.key = key;
        }

        public MessageKey getKey() {
            if (key == null) return null;
            if (key instanceof MessageKey mk) return mk;
            if (key instanceof Map<?, ?> map) {
                try {
                    return INTERNAL_MAPPER.convertValue(map, MessageKey.class);
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        }

        public void setMessage(Object message) {
            this.message = message;
        }

        public MessageContent getMessage() {
            return unwrapContent(message);
        }

        public String getPushName() {
            if (pushName == null) return null;
            if (pushName instanceof String s) return s;
            if (pushName instanceof Map<?, ?> m && m.containsKey("name")) {
                Object n = m.get("name");
                return n != null ? n.toString() : null;
            }
            return pushName.toString();
        }

        public String getMessageType() {
            return messageType != null ? messageType.toString() : null;
        }

        public Long getMessageTimestamp() {
            if (messageTimestamp == null) return null;
            if (messageTimestamp instanceof Number num) return num.longValue();
            if (messageTimestamp instanceof Map<?, ?> map && map.containsKey("low")) {
                Object low = map.get("low");
                if (low instanceof Number n) return n.longValue();
            }
            try {
                return Long.parseLong(messageTimestamp.toString());
            } catch (Exception e) {
                return null;
            }
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageKey {
        private Object remoteJid;
        private Object fromMe;
        private Object id;
        private Object participant;

        public String getRemoteJid() {
            return remoteJid != null ? remoteJid.toString() : null;
        }

        public String getId() {
            return id != null ? id.toString() : null;
        }

        public String getParticipant() {
            return participant != null ? participant.toString() : null;
        }

        public Boolean getFromMe() {
            if (fromMe == null) return false;
            if (fromMe instanceof Boolean b) return b;
            if (fromMe instanceof Number n) return n.intValue() == 1;
            return Boolean.parseBoolean(fromMe.toString());
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageContent {
        private Object conversation;
        private ExtendedTextMessage extendedTextMessage;
        private ImageMessage imageMessage;
        private DocumentMessage documentMessage;
        private ViewOnceMessage viewOnceMessage;
        private ViewOnceMessage viewOnceMessageV2;
        private ViewOnceMessage viewOnceMessageV2Extension;
        private EphemeralMessage ephemeralMessage;
        private DocumentWithCaptionMessage documentWithCaptionMessage;
        private Object text;
        private Object caption;
        private Object base64;

        public String getConversation() {
            if (conversation == null) return null;
            if (conversation instanceof String s) return s;
            if (conversation instanceof Map<?, ?> m && m.containsKey("text")) {
                Object t = m.get("text");
                return t != null ? t.toString() : null;
            }
            return conversation.toString();
        }
    }

    private static MessageContent unwrapContent(Object message) {
        if (message == null) return null;
        if (message instanceof MessageContent mc) return mc;
        if (message instanceof String s) {
            MessageContent content = new MessageContent();
            content.setConversation(s);
            return content;
        }
        Object target = message;
        if (message instanceof java.util.List<?> list) {
            if (list.isEmpty()) return null;
            target = list.get(0);
        }
        if (target instanceof Map<?, ?> map) {
            try {
                return INTERNAL_MAPPER.convertValue(map, MessageContent.class);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ViewOnceMessage {
        private Object message;

        public void setMessage(Object message) {
            this.message = message;
        }

        public MessageContent getMessage() {
            return unwrapContent(message);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ViewOnceMessageV2 {
        private Object message;

        public void setMessage(Object message) {
            this.message = message;
        }

        public MessageContent getMessage() {
            return unwrapContent(message);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EphemeralMessage {
        private Object message;

        public void setMessage(Object message) {
            this.message = message;
        }

        public MessageContent getMessage() {
            return unwrapContent(message);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DocumentWithCaptionMessage {
        private Object message;

        public void setMessage(Object message) {
            this.message = message;
        }

        public MessageContent getMessage() {
            return unwrapContent(message);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtendedTextMessage {
        private Object text;
        private Object description;
        private Object title;

        public ExtendedTextMessage() {}

        public ExtendedTextMessage(String text) {
            this.text = text;
        }

        public String getText() {
            if (text == null) return null;
            if (text instanceof String s) return s;
            if (text instanceof Map<?, ?> m && m.containsKey("text")) {
                Object t = m.get("text");
                return t != null ? t.toString() : null;
            }
            return text.toString();
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DocumentMessage {
        private Object caption;
        private Object mimetype;
        private Object title;
        private Object fileName;
        private Object url;
        private Object mediaKey;
        private Object base64;
        private Object fileSha256;
        private Object fileEncSha256;
        private Object directPath;
        private Object fileLength;

        public String getCaption() {
            if (caption != null && !caption.toString().isBlank()) return caption.toString();
            if (title != null && !title.toString().isBlank()) return title.toString();
            if (fileName != null && !fileName.toString().isBlank()) return fileName.toString();
            return null;
        }

        public String getMimetype() {
            return mimetype != null ? mimetype.toString() : null;
        }

        public String getUrl() {
            return url != null ? url.toString() : null;
        }

        public String getDirectPath() {
            return directPath != null ? directPath.toString() : null;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImageMessage {
        private Object caption;
        private Object mimetype;
        private Object url;
        private Object mediaKey;
        private Object base64;
        private Object fileSha256;
        private Object fileEncSha256;
        private Object directPath;
        private Object fileLength;
        private Object height;
        private Object width;
        private Object mediaKeyTimestamp;
        private Object jpegThumbnail;

        public String getCaption() {
            if (caption == null) return null;
            if (caption instanceof String s) return s;
            if (caption instanceof Map<?, ?> m && m.containsKey("text")) {
                Object t = m.get("text");
                return t != null ? t.toString() : null;
            }
            return caption.toString();
        }

        public String getMimetype() {
            return mimetype != null ? mimetype.toString() : null;
        }

        public String getUrl() {
            return url != null ? url.toString() : null;
        }

        public String getDirectPath() {
            return directPath != null ? directPath.toString() : null;
        }
    }

    public String extractMessageText() {
        WebhookData d = getData();
        if (d == null || d.getMessage() == null) {
            return null;
        }
        return extractTextFromContent(d.getMessage());
    }

    private static String extractTextFromContent(MessageContent content) {
        if (content == null) {
            return null;
        }
        String conv = content.getConversation();
        if (conv != null && !conv.isBlank()) {
            return conv;
        }
        if (content.getExtendedTextMessage() != null && content.getExtendedTextMessage().getText() != null
                && !content.getExtendedTextMessage().getText().isBlank()) {
            return content.getExtendedTextMessage().getText();
        }
        if (content.getText() != null && !content.getText().toString().isBlank()) {
            return content.getText().toString();
        }
        ImageMessage img = unwrapImageMessage(content);
        if (img != null && img.getCaption() != null && !img.getCaption().isBlank()) {
            return img.getCaption();
        }
        if (content.getCaption() != null && !content.getCaption().toString().isBlank()) {
            return content.getCaption().toString();
        }
        if (content.getViewOnceMessage() != null && content.getViewOnceMessage().getMessage() != null) {
            String txt = extractTextFromContent(content.getViewOnceMessage().getMessage());
            if (txt != null && !txt.isBlank()) return txt;
        }
        if (content.getViewOnceMessageV2() != null && content.getViewOnceMessageV2().getMessage() != null) {
            String txt = extractTextFromContent(content.getViewOnceMessageV2().getMessage());
            if (txt != null && !txt.isBlank()) return txt;
        }
        if (content.getViewOnceMessageV2Extension() != null && content.getViewOnceMessageV2Extension().getMessage() != null) {
            String txt = extractTextFromContent(content.getViewOnceMessageV2Extension().getMessage());
            if (txt != null && !txt.isBlank()) return txt;
        }
        if (content.getEphemeralMessage() != null && content.getEphemeralMessage().getMessage() != null) {
            String txt = extractTextFromContent(content.getEphemeralMessage().getMessage());
            if (txt != null && !txt.isBlank()) return txt;
        }
        if (content.getDocumentWithCaptionMessage() != null && content.getDocumentWithCaptionMessage().getMessage() != null) {
            String txt = extractTextFromContent(content.getDocumentWithCaptionMessage().getMessage());
            if (txt != null && !txt.isBlank()) return txt;
        }
        return null;
    }

    public boolean hasImage() {
        if (getImageMessage() != null) {
            return true;
        }
        WebhookData d = getData();
        if (d != null && d.getMessageType() != null) {
            String type = d.getMessageType().toLowerCase();
            return type.contains("image");
        }
        return false;
    }

    public ImageMessage getImageMessage() {
        WebhookData d = getData();
        if (d == null || d.getMessage() == null) {
            return null;
        }
        return unwrapImageMessage(d.getMessage());
    }

    private static ImageMessage unwrapImageMessage(MessageContent content) {
        if (content == null) {
            return null;
        }
        if (content.getImageMessage() != null) {
            return content.getImageMessage();
        }
        if (content.getDocumentMessage() != null && isImageMimeType(content.getDocumentMessage().getMimetype())) {
            return convertDocumentToImage(content.getDocumentMessage());
        }
        if (content.getViewOnceMessage() != null && content.getViewOnceMessage().getMessage() != null) {
            ImageMessage img = unwrapImageMessage(content.getViewOnceMessage().getMessage());
            if (img != null) return img;
        }
        if (content.getViewOnceMessageV2() != null && content.getViewOnceMessageV2().getMessage() != null) {
            ImageMessage img = unwrapImageMessage(content.getViewOnceMessageV2().getMessage());
            if (img != null) return img;
        }
        if (content.getViewOnceMessageV2Extension() != null && content.getViewOnceMessageV2Extension().getMessage() != null) {
            ImageMessage img = unwrapImageMessage(content.getViewOnceMessageV2Extension().getMessage());
            if (img != null) return img;
        }
        if (content.getEphemeralMessage() != null && content.getEphemeralMessage().getMessage() != null) {
            ImageMessage img = unwrapImageMessage(content.getEphemeralMessage().getMessage());
            if (img != null) return img;
        }
        if (content.getDocumentWithCaptionMessage() != null && content.getDocumentWithCaptionMessage().getMessage() != null) {
            ImageMessage img = unwrapImageMessage(content.getDocumentWithCaptionMessage().getMessage());
            if (img != null) return img;
        }
        return null;
    }

    private static boolean isImageMimeType(String mime) {
        if (mime == null) return false;
        String m = mime.toLowerCase();
        return m.startsWith("image/") || m.contains("jpg") || m.contains("jpeg") || m.contains("png") || m.contains("webp");
    }

    private static ImageMessage convertDocumentToImage(DocumentMessage doc) {
        ImageMessage img = new ImageMessage();
        img.setCaption(doc.getCaption());
        img.setMimetype(doc.getMimetype());
        img.setUrl(doc.getUrl());
        img.setMediaKey(doc.getMediaKey());
        img.setBase64(doc.getBase64());
        img.setFileSha256(doc.getFileSha256());
        img.setFileEncSha256(doc.getFileEncSha256());
        img.setDirectPath(doc.getDirectPath());
        return img;
    }

    public String extractMessageId() {
        WebhookData d = getData();
        if (d != null) {
            if (d.getKey() != null && d.getKey().getId() != null) {
                return d.getKey().getId();
            }
            if (d.getId() != null) {
                return d.getId().toString();
            }
            if (d.getMessageId() != null) {
                return d.getMessageId().toString();
            }
        }
        return null;
    }

    public String extractRemoteJid() {
        WebhookData d = getData();
        if (d != null && d.getKey() != null && d.getKey().getRemoteJid() != null) {
            return d.getKey().getRemoteJid();
        }
        String phone = extractSenderPhone();
        return (phone != null && !phone.isBlank()) ? phone + "@s.whatsapp.net" : null;
    }

    public String extractImageMimeType() {
        ImageMessage img = getImageMessage();
        if (img != null && img.getMimetype() != null && !img.getMimetype().isBlank()) {
            return img.getMimetype();
        }
        return "image/jpeg";
    }

    public String extractBase64() {
        ImageMessage img = getImageMessage();
        if (img != null && img.getBase64() != null) {
            String resolved = resolveBase64(img.getBase64());
            if (resolved != null && !resolved.isBlank()) {
                return resolved;
            }
        }
        WebhookData d = getData();
        if (d != null) {
            if (d.getBase64() != null) {
                String resolved = resolveBase64(d.getBase64());
                if (resolved != null && !resolved.isBlank()) {
                    return resolved;
                }
            }
            if (d.getMessage() != null && d.getMessage().getBase64() != null) {
                String resolved = resolveBase64(d.getMessage().getBase64());
                if (resolved != null && !resolved.isBlank()) {
                    return resolved;
                }
            }
        }
        if (this.base64 != null) {
            String resolved = resolveBase64(this.base64);
            if (resolved != null && !resolved.isBlank()) {
                return resolved;
            }
        }
        return null;
    }

    private static String resolveBase64(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof String str) {
            String s = str.trim();
            return s.isBlank() ? null : s;
        }
        if (obj instanceof Map<?, ?> map) {
            if (map.containsKey("base64")) {
                Object b64Obj = map.get("base64");
                if (b64Obj != null) return b64Obj.toString().trim();
            }
            if (map.containsKey("data")) {
                Object dObj = map.get("data");
                if (dObj != null) return dObj.toString().trim();
            }
            return null;
        }
        return null;
    }

    public String extractSenderPhone() {
        WebhookData d = getData();
        if (d != null && d.getKey() != null && d.getKey().getRemoteJid() != null) {
            return cleanPhone(d.getKey().getRemoteJid());
        }
        if (sender != null) {
            if (sender instanceof String str) {
                return cleanPhone(str);
            }
            if (sender instanceof Map<?, ?> map) {
                Object id = map.get("id");
                if (id == null) id = map.get("remoteJid");
                if (id == null) id = map.get("jid");
                if (id != null) {
                    return cleanPhone(id.toString());
                }
            }
            return cleanPhone(sender.toString());
        }
        return null;
    }

    private static String cleanPhone(String raw) {
        if (raw == null || raw.isBlank()) return null;
        // Strip out @s.whatsapp.net, @g.us, @lid
        String jid = raw.split("@")[0];
        // Strip out device ID (e.g., 554199074863:1 -> 554199074863)
        String phone = jid.split(":")[0];
        // Extract digits only
        String digits = phone.replaceAll("[^0-9]", "");
        return digits.isBlank() ? null : digits;
    }

    public boolean isFromMe() {
        WebhookData d = getData();
        return d != null && d.getKey() != null && Boolean.TRUE.equals(d.getKey().getFromMe());
    }
}
