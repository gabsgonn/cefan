package br.com.cefan.whatsapp.application.service;

import br.com.cefan.whatsapp.api.dto.TenantConfigDto;
import br.com.cefan.whatsapp.domain.model.TenantConfig;
import br.com.cefan.whatsapp.domain.repository.TenantConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantConfigService {

    private final TenantConfigRepository tenantConfigRepository;

    @Transactional(readOnly = true)
    public TenantConfigDto getConfig() {
        TenantConfig config = tenantConfigRepository.findFirstByOrderByUpdatedAtDesc()
                .or(() -> tenantConfigRepository.findAll().stream().findFirst())
                .orElseGet(this::createDefaultConfig);
        return TenantConfigDto.fromEntity(config);
    }

    @Transactional
    public TenantConfigDto updateConfig(TenantConfigDto dto) {
        TenantConfig config = tenantConfigRepository.findFirstByOrderByUpdatedAtDesc()
                .or(() -> tenantConfigRepository.findAll().stream().findFirst())
                .orElseGet(this::createDefaultConfig);

        config.setStudioName(dto.getStudioName());
        config.setDefaultHourlyRate(dto.getDefaultHourlyRate());
        config.setFixedSetupCost(dto.getFixedSetupCost());
        config.setAbsorbCreditCardFees(dto.getAbsorbCreditCardFees());
        if (dto.getLogoBase64() != null) {
            config.setLogoBase64(dto.getLogoBase64());
        }
        if (dto.getSignalPercentage() != null) {
            config.setSignalPercentage(dto.getSignalPercentage());
        }
        if (dto.getMinDepositAmount() != null) {
            config.setMinDepositAmount(dto.getMinDepositAmount());
        }
        if (dto.getPixExpirationMinutes() != null && dto.getPixExpirationMinutes() > 0) {
            config.setPixExpirationMinutes(dto.getPixExpirationMinutes());
        }
        if (dto.getOpeningTime() != null && !dto.getOpeningTime().isBlank()) {
            config.setOpeningTime(dto.getOpeningTime().trim());
        }
        if (dto.getClosingTime() != null && !dto.getClosingTime().isBlank()) {
            config.setClosingTime(dto.getClosingTime().trim());
        }
        if (dto.getWorkingDays() != null && !dto.getWorkingDays().isBlank()) {
            config.setWorkingDays(dto.getWorkingDays().trim());
        }
        if (dto.getWelcomeMessage() != null) {
            config.setWelcomeMessage(dto.getWelcomeMessage());
        }
        if (dto.getOrcamentoIntroMessage() != null) {
            config.setOrcamentoIntroMessage(dto.getOrcamentoIntroMessage());
        }
        if (dto.getAgendamentoIntroMessage() != null) {
            config.setAgendamentoIntroMessage(dto.getAgendamentoIntroMessage());
        }
        if (dto.getPixPaymentMessage() != null) {
            config.setPixPaymentMessage(dto.getPixPaymentMessage());
        }
        if (dto.getPixConfirmedMessage() != null) {
            config.setPixConfirmedMessage(dto.getPixConfirmedMessage());
        }
        if (dto.getHumanTransferMessage() != null) {
            config.setHumanTransferMessage(dto.getHumanTransferMessage());
        }
        if (dto.getBotResetMessage() != null) {
            config.setBotResetMessage(dto.getBotResetMessage());
        }
        if (dto.getAbsenceMessage() != null) {
            config.setAbsenceMessage(dto.getAbsenceMessage());
        }
        if (dto.getOrcamentoShowGreeting() != null) {
            config.setOrcamentoShowGreeting(dto.getOrcamentoShowGreeting());
        }
        if (dto.getOrcamentoShowStyle() != null) {
            config.setOrcamentoShowStyle(dto.getOrcamentoShowStyle());
        }
        if (dto.getOrcamentoShowSize() != null) {
            config.setOrcamentoShowSize(dto.getOrcamentoShowSize());
        }
        if (dto.getOrcamentoShowDuration() != null) {
            config.setOrcamentoShowDuration(dto.getOrcamentoShowDuration());
        }
        if (dto.getOrcamentoShowComplexity() != null) {
            config.setOrcamentoShowComplexity(dto.getOrcamentoShowComplexity());
        }
        if (dto.getOrcamentoShowLocation() != null) {
            config.setOrcamentoShowLocation(dto.getOrcamentoShowLocation());
        }
        if (dto.getOrcamentoShowArtists() != null) {
            config.setOrcamentoShowArtists(dto.getOrcamentoShowArtists());
        }
        if (dto.getOrcamentoShowDescription() != null) {
            config.setOrcamentoShowDescription(dto.getOrcamentoShowDescription());
        }
        if (dto.getOrcamentoShowPrice() != null) {
            config.setOrcamentoShowPrice(dto.getOrcamentoShowPrice());
        }
        if (dto.getOrcamentoShowMaterialsNote() != null) {
            config.setOrcamentoShowMaterialsNote(dto.getOrcamentoShowMaterialsNote());
        }
        if (dto.getOrcamentoShowAdjustHint() != null) {
            config.setOrcamentoShowAdjustHint(dto.getOrcamentoShowAdjustHint());
        }

        config = tenantConfigRepository.save(config);
        log.info("Configurações do estúdio [{}] atualizadas com sucesso.", config.getStudioName());
        return TenantConfigDto.fromEntity(config);
    }

    private TenantConfig createDefaultConfig() {
        return tenantConfigRepository.save(TenantConfig.builder()
                .studioName("Vício Tattoo")
                .defaultHourlyRate(new BigDecimal("150.00"))
                .fixedSetupCost(new BigDecimal("50.00"))
                .absorbCreditCardFees(false)
                .build());
    }
}
