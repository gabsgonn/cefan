package br.com.cefan.whatsapp.application.service;

import br.com.cefan.whatsapp.api.dto.ChatMessageDto;
import br.com.cefan.whatsapp.domain.model.ChatMessage;
import br.com.cefan.whatsapp.domain.model.ConversationSession;
import br.com.cefan.whatsapp.domain.model.ConversationState;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.model.User;
import br.com.cefan.whatsapp.domain.model.UserRole;
import br.com.cefan.whatsapp.domain.repository.ChatMessageRepository;
import br.com.cefan.whatsapp.domain.repository.ConversationSessionRepository;
import br.com.cefan.whatsapp.domain.repository.UserRepository;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ConversationSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final EvolutionApiClient evolutionApiClient;

    // Mapa de sessões ativas com lista de emitters SSE para streaming em tempo real
    private final Map<UUID, List<SseEmitter>> sessionEmitters = new ConcurrentHashMap<>();
    private final List<SseEmitter> globalEmitters = new CopyOnWriteArrayList<>();

    @Transactional
    public ChatMessageDto recordIncomingMessage(ConversationSession session, Customer customer, String content, String mediaUrl, String mediaType) {
        ChatMessage message = ChatMessage.builder()
                .session(session)
                .customer(customer)
                .senderType(ChatMessage.SenderType.CUSTOMER)
                .senderName(customer.getName() != null && !customer.getName().isBlank() ? customer.getName() : "Cliente")
                .content(content != null ? content : "[Mídia]")
                .mediaUrl(mediaUrl)
                .mediaType(mediaType)
                .isFromMe(false)
                .createdAt(OffsetDateTime.now())
                .build();

        ChatMessage saved = chatMessageRepository.save(message);
        ChatMessageDto dto = ChatMessageDto.fromEntity(saved);
        broadcast(session.getId(), dto);
        return dto;
    }

    @Transactional
    public ChatMessageDto recordBotMessage(ConversationSession session, Customer customer, String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        ChatMessage message = ChatMessage.builder()
                .session(session)
                .customer(customer)
                .senderType(ChatMessage.SenderType.BOT)
                .senderName("IA Vício Tattoo")
                .content(content)
                .isFromMe(true)
                .createdAt(OffsetDateTime.now())
                .build();

        ChatMessage saved = chatMessageRepository.save(message);
        ChatMessageDto dto = ChatMessageDto.fromEntity(saved);
        broadcast(session.getId(), dto);
        return dto;
    }

    @Transactional
    public ChatMessageDto sendAgentMessage(UUID sessionId, UUID agentUserId, String content) {
        ConversationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sessão de conversa não encontrada para ID: " + sessionId));

        Customer customer = session.getCustomer();
        User agent = null;
        String agentName = "Atendente do Estúdio";

        if (agentUserId != null) {
            agent = userRepository.findById(agentUserId).orElse(null);
            if (agent != null) {
                agentName = agent.getName() + " (" + (agent.getRole() == UserRole.OWNER ? "Dono" : "Tatuador") + ")";
            }
        }

        // 1. Envia mensagem para o WhatsApp do cliente via Evolution API
        evolutionApiClient.sendTextMessage(customer.getPhone(), content);

        // 2. Registra mensagem no banco
        ChatMessage message = ChatMessage.builder()
                .session(session)
                .customer(customer)
                .senderType(ChatMessage.SenderType.AGENT)
                .senderUser(agent)
                .senderName(agentName)
                .content(content)
                .isFromMe(true)
                .createdAt(OffsetDateTime.now())
                .build();

        ChatMessage saved = chatMessageRepository.save(message);
        ChatMessageDto dto = ChatMessageDto.fromEntity(saved);

        // Atualiza última interação
        session.setLastInteractionAt(OffsetDateTime.now());
        sessionRepository.save(session);

        // 3. Notifica clientes conectados via SSE
        broadcast(sessionId, dto);
        return dto;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessagesBySession(UUID sessionId) {
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(ChatMessageDto::fromEntity)
                .toList();
    }

    public SseEmitter subscribe(UUID sessionId) {
        SseEmitter emitter = new SseEmitter(180_000L); // 3 minutos timeout
        List<SseEmitter> emitters = sessionEmitters.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError((e) -> emitters.remove(emitter));

        // Envia ping inicial para abrir o fluxo
        try {
            emitter.send(SseEmitter.event().name("INIT").data("Connected to chat stream: " + sessionId));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    public SseEmitter subscribeGlobal() {
        SseEmitter emitter = new SseEmitter(180_000L);
        globalEmitters.add(emitter);

        emitter.onCompletion(() -> globalEmitters.remove(emitter));
        emitter.onTimeout(() -> {
            globalEmitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError((e) -> globalEmitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event().name("INIT").data("Connected to global live chat stream"));
        } catch (IOException e) {
            globalEmitters.remove(emitter);
        }

        return emitter;
    }

    @Transactional
    public void assignArtist(UUID sessionId, UUID artistUserId) {
        ConversationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sessão não encontrada"));

        if (artistUserId == null) {
            session.setAssignedUser(null);
            session.setAssignedArtistName(null);
        } else {
            User artist = userRepository.findById(artistUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Profissional não encontrado"));
            session.setAssignedUser(artist);
            session.setAssignedArtistName(artist.getName());
        }
        sessionRepository.save(session);
        log.info("Sessão [{}] atribuída ao profissional [{}]", sessionId, session.getAssignedArtistName());
    }

    @Transactional
    public void resetBotSession(UUID sessionId) {
        ConversationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sessão não encontrada"));

        session.setCurrentState(ConversationState.MENU);
        session.setContextData(null);
        session.setLastInteractionAt(OffsetDateTime.now());
        sessionRepository.save(session);

        String resetMsg = "🔄 Conversa reiniciada pelo estúdio. Olá! Como podemos te ajudar agora?";
        evolutionApiClient.sendTextMessage(session.getCustomer().getPhone(), resetMsg);
        recordBotMessage(session, session.getCustomer(), resetMsg);

        log.info("Sessão [{}] resetada com sucesso pelo operador do estúdio.", sessionId);
    }

    private void broadcast(UUID sessionId, ChatMessageDto dto) {
        List<SseEmitter> sessionList = sessionEmitters.get(sessionId);
        if (sessionList != null) {
            for (SseEmitter emitter : sessionList) {
                try {
                    emitter.send(SseEmitter.event().name("MESSAGE").data(dto));
                } catch (Exception e) {
                    sessionList.remove(emitter);
                }
            }
        }

        for (SseEmitter emitter : globalEmitters) {
            try {
                emitter.send(SseEmitter.event().name("MESSAGE").data(dto));
            } catch (Exception e) {
                globalEmitters.remove(emitter);
            }
        }
    }
}
