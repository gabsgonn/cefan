package br.com.cefan.whatsapp.infrastructure.mercadopago;

import br.com.cefan.whatsapp.infrastructure.mercadopago.dto.MercadoPagoPaymentResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MercadoPagoPaymentClientTest {

    @Test
    void shouldReturnSimulatedPixWhenAccessTokenIsBlank() {
        MercadoPagoPaymentClient client = new MercadoPagoPaymentClient("", "https://api.mercadopago.com/v1", "");

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
    void shouldReturnApprovedStatusForSimulatedPaymentInGetPayment() {
        MercadoPagoPaymentClient client = new MercadoPagoPaymentClient("", "https://api.mercadopago.com/v1", "");

        MercadoPagoPaymentResponse response = client.getPayment("sim_12345");

        assertNotNull(response);
        assertTrue(response.isApproved());
        assertEquals("approved", response.getStatus());
    }
}
