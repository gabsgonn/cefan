package br.com.cefan.whatsapp.application.statemachine.handler;

import br.com.cefan.whatsapp.application.statemachine.ConversationStateHandler;
import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import br.com.cefan.whatsapp.domain.model.ConversationSession;
import br.com.cefan.whatsapp.domain.model.ConversationState;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.repository.AppointmentRepository;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import br.com.cefan.whatsapp.infrastructure.evolution.dto.EvolutionWebhookRequest;
import br.com.cefan.whatsapp.infrastructure.mercadopago.MercadoPagoPaymentClient;
import br.com.cefan.whatsapp.infrastructure.mercadopago.dto.MercadoPagoPaymentResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgendamentoStateHandler implements ConversationStateHandler {

    private final EvolutionApiClient evolutionApiClient;
    private final AppointmentRepository appointmentRepository;
    private final MercadoPagoPaymentClient paymentClient;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"));

    @Override
    public ConversationState getSupportedState() {
        return ConversationState.AGENDAMENTO_SELECAO_DATA;
    }

    @Override
    public void handle(Customer customer, ConversationSession session, EvolutionWebhookRequest webhook, String messageText) {
        log.info("Processando agendamento para cliente [{}]: {}", customer.getPhone(), messageText);

        // 1. Determina valor total e valor do sinal a partir do contextData (ou valores padrão)
        BigDecimal totalPrice = extractPriceFromContext(session.getContextData());
        // Sinal de reserva: 30% do valor total ou mínimo de R$ 50,00
        BigDecimal depositAmount = totalPrice.multiply(new BigDecimal("0.30")).setScale(2, RoundingMode.HALF_UP);
        if (depositAmount.compareTo(new BigDecimal("50.00")) < 0) {
            depositAmount = new BigDecimal("50.00");
        }
        BigDecimal remainingAmount = totalPrice.subtract(depositAmount).setScale(2, RoundingMode.HALF_UP);

        // 2. Define data prevista (ex: próxima disponibilidade ou slot selecionado)
        OffsetDateTime scheduledStart = OffsetDateTime.now().plusDays(2).withHour(14).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime scheduledEnd = scheduledStart.plusHours(2);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(2);

        // 3. Cria cobrança PIX no Mercado Pago
        String payerEmail = customer.getPhone() + "@cefan.app";
        String description = "Sinal Agendamento Tatuagem - Vício Tattoo";
        MercadoPagoPaymentResponse paymentResponse = paymentClient.createPixPayment(
                depositAmount, description, payerEmail, customer.getName(), expiresAt);

        String paymentId = (paymentResponse != null && paymentResponse.getId() != null)
                ? String.valueOf(paymentResponse.getId())
                : null;
        String pixCopyPaste = (paymentResponse != null) ? paymentResponse.extractPixCopyPaste() : null;
        String pixQrCodeBase64 = (paymentResponse != null) ? paymentResponse.extractPixQrCodeBase64() : null;
        String ticketUrl = (paymentResponse != null) ? paymentResponse.extractTicketUrl() : null;

        // 4. Salva o registro do agendamento no banco de dados
        Appointment appointment = Appointment.builder()
                .customer(customer)
                .scheduledStart(scheduledStart)
                .scheduledEnd(scheduledEnd)
                .status(AppointmentStatus.AGUARDANDO_PAGAMENTO)
                .totalPrice(totalPrice)
                .depositAmount(depositAmount)
                .expiresAt(expiresAt)
                .mpPaymentId(paymentId)
                .mpPixCopyPaste(pixCopyPaste)
                .mpPixQrCodeBase64(pixQrCodeBase64)
                .mpPaymentLink(ticketUrl)
                .build();

        appointmentRepository.save(appointment);

        // 5. Envia mensagem formatada com as instruções de pagamento no WhatsApp
        String dateFormatted = scheduledStart.format(DATE_FORMATTER);
        String reply = String.format(
                "🗓️ *Pré-Agendamento Criado!* 🗓️\n\n" +
                "Olá, %s! Bloqueamos seu horário na nossa grade:\n" +
                "📅 *Previsão da Sessão:* %sh\n" +
                "💰 *Sinal de Reserva:* R$ %s\n" +
                "💵 *Saldo Restante:* R$ %s (pago no estúdio no dia da tattoo)\n\n" +
                "⏳ *Atenção:* Seu horário fica reservado por exatamente *2 horas*. Para confirmar seu agendamento, efetue o pagamento do sinal via PIX:\n\n" +
                "🔑 *Código Pix Copia e Cola:*\n" +
                "%s\n\n" +
                "Assim que o pagamento for identificado, confirmaremos sua vaga automaticamente! ✨\n\n" +
                "💡 _Digite 0 a qualquer momento para voltar ao menu inicial._",
                customer.getName(),
                dateFormatted,
                depositAmount,
                remainingAmount,
                pixCopyPaste != null ? pixCopyPaste : "Chave PIX temporariamente indisponível. Fale com o tatuador digitando 3."
        );

        evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
    }

    private BigDecimal extractPriceFromContext(String contextData) {
        if (contextData == null || contextData.isBlank()) {
            return new BigDecimal("300.00");
        }
        try {
            JsonNode node = objectMapper.readTree(contextData);
            if (node.has("basePrice")) {
                return new BigDecimal(node.get("basePrice").asText());
            }
        } catch (Exception e) {
            log.warn("Erro ao extrair preço do contextData: {}", e.getMessage());
        }
        return new BigDecimal("300.00");
    }
}
