package br.com.cefan.whatsapp.infrastructure.mercadopago;

public class MercadoPagoPaymentException extends RuntimeException {

    public MercadoPagoPaymentException(String message) {
        super(message);
    }

    public MercadoPagoPaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
