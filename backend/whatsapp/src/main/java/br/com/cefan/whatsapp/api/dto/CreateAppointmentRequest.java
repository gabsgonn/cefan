package br.com.cefan.whatsapp.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateAppointmentRequest {

    @NotBlank(message = "O nome do cliente é obrigatório")
    private String customerName;

    @NotBlank(message = "O telefone do cliente é obrigatório")
    private String customerPhone;

    @NotNull(message = "O horário inicial é obrigatório")
    private OffsetDateTime scheduledStart;

    @NotNull(message = "O horário final é obrigatório")
    private OffsetDateTime scheduledEnd;

    @Builder.Default
    private Boolean isTouchup = false;

    private BigDecimal totalPrice;
    private BigDecimal depositAmount;
}
