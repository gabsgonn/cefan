package br.com.cefan.whatsapp.application.statemachine.handler;

import br.com.cefan.whatsapp.application.service.ChatService;
import br.com.cefan.whatsapp.application.statemachine.ConversationStateHandler;
import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import br.com.cefan.whatsapp.domain.model.ConversationSession;
import br.com.cefan.whatsapp.domain.model.ConversationState;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.model.TenantConfig;
import br.com.cefan.whatsapp.domain.model.User;
import br.com.cefan.whatsapp.domain.model.UserRole;
import br.com.cefan.whatsapp.domain.repository.AppointmentRepository;
import br.com.cefan.whatsapp.domain.repository.TenantConfigRepository;
import br.com.cefan.whatsapp.domain.repository.UserRepository;
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
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgendamentoStateHandler implements ConversationStateHandler {

    private final EvolutionApiClient evolutionApiClient;
    private final AppointmentRepository appointmentRepository;
    private final MercadoPagoPaymentClient paymentClient;
    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final TenantConfigRepository tenantConfigRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"));
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{1,2})[/.-](\\d{1,2})");

    @Override
    public ConversationState getSupportedState() {
        return ConversationState.AGENDAMENTO_SELECAO_DATA;
    }

    @Override
    public void handle(Customer customer, ConversationSession session, EvolutionWebhookRequest webhook, String messageText) {
        log.info("Processando agendamento para cliente [{}]: {}", customer.getPhone(), messageText);

        TenantConfig tenantConfig = tenantConfigRepository.findAll().stream()
                .findFirst()
                .orElse(null);

        // 1. Identifica o Tatuador preferido ou recomendado
        User assignedArtist = resolveArtist(messageText, session.getContextData());
        String artistName = (assignedArtist != null) ? assignedArtist.getName() : "Equipe Vício Tattoo";

        // 2. Calcula data inteligente e verifica disponibilidade de slots
        OffsetDateTime scheduledStart = resolveAvailableSlot(messageText, assignedArtist);
        OffsetDateTime scheduledEnd = scheduledStart.plusHours(2);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(2);

        // 3. Determina valor total e valor do sinal a partir do contextData e regras de estúdio
        BigDecimal totalPrice = extractPriceFromContext(session.getContextData());
        BigDecimal signalPercent = (tenantConfig != null && tenantConfig.getSignalPercentage() != null)
                ? tenantConfig.getSignalPercentage()
                : new BigDecimal("30.00");
        BigDecimal minDeposit = (tenantConfig != null && tenantConfig.getMinDepositAmount() != null)
                ? tenantConfig.getMinDepositAmount()
                : new BigDecimal("50.00");

        BigDecimal depositAmount = totalPrice.multiply(signalPercent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
        if (depositAmount.compareTo(minDeposit) < 0) {
            depositAmount = minDeposit;
        }
        BigDecimal remainingAmount = totalPrice.subtract(depositAmount).setScale(2, RoundingMode.HALF_UP);

        // 4. Cria cobrança PIX no Mercado Pago
        String payerEmail = customer.getPhone() + "@cefan.app";
        String description = "Sinal Agendamento (" + artistName + ") - Vício Tattoo";
        MercadoPagoPaymentResponse paymentResponse = paymentClient.createPixPayment(
                depositAmount, description, payerEmail, customer.getName(), expiresAt);

        String paymentId = (paymentResponse != null && paymentResponse.getId() != null)
                ? String.valueOf(paymentResponse.getId())
                : null;
        String pixCopyPaste = (paymentResponse != null) ? paymentResponse.extractPixCopyPaste() : null;
        String pixQrCodeBase64 = (paymentResponse != null) ? paymentResponse.extractPixQrCodeBase64() : null;
        String ticketUrl = (paymentResponse != null) ? paymentResponse.extractTicketUrl() : null;

        // 5. Salva o registro do agendamento no banco de dados
        Appointment appointment = Appointment.builder()
                .customer(customer)
                .artist(assignedArtist)
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

        // Atualiza a sessão com o profissional atribuído
        if (assignedArtist != null) {
            session.setAssignedUser(assignedArtist);
            session.setAssignedArtistName(assignedArtist.getName());
        }

        // 6. Envia mensagem formatada com as instruções de pagamento no WhatsApp
        String dateFormatted = scheduledStart.format(DATE_FORMATTER);
        String reply = String.format(
                "🗓️ *Pré-Agendamento Criado!* 🗓️\n\n" +
                "Olá, %s! Bloqueamos seu horário com sucesso:\n" +
                "📅 *Data da Sessão:* %s\n" +
                "👨‍🎨 *Profissional:* %s\n" +
                "💰 *Sinal de Reserva:* R$ %s\n" +
                "💵 *Saldo Restante:* R$ %s (pago no estúdio no dia da tattoo)\n\n" +
                "⏳ *Atenção:* Seu horário fica reservado por *2 horas*. Para confirmar a vaga, efetue o pagamento do sinal via PIX Copia e Cola:\n\n" +
                "🔑 *Código Pix:*\n" +
                "%s\n\n" +
                "Assim que o pagamento for identificado, confirmaremos sua vaga automaticamente! ✨\n\n" +
                "💡 _Digite 0 a qualquer momento para voltar ao menu inicial._",
                customer.getName(),
                dateFormatted,
                artistName,
                depositAmount,
                remainingAmount,
                pixCopyPaste != null ? pixCopyPaste : "Chave PIX temporariamente indisponível. Fale com o tatuador digitando 3."
        );

        evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
        chatService.recordBotMessage(session, customer, reply);
    }

    private User resolveArtist(String messageText, String contextData) {
        List<User> artists = userRepository.findAll();
        String textLower = (messageText != null) ? messageText.toLowerCase() : "";

        // Procura nome de artista citado na mensagem
        for (User u : artists) {
            String firstName = u.getName().split(" ")[0].toLowerCase();
            if (textLower.contains(firstName)) {
                return u;
            }
        }

        // Se não citou pelo nome, tenta buscar recomendação salva no contextData
        if (contextData != null && !contextData.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(contextData);
                if (node.has("recommendedArtists") && node.get("recommendedArtists").isArray() && node.get("recommendedArtists").size() > 0) {
                    String recommended = node.get("recommendedArtists").get(0).asText();
                    for (User u : artists) {
                        if (recommended.toLowerCase().contains(u.getName().split(" ")[0].toLowerCase())) {
                            return u;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Retorna o primeiro artista ativo se disponível
        return artists.stream()
                .filter(u -> u.getRole() == UserRole.ARTIST && Boolean.TRUE.equals(u.getIsActive()))
                .findFirst()
                .orElse(null);
    }

    private OffsetDateTime resolveAvailableSlot(String messageText, User artist) {
        String lower = (messageText != null) ? messageText.toLowerCase() : "";
        OffsetDateTime target = OffsetDateTime.now().plusDays(2).withHour(14).withMinute(0).withSecond(0).withNano(0);

        // Ajuste de período (manhã / tarde / noite)
        int hour = 14;
        if (lower.contains("manhã") || lower.contains("manha") || lower.contains("10h")) {
            hour = 10;
        } else if (lower.contains("noite") || lower.contains("18h") || lower.contains("19h")) {
            hour = 18;
        } else if (lower.contains("16h")) {
            hour = 16;
        }

        if (lower.contains("amanhã") || lower.contains("amanha")) {
            target = OffsetDateTime.now().plusDays(1).withHour(hour).withMinute(0).withSecond(0).withNano(0);
        } else if (lower.contains("hoje")) {
            target = OffsetDateTime.now().withHour(Math.max(OffsetDateTime.now().getHour() + 2, 14)).withMinute(0).withSecond(0).withNano(0);
        } else {
            // Verifica dia da semana
            DayOfWeek dow = null;
            if (lower.contains("segunda")) dow = DayOfWeek.MONDAY;
            else if (lower.contains("terça") || lower.contains("terca")) dow = DayOfWeek.TUESDAY;
            else if (lower.contains("quarta")) dow = DayOfWeek.WEDNESDAY;
            else if (lower.contains("quinta")) dow = DayOfWeek.THURSDAY;
            else if (lower.contains("sexta")) dow = DayOfWeek.FRIDAY;
            else if (lower.contains("sábado") || lower.contains("sabado")) dow = DayOfWeek.SATURDAY;

            if (dow != null) {
                target = OffsetDateTime.now();
                while (target.getDayOfWeek() != dow) {
                    target = target.plusDays(1);
                }
                target = target.withHour(hour).withMinute(0).withSecond(0).withNano(0);
            } else {
                // Tenta regex dd/MM
                Matcher m = DATE_PATTERN.matcher(lower);
                if (m.find()) {
                    try {
                        int day = Integer.parseInt(m.group(1));
                        int month = Integer.parseInt(m.group(2));
                        target = target.withMonth(month).withDayOfMonth(day).withHour(hour).withMinute(0).withSecond(0).withNano(0);
                        if (target.isBefore(OffsetDateTime.now())) {
                            target = target.plusYears(1);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // Verifica colisão na grade de agendamentos
        if (artist != null) {
            OffsetDateTime windowStart = target.minusHours(1);
            OffsetDateTime windowEnd = target.plusHours(2);
            List<Appointment> conflicts = appointmentRepository.findByArtistIdAndScheduledStartBetween(artist.getId(), windowStart, windowEnd);
            if (!conflicts.isEmpty()) {
                // Horário ocupado, avança para próximo slot livre (ex: +3 horas)
                target = target.plusHours(3);
            }
        }

        return target;
    }

    private BigDecimal extractPriceFromContext(String contextData) {
        if (contextData == null || contextData.isBlank()) {
            return new BigDecimal("250.00");
        }
        try {
            JsonNode node = objectMapper.readTree(contextData);
            if (node.has("basePrice")) {
                return new BigDecimal(node.get("basePrice").asText());
            }
        } catch (Exception e) {
            log.warn("Erro ao extrair preço do contextData: {}", e.getMessage());
        }
        return new BigDecimal("250.00");
    }
}
