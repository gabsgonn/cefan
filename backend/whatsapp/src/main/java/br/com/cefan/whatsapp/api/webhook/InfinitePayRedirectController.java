package br.com.cefan.whatsapp.api.webhook;

import br.com.cefan.whatsapp.application.payment.PaymentInboxService;
import br.com.cefan.whatsapp.infrastructure.infinitepay.InfinitePayPaymentGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Rota de redirect_url pós-checkout InfinitePay.
 * Os parâmetros da URL NÃO são prova de pagamento: apenas enfileiram um evento REDIRECT na inbox
 * para verificação posterior via payment_check e exibem uma página simples de retorno ao WhatsApp.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments/infinitepay")
@RequiredArgsConstructor
public class InfinitePayRedirectController {

    private final PaymentInboxService paymentInboxService;

    @org.springframework.beans.factory.annotation.Value("${cefan.whatsapp.return-url:https://wa.me/}")
    private String whatsappReturnUrl = "https://wa.me/";

    @GetMapping(value = "/return", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> handleReturn(
            @RequestParam(name = "order_nsu", required = false) String orderNsuRaw,
            @RequestParam(name = "slug", required = false) String slug,
            @RequestParam(name = "transaction_nsu", required = false) String transactionNsu,
            @RequestParam(name = "capture_method", required = false) String captureMethod) {

        if (orderNsuRaw != null && !orderNsuRaw.isBlank() && transactionNsu != null && !transactionNsu.isBlank()) {
            try {
                UUID orderNsu = UUID.fromString(orderNsuRaw.trim());
                var event = paymentInboxService.enqueueIdempotent(
                        InfinitePayPaymentGateway.PROVIDER,
                        orderNsu,
                        orderNsu,
                        transactionNsu.trim(),
                        slug,
                        captureMethod,
                        "REDIRECT"
                );
                paymentInboxService.processNowWithRetry(event);
            } catch (Exception e) {
                log.warn("Falha ao registrar/processar retorno redirect InfinitePay: {}", e.getMessage());
            }
        }

        String safeReturnUrl = (whatsappReturnUrl != null && !whatsappReturnUrl.isBlank()) ? whatsappReturnUrl.trim() : "https://wa.me/";
        String html = String.format("""
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>Vício Tattoo CWB — Pagamento Recebido</title>
                  <style>
                    body { font-family: system-ui, sans-serif; background: #111; color: #f5f5f5; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 16px; }
                    .card { background: #1c1c1e; border-radius: 16px; padding: 32px; max-width: 420px; text-align: center; box-shadow: 0 12px 32px rgba(0,0,0,0.4); }
                    h1 { font-size: 1.4rem; margin-bottom: 12px; }
                    p { color: #b5b5bd; line-height: 1.5; margin-bottom: 24px; }
                    a.btn { display: inline-block; background: #25D366; color: #081c0f; font-weight: 700; text-decoration: none; padding: 12px 24px; border-radius: 999px; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h1>✅ Pagamento recebido!</h1>
                    <p>Já estamos validando o seu sinal e enviamos a confirmação direto no seu WhatsApp.</p>
                    <a class="btn" href="%s">Voltar para o WhatsApp</a>
                  </div>
                </body>
                </html>
                """, safeReturnUrl);
        return ResponseEntity.ok(html);
    }
}
