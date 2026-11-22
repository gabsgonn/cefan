package br.com.cefan.whatsapp.domain.repository;

import br.com.cefan.whatsapp.domain.model.WhatsAppPoll;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WhatsAppPollRepository extends JpaRepository<WhatsAppPoll, UUID> {

    Optional<WhatsAppPoll> findByPollMessageId(String pollMessageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM WhatsAppPoll p WHERE p.pollMessageId = :pollMessageId")
    Optional<WhatsAppPoll> findByPollMessageIdForUpdate(@Param("pollMessageId") String pollMessageId);

    @Modifying
    @Query("UPDATE WhatsAppPoll p SET p.active = false WHERE p.customer.id = :customerId AND p.active = true")
    int deactivateActivePollsForCustomer(@Param("customerId") UUID customerId);
}
