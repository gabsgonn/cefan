package br.com.cefan.whatsapp.api.controller;

import br.com.cefan.whatsapp.api.dto.AppointmentResponse;
import br.com.cefan.whatsapp.api.dto.CreateAppointmentRequest;
import br.com.cefan.whatsapp.api.dto.UpdateAppointmentRequest;
import br.com.cefan.whatsapp.application.service.AppointmentService;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
@AutoConfigureMockMvc
class AppointmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AppointmentService appointmentService;

    @Test
    void shouldListAppointments() throws Exception {
        UUID id = UUID.randomUUID();
        AppointmentResponse response = AppointmentResponse.builder()
                .id(id)
                .customerName("João Silva")
                .customerPhone("5511999991111")
                .scheduledStart(OffsetDateTime.now())
                .scheduledEnd(OffsetDateTime.now().plusHours(2))
                .status(AppointmentStatus.CONFIRMADO)
                .totalPrice(new BigDecimal("350.00"))
                .depositAmount(new BigDecimal("100.00"))
                .isTouchup(false)
                .build();

        when(appointmentService.listAppointments(any(), any(), any())).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/appointments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerName").value("João Silva"))
                .andExpect(jsonPath("$[0].status").value("CONFIRMADO"));
    }

    @Test
    void shouldCreateAppointment() throws Exception {
        CreateAppointmentRequest request = CreateAppointmentRequest.builder()
                .customerName("Aline")
                .customerPhone("5511988882222")
                .scheduledStart(OffsetDateTime.now().plusDays(1))
                .scheduledEnd(OffsetDateTime.now().plusDays(1).plusHours(2))
                .totalPrice(new BigDecimal("450.00"))
                .depositAmount(new BigDecimal("135.00"))
                .isTouchup(false)
                .build();

        AppointmentResponse response = AppointmentResponse.builder()
                .id(UUID.randomUUID())
                .customerName("Aline")
                .customerPhone("5511988882222")
                .status(AppointmentStatus.CONFIRMADO)
                .totalPrice(new BigDecimal("450.00"))
                .build();

        when(appointmentService.create(any(CreateAppointmentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerName").value("Aline"));

        verify(appointmentService).create(any(CreateAppointmentRequest.class));
    }

    @Test
    void shouldUpdateAppointmentWithAuditReason() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateAppointmentRequest request = UpdateAppointmentRequest.builder()
                .newPrice(new BigDecimal("500.00"))
                .reasonForChange("Cliente pediu para aumentar o tamanho do desenho na hora")
                .build();

        AppointmentResponse response = AppointmentResponse.builder()
                .id(id)
                .customerName("Aline")
                .totalPrice(new BigDecimal("500.00"))
                .build();

        when(appointmentService.update(eq(id), any(UpdateAppointmentRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/v1/appointments/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrice").value(500.00));

        verify(appointmentService).update(eq(id), any(UpdateAppointmentRequest.class));
    }
}
