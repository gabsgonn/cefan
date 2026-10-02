package br.com.cefan.whatsapp.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "user_notification_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotificationPreference {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "notify_new_lead", nullable = false)
    @Builder.Default
    private Boolean notifyNewLead = true;

    @Column(name = "notify_payment_received", nullable = false)
    @Builder.Default
    private Boolean notifyPaymentReceived = true;

    @Column(name = "notify_human_request", nullable = false)
    @Builder.Default
    private Boolean notifyHumanRequest = true;
}
