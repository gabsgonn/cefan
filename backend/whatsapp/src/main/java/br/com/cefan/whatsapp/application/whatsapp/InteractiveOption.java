package br.com.cefan.whatsapp.application.whatsapp;

public record InteractiveOption(
        String id,
        String title,
        String description
) {
    public InteractiveOption(String id, String title) {
        this(id, title, null);
    }
}
