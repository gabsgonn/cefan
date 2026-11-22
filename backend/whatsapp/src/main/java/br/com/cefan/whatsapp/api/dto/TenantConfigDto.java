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

    private String logoBase64;

    @NotNull(message = "O valor padrão por hora é obrigatório")
    private BigDecimal defaultHourlyRate;

    @NotNull(message = "O custo fixo de montagem é obrigatório")
    private BigDecimal fixedSetupCost;

    @NotNull(message = "A definição de absorção de taxas é obrigatória")
    private Boolean absorbCreditCardFees;

    private BigDecimal signalPercentage;

    private BigDecimal minDepositAmount;

    private Integer pixExpirationMinutes;

    private String openingTime;

    private String closingTime;

    private String workingDays;

    private String welcomeMessage;

    private String orcamentoIntroMessage;

    private String agendamentoIntroMessage;

    private String pixPaymentMessage;

    private String pixConfirmedMessage;

    private String humanTransferMessage;

    private String botResetMessage;

    private String absenceMessage;

    @NotNull(message = "A configuração de exibição de saudação do orçamento é obrigatória")
    @Builder.Default
    private Boolean orcamentoShowGreeting = true;

    @NotNull(message = "A configuração de exibição de estilo do orçamento é obrigatória")
    @Builder.Default
    private Boolean orcamentoShowStyle = true;

    @NotNull(message = "A configuração de exibição de tamanho do orçamento é obrigatória")
    @Builder.Default
    private Boolean orcamentoShowSize = true;

    @NotNull(message = "A configuração de exibição de duração do orçamento é obrigatória")
    @Builder.Default
    private Boolean orcamentoShowDuration = true;

    @NotNull(message = "A configuração de exibição de complexidade do orçamento é obrigatória")
    @Builder.Default
    private Boolean orcamentoShowComplexity = true;

    @NotNull(message = "A configuração de exibição de local do corpo do orçamento é obrigatória")
    @Builder.Default
    private Boolean orcamentoShowLocation = true;

    @NotNull(message = "A configuração de exibição de artistas do orçamento é obrigatória")
    @Builder.Default
    private Boolean orcamentoShowArtists = true;

    @NotNull(message = "A configuração de exibição de descrição do orçamento é obrigatória")
    @Builder.Default
    private Boolean orcamentoShowDescription = true;

    @NotNull(message = "A configuração de exibição de preço do orçamento é obrigatória")
    @Builder.Default
    private Boolean orcamentoShowPrice = true;

    @NotNull(message = "A configuração de exibição de nota de materiais do orçamento é obrigatória")
    @Builder.Default
    private Boolean orcamentoShowMaterialsNote = true;

    @NotNull(message = "A configuração de exibição de dica de ajuste do orçamento é obrigatória")
    @Builder.Default
    private Boolean orcamentoShowAdjustHint = true;

    public static TenantConfigDto fromEntity(TenantConfig entity) {
        return TenantConfigDto.builder()
                .id(entity.getId())
                .studioName(entity.getStudioName())
                .logoBase64(entity.getLogoBase64())
                .defaultHourlyRate(entity.getDefaultHourlyRate())
                .fixedSetupCost(entity.getFixedSetupCost())
                .absorbCreditCardFees(entity.getAbsorbCreditCardFees())
                .signalPercentage(entity.getSignalPercentage() != null ? entity.getSignalPercentage() : new BigDecimal("30.00"))
                .minDepositAmount(entity.getMinDepositAmount() != null ? entity.getMinDepositAmount() : new BigDecimal("50.00"))
                .pixExpirationMinutes(entity.getPixExpirationMinutes() != null ? entity.getPixExpirationMinutes() : 120)
                .openingTime(entity.getOpeningTime() != null ? entity.getOpeningTime() : "10:00")
                .closingTime(entity.getClosingTime() != null ? entity.getClosingTime() : "20:00")
                .workingDays(entity.getWorkingDays() != null ? entity.getWorkingDays() : "SEG-SAB")
                .welcomeMessage(entity.getWelcomeMessage())
                .orcamentoIntroMessage(entity.getOrcamentoIntroMessage())
                .agendamentoIntroMessage(entity.getAgendamentoIntroMessage())
                .pixPaymentMessage(entity.getPixPaymentMessage())
                .pixConfirmedMessage(entity.getPixConfirmedMessage())
                .humanTransferMessage(entity.getHumanTransferMessage())
                .botResetMessage(entity.getBotResetMessage())
                .absenceMessage(entity.getAbsenceMessage())
                .orcamentoShowGreeting(entity.getOrcamentoShowGreeting() == null || entity.getOrcamentoShowGreeting())
                .orcamentoShowStyle(entity.getOrcamentoShowStyle() == null || entity.getOrcamentoShowStyle())
                .orcamentoShowSize(entity.getOrcamentoShowSize() == null || entity.getOrcamentoShowSize())
                .orcamentoShowDuration(entity.getOrcamentoShowDuration() == null || entity.getOrcamentoShowDuration())
                .orcamentoShowComplexity(entity.getOrcamentoShowComplexity() == null || entity.getOrcamentoShowComplexity())
                .orcamentoShowLocation(entity.getOrcamentoShowLocation() == null || entity.getOrcamentoShowLocation())
                .orcamentoShowArtists(entity.getOrcamentoShowArtists() == null || entity.getOrcamentoShowArtists())
                .orcamentoShowDescription(entity.getOrcamentoShowDescription() == null || entity.getOrcamentoShowDescription())
                .orcamentoShowPrice(entity.getOrcamentoShowPrice() == null || entity.getOrcamentoShowPrice())
                .orcamentoShowMaterialsNote(entity.getOrcamentoShowMaterialsNote() == null || entity.getOrcamentoShowMaterialsNote())
                .orcamentoShowAdjustHint(entity.getOrcamentoShowAdjustHint() == null || entity.getOrcamentoShowAdjustHint())
                .build();
    }
}
