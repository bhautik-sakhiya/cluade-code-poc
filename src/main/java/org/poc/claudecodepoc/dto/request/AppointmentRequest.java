package org.poc.claudecodepoc.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request body for booking an appointment")
public class AppointmentRequest {

    @NotNull
    @Schema(description = "ID of the AVAILABLE slot to book", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long slotId;
}