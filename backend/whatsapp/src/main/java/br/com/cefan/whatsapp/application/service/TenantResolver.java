package br.com.cefan.whatsapp.application.service;

import br.com.cefan.whatsapp.domain.model.TenantConfig;
import br.com.cefan.whatsapp.domain.repository.TenantConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Resolve o tenant de forma determinística (sem {@code findAll().findFirst()} sem ORDER BY).
 */
@Service
@RequiredArgsConstructor
public class TenantResolver {

    private final TenantConfigRepository tenantConfigRepository;

    @Transactional(readOnly = true)
    public Optional<TenantConfig> resolvePrimaryTenant() {
        return tenantConfigRepository.findFirstByOrderByUpdatedAtDesc();
    }
}
