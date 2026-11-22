package br.com.cefan.whatsapp.application.statemachine.handler;

import br.com.cefan.whatsapp.api.controller.StudioAdminController;
import br.com.cefan.whatsapp.application.service.AppointmentReservationService;
import br.com.cefan.whatsapp.application.service.ChatService;
import br.com.cefan.whatsapp.application.statemachine.ConversationStateHandler;
import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import br.com.cefan.whatsapp.domain.model.ConversationSession;
import br.com.cefan.whatsapp.domain.model.ConversationState;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.model.TattooSizeOption;
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
import java.text.Normalizer;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AgendamentoStateHandler implements ConversationStateHandler {

    static final String STEP_PROFISSIONAL = "PROFISSIONAL";
    static final String STEP_DATA = "DATA";
    static final String STEP_HORARIO = "HORARIO";

    static final String CTX_SCHEDULING_STEP = "schedulingStep";
    static final String CTX_CANDIDATE_ARTIST_IDS = "schedulingCandidateArtistIds";
    static final String CTX_SELECTED_ARTIST_ID = "schedulingSelectedArtistId";
    static final String CTX_SELECTED_ARTIST_NAME = "schedulingSelectedArtistName";
    static final String CTX_SELECTED_DATE = "schedulingSelectedDate";
    static final String CTX_AVAILABLE_SLOTS = "schedulingAvailableSlots";
    static final String CTX_DURATION_HOURS = "schedulingDurationHours";

    public static final ZoneId STUDIO_ZONE = ZoneId.of("America/Sao_Paulo");

    private static final int MIN_SESSION_DURATION_HOURS = 2;
    private static final int MAX_SUGGESTED_ARTISTS = 4;
    private static final int ROLLOVER_YEAR_WINDOW_DAYS = 90;
    private static final ObjectMapper FALLBACK_OBJECT_MAPPER = new ObjectMapper();

    private static final DateTimeFormatter DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));
    private static final DateTimeFormatter TIME_ONLY_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("pt-BR"));
    private static final Pattern FULL_DATE_PATTERN = Pattern.compile("(?<![\\d/.-])(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2,4})(?![\\d/.-])");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("(?<![\\d/.-])(\\d{4})-(\\d{1,2})-(\\d{1,2})(?![\\d/.-])");
    private static final Pattern SHORT_DATE_PATTERN = Pattern.compile("(?<![\\d/.-])(\\d{1,2})([/.-])(\\d{1,2})(?![\\d/.-])");
    private static final Pattern MONTH_NAME_DATE_PATTERN = Pattern.compile(
            "(?:\\bdia\\s+)?\\b(\\d{1,2})\\s+de\\s+(janeiro|jan|fevereiro|fev|marco|mar|abril|abr|maio|mai|junho|jun|julho|jul|agosto|ago|setembro|set|outubro|out|novembro|nov|dezembro|dez)(?:\\s+(?:de\\s+)?(\\d{2,4}))?\\b");
    private static final Pattern DIA_ONLY_PATTERN = Pattern.compile("\\bdia\\s+(\\d{1,2})\\b");
    private static final Pattern EXPLICIT_TIME_PATTERN = Pattern.compile("(?i)\\b(\\d{1,2})(?:\\s*:\\s*(\\d{2})|\\s*h\\s*(\\d{2})|\\s*h(?:s|oras?)?|\\s+horas?)\\b");
    private static final Pattern PREFIXED_HOUR_PATTERN = Pattern.compile("(?i)\\b(?:as|horario|das)\\s+(\\d{1,2})(?!\\d)");
    private static final Pattern PM_HOUR_PATTERN = Pattern.compile("(?i)\\b(\\d{1,2})\\s*(?:h(?:s|oras?)?|horas?)?\\s+(?:da|a|pela)\\s+(?:tarde|noite)\\b");
    private static final Pattern TIME_RANGE_PATTERN = Pattern.compile(
            "(?i)(?:\\bdas\\s+)?\\b(\\d{1,2})(?:\\s*:\\s*(\\d{2})|\\s*h\\s*(\\d{2})|\\s*h(?:s|oras?)?)?\\s*(?:\\bas\\b|\\bate\\b|-)\\s*(\\d{1,2})(?:\\s*:\\s*(\\d{2})|\\s*h\\s*(\\d{2})|\\s*h(?:s|oras?)?)?\\b");

    private static final Map<String, Integer> PT_MONTHS = Map.ofEntries(
            Map.entry("janeiro", 1), Map.entry("jan", 1),
            Map.entry("fevereiro", 2), Map.entry("fev", 2),
            Map.entry("marco", 3), Map.entry("mar", 3),
            Map.entry("abril", 4), Map.entry("abr", 4),
            Map.entry("maio", 5), Map.entry("mai", 5),
            Map.entry("junho", 6), Map.entry("jun", 6),
            Map.entry("julho", 7), Map.entry("jul", 7),
            Map.entry("agosto", 8), Map.entry("ago", 8),
            Map.entry("setembro", 9), Map.entry("set", 9),
            Map.entry("outubro", 10), Map.entry("out", 10),
            Map.entry("novembro", 11), Map.entry("nov", 11),
            Map.entry("dezembro", 12), Map.entry("dez", 12)
    );

    private static final Set<String> CONFIRMATION_INPUTS = Set.of(
            "sim", "s", "ok", "pode ser", "confirmo", "confirmar", "beleza", "fechado",
            "isso", "esse", "essa", "com ele", "com ela", "perfeito", "combinado",
            "pode ser ele", "pode ser ela", "sim pode ser", "sim com ele", "sim com ela",
            "sim confirmo", "sim quero", "o mesmo do orcamento", "a mesma do orcamento",
            "o do orcamento", "a do orcamento", "com o do orcamento", "com a do orcamento",
            "mesmo do orcamento", "mesma do orcamento", "artista do orcamento", "tatuador do orcamento"
    );

    private static final Set<String> NON_NAME_TOKENS = Set.of(
            "piercing", "piercer", "tattoo", "old", "dark", "silveira", "rocha", "lima",
            "santos", "alcantara", "fernandes", "moura", "souza", "da", "de", "do"
    );

    private static final Map<DayOfWeek, String> DAY_OF_WEEK_PT = Map.of(
            DayOfWeek.MONDAY, "Segunda-feira",
            DayOfWeek.TUESDAY, "Terça-feira",
            DayOfWeek.WEDNESDAY, "Quarta-feira",
            DayOfWeek.THURSDAY, "Quinta-feira",
            DayOfWeek.FRIDAY, "Sexta-feira",
            DayOfWeek.SATURDAY, "Sábado",
            DayOfWeek.SUNDAY, "Domingo"
    );

    private final EvolutionApiClient evolutionApiClient;
    private final AppointmentRepository appointmentRepository;
    private final MercadoPagoPaymentClient paymentClient;
    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final TenantConfigRepository tenantConfigRepository;
    private final UserRepository userRepository;
    private final StudioAdminController studioAdminController;
    private final AppointmentReservationService reservationService;
    private final Clock clock;

    public AgendamentoStateHandler(
            EvolutionApiClient evolutionApiClient,
            AppointmentRepository appointmentRepository,
            MercadoPagoPaymentClient paymentClient,
            ObjectMapper objectMapper,
            ChatService chatService,
            TenantConfigRepository tenantConfigRepository,
            UserRepository userRepository) {
        this(evolutionApiClient, appointmentRepository, paymentClient, objectMapper, chatService,
                tenantConfigRepository, userRepository, null, null, Clock.system(STUDIO_ZONE));
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
            StudioAdminController studioAdminController,
            AppointmentReservationService reservationService) {
        this(evolutionApiClient, appointmentRepository, paymentClient, objectMapper, chatService,
                tenantConfigRepository, userRepository, studioAdminController, reservationService, Clock.system(STUDIO_ZONE));
    }

    AgendamentoStateHandler(
            EvolutionApiClient evolutionApiClient,
            AppointmentRepository appointmentRepository,
            MercadoPagoPaymentClient paymentClient,
            ObjectMapper objectMapper,
            ChatService chatService,
            TenantConfigRepository tenantConfigRepository,
            UserRepository userRepository,
            StudioAdminController studioAdminController,
            Clock clock) {
        this(evolutionApiClient, appointmentRepository, paymentClient, objectMapper, chatService,
                tenantConfigRepository, userRepository, studioAdminController, null, clock);
    }

    AgendamentoStateHandler(
            EvolutionApiClient evolutionApiClient,
            AppointmentRepository appointmentRepository,
            MercadoPagoPaymentClient paymentClient,
            ObjectMapper objectMapper,
            ChatService chatService,
            TenantConfigRepository tenantConfigRepository,
            UserRepository userRepository,
            StudioAdminController studioAdminController,
            AppointmentReservationService reservationService,
            Clock clock) {
        this.evolutionApiClient = evolutionApiClient;
        this.appointmentRepository = appointmentRepository;
        this.paymentClient = paymentClient;
        this.objectMapper = objectMapper;
        this.chatService = chatService;
        this.tenantConfigRepository = tenantConfigRepository;
        this.userRepository = userRepository;
        this.studioAdminController = studioAdminController;
        this.reservationService = (reservationService != null)
                ? reservationService
                : new AppointmentReservationService(appointmentRepository, userRepository);
        this.clock = (clock != null) ? clock : Clock.system(STUDIO_ZONE);
    }

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

        Map<String, Object> context = readContextMap(session.getContextData());
        String currentStep = extractStep(context);
        List<String> currentCandidateIds = extractStringList(context, CTX_CANDIDATE_ARTIST_IDS);
        int effectiveCandidateCount = currentCandidateIds.size();
        if (STEP_PROFISSIONAL.equals(currentStep) && effectiveCandidateCount == 0) {
            effectiveCandidateCount = countRecommendedInContext(session.getContextData());
        }

        String normalizedInput = normalizeText(messageText);

        // 1. Transbordo humano explícito (sem colidir com escolha numérica 3 quando houver 3+ artistas sugeridos)
        if (isHumanHandoffRequest(normalizedInput, currentStep, effectiveCandidateCount)) {
            clearSchedulingContext(context);
            writeContextMap(session, context);
            triggerHumanHandoff(customer, session, webhook, tenantConfig,
                    "Aguardando Tatuador",
                    customer.getName() + " solicitou atendimento humano no WhatsApp.",
                    null);
            return;
        }

        // 2. Retorno explícito para orçamento com IA (sem interceptar "o mesmo do orçamento" na confirmação de artista)
        if (isNewQuoteRequest(normalizedInput)) {
            session.setContextData(null);
            session.setCurrentState(ConversationState.ORCAMENTO_AGUARDANDO_FOTO);
            String customTemplate = MenuStateHandler.resolveCleanOrcamentoIntro(tenantConfig);
            String reply = String.format("Perfeito, %s! 🎨\n\n%s\n\n💡 _Você pode digitar 0 a qualquer momento para voltar ao menu inicial._",
                    customer.getName(), customTemplate);
            sendAndRecord(session, customer, webhook, reply);
            return;
        }

        // 3. Projeto de Fechamento -> valor "A combinar", vai direto para o tatuador especialista
        if (TattooSizeOption.fromInput(messageText).filter(opt -> opt == TattooSizeOption.FECHAMENTO).isPresent()
                || isFechamentoInContext(session.getContextData())) {
            List<User> activeArtists = findActiveArtists();
            User assignedArtist = resolveBestArtistForHandoff(messageText, session.getContextData(), activeArtists);
            String artistName = (assignedArtist != null) ? assignedArtist.getName() : "Carlos Silveira";
            if (assignedArtist != null) {
                session.setAssignedUser(assignedArtist);
            }
            session.setAssignedArtistName(artistName);
            session.setCurrentState(ConversationState.TRANSBORDO_HUMANO);
            updateContextForFechamento(session, artistName);

            String absenceNotice = buildAbsenceNoticeIfOutsideHours(tenantConfig, now());
            String reply = String.format(
                    "🎨 *Projeto de Fechamento - Valor A Combinar* 🎨\n\n" +
                    "Olá, %s! Como projetos de *Fechamento (%s)* têm valor *A combinar* de acordo com a anatomia e sessões, encaminhamos sua conversa diretamente para o nosso tatuador especialista *%s*, que já vai alinhar os horários e detalhes com você por aqui!%s\n\n" +
                    "💡 _Se quiser reiniciar o assistente automático, basta enviar 0 a qualquer momento._",
                    customer.getName(),
                    TattooSizeOption.FECHAMENTO.getMeasurementLabel(),
                    artistName,
                    absenceNotice
            );
            sendAndRecord(session, customer, webhook, reply);
            if (studioAdminController != null) {
                studioAdminController.publishNotification(
                        "HUMAN_REQUEST",
                        "Fechamento - " + artistName,
                        customer.getName() + " solicitou agendamento de Fechamento (A combinar). Encaminhado para " + artistName + ".",
                        customer.getPhone()
                );
            }
            return;
        }

        // 4. Regra de duração: ceil(estimatedHours), mínimo 2h. Se sessão > expediente -> transbordo humano
        int durationHours = resolveDurationHours(session.getContextData(), objectMapper);
        if (!doesSessionFitInOperatingHours(durationHours, tenantConfig)) {
            List<User> activeArtists = findActiveArtists();
            User assignedArtist = resolveBestArtistForHandoff(messageText, session.getContextData(), activeArtists);
            routeLongSessionToHumanHandoff(customer, session, webhook, tenantConfig, durationHours, assignedArtist, context);
            return;
        }

        // 5. Despacha para a etapa atual do fluxo guiado: PROFISSIONAL -> DATA -> HORARIO
        switch (currentStep) {
            case STEP_DATA -> handleDateStep(customer, session, webhook, messageText, normalizedInput, tenantConfig, context, durationHours);
            case STEP_HORARIO -> handleTimeStep(customer, session, webhook, messageText, normalizedInput, tenantConfig, context, durationHours);
            default -> handleProfessionalStep(customer, session, webhook, messageText, normalizedInput, tenantConfig, context, durationHours);
        }
    }

    // =========================================================================
    // ETAPA 1: PROFISSIONAL
    // =========================================================================

    private void handleProfessionalStep(
            Customer customer,
            ConversationSession session,
            EvolutionWebhookRequest webhook,
            String messageText,
            String normalizedInput,
            TenantConfig tenantConfig,
            Map<String, Object> context,
            int durationHours) {

        List<User> activeArtists = findActiveArtists();
        if (activeArtists.isEmpty()) {
            clearSchedulingContext(context);
            writeContextMap(session, context);
            triggerHumanHandoff(customer, session, webhook, tenantConfig,
                    "Sem Profissionais Ativos",
                    customer.getName() + " tentou agendar, mas não há profissionais ativos cadastrados.",
                    String.format("Olá, %s! No momento não encontramos profissionais disponíveis na agenda automática. Encaminhei seu atendimento para nossa equipe humana te ajudar por aqui!", customer.getName()));
            return;
        }

        List<User> candidates = resolveCurrentCandidates(context, activeArtists, session.getContextData());

        // "qualquer disponível" fica para depois -> orientar escolha por nome ou estilo
        if (isAnyAvailableRequest(normalizedInput)) {
            String reply = buildDeferredAnyArtistReply(candidates);
            saveProfessionalStepState(session, context, candidates, durationHours);
            sendAndRecord(session, customer, webhook, reply);
            return;
        }

        // 1. Confirmação de único artista sugerido ("sim", "sim!", "1", "ok", "pode ser", "o mesmo do orçamento", etc.)
        if (candidates.size() == 1 && (stripPunctuation(normalizedInput).equals("1") || isConfirmationInput(normalizedInput, activeArtists))) {
            advanceToDateStep(customer, session, webhook, candidates.get(0), tenantConfig, context, durationHours);
            return;
        }

        // 2. Escolha numérica a partir da lista curta atual (ex: 1, 2, 3)
        Optional<Integer> numericChoice = parsePositiveInt(normalizedInput);
        if (numericChoice.isPresent()) {
            int idx = numericChoice.get();
            if (idx >= 1 && idx <= candidates.size()) {
                advanceToDateStep(customer, session, webhook, candidates.get(idx - 1), tenantConfig, context, durationHours);
                return;
            }
            String reply = candidates.isEmpty()
                    ? "⚠️ Ainda não há uma lista numerada selecionada. Por favor, informe o *nome do profissional* ou o *estilo da tatuagem*.\n\n"
                      + buildProfessionalOptionsBlock(candidates)
                    : "⚠️ Opção inválida. Por favor, escolha um número válido da lista ou informe o nome/estilo do profissional.\n\n"
                      + buildProfessionalOptionsBlock(candidates);
            saveProfessionalStepState(session, context, candidates, durationHours);
            sendAndRecord(session, customer, webhook, reply);
            return;
        }

        // 3. Busca explícita por nome do profissional entre todos os artistas ativos
        List<User> nameMatches = findArtistsByName(normalizedInput, activeArtists);
        if (nameMatches.size() == 1) {
            advanceToDateStep(customer, session, webhook, nameMatches.get(0), tenantConfig, context, durationHours);
            return;
        }
        if (nameMatches.size() > 1) {
            List<User> limited = nameMatches.subList(0, Math.min(MAX_SUGGESTED_ARTISTS, nameMatches.size()));
            saveProfessionalStepState(session, context, limited, durationHours);
            String reply = "Encontrei mais de um profissional com esse nome. Qual você prefere?\n\n"
                    + buildProfessionalOptionsBlock(limited);
            sendAndRecord(session, customer, webhook, reply);
            return;
        }

        // 4. Busca por estilo de tatuagem / especialidade (nunca lista todos os 11 de uma vez)
        List<User> styleMatches = findArtistsByStyle(normalizedInput, activeArtists);
        if (!styleMatches.isEmpty()) {
            List<User> limited = styleMatches.subList(0, Math.min(MAX_SUGGESTED_ARTISTS, styleMatches.size()));
            saveProfessionalStepState(session, context, limited, durationHours);
            String intro = limited.size() == 1
                    ? String.format("Para esse estilo, o especialista indicado é *%s* (_%s_).",
                        limited.get(0).getName(), formatSpecialties(limited.get(0)))
                    : "Para esse estilo, estes são os especialistas indicados:";
            String reply = intro + "\n\n" + buildProfessionalOptionsBlock(limited);
            sendAndRecord(session, customer, webhook, reply);
            return;
        }

        // 5. Entrada inválida/inesperada -> resposta amigável + reenvio das opções
        saveProfessionalStepState(session, context, candidates, durationHours);
        String reply = "Não consegui identificar esse profissional ou estilo. 😅\n\n"
                + buildProfessionalOptionsBlock(candidates);
        sendAndRecord(session, customer, webhook, reply);
    }

    private void advanceToDateStep(
            Customer customer,
            ConversationSession session,
            EvolutionWebhookRequest webhook,
            User selectedArtist,
            TenantConfig tenantConfig,
            Map<String, Object> context,
            int durationHours) {

        session.setAssignedUser(selectedArtist);
        session.setAssignedArtistName(selectedArtist.getName());

        context.put(CTX_SCHEDULING_STEP, STEP_DATA);
        context.put(CTX_SELECTED_ARTIST_ID, selectedArtist.getId().toString());
        context.put(CTX_SELECTED_ARTIST_NAME, selectedArtist.getName());
        context.put(CTX_DURATION_HOURS, durationHours);
        context.remove(CTX_SELECTED_DATE);
        context.remove(CTX_AVAILABLE_SLOTS);
        writeContextMap(session, context);

        String reply = String.format(
                "✅ Profissional confirmado: *%s*\n" +
                "⏱️ Duração estimada da sessão: *%dh*\n\n" +
                "%s",
                selectedArtist.getName(),
                durationHours,
                buildDatePromptBlock(tenantConfig)
        );
        sendAndRecord(session, customer, webhook, reply);
    }

    // =========================================================================
    // ETAPA 2: DATA
    // =========================================================================

    private void handleDateStep(
            Customer customer,
            ConversationSession session,
            EvolutionWebhookRequest webhook,
            String messageText,
            String normalizedInput,
            TenantConfig tenantConfig,
            Map<String, Object> context,
            int durationHours) {

        List<User> activeArtists = findActiveArtists();

        if (isChangeProfessionalRequest(normalizedInput)) {
            List<User> recommended = findRecommendedActiveArtists(session.getContextData(), objectMapper, activeArtists);
            saveProfessionalStepState(session, context, recommended, durationHours);
            String reply = "Sem problemas! Vamos escolher outro profissional.\n\n"
                    + buildProfessionalOptionsBlock(recommended);
            sendAndRecord(session, customer, webhook, reply);
            return;
        }

        User artist = resolveSelectedArtist(context, activeArtists);
        if (artist == null) {
            List<User> recommended = findRecommendedActiveArtists(session.getContextData(), objectMapper, activeArtists);
            saveProfessionalStepState(session, context, recommended, durationHours);
            String reply = "⚠️ O profissional selecionado anteriormente não está mais disponível. Vamos escolher novamente:\n\n"
                    + buildProfessionalOptionsBlock(recommended);
            sendAndRecord(session, customer, webhook, reply);
            return;
        }

        OffsetDateTime currentNow = now();
        LocalDate today = currentNow.toLocalDate();
        DateParseResult parseResult = parseDateInput(messageText, today, currentNow, tenantConfig);

        switch (parseResult.status()) {
            case INVALID_CALENDAR_DATE -> {
                String reply = "⚠️ A data informada não existe no calendário. Por favor, informe uma data válida.\n\n"
                        + buildDatePromptBlock(tenantConfig);
                sendAndRecord(session, customer, webhook, reply);
                return;
            }
            case PAST_DATE -> {
                String pastLabel = parseResult.date() != null ? parseResult.date().format(DATE_ONLY_FORMATTER) : "";
                String reply = String.format(
                        "⚠️ A data *%s* já passou! Por favor, escolha uma data a partir de hoje (%s) ou informe o ano completo para datas do próximo ano.\n\n%s",
                        pastLabel,
                        today.format(DATE_ONLY_FORMATTER),
                        buildDatePromptBlock(tenantConfig)
                );
                sendAndRecord(session, customer, webhook, reply);
                return;
            }
            case UNRECOGNIZED -> {
                String reply = "Não consegui entender a data informada. 😅\n\n"
                        + buildDatePromptBlock(tenantConfig);
                sendAndRecord(session, customer, webhook, reply);
                return;
            }
            case VALID -> {
                LocalDate selectedDate = parseResult.date();
                if (selectedDate.isBefore(today)) {
                    String reply = String.format(
                            "⚠️ A data *%s* já passou! Por favor, escolha uma data futura.\n\n%s",
                            selectedDate.format(DATE_ONLY_FORMATTER),
                            buildDatePromptBlock(tenantConfig)
                    );
                    sendAndRecord(session, customer, webhook, reply);
                    return;
                }

                // Valida se o artista está ativo e se o estúdio abre no dia escolhido (working_days por artista ainda não existe em users)
                if (!isArtistActiveOnStudioWorkingDay(artist, selectedDate.getDayOfWeek(), tenantConfig)) {
                    LocalTime openTime = resolveOpeningTime(tenantConfig);
                    LocalTime closeTime = resolveClosingTime(tenantConfig);
                    String reply = String.format(
                            "⚠️ O estúdio não tem expediente em *%s* (%s).\n" +
                            "📅 Nosso funcionamento é de *%s*, das *%s às %s*.\n\n%s",
                            formatDayOfWeekPt(selectedDate.getDayOfWeek()),
                            selectedDate.format(DATE_ONLY_FORMATTER),
                            formatWorkingDaysLabel(tenantConfig),
                            openTime.format(TIME_ONLY_FORMATTER),
                            closeTime.format(TIME_ONLY_FORMATTER),
                            buildDatePromptBlock(tenantConfig)
                    );
                    sendAndRecord(session, customer, webhook, reply);
                    return;
                }

                // Calcula horários disponíveis de hora em hora (bloco inteiro no expediente e sem sobreposição)
                List<LocalTime> availableSlots = findAvailableSlots(artist, selectedDate, durationHours, tenantConfig, currentNow);
                if (availableSlots.isEmpty()) {
                    String reason = selectedDate.equals(today)
                            ? "não há mais horários livres suficientes para hoje"
                            : "a agenda está completa nessa data";
                    String reply = String.format(
                            "😕 Para *%s* em *%s (%s)* (%dh de sessão), %s.\n\n" +
                            "Por favor, escolha outra data ou digite *trocar profissional*.\n\n%s",
                            artist.getName(),
                            selectedDate.format(DATE_ONLY_FORMATTER),
                            formatDayOfWeekPt(selectedDate.getDayOfWeek()),
                            durationHours,
                            reason,
                            buildDatePromptBlock(tenantConfig)
                    );
                    sendAndRecord(session, customer, webhook, reply);
                    return;
                }

                // Avança para Etapa 3 (HORARIO)
                context.put(CTX_SCHEDULING_STEP, STEP_HORARIO);
                context.put(CTX_SELECTED_DATE, selectedDate.toString());
                context.put(CTX_AVAILABLE_SLOTS, availableSlots.stream().map(t -> t.format(TIME_ONLY_FORMATTER)).toList());
                context.put(CTX_DURATION_HOURS, durationHours);
                writeContextMap(session, context);

                String reply = buildTimeSlotsPrompt(artist, selectedDate, availableSlots, durationHours);
                sendAndRecord(session, customer, webhook, reply);
            }
        }
    }

    // =========================================================================
    // ETAPA 3: HORÁRIO -> RECHECAGEM -> PIX -> RESERVA
    // =========================================================================

    private void handleTimeStep(
            Customer customer,
            ConversationSession session,
            EvolutionWebhookRequest webhook,
            String messageText,
            String normalizedInput,
            TenantConfig tenantConfig,
            Map<String, Object> context,
            int durationHours) {

        List<User> activeArtists = findActiveArtists();

        if (isChangeProfessionalRequest(normalizedInput)) {
            List<User> recommended = findRecommendedActiveArtists(session.getContextData(), objectMapper, activeArtists);
            saveProfessionalStepState(session, context, recommended, durationHours);
            String reply = "Sem problemas! Vamos escolher outro profissional.\n\n"
                    + buildProfessionalOptionsBlock(recommended);
            sendAndRecord(session, customer, webhook, reply);
            return;
        }

        User artist = resolveSelectedArtist(context, activeArtists);
        if (artist == null) {
            List<User> recommended = findRecommendedActiveArtists(session.getContextData(), objectMapper, activeArtists);
            saveProfessionalStepState(session, context, recommended, durationHours);
            String reply = "⚠️ Não foi possível confirmar o profissional selecionado. Vamos escolher novamente:\n\n"
                    + buildProfessionalOptionsBlock(recommended);
            sendAndRecord(session, customer, webhook, reply);
            return;
        }

        if (isChangeDateRequest(normalizedInput)) {
            advanceToDateStep(customer, session, webhook, artist, tenantConfig, context, durationHours);
            return;
        }

        LocalDate selectedDate = parseStoredDate(context);
        List<LocalTime> offeredSlots = parseStoredSlots(context);
        if (selectedDate == null || offeredSlots.isEmpty()) {
            advanceToDateStep(customer, session, webhook, artist, tenantConfig, context, durationHours);
            return;
        }

        Optional<LocalTime> parsedTime = parseTimeSelection(messageText, offeredSlots);
        if (parsedTime.isEmpty()) {
            String reply = "Não consegui identificar o horário escolhido. 😅\n\n"
                    + buildTimeSlotsPrompt(artist, selectedDate, offeredSlots, durationHours);
            sendAndRecord(session, customer, webhook, reply);
            return;
        }

        LocalTime chosenTime = parsedTime.get();
        if (!offeredSlots.contains(chosenTime)) {
            String reply = String.format(
                    "⚠️ O horário *%s* não está entre as opções disponíveis para *%s* em *%s*.\n\n%s",
                    chosenTime.format(TIME_ONLY_FORMATTER),
                    artist.getName(),
                    selectedDate.format(DATE_ONLY_FORMATTER),
                    buildTimeSlotsPrompt(artist, selectedDate, offeredSlots, durationHours)
            );
            sendAndRecord(session, customer, webhook, reply);
            return;
        }

        OffsetDateTime currentNow = now();
        OffsetDateTime scheduledStart = toOffsetDateTime(selectedDate, chosenTime);
        OffsetDateTime scheduledEnd = scheduledStart.plusHours(durationHours);

        int expirationMinutes = (tenantConfig != null && tenantConfig.getPixExpirationMinutes() != null && tenantConfig.getPixExpirationMinutes() > 0)
                ? tenantConfig.getPixExpirationMinutes()
                : 120;
        OffsetDateTime expiresAt = currentNow.plusMinutes(expirationMinutes);

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
        if (remainingAmount.compareTo(BigDecimal.ZERO) < 0) {
            remainingAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        // 1. Transação curta (REQUIRES_NEW): FOR UPDATE no artista -> expira vencidos -> valida -> saveAndFlush -> COMMIT (libera o lock!)
        Optional<Appointment> reservedOpt = reservationService.reserveSlotAtomically(
                customer, artist, scheduledStart, scheduledEnd,
                totalPrice, depositAmount, expiresAt, tenantConfig, currentNow);
        if (reservedOpt.isEmpty()) {
            handleSlotNoLongerAvailable(customer, session, webhook, artist, selectedDate, chosenTime, durationHours, tenantConfig, context);
            return;
        }

        Appointment reserved = reservedOpt.get();
        User reservedArtist = reserved.getArtist() != null ? reserved.getArtist() : artist;

        // 2. FORA do lock e APÓS commit da reserva: cria cobrança PIX com date_of_expiration = expires_at da reserva
        String payerEmail = customer.getPhone() + "@cefan.app";
        String description = "Sinal Agendamento (" + reservedArtist.getName() + ") - Vício Tattoo";
        MercadoPagoPaymentResponse paymentResponse;
        try {
            paymentResponse = paymentClient.createPixPayment(
                    depositAmount, description, payerEmail, customer.getName(), reserved.getExpiresAt());
        } catch (Exception e) {
            log.error("Falha ao criar pagamento PIX para cliente [{}]: {}. Cancelando reserva em REQUIRES_NEW.",
                    customer.getPhone(), e.getMessage(), e);
            reservationService.cancelTemporaryReservation(reserved);
            handlePixCreationFailure(customer, session, webhook, reservedArtist, scheduledStart, tenantConfig, context);
            return;
        }

        String paymentId = (paymentResponse != null && paymentResponse.getId() != null)
                ? String.valueOf(paymentResponse.getId())
                : null;
        String pixCopyPaste = (paymentResponse != null) ? paymentResponse.extractPixCopyPaste() : null;
        String pixQrCodeBase64 = (paymentResponse != null) ? paymentResponse.extractPixQrCodeBase64() : null;
        String ticketUrl = (paymentResponse != null) ? paymentResponse.extractTicketUrl() : null;

        if (paymentId == null || pixCopyPaste == null || pixCopyPaste.isBlank()) {
            log.error("Mercado Pago retornou resposta PIX inválida/incompleta para cliente [{}] (paymentId={}, pixPresent={}). Cancelando reserva em REQUIRES_NEW.",
                    customer.getPhone(), paymentId, pixCopyPaste != null && !pixCopyPaste.isBlank());
            reservationService.cancelTemporaryReservation(reserved);
            handlePixCreationFailure(customer, session, webhook, reservedArtist, scheduledStart, tenantConfig, context);
            return;
        }

        // 3. Transação curta (REQUIRES_NEW): vincula dados do PIX na reserva já comitada
        try {
            reservationService.attachPixDetails(reserved, paymentId, pixCopyPaste, pixQrCodeBase64, ticketUrl);
        } catch (Exception e) {
            log.error("Falha ao vincular dados do PIX [{}] na reserva [{}]: {}. Cancelando reserva em REQUIRES_NEW e acionando transbordo.",
                    paymentId, reserved.getId(), e.getMessage(), e);
            try {
                reservationService.cancelTemporaryReservation(reserved);
            } catch (Exception cancelEx) {
                log.error("Falha ao cancelar reserva [{}] após erro em attachPixDetails: {}",
                        reserved.getId(), cancelEx.getMessage(), cancelEx);
            }
            handlePixCreationFailure(customer, session, webhook, reservedArtist, scheduledStart, tenantConfig, context);
            return;
        }

        session.setAssignedUser(reservedArtist);
        session.setAssignedArtistName(reservedArtist.getName());
        session.setCurrentState(ConversationState.MENU);
        clearSchedulingContext(context);
        writeContextMap(session, context);

        // Mensagem 1: Resumo unificado (sem introdução duplicada)
        String summaryMessage = buildUnifiedPixSummaryMessage(
                customer, reservedArtist.getName(), scheduledStart, scheduledEnd, durationHours,
                depositAmount, remainingAmount, expirationMinutes, tenantConfig);
        sendAndRecord(session, customer, webhook, summaryMessage);

        // Mensagem 2: Apenas o código PIX Copia e Cola em mensagem separada para facilitar copiar
        sendAndRecord(session, customer, webhook, pixCopyPaste.trim());
    }

    private void handleSlotNoLongerAvailable(
            Customer customer,
            ConversationSession session,
            EvolutionWebhookRequest webhook,
            User artist,
            LocalDate selectedDate,
            LocalTime chosenTime,
            int durationHours,
            TenantConfig tenantConfig,
            Map<String, Object> context) {

        OffsetDateTime currentNow = now();
        List<LocalTime> freshSlots = findAvailableSlots(artist, selectedDate, durationHours, tenantConfig, currentNow);
        if (!freshSlots.isEmpty()) {
            context.put(CTX_SCHEDULING_STEP, STEP_HORARIO);
            context.put(CTX_SELECTED_DATE, selectedDate.toString());
            context.put(CTX_AVAILABLE_SLOTS, freshSlots.stream().map(t -> t.format(TIME_ONLY_FORMATTER)).toList());
            writeContextMap(session, context);

            String reply = String.format(
                    "⚠️ Poxa, o horário *%s* de *%s* acabou de ser reservado! Nenhum pré-agendamento foi criado.\n\n%s",
                    chosenTime.format(TIME_ONLY_FORMATTER),
                    selectedDate.format(DATE_ONLY_FORMATTER),
                    buildTimeSlotsPrompt(artist, selectedDate, freshSlots, durationHours)
            );
            sendAndRecord(session, customer, webhook, reply);
        } else {
            context.put(CTX_SCHEDULING_STEP, STEP_DATA);
            context.remove(CTX_SELECTED_DATE);
            context.remove(CTX_AVAILABLE_SLOTS);
            writeContextMap(session, context);

            String reply = String.format(
                    "⚠️ Poxa, o horário *%s* de *%s* acabou de ser reservado e não restaram vagas livres para *%s* nessa data.\n\n%s",
                    chosenTime.format(TIME_ONLY_FORMATTER),
                    selectedDate.format(DATE_ONLY_FORMATTER),
                    artist.getName(),
                    buildDatePromptBlock(tenantConfig)
            );
            sendAndRecord(session, customer, webhook, reply);
        }
    }

    private void handlePixCreationFailure(
            Customer customer,
            ConversationSession session,
            EvolutionWebhookRequest webhook,
            User artist,
            OffsetDateTime scheduledStart,
            TenantConfig tenantConfig,
            Map<String, Object> context) {

        clearSchedulingContext(context);
        writeContextMap(session, context);

        if (artist != null) {
            session.setAssignedUser(artist);
            session.setAssignedArtistName(artist.getName());
        }
        session.setCurrentState(ConversationState.TRANSBORDO_HUMANO);

        String artistName = (artist != null) ? artist.getName() : "nossa equipe";
        String slotLabel = scheduledStart.format(DATE_ONLY_FORMATTER) + " às " + scheduledStart.format(TIME_ONLY_FORMATTER);
        String absenceNotice = buildAbsenceNoticeIfOutsideHours(tenantConfig, now());

        String reply = String.format(
                "⚠️ Ops, %s! Tivemos uma instabilidade temporária ao gerar o código PIX para o seu sinal.\n\n" +
                "Fique tranquilo(a): *nenhum pré-agendamento ou cobrança foi criado*. Encaminhei sua solicitação de horário (*%s* com *%s*) para nossa equipe finalizar seu agendamento diretamente por aqui em instantes!%s\n\n" +
                "💡 _Se quiser reiniciar o assistente automático, basta enviar 0 a qualquer momento._",
                customer.getName(),
                slotLabel,
                artistName,
                absenceNotice
        );

        sendAndRecord(session, customer, webhook, reply);
        if (studioAdminController != null) {
            studioAdminController.publishNotification(
                    "HUMAN_REQUEST",
                    "Falha ao gerar PIX - " + artistName,
                    customer.getName() + " tentou agendar " + slotLabel + " com " + artistName + ", mas a geração do PIX falhou.",
                    customer.getPhone()
            );
        }
    }

    private void routeLongSessionToHumanHandoff(
            Customer customer,
            ConversationSession session,
            EvolutionWebhookRequest webhook,
            TenantConfig tenantConfig,
            int durationHours,
            User assignedArtist,
            Map<String, Object> context) {

        clearSchedulingContext(context);
        writeContextMap(session, context);

        String artistName = (assignedArtist != null) ? assignedArtist.getName() : "nossa equipe especialista";
        if (assignedArtist != null) {
            session.setAssignedUser(assignedArtist);
            session.setAssignedArtistName(assignedArtist.getName());
        }
        session.setCurrentState(ConversationState.TRANSBORDO_HUMANO);

        LocalTime openTime = resolveOpeningTime(tenantConfig);
        LocalTime closeTime = resolveClosingTime(tenantConfig);
        String absenceNotice = buildAbsenceNoticeIfOutsideHours(tenantConfig, now());

        String reply = String.format(
                "🎨 *Sessão Especial (%dh) — Atendimento Personalizado* 🎨\n\n" +
                "Olá, %s! Como o tempo estimado da sua sessão (*%dh*) ultrapassa nosso expediente diário (*%s às %s*), encaminhei sua conversa diretamente para *%s* organizar a melhor divisão de sessões e horários com você!%s\n\n" +
                "💡 _Se quiser reiniciar o assistente automático, basta enviar 0 a qualquer momento._",
                durationHours,
                customer.getName(),
                durationHours,
                openTime.format(TIME_ONLY_FORMATTER),
                closeTime.format(TIME_ONLY_FORMATTER),
                artistName,
                absenceNotice
        );

        sendAndRecord(session, customer, webhook, reply);
        if (studioAdminController != null) {
            studioAdminController.publishNotification(
                    "HUMAN_REQUEST",
                    "Sessão Longa (" + durationHours + "h)",
                    customer.getName() + " possui sessão estimada em " + durationHours + "h (maior que o expediente). Encaminhado para " + artistName + ".",
                    customer.getPhone()
            );
        }
    }

    // =========================================================================
    // REGRAS DE DURAÇÃO, EXPEDIENTE E DISPONIBILIDADE DE SLOTS
    // =========================================================================

    public static int resolveDurationHours(String contextData, ObjectMapper objectMapper) {
        if (contextData == null || contextData.isBlank() || objectMapper == null) {
            return MIN_SESSION_DURATION_HOURS;
        }
        try {
            JsonNode node = objectMapper.readTree(contextData);
            if (node.hasNonNull("estimatedHours")) {
                double estimated = node.get("estimatedHours").asDouble(0.0);
                if (estimated > 0.0) {
                    return Math.max(MIN_SESSION_DURATION_HOURS, (int) Math.ceil(estimated));
                }
            }
        } catch (Exception ignored) {
        }
        return MIN_SESSION_DURATION_HOURS;
    }

    public static boolean doesSessionFitInOperatingHours(int durationHours, TenantConfig config) {
        int safeDuration = Math.max(MIN_SESSION_DURATION_HOURS, durationHours);
        LocalTime openTime = resolveOpeningTime(config);
        LocalTime closeTime = resolveClosingTime(config);
        if (!openTime.isBefore(closeTime)) {
            return false;
        }
        int firstSlotHour = openTime.getMinute() == 0 ? openTime.getHour() : openTime.getHour() + 1;
        LocalDate refDate = LocalDate.of(2026, 1, 1);
        for (int hour = firstSlotHour; hour <= 23; hour++) {
            LocalDateTime start = refDate.atTime(hour, 0);
            LocalDateTime end = start.plusHours(safeDuration);
            if (end.toLocalDate().equals(refDate) && !end.toLocalTime().isAfter(closeTime)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Valida se o profissional está ativo E se o estúdio abre no dia da semana informado.
     *
     * <p><b>Limitação conhecida do modelo de dados:</b> a tabela {@code users} ainda não possui
     * coluna própria de {@code working_days} por artista. Portanto, este método verifica apenas
     * {@code artist.isActive} e {@code TenantConfig.workingDays} (expediente geral do estúdio).
     * Quando o estúdio definir os dias individuais de cada tatuador, uma migração adicionando
     * {@code users.working_days} deverá ser consultada aqui.</p>
     */
    public static boolean isArtistActiveOnStudioWorkingDay(User artist, DayOfWeek dayOfWeek, TenantConfig config) {
        if (artist == null || !Boolean.TRUE.equals(artist.getIsActive()) || dayOfWeek == null) {
            return false;
        }
        return isWorkingDay(dayOfWeek, config);
    }

    /**
     * Alias mantido por compatibilidade. Veja {@link #isArtistActiveOnStudioWorkingDay}.
     */
    public static boolean artistWorksOnDay(User artist, DayOfWeek dayOfWeek, TenantConfig config) {
        return isArtistActiveOnStudioWorkingDay(artist, dayOfWeek, config);
    }

    public static boolean isWorkingDay(DayOfWeek dayOfWeek, TenantConfig config) {
        if (dayOfWeek == null) {
            return false;
        }
        String raw = (config != null && config.getWorkingDays() != null && !config.getWorkingDays().isBlank())
                ? config.getWorkingDays()
                : "SEG-SAB";
        String normalized = normalizeText(raw).toUpperCase(Locale.ROOT).replace(" ", "");

        if (normalized.equals("SEG-DOM") || normalized.equals("TODOS")) {
            return true;
        }
        if (normalized.equals("SEG-SAB")) {
            return dayOfWeek != DayOfWeek.SUNDAY;
        }
        if (normalized.equals("SEG-SEX")) {
            return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
        }

        // Suporte a intervalos genéricos (ex: TER-SAB, QUA-DOM)
        if (normalized.contains("-") && !normalized.contains(",")) {
            String[] parts = normalized.split("-");
            if (parts.length == 2) {
                DayOfWeek start = parseDayAbbrev(parts[0]);
                DayOfWeek end = parseDayAbbrev(parts[1]);
                if (start != null && end != null) {
                    int s = start.getValue();
                    int e = end.getValue();
                    int d = dayOfWeek.getValue();
                    return s <= e ? (d >= s && d <= e) : (d >= s || d <= e);
                }
            }
        }

        // Suporte a listas separadas por vírgula ou barra (ex: SEG,QUA,SEX)
        String[] tokens = normalized.split("[,/;]+");
        boolean parsedAny = false;
        for (String token : tokens) {
            DayOfWeek parsed = parseDayAbbrev(token);
            if (parsed != null) {
                parsedAny = true;
                if (parsed == dayOfWeek) {
                    return true;
                }
            }
        }
        if (parsedAny) {
            return false;
        }

        // Fallback seguro: SEG-SAB
        return dayOfWeek != DayOfWeek.SUNDAY;
    }

    List<LocalTime> findAvailableSlots(User artist, LocalDate date, int durationHours, TenantConfig config, OffsetDateTime referenceNow) {
        if (artist == null || date == null || !artistWorksOnDay(artist, date.getDayOfWeek(), config)) {
            return List.of();
        }
        int safeDuration = Math.max(MIN_SESSION_DURATION_HOURS, durationHours);
        LocalTime openTime = resolveOpeningTime(config);
        LocalTime closeTime = resolveClosingTime(config);
        if (!openTime.isBefore(closeTime)) {
            return List.of();
        }

        OffsetDateTime queryStart = toOffsetDateTime(date.minusDays(1), LocalTime.MIN);
        OffsetDateTime queryEnd = toOffsetDateTime(date.plusDays(1), LocalTime.MIN);
        List<Appointment> existing = fetchBlockingAppointments(artist.getId(), queryStart, queryEnd, referenceNow);

        int firstSlotHour = openTime.getMinute() == 0 ? openTime.getHour() : openTime.getHour() + 1;
        List<LocalTime> available = new ArrayList<>();

        for (int hour = firstSlotHour; hour <= 23; hour++) {
            LocalTime candidateTime = LocalTime.of(hour, 0);
            LocalDateTime startLdt = date.atTime(candidateTime);
            LocalDateTime endLdt = startLdt.plusHours(safeDuration);

            if (!endLdt.toLocalDate().equals(date) || endLdt.toLocalTime().isAfter(closeTime)) {
                continue;
            }

            OffsetDateTime slotStart = toOffsetDateTime(date, candidateTime);
            if (!slotStart.isAfter(referenceNow)) {
                continue;
            }
            OffsetDateTime slotEnd = slotStart.plusHours(safeDuration);

            if (!hasOverlap(existing, slotStart, slotEnd)) {
                available.add(candidateTime);
            }
        }
        return available;
    }

    boolean isSlotAvailable(User artist, OffsetDateTime slotStart, OffsetDateTime slotEnd, TenantConfig config, OffsetDateTime referenceNow) {
        if (artist == null || slotStart == null || slotEnd == null || !slotEnd.isAfter(slotStart)) {
            return false;
        }
        if (!slotStart.isAfter(referenceNow)) {
            return false;
        }
        LocalDate date = slotStart.toLocalDate();
        if (!artistWorksOnDay(artist, date.getDayOfWeek(), config)) {
            return false;
        }
        if (!slotEnd.toLocalDate().equals(date)) {
            return false;
        }

        LocalTime openTime = resolveOpeningTime(config);
        LocalTime closeTime = resolveClosingTime(config);
        LocalTime startTime = slotStart.toLocalTime().withSecond(0).withNano(0);
        LocalTime endTime = slotEnd.toLocalTime().withSecond(0).withNano(0);
        if (startTime.isBefore(openTime) || endTime.isAfter(closeTime)) {
            return false;
        }

        OffsetDateTime queryStart = toOffsetDateTime(date.minusDays(1), LocalTime.MIN);
        OffsetDateTime queryEnd = toOffsetDateTime(date.plusDays(1), LocalTime.MIN);
        List<Appointment> existing = fetchBlockingAppointments(artist.getId(), queryStart, queryEnd, referenceNow);
        return !hasOverlap(existing, slotStart, slotEnd);
    }

    private List<Appointment> fetchBlockingAppointments(UUID artistId, OffsetDateTime queryStart, OffsetDateTime queryEnd, OffsetDateTime referenceNow) {
        if (artistId == null) {
            return List.of();
        }
        List<Appointment> raw = appointmentRepository.findByArtistIdAndScheduledStartBetween(artistId, queryStart, queryEnd);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELADO && a.getStatus() != AppointmentStatus.EXPIRADO)
                .filter(a -> !(a.getStatus() == AppointmentStatus.AGUARDANDO_PAGAMENTO
                        && a.getExpiresAt() != null
                        && !a.getExpiresAt().isAfter(referenceNow)))
                .toList();
    }

    private boolean hasOverlap(List<Appointment> blockingAppointments, OffsetDateTime slotStart, OffsetDateTime slotEnd) {
        for (Appointment appt : blockingAppointments) {
            if (appt.getScheduledStart() == null) {
                continue;
            }
            OffsetDateTime existingStart = appt.getScheduledStart();
            OffsetDateTime existingEnd = appt.getScheduledEnd() != null
                    ? appt.getScheduledEnd()
                    : existingStart.plusHours(MIN_SESSION_DURATION_HOURS);
            if (existingStart.isBefore(slotEnd) && existingEnd.isAfter(slotStart)) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // PARSING ESTRITO DE DATA E HORÁRIO
    // =========================================================================

    enum DateParseStatus {
        VALID,
        PAST_DATE,
        INVALID_CALENDAR_DATE,
        UNRECOGNIZED
    }

    record DateParseResult(DateParseStatus status, LocalDate date) {}

    DateParseResult parseDateInput(String messageText, LocalDate today, OffsetDateTime currentNow, TenantConfig tenantConfig) {
        String normalized = normalizeText(messageText);
        if (normalized.isBlank()) {
            return new DateParseResult(DateParseStatus.UNRECOGNIZED, null);
        }

        // 1. Data completa ISO yyyy-MM-dd
        Matcher isoMatcher = ISO_DATE_PATTERN.matcher(normalized);
        if (isoMatcher.find()) {
            int year = Integer.parseInt(isoMatcher.group(1));
            int month = Integer.parseInt(isoMatcher.group(2));
            int day = Integer.parseInt(isoMatcher.group(3));
            return resolveExplicitYearDate(year, month, day, today);
        }

        // 2. Data completa dd/MM/yyyy (ou dd-MM-yyyy / dd.MM.yyyy)
        Matcher fullMatcher = FULL_DATE_PATTERN.matcher(normalized);
        if (fullMatcher.find()) {
            int day = Integer.parseInt(fullMatcher.group(1));
            int month = Integer.parseInt(fullMatcher.group(2));
            int year = Integer.parseInt(fullMatcher.group(3));
            if (year < 100) {
                year += 2000;
            }
            return resolveExplicitYearDate(year, month, day, today);
        }

        // 3. Data por extenso em PT-BR (ex: "25 de novembro", "dia 5 de dezembro", "25 de dez de 2026")
        Matcher monthNameMatcher = MONTH_NAME_DATE_PATTERN.matcher(normalized);
        if (monthNameMatcher.find()) {
            int day = Integer.parseInt(monthNameMatcher.group(1));
            Integer month = PT_MONTHS.get(monthNameMatcher.group(2));
            String yearGroup = monthNameMatcher.group(3);
            if (month != null) {
                if (yearGroup != null && !yearGroup.isBlank()) {
                    int year = Integer.parseInt(yearGroup);
                    if (year < 100) {
                        year += 2000;
                    }
                    return resolveExplicitYearDate(year, month, day, today);
                }
                return resolveDayMonthWithRollover(day, month, today);
            }
        }

        // 4. Data curta dd/MM (ou dd-MM / dd.MM), sem confundir com decimais tipo "2.5 horas" ou anos malformados
        Matcher shortMatcher = SHORT_DATE_PATTERN.matcher(normalized);
        while (shortMatcher.find()) {
            String separator = shortMatcher.group(2);
            if (".".equals(separator) && isLikelyDecimalNumber(normalized, shortMatcher.start(), shortMatcher.end())) {
                continue;
            }
            int day = Integer.parseInt(shortMatcher.group(1));
            int month = Integer.parseInt(shortMatcher.group(3));
            return resolveDayMonthWithRollover(day, month, today);
        }

        // 5. Expressões relativas
        if (containsWord(normalized, "ontem")) {
            return new DateParseResult(DateParseStatus.PAST_DATE, today.minusDays(1));
        }
        if (normalized.contains("depois de amanha")) {
            return new DateParseResult(DateParseStatus.VALID, today.plusDays(2));
        }
        if (containsWord(normalized, "amanha")) {
            return new DateParseResult(DateParseStatus.VALID, today.plusDays(1));
        }
        if (containsWord(normalized, "hoje")) {
            return new DateParseResult(DateParseStatus.VALID, today);
        }

        // 6. Dia da semana
        DayOfWeek dow = extractDayOfWeek(normalized);
        if (dow != null) {
            boolean explicitNextWeek = normalized.contains("proxima") || normalized.contains("proximo") || normalized.contains("que vem");
            LocalDate resolved;
            if (today.getDayOfWeek() == dow) {
                LocalTime closeTime = resolveClosingTime(tenantConfig);
                if (!explicitNextWeek && currentNow.toLocalTime().isBefore(closeTime)) {
                    resolved = today;
                } else {
                    resolved = today.plusDays(7);
                }
            } else {
                resolved = today.with(TemporalAdjusters.next(dow));
            }
            return new DateParseResult(DateParseStatus.VALID, resolved);
        }

        // 7. "dia 25" (sem mês explícito -> mês atual)
        Matcher diaMatcher = DIA_ONLY_PATTERN.matcher(normalized);
        if (diaMatcher.find()) {
            int day = Integer.parseInt(diaMatcher.group(1));
            try {
                LocalDate parsed = LocalDate.of(today.getYear(), today.getMonth(), day);
                if (parsed.isBefore(today)) {
                    return new DateParseResult(DateParseStatus.PAST_DATE, parsed);
                }
                return new DateParseResult(DateParseStatus.VALID, parsed);
            } catch (DateTimeException e) {
                return new DateParseResult(DateParseStatus.INVALID_CALENDAR_DATE, null);
            }
        }

        return new DateParseResult(DateParseStatus.UNRECOGNIZED, null);
    }

    private DateParseResult resolveExplicitYearDate(int year, int month, int day, LocalDate today) {
        try {
            LocalDate parsed = LocalDate.of(year, month, day);
            if (parsed.isBefore(today)) {
                return new DateParseResult(DateParseStatus.PAST_DATE, parsed);
            }
            return new DateParseResult(DateParseStatus.VALID, parsed);
        } catch (DateTimeException e) {
            return new DateParseResult(DateParseStatus.INVALID_CALENDAR_DATE, null);
        }
    }

    private DateParseResult resolveDayMonthWithRollover(int day, int month, LocalDate today) {
        try {
            LocalDate parsed = LocalDate.of(today.getYear(), month, day);
            if (!parsed.isBefore(today)) {
                return new DateParseResult(DateParseStatus.VALID, parsed);
            }
            try {
                LocalDate nextYearCandidate = LocalDate.of(today.getYear() + 1, month, day);
                long daysUntilNextYear = ChronoUnit.DAYS.between(today, nextYearCandidate);
                if (daysUntilNextYear >= 0 && daysUntilNextYear <= ROLLOVER_YEAR_WINDOW_DAYS) {
                    return new DateParseResult(DateParseStatus.VALID, nextYearCandidate);
                }
            } catch (DateTimeException ignored) {
                // Ex.: 29/02 em ano bissexto quando o próximo ano não é bissexto -> continua sendo data passada
            }
            return new DateParseResult(DateParseStatus.PAST_DATE, parsed);
        } catch (DateTimeException e) {
            // Ex.: em dez/2027 (não bissexto), "29/02" pode se referir a 29/02/2028 dentro da janela de virada de ano
            if (month == 2 && day == 29) {
                try {
                    LocalDate nextYearLeap = LocalDate.of(today.getYear() + 1, 2, 29);
                    long daysUntilNextYear = ChronoUnit.DAYS.between(today, nextYearLeap);
                    if (daysUntilNextYear >= 0 && daysUntilNextYear <= ROLLOVER_YEAR_WINDOW_DAYS) {
                        return new DateParseResult(DateParseStatus.VALID, nextYearLeap);
                    }
                } catch (DateTimeException ignored) {
                }
            }
            return new DateParseResult(DateParseStatus.INVALID_CALENDAR_DATE, null);
        }
    }

    private boolean isLikelyDecimalNumber(String normalized, int matchStart, int matchEnd) {
        String after = normalized.substring(matchEnd).trim();
        if (after.startsWith("h") || after.startsWith("cm") || after.startsWith("kg") || after.startsWith("reais")) {
            return true;
        }
        String before = normalized.substring(0, matchStart).trim();
        return before.endsWith("r$");
    }

    Optional<LocalTime> parseTimeSelection(String messageText) {
        return parseTimeSelection(messageText, List.of());
    }

    Optional<LocalTime> parseTimeSelection(String messageText, List<LocalTime> offeredSlots) {
        if (messageText == null || messageText.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeText(messageText);

        // Rejeita entradas que contêm datas como "25/11" para não confundir com hora
        if (FULL_DATE_PATTERN.matcher(normalized).find() || ISO_DATE_PATTERN.matcher(normalized).find()
                || SHORT_DATE_PATTERN.matcher(normalized).find()) {
            return Optional.empty();
        }

        // 1. Prioridade absoluta: número simples ("1", "2", "2.", "2)") = índice da opção na lista (1..N).
        //    Nunca interpreta número solto como hora ("2" nunca vira 02:00 nem 14:00; "14" solto sem "h"/":" também é rejeitado se > N).
        String stripped = stripPunctuation(normalized);
        if (stripped.matches("^\\d{1,2}$")) {
            int optionIndex = Integer.parseInt(stripped);
            if (offeredSlots != null && optionIndex >= 1 && optionIndex <= offeredSlots.size()) {
                return Optional.of(offeredSlots.get(optionIndex - 1));
            }
            return Optional.empty();
        }

        // 2. Intervalo explícito de horário (ex: "14h às 16h", "14:00 às 17:00", "das 14 às 16") -> sempre usa o início, nunca o fim
        Matcher rangeMatcher = TIME_RANGE_PATTERN.matcher(normalized);
        if (rangeMatcher.find()) {
            try {
                int startHour = Integer.parseInt(rangeMatcher.group(1));
                String startMinGroup = rangeMatcher.group(2) != null ? rangeMatcher.group(2) : rangeMatcher.group(3);
                int startMin = (startMinGroup != null) ? Integer.parseInt(startMinGroup) : 0;
                int endHour = Integer.parseInt(rangeMatcher.group(4));
                if (startHour >= 7 && startHour <= 23 && endHour > startHour && endHour <= 23 && startMin >= 0 && startMin <= 59) {
                    return Optional.of(LocalTime.of(startHour, startMin));
                }
            } catch (Exception ignored) {
            }
        }

        List<LocalTime> candidates = new ArrayList<>();

        // 3. Hora com qualificador de período (ex: "2h da tarde" -> 14:00, "3 da tarde" -> 15:00, "7 da noite" -> 19:00)
        Matcher pmMatcher = PM_HOUR_PATTERN.matcher(normalized);
        while (pmMatcher.find()) {
            int hour = Integer.parseInt(pmMatcher.group(1));
            if (hour >= 1 && hour <= 11) {
                candidates.add(LocalTime.of(hour + 12, 0));
            } else if (hour >= 12 && hour <= 23) {
                candidates.add(LocalTime.of(hour, 0));
            }
        }

        // 4. Hora explícita com marcador "h", ":" ou "horas" (ex: "14:00", "14h", "14h30", "quero 2h às 14h", "15 horas")
        Matcher explicit = EXPLICIT_TIME_PATTERN.matcher(normalized);
        while (explicit.find()) {
            try {
                int hour = Integer.parseInt(explicit.group(1));
                String minGroup = explicit.group(2) != null ? explicit.group(2) : explicit.group(3);
                int minute = (minGroup != null) ? Integer.parseInt(minGroup) : 0;
                if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                    candidates.add(LocalTime.of(hour, minute));
                }
            } catch (Exception ignored) {
            }
        }

        // 5. Hora precedida de preposição/palavra-chave explícita (ex: "quero às 15", "horário 14")
        Matcher prefixed = PREFIXED_HOUR_PATTERN.matcher(normalized);
        while (prefixed.find()) {
            try {
                int hour = Integer.parseInt(prefixed.group(1));
                if (hour >= 0 && hour <= 23) {
                    candidates.add(LocalTime.of(hour, 0));
                }
            } catch (Exception ignored) {
            }
        }

        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (offeredSlots != null && !offeredSlots.isEmpty()) {
            for (LocalTime candidate : candidates) {
                if (offeredSlots.contains(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.of(candidates.get(0));
    }

    // =========================================================================
    // PROMPTS DE ENTRADA E MENSAGENS UNIFICADAS (REUTILIZÁVEIS POR MENU / ORÇAMENTO)
    // =========================================================================

    public static String buildSchedulingEntryPrompt(
            Customer customer,
            ConversationSession session,
            TenantConfig tenantConfig,
            List<User> activeArtists,
            ObjectMapper objectMapper,
            boolean includeCustomIntroIfNoQuote) {

        ObjectMapper effectiveMapper = (objectMapper != null) ? objectMapper : FALLBACK_OBJECT_MAPPER;
        int durationHours = resolveDurationHours(session.getContextData(), effectiveMapper);
        List<User> recommended = findRecommendedActiveArtists(session.getContextData(), effectiveMapper, activeArtists);

        Map<String, Object> ctx = readContextMapStatic(session.getContextData(), effectiveMapper);
        ctx.put(CTX_SCHEDULING_STEP, STEP_PROFISSIONAL);
        ctx.put(CTX_CANDIDATE_ARTIST_IDS, recommended.stream().map(u -> u.getId().toString()).toList());
        ctx.put(CTX_DURATION_HOURS, durationHours);
        ctx.remove(CTX_SELECTED_ARTIST_ID);
        ctx.remove(CTX_SELECTED_ARTIST_NAME);
        ctx.remove(CTX_SELECTED_DATE);
        ctx.remove(CTX_AVAILABLE_SLOTS);
        writeContextMapStatic(session, ctx, effectiveMapper);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Ótimo, %s! 🗓️\n\n", customer.getName()));

        if (!recommended.isEmpty()) {
            sb.append("*Etapa 1 de 3 — Profissional:*\n");
            if (recommended.size() == 1) {
                User artist = recommended.get(0);
                sb.append(String.format("No seu orçamento, o profissional sugerido foi *%s* (_%s_).\n\n",
                        artist.getName(), formatSpecialties(artist)));
                sb.append(String.format("Deseja agendar com *%s*?\n", artist.getName()));
                sb.append("👉 Digite *1* (ou *sim*) para confirmar\n");
                sb.append("👉 Ou digite o *nome de outro tatuador* ou o *estilo da tatuagem*");
            } else {
                sb.append("Com base no seu orçamento, estes são os profissionais sugeridos:\n");
                for (int i = 0; i < recommended.size(); i++) {
                    User artist = recommended.get(i);
                    sb.append(String.format("%d️⃣ *%s* — _%s_\n", i + 1, artist.getName(), formatSpecialties(artist)));
                }
                sb.append("\n👉 Digite o *número* ou o *nome* do profissional desejado (ou informe outro nome/estilo)");
            }
        } else {
            String customIntro = (includeCustomIntroIfNoQuote && tenantConfig != null && tenantConfig.getAgendamentoIntroMessage() != null)
                    ? tenantConfig.getAgendamentoIntroMessage().trim()
                    : "";
            boolean isLegacyDateFirstIntro = customIntro.toLowerCase().contains("qual dia da semana ou período você tem preferência");
            if (!customIntro.isBlank() && !isLegacyDateFirstIntro) {
                sb.append(customIntro).append("\n\n");
            } else {
                sb.append("Vamos iniciar seu agendamento!\n\n");
            }
            sb.append("*Etapa 1 de 3 — Profissional:*\n");
            sb.append("Com qual tatuador você deseja agendar?\n");
            sb.append("👉 Digite o *nome do profissional* (ex: Carlos, Mariana) ou o *estilo da tatuagem* (ex: realismo, fineline, blackwork, aquarela, piercing) que eu indico os especialistas para você.");
        }

        if (recommended.size() >= 3) {
            sb.append("\n\n💡 _Digite *atendente* para falar com nossa equipe, ou *0* para voltar ao menu inicial._");
        } else {
            sb.append("\n\n💡 _Digite *atendente* (ou 3) para falar com nossa equipe, ou *0* para voltar ao menu inicial._");
        }
        return sb.toString();
    }

    private String buildProfessionalOptionsBlock(List<User> candidates) {
        StringBuilder sb = new StringBuilder("*Etapa 1 de 3 — Profissional:*\n");
        if (candidates.size() == 1) {
            User artist = candidates.get(0);
            sb.append(String.format("👉 Digite *1* (ou *sim*) para confirmar *%s*\n", artist.getName()));
            sb.append("👉 Ou digite o *nome de outro profissional* ou o *estilo da tatuagem* (ex: realismo, fineline, blackwork, aquarela, piercing)");
        } else if (candidates.size() > 1) {
            for (int i = 0; i < candidates.size(); i++) {
                User artist = candidates.get(i);
                sb.append(String.format("%d️⃣ *%s* — _%s_\n", i + 1, artist.getName(), formatSpecialties(artist)));
            }
            sb.append("\n👉 Digite o *número* ou o *nome* do profissional desejado (ou informe outro nome/estilo)");
        } else {
            sb.append("👉 Digite o *nome do profissional* (ex: Carlos, Mariana) ou o *estilo da tatuagem* (ex: realismo, fineline, blackwork, aquarela, piercing)");
        }
        sb.append("\n\n💡 _Digite *atendente* para falar com nossa equipe ou *0* para voltar ao menu inicial._");
        return sb.toString();
    }

    private String buildDeferredAnyArtistReply(List<User> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("Por enquanto, para garantir o especialista ideal para a sua tattoo, precisamos escolher o profissional primeiro (a opção \"qualquer disponível\" ainda não está ativa). 🎨\n\n");
        sb.append(buildProfessionalOptionsBlock(candidates));
        return sb.toString();
    }

    private String buildDatePromptBlock(TenantConfig tenantConfig) {
        LocalTime openTime = resolveOpeningTime(tenantConfig);
        LocalTime closeTime = resolveClosingTime(tenantConfig);
        return String.format(
                "*Etapa 2 de 3 — Data:*\n" +
                "Para qual *data* você gostaria de agendar?\n" +
                "📅 Atendemos de *%s* (das %s às %s).\n" +
                "Exemplos: *amanhã*, *sexta-feira*, *28/11* ou *28/11/2026*.\n\n" +
                "💡 _Digite *trocar profissional* para escolher outro artista, *atendente* (ou 3) para falar com nossa equipe ou *0* para o menu._",
                formatWorkingDaysLabel(tenantConfig),
                openTime.format(TIME_ONLY_FORMATTER),
                closeTime.format(TIME_ONLY_FORMATTER)
        );
    }

    private String buildTimeSlotsPrompt(User artist, LocalDate date, List<LocalTime> slots, int durationHours) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("🗓️ Horários disponíveis com *%s* em *%s (%s)* — sessão de *%dh*:\n\n",
                artist.getName(),
                date.format(DATE_ONLY_FORMATTER),
                formatDayOfWeekPt(date.getDayOfWeek()),
                durationHours));

        for (int i = 0; i < slots.size(); i++) {
            LocalTime start = slots.get(i);
            LocalTime end = start.plusHours(durationHours);
            sb.append(String.format("*%d)* *%s* (%s às %s)\n",
                    i + 1,
                    start.format(TIME_ONLY_FORMATTER),
                    start.format(TIME_ONLY_FORMATTER),
                    end.format(TIME_ONLY_FORMATTER)));
        }

        sb.append("\n*Etapa 3 de 3 — Horário:*\n");
        sb.append("👉 Digite o *número da opção* (ex: *1*, *2*) ou o *horário* com `h` ou `:` (ex: *")
                .append(slots.get(0).format(TIME_ONLY_FORMATTER))
                .append("* ou *")
                .append(slots.get(0).getHour())
                .append("h*).\n\n");
        sb.append("💡 _Digite *outra data* para escolher outro dia, *trocar profissional* para mudar de artista, *atendente* para falar com nossa equipe ou *0* para o menu._");
        return sb.toString();
    }

    private String buildUnifiedPixSummaryMessage(
            Customer customer,
            String artistName,
            OffsetDateTime scheduledStart,
            OffsetDateTime scheduledEnd,
            int durationHours,
            BigDecimal depositAmount,
            BigDecimal remainingAmount,
            int expirationMinutes,
            TenantConfig tenantConfig) {

        String expirationLabel = (expirationMinutes == 120)
                ? "2 horas"
                : (expirationMinutes % 60 == 0 && expirationMinutes > 60
                        ? (expirationMinutes / 60) + " horas (" + expirationMinutes + " minutos)"
                        : expirationMinutes + " minutos");

        String customHeader = (tenantConfig != null && tenantConfig.getPixPaymentMessage() != null && !tenantConfig.getPixPaymentMessage().isBlank())
                ? tenantConfig.getPixPaymentMessage().trim()
                : "";
        String customHeaderNorm = normalizeText(customHeader);
        boolean isLegacyVerbosePixTemplate = customHeaderNorm.contains("bloqueamos seu horario na grade")
                && (customHeaderNorm.contains("pix copia e cola") || customHeaderNorm.contains("reservado por 2 horas"));

        String intro = (customHeader.isEmpty() || isLegacyVerbosePixTemplate)
                ? String.format("🗓️ *Pré-Agendamento Criado!*\n\nOlá, %s! Confira os detalhes da sua reserva:", customer.getName())
                : String.format("%s\n\nOlá, %s! Confira os detalhes da sua reserva:", customHeader, customer.getName());

        boolean showPrice = tenantConfig == null || !Boolean.FALSE.equals(tenantConfig.getOrcamentoShowPrice());
        String balanceLine = showPrice
                ? String.format("💵 *Saldo Restante Estimado:* R$ %s (estimativa — valor final confirmado pelo tatuador no dia da sessão)\n",
                        formatBrl(remainingAmount))
                : "";

        return String.format(
                "%s\n" +
                "📅 *Data e Horário:* %s das %s às %s (%dh)\n" +
                "👨‍🎨 *Profissional:* %s\n" +
                "💰 *Sinal de Reserva:* R$ %s\n" +
                "%s" +
                "⏳ *Validade do PIX:* %s\n\n" +
                "Abaixo enviarei o *código PIX Copia e Cola* em uma mensagem separada para facilitar na hora de copiar. Assim que o pagamento for identificado, confirmaremos sua vaga automaticamente! ✨\n\n" +
                "💡 _Digite 0 a qualquer momento para voltar ao menu inicial._",
                intro,
                scheduledStart.format(DATE_ONLY_FORMATTER),
                scheduledStart.format(TIME_ONLY_FORMATTER),
                scheduledEnd.format(TIME_ONLY_FORMATTER),
                durationHours,
                artistName,
                formatBrl(depositAmount),
                balanceLine,
                expirationLabel
        );
    }

    public static String formatBrl(BigDecimal amount) {
        if (amount == null) {
            return "0,00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    // =========================================================================
    // RESOLUÇÃO E BUSCA DE PROFISSIONAIS (POR RECOMENDAÇÃO, NOME OU ESTILO)
    // =========================================================================

    private List<User> findActiveArtists() {
        List<User> allUsers = userRepository.findAll();
        if (allUsers == null || allUsers.isEmpty()) {
            return List.of();
        }
        return allUsers.stream()
                .filter(u -> u.getRole() == UserRole.ARTIST && Boolean.TRUE.equals(u.getIsActive()))
                .toList();
    }

    public static List<User> findRecommendedActiveArtists(String contextData, ObjectMapper objectMapper, List<User> activeArtists) {
        if (contextData == null || contextData.isBlank() || objectMapper == null || activeArtists == null || activeArtists.isEmpty()) {
            return List.of();
        }
        List<User> resolved = new ArrayList<>();
        Set<UUID> seen = new LinkedHashSet<>();
        try {
            JsonNode node = objectMapper.readTree(contextData);
            if (node.has("recommendedArtists") && node.get("recommendedArtists").isArray()) {
                for (JsonNode item : node.get("recommendedArtists")) {
                    String recNorm = normalizeText(item.asText());
                    if (recNorm.isBlank()) continue;
                    for (User artist : activeArtists) {
                        if (artist.getId() == null || seen.contains(artist.getId())) continue;
                        if (matchesArtistName(recNorm, artist)) {
                            resolved.add(artist);
                            seen.add(artist.getId());
                            break;
                        }
                    }
                    if (resolved.size() >= MAX_SUGGESTED_ARTISTS) {
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return resolved;
    }

    private List<User> resolveCurrentCandidates(Map<String, Object> context, List<User> activeArtists, String rawContextData) {
        List<String> storedIds = extractStringList(context, CTX_CANDIDATE_ARTIST_IDS);
        if (!storedIds.isEmpty()) {
            List<User> fromIds = new ArrayList<>();
            for (String idStr : storedIds) {
                try {
                    UUID id = UUID.fromString(idStr);
                    activeArtists.stream()
                            .filter(u -> id.equals(u.getId()))
                            .findFirst()
                            .ifPresent(fromIds::add);
                } catch (Exception ignored) {
                }
            }
            if (!fromIds.isEmpty()) {
                return fromIds;
            }
        }
        return findRecommendedActiveArtists(rawContextData, objectMapper, activeArtists);
    }

    private User resolveSelectedArtist(Map<String, Object> context, List<User> activeArtists) {
        Object idObj = context.get(CTX_SELECTED_ARTIST_ID);
        if (idObj != null) {
            try {
                UUID id = UUID.fromString(String.valueOf(idObj));
                return activeArtists.stream()
                        .filter(u -> id.equals(u.getId()))
                        .findFirst()
                        .orElse(null);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private List<User> findArtistsByName(String normalizedInput, List<User> activeArtists) {
        if (normalizedInput == null || normalizedInput.isBlank()) {
            return List.of();
        }
        List<User> matches = new ArrayList<>();
        for (User artist : activeArtists) {
            if (matchesArtistName(normalizedInput, artist)) {
                matches.add(artist);
            }
        }
        return matches;
    }

    private static boolean matchesArtistName(String normalizedInput, User artist) {
        if (artist == null || artist.getName() == null || artist.getName().isBlank()) {
            return false;
        }
        String fullNorm = normalizeText(artist.getName());
        if (!fullNorm.isBlank() && containsWordSequence(normalizedInput, fullNorm)) {
            return true;
        }
        String[] tokens = fullNorm.split("\\s+");
        if (tokens.length == 0) {
            return false;
        }
        // Primeiro nome sempre é válido para busca por nome
        if (tokens[0].length() >= 3 && containsWord(normalizedInput, tokens[0])) {
            return true;
        }
        // Apelidos distintivos (ex: "blade"), ignorando sobrenomes genéricos / palavras de estilo
        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.length() >= 4 && !NON_NAME_TOKENS.contains(token) && containsWord(normalizedInput, token)) {
                return true;
            }
        }
        return false;
    }

    private List<User> findArtistsByStyle(String normalizedInput, List<User> activeArtists) {
        if (normalizedInput == null || normalizedInput.isBlank()) {
            return List.of();
        }
        Set<String> targetKeywords = new LinkedHashSet<>();

        if (containsAny(normalizedInput, "realismo", "realista", "retrato", "leao", "lobo", "microrealismo")) {
            targetKeywords.add("realismo");
            targetKeywords.add("microrealismo");
        }
        if (containsAny(normalizedInput, "fineline", "fine line", "traco fino", "delicad", "minimalista")) {
            targetKeywords.add("fineline");
            targetKeywords.add("delicadas");
            targetKeywords.add("minimalista");
        }
        if (containsAny(normalizedInput, "blackwork", "tribal")) {
            targetKeywords.add("blackwork");
            targetKeywords.add("tribal");
        }
        if (containsAny(normalizedInput, "geometrico", "geometria")) {
            targetKeywords.add("geometrico");
        }
        if (containsAny(normalizedInput, "aquarela", "botanic", "colorid")) {
            targetKeywords.add("aquarela");
            targetKeywords.add("botanico");
        }
        if (containsAny(normalizedInput, "floral", "florais", "flor", "flores")) {
            targetKeywords.add("floral");
            targetKeywords.add("florais");
        }
        if (containsAny(normalizedInput, "old school", "tradicional")) {
            targetKeywords.add("old school");
            targetKeywords.add("tradicional");
        }
        if (containsAny(normalizedInput, "oriental", "irezumi", "anime", "dragao", "neo tradicional")) {
            targetKeywords.add("oriental");
            targetKeywords.add("anime");
            targetKeywords.add("neo tradicional");
        }
        if (containsAny(normalizedInput, "pontilhismo", "mandala")) {
            targetKeywords.add("pontilhismo");
            targetKeywords.add("mandala");
        }
        if (containsAny(normalizedInput, "dark", "lettering", "chicano", "gotico", "escrita", "caligrafia")) {
            targetKeywords.add("dark");
            targetKeywords.add("lettering");
            targetKeywords.add("caligrafia");
        }
        if (containsAny(normalizedInput, "piercing", "joia", "joias", "microdermal")) {
            targetKeywords.add("piercing");
        }
        if (containsAny(normalizedInput, "mini tattoo", "mini tattoos", "flash")) {
            targetKeywords.add("mini tattoos");
        }

        List<User> matches = new ArrayList<>();
        Set<UUID> seen = new LinkedHashSet<>();

        for (User artist : activeArtists) {
            String specNorm = normalizeText(artist.getSpecialties());
            if (specNorm.isBlank()) continue;

            boolean matched = false;
            for (String kw : targetKeywords) {
                if (specNorm.contains(kw)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                // Match direto por termos significativos da especialidade do artista
                for (String specToken : specNorm.split("[,/&\\s]+")) {
                    if (specToken.length() >= 5 && containsWord(normalizedInput, specToken)) {
                        matched = true;
                        break;
                    }
                }
            }
            if (matched && artist.getId() != null && seen.add(artist.getId())) {
                matches.add(artist);
            }
        }
        return matches;
    }

    private User resolveBestArtistForHandoff(String messageText, String contextData, List<User> activeArtists) {
        if (activeArtists.isEmpty()) {
            return null;
        }
        String norm = normalizeText(messageText);
        List<User> byName = findArtistsByName(norm, activeArtists);
        if (!byName.isEmpty()) {
            return byName.get(0);
        }
        List<User> byStyle = findArtistsByStyle(norm, activeArtists);
        if (!byStyle.isEmpty()) {
            return byStyle.get(0);
        }
        List<User> recommended = findRecommendedActiveArtists(contextData, objectMapper, activeArtists);
        if (!recommended.isEmpty()) {
            return recommended.get(0);
        }
        return activeArtists.get(0);
    }

    // =========================================================================
    // UTILITÁRIOS DE HORÁRIO DE FUNCIONAMENTO E AUSÊNCIA (USADOS POR OUTROS HANDLERS)
    // =========================================================================

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
        } catch (Exception ignored) {
        }
        return fallback;
    }

    public static LocalTime resolveOpeningTime(TenantConfig config) {
        return config != null ? parseLocalTime(config.getOpeningTime(), LocalTime.of(10, 0)) : LocalTime.of(10, 0);
    }

    public static LocalTime resolveClosingTime(TenantConfig config) {
        return config != null ? parseLocalTime(config.getClosingTime(), LocalTime.of(20, 0)) : LocalTime.of(20, 0);
    }

    public static boolean isOutsideOperatingHours(OffsetDateTime dateTime, TenantConfig config) {
        if (config == null || dateTime == null) {
            return false;
        }
        ZonedDateTime studioTime = dateTime.atZoneSameInstant(STUDIO_ZONE);
        if (!isWorkingDay(studioTime.getDayOfWeek(), config)) {
            return true;
        }
        LocalTime openTime = resolveOpeningTime(config);
        LocalTime closeTime = resolveClosingTime(config);
        LocalTime current = studioTime.toLocalTime().withSecond(0).withNano(0);
        return current.isBefore(openTime) || current.isAfter(closeTime);
    }

    public static String buildAbsenceNoticeIfOutsideHours(TenantConfig config) {
        return buildAbsenceNoticeIfOutsideHours(config, OffsetDateTime.now(STUDIO_ZONE));
    }

    static String buildAbsenceNoticeIfOutsideHours(TenantConfig config, OffsetDateTime referenceNow) {
        if (config == null || config.getAbsenceMessage() == null || config.getAbsenceMessage().isBlank()) {
            return "";
        }
        if (isOutsideOperatingHours(referenceNow, config)) {
            return "\n\n" + config.getAbsenceMessage().trim();
        }
        return "";
    }

    public static String formatWorkingDaysLabel(TenantConfig config) {
        String raw = (config != null && config.getWorkingDays() != null && !config.getWorkingDays().isBlank())
                ? config.getWorkingDays().trim()
                : "SEG-SAB";
        String norm = normalizeText(raw).toUpperCase(Locale.ROOT).replace(" ", "");
        return switch (norm) {
            case "SEG-SAB" -> "Segunda a Sábado";
            case "SEG-SEX" -> "Segunda a Sexta";
            case "SEG-DOM" -> "Segunda a Domingo";
            default -> raw;
        };
    }

    // =========================================================================
    // HELPERS INTERNOS
    // =========================================================================

    private void triggerHumanHandoff(
            Customer customer,
            ConversationSession session,
            EvolutionWebhookRequest webhook,
            TenantConfig tenantConfig,
            String notificationTitle,
            String notificationMessage,
            String customReplyOverride) {

        session.setCurrentState(ConversationState.TRANSBORDO_HUMANO);
        String customTemplate = (customReplyOverride != null && !customReplyOverride.isBlank())
                ? customReplyOverride
                : ((tenantConfig != null && tenantConfig.getHumanTransferMessage() != null && !tenantConfig.getHumanTransferMessage().isBlank())
                        ? tenantConfig.getHumanTransferMessage()
                        : "Notifiquei nossa equipe e um dos nossos tatuadores irá te responder diretamente por aqui em instantes. Aguarde só um momento!");
        String absenceNotice = buildAbsenceNoticeIfOutsideHours(tenantConfig, now());
        String reply = (customReplyOverride != null && !customReplyOverride.isBlank())
                ? String.format("%s%s\n\n💡 _Se quiser reiniciar o assistente automático, basta enviar 0 a qualquer momento._", customTemplate, absenceNotice)
                : String.format("Entendido, %s! 👨‍🎨\n\n%s%s\n\n💡 _Se quiser reiniciar o assistente automático, basta enviar 0 a qualquer momento._",
                        customer.getName(), customTemplate, absenceNotice);

        sendAndRecord(session, customer, webhook, reply);
        if (studioAdminController != null) {
            studioAdminController.publishNotification(
                    "HUMAN_REQUEST",
                    notificationTitle,
                    notificationMessage,
                    customer.getPhone()
            );
        }
    }

    private void sendAndRecord(ConversationSession session, Customer customer, EvolutionWebhookRequest webhook, String message) {
        evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), message);
        chatService.recordBotMessage(session, customer, message);
    }

    private void saveProfessionalStepState(ConversationSession session, Map<String, Object> context, List<User> candidates, int durationHours) {
        context.put(CTX_SCHEDULING_STEP, STEP_PROFISSIONAL);
        context.put(CTX_CANDIDATE_ARTIST_IDS, candidates.stream()
                .filter(u -> u.getId() != null)
                .map(u -> u.getId().toString())
                .toList());
        context.put(CTX_DURATION_HOURS, durationHours);
        context.remove(CTX_SELECTED_ARTIST_ID);
        context.remove(CTX_SELECTED_ARTIST_NAME);
        context.remove(CTX_SELECTED_DATE);
        context.remove(CTX_AVAILABLE_SLOTS);
        writeContextMap(session, context);
    }

    private void clearSchedulingContext(Map<String, Object> context) {
        context.remove(CTX_SCHEDULING_STEP);
        context.remove(CTX_CANDIDATE_ARTIST_IDS);
        context.remove(CTX_SELECTED_ARTIST_ID);
        context.remove(CTX_SELECTED_ARTIST_NAME);
        context.remove(CTX_SELECTED_DATE);
        context.remove(CTX_AVAILABLE_SLOTS);
        context.remove(CTX_DURATION_HOURS);
    }

    private boolean isHumanHandoffRequest(String normalizedInput, String currentStep, int candidateCount) {
        String stripped = stripPunctuation(normalizedInput);
        if (stripped.isBlank()) {
            return false;
        }
        if (stripped.equals("3")) {
            if (STEP_HORARIO.equals(currentStep)) {
                return false;
            }
            return !(STEP_PROFISSIONAL.equals(currentStep) && candidateCount >= 3);
        }
        return stripped.contains("humano")
                || stripped.contains("atendente")
                || stripped.contains("falar")
                || stripped.equals("tatuador");
    }

    private boolean isNewQuoteRequest(String normalizedInput) {
        String stripped = stripPunctuation(normalizedInput);
        if (!stripped.contains("orcamento")) {
            return false;
        }
        if (stripped.equals("orcamento")) {
            return true;
        }
        return containsAny(stripped,
                "fazer orcamento", "novo orcamento", "outro orcamento", "refazer orcamento",
                "voltar ao orcamento", "voltar pro orcamento", "voltar para orcamento",
                "ir para orcamento", "quero orcamento", "quero um orcamento", "pedir orcamento");
    }

    private boolean isConfirmationInput(String normalizedInput, List<User> activeArtists) {
        String stripped = stripPunctuation(normalizedInput);
        if (stripped.isBlank()) {
            return false;
        }
        if (CONFIRMATION_INPUTS.contains(stripped)) {
            return true;
        }
        if (stripped.startsWith("sim ") && !containsAny(stripped, "nao", "outro", "outra", "trocar", "mudar")) {
            // Se o cliente disse "sim, quero o Carlos" quando a sugestão era Mariana, deixa a busca por nome prevalecer
            return findArtistsByName(normalizedInput, activeArtists).isEmpty();
        }
        return false;
    }

    private int countRecommendedInContext(String contextData) {
        if (contextData == null || contextData.isBlank() || objectMapper == null) {
            return 0;
        }
        try {
            JsonNode node = objectMapper.readTree(contextData);
            if (node.has("recommendedArtists") && node.get("recommendedArtists").isArray()) {
                return Math.min(MAX_SUGGESTED_ARTISTS, node.get("recommendedArtists").size());
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private boolean isAnyAvailableRequest(String normalizedInput) {
        String stripped = stripPunctuation(normalizedInput);
        return stripped.equals("qualquer")
                || stripped.contains("qualquer disponivel")
                || stripped.contains("qualquer um")
                || stripped.contains("qualquer tatuador")
                || stripped.contains("tanto faz");
    }

    private boolean isChangeProfessionalRequest(String normalizedInput) {
        return normalizedInput.contains("trocar profissional")
                || normalizedInput.contains("outro profissional")
                || normalizedInput.contains("trocar tatuador")
                || normalizedInput.contains("outro tatuador")
                || normalizedInput.contains("trocar artista")
                || normalizedInput.contains("outro artista")
                || normalizedInput.contains("mudar profissional")
                || normalizedInput.contains("mudar tatuador");
    }

    private boolean isChangeDateRequest(String normalizedInput) {
        return normalizedInput.contains("outra data")
                || normalizedInput.contains("trocar data")
                || normalizedInput.contains("outro dia")
                || normalizedInput.contains("mudar data")
                || normalizedInput.contains("trocar dia");
    }

    private String extractStep(Map<String, Object> context) {
        Object step = context.get(CTX_SCHEDULING_STEP);
        if (step instanceof String s && !s.isBlank()) {
            return s.trim().toUpperCase(Locale.ROOT);
        }
        return STEP_PROFISSIONAL;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Map<String, Object> context, String key) {
        Object val = context.get(key);
        if (val instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        return List.of();
    }

    private LocalDate parseStoredDate(Map<String, Object> context) {
        Object val = context.get(CTX_SELECTED_DATE);
        if (val instanceof String s && !s.isBlank()) {
            try {
                return LocalDate.parse(s.trim());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private List<LocalTime> parseStoredSlots(Map<String, Object> context) {
        List<String> raw = extractStringList(context, CTX_AVAILABLE_SLOTS);
        List<LocalTime> times = new ArrayList<>();
        for (String s : raw) {
            try {
                times.add(LocalTime.parse(s.trim(), TIME_ONLY_FORMATTER));
            } catch (Exception ignored) {
            }
        }
        return times;
    }

    private Map<String, Object> readContextMap(String contextData) {
        return readContextMapStatic(contextData, objectMapper);
    }

    private void writeContextMap(ConversationSession session, Map<String, Object> context) {
        writeContextMapStatic(session, context, objectMapper);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readContextMapStatic(String contextData, ObjectMapper mapper) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (contextData != null && !contextData.isBlank() && mapper != null) {
            try {
                Map<String, Object> existing = mapper.readValue(contextData, Map.class);
                if (existing != null) {
                    context.putAll(existing);
                }
            } catch (Exception ignored) {
            }
        }
        return context;
    }

    private static void writeContextMapStatic(ConversationSession session, Map<String, Object> context, ObjectMapper mapper) {
        if (mapper == null) {
            return;
        }
        try {
            session.setContextData(mapper.writeValueAsString(context));
        } catch (Exception e) {
            log.warn("Erro ao serializar contextData da sessão de agendamento: {}", e.getMessage());
        }
    }

    private void updateContextForFechamento(ConversationSession session, String artistName) {
        Map<String, Object> context = readContextMap(session.getContextData());
        clearSchedulingContext(context);
        if (!context.containsKey("style") || context.get("style") == null || String.valueOf(context.get("style")).isBlank()) {
            context.put("style", "Projeto de Fechamento Autoral");
        }
        context.put("sizeCategory", TattooSizeOption.FECHAMENTO.getCategoryKey());
        context.put("estimatedSizeCm", TattooSizeOption.FECHAMENTO.getFormattedLabel());
        context.put("isFechamento", true);
        context.put("priceDisplay", "A combinar");
        context.put("assignedArtistName", artistName);
        context.put("recommendedArtists", List.of(artistName));
        context.remove("basePrice");
        context.remove("minPrice");
        context.remove("maxPrice");
        writeContextMap(session, context);
    }

    private boolean isFechamentoInContext(String contextData) {
        if (contextData == null || contextData.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(contextData);
            if (node.has("isFechamento") && node.get("isFechamento").asBoolean()) {
                return true;
            }
            if (node.hasNonNull("sizeCategory") && "FECHAMENTO".equalsIgnoreCase(node.get("sizeCategory").asText())) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private BigDecimal extractPriceFromContext(String contextData) {
        if (contextData == null || contextData.isBlank()) {
            return new BigDecimal("250.00");
        }
        try {
            JsonNode node = objectMapper.readTree(contextData);
            if (node.hasNonNull("basePrice")) {
                return new BigDecimal(node.get("basePrice").asText());
            }
        } catch (Exception e) {
            log.warn("Erro ao extrair preço do contextData: {}", e.getMessage());
        }
        return new BigDecimal("250.00");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).atZoneSameInstant(STUDIO_ZONE).toOffsetDateTime();
    }

    private OffsetDateTime toOffsetDateTime(LocalDate date, LocalTime time) {
        LocalDateTime ldt = date.atTime(time);
        ZoneOffset offset = STUDIO_ZONE.getRules().getOffset(ldt);
        return ldt.atOffset(offset);
    }

    private static Optional<Integer> parsePositiveInt(String text) {
        String stripped = stripPunctuation(text);
        if (stripped.isBlank() || !stripped.matches("^\\d{1,2}$")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(stripped));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String stripPunctuation(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static DayOfWeek extractDayOfWeek(String normalized) {
        if (containsWord(normalized, "segunda") || normalized.contains("segunda-feira")) return DayOfWeek.MONDAY;
        if (containsWord(normalized, "terca") || normalized.contains("terca-feira")) return DayOfWeek.TUESDAY;
        if (containsWord(normalized, "quarta") || normalized.contains("quarta-feira")) return DayOfWeek.WEDNESDAY;
        if (containsWord(normalized, "quinta") || normalized.contains("quinta-feira")) return DayOfWeek.THURSDAY;
        if (containsWord(normalized, "sexta") || normalized.contains("sexta-feira")) return DayOfWeek.FRIDAY;
        if (containsWord(normalized, "sabado")) return DayOfWeek.SATURDAY;
        if (containsWord(normalized, "domingo")) return DayOfWeek.SUNDAY;
        return null;
    }

    private static DayOfWeek parseDayAbbrev(String token) {
        if (token == null) return null;
        return switch (token.trim().toUpperCase(Locale.ROOT)) {
            case "SEG", "SEGUNDA" -> DayOfWeek.MONDAY;
            case "TER", "TERCA" -> DayOfWeek.TUESDAY;
            case "QUA", "QUARTA" -> DayOfWeek.WEDNESDAY;
            case "QUI", "QUINTA" -> DayOfWeek.THURSDAY;
            case "SEX", "SEXTA" -> DayOfWeek.FRIDAY;
            case "SAB", "SABADO" -> DayOfWeek.SATURDAY;
            case "DOM", "DOMINGO" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    private static String formatDayOfWeekPt(DayOfWeek dow) {
        return DAY_OF_WEEK_PT.getOrDefault(dow, dow.name());
    }

    private static String formatSpecialties(User artist) {
        return (artist != null && artist.getSpecialties() != null && !artist.getSpecialties().isBlank())
                ? artist.getSpecialties().trim()
                : "Tatuagem Autoral";
    }

    private static boolean containsAny(String text, String... keywords) {
        return Arrays.stream(keywords).anyMatch(text::contains);
    }

    private static boolean containsWord(String text, String word) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(word) + "(?![\\p{L}\\p{N}])")
                .matcher(text)
                .find();
    }

    private static boolean containsWordSequence(String text, String phrase) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(phrase) + "(?![\\p{L}\\p{N}])")
                .matcher(text)
                .find();
    }

    static String normalizeText(String raw) {
        if (raw == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return decomposed.toLowerCase(Locale.ROOT)
                .replace('\'', ' ')
                .replace('"', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
