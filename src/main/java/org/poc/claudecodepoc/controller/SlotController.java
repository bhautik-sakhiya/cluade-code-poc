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
import org.poc.claudecodepoc.dto.request.SlotRequest;
import org.poc.claudecodepoc.dto.response.SlotResponse;
import org.poc.claudecodepoc.service.SlotService;
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

@Tag(name = "Slots", description = "Manage doctor availability slots")
@RestController
@RequestMapping(ApiVersions.PREFIX + "/slots")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;

    @Operation(summary = "Create a slot", description = "Doctor creates an availability slot. Requires DOCTOR role.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Slot created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input or duplicate slot", content = @Content(schema = @Schema(implementation = org.poc.claudecodepoc.dto.response.ApiResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Caller is not a DOCTOR")
    })
    @PostMapping
    @ApiVersion(from = 1)
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<org.poc.claudecodepoc.dto.response.ApiResponse<SlotResponse>> createSlot(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SlotRequest request) {
        SlotResponse response = slotService.createSlot(request, jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(org.poc.claudecodepoc.dto.response.ApiResponse.success(HttpStatus.CREATED, "Slot created", response));
    }

    @Operation(summary = "Get a slot by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Slot found"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @ApiResponse(responseCode = "404", description = "Slot not found")
    })
    @GetMapping("/{id}")
    @ApiVersion(from = 1)
    public ResponseEntity<org.poc.claudecodepoc.dto.response.ApiResponse<SlotResponse>> getSlot(
            @Parameter(description = "Slot ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(org.poc.claudecodepoc.dto.response.ApiResponse.success(slotService.getSlotById(id)));
    }

    @Operation(summary = "List slots for a doctor", description = "Returns all slots for a given doctor, optionally filtered by date.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Slots returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    @GetMapping
    @ApiVersion(from = 1)
    public ResponseEntity<org.poc.claudecodepoc.dto.response.ApiResponse<List<SlotResponse>>> getSlots(
            @Parameter(description = "Doctor's Keycloak user ID", required = true, example = "doctor-uuid-123")
            @RequestParam String doctorId,
            @Parameter(description = "Filter by date (ISO format: yyyy-MM-dd)", example = "2026-06-15")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(org.poc.claudecodepoc.dto.response.ApiResponse.success(slotService.getSlotsByDoctor(doctorId, date)));
    }

    @Operation(summary = "Update a slot", description = "Doctor updates their own slot. Slot must not be BOOKED. Requires DOCTOR role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Slot updated"),
            @ApiResponse(responseCode = "400", description = "Cannot update a booked slot or invalid input"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Caller does not own this slot"),
            @ApiResponse(responseCode = "404", description = "Slot not found")
    })
    @PutMapping("/{id}")
    @ApiVersion(from = 1)
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<org.poc.claudecodepoc.dto.response.ApiResponse<SlotResponse>> updateSlot(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Slot ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody SlotRequest request) {
        return ResponseEntity.ok(
                org.poc.claudecodepoc.dto.response.ApiResponse.success(HttpStatus.OK, "Slot updated", slotService.updateSlot(id, request, jwt.getSubject())));
    }

    @Operation(summary = "Delete a slot", description = "Doctor deletes their own slot. Slot must not be BOOKED. Requires DOCTOR role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Slot deleted"),
            @ApiResponse(responseCode = "400", description = "Cannot delete a booked slot"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Caller does not own this slot"),
            @ApiResponse(responseCode = "404", description = "Slot not found")
    })
    @DeleteMapping("/{id}")
    @ApiVersion(from = 1)
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<org.poc.claudecodepoc.dto.response.ApiResponse<Void>> deleteSlot(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Slot ID", example = "1") @PathVariable Long id) {
        slotService.deleteSlot(id, jwt.getSubject());
        return ResponseEntity.ok(org.poc.claudecodepoc.dto.response.ApiResponse.success(HttpStatus.OK, "Slot deleted", null));
    }

    @Operation(
            summary = "Filter and paginate slots",
            description = "Dynamic filter using commons-dao FilterRequest. Supports criteria-based filtering, sorting, and pagination."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Filtered page of slots"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    @PostMapping("/filter")
    @ApiVersion(from = 1)
    public ResponseEntity<org.poc.claudecodepoc.dto.response.ApiResponse<Page<SlotResponse>>> filterSlots(
            @RequestBody FilterRequest request) {
        return ResponseEntity.ok(org.poc.claudecodepoc.dto.response.ApiResponse.success(slotService.filterPaged(request)));
    }
}