package br.com.cefan.whatsapp.application.service;

import br.com.cefan.whatsapp.api.dto.AppointmentResponse;
import br.com.cefan.whatsapp.api.dto.CreateAppointmentRequest;
import br.com.cefan.whatsapp.api.dto.UpdateAppointmentRequest;
import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentAuditLog;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.repository.AppointmentAuditLogRepository;
import br.com.cefan.whatsapp.domain.repository.AppointmentRepository;
import br.com.cefan.whatsapp.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final AppointmentAuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public List<AppointmentResponse> listAppointments(OffsetDateTime start, OffsetDateTime end, AppointmentStatus status) {
        List<Appointment> all = appointmentRepository.findAll();

        return all.stream()
                .filter(a -> {
                    if (start != null && a.getScheduledStart().isBefore(start)) {
                        return false;
                    }
                    if (end != null && a.getScheduledStart().isAfter(end)) {
                        return false;
                    }
                    if (status != null && a.getStatus() != status) {
                        return false;
                    }
                    return true;
                })
                .sorted(Comparator.comparing(Appointment::getScheduledStart))
                .map(AppointmentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getById(UUID id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agendamento não encontrado para ID: " + id));
        return AppointmentResponse.fromEntity(appointment);
    }

    @Transactional
    public AppointmentResponse create(CreateAppointmentRequest request) {
        log.info("Criando agendamento manual para cliente [{}] ({})", request.getCustomerName(), request.getCustomerPhone());

        Customer customer = customerRepository.findByPhone(request.getCustomerPhone())
                .orElseGet(() -> customerRepository.save(Customer.builder()
                        .name(request.getCustomerName())
                        .phone(request.getCustomerPhone())
                        .build()));

        boolean isTouchup = Boolean.TRUE.equals(request.getIsTouchup());
        BigDecimal totalPrice = isTouchup ? BigDecimal.ZERO : (request.getTotalPrice() != null ? request.getTotalPrice() : BigDecimal.ZERO);
        BigDecimal deposit = isTouchup ? BigDecimal.ZERO : (request.getDepositAmount() != null ? request.getDepositAmount() : BigDecimal.ZERO);

        Appointment appointment = Appointment.builder()
                .customer(customer)
                .scheduledStart(request.getScheduledStart())
                .scheduledEnd(request.getScheduledEnd())
                .status(AppointmentStatus.CONFIRMADO)
                .isTouchup(isTouchup)
                .totalPrice(totalPrice)
                .depositAmount(deposit)
                .build();

        appointment = appointmentRepository.save(appointment);
        return AppointmentResponse.fromEntity(appointment);
    }

    @Transactional
    public AppointmentResponse update(UUID id, UpdateAppointmentRequest request) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agendamento não encontrado para ID: " + id));

        // Regra de Auditoria de Valores (Caso de Borda C do Plano)
        if (request.getNewPrice() != null && request.getNewPrice().compareTo(appointment.getTotalPrice()) != 0) {
            String reason = request.getReasonForChange();
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("A alteração de valor de um agendamento exige uma justificativa para auditoria.");
            }

            log.info("Auditando alteração de valor no agendamento [{}]: de R$ {} para R$ {}. Motivo: {}",
                    id, appointment.getTotalPrice(), request.getNewPrice(), reason);

            AppointmentAuditLog auditLog = AppointmentAuditLog.builder()
                    .appointment(appointment)
                    .oldValue(appointment.getTotalPrice())
                    .newValue(request.getNewPrice())
                    .reason(reason.trim())
                    .build();

            auditLogRepository.save(auditLog);
            appointment.setTotalPrice(request.getNewPrice());
        }

        // Regra de Retoque (Caso de Borda A do Plano)
        if (request.getIsTouchup() != null) {
            appointment.setIsTouchup(request.getIsTouchup());
            if (Boolean.TRUE.equals(request.getIsTouchup())) {
                appointment.setTotalPrice(BigDecimal.ZERO);
            }
        }

        if (request.getScheduledStart() != null) {
            appointment.setScheduledStart(request.getScheduledStart());
        }
        if (request.getScheduledEnd() != null) {
            appointment.setScheduledEnd(request.getScheduledEnd());
        }
        if (request.getStatus() != null) {
            appointment.setStatus(request.getStatus());
        }

        appointment = appointmentRepository.save(appointment);
        return AppointmentResponse.fromEntity(appointment);
    }
}
