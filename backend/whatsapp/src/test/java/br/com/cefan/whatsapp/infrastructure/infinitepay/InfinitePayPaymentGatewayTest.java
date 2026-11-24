package br.com.cefan.whatsapp.infrastructure.infinitepay;

import br.com.cefan.whatsapp.application.payment.*;
import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import br.com.cefan.whatsapp.domain.model.TenantConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class InfinitePayPaymentGatewayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateCheckoutLinkWithValidUntilWithoutNanosAndSinglePayment() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.checkout.infinitepay.io");
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();

        UUID appointmentId = UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.of(2026, 11, 24, 16, 0, 0, 123456789, ZoneOffset.ofHours(-3));

        mockServer.expect(requestTo("https://api.checkout.infinitepay.io/links"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "inchk_test_secret"))
                .andExpect(content().string(allOf(
                        containsString("\"handle\":\"viciotattoo\""),
                        containsString("\"order_nsu\":\"" + appointmentId + "\""),
                        containsString("\"description\":\"Sinal de reserva\""),
                        containsString("\"price\":6375"),
                        containsString("\"valid_until\":\"2026-11-24T16:00:00-03:00\""),
                        containsString("\"single_payment\":true"),
                        not(containsString("123456789"))
                )))
                .andRespond(withSuccess("{\"url\":\"https://checkout.infinitepay.io/viciotattoo/abc123\"}", MediaType.APPLICATION_JSON));

        InfinitePayPaymentGateway gateway = new InfinitePayPaymentGateway(
                objectMapper, "https://api.checkout.infinitepay.io", "inchk_test_secret");
        ReflectionTestUtils.setField(gateway, "restClient", builder.build());

        TenantConfig tenant = TenantConfig.builder()
                .studioName("Vício Tattoo")
                .paymentProvider("infinitepay")
                .infinitepayHandle("viciotattoo")
                .build();

        PaymentCharge charge = gateway.createCharge(tenant, new CreateChargeRequest(
                appointmentId, 6375L, "Sinal de reserva", "Gabriel", "5541999074863", null,
                expiresAt, "https://cefan.gabrielgonc.com/api/v1/webhooks/infinitepay/" + appointmentId + "/abc",
                "https://cefan.gabrielgonc.com/api/v1/payments/infinitepay/return"
        ));

        mockServer.verify();
        assertEquals("infinitepay", charge.provider());
        assertEquals(appointmentId.toString(), charge.reference());
        assertEquals("https://checkout.infinitepay.io/viciotattoo/abc123", charge.paymentUrl());
    }

    @Test
    void shouldNotConfirmOnFakeWebhookWhenPaymentCheckReturnsPaidFalse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.checkout.infinitepay.io");
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();

        UUID appointmentId = UUID.randomUUID();
        mockServer.expect(requestTo("https://api.checkout.infinitepay.io/payment_check"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "inchk_test_secret"))
                .andRespond(withSuccess("{\"success\":true,\"paid\":false}", MediaType.APPLICATION_JSON));

        InfinitePayPaymentGateway gateway = new InfinitePayPaymentGateway(
                objectMapper, "https://api.checkout.infinitepay.io", "inchk_test_secret");
        ReflectionTestUtils.setField(gateway, "restClient", builder.build());

        TenantConfig tenant = TenantConfig.builder().infinitepayHandle("viciotattoo").build();
        PaymentVerificationResult verification = gateway.verify(
                tenant, new PaymentEvidence("infinitepay", null, appointmentId, "fake-tx-999", "slug-1"));

        mockServer.verify();
        assertEquals(PaymentVerificationStatus.NOT_PAID, verification.status());

        Appointment appt = Appointment.builder()
                .id(appointmentId)
                .status(AppointmentStatus.AGUARDANDO_PAGAMENTO)
                .depositAmount(new BigDecimal("63.75"))
                .expiresAt(OffsetDateTime.now(ZoneOffset.ofHours(-3)).plusHours(2))
                .build();
        PaymentConfirmationService confirmationService = new PaymentConfirmationService();
        assertEquals(PaymentConfirmationService.Decision.NOT_PAID,
                confirmationService.evaluate(appt, verification, OffsetDateTime.now(ZoneOffset.ofHours(-3))));
    }

    @Test
    void shouldReturnUnverifiableOn503ForRetryWithBackoff() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.checkout.infinitepay.io");
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();

        mockServer.expect(requestTo("https://api.checkout.infinitepay.io/payment_check"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        InfinitePayPaymentGateway gateway = new InfinitePayPaymentGateway(
                objectMapper, "https://api.checkout.infinitepay.io", "inchk_test_secret");
        ReflectionTestUtils.setField(gateway, "restClient", builder.build());

        TenantConfig tenant = TenantConfig.builder().infinitepayHandle("viciotattoo").build();
        PaymentVerificationResult verification = gateway.verify(
                tenant, new PaymentEvidence("infinitepay", null, UUID.randomUUID(), "tx-503", "slug-503"));

        mockServer.verify();
        assertEquals(PaymentVerificationStatus.UNVERIFIABLE, verification.status());
    }
}
