package br.com.cefan.whatsapp.api.controller;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
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
public class StudioAdminController {

    private final ConversationSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final EvolutionApiClient evolutionApiClient;
    private final EntityManager entityManager;

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
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        if (request == null || request.getEmail() == null || request.getPassword() == null) {
            throw new IllegalArgumentException("E-mail e senha são obrigatórios.");
        }

        String email = request.getEmail().trim().toLowerCase();
        String pass = request.getPassword().trim();

        Optional<User> dbUser = userRepository.findByEmail(email);
        boolean authenticated = (email.equalsIgnoreCase(adminEmail) && pass.equals(adminPassword))
                || (dbUser.isPresent() && Boolean.TRUE.equals(dbUser.get().getIsActive()) && pass.equals(adminPassword));

        if (!authenticated) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Credenciais inválidas para o painel administrativo."));
        }

        String name = dbUser.map(User::getName).orElse(adminName);
        String role = dbUser.map(u -> u.getRole() != null ? u.getRole().name() : "OWNER").orElse("OWNER");
        String token = Base64.getEncoder().encodeToString((email + ":" + System.currentTimeMillis()).getBytes());

        return ResponseEntity.ok(Map.of(
                "token", token,
                "name", name,
                "email", email,
                "role", role,
                "studioName", "Vício Tattoo"
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

        List<ConversationSessionDto> dtos = sessions.stream()
                .map(ConversationSessionDto::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/conversations/{id}/messages")
    @Transactional
    public ResponseEntity<ConversationSessionDto> sendHumanMessage(
            @PathVariable UUID id,
            @RequestBody SendMessageRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            throw new IllegalArgumentException("A mensagem não pode estar vazia.");
        }

        ConversationSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sessão de atendimento não encontrada: " + id));

        Customer customer = session.getCustomer();
        String formattedMessage = "👨‍🎨 *Atendimento Vício Tattoo:*\n" + request.getMessage().trim();

        evolutionApiClient.sendTextMessage(customer.getPhone(), formattedMessage);

        session.setLastInteractionAt(OffsetDateTime.now());
        if (session.getCurrentState() != ConversationState.TRANSBORDO_HUMANO) {
            session.setCurrentState(ConversationState.TRANSBORDO_HUMANO);
        }
        session = sessionRepository.save(session);

        return ResponseEntity.ok(ConversationSessionDto.fromEntity(session));
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
                    .lastInteractionAt(session.getLastInteractionAt())
                    .createdAt(session.getCreatedAt())
                    .updatedAt(session.getUpdatedAt())
                    .build();
        }
    }
}
