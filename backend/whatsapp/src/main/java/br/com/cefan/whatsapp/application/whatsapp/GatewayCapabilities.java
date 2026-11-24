package br.com.cefan.whatsapp.application.whatsapp;

public record GatewayCapabilities(
        boolean supportsButtons,
        boolean supportsList,
        boolean supportsPoll,
        boolean requiresTemplateOutsideWindow
) {
    public static GatewayCapabilities evolution() {
        return new GatewayCapabilities(false, false, true, false);
    }

    public static GatewayCapabilities metaCloud() {
        return new GatewayCapabilities(true, true, false, true);
    }
}
