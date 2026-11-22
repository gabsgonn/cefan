package br.com.cefan.whatsapp.api.controller;

import br.com.cefan.whatsapp.api.dto.TenantConfigDto;
import br.com.cefan.whatsapp.application.service.TenantConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenant-configs")
@RequiredArgsConstructor
public class TenantConfigController {

    private final TenantConfigService tenantConfigService;

    @GetMapping("/branding")
    public ResponseEntity<StudioBrandingResponse> getBranding() {
        TenantConfigDto config = tenantConfigService.getConfig();
        return ResponseEntity.ok(new StudioBrandingResponse(config.getStudioName(), config.getLogoBase64()));
    }

    @GetMapping
    public ResponseEntity<TenantConfigDto> getConfig() {
        return ResponseEntity.ok(tenantConfigService.getConfig());
    }

    @PutMapping
    public ResponseEntity<TenantConfigDto> updateConfig(@Valid @RequestBody TenantConfigDto dto) {
        return ResponseEntity.ok(tenantConfigService.updateConfig(dto));
    }

    public record StudioBrandingResponse(String studioName, String logoBase64) {}
}
