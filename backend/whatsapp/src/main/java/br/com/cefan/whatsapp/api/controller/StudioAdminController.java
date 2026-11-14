package br.com.cefan.whatsapp.api.controller;

import br.com.cefan.whatsapp.api.dto.ChatMessageDto;
import br.com.cefan.whatsapp.application.service.ChatService;
import br.com.cefan.whatsapp.domain.model.ConversationSession;
import br.com.cefan.whatsapp.domain.model.ConversationState;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.model.User;
import br.com.cefan.whatsapp.domain.model.UserNotificationPreference;
import br.com.cefan.whatsapp.domain.model.UserRole;
import br.com.cefan.whatsapp.domain.repository.ConversationSessionRepository;
import br.com.cefan.whatsapp.domain.repository.UserRepository;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RestControllerAdvice
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StudioAdminController implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    private final ConversationSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final EvolutionApiClient evolutionApiClient;
    private final EntityManager entityManager;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final br.com.cefan.whatsapp.infrastructure.security.JwtTokenProvider jwtTokenProvider;
    private final ChatService chatService;

    private static final String DUMMY_HASH = "$2a$10$wN1FzZ5qF.pX8K7B5aQ5s.3tPz6E6tVj6K9f6V8e0h8y6f6v8e0h8";

    @Value("${cefan.admin.email:admin@viciotattoo.com}")
    private String adminEmail;

    @Value("${cefan.admin.password:cefan123}")
    private String adminPassword;

    @Value("${cefan.admin.name:Dono - Vício Tattoo}")
    private String adminName;

    private final List<SseEmitter> sseEmitters = new CopyOnWriteArrayList<>();
    private final List<NotificationEventDto> recentNotifications = new CopyOnWriteArrayList<>();

    private volatile boolean notifyNewLead = true;
    private volatile boolean notifyPaymentReceived = true;
    private volatile boolean notifyHumanRequest = true;

    // ==========================================================
    // 1. AUTENTICAÇÃO DO PAINEL ADMINISTRATIVO (/api/v1/auth/login)
    // ==========================================================

    @PostMapping("/auth/login")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        if (request == null || request.getEmail() == null || request.getPassword() == null) {
            throw new IllegalArgumentException("E-mail e senha são obrigatórios.");
        }

        String email = request.getEmail().trim().toLowerCase();
        String pass = request.getPassword().trim();

        Optional<User> dbUser = userRepository.findByEmail(email);

        if (dbUser.isEmpty()) {
            passwordEncoder.matches(pass, DUMMY_HASH);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Credenciais inválidas para o painel administrativo."));
        }

        User user = dbUser.get();
        if (!Boolean.TRUE.equals(user.getIsActive()) || user.getPasswordHash() == null
                || !passwordEncoder.matches(pass, user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Credenciais inválidas para o painel administrativo."));
        }

        String token = jwtTokenProvider.generateToken(user);
        String role = user.getRole() != null ? user.getRole().name() : "ARTIST";
        String studioName = user.getTenant() != null ? user.getTenant().getStudioName() : "Vício Tattoo";

        return ResponseEntity.ok(Map.of(
                "token", token,
                "id", user.getId().toString(),
                "name", user.getName(),
                "email", user.getEmail(),
                "role", role,
                "studioName", studioName
        ));
    }

    // ==========================================================
    // 2. PAINEL DE ATENDIMENTOS / LIVE CHAT (/api/v1/conversations)
    // ==========================================================

    @GetMapping("/conversations")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ConversationSessionDto>> listConversations(
            @RequestParam(required = false) ConversationState state) {
        List<ConversationSession> sessions = (state != null)
                ? sessionRepository.findByCurrentStateOrderByLastInteractionAtDesc(state)
                : sessionRepository.findAllByOrderByLastInteractionAtDesc();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = null;
        if (auth != null && auth.getName() != null) {
            currentUser = userRepository.findByEmail(auth.getName()).orElse(null);
        }

        // Se o usuário for um ARTIST (Tatuador), exibe estritamente os atendimentos atribuídos a ele
        if (currentUser != null && currentUser.getRole() == UserRole.ARTIST) {
            final User artistUser = currentUser;
            sessions = sessions.stream()
                    .filter(s -> (s.getAssignedUser() != null && s.getAssignedUser().getId().equals(artistUser.getId())) ||
                            (s.getAssignedArtistName() != null && s.getAssignedArtistName().toLowerCase().contains(artistUser.getName().toLowerCase())))
                    .collect(Collectors.toList());
        }

        List<ConversationSessionDto> dtos = sessions.stream()
                .map(ConversationSessionDto::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/conversations/{id}/messages")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ChatMessageDto>> getConversationMessages(@PathVariable UUID id) {
        return ResponseEntity.ok(chatService.getMessagesBySession(id));
    }

    @GetMapping(value = "/conversations/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamConversationMessages(@PathVariable UUID id) {
        return chatService.subscribe(id);
    }

    @PostMapping("/conversations/{id}/messages")
    @Transactional
    public ResponseEntity<ChatMessageDto> sendHumanMessage(
            @PathVariable UUID id,
            @RequestBody SendMessageRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            throw new IllegalArgumentException("A mensagem não pode estar vazia.");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = null;
        if (auth != null && auth.getName() != null) {
            currentUser = userRepository.findByEmail(auth.getName()).orElse(null);
        }

        UUID agentUserId = (currentUser != null) ? currentUser.getId() : null;
        ChatMessageDto chatMessage = chatService.sendAgentMessage(id, agentUserId, request.getMessage().trim());

        // Garante transbordo humano ativo na sessão
        sessionRepository.findById(id).ifPresent(session -> {
            session.setCurrentState(ConversationState.TRANSBORDO_HUMANO);
            session.setLastInteractionAt(OffsetDateTime.now());
            sessionRepository.save(session);
        });

        return ResponseEntity.ok(chatMessage);
    }

    @PostMapping("/conversations/{id}/assign")
    @Transactional
    public ResponseEntity<Void> assignArtist(
            @PathVariable UUID id,
            @RequestBody AssignArtistRequest request) {
        chatService.assignArtist(id, request != null ? request.getArtistId() : null);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/conversations/{id}/reset")
    @Transactional
    public ResponseEntity<Void> resetBotSession(@PathVariable UUID id) {
        chatService.resetBotSession(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/bot/status")
    public ResponseEntity<Map<String, Object>> getBotStatus() {
        return ResponseEntity.ok(Map.of(
                "status", "CONNECTED",
                "instance", "cefan-studio",
                "webhookActive", true,
                "timestamp", OffsetDateTime.now()
        ));
    }

    @PatchMapping("/conversations/{id}/state")
    @Transactional
    public ResponseEntity<ConversationSessionDto> updateConversationState(
            @PathVariable UUID id,
            @RequestBody UpdateStateRequest request) {
        if (request == null || request.getState() == null) {
            throw new IllegalArgumentException("O novo estado da conversa é obrigatório.");
        }

        ConversationSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sessão de atendimento não encontrada: " + id));

        ConversationState oldState = session.getCurrentState();
        session.setCurrentState(request.getState());
        session.setLastInteractionAt(OffsetDateTime.now());
        session = sessionRepository.save(session);

        Customer customer = session.getCustomer();
        if (Boolean.TRUE.equals(request.getNotifyCustomer()) && customer != null) {
            if (request.getState() == ConversationState.MENU && oldState == ConversationState.TRANSBORDO_HUMANO) {
                evolutionApiClient.sendTextMessage(
                        customer.getPhone(),
                        "✅ *Atendimento finalizado pelo tatuador.*\n\nO assistente virtual do *Vício Tattoo* voltou a ficar ativo! Digite *1* para Orçamento, *2* para Agendar ou *3* para Falar com o Tatuador."
                );
            } else if (request.getState() == ConversationState.TRANSBORDO_HUMANO) {
                evolutionApiClient.sendTextMessage(
                        customer.getPhone(),
                        "👨‍🎨 Olá, " + customer.getName() + "! Um tatuador assumiu o seu atendimento por aqui."
                );
            }
        }

        return ResponseEntity.ok(ConversationSessionDto.fromEntity(session));
    }

    // ==========================================================
    // 3. NOTIFICAÇÕES SSE & PREFERÊNCIAS (/api/v1/notifications)
    // ==========================================================

    @GetMapping("/notifications")
    @Transactional(readOnly = true)
    public ResponseEntity<List<NotificationEventDto>> listNotifications() {
        List<NotificationEventDto> result = new ArrayList<>(recentNotifications);

        if (notifyHumanRequest) {
            List<ConversationSession> humanSessions = sessionRepository
                    .findByCurrentStateOrderByLastInteractionAtDesc(ConversationState.TRANSBORDO_HUMANO);
            for (ConversationSession s : humanSessions) {
                boolean alreadyTracked = result.stream()
                        .anyMatch(n -> "HUMAN_REQUEST".equals(n.getType()) && Objects.equals(n.getCustomerPhone(), s.getCustomer().getPhone()));
                if (!alreadyTracked) {
                    result.add(0, NotificationEventDto.builder()
                            .id(s.getId().toString())
                            .type("HUMAN_REQUEST")
                            .title("Aguardando Tatuador")
                            .message(s.getCustomer().getName() + " solicitou atendimento humano no WhatsApp.")
                            .customerPhone(s.getCustomer().getPhone())
                            .createdAt(s.getLastInteractionAt())
                            .read(false)
                            .build());
                }
            }
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping(value = "/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications() {
        SseEmitter emitter = new SseEmitter(0L);
        sseEmitters.add(emitter);

        emitter.onCompletion(() -> sseEmitters.remove(emitter));
        emitter.onTimeout(() -> sseEmitters.remove(emitter));
        emitter.onError(e -> sseEmitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("status", "connected", "timestamp", OffsetDateTime.now().toString())));
        } catch (IOException e) {
            sseEmitters.remove(emitter);
        }

        return emitter;
    }

    @GetMapping("/notifications/preferences")
    @Transactional(readOnly = true)
    public ResponseEntity<NotificationPreferenceDto> getPreferences() {
        List<UserNotificationPreference> prefs = entityManager
                .createQuery("SELECT p FROM UserNotificationPreference p", UserNotificationPreference.class)
                .getResultList();

        if (!prefs.isEmpty()) {
            UserNotificationPreference p = prefs.get(0);
            notifyNewLead = Boolean.TRUE.equals(p.getNotifyNewLead());
            notifyPaymentReceived = Boolean.TRUE.equals(p.getNotifyPaymentReceived());
            notifyHumanRequest = Boolean.TRUE.equals(p.getNotifyHumanRequest());
        }

        return ResponseEntity.ok(NotificationPreferenceDto.builder()
                .notifyNewLead(notifyNewLead)
                .notifyPaymentReceived(notifyPaymentReceived)
                .notifyHumanRequest(notifyHumanRequest)
                .build());
    }

    @PutMapping("/notifications/preferences")
    @Transactional
    public ResponseEntity<NotificationPreferenceDto> updatePreferences(@RequestBody NotificationPreferenceDto dto) {
        if (dto.getNotifyNewLead() != null) {
            notifyNewLead = dto.getNotifyNewLead();
        }
        if (dto.getNotifyPaymentReceived() != null) {
            notifyPaymentReceived = dto.getNotifyPaymentReceived();
        }
        if (dto.getNotifyHumanRequest() != null) {
            notifyHumanRequest = dto.getNotifyHumanRequest();
        }

        List<UserNotificationPreference> prefs = entityManager
                .createQuery("SELECT p FROM UserNotificationPreference p", UserNotificationPreference.class)
                .getResultList();

        if (!prefs.isEmpty()) {
            UserNotificationPreference pref = prefs.get(0);
            pref.setNotifyNewLead(notifyNewLead);
            pref.setNotifyPaymentReceived(notifyPaymentReceived);
            pref.setNotifyHumanRequest(notifyHumanRequest);
            entityManager.merge(pref);
        } else {
            List<User> users = userRepository.findAll();
            User user = users.isEmpty()
                    ? userRepository.save(User.builder()
                            .name(adminName)
                            .email(adminEmail)
                            .role(UserRole.OWNER)
                            .isActive(true)
                            .build())
                    : users.get(0);

            UserNotificationPreference pref = UserNotificationPreference.builder()
                    .user(user)
                    .notifyNewLead(notifyNewLead)
                    .notifyPaymentReceived(notifyPaymentReceived)
                    .notifyHumanRequest(notifyHumanRequest)
                    .build();
            entityManager.persist(pref);
        }

        return ResponseEntity.ok(NotificationPreferenceDto.builder()
                .notifyNewLead(notifyNewLead)
                .notifyPaymentReceived(notifyPaymentReceived)
                .notifyHumanRequest(notifyHumanRequest)
                .build());
    }

    public void publishNotification(String type, String title, String message, String customerPhone) {
        if ("NEW_LEAD".equals(type) && !notifyNewLead) return;
        if ("PAYMENT_RECEIVED".equals(type) && !notifyPaymentReceived) return;
        if ("HUMAN_REQUEST".equals(type) && !notifyHumanRequest) return;

        NotificationEventDto event = NotificationEventDto.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .title(title)
                .message(message)
                .customerPhone(customerPhone)
                .createdAt(OffsetDateTime.now())
                .read(false)
                .build();

        recentNotifications.add(0, event);
        while (recentNotifications.size() > 50) {
            recentNotifications.remove(recentNotifications.size() - 1);
        }

        for (SseEmitter emitter : sseEmitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(event));
            } catch (IOException e) {
                sseEmitters.remove(emitter);
            }
        }
    }

    // ==========================================================
    // 4. TRATAMENTO GLOBAL DE EXCEÇÕES DE NEGÓCIO (@RestControllerAdvice)
    // ==========================================================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage() != null ? ex.getMessage() : "Requisição inválida."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("Dados de entrada inválidos.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", msg));
    }

    // ==========================================================
    // DTOs INTERNOS
    // ==========================================================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        private String email;
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendMessageRequest {
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateStateRequest {
        private ConversationState state;
        private Boolean notifyCustomer;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationPreferenceDto {
        private Boolean notifyNewLead;
        private Boolean notifyPaymentReceived;
        private Boolean notifyHumanRequest;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationEventDto {
        private String id;
        private String type;
        private String title;
        private String message;
        private String customerPhone;
        private OffsetDateTime createdAt;
        private boolean read;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignArtistRequest {
        private UUID artistId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationSessionDto {
        private UUID id;
        private UUID customerId;
        private String customerName;
        private String customerPhone;
        private ConversationState currentState;
        private String contextData;
        private UUID assignedUserId;
        private String assignedArtistName;
        private OffsetDateTime lastInteractionAt;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        public static ConversationSessionDto fromEntity(ConversationSession session) {
            return ConversationSessionDto.builder()
                    .id(session.getId())
                    .customerId(session.getCustomer() != null ? session.getCustomer().getId() : null)
                    .customerName(session.getCustomer() != null ? session.getCustomer().getName() : "Cliente")
                    .customerPhone(session.getCustomer() != null ? session.getCustomer().getPhone() : "")
                    .currentState(session.getCurrentState())
                    .contextData(session.getContextData())
                    .assignedUserId(session.getAssignedUser() != null ? session.getAssignedUser().getId() : null)
                    .assignedArtistName(session.getAssignedArtistName())
                    .lastInteractionAt(session.getLastInteractionAt())
                    .createdAt(session.getCreatedAt())
                    .updatedAt(session.getUpdatedAt())
                    .build();
        }
    }
}
