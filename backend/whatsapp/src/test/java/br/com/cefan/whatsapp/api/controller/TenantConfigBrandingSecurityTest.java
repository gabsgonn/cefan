package br.com.cefan.whatsapp.api.controller;

import br.com.cefan.whatsapp.api.dto.TenantConfigDto;
import br.com.cefan.whatsapp.application.service.TenantConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TenantConfigBrandingSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TenantConfigService tenantConfigService;

    private String ownerToken;
    private String artistToken;

    @BeforeEach
    void setUp() throws Exception {
        MvcResult ownerLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "admin@viciotattoo.com",
                                "password", "cefan123"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        ownerToken = objectMapper.readTree(ownerLogin.getResponse().getContentAsString()).get("token").asText();

        MvcResult artistLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "mariana@viciotattoo.com",
                                "password", "cefan123"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        artistToken = objectMapper.readTree(artistLogin.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void shouldRejectUnauthenticatedRequestToBranding() throws Exception {
        mockMvc.perform(get("/api/v1/tenant-configs/branding"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowArtistToFetchBrandingButNotFullConfig() throws Exception {
        TenantConfigDto dto = TenantConfigDto.builder()
                .studioName("Studio Teste")
                .logoBase64("data:image/png;base64,abc123")
                .defaultHourlyRate(new BigDecimal("200.00"))
                .build();
        when(tenantConfigService.getConfig()).thenReturn(dto);

        mockMvc.perform(get("/api/v1/tenant-configs/branding")
                        .header("Authorization", "Bearer " + artistToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studioName").value("Studio Teste"))
                .andExpect(jsonPath("$.logoBase64").value("data:image/png;base64,abc123"))
                .andExpect(jsonPath("$.defaultHourlyRate").doesNotExist());

        mockMvc.perform(get("/api/v1/tenant-configs")
                        .header("Authorization", "Bearer " + artistToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowOwnerToFetchBrandingWithNullLogo() throws Exception {
        TenantConfigDto dto = TenantConfigDto.builder()
                .studioName("Studio Sem Logo")
                .logoBase64(null)
                .build();
        when(tenantConfigService.getConfig()).thenReturn(dto);

        mockMvc.perform(get("/api/v1/tenant-configs/branding")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studioName").value("Studio Sem Logo"))
                .andExpect(jsonPath("$.logoBase64").value(nullValue()));
    }
}
