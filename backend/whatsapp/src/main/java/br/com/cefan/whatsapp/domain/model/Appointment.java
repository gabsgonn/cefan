package br.com.cefan.whatsapp.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id")
    private User artist;

    @Column(name = "scheduled_start", nullable = false)
    private OffsetDateTime scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private OffsetDateTime scheduledEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private AppointmentStatus status = AppointmentStatus.AGUARDANDO_PAGAMENTO;

    @Column(name = "is_touchup", nullable = false)
    @Builder.Default
    private Boolean isTouchup = false;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalPrice = BigDecimal.ZERO;

    @Column(name = "deposit_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal depositAmount = BigDecimal.ZERO;

    @Column(name = "mp_payment_id")
    private String mpPaymentId;

    @Column(name = "mp_payment_link", columnDefinition = "TEXT")
    private String mpPaymentLink;

    @Column(name = "mp_pix_copy_paste", columnDefinition = "TEXT")
    private String mpPixCopyPaste;

    @Column(name = "mp_pix_qr_code_base64", columnDefinition = "TEXT")
    private String mpPixQrCodeBase64;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "reminder_24h_sent", nullable = false)
    @Builder.Default
    private Boolean reminder24hSent = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
