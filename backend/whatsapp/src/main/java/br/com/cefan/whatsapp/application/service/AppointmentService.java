package br.com.cefan.whatsapp.application.service;

import br.com.cefan.whatsapp.api.dto.AppointmentResponse;
import br.com.cefan.whatsapp.api.dto.CreateAppointmentRequest;
import br.com.cefan.whatsapp.api.dto.UpdateAppointmentRequest;
import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentAuditLog;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.model.User;
import br.com.cefan.whatsapp.domain.model.UserRole;
import br.com.cefan.whatsapp.domain.repository.AppointmentAuditLogRepository;
import br.com.cefan.whatsapp.domain.repository.AppointmentRepository;
import br.com.cefan.whatsapp.domain.repository.ConversationSessionRepository;
import br.com.cefan.whatsapp.domain.repository.CustomerRepository;
import br.com.cefan.whatsapp.domain.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final AppointmentAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ConversationSessionRepository sessionRepository;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            CustomerRepository customerRepository,
            AppointmentAuditLogRepository auditLogRepository) {
        this(appointmentRepository, customerRepository, auditLogRepository, null, null);
    }

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            CustomerRepository customerRepository,
            AppointmentAuditLogRepository auditLogRepository,
            UserRepository userRepository) {
        this(appointmentRepository, customerRepository, auditLogRepository, userRepository, null);
    }

    @Autowired
    public AppointmentService(
            AppointmentRepository appointmentRepository,
            CustomerRepository customerRepository,
            AppointmentAuditLogRepository auditLogRepository,
            UserRepository userRepository,
            ConversationSessionRepository sessionRepository) {
        this.appointmentRepository = appointmentRepository;
        this.customerRepository = customerRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> listAppointments(OffsetDateTime start, OffsetDateTime end, AppointmentStatus status) {
        return listAppointments(start, end, status, null);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> listAppointments(OffsetDateTime start, OffsetDateTime end, AppointmentStatus status, UUID artistId) {
        List<Appointment> all = appointmentRepository.findAll();

        User currentUser = getCurrentAuthenticatedUser();
        UUID effectiveArtistFilter = artistId;
        if (currentUser != null && currentUser.getRole() == UserRole.ARTIST) {
            effectiveArtistFilter = currentUser.getId();
        }
        final UUID targetArtistId = effectiveArtistFilter;

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
                    if (targetArtistId != null) {
                        return a.getArtist() != null && targetArtistId.equals(a.getArtist().getId());
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

        User artist = null;
        if (userRepository != null) {
            if (request.getArtistId() != null) {
                artist = userRepository.findById(request.getArtistId()).orElse(null);
            } else {
                User currentUser = getCurrentAuthenticatedUser();
                if (currentUser != null && currentUser.getRole() == UserRole.ARTIST) {
                    artist = currentUser;
                }
            }
        }

        if (request.getScheduledStart() != null && request.getScheduledEnd() != null
                && !request.getScheduledEnd().isAfter(request.getScheduledStart())) {
            throw new IllegalArgumentException("O horário de término deve ser posterior ao horário de início.");
        }

        Appointment appointment = Appointment.builder()
                .customer(customer)
                .artist(artist)
                .scheduledStart(request.getScheduledStart())
                .scheduledEnd(request.getScheduledEnd())
                .status(AppointmentStatus.CONFIRMADO)
                .isTouchup(isTouchup)
                .totalPrice(totalPrice)
                .depositAmount(deposit)
                .build();

        try {
            appointment = appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            log.warn("Conflito ao criar agendamento manual no painel: {}", e.getMessage());
            throw new IllegalStateException("Já existe um agendamento ativo para este profissional neste horário. Escolha outro horário ou profissional.", e);
        }
        syncCustomerSessionArtist(customer, artist);
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
                appointment.setDepositAmount(BigDecimal.ZERO);
            }
        }

        if (request.getArtistId() != null && userRepository != null) {
            userRepository.findById(request.getArtistId()).ifPresent(appointment::setArtist);
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

        if (appointment.getScheduledStart() != null && appointment.getScheduledEnd() != null
                && !appointment.getScheduledEnd().isAfter(appointment.getScheduledStart())) {
            throw new IllegalArgumentException("O horário de término deve ser posterior ao horário de início.");
        }

        try {
            appointment = appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            log.warn("Conflito ao atualizar agendamento [{}] no painel: {}", id, e.getMessage());
            throw new IllegalStateException("Já existe um agendamento ativo para este profissional neste horário. Escolha outro horário ou profissional.", e);
        }
        syncCustomerSessionArtist(appointment.getCustomer(), appointment.getArtist());
        return AppointmentResponse.fromEntity(appointment);
    }

    private void syncCustomerSessionArtist(Customer customer, User artist) {
        if (sessionRepository == null || customer == null || artist == null) {
            return;
        }
        sessionRepository.findByCustomer(customer).ifPresent(session -> {
            session.setAssignedUser(artist);
            session.setAssignedArtistName(artist.getName());
            sessionRepository.save(session);
        });
    }

    private User getCurrentAuthenticatedUser() {
        if (userRepository == null) return null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            return userRepository.findByEmail(auth.getName()).orElse(null);
        }
        return null;
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse.AuditLogDto> getAuditLogs(UUID appointmentId) {
        return auditLogRepository.findByAppointmentIdOrderByChangedAtDesc(appointmentId)
                .stream()
                .map(logItem -> AppointmentResponse.AuditLogDto.builder()
                        .id(logItem.getId())
                        .appointmentId(appointmentId)
                        .oldValue(logItem.getOldValue())
                        .newValue(logItem.getNewValue())
                        .reason(logItem.getReason())
                        .changedAt(logItem.getChangedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
