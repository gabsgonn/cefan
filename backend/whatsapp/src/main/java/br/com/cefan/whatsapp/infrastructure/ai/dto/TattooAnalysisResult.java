package br.com.cefan.whatsapp.infrastructure.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TattooAnalysisResult {
    private String style;
    private String complexityLevel;
    private Double complexityMultiplier;
    private Double estimatedHours;
    private String suggestedBodyLocation;
    private String technicalNotes;
    private List<String> primaryColors;
}
