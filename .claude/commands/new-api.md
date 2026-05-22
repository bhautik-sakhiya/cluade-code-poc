# Scaffold a complete new API feature

Scaffold a complete new API feature for the medical appointment system following the project's layered architecture exactly as defined in CLAUDE.md.

## What to create

Given the feature name provided in $ARGUMENTS, create all of the following files in the correct packages under `src/main/java/org/poc/claudecodepoc/`:

### 1. Entity (`entity/<Name>.java`)
- JPA entity with `@Entity @Table(name = "<table_name>")`
- Fields: `id` (BIGINT, auto-increment), domain fields, `createdBy` (String, Keycloak userId)
- Lombok: `@Data @Builder @NoArgsConstructor @AllArgsConstructor`
- Enums in `entity/enums/` if the entity has status/type fields

### 2. Repository (`repository/<Name>Repository.java`)
- Extends `JpaRepository<Entity, Long>`
- Add only the query methods actually needed by the service
- `@Repository`

### 3. Service interface (`service/<Name>Service.java`)
- CRUD methods: create, getById, list (with sensible filters), update, delete
- All method signatures use DTOs, not entities

### 4. Service implementation (`service/impl/<Name>ServiceImpl.java`)
- `@Service @RequiredArgsConstructor`
- All business logic and validation here
- Ownership check on update/delete: throw `ForbiddenException` if `createdBy` != `userId`
- Throw `ResourceNotFoundException` for missing records
- Throw `BadRequestException` for rule violations
- Map entity → response DTO before returning — never expose the entity

### 5. Request DTO (`dto/request/<Name>Request.java`)
- `@Data` (Lombok)
- `@NotNull` / `@NotBlank` on required fields
- `@FutureOrPresent` on date fields where appropriate

### 6. Response DTO (`dto/response/<Name>Response.java`)
- `@Data @Builder` (Lombok)
- Mirror the entity fields that are safe to expose

### 7. Controller (`controller/<Name>Controller.java`)
- `@RestController @RequestMapping("/api/<plural-name>") @RequiredArgsConstructor`
- Endpoints: POST (create), GET /{id}, GET (list with filters), PUT /{id} (update), DELETE /{id}
- Extract `userId` from `Authorization` header via `TokenUtils` on write operations
- `@Valid` on all `@RequestBody`
- Return `ResponseEntity<ApiResponse<T>>` — use correct HTTP status (201 for create, 200 for others)

### 8. Liquibase changeset (`src/main/resources/db/changelog/NNN-create-<table>-table.yaml`)
- Next sequential number after existing changesets
- Create table with all columns matching the entity
- Add unique constraints where duplicates must be prevented
- Reference the new file in `db.changelog-master.yaml`

### 9. Tests
- `src/test/.../service/<Name>ServiceImplTest.java` — `@ExtendWith(MockitoExtension.class)`, mock the repository, cover every method and every exception branch
- `src/test/.../controller/<Name>ControllerTest.java` — `MockMvcBuilders.standaloneSetup()` with `GlobalExceptionHandler`, cover all endpoints, status codes, and response body fields

## Rules to follow
- Read CLAUDE.md before generating any code
- Follow the exact package layout defined there
- Never put business logic in the controller
- Never expose entities outside the service layer
- Always use `ApiResponse<T>` as the response wrapper
- Run `./gradlew build` at the end and fix any compilation errors before reporting done