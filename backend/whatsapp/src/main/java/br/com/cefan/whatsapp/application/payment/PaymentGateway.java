package br.com.cefan.whatsapp.application.payment;

import br.com.cefan.whatsapp.domain.model.TenantConfig;

public interface PaymentGateway {

    String getProviderName();

    PaymentCapabilities capabilities();

    boolean isConfiguredFor(TenantConfig tenantConfig);

    PaymentCharge createCharge(TenantConfig tenantConfig, CreateChargeRequest request);

    PaymentVerificationResult verify(TenantConfig tenantConfig, PaymentEvidence evidence);
}
