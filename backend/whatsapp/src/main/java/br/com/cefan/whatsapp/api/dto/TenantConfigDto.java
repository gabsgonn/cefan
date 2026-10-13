package br.com.cefan.whatsapp.api.dto;

import br.com.cefan.whatsapp.domain.model.TenantConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantConfigDto {

    private UUID id;

    @NotBlank(message = "O nome do estúdio é obrigatório")
    private String studioName;

    @NotNull(message = "O valor padrão por hora é obrigatório")
    private BigDecimal defaultHourlyRate;

    @NotNull(message = "O custo fixo de montagem é obrigatório")
    private BigDecimal fixedSetupCost;

    @NotNull(message = "A definição de absorção de taxas é obrigatória")
    private Boolean absorbCreditCardFees;

    public static TenantConfigDto fromEntity(TenantConfig entity) {
        return TenantConfigDto.builder()
                .id(entity.getId())
                .studioName(entity.getStudioName())
                .defaultHourlyRate(entity.getDefaultHourlyRate())
                .fixedSetupCost(entity.getFixedSetupCost())
                .absorbCreditCardFees(entity.getAbsorbCreditCardFees())
                .build();
    }
}
