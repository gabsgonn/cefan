package br.com.cefan.whatsapp.api.dto;

import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAppointmentRequest {

    private OffsetDateTime scheduledStart;
    private OffsetDateTime scheduledEnd;
    private AppointmentStatus status;
    private Boolean isTouchup;
    private BigDecimal newPrice;
    private String reasonForChange;
}
