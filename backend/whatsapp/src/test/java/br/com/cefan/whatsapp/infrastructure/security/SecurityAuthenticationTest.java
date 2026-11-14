package br.com.cefan.whatsapp.infrastructure.security;

import br.com.cefan.whatsapp.domain.model.TenantConfig;
import br.com.cefan.whatsapp.domain.model.User;
import br.com.cefan.whatsapp.domain.model.UserRole;
import br.com.cefan.whatsapp.domain.repository.TenantConfigRepository;
import br.com.cefan.whatsapp.domain.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantConfigRepository tenantConfigRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        TenantConfig tenant = tenantConfigRepository.findAll().stream().findFirst().orElseGet(() ->
                tenantConfigRepository.save(TenantConfig.builder()
                        .studioName("Vício Tattoo")
                        .defaultHourlyRate(new BigDecimal("150.00"))
                        .fixedSetupCost(new BigDecimal("50.00"))
                        .absorbCreditCardFees(false)
                        .build())
        );

        if (userRepository.findByEmail("admin@viciotattoo.com").isEmpty()) {
            userRepository.save(User.builder()
                    .tenant(tenant)
                    .name("Dono - Vício Tattoo")
                    .email("admin@viciotattoo.com")
                    .role(UserRole.OWNER)
                    .passwordHash(passwordEncoder.encode("cefan123"))
                    .isActive(true)
                    .build());
        }
    }

    @Test
    void shouldLoginSuccessfullyWithValidCredentials() throws Exception {
        Map<String, String> loginRequest = Map.of(
                "email", "admin@viciotattoo.com",
                "password", "cefan123"
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.email").value("admin@viciotattoo.com"));
    }

    @Test
    void shouldRejectLoginWithInvalidPassword() throws Exception {
        Map<String, String> loginRequest = Map.of(
                "email", "admin@viciotattoo.com",
                "password", "senha_errada"
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciais inválidas para o painel administrativo."));
    }

    @Test
    void shouldRejectLoginForNonExistentUser() throws Exception {
        Map<String, String> loginRequest = Map.of(
                "email", "desconhecido@viciotattoo.com",
                "password", "qualquer"
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciais inválidas para o painel administrativo."));
    }

    @Test
    void shouldRejectUnauthenticatedAccessToAppointments() throws Exception {
        mockMvc.perform(get("/api/v1/appointments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldForbidArtistFromModifyingTenantConfig() throws Exception {
        String payload = """
                {
                  "studioName": "Nome Hacker",
                  "defaultHourlyRate": 999.00,
                  "fixedSetupCost": 100.00,
                  "absorbCreditCardFees": true
                }
                """;

        mockMvc.perform(put("/api/v1/tenant-configs")
                        .with(user("tatuador@viciotattoo.com").roles("ARTIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowPublicAccessToWebhooksHealth() throws Exception {
        mockMvc.perform(get("/api/v1/webhooks/evolution/health"))
                .andExpect(status().isOk());
    }
}
