package br.com.cefan.whatsapp.infrastructure.mercadopago;

import br.com.cefan.whatsapp.application.payment.*;
import br.com.cefan.whatsapp.domain.model.TenantConfig;
import br.com.cefan.whatsapp.infrastructure.mercadopago.dto.MercadoPagoPaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Component
@RequiredArgsConstructor
public class MercadoPagoPaymentGateway implements PaymentGateway {

    public static final String PROVIDER = "mercadopago";

    private final MercadoPagoPaymentClient paymentClient;

    @Override
    public String getProviderName() {
        return PROVIDER;
    }

    @Override
    public PaymentCapabilities capabilities() {
        return PaymentCapabilities.mercadoPago();
    }

    @Override
    public boolean isConfiguredFor(TenantConfig tenantConfig) {
        return true;
    }

    @Override
    public PaymentCharge createCharge(TenantConfig tenantConfig, CreateChargeRequest request) {
        BigDecimal amount = BigDecimal.valueOf(request.amountCents(), 2);
        MercadoPagoPaymentResponse response = paymentClient.createPixPayment(
                amount,
                request.description(),
                request.customerEmail(),
                request.customerName(),
                request.expiresAt()
        );
        return new PaymentCharge(
                PROVIDER,
                response.getId() != null ? String.valueOf(response.getId()) : null,
                null,
                response.extractPixCopyPaste(),
                response.extractPixQrCodeBase64(),
                request.expiresAt()
        );
    }

    @Override
    public PaymentVerificationResult verify(TenantConfig tenantConfig, PaymentEvidence evidence) {
        if (evidence == null || evidence.paymentId() == null || evidence.paymentId().isBlank()) {
            return PaymentVerificationResult.notPaid(null, "paymentId ausente");
        }
        try {
            MercadoPagoPaymentResponse response = paymentClient.getPayment(evidence.paymentId());
            if (response == null) {
                return PaymentVerificationResult.unverifiable("Resposta nula do Mercado Pago para paymentId=" + evidence.paymentId());
            }
            if ("approved".equalsIgnoreCase(response.getStatus())) {
                Long cents = response.getTransactionAmount() != null
                        ? response.getTransactionAmount().setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
                        : null;
                return PaymentVerificationResult.paid(
                        response.getStatus(),
                        cents,
                        cents,
                        "pix",
                        String.valueOf(response.getId()),
                        null
                );
            }
            return PaymentVerificationResult.notPaid(response.getStatus(), "Status MP: " + response.getStatus());
        } catch (Exception e) {
            log.warn("Falha transitória ao verificar pagamento [{}] no Mercado Pago: {}", evidence.paymentId(), e.getMessage());
            return PaymentVerificationResult.unverifiable(e.getMessage());
        }
    }
}
