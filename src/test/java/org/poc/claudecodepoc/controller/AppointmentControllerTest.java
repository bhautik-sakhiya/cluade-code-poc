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
import org.poc.claudecodepoc.dto.request.AppointmentRequest;
import org.poc.claudecodepoc.dto.request.AppointmentStatusUpdateRequest;
import org.poc.claudecodepoc.dto.response.AppointmentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.poc.claudecodepoc.entity.enums.AppointmentStatus;
import org.poc.claudecodepoc.exception.BadRequestException;
import org.poc.claudecodepoc.exception.ForbiddenException;
import org.poc.claudecodepoc.exception.GlobalExceptionHandler;
import org.poc.claudecodepoc.exception.ResourceNotFoundException;
import org.poc.claudecodepoc.service.AppointmentService;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AppointmentControllerTest {

    @Mock
    private AppointmentService appointmentService;

    @InjectMocks
    private AppointmentController appointmentController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final String PATIENT_ID = "patient-abc";
    private static final String DOCTOR_ID = "doctor-123";

    private AppointmentResponse appointmentResponse;
    private AppointmentRequest appointmentRequest;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        appointmentRequest = new AppointmentRequest();
        appointmentRequest.setSlotId(1L);

        appointmentResponse = AppointmentResponse.builder()
                .id(10L)
                .patientId(PATIENT_ID)
                .slotId(1L)
                .doctorId(DOCTOR_ID)
                .date(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .status(AppointmentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(appointmentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void setPatientContext() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(PATIENT_ID)
                .claim("realm_access", Map.of("roles", List.of("PATIENT")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_PATIENT"))));
    }

    private void setDoctorContext() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(DOCTOR_ID)
                .claim("realm_access", Map.of("roles", List.of("DOCTOR")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))));
    }

    // ── POST /api/v1/appointments ────────────────────────────────────────────

    @Test
    void bookAppointment_returns201() throws Exception {
        setPatientContext();
        when(appointmentService.bookAppointment(any(AppointmentRequest.class), eq(PATIENT_ID)))
                .thenReturn(appointmentResponse);

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(appointmentRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void bookAppointment_returns400_whenSlotNotAvailable() throws Exception {
        setPatientContext();
        when(appointmentService.bookAppointment(any(), eq(PATIENT_ID)))
                .thenThrow(new BadRequestException("Slot is not available for booking"));

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(appointmentRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Slot is not available for booking"));
    }

    @Test
    void bookAppointment_returns404_whenSlotNotFound() throws Exception {
        setPatientContext();
        when(appointmentService.bookAppointment(any(), eq(PATIENT_ID)))
                .thenThrow(new ResourceNotFoundException("Slot not found with id: 1"));

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(appointmentRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── PATCH /api/v1/appointments/{id} — status transitions ─────────────────

    @Test
    void updateAppointmentStatus_cancel_returns200() throws Exception {
        setPatientContext();
        AppointmentStatusUpdateRequest req = new AppointmentStatusUpdateRequest();
        req.setStatus(AppointmentStatus.CANCELLED);
        appointmentResponse.setStatus(AppointmentStatus.CANCELLED);
        when(appointmentService.updateAppointmentStatus(10L, PATIENT_ID, AppointmentStatus.CANCELLED, null))
                .thenReturn(appointmentResponse);

        mockMvc.perform(patch("/api/v1/appointments/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Appointment cancelled"));
    }

    @Test
    void updateAppointmentStatus_cancel_returns403_whenNotOwner() throws Exception {
        setPatientContext();
        AppointmentStatusUpdateRequest req = new AppointmentStatusUpdateRequest();
        req.setStatus(AppointmentStatus.CANCELLED);
        when(appointmentService.updateAppointmentStatus(10L, PATIENT_ID, AppointmentStatus.CANCELLED, null))
                .thenThrow(new ForbiddenException("You are not allowed to cancel this appointment"));

        mockMvc.perform(patch("/api/v1/appointments/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void updateAppointmentStatus_cancel_returns400_whenInvalidTransition() throws Exception {
        setPatientContext();
        AppointmentStatusUpdateRequest req = new AppointmentStatusUpdateRequest();
        req.setStatus(AppointmentStatus.CANCELLED);
        when(appointmentService.updateAppointmentStatus(10L, PATIENT_ID, AppointmentStatus.CANCELLED, null))
                .thenThrow(new BadRequestException("Only PENDING or CONFIRMED appointments can be cancelled"));

        mockMvc.perform(patch("/api/v1/appointments/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only PENDING or CONFIRMED appointments can be cancelled"));
    }

    @Test
    void updateAppointmentStatus_returns400_whenStatusMissing() throws Exception {
        setPatientContext();

        mockMvc.perform(patch("/api/v1/appointments/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateAppointmentStatus_confirm_returns200() throws Exception {
        setDoctorContext();
        AppointmentStatusUpdateRequest req = new AppointmentStatusUpdateRequest();
        req.setStatus(AppointmentStatus.CONFIRMED);
        appointmentResponse.setStatus(AppointmentStatus.CONFIRMED);
        when(appointmentService.updateAppointmentStatus(10L, DOCTOR_ID, AppointmentStatus.CONFIRMED, null))
                .thenReturn(appointmentResponse);

        mockMvc.perform(patch("/api/v1/appointments/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Appointment confirmed"))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void updateAppointmentStatus_confirm_returns400_whenNotPending() throws Exception {
        setDoctorContext();
        AppointmentStatusUpdateRequest req = new AppointmentStatusUpdateRequest();
        req.setStatus(AppointmentStatus.CONFIRMED);
        when(appointmentService.updateAppointmentStatus(10L, DOCTOR_ID, AppointmentStatus.CONFIRMED, null))
                .thenThrow(new BadRequestException("Only PENDING appointments can be confirmed"));

        mockMvc.perform(patch("/api/v1/appointments/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void updateAppointmentStatus_confirm_returns403_whenNotDoctorOfSlot() throws Exception {
        setDoctorContext();
        AppointmentStatusUpdateRequest req = new AppointmentStatusUpdateRequest();
        req.setStatus(AppointmentStatus.CONFIRMED);
        when(appointmentService.updateAppointmentStatus(10L, DOCTOR_ID, AppointmentStatus.CONFIRMED, null))
                .thenThrow(new ForbiddenException("You are not allowed to confirm this appointment"));

        mockMvc.perform(patch("/api/v1/appointments/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateAppointmentStatus_reject_returns200() throws Exception {
        setDoctorContext();
        AppointmentStatusUpdateRequest req = new AppointmentStatusUpdateRequest();
        req.setStatus(AppointmentStatus.REJECTED);
        req.setReason("Schedule conflict");
        appointmentResponse.setStatus(AppointmentStatus.REJECTED);
        when(appointmentService.updateAppointmentStatus(10L, DOCTOR_ID, AppointmentStatus.REJECTED, "Schedule conflict"))
                .thenReturn(appointmentResponse);

        mockMvc.perform(patch("/api/v1/appointments/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Appointment rejected"))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void updateAppointmentStatus_complete_returns200() throws Exception {
        setDoctorContext();
        AppointmentStatusUpdateRequest req = new AppointmentStatusUpdateRequest();
        req.setStatus(AppointmentStatus.COMPLETED);
        appointmentResponse.setStatus(AppointmentStatus.COMPLETED);
        when(appointmentService.updateAppointmentStatus(10L, DOCTOR_ID, AppointmentStatus.COMPLETED, null))
                .thenReturn(appointmentResponse);

        mockMvc.perform(patch("/api/v1/appointments/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Appointment completed"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void updateAppointmentStatus_complete_returns400_whenNotConfirmed() throws Exception {
        setDoctorContext();
        AppointmentStatusUpdateRequest req = new AppointmentStatusUpdateRequest();
        req.setStatus(AppointmentStatus.COMPLETED);
        when(appointmentService.updateAppointmentStatus(10L, DOCTOR_ID, AppointmentStatus.COMPLETED, null))
                .thenThrow(new BadRequestException("Only CONFIRMED appointments can be completed"));

        mockMvc.perform(patch("/api/v1/appointments/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void updateAppointmentStatus_complete_returns404_whenNotFound() throws Exception {
        setDoctorContext();
        AppointmentStatusUpdateRequest req = new AppointmentStatusUpdateRequest();
        req.setStatus(AppointmentStatus.COMPLETED);
        when(appointmentService.updateAppointmentStatus(10L, DOCTOR_ID, AppointmentStatus.COMPLETED, null))
                .thenThrow(new ResourceNotFoundException("Appointment not found with id: 10"));

        mockMvc.perform(patch("/api/v1/appointments/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/appointments ─────────────────────────────────────────────

    @Test
    void getAppointments_asPatient_returns200() throws Exception {
        setPatientContext();
        when(appointmentService.getAppointments(PATIENT_ID, false, null, null))
                .thenReturn(List.of(appointmentResponse));

        mockMvc.perform(get("/api/v1/appointments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].patientId").value(PATIENT_ID));
    }

    @Test
    void getAppointments_asDoctor_returns200() throws Exception {
        setDoctorContext();
        when(appointmentService.getAppointments(DOCTOR_ID, true, null, null))
                .thenReturn(List.of(appointmentResponse));

        mockMvc.perform(get("/api/v1/appointments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].doctorId").value(DOCTOR_ID));
    }

    @Test
    void getAppointments_asDoctor_withFilters_passesFiltersToService() throws Exception {
        setDoctorContext();
        LocalDate date = LocalDate.now().plusDays(1);
        when(appointmentService.getAppointments(DOCTOR_ID, true, date, AppointmentStatus.PENDING))
                .thenReturn(List.of(appointmentResponse));

        mockMvc.perform(get("/api/v1/appointments")
                        .param("date", date.toString())
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(appointmentService).getAppointments(DOCTOR_ID, true, date, AppointmentStatus.PENDING);
    }

    // ── POST /api/v1/appointments/filter ────────────────────────────────────

    @Test
    void filterAppointments_returns200_withPagedResult() throws Exception {
        setPatientContext();
        Page<AppointmentResponse> page = new PageImpl<>(List.of(appointmentResponse), PageRequest.of(0, 10), 1);
        when(appointmentService.filterPaged(any(FilterRequest.class))).thenReturn(page);

        mockMvc.perform(post("/api/v1/appointments/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FilterRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }
}