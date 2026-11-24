package br.com.cefan.whatsapp.infrastructure.payment;

import br.com.cefan.whatsapp.application.payment.*;
import br.com.cefan.whatsapp.domain.model.TenantConfig;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Gateway fake habilitado EXCLUSIVAMENTE no profile 'dev' (nunca usado como fallback em produção).
 */
@Component
@Profile("dev")
public class FakePaymentGateway implements PaymentGateway {

    @Override
    public String getProviderName() {
        return "fake";
    }

    @Override
    public PaymentCapabilities capabilities() {
        return new PaymentCapabilities(false, true, false, false, false, true);
    }

    @Override
    public boolean isConfiguredFor(TenantConfig tenantConfig) {
        return true;
    }

    @Override
    public PaymentCharge createCharge(TenantConfig tenantConfig, CreateChargeRequest request) {
        return new PaymentCharge(
                "fake",
                request.appointmentId().toString(),
                "https://dev.local/checkout/" + request.appointmentId(),
                null,
                null,
                request.expiresAt()
        );
    }

    @Override
    public PaymentVerificationResult verify(TenantConfig tenantConfig, PaymentEvidence evidence) {
        return PaymentVerificationResult.paid("paid", null, null, "pix",
                evidence != null ? evidence.transactionNsu() : "fake-tx",
                evidence != null ? evidence.invoiceSlug() : "fake-slug");
    }
}
