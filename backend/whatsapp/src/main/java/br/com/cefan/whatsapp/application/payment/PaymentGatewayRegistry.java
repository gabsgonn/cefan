package br.com.cefan.whatsapp.application.payment;

import br.com.cefan.whatsapp.domain.model.TenantConfig;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaymentGatewayRegistry {

    private final Map<String, PaymentGateway> gatewaysByProvider;

    public PaymentGatewayRegistry(List<PaymentGateway> gateways) {
        this.gatewaysByProvider = (gateways != null)
                ? gateways.stream().collect(Collectors.toMap(
                        g -> normalize(g.getProviderName()),
                        Function.identity(),
                        (a, b) -> a))
                : Map.of();
    }

    public Optional<PaymentGateway> forTenant(TenantConfig config) {
        String provider = (config != null && config.getPaymentProvider() != null && !config.getPaymentProvider().isBlank())
                ? config.getPaymentProvider()
                : "mercadopago";
        return byProvider(provider);
    }

    public Optional<PaymentGateway> byProvider(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(gatewaysByProvider.get(normalize(providerName)));
    }

    private static String normalize(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }
}
