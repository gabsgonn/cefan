package br.com.cefan.whatsapp.infrastructure.evolution.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendTextMessageRequest {
    private String number;
    private String text;
}
