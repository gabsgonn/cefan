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
        TenantConfig config = tenantConfigRepository.findAll().stream()
                .findFirst()
                .orElseGet(this::createDefaultConfig);
        return TenantConfigDto.fromEntity(config);
    }

    @Transactional
    public TenantConfigDto updateConfig(TenantConfigDto dto) {
        TenantConfig config = tenantConfigRepository.findAll().stream()
                .findFirst()
                .orElseGet(this::createDefaultConfig);

        config.setStudioName(dto.getStudioName());
        config.setDefaultHourlyRate(dto.getDefaultHourlyRate());
        config.setFixedSetupCost(dto.getFixedSetupCost());
        config.setAbsorbCreditCardFees(dto.getAbsorbCreditCardFees());
        if (dto.getSignalPercentage() != null) {
            config.setSignalPercentage(dto.getSignalPercentage());
        }
        if (dto.getMinDepositAmount() != null) {
            config.setMinDepositAmount(dto.getMinDepositAmount());
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
