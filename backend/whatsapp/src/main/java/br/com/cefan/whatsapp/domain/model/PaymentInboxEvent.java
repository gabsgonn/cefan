package br.com.cefan.whatsapp.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_inbox_events", uniqueConstraints = {
        @UniqueConstraint(name = "uq_payment_inbox_provider_tx", columnNames = {"provider", "transaction_nsu"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentInboxEvent {

    public enum Status {
        PENDING,
        PROCESSED,
        IGNORED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "order_nsu")
    private UUID orderNsu;

    @Column(name = "transaction_nsu", nullable = false, length = 120)
    private String transactionNsu;

    @Column(name = "invoice_slug", length = 120)
    private String invoiceSlug;

    @Column(name = "capture_method", length = 40)
    private String captureMethod;

    @Column(nullable = false, length = 20)
    private String source; // WEBHOOK | REDIRECT

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;
}
