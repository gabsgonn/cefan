package br.com.cefan.whatsapp.application.statemachine.handler;

import br.com.cefan.whatsapp.api.controller.StudioAdminController;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AgendamentoStateHandler implements ConversationStateHandler {

    private final EvolutionApiClient evolutionApiClient;
    private final AppointmentRepository appointmentRepository;
    private final MercadoPagoPaymentClient paymentClient;
    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final TenantConfigRepository tenantConfigRepository;
    private final UserRepository userRepository;
    private final StudioAdminController studioAdminController;

    public AgendamentoStateHandler(
            EvolutionApiClient evolutionApiClient,
            AppointmentRepository appointmentRepository,
            MercadoPagoPaymentClient paymentClient,
            ObjectMapper objectMapper,
            ChatService chatService,
            TenantConfigRepository tenantConfigRepository,
            UserRepository userRepository) {
        this(evolutionApiClient, appointmentRepository, paymentClient, objectMapper, chatService, tenantConfigRepository, userRepository, null);
    }

    @Autowired
    public AgendamentoStateHandler(
            EvolutionApiClient evolutionApiClient,
            AppointmentRepository appointmentRepository,
            MercadoPagoPaymentClient paymentClient,
            ObjectMapper objectMapper,
            ChatService chatService,
            TenantConfigRepository tenantConfigRepository,
            UserRepository userRepository,
            StudioAdminController studioAdminController) {
        this.evolutionApiClient = evolutionApiClient;
        this.appointmentRepository = appointmentRepository;
        this.paymentClient = paymentClient;
        this.objectMapper = objectMapper;
        this.chatService = chatService;
        this.tenantConfigRepository = tenantConfigRepository;
        this.userRepository = userRepository;
        this.studioAdminController = studioAdminController;
    }

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"));
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{1,2})[/.-](\\d{1,2})");
    private static final Pattern HOUR_PATTERN = Pattern.compile("(?i)(?:às|as|a partir das|\\b)\\s*(\\d{1,2})\\s*(?:h(?:oras)?(?:\\s*(\\d{2}))?|:(\\d{2}))");

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

        String input = (messageText != null) ? messageText.trim().toLowerCase() : "";

        // Permite transbordo humano (Opção 3) mesmo dentro do fluxo de agendamento
        if (input.equals("3") || input.contains("humano") || input.contains("falar") || input.contains("atendente") || input.equals("tatuador")) {
            session.setCurrentState(ConversationState.TRANSBORDO_HUMANO);
            String customTemplate = (tenantConfig != null && tenantConfig.getHumanTransferMessage() != null && !tenantConfig.getHumanTransferMessage().isBlank())
                    ? tenantConfig.getHumanTransferMessage()
                    : "Notifiquei nossa equipe e um dos nossos tatuadores irá te responder diretamente por aqui em instantes. Aguarde só um momento!";
            String absenceNotice = buildAbsenceNoticeIfOutsideHours(tenantConfig);
            String reply = String.format("Entendido, %s! 👨‍🎨\n\n%s%s\n\n💡 _Se quiser reiniciar o assistente automático, basta enviar 0 a qualquer momento._",
                    customer.getName(), customTemplate, absenceNotice);
            evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
            chatService.recordBotMessage(session, customer, reply);
            if (studioAdminController != null) {
                studioAdminController.publishNotification(
                        "HUMAN_REQUEST",
                        "Aguardando Tatuador",
                        customer.getName() + " solicitou atendimento humano no WhatsApp.",
                        customer.getPhone()
                );
            }
            return;
        }

        // Permite voltar para orçamento com IA (Opção 1)
        if (input.equals("1") || input.contains("orçamento") || input.contains("orcamento")) {
            session.setCurrentState(ConversationState.ORCAMENTO_AGUARDANDO_FOTO);
            String customTemplate = (tenantConfig != null && tenantConfig.getOrcamentoIntroMessage() != null && !tenantConfig.getOrcamentoIntroMessage().isBlank())
                    ? tenantConfig.getOrcamentoIntroMessage()
                    : "Por favor, envie uma *foto da tatuagem* que você deseja fazer (ou uma referência visual) e nos diga o local do corpo e tamanho aproximado em cm.";
            String reply = String.format("Perfeito, %s! 🎨\n\n%s\n\n💡 _Você pode digitar 0 a qualquer momento para voltar ao menu inicial._",
                    customer.getName(), customTemplate);
            evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
            chatService.recordBotMessage(session, customer, reply);
            return;
        }

        // 1. Identifica o Tatuador preferido ou recomendado
        User assignedArtist = resolveArtist(messageText, session.getContextData());
        String artistName = (assignedArtist != null) ? assignedArtist.getName() : "Equipe Vício Tattoo";

        // 2. Calcula data inteligente e verifica disponibilidade de slots (respeitando horário de funcionamento do estúdio)
        OffsetDateTime requestedSlot = parseRequestedSlot(messageText, tenantConfig);
        OffsetDateTime scheduledStart = resolveAvailableSlot(requestedSlot, assignedArtist, tenantConfig);
        boolean wasConflictAdjusted = !scheduledStart.isEqual(requestedSlot);
        OffsetDateTime scheduledEnd = scheduledStart.plusHours(2);

        int expirationMinutes = (tenantConfig != null && tenantConfig.getPixExpirationMinutes() != null && tenantConfig.getPixExpirationMinutes() > 0)
                ? tenantConfig.getPixExpirationMinutes()
                : 120;
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(expirationMinutes);

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
        String conflictNotice = wasConflictAdjusted
                ? String.format("⚠️ _Nota: O horário de %s já estava ocupado na agenda de *%s*, então reservamos o próximo horário livre disponível:_\n\n",
                        requestedSlot.format(DATE_FORMATTER), artistName)
                : "";

        String expirationLabel = (expirationMinutes == 120)
                ? "2 horas"
                : (expirationMinutes % 60 == 0 && expirationMinutes > 60
                        ? (expirationMinutes / 60) + " horas (" + expirationMinutes + " minutos)"
                        : expirationMinutes + " minutos");

        String rawPixHeader = (tenantConfig != null && tenantConfig.getPixPaymentMessage() != null && !tenantConfig.getPixPaymentMessage().isBlank())
                ? tenantConfig.getPixPaymentMessage().trim()
                : "";
        String headerTitle = rawPixHeader.toLowerCase().contains("pré-agendamento criado") || rawPixHeader.toLowerCase().contains("pre-agendamento criado")
                ? rawPixHeader + "\n\n"
                : "🗓️ *Pré-Agendamento Criado!* 🗓️\n\n" + (rawPixHeader.isEmpty() ? "" : rawPixHeader + "\n\n");

        String reply = String.format(
                "%s" +
                "%s" +
                "Olá, %s! Bloqueamos seu horário com sucesso:\n" +
                "📅 *Data da Sessão:* %s\n" +
                "👨‍🎨 *Profissional:* %s\n" +
                "💰 *Sinal de Reserva:* R$ %s\n" +
                "💵 *Saldo Restante:* R$ %s (pago no estúdio no dia da tattoo)\n\n" +
                "⏳ *Atenção:* Seu horário fica reservado por *%s*. Para confirmar a vaga, efetue o pagamento do sinal via PIX Copia e Cola:\n\n" +
                "🔑 *Código Pix:*\n" +
                "%s\n\n" +
                "Assim que o pagamento for identificado, confirmaremos sua vaga automaticamente! ✨\n\n" +
                "💡 _Digite 0 a qualquer momento para voltar ao menu inicial._",
                headerTitle,
                conflictNotice,
                customer.getName(),
                dateFormatted,
                artistName,
                depositAmount,
                remainingAmount,
                expirationLabel,
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

    OffsetDateTime parseRequestedSlot(String messageText) {
        return parseRequestedSlot(messageText, false);
    }

    private OffsetDateTime parseRequestedSlot(String messageText, boolean allowAnyHour) {
        String lower = (messageText != null) ? messageText.toLowerCase() : "";
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime target = now.plusDays(2).withHour(14).withMinute(0).withSecond(0).withNano(0);

        // Ajuste de período (manhã / tarde / noite) ou hora explícita (ex: 15h, 15h30, 11h, 17:00, 15:30)
        int hour = 14;
        int minute = 0;
        if (lower.contains("manhã") || lower.contains("manha")) {
            hour = 10;
        } else if (lower.contains("noite")) {
            hour = 18;
        }

        boolean hasExplicitHour = false;
        Matcher hourMatcher = HOUR_PATTERN.matcher(lower);
        if (hourMatcher.find()) {
            try {
                int parsedHour = Integer.parseInt(hourMatcher.group(1));
                if (allowAnyHour ? (parsedHour >= 0 && parsedHour <= 23) : (parsedHour >= 8 && parsedHour <= 21)) {
                    hour = parsedHour;
                    hasExplicitHour = true;
                }
                String minGroup = hourMatcher.group(2) != null ? hourMatcher.group(2) : hourMatcher.group(3);
                if (minGroup != null) {
                    int parsedMin = Integer.parseInt(minGroup);
                    if (parsedMin >= 0 && parsedMin < 60) {
                        minute = parsedMin;
                    }
                }
            } catch (Exception ignored) {}
        }

        if (lower.contains("amanhã") || lower.contains("amanha")) {
            target = now.plusDays(1).withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        } else if (lower.contains("hoje")) {
            int candidateHour = hasExplicitHour ? hour : Math.max(now.getHour() + 2, hour);
            if (candidateHour > 20 || candidateHour < now.getHour() || (candidateHour == now.getHour() && minute <= now.getMinute())) {
                int nextDayHour = (hasExplicitHour && (allowAnyHour || (hour >= 8 && hour <= 20))) ? hour : 14;
                target = now.plusDays(1).withHour(nextDayHour).withMinute(minute).withSecond(0).withNano(0);
            } else {
                target = now.withHour(candidateHour).withMinute(minute).withSecond(0).withNano(0);
            }
        } else {
            // Verifica dia da semana
            DayOfWeek dow = null;
            if (lower.contains("segunda")) dow = DayOfWeek.MONDAY;
            else if (lower.contains("terça") || lower.contains("terca")) dow = DayOfWeek.TUESDAY;
            else if (lower.contains("quarta")) dow = DayOfWeek.WEDNESDAY;
            else if (lower.contains("quinta")) dow = DayOfWeek.THURSDAY;
            else if (lower.contains("sexta")) dow = DayOfWeek.FRIDAY;
            else if (lower.contains("sábado") || lower.contains("sabado")) dow = DayOfWeek.SATURDAY;
            else if (lower.contains("domingo")) dow = DayOfWeek.SUNDAY;

            if (dow != null) {
                target = now;
                while (target.getDayOfWeek() != dow) {
                    target = target.plusDays(1);
                }
                target = target.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
                if (!target.isAfter(now)) {
                    target = target.plusDays(7);
                }
            } else {
                // Tenta regex dd/MM
                Matcher m = DATE_PATTERN.matcher(lower);
                if (m.find()) {
                    try {
                        int day = Integer.parseInt(m.group(1));
                        int month = Integer.parseInt(m.group(2));
                        target = target.withMonth(month).withDayOfMonth(day).withHour(hour).withMinute(minute).withSecond(0).withNano(0);
                        if (target.isBefore(now)) {
                            target = target.plusYears(1);
                        }
                    } catch (Exception ignored) {}
                } else {
                    target = target.withHour(hour).withMinute(minute);
                }
            }
        }
        return target;
    }

    OffsetDateTime parseRequestedSlot(String messageText, TenantConfig tenantConfig) {
        OffsetDateTime target = parseRequestedSlot(messageText, tenantConfig != null);
        if (tenantConfig == null) {
            return target;
        }
        return adjustToOperatingSchedule(target, tenantConfig);
    }

    private OffsetDateTime adjustToOperatingSchedule(OffsetDateTime slot, TenantConfig config) {
        if (config == null) {
            return slot;
        }
        LocalTime openTime = parseLocalTime(config.getOpeningTime(), LocalTime.of(10, 0));
        LocalTime closeTime = parseLocalTime(config.getClosingTime(), LocalTime.of(20, 0));
        String workingDays = config.getWorkingDays() != null ? config.getWorkingDays().trim().toUpperCase() : "SEG-SAB";

        OffsetDateTime adjusted = slot;
        LocalTime slotTime = adjusted.toLocalTime().withSecond(0).withNano(0);
        if (slotTime.isBefore(openTime)) {
            adjusted = adjusted.withHour(openTime.getHour()).withMinute(openTime.getMinute()).withSecond(0).withNano(0);
        } else if (slotTime.isAfter(closeTime)) {
            adjusted = adjusted.plusDays(1).withHour(openTime.getHour()).withMinute(openTime.getMinute()).withSecond(0).withNano(0);
        }

        for (int i = 0; i < 7; i++) {
            DayOfWeek dow = adjusted.getDayOfWeek();
            boolean closedOnSunday = !"SEG-DOM".equals(workingDays) && dow == DayOfWeek.SUNDAY;
            boolean closedOnSaturday = "SEG-SEX".equals(workingDays) && dow == DayOfWeek.SATURDAY;
            if (closedOnSunday || closedOnSaturday) {
                adjusted = adjusted.plusDays(1);
            } else {
                break;
            }
        }
        return adjusted;
    }

    public static LocalTime parseLocalTime(String timeStr, LocalTime fallback) {
        if (timeStr == null || timeStr.isBlank()) {
            return fallback;
        }
        try {
            String[] parts = timeStr.trim().split(":");
            int h = Integer.parseInt(parts[0].trim());
            int m = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
            if (h >= 0 && h <= 23 && m >= 0 && m <= 59) {
                return LocalTime.of(h, m);
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    public static boolean isOutsideOperatingHours(OffsetDateTime dateTime, TenantConfig config) {
        if (config == null || dateTime == null) {
            return false;
        }
        LocalTime openTime = parseLocalTime(config.getOpeningTime(), LocalTime.of(10, 0));
        LocalTime closeTime = parseLocalTime(config.getClosingTime(), LocalTime.of(20, 0));
        String workingDays = config.getWorkingDays() != null ? config.getWorkingDays().trim().toUpperCase() : "SEG-SAB";

        DayOfWeek dow = dateTime.getDayOfWeek();
        boolean closedOnSunday = !"SEG-DOM".equals(workingDays) && dow == DayOfWeek.SUNDAY;
        boolean closedOnSaturday = "SEG-SEX".equals(workingDays) && dow == DayOfWeek.SATURDAY;
        if (closedOnSunday || closedOnSaturday) {
            return true;
        }

        LocalTime current = dateTime.toLocalTime().withSecond(0).withNano(0);
        return current.isBefore(openTime) || current.isAfter(closeTime);
    }

    public static String buildAbsenceNoticeIfOutsideHours(TenantConfig config) {
        if (config == null || config.getAbsenceMessage() == null || config.getAbsenceMessage().isBlank()) {
            return "";
        }
        if (isOutsideOperatingHours(OffsetDateTime.now(), config)) {
            return "\n\n" + config.getAbsenceMessage().trim();
        }
        return "";
    }

    private OffsetDateTime resolveAvailableSlot(OffsetDateTime target, User artist) {
        return resolveAvailableSlot(target, artist, null);
    }

    private OffsetDateTime resolveAvailableSlot(OffsetDateTime target, User artist, TenantConfig config) {
        if (artist == null) {
            return target;
        }

        LocalTime openTime = config != null ? parseLocalTime(config.getOpeningTime(), LocalTime.of(9, 0)) : LocalTime.of(9, 0);
        LocalTime closeTime = config != null ? parseLocalTime(config.getClosingTime(), LocalTime.of(20, 0)) : LocalTime.of(20, 0);

        OffsetDateTime candidate = target;
        for (int attempt = 0; attempt < 5; attempt++) {
            OffsetDateTime windowStart = candidate.minusHours(2);
            OffsetDateTime windowEnd = candidate.plusHours(2);
            final OffsetDateTime slotStart = candidate;
            final OffsetDateTime slotEnd = candidate.plusHours(2);
            List<Appointment> conflicts = appointmentRepository
                    .findByArtistIdAndScheduledStartBetween(artist.getId(), windowStart, windowEnd)
                    .stream()
                    .filter(a -> a.getStatus() != AppointmentStatus.CANCELADO && a.getStatus() != AppointmentStatus.EXPIRADO)
                    .filter(a -> {
                        if (a.getScheduledStart() == null) return true;
                        OffsetDateTime existingEnd = a.getScheduledEnd() != null ? a.getScheduledEnd() : a.getScheduledStart().plusHours(2);
                        return a.getScheduledStart().isBefore(slotEnd) && existingEnd.isAfter(slotStart);
                    })
                    .toList();

            if (conflicts.isEmpty()) {
                return candidate;
            }
            candidate = candidate.plusHours(3);
            LocalTime candidateTime = candidate.toLocalTime().withSecond(0).withNano(0);
            if (candidateTime.isAfter(closeTime) || candidateTime.isBefore(openTime)) {
                LocalTime nextDayStart = (config != null && config.getOpeningTime() != null && !config.getOpeningTime().isBlank())
                        ? openTime
                        : LocalTime.of(14, 0);
                if (nextDayStart.isAfter(closeTime)) {
                    nextDayStart = openTime;
                }
                candidate = candidate.plusDays(1)
                        .withHour(nextDayStart.getHour())
                        .withMinute(nextDayStart.getMinute())
                        .withSecond(0)
                        .withNano(0);
            }
            if (config != null) {
                candidate = adjustToOperatingSchedule(candidate, config);
            }
        }
        return candidate;
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
