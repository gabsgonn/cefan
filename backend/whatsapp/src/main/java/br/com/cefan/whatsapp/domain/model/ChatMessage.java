package br.com.cefan.whatsapp.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ConversationSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 20)
    private SenderType senderType; // CUSTOMER, BOT, AGENT

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User senderUser;

    @Column(name = "sender_name", nullable = false, length = 100)
    private String senderName;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "media_url", columnDefinition = "TEXT")
    private String mediaUrl;

    @Column(name = "media_type", length = 50)
    private String mediaType;

    @Column(name = "is_from_me", nullable = false)
    @Builder.Default
    private Boolean isFromMe = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public enum SenderType {
        CUSTOMER,
        BOT,
        AGENT
    }
}
