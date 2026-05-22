# Coding Standards

These are the mandatory coding standards for this project. Follow every rule below when writing or reviewing any code.

---

## 1. Layer Responsibilities

### Entity
- Only JPA mapping annotations + Lombok. Zero logic.
- Always include `createdBy` (String) to store the Keycloak userId of the creator.
- Enums that belong to an entity live in `entity/enums/`.

```java
@Entity
@Table(name = "appointments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private String patientId;

    @Column(name = "doctor_id", nullable = false)
    private String doctorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Column(name = "created_by", nullable = false)
    private String createdBy;
}
```

### Repository
- Extends `JpaRepository<Entity, Long>` only.
- Add derived query methods only when a service actually calls them.
- Never add methods that are not called anywhere.

```java
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByPatientId(String patientId);
    List<Appointment> findByDoctorIdAndStatus(String doctorId, AppointmentStatus status);
    boolean existsByPatientIdAndSlotId(String patientId, Long slotId);
}
```

### Service Interface
- One interface per domain feature.
- Method signatures accept/return DTOs only — never entities.
- No annotations on the interface itself.

```java
public interface AppointmentService {
    AppointmentResponse create(AppointmentRequest request, String userId);
    AppointmentResponse getById(Long id);
    List<AppointmentResponse> getByPatient(String patientId);
    AppointmentResponse updateStatus(Long id, UpdateStatusRequest request, String userId);
    void cancel(Long id, String userId);
}
```

### Service Implementation
- `@Service @RequiredArgsConstructor` — constructor injection via Lombok.
- All business logic, validation, and ownership checks here.
- Map entity → DTO before returning. Never return a raw entity.
- Always throw typed exceptions. Never return null to signal failure.

```java
@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;

    @Override
    public AppointmentResponse create(AppointmentRequest request, String userId) {
        if (appointmentRepository.existsByPatientIdAndSlotId(userId, request.getSlotId())) {
            throw new BadRequestException("You already have a booking for this slot");
        }
        Appointment saved = appointmentRepository.save(
            Appointment.builder()
                .patientId(userId)
                .doctorId(request.getDoctorId())
                .slotId(request.getSlotId())
                .createdBy(userId)
                .build()
        );
        return toResponse(saved);
    }

    private Appointment findOrThrow(Long id) {
        return appointmentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with id: " + id));
    }

    private AppointmentResponse toResponse(Appointment a) {
        return AppointmentResponse.builder()
            .id(a.getId())
            .patientId(a.getPatientId())
            .doctorId(a.getDoctorId())
            .status(a.getStatus())
            .build();
    }
}
```

### Controller
- `@RestController @RequestMapping("/api/...") @RequiredArgsConstructor`
- Zero business logic — delegate everything to the service.
- Extract `userId` from `Authorization` header on every write operation.
- Always return `ResponseEntity<ApiResponse<T>>`.
- Annotate every `@RequestBody` with `@Valid`.

```java
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final TokenUtils tokenUtils;

    @PostMapping
    public ResponseEntity<ApiResponse<AppointmentResponse>> create(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody AppointmentRequest request) {
        String userId = tokenUtils.extractUserId(authHeader);
        AppointmentResponse response = appointmentService.create(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "Appointment created", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(appointmentService.getById(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        String userId = tokenUtils.extractUserId(authHeader);
        appointmentService.cancel(id, userId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Appointment cancelled", null));
    }
}
```

---

## 2. DTO Rules

### Request DTO
- `@Data` (needs setters for Jackson deserialization)
- `@NotNull` / `@NotBlank` on every required field
- `@FutureOrPresent` on date fields
- No entity references — primitive types and enums only

```java
@Data
public class AppointmentRequest {

    @NotNull
    private String doctorId;

    @NotNull
    private Long slotId;

    @NotBlank
    private String reason;
}
```

### Response DTO
- `@Data @Builder`
- Expose only what the client needs — never copy-paste the entire entity
- Never include sensitive internal fields

```java
@Data
@Builder
public class AppointmentResponse {
    private Long id;
    private String patientId;
    private String doctorId;
    private AppointmentStatus status;
}
```

---

## 3. ApiResponse Wrapper

Every endpoint must return `ApiResponse<T>`. Never return a raw object.

```java
// 200 — data only
ApiResponse.success(data)

// 2xx — with message and status
ApiResponse.success(HttpStatus.CREATED, "Resource created", data)
ApiResponse.success(HttpStatus.OK, "Resource updated", data)

// Error — only used inside GlobalExceptionHandler
ApiResponse.error(HttpStatus.NOT_FOUND, "Not found message")
```

Response shape:
```json
{ "status": 201, "success": true, "message": "Appointment created", "data": { ... } }
{ "status": 400, "success": false, "message": "You already have a booking for this slot" }
```

---

## 4. Exception Rules

Throw from the service layer. Never catch and re-wrap exceptions inside the same layer.

| Exception                   | When to throw                                        |
|-----------------------------|------------------------------------------------------|
| `ResourceNotFoundException` | Record not found by ID                               |
| `BadRequestException`       | Business rule violation, invalid state transition    |
| `ForbiddenException`        | Caller is not the owner of the resource              |

`GlobalExceptionHandler` catches all of these and converts them to the correct HTTP status + `ApiResponse`.

---

## 5. Lombok Rules

| Class type             | Annotations required                                         |
|------------------------|--------------------------------------------------------------|
| Entity                 | `@Data @Builder @NoArgsConstructor @AllArgsConstructor`      |
| Request DTO            | `@Data`                                                      |
| Response DTO           | `@Data @Builder`                                             |
| Service / Controller   | `@RequiredArgsConstructor`                                   |

- Never write manual constructors, getters, setters, or builders.
- Use `@Builder.Default` for fields with a default value (e.g. status enums).

---

## 6. Liquibase Rules

- Every new entity needs a changeset **before** the entity is used.
- File naming: `NNN-create-<table>-table.yaml` (sequential, zero-padded).
- Always register the new file in `db.changelog-master.yaml`.
- Define unique constraints in Liquibase, not just as `@Column(unique = true)`.
- Never use `ddl-auto: create` or `update` — only `validate`.

---

## 7. Auth / Keycloak

- User management is entirely in Keycloak — no User entity or User table.
- `userId` = the Keycloak `sub` claim, extracted via `TokenUtils.extractUserId(authHeader)`.
- Pass `userId` as a plain `String` from controller → service.
- Use `userId` for `createdBy` fields and ownership checks.

---

## 8. General Rules

- No comments explaining what code does — use clear names instead.
- No `System.out.println` — use SLF4J if logging is needed.
- No hardcoded strings for status/type values — always use enums.
- No `Optional` returned from service methods — throw instead.
- `application.yaml` only — no `.properties` files.
- No logic in `main()` or `@SpringBootApplication` class.