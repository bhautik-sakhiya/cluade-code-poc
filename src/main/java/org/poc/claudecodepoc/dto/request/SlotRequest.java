package org.poc.claudecodepoc.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Schema(description = "Request body for creating or updating an availability slot")
public class SlotRequest {

    @NotNull
    @Schema(description = "Keycloak user ID of the doctor", example = "doctor-uuid-123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String doctorId;

    @NotNull
    @FutureOrPresent
    @Schema(description = "Date of the slot (today or future)", example = "2026-06-15", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate date;

    @NotNull
    @Schema(description = "Slot start time (HH:mm)", example = "09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalTime startTime;

    @NotNull
    @Schema(description = "Slot end time (HH:mm), must be after startTime", example = "10:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalTime endTime;
}