package br.com.cefan.whatsapp.api.controller;

import br.com.cefan.whatsapp.api.dto.ProfessionalDto;
import br.com.cefan.whatsapp.application.service.ProfessionalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(username = "admin@viciotattoo.com", roles = {"OWNER"})
class ProfessionalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProfessionalService professionalService;

    @Test
    void shouldListProfessionals() throws Exception {
        UUID id = UUID.randomUUID();
        ProfessionalDto dto = ProfessionalDto.builder()
                .id(id)
                .name("Carlos Eduardo")
                .email("carlos@viciotattoo.com")
                .specialties("Fineline, Traço Fino")
                .artistRateType("PERCENTAGE")
                .artistRateValue(new BigDecimal("60.00"))
                .reputation(new BigDecimal("4.9"))
                .isActive(true)
                .build();

        when(professionalService.listAll(true)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/professionals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Carlos Eduardo"))
                .andExpect(jsonPath("$[0].specialties").value("Fineline, Traço Fino"));
    }

    @Test
    void shouldGetProfessionalById() throws Exception {
        UUID id = UUID.randomUUID();
        ProfessionalDto dto = ProfessionalDto.builder()
                .id(id)
                .name("Mariana Rocha")
                .email("mariana@viciotattoo.com")
                .specialties("Realismo Preto e Cinza")
                .reputation(new BigDecimal("5.0"))
                .build();

        when(professionalService.getById(id)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/professionals/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mariana Rocha"))
                .andExpect(jsonPath("$.specialties").value("Realismo Preto e Cinza"));
    }

    @Test
    void shouldCreateProfessional() throws Exception {
        UUID id = UUID.randomUUID();
        ProfessionalDto request = ProfessionalDto.builder()
                .name("Novo Artista")
                .email("novo@viciotattoo.com")
                .password("cefan123")
                .specialties("Old School")
                .artistRateType("PERCENTAGE")
                .artistRateValue(new BigDecimal("60.00"))
                .build();

        ProfessionalDto response = ProfessionalDto.builder()
                .id(id)
                .name("Novo Artista")
                .email("novo@viciotattoo.com")
                .specialties("Old School")
                .artistRateType("PERCENTAGE")
                .artistRateValue(new BigDecimal("60.00"))
                .isActive(true)
                .build();

        when(professionalService.create(any(ProfessionalDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/professionals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Novo Artista"));

        verify(professionalService).create(any(ProfessionalDto.class));
    }

    @Test
    void shouldUpdateProfessional() throws Exception {
        UUID id = UUID.randomUUID();
        ProfessionalDto request = ProfessionalDto.builder()
                .name("Artista Atualizado")
                .email("artista@viciotattoo.com")
                .specialties("Aquarela")
                .artistRateValue(new BigDecimal("65.00"))
                .build();

        ProfessionalDto response = ProfessionalDto.builder()
                .id(id)
                .name("Artista Atualizado")
                .email("artista@viciotattoo.com")
                .specialties("Aquarela")
                .artistRateValue(new BigDecimal("65.00"))
                .build();

        when(professionalService.update(eq(id), any(ProfessionalDto.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/professionals/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Artista Atualizado"));

        verify(professionalService).update(eq(id), any(ProfessionalDto.class));
    }

    @Test
    void shouldDeleteProfessional() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/professionals/" + id))
                .andExpect(status().isNoContent());

        verify(professionalService).delete(id);
    }
}
