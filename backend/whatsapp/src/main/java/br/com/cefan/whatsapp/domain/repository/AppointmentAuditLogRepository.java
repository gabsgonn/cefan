package br.com.cefan.whatsapp.domain.repository;

import br.com.cefan.whatsapp.domain.model.AppointmentAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AppointmentAuditLogRepository extends JpaRepository<AppointmentAuditLog, UUID> {
    List<AppointmentAuditLog> findByAppointmentIdOrderByChangedAtDesc(UUID appointmentId);
}
