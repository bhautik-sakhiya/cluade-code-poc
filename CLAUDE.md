# CLAUDE.md

This file governs all AI-assisted work in this repository. Every convention here must be followed exactly.

---

## Commands

```bash
# Build
./gradlew build
./gradlew clean build

# Run
./gradlew bootRun

# Test
./gradlew test

# Single test class
./gradlew test --tests "org.poc.claudecodepoc.SomeTestClass"

# Single test method
./gradlew test --tests "org.poc.claudecodepoc.SomeTestClass.someMethod"

# Coverage report (generated automatically after test)
# Open: build/reports/jacoco/test/html/index.html
```

---

## Architecture

Spring Boot 4 / Java 21 REST API backed by JPA + Liquibase. Authentication is handled externally by **Keycloak** — user management is not in this system. The `userId` (Keycloak `sub` claim) is extracted from the Bearer JWT on every write operation via `TokenUtils`.

---

## Package Structure

```
src/main/java/org/poc/claudecodepoc/
├── controller/          # REST layer — no business logic
├── service/             # Interfaces
│   └── impl/            # Implementations — all business logic lives here
├── repository/          # Spring Data JPA interfaces only
├── entity/              # JPA entities
│   └── enums/           # Enums used by entities
├── dto/
│   ├── request/         # Inbound request bodies
│   └── response/        # Outbound response bodies (always wrapped in ApiResponse)
├── exception/           # Custom exceptions + GlobalExceptionHandler
└── util/                # Stateless helpers (e.g. TokenUtils)
```

All new features must follow this exact package layout. Never mix layers.

---

## Layer Rules

### Entity (`entity/`)
- JPA annotations only: `@Entity`, `@Table`, `@Column`, `@Id`, `@GeneratedValue`, `@Enumerated`
- Lombok: `@Data @Builder @NoArgsConstructor @AllArgsConstructor`
- No business logic, no service calls, no DTOs
- Every entity needs a corresponding Liquibase changeset before it is used

### Repository (`repository/`)
- Extend `JpaRepository<Entity, ID>` — nothing else
- Only add methods that are actually called by a service
- No `@Query` unless a derived method name becomes unreadable
- Annotate with `@Repository`

### Service Interface (`service/`)
- One interface per feature (e.g. `SlotService`)
- Method signatures use DTOs, not entities
- No Spring annotations on the interface

### Service Implementation (`service/impl/`)
- `@Service @RequiredArgsConstructor`
- All business logic, validation, ownership checks go here
- Never expose entities outside the service layer — map to response DTOs before returning
- Throw typed exceptions (`BadRequestException`, `ForbiddenException`, `ResourceNotFoundException`) — never return null to signal failure

### Controller (`controller/`)
- `@RestController @RequestMapping("/api/...") @RequiredArgsConstructor`
- Delegate everything to the service — zero business logic
- Extract `userId` from the Authorization header via `TokenUtils` for any write operation
- Always return `ResponseEntity<ApiResponse<T>>`
- Use `@Valid` on all `@RequestBody` parameters

---

## API Response Standard

Every endpoint returns `ApiResponse<T>`. Never return a raw object.

```java
// Success with data
ApiResponse.success(data)                                  // 200, no message
ApiResponse.success(HttpStatus.CREATED, "message", data)  // 201, with message

// Error (used only in GlobalExceptionHandler)
ApiResponse.error(HttpStatus.NOT_FOUND, "message")
```

Response shape:
```json
{ "status": 200, "success": true, "message": "...", "data": { ... } }
{ "status": 404, "success": false, "message": "Slot not found with id: 99" }
```

---

## Exception Handling

All exceptions are handled centrally in `GlobalExceptionHandler`. Throw these from service layer:

| Exception                    | HTTP  | When to use                                      |
|------------------------------|-------|--------------------------------------------------|
| `ResourceNotFoundException`  | 404   | Entity not found by ID                           |
| `BadRequestException`        | 400   | Invalid input, business rule violation           |
| `ForbiddenException`         | 403   | Caller does not own the resource                 |

Never catch and swallow exceptions in service or controller. Never return error state via null or Optional — always throw.

---

## Lombok Usage

Always use Lombok to eliminate boilerplate:

| Scenario              | Annotation(s)                                      |
|-----------------------|----------------------------------------------------|
| Entity / DTO          | `@Data @Builder @NoArgsConstructor @AllArgsConstructor` |
| Service / Controller  | `@RequiredArgsConstructor` (for constructor injection) |
| Request DTO           | `@Data` (setters needed for deserialization)       |
| Response DTO          | `@Data @Builder`                                   |

Never write manual constructors, getters, setters, or builders where Lombok can generate them.

---

## Database & Liquibase

- All schema changes go in `src/main/resources/db/changelog/` as YAML changesets
- Naming convention: `NNN-description.yaml` (e.g. `002-create-appointments-table.yaml`)
- Every new changeset must be referenced in `db.changelog-master.yaml`
- Never use `ddl-auto: create` or `ddl-auto: update` in production config — only `validate`
- Add unique constraints in Liquibase, not just in the entity

---

## Testing Rules

**Every logic change must include a corresponding JUnit 5 test. No exceptions.**

### Service tests (`src/test/.../service/`)
- Use `@ExtendWith(MockitoExtension.class)`
- Mock the repository with `@Mock`, inject with `@InjectMocks`
- Cover: happy path, each exception branch, boundary conditions

### Controller tests (`src/test/.../controller/`)
- Use `MockMvcBuilders.standaloneSetup()` with `GlobalExceptionHandler` set as controller advice
- Cover: correct HTTP status, response body shape (`$.status`, `$.success`, `$.data.*`), error responses
- Register `JavaTimeModule` on `ObjectMapper` for date/time serialization

### Utility tests (`src/test/.../util/`)
- Plain unit tests — no Spring context
- Cover all branches including null/blank/malformed inputs

### Naming convention
```
methodName_expectedBehavior_whenCondition()
// e.g.
createSlot_throwsBadRequest_whenEndTimeNotAfterStartTime()
```

---

## Auth / Keycloak

- User management is **not** in this system — Keycloak owns it
- `userId` = Keycloak `sub` claim extracted from Bearer JWT via `TokenUtils.extractUserId(authHeader)`
- Pass `userId` from controller → service as a plain `String` parameter
- Never store or look up users in our DB — reference them only by their Keycloak ID string
- When Spring Security + Keycloak resource server is configured, `TokenUtils` will be replaced by `@AuthenticationPrincipal` — keep that migration in mind when adding new endpoints

---

## General Coding Rules

- No comments explaining what code does — name things clearly instead
- No TODO comments committed to the repo
- DTOs are immutable where possible — prefer records for response DTOs once the team aligns
- No `System.out.println` — use SLF4J logger if logging is needed
- `application.yaml` is the single config file — no `.properties` files
- No hardcoded strings for status values — always use enums