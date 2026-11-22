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

    @Autowired
    public WhatsAppPollService(
            EvolutionApiClient evolutionApiClient,
            WhatsAppPollRepository pollRepository,
            ObjectMapper objectMapper) {
        this.evolutionApiClient = evolutionApiClient;
        this.pollRepository = pollRepository;
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
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
            return Optional.empty();
        }

        List<String> values = options.stream().map(PollOption::label).toList();
        Optional<String> pollIdOpt = evolutionApiClient.sendPollMessage(
                instance, customer.getPhone(), title, values, 1);

        if (pollIdOpt.isEmpty() || pollIdOpt.get().isBlank()) {
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
            return Optional.of(saved);
        } catch (Exception e) {
            log.warn("Erro ao persistir enquete [{}] para cliente [{}]: {}", pollMessageId, customer.getPhone(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Resolve e consome um voto de enquete de forma idempotente e por ÍNDICE (não pelo texto).
     * Ignora enquetes desconhecidas, antigas (inativas ou de outro estado), votos fora de faixa
     * e reenvios (quando votedOptionIndex já foi preenchido).
     */
    @Transactional
    public Optional<ResolvedPollVote> resolveAndConsumeVote(String pollMessageId, int votedIndex) {
        if (pollMessageId == null || pollMessageId.isBlank() || votedIndex < 0) {
            return Optional.empty();
        }

        WhatsAppPoll poll = pollRepository.findByPollMessageIdForUpdate(pollMessageId)
                .or(() -> pollRepository.findByPollMessageId(pollMessageId))
                .orElse(null);

        if (poll == null) {
            log.debug("Enquete [{}] não encontrada na base. Ignorando voto.", pollMessageId);
            return Optional.empty();
        }

        if (poll.getVotedOptionIndex() != null) {
            log.info("Voto na enquete [{}] já foi processado anteriormente (índice {}). Ignorando reenvio.",
                    pollMessageId, poll.getVotedOptionIndex());
            return Optional.empty();
        }

        if (!Boolean.TRUE.equals(poll.getActive())) {
            log.info("Enquete [{}] está inativa (enquete antiga). Ignorando voto.", pollMessageId);
            return Optional.empty();
        }

        ConversationSession session = poll.getSession();
        if (session == null || session.getCurrentState() != poll.getExpectedState()) {
            log.info("Enquete [{}] esperava estado [{}], mas sessão está em [{}]. Desativando e ignorando voto.",
                    pollMessageId, poll.getExpectedState(), session != null ? session.getCurrentState() : "null");
            poll.setActive(false);
            pollRepository.save(poll);
            return Optional.empty();
        }

        List<PollOption> options = parseOptions(poll.getOptionsJson());
        PollOption matched = options.stream()
                .filter(opt -> opt.index() == votedIndex)
                .findFirst()
                .orElse(null);

        if (matched == null || matched.intent() == null) {
            log.warn("Índice de voto [{}] não existe nas opções da enquete [{}]. Ignorando.", votedIndex, pollMessageId);
            return Optional.empty();
        }

        poll.setVotedOptionIndex(votedIndex);
        poll.setVotedAt(OffsetDateTime.now(AgendamentoStateHandler.STUDIO_ZONE));
        poll.setActive(false);
        pollRepository.save(poll);

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
