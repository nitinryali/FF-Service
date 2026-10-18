# Implementation Plan: Flag Service (Spring Boot 3.x / Java 21)

## Overview

This plan covers the Flag Service only. Tasks are scoped to writing and modifying code inside `flag-service/`. No Order Service, CI/CD pipeline, or Render deployment tasks are included. Seven top-level tasks map directly to distinct concerns; each is broken into fine-grained, independently executable sub-tasks.

---

## Tasks

- [x] 1. Project scaffold
  - [x] 1.1 Initialize `flag-service/` Maven project with `pom.xml`
    - Set `spring-boot-starter-parent` to `3.2.x`, `java.version` to `21`, `packaging` to `jar`
    - Add plugin: `spring-boot-maven-plugin` (repackage goal)
    - Add Maven wrapper via `mvn wrapper:wrapper` so the project ships with `./mvnw`
    - _Requirements: 7.1, 7.3_

  - [x] 1.2 Add all runtime and test dependencies to `pom.xml`
    - Runtime: `spring-boot-starter-web`, `spring-boot-starter-data-mongodb`, `spring-boot-starter-security`, `spring-boot-starter-data-redis`, `spring-boot-starter-validation`
    - JWT (pinned): `io.jsonwebtoken:jjwt-api:0.12.6`, `io.jsonwebtoken:jjwt-impl:0.12.6` (runtime scope), `io.jsonwebtoken:jjwt-jackson:0.12.6` (runtime scope)
    - OpenAPI: `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0`
    - Optional: `org.projectlombok:lombok` (provided scope)
    - Test: `spring-boot-starter-test` (includes JUnit 5 + Mockito), `org.testcontainers:mongodb:1.19.x`, `org.testcontainers:junit-jupiter:1.19.x`
    - Property-based: `net.jqwik:jqwik:1.8.4` (test scope)
    - Coverage: `org.jacoco:jacoco-maven-plugin` — bind `prepare-agent` to `initialize` phase, `report` to `verify` phase; add `<rule>` with `<limit><counter>LINE</counter><minimum>0.80</minimum></limit>`; build fails below threshold
    - _Requirements: 7.1, 7.3_

  - [x] 1.3 Create `FlagServiceApplication.java`
    - Annotate with `@SpringBootApplication`
    - `@EnableMongoAuditing` activates `@CreatedDate` / `@LastModifiedDate` population on `Flag`; it is declared in `config/MongoConfig.java` rather than on the application class so that `@WebMvcTest` slices can start without the MongoDB auditing infrastructure
    - _Requirements: 6.1_

  - [x] 1.4 Create all empty package directories matching the project structure
    - Create: `config/`, `auth/`, `flag/`, `flag/dto/`, `redis/`, `exception/`
    - `.gitkeep` placeholders are used only while a directory is empty, and are removed once it contains real classes
    - _Requirements: 7.1_

  - [x] 1.5 Create multi-stage `Dockerfile`
    - Stage 1: `FROM maven:3-eclipse-temurin-21 AS build` — copy `mvnw` + `.mvn/` + `pom.xml` + `src/`, then run `chmod +x mvnw && ./mvnw -B package -DskipTests`
    - The Maven wrapper must be copied into the build stage: the base image provides `mvn`, not `./mvnw`, so omitting it makes the image build fail
    - Stage 2: `FROM eclipse-temurin:21-jre-alpine` — copy `target/*.jar` as `app.jar`, `EXPOSE 8001`, `ENTRYPOINT ["java","-jar","/app/app.jar"]`
    - _Requirements: 7.1, 7.3, 7.4_

  - [x] 1.6 Create `.dockerignore`
    - Exclude: `target/`, `.git/`, `*.md`, `.env`, `**/*.iml`, `.idea/`
    - _Requirements: 7.1_

  - [x] 1.7 Create `README.md`
    - Document the exposed port (`8001`) alongside the `EXPOSE` instruction
    - Include a flow diagram of the request path (client → filter chain → controller → service → MongoDB/Redis)
    - Add collapsible sections covering quick start, the API reference, configuration, and troubleshooting
    - _Requirements: 7.4_

