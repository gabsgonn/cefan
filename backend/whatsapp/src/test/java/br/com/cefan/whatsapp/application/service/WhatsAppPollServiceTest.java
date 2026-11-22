package br.com.cefan.whatsapp.application.service;

import br.com.cefan.whatsapp.application.statemachine.UserIntent;
import br.com.cefan.whatsapp.domain.model.ConversationSession;
import br.com.cefan.whatsapp.domain.model.ConversationState;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.model.WhatsAppPoll;
import br.com.cefan.whatsapp.domain.repository.WhatsAppPollRepository;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import br.com.cefan.whatsapp.infrastructure.evolution.dto.EvolutionWebhookRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppPollServiceTest {

    @Mock
    private EvolutionApiClient evolutionApiClient;

    @Mock
    private WhatsAppPollRepository pollRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WhatsAppPollService pollService;
    private Customer customer;
    private ConversationSession session;

    @BeforeEach
    void setUp() {
        pollService = new WhatsAppPollService(evolutionApiClient, pollRepository, objectMapper);
        customer = Customer.builder()
                .id(UUID.randomUUID())
                .name("Gabriel")
                .phone("5541999999999")
                .build();
        session = ConversationSession.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .currentState(ConversationState.ORCAMENTO_AGUARDANDO_FOTO)
                .build();
    }

    @Test
    void shouldPersistPollDeactivatingPreviousAndMapVoteByIndexNotText() {
        when(evolutionApiClient.sendPollMessage(eq("cefan-studio"), eq("5541999999999"), anyString(), anyList(), eq(1)))
                .thenReturn(Optional.of("POLL-MSG-001"));
        when(pollRepository.save(any(WhatsAppPoll.class))).thenAnswer(inv -> inv.getArgument(0));

        List<WhatsAppPollService.PollOption> options = List.of(
                new WhatsAppPollService.PollOption(0, "Texto arbitrário A", UserIntent.AGENDAR),
                new WhatsAppPollService.PollOption(1, "Texto arbitrário B", UserIntent.FALAR_TATUADOR),
                new WhatsAppPollService.PollOption(2, "Texto arbitrário C", UserIntent.VOLTAR)
        );

        Optional<WhatsAppPoll> tracked = pollService.sendAndTrackPoll(
                "cefan-studio", customer, session, ConversationState.ORCAMENTO_AGUARDANDO_FOTO, "Escolha:", options);

        assertTrue(tracked.isPresent());
        verify(pollRepository).deactivateActivePollsForCustomer(customer.getId());

        WhatsAppPoll persisted = tracked.get();
        when(pollRepository.findByPollMessageIdForUpdate("POLL-MSG-001")).thenReturn(Optional.of(persisted));

        // Voto pelo índice 1 -> mapeia para FALAR_TATUADOR pelo índice, independente do rótulo
        Optional<WhatsAppPollService.ResolvedPollVote> resolved = pollService.resolveAndConsumeVote("POLL-MSG-001", 1);
        assertTrue(resolved.isPresent());
        assertEquals(UserIntent.FALAR_TATUADOR, resolved.get().intent());
        assertEquals(1, resolved.get().index());
        assertFalse(persisted.getActive());
        assertEquals(1, persisted.getVotedOptionIndex());

        // Reenvio do mesmo voto -> idempotente, ignorado!
        Optional<WhatsAppPollService.ResolvedPollVote> duplicate = pollService.resolveAndConsumeVote("POLL-MSG-001", 1);
        assertTrue(duplicate.isEmpty());
    }

    @Test
    void shouldIgnoreVoteFromOldPollOrMismatchedState() throws Exception {
        String optionsJson = objectMapper.writeValueAsString(List.of(
                new WhatsAppPollService.PollOption(0, "Agendar sessão", UserIntent.AGENDAR)
        ));

        // Enquete inativa (antiga)
        WhatsAppPoll inactivePoll = WhatsAppPoll.builder()
                .pollMessageId("POLL-OLD")
                .session(session)
                .customer(customer)
                .expectedState(ConversationState.ORCAMENTO_AGUARDANDO_FOTO)
                .optionsJson(optionsJson)
                .active(false)
                .build();
        when(pollRepository.findByPollMessageIdForUpdate("POLL-OLD")).thenReturn(Optional.of(inactivePoll));
        assertTrue(pollService.resolveAndConsumeVote("POLL-OLD", 0).isEmpty());

        // Enquete ativa mas cliente já mudou de estado para MENU
        session.setCurrentState(ConversationState.MENU);
        WhatsAppPoll staleStatePoll = WhatsAppPoll.builder()
                .pollMessageId("POLL-STALE")
                .session(session)
                .customer(customer)
                .expectedState(ConversationState.ORCAMENTO_AGUARDANDO_FOTO)
                .optionsJson(optionsJson)
                .active(true)
                .build();
        when(pollRepository.findByPollMessageIdForUpdate("POLL-STALE")).thenReturn(Optional.of(staleStatePoll));
        assertTrue(pollService.resolveAndConsumeVote("POLL-STALE", 0).isEmpty());
        assertFalse(staleStatePoll.getActive());
    }

    @Test
    void shouldParseEvolution237DecryptedMessagesUpdatePollPayloadAndIgnoreEmptyVote() throws Exception {
        String votedPayload = """
                {
                  "event": "messages.update",
                  "instance": "cefan-studio",
                  "data": {
                    "keyId": "3EB0POLL123",
                    "remoteJid": "5541999999999@s.whatsapp.net",
                    "fromMe": true,
                    "status": "SERVER_ACK",
                    "pollUpdates": [
                      { "name": "Agendar sessão", "voters": [] },
                      { "name": "Falar com o tatuador", "voters": ["5541999999999@s.whatsapp.net"] },
                      { "name": "Voltar ao menu", "voters": [] }
                    ]
                  }
                }
                """;
        EvolutionWebhookRequest votedReq = objectMapper.readValue(votedPayload, EvolutionWebhookRequest.class);
        assertTrue(votedReq.hasPollUpdates());
        assertEquals("3EB0POLL123", votedReq.extractPollUpdateKeyId());
        assertEquals("5541999999999", votedReq.extractSenderPhone());
        assertEquals(OptionalInt.of(1), votedReq.extractVotedPollOptionIndex());

        String emptyVotePayload = """
                {
                  "event": "messages.update",
                  "instance": "cefan-studio",
                  "data": {
                    "keyId": "3EB0POLL123",
                    "remoteJid": "5541999999999@s.whatsapp.net",
                    "fromMe": true,
                    "pollUpdates": [
                      { "name": "Agendar sessão", "voters": [] },
                      { "name": "Falar com o tatuador", "voters": [] },
                      { "name": "Voltar ao menu", "voters": [] }
                    ]
                  }
                }
                """;
        EvolutionWebhookRequest emptyReq = objectMapper.readValue(emptyVotePayload, EvolutionWebhookRequest.class);
        assertTrue(emptyReq.hasPollUpdates());
        assertEquals(OptionalInt.empty(), emptyReq.extractVotedPollOptionIndex());
    }
}
