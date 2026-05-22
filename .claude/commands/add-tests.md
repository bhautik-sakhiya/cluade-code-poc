# Add or fix missing tests for existing logic

Audit the file or feature provided in $ARGUMENTS and write complete JUnit 5 + Mockito tests for any untested or partially tested logic.

## Steps

1. Read the target source file(s) carefully — understand every method, every branch, every exception path.
2. Check if a corresponding test file already exists under `src/test/`.
3. For each method in the source:
   - Identify every branch (if/else, early return, exception throw)
   - Write one test per branch — happy path + every failure case
4. Create or update the test file in the matching test package.

## Test standards to follow

### Service tests
- `@ExtendWith(MockitoExtension.class)`
- `@Mock` the repository, `@InjectMocks` the service impl
- Use `assertThatThrownBy(...).isInstanceOf(...).hasMessageContaining(...)` for exception assertions
- Use `verify(repo, never()).save(any())` to assert no side effects on failure paths

### Controller tests
- `MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build()`
- `ObjectMapper` with `JavaTimeModule` registered
- Assert `$.status`, `$.success`, `$.message`, `$.data.*` in JSON responses
- Cover: 2xx success, 4xx client errors, correct delegation to service

### Utility / helper tests
- Plain `new ClassName()` — no Spring context
- Cover: valid input, null input, blank input, malformed input, boundary values

### Naming convention
```
methodName_expectedBehavior_whenCondition()
```

## Rules
- Do not modify source files — only add or update test files
- Every test must be independent — no shared mutable state between tests
- `@BeforeEach` for setup of common fixtures only
- Run `./gradlew test` at the end and fix any failures before reporting done