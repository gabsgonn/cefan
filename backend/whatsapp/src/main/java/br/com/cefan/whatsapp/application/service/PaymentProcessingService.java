package br.com.cefan.whatsapp.application.service;

import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import br.com.cefan.whatsapp.domain.repository.AppointmentRepository;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import br.com.cefan.whatsapp.infrastructure.mercadopago.MercadoPagoPaymentClient;
import br.com.cefan.whatsapp.infrastructure.mercadopago.dto.MercadoPagoPaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProcessingService {

    private final AppointmentRepository appointmentRepository;
    private final MercadoPagoPaymentClient paymentClient;
    private final EvolutionApiClient evolutionApiClient;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("pt-BR"));

    @Transactional
    public boolean handlePaymentNotification(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            log.warn("Notificação recebida com paymentId vazio.");
            return false;
        }

        log.info("Processando notificação de pagamento do Mercado Pago para ID=[{}]", paymentId);

        Appointment appointment = appointmentRepository.findByMpPaymentId(paymentId)
                .orElse(null);

        if (appointment == null) {
            log.warn("Nenhum agendamento encontrado no banco para o mpPaymentId=[{}]", paymentId);
            return false;
        }

        if (appointment.getStatus() == AppointmentStatus.CONFIRMADO) {
            log.info("Agendamento [{}] já estava confirmado.", appointment.getId());
            return true;
        }

        MercadoPagoPaymentResponse paymentResponse = paymentClient.getPayment(paymentId);
        if (paymentResponse != null && paymentResponse.isApproved()) {
            log.info("Pagamento [{}] APROVADO! Confirmando agendamento [{}]", paymentId, appointment.getId());
            appointment.setStatus(AppointmentStatus.CONFIRMADO);
            appointmentRepository.save(appointment);

            String customerPhone = appointment.getCustomer().getPhone();
            String customerName = appointment.getCustomer().getName();
            String dateFormatted = appointment.getScheduledStart().format(DATE_FORMATTER);
            String timeFormatted = appointment.getScheduledStart().format(TIME_FORMATTER);

            String confirmationMessage = String.format(
                    "🎉 *Pagamento do Sinal Confirmado!* 🎉\n\n" +
                    "Olá, %s! Seu pagamento de *R$ %s* foi confirmado com sucesso!\n\n" +
                    "🗓️ *Sua sessão está 100%% garantida!*\n" +
                    "📅 *Data:* %s às %sh\n" +
                    "📍 *Local:* Estúdio Vício Tattoo\n\n" +
                    "💡 *Dicas importantes:*\n" +
                    "• Venha bem alimentado(a) e hidratado(a);\n" +
                    "• Vista roupas confortáveis que facilitem o acesso ao local da arte;\n" +
                    "• Evite consumir álcool nas 24h anteriores.\n\n" +
                    "Nos vemos em breve! Qualquer dúvida estamos por aqui. 🎨✨",
                    customerName,
                    appointment.getDepositAmount(),
                    dateFormatted,
                    timeFormatted
            );

            evolutionApiClient.sendTextMessage(customerPhone, confirmationMessage);
            return true;
        } else {
            log.info("Pagamento [{}] ainda não está com status 'approved' (Status atual: [{}])",
                    paymentId, paymentResponse != null ? paymentResponse.getStatus() : "nulo");
            return false;
        }
    }
}
