package br.com.cefan.whatsapp.api.dto;

import br.com.cefan.whatsapp.domain.model.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {

    private UUID id;
    private UUID sessionId;
    private UUID customerId;
    private String senderType; // CUSTOMER, BOT, AGENT
    private UUID senderId;
    private String senderName;
    private String content;
    private String mediaUrl;
    private String mediaType;
    private Boolean isFromMe;
    private OffsetDateTime createdAt;

    public static ChatMessageDto fromEntity(ChatMessage entity) {
        return ChatMessageDto.builder()
                .id(entity.getId())
                .sessionId(entity.getSession().getId())
                .customerId(entity.getCustomer().getId())
                .senderType(entity.getSenderType().name())
                .senderId(entity.getSenderUser() != null ? entity.getSenderUser().getId() : null)
                .senderName(entity.getSenderName())
                .content(entity.getContent())
                .mediaUrl(entity.getMediaUrl())
                .mediaType(entity.getMediaType())
                .isFromMe(entity.getIsFromMe())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
