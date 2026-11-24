package br.com.cefan.whatsapp.application.whatsapp;

/**
 * Porta separada de administração de conexão do provedor WhatsApp
 * (usada por /bot/health-status e /bot/reconnect no StudioAdminController).
 */
public interface ConnectionAdminPort {

    boolean isConnected();

    boolean reconnect();

    String getInstanceName();
}
