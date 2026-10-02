package br.com.cefan.whatsapp.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "studio_name", nullable = false)
    private String studioName;

    @Column(name = "absorb_credit_card_fees", nullable = false)
    @Builder.Default
    private Boolean absorbCreditCardFees = false;

    @Column(name = "default_hourly_rate", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal defaultHourlyRate = new BigDecimal("150.00");

    @Column(name = "fixed_setup_cost", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal fixedSetupCost = new BigDecimal("50.00");

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
