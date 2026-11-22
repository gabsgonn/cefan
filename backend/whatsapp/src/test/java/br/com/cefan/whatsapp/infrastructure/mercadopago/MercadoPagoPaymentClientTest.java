package br.com.cefan.whatsapp.infrastructure.mercadopago;

import br.com.cefan.whatsapp.infrastructure.mercadopago.dto.MercadoPagoPaymentResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MercadoPagoPaymentClientTest {

    @Test
    void shouldThrowWhenAccessTokenIsBlankAndSandboxDisabled() {
        MercadoPagoPaymentClient client = new MercadoPagoPaymentClient("", "https://api.mercadopago.com/v1", "");

        assertFalse(client.isSandboxEnabled());
        assertThrows(MercadoPagoPaymentException.class, () -> client.createPixPayment(
                new BigDecimal("100.00"),
                "Sinal Agendamento",
                "cliente@email.com",
                "Gabriel",
                OffsetDateTime.now().plusHours(2)
        ));
    }

    @Test
    void shouldThrowWithoutSilentFallbackWhenApiCallFailsInProduction() {
        // Porta inválida local para garantir falha de comunicação sem depender de internet
        MercadoPagoPaymentClient client = new MercadoPagoPaymentClient("APP_USR-fake-token", "http://127.0.0.1:1", "", false);

        assertThrows(MercadoPagoPaymentException.class, () -> client.createPixPayment(
                new BigDecimal("100.00"),
                "Sinal Agendamento",
                "cliente@email.com",
                "Gabriel",
                OffsetDateTime.now().plusHours(2)
        ));
    }

    @Test
    void shouldNotReturnSimulatedApprovalInGetPaymentWhenSandboxDisabled() {
        MercadoPagoPaymentClient client = new MercadoPagoPaymentClient("", "https://api.mercadopago.com/v1", "", false);

        assertNull(client.getPayment("sim_12345"));
    }

    @Test
    void shouldReturnSimulatedPixWhenSandboxFlagIsExplicitlyEnabled() {
        MercadoPagoPaymentClient client = new MercadoPagoPaymentClient("", "https://api.mercadopago.com/v1", "", true);

        assertTrue(client.isSandboxEnabled());
        MercadoPagoPaymentResponse response = client.createPixPayment(
                new BigDecimal("100.00"),
                "Sinal Agendamento",
                "cliente@email.com",
                "Gabriel",
                OffsetDateTime.now().plusHours(2)
        );

        assertNotNull(response);
        assertNotNull(response.getId());
        assertEquals("pending", response.getStatus());
        assertNotNull(response.extractPixCopyPaste());
        assertTrue(response.extractPixCopyPaste().contains("sim_cefan"));
        assertNotNull(response.extractPixQrCodeBase64());
    }

    @Test
    void shouldReturnApprovedStatusForSimulatedPaymentWhenSandboxEnabled() {
        MercadoPagoPaymentClient client = new MercadoPagoPaymentClient("", "https://api.mercadopago.com/v1", "", true);

        MercadoPagoPaymentResponse response = client.getPayment("sim_12345");

        assertNotNull(response);
        assertTrue(response.isApproved());
        assertEquals("approved", response.getStatus());
    }

    @Test
    void shouldReturnSimulatedApprovalInGetPaymentInSandboxEvenWhenAccessTokenIsSetAndIdIsNumeric() {
        MercadoPagoPaymentClient client = new MercadoPagoPaymentClient("APP_USR-fake-token", "http://127.0.0.1:1", "", true);

        MercadoPagoPaymentResponse created = client.createPixPayment(
                new BigDecimal("100.00"),
                "Sinal Agendamento",
                "cliente@email.com",
                "Gabriel",
                OffsetDateTime.now().plusHours(2)
        );

        MercadoPagoPaymentResponse fetched = client.getPayment(String.valueOf(created.getId()));
        assertNotNull(fetched);
        assertTrue(fetched.isApproved());
        assertEquals(created.getId(), fetched.getId());
    }

    @Test
    void shouldRejectNullOrNonPositiveAmount() {
        MercadoPagoPaymentClient client = new MercadoPagoPaymentClient("", "https://api.mercadopago.com/v1", "", true);

        assertThrows(MercadoPagoPaymentException.class, () -> client.createPixPayment(
                BigDecimal.ZERO, "Sinal", "a@b.com", "Teste", OffsetDateTime.now().plusHours(2)));
        assertThrows(MercadoPagoPaymentException.class, () -> client.createPixPayment(
                null, "Sinal", "a@b.com", "Teste", OffsetDateTime.now().plusHours(2)));
    }
}
