package br.com.cefan.whatsapp;

import br.com.cefan.whatsapp.api.dto.TenantConfigDto;
import br.com.cefan.whatsapp.domain.model.*;
import br.com.cefan.whatsapp.domain.repository.*;
import br.com.cefan.whatsapp.infrastructure.ai.GeminiVisionClient;
import br.com.cefan.whatsapp.infrastructure.ai.dto.TattooAnalysisResult;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import br.com.cefan.whatsapp.infrastructure.mercadopago.MercadoPagoPaymentClient;
import br.com.cefan.whatsapp.infrastructure.mercadopago.dto.MercadoPagoPaymentResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suíte de Homologação Piloto de Ponta a Ponta (Fase 4 - Vício Tattoo CWB).
 * Valida todos os 5 Critérios de Aceite da Entrega Final (Seção 5 do plano-final.md).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HomologacaoPilotoE2ETest {

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
    private AppointmentRepository appointmentRepository;

    @Autowired
    private TenantConfigRepository tenantConfigRepository;

    @Autowired
    private javax.sql.DataSource dataSource;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @MockitoBean
    private EvolutionApiClient evolutionApiClient;

    @MockitoBean
    private GeminiVisionClient geminiVisionClient;

    @MockitoBean
    private MercadoPagoPaymentClient mercadoPagoPaymentClient;

    private String ownerToken;
    private String marianaToken;
    private String carlosToken;

    @BeforeEach
    void setUp() throws Exception {
        when(evolutionApiClient.checkConnectionState()).thenReturn(true);
        when(evolutionApiClient.restartInstance()).thenReturn(true);
        when(evolutionApiClient.getDefaultInstance()).thenReturn("cefan-studio");

        // 1. Login como Dono (OWNER)
        MvcResult ownerLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "admin@viciotattoo.com",
                                "password", "cefan123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andReturn();
        ownerToken = objectMapper.readTree(ownerLogin.getResponse().getContentAsString()).get("token").asText();

        // 2. Login como Tatuadora Mariana Rocha (Fineline) e Carlos Silveira (Realismo)
        MvcResult marianaLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "mariana@viciotattoo.com",
                                "password", "cefan123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ARTIST"))
                .andReturn();
        marianaToken = objectMapper.readTree(marianaLogin.getResponse().getContentAsString()).get("token").asText();

        MvcResult carlosLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "carlos@viciotattoo.com",
                                "password", "cefan123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ARTIST"))
                .andReturn();
        carlosToken = objectMapper.readTree(carlosLogin.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void shouldCompleteFullPilotHomologationJourneyEndToEnd() throws Exception {
        // =========================================================================
        // CRITÉRIO 5: Persistência Confiável de Parâmetros Financeiros, PIX e Horários
        // =========================================================================
        TenantConfigDto updatedConfig = TenantConfigDto.builder()
                .studioName("Vício Tattoo CWB")
                .defaultHourlyRate(new BigDecimal("180.00"))
                .fixedSetupCost(new BigDecimal("60.00"))
                .absorbCreditCardFees(true)
                .signalPercentage(new BigDecimal("30.00"))
                .minDepositAmount(new BigDecimal("50.00"))
                .pixExpirationMinutes(90)
                .openingTime("10:00")
                .closingTime("20:00")
                .workingDays("SEG-SAB")
                .pixPaymentMessage("⚡ Sinal reservado na grade do Vício Tattoo CWB!")
                .pixConfirmedMessage("🎉 Pagamento PIX aprovado com sucesso pelo Vício Tattoo CWB!")
                .absenceMessage("🌙 Estúdio fechado no momento (Seg-Sáb das 10h às 20h).")
                .build();

        mockMvc.perform(put("/api/v1/tenant-configs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedConfig)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultHourlyRate").value(180.0))
                .andExpect(jsonPath("$.fixedSetupCost").value(60.0))
                .andExpect(jsonPath("$.pixExpirationMinutes").value(90))
                .andExpect(jsonPath("$.openingTime").value("10:00"))
                .andExpect(jsonPath("$.closingTime").value("20:00"))
                .andExpect(jsonPath("$.workingDays").value("SEG-SAB"));

        // Confirma leitura idêntica após recarregar (F5)
        mockMvc.perform(get("/api/v1/tenant-configs")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultHourlyRate").value(180.0))
                .andExpect(jsonPath("$.fixedSetupCost").value(60.0))
                .andExpect(jsonPath("$.pixExpirationMinutes").value(90))
                .andExpect(jsonPath("$.absenceMessage", containsString("Seg-Sáb das 10h às 20h")));

        // Profissional (ARTIST) é bloqueado estritamente em /api/v1/tenant-configs (GET e PUT)
        mockMvc.perform(get("/api/v1/tenant-configs")
                        .header("Authorization", "Bearer " + marianaToken))
                .andExpect(status().isForbidden());

        // =========================================================================
        // CRITÉRIO 2 & 3: Orçamento Coerente com IA (Gemini Vision) + Histórico Live Chat
        // =========================================================================
        when(geminiVisionClient.analyzeTattoo(anyString(), anyString(), anyString()))
                .thenReturn(TattooAnalysisResult.builder()
                        .style("Fineline / Traço Fino")
                        .sizeCategory("PEQUENA")
                        .estimatedSizeCm("7x4 cm")
                        .complexityLevel("BAIXA")
                        .complexityMultiplier(1.0)
                        .estimatedHours(1.0)
                        .suggestedBodyLocation("Antebraço")
                        .technicalNotes("Traço fino delicado com agulha 03RL.")
                        .recommendedArtists(List.of("Mariana Rocha"))
                        .primaryColors(List.of("Preto"))
                        .build());

        // Cliente envia foto diretamente no WhatsApp (mesmo estando no estado inicial MENU)
        String webhookFotoPayload = """
                {
                  "event": "messages.upsert",
                  "instance": "cefan-studio",
                  "data": {
                    "key": {
                      "remoteJid": "5541988887777@s.whatsapp.net",
                      "fromMe": false,
                      "id": "WAMID-FOTO-001"
                    },
                    "pushName": "Beatriz Cliente",
                    "message": {
                      "imageMessage": {
                        "caption": "Quero fazer essa flor fineline no antebraço, uns 7cm",
                        "mimetype": "image/jpeg",
                        "base64": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
                      }
                    }
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/webhooks/evolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookFotoPayload))
                .andExpect(status().isOk());

        // Verifica que a sessão avançou para ORCAMENTO_AGUARDANDO_FOTO com valor coerente (1h * 180 + 60 = R$ 240 base -> R$ 216 a R$ 276)
        Customer beatriz = customerRepository.findByPhone("5541988887777").orElseThrow();
        ConversationSession session = sessionRepository.findByCustomer(beatriz).orElseThrow();
        assertEquals(ConversationState.ORCAMENTO_AGUARDANDO_FOTO, session.getCurrentState());
        assertNotNull(session.getContextData());

        JsonNode ctx = objectMapper.readTree(session.getContextData());
        assertEquals("Fineline / Traço Fino", ctx.get("style").asText());
        assertEquals(240.0, ctx.get("basePrice").asDouble(), 0.01);
        assertTrue(ctx.get("referenceImageUrl").asText().startsWith("data:image/jpeg;base64,"));

        // =========================================================================
        // CRITÉRIO 4: Agendamento com Validação de Horário + PIX Mercado Pago
        // =========================================================================
        // Cliente envia "1" (pós-orçamento: 1 = Agendar sessão) para iniciar agendamento
        String webhookOpcaoAgendar = """
                {
                  "event": "messages.upsert",
                  "instance": "cefan-studio",
                  "data": {
                    "key": {
                      "remoteJid": "5541988887777@s.whatsapp.net",
                      "fromMe": false,
                      "id": "WAMID-AGENDAR-002"
                    },
                    "pushName": "Beatriz Cliente",
                    "message": {
                      "conversation": "1"
                    }
                  }
                }
                """;
        mockMvc.perform(post("/api/v1/webhooks/evolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookOpcaoAgendar))
                .andExpect(status().isOk());

        when(mercadoPagoPaymentClient.createPixPayment(any(), any(), any(), any(), any()))
                .thenReturn(MercadoPagoPaymentResponse.builder()
                        .id(555444333L)
                        .status("pending")
                        .transactionAmount(new BigDecimal("72.00"))
                        .pointOfInteraction(MercadoPagoPaymentResponse.PointOfInteraction.builder()
                                .transactionData(MercadoPagoPaymentResponse.TransactionData.builder()
                                        .qrCode("00020126580014br.gov.bcb.pix0136vicio-tattoo-pix-homologacao")
                                        .build())
                                .build())
                        .build());

        // Etapa 1 (Profissional): cliente confirma a artista sugerida no orçamento (Mariana Rocha)
        String webhookConfirmaArtista = """
                {
                  "event": "messages.upsert",
                  "instance": "cefan-studio",
                  "data": {
                    "key": {
                      "remoteJid": "5541988887777@s.whatsapp.net",
                      "fromMe": false,
                      "id": "WAMID-ARTISTA-003"
                    },
                    "pushName": "Beatriz Cliente",
                    "message": {
                      "conversation": "sim"
                    }
                  }
                }
                """;
        mockMvc.perform(post("/api/v1/webhooks/evolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookConfirmaArtista))
                .andExpect(status().isOk());

        // Etapa 2 (Data): cliente escolhe uma data futura válida (próxima sexta)
        String webhookEscolheData = """
                {
                  "event": "messages.upsert",
                  "instance": "cefan-studio",
                  "data": {
                    "key": {
                      "remoteJid": "5541988887777@s.whatsapp.net",
                      "fromMe": false,
                      "id": "WAMID-DATA-004"
                    },
                    "pushName": "Beatriz Cliente",
                    "message": {
                      "conversation": "próxima sexta"
                    }
                  }
                }
                """;
        mockMvc.perform(post("/api/v1/webhooks/evolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookEscolheData))
                .andExpect(status().isOk());

        // Etapa 3 (Horário): cliente escolhe um dos horários disponíveis (15h)
        String webhookEscolheHorario = """
                {
                  "event": "messages.upsert",
                  "instance": "cefan-studio",
                  "data": {
                    "key": {
                      "remoteJid": "5541988887777@s.whatsapp.net",
                      "fromMe": false,
                      "id": "WAMID-HORA-005"
                    },
                    "pushName": "Beatriz Cliente",
                    "message": {
                      "conversation": "15h"
                    }
                  }
                }
                """;
        mockMvc.perform(post("/api/v1/webhooks/evolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookEscolheHorario))
                .andExpect(status().isOk());

        // Verifica agendamento criado com status AGUARDANDO_PAGAMENTO, duração mínima de 2h e Mariana atribuída
        List<Appointment> appointments = appointmentRepository.findAll();
        assertEquals(1, appointments.size());
        Appointment appt = appointments.get(0);
        assertEquals(AppointmentStatus.AGUARDANDO_PAGAMENTO, appt.getStatus());
        assertNotNull(appt.getArtist());
        User apptArtist = userRepository.findById(appt.getArtist().getId()).orElseThrow();
        assertEquals("Mariana Rocha", apptArtist.getName());
        assertEquals(15, appt.getScheduledStart().getHour());
        assertEquals(17, appt.getScheduledEnd().getHour());
        assertEquals(0, new BigDecimal("240.00").compareTo(appt.getTotalPrice()));
        assertEquals(0, new BigDecimal("72.00").compareTo(appt.getDepositAmount())); // 30% de 240 = 72.00

        // Verifica que o código PIX foi enviado em mensagem separada para facilitar copiar
        List<ChatMessage> beatrizMessages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        assertTrue(beatrizMessages.stream().anyMatch(m ->
                "00020126580014br.gov.bcb.pix0136vicio-tattoo-pix-homologacao".equals(m.getContent())));

        // =========================================================================
        // CRITÉRIO 1: Isolamento Estrito entre Profissionais (Mariana vs Carlos vs Dono)
        // =========================================================================
        // Mariana vê a conversa da Beatriz e o histórico de mensagens
        mockMvc.perform(get("/api/v1/conversations")
                        .header("Authorization", "Bearer " + marianaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].customerName").value("Beatriz Cliente"))
                .andExpect(jsonPath("$[0].assignedArtistName").value("Mariana Rocha"));

        mockMvc.perform(get("/api/v1/conversations/" + session.getId() + "/messages")
                        .header("Authorization", "Bearer " + marianaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4))));

        // Carlos NÃO vê a conversa nem os agendamentos da Mariana
        mockMvc.perform(get("/api/v1/conversations")
                        .header("Authorization", "Bearer " + carlosToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/conversations/" + session.getId() + "/messages")
                        .header("Authorization", "Bearer " + carlosToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/appointments")
                        .header("Authorization", "Bearer " + carlosToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/appointments")
                        .header("Authorization", "Bearer " + marianaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // =========================================================================
        // WEBHOOK MERCADO PAGO: Confirmação Automática do Sinal PIX
        // =========================================================================
        when(mercadoPagoPaymentClient.getPayment("555444333"))
                .thenReturn(MercadoPagoPaymentResponse.builder()
                        .id(555444333L)
                        .status("approved")
                        .statusDetail("accredited")
                        .build());

        mockMvc.perform(post("/api/v1/webhooks/mercadopago")
                        .param("data.id", "555444333")
                        .param("topic", "payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"payment.updated\",\"data\":{\"id\":\"555444333\"}}"))
                .andExpect(status().isOk());

        Appointment confirmedAppt = appointmentRepository.findById(appt.getId()).orElseThrow();
        assertEquals(AppointmentStatus.CONFIRMADO, confirmedAppt.getStatus());

        // Notificação PAYMENT_RECEIVED publicada para o painel
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].type", hasItem("PAYMENT_RECEIVED")));

        // =========================================================================
        // TRANSBORDO HUMANO FORA DE EXPEDIENTE: Valida absenceMessage + HUMAN_REQUEST
        // =========================================================================
        // Configura expediente encerrado (ex: 00:00 às 00:01) para garantir fora de expediente em qualquer horário de execução
        updatedConfig.setOpeningTime("00:00");
        updatedConfig.setClosingTime("00:01");
        updatedConfig.setAbsenceMessage("🌙 Estúdio fora de expediente (atendimento automático ativo).");
        mockMvc.perform(put("/api/v1/tenant-configs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedConfig)))
                .andExpect(status().isOk());

        String webhookTransbordo = """
                {
                  "event": "messages.upsert",
                  "instance": "cefan-studio",
                  "data": {
                    "key": {
                      "remoteJid": "5541988887777@s.whatsapp.net",
                      "fromMe": false,
                      "id": "WAMID-HUMANO-004"
                    },
                    "pushName": "Beatriz Cliente",
                    "message": {
                      "conversation": "3"
                    }
                  }
                }
                """;
        mockMvc.perform(post("/api/v1/webhooks/evolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookTransbordo))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/conversations/" + session.getId() + "/messages")
                        .header("Authorization", "Bearer " + marianaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].content", hasItem(containsString("Estúdio fora de expediente"))));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].type", hasItem("HUMAN_REQUEST")));

        // =========================================================================
        // SELEÇÃO DE TAMANHO "FECHAMENTO": Valor "A combinar" + Encaminhamento ao Especialista
        // =========================================================================
        when(geminiVisionClient.analyzeTattoo(anyString(), anyString(), anyString()))
                .thenReturn(TattooAnalysisResult.builder()
                        .style("Realismo Preto e Cinza")
                        .sizeCategory("MEDIA")
                        .estimatedSizeCm("Média (11 a 18 cm)")
                        .complexityLevel("ALTA")
                        .complexityMultiplier(1.4)
                        .estimatedHours(3.5)
                        .suggestedBodyLocation("Braço")
                        .technicalNotes("Realismo de alto contraste.")
                        .recommendedArtists(List.of("Carlos Silveira"))
                        .primaryColors(List.of("Preto", "Cinza"))
                        .build());

        // Novo cliente envia foto e depois escolhe opção "E" (Fechamento)
        String webhookNovoClienteFoto = """
                {
                  "event": "messages.upsert",
                  "instance": "cefan-studio",
                  "data": {
                    "key": {
                      "remoteJid": "5541977776666@s.whatsapp.net",
                      "fromMe": false,
                      "id": "WAMID-FECHAMENTO-FOTO-001"
                    },
                    "pushName": "Rafael Fechamento",
                    "message": {
                      "imageMessage": {
                        "caption": "Quero orçar esse leão em realismo",
                        "mimetype": "image/jpeg",
                        "base64": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
                      }
                    }
                  }
                }
                """;
        mockMvc.perform(post("/api/v1/webhooks/evolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookNovoClienteFoto))
                .andExpect(status().isOk());

        String webhookEscolheFechamento = """
                {
                  "event": "messages.upsert",
                  "instance": "cefan-studio",
                  "data": {
                    "key": {
                      "remoteJid": "5541977776666@s.whatsapp.net",
                      "fromMe": false,
                      "id": "WAMID-FECHAMENTO-OPCAO-002"
                    },
                    "pushName": "Rafael Fechamento",
                    "message": {
                      "conversation": "Fechamento"
                    }
                  }
                }
                """;
        mockMvc.perform(post("/api/v1/webhooks/evolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookEscolheFechamento))
                .andExpect(status().isOk());

        Customer rafael = customerRepository.findByPhone("5541977776666").orElseThrow();
        ConversationSession rafaelSession = sessionRepository.findByCustomer(rafael).orElseThrow();
        assertEquals(ConversationState.TRANSBORDO_HUMANO, rafaelSession.getCurrentState());
        assertEquals("Carlos Silveira", rafaelSession.getAssignedArtistName());
        assertNotNull(rafaelSession.getAssignedUser());
        User rafaelArtist = userRepository.findById(rafaelSession.getAssignedUser().getId()).orElseThrow();
        assertEquals("Carlos Silveira", rafaelArtist.getName());

        JsonNode rafaelCtx = objectMapper.readTree(rafaelSession.getContextData());
        assertEquals("FECHAMENTO", rafaelCtx.get("sizeCategory").asText());
        assertEquals("A combinar", rafaelCtx.get("priceDisplay").asText());
        assertTrue(rafaelCtx.get("isFechamento").asBoolean());

        // Carlos Silveira agora vê a conversa do Rafael atribuída a ele
        mockMvc.perform(get("/api/v1/conversations")
                        .header("Authorization", "Bearer " + carlosToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].customerName").value("Rafael Fechamento"))
                .andExpect(jsonPath("$[0].assignedArtistName").value("Carlos Silveira"));
    }

    @Test
    @Transactional
    void shouldExecuteFlywayV6MigrationSqlCleanlyAndPopulateDefaultParameters() throws Exception {
        entityManager.flush();
        TenantConfig beforeFix = tenantConfigRepository.findAll().stream().findFirst().orElseThrow();
        beforeFix.setAgendamentoIntroMessage("📅 *Agendamento de Sessão*\\n\\nNossa equipe conta com 11 artistas.");
        beforeFix.setPixPaymentMessage("🗓️ *Pré-Agendamento Criado!*\\n\\nBloqueamos seu horário.");
        tenantConfigRepository.saveAndFlush(beforeFix);

        org.springframework.jdbc.datasource.init.ResourceDatabasePopulator populator =
                new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                        new org.springframework.core.io.ClassPathResource("db/migration/V6__operating_hours_and_pix_expiration.sql"),
                        new org.springframework.core.io.ClassPathResource("db/migration/V7__structured_tattoo_size_options.sql"),
                        new org.springframework.core.io.ClassPathResource("db/migration/V9__fix_literal_newlines_in_tenant_messages.sql"),
                        new org.springframework.core.io.ClassPathResource("db/migration/V12__simplify_orcamento_intro_message.sql"),
                        new org.springframework.core.io.ClassPathResource("db/migration/V14__add_quote_display_toggles_to_tenant_configs.sql")
                );
        populator.populate( org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource));
        entityManager.clear();

        TenantConfig config = tenantConfigRepository.findAll().stream().findFirst().orElseThrow();
        assertNotNull(config.getPixExpirationMinutes());
        assertTrue(config.getOrcamentoShowGreeting());
        assertTrue(config.getOrcamentoShowStyle());
        assertTrue(config.getOrcamentoShowSize());
        assertTrue(config.getOrcamentoShowDuration());
        assertTrue(config.getOrcamentoShowComplexity());
        assertTrue(config.getOrcamentoShowLocation());
        assertTrue(config.getOrcamentoShowArtists());
        assertTrue(config.getOrcamentoShowDescription());
        assertTrue(config.getOrcamentoShowPrice());
        assertTrue(config.getOrcamentoShowMaterialsNote());
        assertTrue(config.getOrcamentoShowAdjustHint());
        assertNotNull(config.getOpeningTime());
        assertNotNull(config.getClosingTime());
        assertNotNull(config.getWorkingDays());
        assertNotNull(config.getAbsenceMessage());
        assertTrue(config.getAbsenceMessage().contains("Estúdio Fora do Horário de Expediente"));
        assertFalse(config.getAbsenceMessage().contains("\\n"), "A migração V6 deve gravar quebras de linha reais e não '\\n' literal");
        assertFalse(config.getAgendamentoIntroMessage().contains("\\n"), "A migração V9 deve converter '\\n' literal em quebra de linha real");
        assertTrue(config.getAgendamentoIntroMessage().contains("\n\n"));
        assertFalse(config.getPixPaymentMessage().contains("\\n"), "A migração V9 deve converter '\\n' literal em quebra de linha real");
        assertTrue(config.getPixPaymentMessage().contains("\n\n"));
        assertNotNull(config.getOrcamentoIntroMessage());
        assertFalse(config.getOrcamentoIntroMessage().contains("Digite *A*"), "A migração V12 deve remover a escolha A-E da frente do orçamento");
        assertTrue(config.getOrcamentoIntroMessage().contains("foto da tatuagem"));
    }

    @Test
    void shouldCompleteOldSchoolPantherIntegrationFlowAndHideRemainingBalanceWhenShowPriceIsFalse() throws Exception {
        // 1. Dono desativa orcamentoShowPrice no tenant_configs via endpoint PUT /api/v1/tenant-configs
        TenantConfigDto customToggleConfig = TenantConfigDto.builder()
                .studioName("Vício Tattoo CWB")
                .defaultHourlyRate(new BigDecimal("180.00"))
                .fixedSetupCost(new BigDecimal("60.00"))
                .absorbCreditCardFees(true)
                .signalPercentage(new BigDecimal("30.00"))
                .minDepositAmount(new BigDecimal("50.00"))
                .pixExpirationMinutes(120)
                .openingTime("10:00")
                .closingTime("20:00")
                .workingDays("SEG-SAB")
                .orcamentoShowGreeting(true)
                .orcamentoShowStyle(true)
                .orcamentoShowSize(true)
                .orcamentoShowDuration(true)
                .orcamentoShowComplexity(true)
                .orcamentoShowLocation(true)
                .orcamentoShowArtists(true)
                .orcamentoShowDescription(true)
                .orcamentoShowPrice(false)
                .orcamentoShowMaterialsNote(true)
                .orcamentoShowAdjustHint(true)
                .build();

        mockMvc.perform(put("/api/v1/tenant-configs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customToggleConfig)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orcamentoShowPrice").value(false))
                .andExpect(jsonPath("$.orcamentoShowStyle").value(true));

        // 2. Foto da pantera old school -> estilo Old School / Tradicional e tatuador Rodrigo Old School
        when(geminiVisionClient.analyzeTattoo(anyString(), anyString(), anyString()))
                .thenReturn(TattooAnalysisResult.builder()
                        .style("Old School / Tradicional")
                        .sizeCategory("MEDIA")
                        .estimatedSizeCm("Média (11 a 18 cm)")
                        .complexityLevel("MEDIA")
                        .complexityMultiplier(1.2)
                        .estimatedHours(2.5)
                        .detectedBodyLocation("Antebraço")
                        .suggestedBodyLocation("Panturrilha ou Braço")
                        .customerDescription("Pantera negra clássica tradicional com traços firmes e cores vibrantes.")
                        .technicalNotes("Contorno Bold Line 11RL e preenchimento sólido com Magnum.")
                        .recommendedArtists(List.of("Rodrigo Old School"))
                        .primaryColors(List.of("Preto", "Vermelho", "Amarelo"))
                        .fallback(false)
                        .build());

        String webhookPantera = """
                {
                  "event": "messages.upsert",
                  "instance": "cefan-studio",
                  "data": {
                    "key": {
                      "remoteJid": "5541966665555@s.whatsapp.net",
                      "fromMe": false,
                      "id": "WAMID-PANTERA-001"
                    },
                    "pushName": "Lucas Pantera",
                    "message": {
                      "imageMessage": {
                        "caption": "Quero tatuar essa pantera old school no antebraço",
                        "mimetype": "image/jpeg",
                        "base64": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
                      }
                    }
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/webhooks/evolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPantera))
                .andExpect(status().isOk());

        Customer lucas = customerRepository.findByPhone("5541966665555").orElseThrow();
        ConversationSession lucasSession = sessionRepository.findByCustomer(lucas).orElseThrow();
        List<ChatMessage> msgsAfterPhoto = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(lucasSession.getId());

        // Verifica que enviou aviso imediato "Recebi sua foto" e depois o orçamento com estilo Old School / Tradicional, sem preço e sem technicalNotes
        assertTrue(msgsAfterPhoto.stream().anyMatch(m -> m.getContent().contains("Recebi sua foto")));
        ChatMessage quoteBotMsg = msgsAfterPhoto.get(msgsAfterPhoto.size() - 1);
        assertTrue(quoteBotMsg.getContent().contains("Old School / Tradicional"));
        assertTrue(quoteBotMsg.getContent().contains("Rodrigo Old School"));
        assertTrue(quoteBotMsg.getContent().contains("Pantera negra clássica tradicional"));
        assertFalse(quoteBotMsg.getContent().contains("11RL"), "technicalNotes fica apenas no painel para o tatuador");
        assertFalse(quoteBotMsg.getContent().contains("Faixa de Preço Estimada"), "orcamentoShowPrice=false deve ocultar o preço");

        // No painel (contextData), technicalNotes continua disponível para o tatuador
        JsonNode savedCtx = objectMapper.readTree(lucasSession.getContextData());
        assertEquals("Old School / Tradicional", savedCtx.get("style").asText());
        assertTrue(savedCtx.get("technicalNotes").asText().contains("11RL"));

        // 3. Cliente agenda a sessão e gera PIX -> com orcamentoShowPrice=false, o resumo PIX exibe o Sinal mas NÃO exibe o Saldo Restante
        when(mercadoPagoPaymentClient.createPixPayment(any(), any(), any(), any(), any()))
                .thenReturn(MercadoPagoPaymentResponse.builder()
                        .id(999888777L)
                        .status("pending")
                        .transactionAmount(new BigDecimal("180.00"))
                        .pointOfInteraction(MercadoPagoPaymentResponse.PointOfInteraction.builder()
                                .transactionData(MercadoPagoPaymentResponse.TransactionData.builder()
                                        .qrCode("000201PIX-PANTERA-OLD-SCHOOL")
                                        .build())
                                .build())
                        .build());

        for (String stepInput : List.of("1", "sim", "próxima sexta", "14h")) {
            String stepPayload = String.format("""
                    {
                      "event": "messages.upsert",
                      "instance": "cefan-studio",
                      "data": {
                        "key": {
                          "remoteJid": "5541966665555@s.whatsapp.net",
                          "fromMe": false,
                          "id": "WAMID-STEP-%s"
                        },
                        "pushName": "Lucas Pantera",
                        "message": {
                          "conversation": "%s"
                        }
                      }
                    }
                    """, stepInput.hashCode(), stepInput);
            mockMvc.perform(post("/api/v1/webhooks/evolution")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(stepPayload))
                    .andExpect(status().isOk());
        }

        List<ChatMessage> allLucasMsgs = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(lucasSession.getId());
        ChatMessage pixSummaryMsg = allLucasMsgs.stream()
                .filter(m -> m.getContent().contains("Pré-Agendamento Criado") || m.getContent().contains("Sinal de Reserva:"))
                .findFirst()
                .orElseThrow();
        assertTrue(pixSummaryMsg.getContent().contains("Sinal de Reserva:"));
        assertFalse(pixSummaryMsg.getContent().contains("Saldo Restante:"),
                "Quando orcamentoShowPrice=false, a mensagem de pré-agendamento/PIX não deve exibir o Saldo Restante para não revelar o total");
    }
}
