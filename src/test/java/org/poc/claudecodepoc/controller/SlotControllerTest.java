package org.poc.claudecodepoc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartsensesolutions.commons.dao.filter.FilterRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.poc.claudecodepoc.dto.request.SlotRequest;
import org.poc.claudecodepoc.dto.response.SlotResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.poc.claudecodepoc.entity.enums.SlotStatus;
import org.poc.claudecodepoc.exception.BadRequestException;
import org.poc.claudecodepoc.exception.ForbiddenException;
import org.poc.claudecodepoc.exception.GlobalExceptionHandler;
import org.poc.claudecodepoc.exception.ResourceNotFoundException;
import org.poc.claudecodepoc.service.SlotService;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SlotControllerTest {

    @Mock
    private SlotService slotService;

    @InjectMocks
    private SlotController slotController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final String USER_ID = "user-abc";
    private static final String DOCTOR_ID = "doctor-123";
    private static final LocalDate DATE = LocalDate.now().plusDays(1);
    private static final LocalTime START = LocalTime.of(9, 0);
    private static final LocalTime END = LocalTime.of(10, 0);

    private SlotRequest validRequest;
    private SlotResponse slotResponse;

    @BeforeEach
    void setUp() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(USER_ID)
                .claim("realm_access", Map.of("roles", List.of("DOCTOR")))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))));

        mockMvc = MockMvcBuilders.standaloneSetup(slotController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        validRequest = new SlotRequest();
        validRequest.setDoctorId(DOCTOR_ID);
        validRequest.setDate(DATE);
        validRequest.setStartTime(START);
        validRequest.setEndTime(END);

        slotResponse = SlotResponse.builder()
                .id(1L)
                .doctorId(DOCTOR_ID)
                .date(DATE)
                .startTime(START)
                .endTime(END)
                .status(SlotStatus.AVAILABLE)
                .createdBy(USER_ID)
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── POST /api/v1/slots ──────────────────────────────────────────────────────

    @Test
    void createSlot_returns201() throws Exception {
        when(slotService.createSlot(any(SlotRequest.class), eq(USER_ID))).thenReturn(slotResponse);

        mockMvc.perform(post("/api/v1/slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.doctorId").value(DOCTOR_ID));
    }

    @Test
    void createSlot_returns400_onDuplicateSlot() throws Exception {
        when(slotService.createSlot(any(), eq(USER_ID))).thenThrow(new BadRequestException("already exists"));

        mockMvc.perform(post("/api/v1/slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("already exists"));
    }

    // ── GET /api/v1/slots/{id} ──────────────────────────────────────────────────

    @Test
    void getSlot_returns200() throws Exception {
        when(slotService.getSlotById(1L)).thenReturn(slotResponse);

        mockMvc.perform(get("/api/v1/slots/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void getSlot_returns404_whenNotFound() throws Exception {
        when(slotService.getSlotById(99L)).thenThrow(new ResourceNotFoundException("Slot not found with id: 99"));

        mockMvc.perform(get("/api/v1/slots/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── GET /api/v1/slots?doctorId={doctorId} ───────────────────────────────────

    @Test
    void getSlotsByDoctor_returns200() throws Exception {
        when(slotService.getSlotsByDoctor(DOCTOR_ID, null)).thenReturn(List.of(slotResponse));

        mockMvc.perform(get("/api/v1/slots").param("doctorId", DOCTOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].doctorId").value(DOCTOR_ID));
    }

    @Test
    void getSlotsByDoctor_withDateFilter_passesDateToService() throws Exception {
        when(slotService.getSlotsByDoctor(DOCTOR_ID, DATE)).thenReturn(List.of(slotResponse));

        mockMvc.perform(get("/api/v1/slots")
                        .param("doctorId", DOCTOR_ID)
                        .param("date", DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(slotService).getSlotsByDoctor(DOCTOR_ID, DATE);
    }

    // ── PUT /api/v1/slots/{id} ──────────────────────────────────────────────────

    @Test
    void updateSlot_returns200() throws Exception {
        when(slotService.updateSlot(eq(1L), any(SlotRequest.class), eq(USER_ID))).thenReturn(slotResponse);

        mockMvc.perform(put("/api/v1/slots/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateSlot_returns403_whenNotOwner() throws Exception {
        when(slotService.updateSlot(eq(1L), any(), eq(USER_ID))).thenThrow(new ForbiddenException("Not allowed"));

        mockMvc.perform(put("/api/v1/slots/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── DELETE /api/v1/slots/{id} ───────────────────────────────────────────────

    @Test
    void deleteSlot_returns200() throws Exception {
        doNothing().when(slotService).deleteSlot(1L, USER_ID);

        mockMvc.perform(delete("/api/v1/slots/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Slot deleted"));
    }

    @Test
    void deleteSlot_returns400_whenSlotIsBooked() throws Exception {
        doThrow(new BadRequestException("Cannot delete a booked slot")).when(slotService).deleteSlot(1L, USER_ID);

        mockMvc.perform(delete("/api/v1/slots/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot delete a booked slot"));
    }

    @Test
    void deleteSlot_returns404_whenNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Slot not found with id: 1")).when(slotService).deleteSlot(1L, USER_ID);

        mockMvc.perform(delete("/api/v1/slots/1"))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/v1/slots/filter ───────────────────────────────────────────

    @Test
    void filterSlots_returns200_withPagedResult() throws Exception {
        Page<SlotResponse> page = new PageImpl<>(List.of(slotResponse), PageRequest.of(0, 10), 1);
        when(slotService.filterPaged(any(FilterRequest.class))).thenReturn(page);

        mockMvc.perform(post("/api/v1/slots/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FilterRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }
}