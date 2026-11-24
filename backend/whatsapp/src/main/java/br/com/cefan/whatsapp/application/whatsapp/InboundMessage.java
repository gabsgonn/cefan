package br.com.cefan.whatsapp.application.whatsapp;

import lombok.Builder;
import lombok.Value;

/**
 * Evento de entrada neutro recebido de qualquer provedor WhatsApp (Evolution ou Meta Cloud).
 * Handlers e máquina de estados consomem apenas este contrato, sem depender de DTOs da Evolution.
 */
@Value
@Builder(toBuilder = true)
public class InboundMessage {

    public enum Type {
        TEXT,
        MEDIA,
        INTERACTIVE_REPLY,
        POLL_VOTE,
        STATUS
    }

    public record Referral(
            String sourceType,
            String sourceId,
            String headline,
            String body,
            String ctwaClid
    ) {}

    String messageId;
    String phone;
    String senderName;
    Type type;
    String text;
    String interactiveReplyId;
    String interactiveReplyTitle;
    String mediaReference;
    String mediaMimeType;
    String mediaBase64;
    boolean fromMe;
    Referral referral;

    public boolean hasMedia() {
        return type == Type.MEDIA
                || (mediaBase64 != null && !mediaBase64.isBlank())
                || (mediaReference != null && !mediaReference.isBlank());
    }
}