- [x] 2. Configuration for external dependencies
  - [x] 2.1 Create `src/main/resources/application.properties` — the only configuration file
    - `server.port: ${SERVER_PORT:8001}`
    - `jwt.secret: ${JWT_SECRET}` (no default — startup fails fast if missing)
    - `jwt.expiry-seconds: ${JWT_EXPIRY_SECONDS:3600}` (validated to be 60–86400 at startup)
    - `app.admin.username: ${ADMIN_USERNAME:admin}`
    - `app.admin.password: ${ADMIN_PASSWORD:admin}`
    - `spring.data.mongodb.uri: ${MONGODB_URI:mongodb://localhost:27017/ffproject}`
    - `spring.data.mongodb.auto-index-creation: true`
    - `spring.data.redis.url: ${REDIS_URL:redis://localhost:6379}`
    - Use the `spring.data.redis.*` prefix, not the deprecated `spring.redis.*`: Spring Boot 3.2 does not bind the old names and silently falls back to `localhost:6379`
    - `springdoc.api-docs.path: /v3/api-docs`
    - `springdoc.swagger-ui.path: /swagger-ui.html`
    - `logging.level.com.ffproject: INFO`
    - No profile-specific files: the same configuration serves local testing and the cloud deployment, because every environment-specific value is an environment variable with a localhost default
    - Redis TLS needs no dedicated property — Spring Boot enables SSL automatically when `REDIS_URL` uses the `rediss://` scheme
    - _Requirements: 2.1, 2.5, 7.4, 8.6_

  - [x] 2.2 Create `config/RedisConfig.java`
    - Annotate with `@Configuration`
    - Declare `LettuceConnectionFactory` bean: reads `spring.data.redis.*` properties via Spring Boot auto-configuration (do not construct manually — `@Autowired` Spring Boot's `RedisProperties` instead, or rely on auto-configured `LettuceConnectionFactory`)
    - Declare `RedisTemplate<String, String>` bean: set both key serializer and value serializer to `new StringRedisSerializer()`; call `template.setConnectionFactory(connectionFactory)`
    - Add class-level JavaDoc comment explaining that the connection details come from `spring.data.redis.url` (`REDIS_URL`), defaulting to the local Docker container
    - _Requirements: 3.1_

  - [x] 2.3 Create `docker-compose.yml` at the project root
    - Define `mongo:latest` and `redis:latest` containers with healthchecks, so no local installation of either database is required
    - Define the `flag-service` container built from `./flag-service`, and gate its startup with `depends_on` on both databases reporting healthy
    - Declare a shared `ffproject-network` and override the localhost defaults with service-name URLs (`MONGODB_URI: mongodb://mongo:27017/ffproject`, `REDIS_URL: redis://redis:6379`)
    - Include the Order Service block, commented out until `order-service/` exists
    - _Requirements: 7.5, 7.6, 7.7_

- [x] 3. MongoDB connection and Flag document
  - [x] 3.1 Create `flag/Flag.java` — MongoDB document model
    - `@Document(collection = "flags")`
    - Fields: `@Id String id`, `@Indexed(unique = true) @Size(max = 255) String name`, `Boolean value`, `@CreatedDate Instant createdAt`, `@LastModifiedDate Instant updatedAt`
    - Add `@Data` (Lombok) or generate `equals`, `hashCode`, `toString`, all-args constructor, and builder manually if Lombok is not used
    - _Requirements: 6.1_

  - [x] 3.2 Create `flag/FlagRepository.java` — Spring Data MongoDB repository
    - Extend `MongoRepository<Flag, String>`
    - Add: `Optional<Flag> findByName(String name)`, `boolean existsByName(String name)`, `void deleteByName(String name)`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [x] 3.3 Configure MongoDB index creation and startup connectivity
    - Set `spring.data.mongodb.auto-index-creation: true` in `application.properties` — the single config file
    - Cap the driver's server-selection timeout at 5 seconds so that an unreachable MongoDB fails fast with a non-zero exit code instead of hanging for the 30-second default
    - Apply the timeout via a `MongoClientSettingsBuilderCustomizer` bean in `config/MongoConfig.java`, NOT by appending `?serverSelectionTimeoutMS=5000` to the connection URI — a standard MongoDB Atlas URI already carries a query string, so appending fuses onto the last parameter and silently corrupts `w` while dropping the timeout entirely
    - _Requirements: 6.2, 6.3_

- [x] 4. Authentication — JWT issuance and Spring Security
  - [x] 4.1 Create `auth/AuthService.java`
    - Read `app.admin.username` and `app.admin.password` via `@Value` from `application.properties`
    - Read `jwt.secret` and `jwt.expiry-seconds` via `@Value`
    - `authenticate(String username, String password)`:
      - Validate field lengths: username 1–64 chars, password 1–128 chars
      - Compare against configured admin credentials using `String.equals()` (constant-time not required for single-admin demo)
      - Any mismatch (wrong length, wrong value, null): throw `BadCredentialsException("Authentication failed")` — same message for all failure modes, no field hint
    - `generateToken(String username)`:
      - Build key: `Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8))`
      - Use `Jwts.builder().subject(username).issuedAt(now).expiration(now + jwtExpirySeconds).signWith(key, Jwts.SIG.HS256).compact()`
    - Validate `jwt.expiry-seconds` in the constructor against the requirement's 60–86400 second range and fail fast on an out-of-range value rather than issuing tokens with an unusable lifetime
    - _Requirements: 2.1, 2.5, 2.6_

  - [x] 4.2 Create `auth/AuthController.java`
    - `@RestController @RequestMapping("/auth")`
    - Inner static class or separate file `LoginRequest`: `@NotBlank @Size(max=64) String username`, `@NotBlank @Size(max=128) String password`
    - `POST /auth/login` — `@Valid @RequestBody LoginRequest`:
      - Call `authService.authenticate(username, password)`
      - Catch `BadCredentialsException` → `ResponseEntity.status(401).body(Map.of("error","Authentication failed"))`
      - Success → `ResponseEntity.ok(Map.of("token", authService.generateToken(username)))`
    - _Requirements: 2.1, 2.6_

  - [x] 4.3 Create `auth/JwtFilter.java` — `OncePerRequestFilter`
    - Skip (call `filterChain.doFilter` without touching security context) for paths matching `/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`
    - Paths NOT in skip-list:
      - No `Authorization` header or header doesn't start with `"Bearer "` → write 401 JSON `{"error":"Authentication required"}` directly to `HttpServletResponse`, return (do NOT call `filterChain.doFilter`)
      - Extract token, parse with `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)`
      - `ExpiredJwtException` → 401 `{"error":"Token has expired"}`
      - `JwtException` or `IllegalArgumentException` → 401 `{"error":"Invalid token"}`
      - Success → set `UsernamePasswordAuthenticationToken(subject, null, Collections.emptyList())` in `SecurityContextHolder`, call `filterChain.doFilter`
    - _Requirements: 1.6, 2.2, 2.3, 2.4, 2.7_

  - [x] 4.4 Create `config/SecurityConfig.java`
    - `@Configuration @EnableWebSecurity`
    - Inject `JwtFilter` via constructor
    - `SecurityFilterChain` bean: disable CSRF, session creation policy = STATELESS
    - Permit all: `/auth/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`
    - Authenticate everything else (`anyRequest().authenticated()`)
    - Add `JwtFilter` before `UsernamePasswordAuthenticationFilter`
    - Expose a no-op `PasswordEncoder` bean if needed by Spring Security's auto-config
    - _Requirements: 1.6, 2.2, 2.7_

- [x] 5. Flag CRUD API — DTOs, service, controller, exception handling
  - [x] 5.1 Create exception classes
    - `exception/FlagNotFoundException.java` — extends `RuntimeException`, default message `"Flag not found"`
    - `exception/FlagAlreadyExistsException.java` — extends `RuntimeException`, default message `"Flag already exists"`
    - `exception/ServiceUnavailableException.java` — extends `RuntimeException`, default message `"Service unavailable"`
    - _Requirements: 1.7, 1.8, 1.10_

  - [x] 5.2 Create `exception/GlobalExceptionHandler.java`
    - `@RestControllerAdvice`
    - `FlagNotFoundException` → 404 `{"error":"Flag not found"}`
    - `FlagAlreadyExistsException` → 409 `{"error":"Flag already exists"}`
    - `ServiceUnavailableException` → 503 `{"error":"Service unavailable"}` — log the connection error and its cause at ERROR level before responding
    - `MethodArgumentNotValidException` → 400 `{"error":"<first FieldError default message>"}` (use `ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage()`)
    - `Exception` (catch-all) → 500 `{"error":"An unexpected error occurred"}` — no stack trace in body; log at ERROR level
    - _Requirements: 1.7, 1.8, 1.9, 1.10, 6.4_

  - [x] 5.3 Create DTOs in `flag/dto/`
    - `CreateFlagRequest.java`: `@NotBlank @Pattern(regexp="^[a-zA-Z0-9\\-_]{1,100}$", message="Flag name must be 1–100 characters: alphanumeric, hyphens, underscores only") String name`; `@NotNull Boolean value`
    - `UpdateFlagRequest.java`: `@NotNull Boolean value`
    - `FlagResponse.java`: fields `String id`, `String name`, `Boolean value`, `Instant createdAt`, `Instant updatedAt`; static factory `FlagResponse.from(Flag flag)` that maps all fields
    - _Requirements: 1.1, 1.4, 1.9_

  - [x] 5.4 Create `flag/FlagService.java`
    - `@Service`; inject `FlagRepository` and `FlagEventPublisher` via constructor
    - `createFlag(String name, Boolean value)`:
      - Construct `Flag`, call `flagRepository.save()`
      - Catch `DuplicateKeyException` → throw `FlagAlreadyExistsException`
      - Catch `DataAccessException` (other) → throw `ServiceUnavailableException` (do NOT publish)
      - On success: call `flagEventPublisher.publish(name, value, "created")` inside try/catch — log WARN on failure, never rethrow
      - _Requirements: 1.1, 1.7, 1.10, 3.1_
    - `getAllFlags()`:
      - `flagRepository.findAll()`; catch `DataAccessException` → `ServiceUnavailableException`
      - _Requirements: 1.3_
    - `getFlagByName(String name)`:
      - `flagRepository.findByName(name).orElseThrow(FlagNotFoundException::new)`; catch `DataAccessException` → `ServiceUnavailableException`
      - _Requirements: 1.2, 1.8_
    - `updateFlag(String name, Boolean value)`:
      - Find flag (throw `FlagNotFoundException` if absent), set value, save; catch `DataAccessException` → `ServiceUnavailableException`
      - On success: call `flagEventPublisher.publish(name, value, "updated")` in try/catch
      - _Requirements: 1.4, 1.8, 3.1_
    - `deleteFlag(String name)`:
      - Find flag (throw `FlagNotFoundException` if absent), call `flagRepository.deleteByName(name)`; catch `DataAccessException` → `ServiceUnavailableException`
      - On success: call `flagEventPublisher.publish(name, null, "deleted")` in try/catch
      - _Requirements: 1.5, 1.8, 3.1_

  - [x] 5.5 Create `flag/FlagController.java`
    - `@RestController @RequestMapping("/flags")`; inject `FlagService` via constructor
    - `POST /flags` — `@Valid @RequestBody CreateFlagRequest` → `flagService.createFlag(req.getName(), req.getValue())` → `ResponseEntity.status(201).body(FlagResponse.from(flag))`
    - `GET /flags` → `flagService.getAllFlags()` → `ResponseEntity.ok(flags.stream().map(FlagResponse::from).toList())`
    - `GET /flags/{name}` — `@PathVariable String name` → `flagService.getFlagByName(name)` → `ResponseEntity.ok(FlagResponse.from(flag))`
    - `PATCH /flags/{name}` — `@PathVariable`, `@Valid @RequestBody UpdateFlagRequest` → `flagService.updateFlag(name, req.getValue())` → `ResponseEntity.ok(FlagResponse.from(flag))`
    - `DELETE /flags/{name}` — `@PathVariable` → `flagService.deleteFlag(name)` → `ResponseEntity.noContent().build()`
    - No try/catch in controller — all exceptions handled by `GlobalExceptionHandler`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10_

- [x] 6. Redis Pub/Sub publishing
  - [x] 6.1 Create `redis/FlagEventPublisher.java`
    - Inject `RedisTemplate<String, String>` and `ObjectMapper` via constructor
    - `publish(String name, Boolean value, String event)`:
      - Build `Map<String, Object>`: `{"name": name, "value": value, "event": event}` (value is `null` for delete events — `ObjectMapper` serializes `null` fields by default)
      - Serialize to JSON: `objectMapper.writeValueAsString(map)`
      - Call `redisTemplate.convertAndSend("flag-updates", json)`
      - Entire method body inside try/catch(`Exception e`): log `WARN` with message `"Redis publish failed for flag {}: {}"`, never rethrow
    - _Requirements: 3.1_

  - [x] 6.2 Confirm `RedisTemplate` wiring in `config/RedisConfig.java`
    - Verify the `RedisTemplate<String, String>` bean defined in Task 2.2 uses `StringRedisSerializer` for both key serializer (`setKeySerializer`) and value serializer (`setValueSerializer`)
    - Verify `FlagEventPublisher` receives this bean via constructor injection (Spring resolves by type `RedisTemplate<String, String>`)
    - _Requirements: 3.1_

- [x] 7. Swagger / OpenAPI documentation
  - [x] 7.1 Create `config/OpenApiConfig.java`
    - `@Configuration`
    - `OpenAPI` bean: `new OpenAPI().info(new Info().title("Flag Service API").version("1.0.0").description("Feature flag management REST API"))` with global `SecurityRequirement` named `"bearerAuth"`
    - `SecurityScheme` component bean: name `"bearerAuth"`, type `SecuritySchemeType.HTTP`, scheme `"bearer"`, bearerFormat `"JWT"`
    - Use `Components` to register the security scheme: `new OpenAPI().components(new Components().addSecuritySchemes("bearerAuth", scheme))`
    - _Requirements: 7.1_

  - [x] 7.2 Verify SpringDoc config properties in `application.properties` (from Task 2.1)
    - Confirm `springdoc.api-docs.path: /v3/api-docs` and `springdoc.swagger-ui.path: /swagger-ui.html` are present
    - Confirm these paths are in the `permitAll` list inside `SecurityConfig.java` (Task 4.4)
    - _Requirements: 7.1_

  - [x] 7.3 Annotate `AuthController.java` with OpenAPI annotations
    - Add `@Tag(name = "Authentication")`
    - `POST /auth/login`: `@Operation(summary = "Issue a JWT token")` + `@ApiResponses`:
      - `@ApiResponse(responseCode = "200", description = "JWT token returned")`
      - `@ApiResponse(responseCode = "401", description = "Authentication failed")`
    - _Requirements: 2.1_

  - [x] 7.4 Annotate all 5 methods in `FlagController.java` with OpenAPI annotations
    - Add `@Tag(name = "Flags")` on class; add `@SecurityRequirement(name = "bearerAuth")` on class
    - `POST /flags`: `@Operation(summary="Create flag")` + `@ApiResponses`: 201 FlagResponse, 400 invalid name, 401, 409 duplicate, 503 DB down
    - `GET /flags`: `@Operation(summary="List all flags")` + `@ApiResponses`: 200 array, 401
    - `GET /flags/{name}`: `@Operation(summary="Get flag by name")` + `@Parameter(description="Flag name")` + `@ApiResponses`: 200 FlagResponse, 401, 404
    - `PATCH /flags/{name}`: `@Operation(summary="Update flag value")` + `@ApiResponses`: 200 FlagResponse, 401, 404, 503
    - `DELETE /flags/{name}`: `@Operation(summary="Delete flag")` + `@ApiResponses`: 204, 401, 404, 503
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

---

## Notes

- Each task references specific requirements from `requirements.md` for full traceability
- Task 2 (external dependency config) is independent of Task 1 (scaffold) — both can be done in parallel
- `FlagEventPublisher.publish()` must never throw — Redis failure is fire-and-forget; caller's HTTP response is unaffected
- All controller errors are handled exclusively by `GlobalExceptionHandler` — no try/catch inside `FlagController`
- The catch-all `Exception` → 500 handler also absorbs framework exceptions (malformed JSON bodies, unsupported HTTP methods, unmapped URLs). This is intentional and spec-faithful: the design document specifies only the five error mappings, so these cases surface as 500 rather than 400/404/405

---

## Task Dependency Graph

```json
{
  "waves": [
    {
      "id": 0,
      "tasks": ["1.1", "1.2"]
    },
    {
      "id": 1,
      "tasks": ["1.3", "1.4", "1.5", "1.6", "2.1"]
    },
    {
      "id": 2,
      "tasks": ["2.2"]
    },
    {
      "id": 3,
      "tasks": ["2.3", "3.1", "3.2", "3.3"]
    },
    {
      "id": 4,
      "tasks": ["4.1", "5.1"]
    },
    {
      "id": 5,
      "tasks": ["4.2", "4.3", "4.4", "5.2", "5.3"]
    },
    {
      "id": 6,
      "tasks": ["5.4", "6.1"]
    },
    {
      "id": 7,
      "tasks": ["5.5", "6.2"]
    },
    {
      "id": 8,
      "tasks": ["7.1", "7.2"]
    },
    {
      "id": 9,
      "tasks": ["1.7", "7.3", "7.4"]
    }
  ]
}
```
