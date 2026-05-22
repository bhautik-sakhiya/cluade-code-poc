package org.poc.claudecodepoc.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.poc.claudecodepoc.entity.enums.AppointmentStatus;

@Data
@Schema(description = "Request body for updating an appointment's status")
public class AppointmentStatusUpdateRequest {

    @NotNull(message = "Status is required")
    @Schema(
            description = "Target status. Patients may use CANCELLED; doctors may use CONFIRMED, REJECTED, COMPLETED.",
            example = "CONFIRMED",
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"CONFIRMED", "REJECTED", "CANCELLED", "COMPLETED"}
    )
    private AppointmentStatus status;

    @Schema(description = "Mandatory when status is REJECTED", example = "Schedule conflict")
    private String reason;
}