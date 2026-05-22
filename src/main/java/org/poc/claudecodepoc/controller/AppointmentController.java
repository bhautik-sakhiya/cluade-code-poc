package org.poc.claudecodepoc.controller;

import com.smartsensesolutions.commons.dao.filter.FilterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.poc.claudecodepoc.config.ApiVersion;
import org.poc.claudecodepoc.config.ApiVersions;
import org.poc.claudecodepoc.dto.request.AppointmentRequest;
import org.poc.claudecodepoc.dto.request.AppointmentStatusUpdateRequest;
import org.poc.claudecodepoc.dto.response.AppointmentResponse;
import org.poc.claudecodepoc.entity.enums.AppointmentStatus;
import org.poc.claudecodepoc.service.AppointmentService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "Appointments", description = "Book and manage patient appointments")
@RestController
@RequestMapping(ApiVersions.PREFIX + "/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @Operation(summary = "Book an appointment", description = "Patient books an AVAILABLE slot. Requires PATIENT role.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Appointment booked, status is PENDING"),
            @ApiResponse(responseCode = "400", description = "Slot not available or already booked by this patient", content = @Content(schema = @Schema(implementation = org.poc.claudecodepoc.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Caller is not a PATIENT"),
            @ApiResponse(responseCode = "404", description = "Slot not found")
    })
    @PostMapping
    @ApiVersion(from = 1)
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<org.poc.claudecodepoc.dto.response.ApiResponse<AppointmentResponse>> bookAppointment(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AppointmentRequest request) {
        AppointmentResponse response = appointmentService.bookAppointment(request, jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(org.poc.claudecodepoc.dto.response.ApiResponse.success(HttpStatus.CREATED, "Appointment booked", response));
    }

    @Operation(
            summary = "Update appointment status",
            description = """
                    Unified status transition endpoint. Role-based rules:
                    - **PATIENT**: CANCELLED (from PENDING or CONFIRMED)
                    - **DOCTOR**: CONFIRMED (from PENDING), REJECTED (from PENDING, reason required), COMPLETED (from CONFIRMED)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid transition or missing rejection reason"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Caller does not own or manage this appointment"),
            @ApiResponse(responseCode = "404", description = "Appointment not found")
    })
    @PatchMapping("/{id}")
    @ApiVersion(from = 1)
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT')")
    public ResponseEntity<org.poc.claudecodepoc.dto.response.ApiResponse<AppointmentResponse>> updateAppointmentStatus(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Appointment ID", example = "10") @PathVariable Long id,
            @Valid @RequestBody AppointmentStatusUpdateRequest request) {
        AppointmentResponse response = appointmentService.updateAppointmentStatus(
                id, jwt.getSubject(), request.getStatus(), request.getReason());
        return ResponseEntity.ok(org.poc.claudecodepoc.dto.response.ApiResponse.success(HttpStatus.OK,
                "Appointment " + request.getStatus().name().toLowerCase(), response));
    }

    @Operation(
            summary = "Get appointments",
            description = "Returns appointments for the calling user. Doctors see all appointments on their slots; patients see their own bookings."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Appointments returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    @GetMapping
    @ApiVersion(from = 1)
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
    public ResponseEntity<org.poc.claudecodepoc.dto.response.ApiResponse<List<AppointmentResponse>>> getAppointments(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Filter by date (ISO format: yyyy-MM-dd) — doctors only", example = "2026-06-15")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Filter by appointment status — doctors only", example = "PENDING")
            @RequestParam(required = false) AppointmentStatus status) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        Object rolesObj = realmAccess != null ? realmAccess.get("roles") : null;
        boolean isDoctor = rolesObj instanceof List<?> roles && roles.contains("DOCTOR");
        return ResponseEntity.ok(org.poc.claudecodepoc.dto.response.ApiResponse.success(
                appointmentService.getAppointments(jwt.getSubject(), isDoctor, date, status)));
    }

    @Operation(
            summary = "Filter and paginate appointments",
            description = "Dynamic filter using commons-dao FilterRequest. Supports criteria-based filtering, sorting, and pagination."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Filtered page of appointments"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    @PostMapping("/filter")
    @ApiVersion(from = 1)
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
    public ResponseEntity<org.poc.claudecodepoc.dto.response.ApiResponse<Page<AppointmentResponse>>> filterAppointments(
            @RequestBody FilterRequest request) {
        return ResponseEntity.ok(org.poc.claudecodepoc.dto.response.ApiResponse.success(appointmentService.filterPaged(request)));
    }
}