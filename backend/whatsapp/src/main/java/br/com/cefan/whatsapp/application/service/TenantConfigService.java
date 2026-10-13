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
