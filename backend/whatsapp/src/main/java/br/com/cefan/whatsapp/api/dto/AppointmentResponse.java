package br.com.cefan.whatsapp.api.dto;

import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentResponse {

    private UUID id;
    private UUID customerId;
    private String customerName;
    private String customerPhone;
    private OffsetDateTime scheduledStart;
    private OffsetDateTime scheduledEnd;
    private AppointmentStatus status;
    private Boolean isTouchup;
    private BigDecimal totalPrice;
    private BigDecimal depositAmount;
    private String mpPaymentId;
    private String mpPixCopyPaste;
    private String mpPaymentLink;
    private OffsetDateTime expiresAt;
    private Boolean reminder24hSent;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static AppointmentResponse fromEntity(Appointment appointment) {
        return AppointmentResponse.builder()
                .id(appointment.getId())
                .customerId(appointment.getCustomer() != null ? appointment.getCustomer().getId() : null)
                .customerName(appointment.getCustomer() != null ? appointment.getCustomer().getName() : "Cliente")
                .customerPhone(appointment.getCustomer() != null ? appointment.getCustomer().getPhone() : "")
                .scheduledStart(appointment.getScheduledStart())
                .scheduledEnd(appointment.getScheduledEnd())
                .status(appointment.getStatus())
                .isTouchup(appointment.getIsTouchup())
                .totalPrice(appointment.getTotalPrice())
                .depositAmount(appointment.getDepositAmount())
                .mpPaymentId(appointment.getMpPaymentId())
                .mpPixCopyPaste(appointment.getMpPixCopyPaste())
                .mpPaymentLink(appointment.getMpPaymentLink())
                .expiresAt(appointment.getExpiresAt())
                .reminder24hSent(appointment.getReminder24hSent())
                .createdAt(appointment.getCreatedAt())
                .updatedAt(appointment.getUpdatedAt())
                .build();
    }
}
