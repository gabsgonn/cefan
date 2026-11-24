package br.com.cefan.whatsapp.domain.repository;

import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findByStatusAndExpiresAtBefore(AppointmentStatus status, OffsetDateTime dateTime);

    List<Appointment> findByCustomerId(UUID customerId);

    List<Appointment> findByArtistIdAndScheduledStartBetween(UUID artistId, OffsetDateTime start, OffsetDateTime end);

    List<Appointment> findByScheduledStartBetween(OffsetDateTime start, OffsetDateTime end);

    Optional<Appointment> findByMpPaymentId(String mpPaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Appointment a WHERE a.mpPaymentId = :mpPaymentId")
    Optional<Appointment> findByMpPaymentIdForUpdate(@Param("mpPaymentId") String mpPaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Appointment a WHERE (a.paymentProvider = :provider AND a.paymentReference = :reference) OR (:provider = 'mercadopago' AND a.mpPaymentId = :reference)")
    Optional<Appointment> findByPaymentProviderAndReferenceForUpdate(
            @Param("provider") String provider,
            @Param("reference") String reference);

    List<Appointment> findByStatusAndScheduledStartBetweenAndReminder24hSentFalse(
            AppointmentStatus status, OffsetDateTime start, OffsetDateTime end);
}
