package br.com.cefan.whatsapp.domain.repository;

import br.com.cefan.whatsapp.domain.model.PaymentInboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentInboxEventRepository extends JpaRepository<PaymentInboxEvent, UUID> {

    Optional<PaymentInboxEvent> findByProviderAndTransactionNsu(String provider, String transactionNsu);

    List<PaymentInboxEvent> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            PaymentInboxEvent.Status status,
            OffsetDateTime now);
}
