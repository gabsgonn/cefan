package br.com.cefan.whatsapp.api.controller;

import br.com.cefan.whatsapp.domain.model.*;
import br.com.cefan.whatsapp.domain.repository.*;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudioAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ConversationSessionRepository sessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private TenantConfigRepository tenantConfigRepository;

    @MockitoBean
    private EvolutionApiClient evolutionApiClient;

    private User ownerUser;
    private User artistCarlos;
    private User artistMariana;
    private Customer customer1;
    private Customer customer2;
    private ConversationSession sessionCarlos;
    private ConversationSession sessionMariana;

    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAll();
        sessionRepository.deleteAll();

        TenantConfig config = tenantConfigRepository.findAll().stream().findFirst().orElseGet(() ->
                tenantConfigRepository.save(TenantConfig.builder()
                        .studioName("Vício Tattoo CWB")
                        .defaultHourlyRate(new BigDecimal("150.00"))
                        .fixedSetupCost(new BigDecimal("50.00"))
                        .botResetMessage("🔄 Sessão reiniciada pelo painel de controle!")
                        .build())
        );
        config.setBotResetMessage("🔄 Sessão reiniciada pelo painel de controle!");
        tenantConfigRepository.save(config);

        ownerUser = userRepository.findByEmail("admin@viciotattoo.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Dono - Vício Tattoo")
                        .email("admin@viciotattoo.com")
                        .role(UserRole.OWNER)
                        .isActive(true)
                        .build())
        );

        artistCarlos = userRepository.findByEmail("carlos.test@viciotattoo.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Carlos Silveira")
                        .email("carlos.test@viciotattoo.com")
                        .role(UserRole.ARTIST)
                        .specialties("Realismo")
                        .isActive(true)
                        .build())
        );

        artistMariana = userRepository.findByEmail("mariana.test@viciotattoo.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Mariana Rocha")
                        .email("mariana.test@viciotattoo.com")
                        .role(UserRole.ARTIST)
                        .specialties("Fineline")
                        .isActive(true)
                        .build())
        );

        customer1 = customerRepository.findByPhone("5541999990001").orElseGet(() ->
                customerRepository.save(Customer.builder()
                        .name("Cliente Realismo")
                        .phone("5541999990001")
                        .build())
        );

        customer2 = customerRepository.findByPhone("5541999990002").orElseGet(() ->
                customerRepository.save(Customer.builder()
                        .name("Cliente Fineline")
                        .phone("5541999990002")
                        .build())
        );

        sessionCarlos = sessionRepository.save(ConversationSession.builder()
                .customer(customer1)
                .currentState(ConversationState.TRANSBORDO_HUMANO)
                .assignedUser(artistCarlos)
                .assignedArtistName(artistCarlos.getName())
                .lastInteractionAt(OffsetDateTime.now())
                .build());

        sessionMariana = sessionRepository.save(ConversationSession.builder()
                .customer(customer2)
                .currentState(ConversationState.ORCAMENTO_AGUARDANDO_FOTO)
                .assignedUser(artistMariana)
                .assignedArtistName(artistMariana.getName())
                .contextData("{\"style\":\"Fineline\",\"minPrice\":180,\"maxPrice\":220}")
                .lastInteractionAt(OffsetDateTime.now())
                .build());
    }

    @Test
    void ownerShouldSeeAllConversationsWhileArtistSeesOnlyAssigned() throws Exception {
        // 1. OWNER vê todas as conversas (2 sessões)
        mockMvc.perform(get("/api/v1/conversations")
                        .with(user("admin@viciotattoo.com").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // 2. ARTIST (Carlos) vê estritamente sua própria conversa (1 sessão)
        mockMvc.perform(get("/api/v1/conversations")
                        .with(user("carlos.test@viciotattoo.com").roles("ARTIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].customerName").value("Cliente Realismo"))
                .andExpect(jsonPath("$[0].assignedArtistName").value("Carlos Silveira"));
    }

    @Test
    void artistShouldBeForbiddenFromReadingOrSendingMessagesToOtherArtistSession() throws Exception {
        // Carlos tentando ler mensagens da sessão atribuída à Mariana -> 403 FORBIDDEN
        mockMvc.perform(get("/api/v1/conversations/" + sessionMariana.getId() + "/messages")
                        .with(user("carlos.test@viciotattoo.com").roles("ARTIST")))
                .andExpect(status().isForbidden());

        // Carlos tentando enviar mensagem na sessão da Mariana -> 403 FORBIDDEN
        mockMvc.perform(post("/api/v1/conversations/" + sessionMariana.getId() + "/messages")
                        .with(user("carlos.test@viciotattoo.com").roles("ARTIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "Tentativa indevida"))))
                .andExpect(status().isForbidden());

        // Carlos tentando abrir stream SSE da sessão da Mariana -> 403 FORBIDDEN
        mockMvc.perform(get("/api/v1/conversations/" + sessionMariana.getId() + "/stream")
                        .with(user("carlos.test@viciotattoo.com").roles("ARTIST")))
                .andExpect(status().isForbidden());

        // Carlos tentando alterar estado ou resetar sessão da Mariana -> 403 FORBIDDEN
        mockMvc.perform(patch("/api/v1/conversations/" + sessionMariana.getId() + "/state")
                        .with(user("carlos.test@viciotattoo.com").roles("ARTIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("state", "MENU"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/conversations/" + sessionMariana.getId() + "/reset")
                        .with(user("carlos.test@viciotattoo.com").roles("ARTIST")))
                .andExpect(status().isForbidden());

        // Carlos tentando reatribuir conversa para outro artista -> 403 FORBIDDEN (exclusivo OWNER)
        mockMvc.perform(post("/api/v1/conversations/" + sessionCarlos.getId() + "/assign")
                        .with(user("carlos.test@viciotattoo.com").roles("ARTIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("artistId", artistMariana.getId().toString()))))
                .andExpect(status().isForbidden());

        // Carlos enviando mensagem na própria sessão -> 200 OK
        mockMvc.perform(post("/api/v1/conversations/" + sessionCarlos.getId() + "/messages")
                        .with(user("carlos.test@viciotattoo.com").roles("ARTIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "Olá! Vamos fechar seu realismo?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senderType").value("AGENT"))
                .andExpect(jsonPath("$.content").value("Olá! Vamos fechar seu realismo?"));

        verify(evolutionApiClient).sendTextMessage(eq("5541999990001"), eq("Olá! Vamos fechar seu realismo?"));
    }

    @Test
    void shouldResetBotSessionUsingParameterizedMessageAndClearContext() throws Exception {
        mockMvc.perform(post("/api/v1/conversations/" + sessionMariana.getId() + "/reset")
                        .with(user("admin@viciotattoo.com").roles("OWNER")))
                .andExpect(status().isOk());

        ConversationSession updated = sessionRepository.findById(sessionMariana.getId()).orElseThrow();
        assertEquals(ConversationState.MENU, updated.getCurrentState());
        assertNull(updated.getContextData());

        verify(evolutionApiClient).sendTextMessage(eq("5541999990002"), contains("Sessão reiniciada pelo painel de controle!"));
    }

    @Test
    void shouldReturnDiagnosticsHealthReconnectAndReportIssue() throws Exception {
        mockMvc.perform(get("/api/v1/bot/health-status")
                        .with(user("admin@viciotattoo.com").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONNECTED"))
                .andExpect(jsonPath("$.springBootOnline").value(true))
                .andExpect(jsonPath("$.postgresOnline").value(true))
                .andExpect(jsonPath("$.mercadoPagoWebhookActive").value(true));

        mockMvc.perform(post("/api/v1/bot/reconnect")
                        .with(user("admin@viciotattoo.com").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONNECTED"))
                .andExpect(jsonPath("$.message", containsString("reiniciada")));

        mockMvc.perform(post("/api/v1/bot/report-issue")
                        .with(user("admin@viciotattoo.com").roles("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "category", "ORCAMENTO_IA",
                                "description", "Cliente enviou foto escura",
                                "sessionId", sessionCarlos.getId().toString()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPORTED"))
                .andExpect(jsonPath("$.protocol", startsWith("INC-")));

        // Notificações filtradas por perfil: Mariana em TRANSBORDO_HUMANO não deve aparecer para Carlos
        sessionMariana.setCurrentState(ConversationState.TRANSBORDO_HUMANO);
        sessionRepository.save(sessionMariana);

        mockMvc.perform(get("/api/v1/notifications")
                        .with(user("carlos.test@viciotattoo.com").roles("ARTIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].customerPhone").value("5541999990001"));
    }
}
