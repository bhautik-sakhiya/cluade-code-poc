package org.poc.claudecodepoc.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import org.poc.claudecodepoc.entity.enums.AppointmentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@Schema(description = "Appointment details")
public class AppointmentResponse {

    @Schema(description = "Appointment ID", example = "10")
    private Long id;

    @Schema(description = "Patient's Keycloak user ID", example = "patient-uuid-456")
    private String patientId;

    @Schema(description = "Booked slot ID", example = "42")
    private Long slotId;

    @Schema(description = "Doctor's Keycloak user ID", example = "doctor-uuid-123")
    private String doctorId;

    @Schema(description = "Appointment date", example = "2026-06-15")
    private LocalDate date;

    @Schema(description = "Start time of the appointment", example = "09:00")
    private LocalTime startTime;

    @Schema(description = "End time of the appointment", example = "10:00")
    private LocalTime endTime;

    @Schema(description = "Current appointment status", example = "PENDING")
    private AppointmentStatus status;

    @Schema(description = "Reason provided when the appointment was rejected", example = "Schedule conflict")
    private String rejectionReason;

    @Schema(description = "When the appointment was booked", example = "2026-05-22T10:30:00")
    private LocalDateTime createdAt;
}