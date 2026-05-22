# API Design Standards

Follow these rules for every REST endpoint in this project.

---

## 1. URL Design

### Resource naming
- Use **plural nouns** for resource paths: `/api/slots`, `/api/appointments`
- Use **kebab-case** for multi-word resources: `/api/appointment-notes`
- Never use verbs in URLs — the HTTP method is the verb

```
✅  POST   /api/appointments
✅  GET    /api/appointments/{id}
✅  PUT    /api/appointments/{id}
✅  DELETE /api/appointments/{id}

❌  POST   /api/createAppointment
❌  GET    /api/getAppointmentById/{id}
❌  POST   /api/appointment/cancel
```

### Sub-resources and filters
```
GET  /api/slots/doctor/{doctorId}           → slots for a doctor
GET  /api/slots/doctor/{doctorId}?date=...  → filtered by date
GET  /api/appointments?status=PENDING       → filtered list
```

### Status transitions (actions on a resource)
Use a sub-path with the action noun, not a verb:
```
PUT  /api/appointments/{id}/status      → update status (doctor action)
PUT  /api/appointments/{id}/reschedule  → reschedule request
```

---

## 2. HTTP Methods

| Method   | Use for                             | Success status |
|----------|-------------------------------------|----------------|
| `POST`   | Create a new resource               | `201 Created`  |
| `GET`    | Read one or many resources          | `200 OK`       |
| `PUT`    | Full update of an existing resource | `200 OK`       |
| `PATCH`  | Partial update                      | `200 OK`       |
| `DELETE` | Remove a resource                   | `200 OK`       |

---

## 3. Response Format

All endpoints return `ResponseEntity<ApiResponse<T>>`. Never return raw objects.

```java
// Create
ResponseEntity.status(HttpStatus.CREATED)
    .body(ApiResponse.success(HttpStatus.CREATED, "Appointment created", response));

// Read
ResponseEntity.ok(ApiResponse.success(response));

// Update
ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Appointment updated", response));

// Delete
ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Appointment cancelled", null));
```

Response body shape:
```json
{
  "status": 201,
  "success": true,
  "message": "Appointment created",
  "data": { ... }
}
```

Error shape:
```json
{
  "status": 404,
  "success": false,
  "message": "Appointment not found with id: 99"
}
```

---

## 4. Error Codes

| Scenario                          | Status | Exception to throw              |
|-----------------------------------|--------|---------------------------------|
| Resource not found                | 404    | `ResourceNotFoundException`     |
| Business rule violation           | 400    | `BadRequestException`           |
| Caller does not own the resource  | 403    | `ForbiddenException`            |
| Validation failure (`@Valid`)     | 400    | Handled by `GlobalExceptionHandler` automatically |
| Unexpected server error           | 500    | Let it propagate to handler     |

---

## 5. Request Validation

All `@RequestBody` parameters must be annotated with `@Valid`.
Use Jakarta validation annotations on request DTOs:

```java
@Data
public class AppointmentRequest {

    @NotNull(message = "Doctor ID is required")
    private String doctorId;

    @NotNull(message = "Slot ID is required")
    private Long slotId;

    @NotBlank(message = "Reason is required")
    private String reason;

    @NotNull(message = "Date is required")
    @FutureOrPresent(message = "Date must be today or in the future")
    private LocalDate preferredDate;
}
```

Validation errors are automatically caught by `GlobalExceptionHandler` and returned as `400` with the message.

---

## 6. Query Parameters

- Use `@RequestParam(required = false)` for optional filters
- Always use `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)` for `LocalDate` params
- Never put filter logic in the controller — pass params to the service

```java
@GetMapping("/doctor/{doctorId}")
public ResponseEntity<ApiResponse<List<SlotResponse>>> getSlots(
        @PathVariable String doctorId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) SlotStatus status) {
    return ResponseEntity.ok(ApiResponse.success(slotService.getSlots(doctorId, date, status)));
}
```

---

## 7. Auth Header

Write operations (POST, PUT, PATCH, DELETE) must:
1. Accept `@RequestHeader("Authorization") String authHeader`
2. Extract `userId` via `tokenUtils.extractUserId(authHeader)` immediately
3. Pass `userId` to the service — never pass the raw token

```java
@PostMapping
public ResponseEntity<ApiResponse<SlotResponse>> create(
        @RequestHeader("Authorization") String authHeader,
        @Valid @RequestBody SlotRequest request) {
    String userId = tokenUtils.extractUserId(authHeader);
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(HttpStatus.CREATED, "Slot created",
                    slotService.createSlot(request, userId)));
}
```

Read-only `GET` endpoints that return public data do not require the auth header.
Read-only `GET` endpoints that return user-specific data should accept and pass `userId`.

---

## 8. Pagination (when lists grow large)

For endpoints returning lists, support pagination using Spring Data's `Pageable`:

```java
// Controller
@GetMapping
public ResponseEntity<ApiResponse<Page<AppointmentResponse>>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
    return ResponseEntity.ok(ApiResponse.success(appointmentService.list(pageable)));
}
```

Default page size: `20`. Maximum page size: `100` (enforce in service).