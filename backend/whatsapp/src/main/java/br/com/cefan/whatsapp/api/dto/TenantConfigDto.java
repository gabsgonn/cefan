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

    private BigDecimal signalPercentage;

    private BigDecimal minDepositAmount;

    private String welcomeMessage;

    private String orcamentoIntroMessage;

    private String agendamentoIntroMessage;

    private String pixPaymentMessage;

    private String pixConfirmedMessage;

    private String humanTransferMessage;

    private String botResetMessage;

    public static TenantConfigDto fromEntity(TenantConfig entity) {
        return TenantConfigDto.builder()
                .id(entity.getId())
                .studioName(entity.getStudioName())
                .defaultHourlyRate(entity.getDefaultHourlyRate())
                .fixedSetupCost(entity.getFixedSetupCost())
                .absorbCreditCardFees(entity.getAbsorbCreditCardFees())
                .signalPercentage(entity.getSignalPercentage() != null ? entity.getSignalPercentage() : new BigDecimal("30.00"))
                .minDepositAmount(entity.getMinDepositAmount() != null ? entity.getMinDepositAmount() : new BigDecimal("50.00"))
                .welcomeMessage(entity.getWelcomeMessage())
                .orcamentoIntroMessage(entity.getOrcamentoIntroMessage())
                .agendamentoIntroMessage(entity.getAgendamentoIntroMessage())
                .pixPaymentMessage(entity.getPixPaymentMessage())
                .pixConfirmedMessage(entity.getPixConfirmedMessage())
                .humanTransferMessage(entity.getHumanTransferMessage())
                .botResetMessage(entity.getBotResetMessage())
                .build();
    }
}
