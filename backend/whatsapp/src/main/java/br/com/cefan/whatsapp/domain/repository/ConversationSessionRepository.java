package br.com.cefan.whatsapp.domain.repository;

import br.com.cefan.whatsapp.domain.model.ConversationSession;
import br.com.cefan.whatsapp.domain.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationSessionRepository extends JpaRepository<ConversationSession, UUID> {
    Optional<ConversationSession> findByCustomer(Customer customer);
    Optional<ConversationSession> findByCustomerId(UUID customerId);
}
