package org.poc.claudecodepoc.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import org.poc.claudecodepoc.entity.enums.SlotStatus;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@Schema(description = "Doctor availability slot")
public class SlotResponse {

    @Schema(description = "Slot ID", example = "1")
    private Long id;

    @Schema(description = "Doctor's Keycloak user ID", example = "doctor-uuid-123")
    private String doctorId;

    @Schema(description = "Date of the slot", example = "2026-06-15")
    private LocalDate date;

    @Schema(description = "Slot start time", example = "09:00")
    private LocalTime startTime;

    @Schema(description = "Slot end time", example = "10:00")
    private LocalTime endTime;

    @Schema(description = "Current slot status", example = "AVAILABLE")
    private SlotStatus status;

    @Schema(description = "Keycloak user ID of the doctor who created the slot", example = "doctor-uuid-123")
    private String createdBy;
}