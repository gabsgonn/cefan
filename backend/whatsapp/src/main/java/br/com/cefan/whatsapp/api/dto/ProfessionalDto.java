package br.com.cefan.whatsapp.api.dto;

import br.com.cefan.whatsapp.domain.model.User;
import br.com.cefan.whatsapp.domain.model.UserRole;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfessionalDto {

    private UUID id;

    @NotBlank(message = "O nome é obrigatório.")
    private String name;

    @NotBlank(message = "O e-mail é obrigatório.")
    @Email(message = "Formato de e-mail inválido.")
    private String email;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String phone;

    @Builder.Default
    private UserRole role = UserRole.ARTIST;

    private String specialties;

    @Builder.Default
    private String artistRateType = "PERCENTAGE";

    @Builder.Default
    private BigDecimal artistRateValue = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal reputation = new BigDecimal("5.0");

    private LocalDate hireDate;

    @Builder.Default
    private Boolean isActive = true;

    public static ProfessionalDto fromEntity(User user) {
        if (user == null) {
            return null;
        }
        return ProfessionalDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .specialties(user.getSpecialties())
                .artistRateType(user.getArtistRateType() != null ? user.getArtistRateType() : "PERCENTAGE")
                .artistRateValue(user.getArtistRateValue() != null ? user.getArtistRateValue() : BigDecimal.ZERO)
                .reputation(user.getReputation() != null ? user.getReputation() : new BigDecimal("5.0"))
                .hireDate(user.getHireDate())
                .isActive(user.getIsActive())
                .build();
    }
}
