package br.com.cefan.whatsapp.application.whatsapp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementação em memória de {@link WhatsAppGateway} para testes de handlers e serviços,
 * substituindo mocks diretos de {@code EvolutionApiClient}.
 */
public class FakeWhatsAppGateway implements WhatsAppGateway {

    public record SentText(String to, String text) {}
    public record SentInteractive(String to, String body, List<InteractiveOption> options) {}
    public record SentTemplate(String to, String templateName, String languageCode, List<String> parameters) {}
    public record SentPresence(String to, String presence, int delayMs) {}
    public record SentPaymentLink(String to, long amountCents, String url, String description,
                                  String referenceId, OffsetDateTime expiresAt) {}

    private final List<SentText> sentTexts = new ArrayList<>();
    private final List<SentInteractive> sentInteractives = new ArrayList<>();
    private final List<SentTemplate> sentTemplates = new ArrayList<>();
    private final List<SentPresence> sentPresences = new ArrayList<>();
    private final List<SentPaymentLink> sentPaymentLinks = new ArrayList<>();
    private final AtomicInteger idSeq = new AtomicInteger(1);

    private GatewayCapabilities capabilities = GatewayCapabilities.evolution();
    private SendStatus nextSendStatus = SendStatus.SENT;
    private String nextErrorDetail = null;
    private String stubbedDownloadedMedia = null;

    public void setCapabilities(GatewayCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    public void failNextWith(SendStatus status, String detail) {
        this.nextSendStatus = status;
        this.nextErrorDetail = detail;
    }

    public void stubDownloadedMedia(String base64) {
        this.stubbedDownloadedMedia = base64;
    }

    public List<SentText> getSentTexts() {
        return Collections.unmodifiableList(sentTexts);
    }

    public List<SentInteractive> getSentInteractives() {
        return Collections.unmodifiableList(sentInteractives);
    }

    public List<SentTemplate> getSentTemplates() {
        return Collections.unmodifiableList(sentTemplates);
    }

    public List<SentPresence> getSentPresences() {
        return Collections.unmodifiableList(sentPresences);
    }

    public List<SentPaymentLink> getSentPaymentLinks() {
        return Collections.unmodifiableList(sentPaymentLinks);
    }

    public void reset() {
        sentTexts.clear();
        sentInteractives.clear();
        sentTemplates.clear();
        sentPresences.clear();
        sentPaymentLinks.clear();
        nextSendStatus = SendStatus.SENT;
        nextErrorDetail = null;
        stubbedDownloadedMedia = null;
    }

    @Override
    public String getProviderName() {
        return "fake";
    }

    @Override
    public GatewayCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public SendResult sendText(String to, String text) {
        sentTexts.add(new SentText(to, text));
        return nextResult();
    }

    @Override
    public SendResult sendInteractive(String to, String body, List<InteractiveOption> options) {
        sentInteractives.add(new SentInteractive(to, body, options != null ? List.copyOf(options) : List.of()));
        return nextResult();
    }

    @Override
    public SendResult sendTemplate(String to, String templateName, String languageCode, List<String> parameters) {
        sentTemplates.add(new SentTemplate(to, templateName, languageCode, parameters != null ? List.copyOf(parameters) : List.of()));
        return nextResult();
    }

    @Override
    public SendResult sendPresence(String to, String presence, int delayMs) {
        sentPresences.add(new SentPresence(to, presence, delayMs));
        return nextResult();
    }

    @Override
    public SendResult sendPaymentLink(
            String to,
            long amountCents,
            String url,
            String description,
            String referenceId,
            OffsetDateTime expiresAt) {
        sentPaymentLinks.add(new SentPaymentLink(to, amountCents, url, description, referenceId, expiresAt));
        return nextResult();
    }

    @Override
    public Optional<String> downloadMedia(String mediaReference) {
        return Optional.ofNullable(stubbedDownloadedMedia);
    }

    private SendResult nextResult() {
        if (nextSendStatus == SendStatus.SENT) {
            return SendResult.sent("FAKE-MSG-" + idSeq.getAndIncrement());
        }
        return new SendResult(nextSendStatus, null, nextErrorDetail != null ? nextErrorDetail : "simulated-" + nextSendStatus);
    }
}
