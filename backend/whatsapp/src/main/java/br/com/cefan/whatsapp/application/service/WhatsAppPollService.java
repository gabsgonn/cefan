package br.com.cefan.whatsapp.application.service;

import br.com.cefan.whatsapp.application.statemachine.UserIntent;
import br.com.cefan.whatsapp.application.statemachine.handler.AgendamentoStateHandler;
import br.com.cefan.whatsapp.domain.model.ConversationSession;
import br.com.cefan.whatsapp.domain.model.ConversationState;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.model.WhatsAppPoll;
import br.com.cefan.whatsapp.domain.repository.WhatsAppPollRepository;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class WhatsAppPollService {

    public record PollOption(int index, String label, UserIntent intent) {}
    public record ResolvedPollVote(WhatsAppPoll poll, UserIntent intent, String label, int index) {}

    private final EvolutionApiClient evolutionApiClient;
    private final WhatsAppPollRepository pollRepository;
    private final ObjectMapper objectMapper;
    private final ChatService chatService;

    public WhatsAppPollService(
            EvolutionApiClient evolutionApiClient,
            WhatsAppPollRepository pollRepository,
            ObjectMapper objectMapper) {
        this(evolutionApiClient, pollRepository, objectMapper, null);
    }

    @Autowired
    public WhatsAppPollService(
            EvolutionApiClient evolutionApiClient,
            WhatsAppPollRepository pollRepository,
            ObjectMapper objectMapper,
            ChatService chatService) {
        this.evolutionApiClient = evolutionApiClient;
        this.pollRepository = pollRepository;
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
        this.chatService = chatService;
    }

    @Transactional
    public Optional<WhatsAppPoll> sendAndTrackPoll(
            String instance,
            Customer customer,
            ConversationSession session,
            ConversationState expectedState,
            String title,
            List<PollOption> options) {

        if (customer == null || session == null || session.getId() == null || options == null || options.isEmpty()) {
            log.error("Falha ao enviar/gravar enquete: dados obrigatórios ausentes (customer={}, session={}, optionsSize={}).",
                    customer != null ? customer.getPhone() : "null",
                    session != null ? session.getId() : "null",
                    options != null ? options.size() : 0);
            return Optional.empty();
        }

        List<String> values = options.stream().map(PollOption::label).toList();
        Optional<String> pollIdOpt = evolutionApiClient.sendPollMessage(
                instance, customer.getPhone(), title, values, 1);

        if (pollIdOpt.isEmpty() || pollIdOpt.get().isBlank()) {
            log.error("Falha ao obter ID da enquete enviada para cliente [{}] no estado [{}] (mantendo apenas o texto numerado).",
                    customer.getPhone(), expectedState);
            return Optional.empty();
        }

        String pollMessageId = pollIdOpt.get().trim();
        try {
            pollRepository.deactivateActivePollsForCustomer(customer.getId());
            String optionsJson = objectMapper.writeValueAsString(options);
            WhatsAppPoll saved = pollRepository.save(WhatsAppPoll.builder()
                    .pollMessageId(pollMessageId)
                    .session(session)
                    .customer(customer)
                    .expectedState(expectedState)
                    .optionsJson(optionsJson)
                    .active(true)
                    .createdAt(OffsetDateTime.now(AgendamentoStateHandler.STUDIO_ZONE))
                    .build());
            if (chatService != null) {
                chatService.recordBotPollMessage(session, customer, title, values);
            }
            return Optional.of(saved);
        } catch (Exception e) {
            log.error("Erro ao persistir enquete [{}] após envio para cliente [{}]: {}",
                    pollMessageId, customer.getPhone(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Resolve e consome um voto de enquete de forma idempotente e por ÍNDICE (não pelo texto).
     * Identifica o cliente exclusivamente pela sessão/cliente salvos na enquete (e nunca pelo JID/LID do votante).
     * Emite log estruturado POLL_UPDATE em todos os caminhos (PROCESSED ou IGNORED).
     */
    @Transactional
    public Optional<ResolvedPollVote> resolveAndConsumeVote(String pollMessageId, int votedIndex) {
        if (pollMessageId == null || pollMessageId.isBlank()) {
            log.info("POLL_UPDATE [action=IGNORED, reason=MISSING_POLL_ID, pollId={}, sessionState=N/A, expectedState=N/A]",
                    pollMessageId);
            return Optional.empty();
        }
        if (votedIndex < 0) {
            log.info("POLL_UPDATE [action=IGNORED, reason=INVALID_VOTE_INDEX, pollId={}, sessionState=N/A, expectedState=N/A]",
                    pollMessageId);
            return Optional.empty();
        }

        WhatsAppPoll poll = pollRepository.findByPollMessageIdForUpdate(pollMessageId)
                .or(() -> pollRepository.findByPollMessageId(pollMessageId))
                .orElse(null);

        if (poll == null) {
            log.info("POLL_UPDATE [action=IGNORED, reason=POLL_NOT_FOUND, pollId={}, sessionState=N/A, expectedState=N/A]",
                    pollMessageId);
            return Optional.empty();
        }

        ConversationSession session = poll.getSession();
        String sessionStateStr = (session != null && session.getCurrentState() != null)
                ? session.getCurrentState().name()
                : "NULL";
        String expectedStateStr = (poll.getExpectedState() != null)
                ? poll.getExpectedState().name()
                : "NULL";

        if (poll.getVotedOptionIndex() != null) {
            log.info("POLL_UPDATE [action=IGNORED, reason=ALREADY_VOTED, pollId={}, sessionState={}, expectedState={}]",
                    pollMessageId, sessionStateStr, expectedStateStr);
            return Optional.empty();
        }

        if (!Boolean.TRUE.equals(poll.getActive())) {
            log.info("POLL_UPDATE [action=IGNORED, reason=INACTIVE_POLL, pollId={}, sessionState={}, expectedState={}]",
                    pollMessageId, sessionStateStr, expectedStateStr);
            return Optional.empty();
        }

        if (session == null || session.getCurrentState() != poll.getExpectedState()) {
            poll.setActive(false);
            pollRepository.save(poll);
            log.info("POLL_UPDATE [action=IGNORED, reason=STATE_MISMATCH, pollId={}, sessionState={}, expectedState={}]",
                    pollMessageId, sessionStateStr, expectedStateStr);
            return Optional.empty();
        }

        List<PollOption> options = parseOptions(poll.getOptionsJson());
        PollOption matched = options.stream()
                .filter(opt -> opt.index() == votedIndex)
                .findFirst()
                .orElse(null);

        if (matched == null || matched.intent() == null) {
            log.warn("POLL_UPDATE [action=IGNORED, reason=OPTION_OUT_OF_RANGE, pollId={}, sessionState={}, expectedState={}]",
                    pollMessageId, sessionStateStr, expectedStateStr);
            return Optional.empty();
        }

        poll.setVotedOptionIndex(votedIndex);
        poll.setVotedAt(OffsetDateTime.now(AgendamentoStateHandler.STUDIO_ZONE));
        poll.setActive(false);
        pollRepository.save(poll);

        log.info("POLL_UPDATE [action=PROCESSED, reason=OK, pollId={}, sessionState={}, expectedState={}]",
                pollMessageId, sessionStateStr, expectedStateStr);

        return Optional.of(new ResolvedPollVote(poll, matched.intent(), matched.label(), votedIndex));
    }

    @Transactional
    public void deactivateActivePolls(Customer customer) {
        if (customer == null || customer.getId() == null) {
            return;
        }
        pollRepository.deactivateActivePollsForCustomer(customer.getId());
    }

    private List<PollOption> parseOptions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Erro ao desserializar options_json da enquete: {}", e.getMessage());
            return List.of();
        }
    }
}
