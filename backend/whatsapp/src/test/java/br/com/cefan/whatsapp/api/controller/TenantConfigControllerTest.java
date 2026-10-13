package br.com.cefan.whatsapp.api.controller;

import br.com.cefan.whatsapp.api.dto.TenantConfigDto;
import br.com.cefan.whatsapp.application.service.TenantConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TenantConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TenantConfigService tenantConfigService;

    @Test
    void shouldReturnStudioConfig() throws Exception {
        TenantConfigDto dto = TenantConfigDto.builder()
                .id(UUID.randomUUID())
                .studioName("Vício Tattoo")
                .defaultHourlyRate(new BigDecimal("180.00"))
                .fixedSetupCost(new BigDecimal("60.00"))
                .absorbCreditCardFees(true)
                .build();

        when(tenantConfigService.getConfig()).thenReturn(dto);

        mockMvc.perform(get("/api/v1/tenant-configs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studioName").value("Vício Tattoo"))
                .andExpect(jsonPath("$.defaultHourlyRate").value(180.00))
                .andExpect(jsonPath("$.absorbCreditCardFees").value(true));
    }

    @Test
    void shouldUpdateStudioConfig() throws Exception {
        TenantConfigDto dto = TenantConfigDto.builder()
                .studioName("Vício Tattoo Premium")
                .defaultHourlyRate(new BigDecimal("220.00"))
                .fixedSetupCost(new BigDecimal("70.00"))
                .absorbCreditCardFees(false)
                .build();

        when(tenantConfigService.updateConfig(any(TenantConfigDto.class))).thenReturn(dto);

        mockMvc.perform(put("/api/v1/tenant-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studioName").value("Vício Tattoo Premium"))
                .andExpect(jsonPath("$.defaultHourlyRate").value(220.00));

        verify(tenantConfigService).updateConfig(any(TenantConfigDto.class));
    }
}
