# Test Standards

These are the mandatory testing standards for this project. Every piece of logic must have a corresponding test. Follow every pattern below exactly.

---

## Core Rules

- Write tests alongside logic — never after. Every PR must include tests for every change.
- One test method = one behaviour. Never assert multiple unrelated things in a single test.
- Tests must be independent — no shared mutable state, no ordering dependency.
- Never use `@SpringBootTest` for service or controller unit tests — it's slow and unnecessary.
- Run `./gradlew test` before marking any task done.

---

## 1. Service Tests

**Location:** `src/test/java/org/poc/claudecodepoc/service/`
**Naming:** `<ServiceImpl>Test.java` (e.g. `SlotServiceImplTest.java`)

### Setup
```java
@ExtendWith(MockitoExtension.class)
class AppointmentServiceImplTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private AppointmentServiceImpl appointmentService;

    private static final String USER_ID = "user-abc";
    private static final String DOCTOR_ID = "doctor-xyz";

    private Appointment savedAppointment;

    @BeforeEach
    void setUp() {
        savedAppointment = Appointment.builder()
                .id(1L)
                .patientId(USER_ID)
                .doctorId(DOCTOR_ID)
                .status(AppointmentStatus.PENDING)
                .createdBy(USER_ID)
                .build();
    }
}
```

### Happy path
```java
@Test
void create_success() {
    when(appointmentRepository.existsByPatientIdAndSlotId(USER_ID, 10L)).thenReturn(false);
    when(appointmentRepository.save(any(Appointment.class))).thenReturn(savedAppointment);

    AppointmentResponse response = appointmentService.create(request, USER_ID);

    assertThat(response.getId()).isEqualTo(1L);
    assertThat(response.getStatus()).isEqualTo(AppointmentStatus.PENDING);
    verify(appointmentRepository).save(any(Appointment.class));
}
```

### Exception paths — one test per throw
```java
@Test
void create_throwsBadRequest_whenDuplicateBooking() {
    when(appointmentRepository.existsByPatientIdAndSlotId(USER_ID, 10L)).thenReturn(true);

    assertThatThrownBy(() -> appointmentService.create(request, USER_ID))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("already have a booking");

    verify(appointmentRepository, never()).save(any());
}

@Test
void cancel_throwsForbidden_whenNotOwner() {
    when(appointmentRepository.findById(1L)).thenReturn(Optional.of(savedAppointment));

    assertThatThrownBy(() -> appointmentService.cancel(1L, "other-user"))
            .isInstanceOf(ForbiddenException.class);

    verify(appointmentRepository, never()).delete(any());
}

@Test
void getById_throwsNotFound_whenMissing() {
    when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> appointmentService.getById(99L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("99");
}
```

### Verify no side effects on failure
Always add `verify(..., never())` on the mutating call when testing an exception path:
```java
verify(appointmentRepository, never()).save(any());
verify(appointmentRepository, never()).delete(any());
```

---

## 2. Controller Tests

**Location:** `src/test/java/org/poc/claudecodepoc/controller/`
**Naming:** `<Controller>Test.java` (e.g. `AppointmentControllerTest.java`)

### Setup — always use standaloneSetup with GlobalExceptionHandler
```java
@ExtendWith(MockitoExtension.class)
class AppointmentControllerTest {

    @Mock private AppointmentService appointmentService;
    @Mock private TokenUtils tokenUtils;

    @InjectMocks private AppointmentController appointmentController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final String AUTH_HEADER = "Bearer fake.jwt.token";
    private static final String USER_ID = "user-abc";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(appointmentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }
}
```

### Assert HTTP status + ApiResponse shape
```java
@Test
void create_returns201() throws Exception {
    when(tokenUtils.extractUserId(AUTH_HEADER)).thenReturn(USER_ID);
    when(appointmentService.create(any(), eq(USER_ID))).thenReturn(appointmentResponse);

    mockMvc.perform(post("/api/appointments")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(1));
}

@Test
void create_returns400_onDuplicateBooking() throws Exception {
    when(tokenUtils.extractUserId(AUTH_HEADER)).thenReturn(USER_ID);
    when(appointmentService.create(any(), eq(USER_ID)))
            .thenThrow(new BadRequestException("already have a booking"));

    mockMvc.perform(post("/api/appointments")
                    .header("Authorization", AUTH_HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("already have a booking"));
}

@Test
void cancel_returns403_whenNotOwner() throws Exception {
    when(tokenUtils.extractUserId(AUTH_HEADER)).thenReturn(USER_ID);
    doThrow(new ForbiddenException("Not allowed")).when(appointmentService).cancel(1L, USER_ID);

    mockMvc.perform(delete("/api/appointments/1")
                    .header("Authorization", AUTH_HEADER))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.success").value(false));
}

@Test
void getById_returns404_whenMissing() throws Exception {
    when(appointmentService.getById(99L))
            .thenThrow(new ResourceNotFoundException("Appointment not found with id: 99"));

    mockMvc.perform(get("/api/appointments/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
}
```

### Required controller test coverage checklist
For every endpoint, write tests for:
- [ ] 2xx success — correct status code, `$.status`, `$.success`, `$.data` shape
- [ ] 4xx client error — `BadRequestException`, `ForbiddenException`, `ResourceNotFoundException`
- [ ] Missing/invalid `Authorization` header (for write endpoints)

---

## 3. Utility Tests

**Location:** `src/test/java/org/poc/claudecodepoc/util/`

No Spring context. Just `new ClassName()`.

```java
class TokenUtilsTest {

    private final TokenUtils tokenUtils = new TokenUtils();

    @Test
    void extractUserId_returnsSubClaim() { ... }

    @Test
    void extractUserId_throwsBadRequest_whenHeaderIsNull() {
        assertThatThrownBy(() -> tokenUtils.extractUserId(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void extractUserId_throwsBadRequest_whenNoBearerPrefix() {
        assertThatThrownBy(() -> tokenUtils.extractUserId("rawtoken"))
                .isInstanceOf(BadRequestException.class);
    }
}
```

Cover: valid input, null, blank, malformed, boundary values.

---

## 4. Test Method Naming

```
methodName_expectedBehavior_whenCondition()
```

Examples:
```
createSlot_success()
createSlot_throwsBadRequest_whenEndTimeNotAfterStartTime()
createSlot_throwsBadRequest_whenDuplicateSlotExists()
updateSlot_throwsForbidden_whenNotOwner()
updateSlot_throwsBadRequest_whenSlotIsBooked()
getSlotById_throwsNotFound_whenMissing()
deleteSlot_success()
```

---

## 5. AssertJ — Preferred Assertion Patterns

```java
// Value assertions
assertThat(response.getId()).isEqualTo(1L);
assertThat(response.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
assertThat(list).hasSize(2);
assertThat(list).isEmpty();

// Exception assertions
assertThatThrownBy(() -> service.method(args))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("keyword");

// Verify interactions
verify(repository).save(any(Entity.class));
verify(repository, never()).save(any());
verify(repository, times(1)).findById(1L);
verifyNoInteractions(repository);
```

Never use JUnit's `assertThrows` — use AssertJ's `assertThatThrownBy` consistently.

---

## 6. What NOT to do

- Do not use `@SpringBootTest` for unit tests
- Do not use `Mockito.mock()` inline — always use `@Mock` / `@InjectMocks`
- Do not share mutable fixture objects between tests without resetting in `@BeforeEach`
- Do not write a single test that covers multiple unrelated behaviours
- Do not leave tests that just call a method without asserting anything
- Do not mock the class under test itself