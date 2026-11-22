package br.com.cefan.whatsapp.application.service;

import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.model.TenantConfig;
import br.com.cefan.whatsapp.domain.model.User;
import br.com.cefan.whatsapp.domain.repository.AppointmentRepository;
import br.com.cefan.whatsapp.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static br.com.cefan.whatsapp.application.statemachine.handler.AgendamentoStateHandler.STUDIO_ZONE;
import static br.com.cefan.whatsapp.application.statemachine.handler.AgendamentoStateHandler.isArtistActiveOnStudioWorkingDay;
import static br.com.cefan.whatsapp.application.statemachine.handler.AgendamentoStateHandler.resolveClosingTime;
import static br.com.cefan.whatsapp.application.statemachine.handler.AgendamentoStateHandler.resolveOpeningTime;

/**
 * Gerencia fronteiras curtas e isoladas de transação (REQUIRES_NEW) para reserva atômica de agenda.
 * Garante que o lock FOR UPDATE e a gravação da reserva sejam comitados ANTES de qualquer chamada
 * HTTP externa (ex.: Mercado Pago), e que o cancelamento compensatório também rode em REQUIRES_NEW.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentReservationService {

    private static final int MIN_SESSION_DURATION_HOURS = 2;

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;

    /**
     * Transação curta (REQUIRES_NEW):
     * 1. Adquire lock pessimista (FOR UPDATE) na linha do artista;
     * 2. Expira reservas pendentes vencidas do artista no dia;
     * 3. Valida disponibilidade dentro do lock;
     * 4. Persiste a reserva como AGUARDANDO_PAGAMENTO (saveAndFlush) e faz COMMIT liberando o lock.
     *
     * @return o Appointment comitado, ou Optional.empty() se o horário estiver indisponível/conflitante.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Appointment> reserveSlotAtomically(
            Customer customer,
            User artist,
            OffsetDateTime scheduledStart,
            OffsetDateTime scheduledEnd,
            BigDecimal totalPrice,
            BigDecimal depositAmount,
            OffsetDateTime expiresAt,
            TenantConfig tenantConfig,
            OffsetDateTime referenceNow) {

        if (customer == null || artist == null || scheduledStart == null || scheduledEnd == null
                || !scheduledEnd.isAfter(scheduledStart) || !scheduledStart.isAfter(referenceNow)) {
            return Optional.empty();
        }

        User lockedArtist = (artist.getId() != null)
                ? userRepository.findByIdForUpdate(artist.getId()).orElse(artist)
                : artist;

        LocalDate date = scheduledStart.atZoneSameInstant(STUDIO_ZONE).toLocalDate();
        if (!isArtistActiveOnStudioWorkingDay(lockedArtist, date.getDayOfWeek(), tenantConfig)) {
            return Optional.empty();
        }
        if (!scheduledEnd.atZoneSameInstant(STUDIO_ZONE).toLocalDate().equals(date)) {
            return Optional.empty();
        }

        LocalTime openTime = resolveOpeningTime(tenantConfig);
        LocalTime closeTime = resolveClosingTime(tenantConfig);
        LocalTime startTime = scheduledStart.atZoneSameInstant(STUDIO_ZONE).toLocalTime().withSecond(0).withNano(0);
        LocalTime endTime = scheduledEnd.atZoneSameInstant(STUDIO_ZONE).toLocalTime().withSecond(0).withNano(0);
        if (startTime.isBefore(openTime) || endTime.isAfter(closeTime)) {
            return Optional.empty();
        }

        OffsetDateTime queryStart = toStudioOffset(date.minusDays(1), LocalTime.MIN);
        OffsetDateTime queryEnd = toStudioOffset(date.plusDays(1), LocalTime.MIN);

        expireOverduePendingForArtist(lockedArtist.getId(), queryStart, queryEnd, referenceNow);

        List<Appointment> blocking = fetchBlockingAppointments(lockedArtist.getId(), queryStart, queryEnd, referenceNow);
        if (hasOverlap(blocking, scheduledStart, scheduledEnd)) {
            return Optional.empty();
        }

        Appointment appointment = Appointment.builder()
                .customer(customer)
                .artist(lockedArtist)
                .scheduledStart(scheduledStart)
                .scheduledEnd(scheduledEnd)
                .status(AppointmentStatus.AGUARDANDO_PAGAMENTO)
                .totalPrice(totalPrice)
                .depositAmount(depositAmount)
                .expiresAt(expiresAt)
                .build();

        try {
            Appointment saved = appointmentRepository.saveAndFlush(appointment);
            return Optional.of(saved != null ? saved : appointment);
        } catch (DataIntegrityViolationException e) {
            log.warn("Conflito de agenda barrado pela constraint do banco para artista [{}] em {}: {}",
                    lockedArtist.getName(), scheduledStart, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Transação curta (REQUIRES_NEW) executada APÓS o retorno bem-sucedido do Mercado Pago.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attachPixDetails(
            Appointment reserved,
            String paymentId,
            String pixCopyPaste,
            String pixQrCodeBase64,
            String ticketUrl) {
        if (reserved == null) {
            return;
        }
        Appointment target = (reserved.getId() != null)
                ? appointmentRepository.findById(reserved.getId()).orElse(reserved)
                : reserved;
        target.setMpPaymentId(paymentId);
        target.setMpPixCopyPaste(pixCopyPaste != null ? pixCopyPaste.trim() : null);
        target.setMpPixQrCodeBase64(pixQrCodeBase64);
        target.setMpPaymentLink(ticketUrl);
        appointmentRepository.saveAndFlush(target);
    }

    /**
     * Transação compensatória independente (REQUIRES_NEW): cancela imediatamente a reserva
     * temporária se a geração do PIX falhar, liberando o horário na agenda.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelTemporaryReservation(Appointment reserved) {
        if (reserved == null) {
            return;
        }
        Appointment target = (reserved.getId() != null)
                ? appointmentRepository.findById(reserved.getId()).orElse(reserved)
                : reserved;
        target.setStatus(AppointmentStatus.CANCELADO);
        appointmentRepository.saveAndFlush(target);
    }

    private void expireOverduePendingForArtist(UUID artistId, OffsetDateTime queryStart, OffsetDateTime queryEnd, OffsetDateTime referenceNow) {
        if (artistId == null) {
            return;
        }
        List<Appointment> existing = appointmentRepository.findByArtistIdAndScheduledStartBetween(artistId, queryStart, queryEnd);
        if (existing == null || existing.isEmpty()) {
            return;
        }
        boolean expiredAny = false;
        for (Appointment appt : existing) {
            if (appt.getStatus() == AppointmentStatus.AGUARDANDO_PAGAMENTO
                    && appt.getExpiresAt() != null
                    && !appt.getExpiresAt().isAfter(referenceNow)) {
                appt.setStatus(AppointmentStatus.EXPIRADO);
                appointmentRepository.save(appt);
                expiredAny = true;
            }
        }
        if (expiredAny) {
            appointmentRepository.flush();
        }
    }

    private List<Appointment> fetchBlockingAppointments(UUID artistId, OffsetDateTime queryStart, OffsetDateTime queryEnd, OffsetDateTime referenceNow) {
        if (artistId == null) {
            return List.of();
        }
        List<Appointment> raw = appointmentRepository.findByArtistIdAndScheduledStartBetween(artistId, queryStart, queryEnd);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELADO && a.getStatus() != AppointmentStatus.EXPIRADO)
                .filter(a -> !(a.getStatus() == AppointmentStatus.AGUARDANDO_PAGAMENTO
                        && a.getExpiresAt() != null
                        && !a.getExpiresAt().isAfter(referenceNow)))
                .toList();
    }

    private boolean hasOverlap(List<Appointment> blockingAppointments, OffsetDateTime slotStart, OffsetDateTime slotEnd) {
        for (Appointment appt : blockingAppointments) {
            if (appt.getScheduledStart() == null) {
                continue;
            }
            OffsetDateTime existingStart = appt.getScheduledStart();
            OffsetDateTime existingEnd = appt.getScheduledEnd() != null
                    ? appt.getScheduledEnd()
                    : existingStart.plusHours(MIN_SESSION_DURATION_HOURS);
            if (existingStart.isBefore(slotEnd) && existingEnd.isAfter(slotStart)) {
                return true;
            }
        }
        return false;
    }

    private OffsetDateTime toStudioOffset(LocalDate date, LocalTime time) {
        ZoneOffset offset = STUDIO_ZONE.getRules().getOffset(date.atTime(time));
        return date.atTime(time).atOffset(offset);
    }
}
