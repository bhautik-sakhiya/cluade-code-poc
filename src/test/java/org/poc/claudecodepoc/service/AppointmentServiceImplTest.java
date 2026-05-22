package org.poc.claudecodepoc.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.poc.claudecodepoc.dto.request.AppointmentRequest;
import org.poc.claudecodepoc.dto.response.AppointmentResponse;
import org.poc.claudecodepoc.entity.Appointment;
import org.poc.claudecodepoc.entity.Slot;
import org.poc.claudecodepoc.entity.enums.AppointmentStatus;
import org.poc.claudecodepoc.entity.enums.SlotStatus;
import org.poc.claudecodepoc.exception.BadRequestException;
import org.poc.claudecodepoc.exception.ForbiddenException;
import org.poc.claudecodepoc.exception.ResourceNotFoundException;
import org.poc.claudecodepoc.repository.AppointmentRepository;
import org.poc.claudecodepoc.repository.SlotRepository;
import org.poc.claudecodepoc.service.impl.AppointmentServiceImpl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceImplTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private SlotRepository slotRepository;

    @InjectMocks
    private AppointmentServiceImpl appointmentService;

    private static final String PATIENT_ID = "patient-abc";
    private static final String DOCTOR_ID = "doctor-123";
    private static final String OTHER_ID = "other-user";
    private static final Long SLOT_ID = 1L;
    private static final Long APPOINTMENT_ID = 10L;

    private Slot availableSlot;
    private Appointment pendingAppointment;
    private Appointment confirmedAppointment;
    private AppointmentRequest appointmentRequest;

    @BeforeEach
    void setUp() {
        availableSlot = Slot.builder()
                .id(SLOT_ID)
                .doctorId(DOCTOR_ID)
                .date(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .status(SlotStatus.AVAILABLE)
                .createdBy(DOCTOR_ID)
                .build();

        pendingAppointment = Appointment.builder()
                .id(APPOINTMENT_ID)
                .patientId(PATIENT_ID)
                .slot(availableSlot)
                .status(AppointmentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        confirmedAppointment = Appointment.builder()
                .id(APPOINTMENT_ID)
                .patientId(PATIENT_ID)
                .slot(availableSlot)
                .status(AppointmentStatus.CONFIRMED)
                .createdAt(LocalDateTime.now())
                .build();

        appointmentRequest = new AppointmentRequest();
        appointmentRequest.setSlotId(SLOT_ID);
    }

    // ── bookAppointment ──────────────────────────────────────────────────────

    @Test
    void bookAppointment_success() {
        when(slotRepository.findById(SLOT_ID)).thenReturn(Optional.of(availableSlot));
        when(appointmentRepository.existsByPatientIdAndSlot_Id(PATIENT_ID, SLOT_ID)).thenReturn(false);
        when(appointmentRepository.save(any(Appointment.class))).thenReturn(pendingAppointment);

        AppointmentResponse response = appointmentService.bookAppointment(appointmentRequest, PATIENT_ID);

        assertThat(response.getId()).isEqualTo(APPOINTMENT_ID);
        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(response.getPatientId()).isEqualTo(PATIENT_ID);
        verify(slotRepository).save(availableSlot);
        assertThat(availableSlot.getStatus()).isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void bookAppointment_throwsNotFound_whenSlotMissing() {
        when(slotRepository.findById(SLOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.bookAppointment(appointmentRequest, PATIENT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(SLOT_ID.toString());

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void bookAppointment_throwsBadRequest_whenSlotNotAvailable() {
        availableSlot.setStatus(SlotStatus.BOOKED);
        when(slotRepository.findById(SLOT_ID)).thenReturn(Optional.of(availableSlot));

        assertThatThrownBy(() -> appointmentService.bookAppointment(appointmentRequest, PATIENT_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not available");

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void bookAppointment_throwsBadRequest_whenDuplicateBooking() {
        when(slotRepository.findById(SLOT_ID)).thenReturn(Optional.of(availableSlot));
        when(appointmentRepository.existsByPatientIdAndSlot_Id(PATIENT_ID, SLOT_ID)).thenReturn(true);

        assertThatThrownBy(() -> appointmentService.bookAppointment(appointmentRequest, PATIENT_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already booked");

        verify(appointmentRepository, never()).save(any());
    }

    // ── cancelAppointment ────────────────────────────────────────────────────

    @Test
    void cancelAppointment_success_whenPending() {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(pendingAppointment));
        when(appointmentRepository.save(pendingAppointment)).thenReturn(pendingAppointment);

        AppointmentResponse response = appointmentService.cancelAppointment(APPOINTMENT_ID, PATIENT_ID);

        assertThat(response).isNotNull();
        assertThat(pendingAppointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(availableSlot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        verify(slotRepository).save(availableSlot);
        verify(appointmentRepository).save(pendingAppointment);
    }

    @Test
    void cancelAppointment_success_whenConfirmed() {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(confirmedAppointment));
        when(appointmentRepository.save(confirmedAppointment)).thenReturn(confirmedAppointment);

        AppointmentResponse response = appointmentService.cancelAppointment(APPOINTMENT_ID, PATIENT_ID);

        assertThat(response).isNotNull();
        assertThat(confirmedAppointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void cancelAppointment_throwsNotFound_whenMissing() {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.cancelAppointment(APPOINTMENT_ID, PATIENT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancelAppointment_throwsForbidden_whenNotOwner() {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(pendingAppointment));

        assertThatThrownBy(() -> appointmentService.cancelAppointment(APPOINTMENT_ID, OTHER_ID))
                .isInstanceOf(ForbiddenException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void cancelAppointment_throwsBadRequest_whenAlreadyCancelled() {
        pendingAppointment.setStatus(AppointmentStatus.CANCELLED);
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(pendingAppointment));

        assertThatThrownBy(() -> appointmentService.cancelAppointment(APPOINTMENT_ID, PATIENT_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PENDING or CONFIRMED");

        verify(appointmentRepository, never()).save(any());
    }

    // ── getMyAppointments ────────────────────────────────────────────────────

    @Test
    void getMyAppointments_returnsPatientAppointments() {
        when(appointmentRepository.findByPatientId(PATIENT_ID)).thenReturn(List.of(pendingAppointment));

        List<AppointmentResponse> result = appointmentService.getMyAppointments(PATIENT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPatientId()).isEqualTo(PATIENT_ID);
    }

    // ── confirmAppointment ───────────────────────────────────────────────────

    @Test
    void confirmAppointment_success() {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(pendingAppointment));
        when(appointmentRepository.save(pendingAppointment)).thenReturn(pendingAppointment);

        AppointmentResponse response = appointmentService.confirmAppointment(APPOINTMENT_ID, DOCTOR_ID);

        assertThat(pendingAppointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(response).isNotNull();
    }

    @Test
    void confirmAppointment_throwsForbidden_whenNotDoctorOfSlot() {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(pendingAppointment));

        assertThatThrownBy(() -> appointmentService.confirmAppointment(APPOINTMENT_ID, OTHER_ID))
                .isInstanceOf(ForbiddenException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void confirmAppointment_throwsBadRequest_whenNotPending() {
        pendingAppointment.setStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(pendingAppointment));

        assertThatThrownBy(() -> appointmentService.confirmAppointment(APPOINTMENT_ID, DOCTOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PENDING");

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void confirmAppointment_throwsNotFound_whenMissing() {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.confirmAppointment(APPOINTMENT_ID, DOCTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── rejectAppointment ────────────────────────────────────────────────────

    @Test
    void rejectAppointment_success() {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(pendingAppointment));
        when(appointmentRepository.save(pendingAppointment)).thenReturn(pendingAppointment);

        AppointmentResponse response = appointmentService.rejectAppointment(APPOINTMENT_ID, DOCTOR_ID, "Not available");

        assertThat(pendingAppointment.getStatus()).isEqualTo(AppointmentStatus.REJECTED);
        assertThat(pendingAppointment.getRejectionReason()).isEqualTo("Not available");
        assertThat(availableSlot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        verify(slotRepository).save(availableSlot);
    }

    @Test
    void rejectAppointment_throwsForbidden_whenNotDoctorOfSlot() {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(pendingAppointment));

        assertThatThrownBy(() -> appointmentService.rejectAppointment(APPOINTMENT_ID, OTHER_ID, "reason"))
                .isInstanceOf(ForbiddenException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void rejectAppointment_throwsBadRequest_whenNotPending() {
        pendingAppointment.setStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(pendingAppointment));

        assertThatThrownBy(() -> appointmentService.rejectAppointment(APPOINTMENT_ID, DOCTOR_ID, "reason"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PENDING");

        verify(appointmentRepository, never()).save(any());
    }

    // ── completeAppointment ──────────────────────────────────────────────────

    @Test
    void completeAppointment_success() {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(confirmedAppointment));
        when(appointmentRepository.save(confirmedAppointment)).thenReturn(confirmedAppointment);

        AppointmentResponse response = appointmentService.completeAppointment(APPOINTMENT_ID, DOCTOR_ID);

        assertThat(confirmedAppointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(response).isNotNull();
    }

    @Test
    void completeAppointment_throwsForbidden_whenNotDoctorOfSlot() {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(confirmedAppointment));

        assertThatThrownBy(() -> appointmentService.completeAppointment(APPOINTMENT_ID, OTHER_ID))
                .isInstanceOf(ForbiddenException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void completeAppointment_throwsBadRequest_whenNotConfirmed() {
        confirmedAppointment.setStatus(AppointmentStatus.PENDING);
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(confirmedAppointment));

        assertThatThrownBy(() -> appointmentService.completeAppointment(APPOINTMENT_ID, DOCTOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CONFIRMED");

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void completeAppointment_throwsNotFound_whenMissing() {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.completeAppointment(APPOINTMENT_ID, DOCTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getAppointmentsForDoctor ─────────────────────────────────────────────

    @Test
    void getAppointmentsForDoctor_withNoFilters_returnsAll() {
        when(appointmentRepository.findByDoctorWithFilters(DOCTOR_ID, null, null))
                .thenReturn(List.of(pendingAppointment));

        List<AppointmentResponse> result = appointmentService.getAppointmentsForDoctor(DOCTOR_ID, null, null);

        assertThat(result).hasSize(1);
        verify(appointmentRepository).findByDoctorWithFilters(DOCTOR_ID, null, null);
    }

    @Test
    void getAppointmentsForDoctor_withDateOnly_filtersByDate() {
        LocalDate date = LocalDate.now().plusDays(1);
        when(appointmentRepository.findByDoctorWithFilters(DOCTOR_ID, date, null))
                .thenReturn(List.of(pendingAppointment));

        List<AppointmentResponse> result = appointmentService.getAppointmentsForDoctor(DOCTOR_ID, date, null);

        assertThat(result).hasSize(1);
        verify(appointmentRepository).findByDoctorWithFilters(DOCTOR_ID, date, null);
    }

    @Test
    void getAppointmentsForDoctor_withStatusOnly_filtersByStatus() {
        when(appointmentRepository.findByDoctorWithFilters(DOCTOR_ID, null, AppointmentStatus.PENDING))
                .thenReturn(List.of(pendingAppointment));

        List<AppointmentResponse> result = appointmentService.getAppointmentsForDoctor(DOCTOR_ID, null, AppointmentStatus.PENDING);

        assertThat(result).hasSize(1);
        verify(appointmentRepository).findByDoctorWithFilters(DOCTOR_ID, null, AppointmentStatus.PENDING);
    }

    @Test
    void getAppointmentsForDoctor_withDateAndStatus_filtersBoth() {
        LocalDate date = LocalDate.now().plusDays(1);
        when(appointmentRepository.findByDoctorWithFilters(DOCTOR_ID, date, AppointmentStatus.PENDING))
                .thenReturn(List.of(pendingAppointment));

        List<AppointmentResponse> result = appointmentService.getAppointmentsForDoctor(DOCTOR_ID, date, AppointmentStatus.PENDING);

        assertThat(result).hasSize(1);
        verify(appointmentRepository).findByDoctorWithFilters(DOCTOR_ID, date, AppointmentStatus.PENDING);
    }

    // ── updateAppointmentStatus ──────────────────────────────────────────────

    @Test
    void updateAppointmentStatus_cancel_delegatesToCancel() {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(pendingAppointment));
        when(appointmentRepository.save(pendingAppointment)).thenReturn(pendingAppointment);

        AppointmentResponse response = appointmentService.updateAppointmentStatus(
                APPOINTMENT_ID, PATIENT_ID, AppointmentStatus.CANCELLED, null);

        assertThat(response).isNotNull();
        assertThat(pendingAppointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void updateAppointmentStatus_confirm_delegatesToConfirm() {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(pendingAppointment));
        when(appointmentRepository.save(pendingAppointment)).thenReturn(pendingAppointment);

        AppointmentResponse response = appointmentService.updateAppointmentStatus(
                APPOINTMENT_ID, DOCTOR_ID, AppointmentStatus.CONFIRMED, null);

        assertThat(response).isNotNull();
        assertThat(pendingAppointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void updateAppointmentStatus_reject_delegatesToReject() {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(pendingAppointment));
        when(appointmentRepository.save(pendingAppointment)).thenReturn(pendingAppointment);

        AppointmentResponse response = appointmentService.updateAppointmentStatus(
                APPOINTMENT_ID, DOCTOR_ID, AppointmentStatus.REJECTED, "Schedule conflict");

        assertThat(response).isNotNull();
        assertThat(pendingAppointment.getStatus()).isEqualTo(AppointmentStatus.REJECTED);
        assertThat(pendingAppointment.getRejectionReason()).isEqualTo("Schedule conflict");
    }

    @Test
    void updateAppointmentStatus_throwsBadRequest_whenRejectWithNoReason() {
        assertThatThrownBy(() -> appointmentService.updateAppointmentStatus(
                        APPOINTMENT_ID, DOCTOR_ID, AppointmentStatus.REJECTED, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void updateAppointmentStatus_complete_delegatesToComplete() {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(confirmedAppointment));
        when(appointmentRepository.save(confirmedAppointment)).thenReturn(confirmedAppointment);

        AppointmentResponse response = appointmentService.updateAppointmentStatus(
                APPOINTMENT_ID, DOCTOR_ID, AppointmentStatus.COMPLETED, null);

        assertThat(response).isNotNull();
        assertThat(confirmedAppointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void updateAppointmentStatus_throwsBadRequest_whenInvalidStatus() {
        assertThatThrownBy(() -> appointmentService.updateAppointmentStatus(
                        APPOINTMENT_ID, PATIENT_ID, AppointmentStatus.PENDING, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid status transition");
    }

    // ── getAppointments ──────────────────────────────────────────────────────

    @Test
    void getAppointments_returnsPatientAppointments_whenNotDoctor() {
        when(appointmentRepository.findByPatientId(PATIENT_ID)).thenReturn(List.of(pendingAppointment));

        List<AppointmentResponse> result = appointmentService.getAppointments(PATIENT_ID, false, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPatientId()).isEqualTo(PATIENT_ID);
    }

    @Test
    void getAppointments_returnsDoctorAppointments_whenDoctor() {
        when(appointmentRepository.findByDoctorWithFilters(DOCTOR_ID, null, null))
                .thenReturn(List.of(pendingAppointment));

        List<AppointmentResponse> result = appointmentService.getAppointments(DOCTOR_ID, true, null, null);

        assertThat(result).hasSize(1);
        verify(appointmentRepository).findByDoctorWithFilters(DOCTOR_ID, null, null);
    }
}