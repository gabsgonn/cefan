package br.com.cefan.whatsapp.infrastructure.evolution.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendPollMessageRequest {
    private String number;
    private String name;
    private Integer selectableCount;
    private List<String> values;
}
