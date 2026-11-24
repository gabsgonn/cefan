package br.com.cefan.whatsapp.application.whatsapp;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Porta de saída neutra para envio de mensagens e download de mídia no WhatsApp.
 * Desacopla handlers e serviços de aplicação do provedor concreto (Evolution API vs Meta Cloud API).
 */
public interface WhatsAppGateway {

    String getProviderName();

    GatewayCapabilities capabilities();

    SendResult sendText(String to, String text);

    SendResult sendInteractive(String to, String body, List<InteractiveOption> options);

    SendResult sendTemplate(String to, String templateName, String languageCode, List<String> parameters);

    SendResult sendPresence(String to, String presence, int delayMs);

    SendResult sendPaymentLink(
            String to,
            long amountCents,
            String url,
            String description,
            String referenceId,
            OffsetDateTime expiresAt);

    Optional<String> downloadMedia(String mediaReference);
}
