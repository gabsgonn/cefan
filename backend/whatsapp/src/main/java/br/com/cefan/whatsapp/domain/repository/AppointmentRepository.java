package br.com.cefan.whatsapp.domain.repository;

import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findByStatusAndExpiresAtBefore(AppointmentStatus status, OffsetDateTime dateTime);

    List<Appointment> findByCustomerId(UUID customerId);

    List<Appointment> findByArtistIdAndScheduledStartBetween(UUID artistId, OffsetDateTime start, OffsetDateTime end);
}
