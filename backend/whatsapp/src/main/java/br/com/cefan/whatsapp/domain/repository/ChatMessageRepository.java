package br.com.cefan.whatsapp.domain.repository;

import br.com.cefan.whatsapp.domain.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    List<ChatMessage> findByCustomerIdOrderByCreatedAtAsc(UUID customerId);
}
