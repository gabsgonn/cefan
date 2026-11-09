package br.com.cefan.whatsapp.domain.repository;

import br.com.cefan.whatsapp.domain.model.ConversationSession;
import br.com.cefan.whatsapp.domain.model.ConversationState;
import br.com.cefan.whatsapp.domain.model.Customer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationSessionRepository extends JpaRepository<ConversationSession, UUID> {
    Optional<ConversationSession> findByCustomer(Customer customer);
    Optional<ConversationSession> findByCustomerId(UUID customerId);

    @EntityGraph(attributePaths = {"customer"})
    List<ConversationSession> findByCurrentStateOrderByLastInteractionAtDesc(ConversationState currentState);

    @EntityGraph(attributePaths = {"customer"})
    List<ConversationSession> findAllByOrderByLastInteractionAtDesc();
}
