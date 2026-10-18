# Design Document — FF Project Full-Stack Feature Flag Platform

## Overview

The FF Project is a production-grade **Feature Flag platform** built on a microservices architecture. It provides a REST API for creating and managing boolean feature flags, propagates flag changes to consumer services in real time using Redis Pub/Sub, and demonstrates flag-controlled behavior changes through a reference Order Service.

### Goals

- Zero-downtime flag toggling — consumers receive updates in under 1 second without polling.
- Stateless, token-authenticated REST API with JWT (no session state on the server).
- Resilient consumer design — the Order Service continues serving requests even when the Flag Service or Redis is temporarily unavailable.
- Fully containerized, CI/CD-deployed workload with no manual deployment steps.
- MongoDB Atlas as the persistent store for flag data, using Mongoose for schema enforcement (Order Service) and Spring Data MongoDB for the Flag Service.
- **Spring Boot (Java)** for the Flag Service with Spring Data MongoDB, Spring Security for JWT, and SpringDoc OpenAPI (Swagger UI).

### Non-Goals

- No frontend dashboard (explicitly excluded from scope).
- No multi-tenancy or per-user flag targeting.
- No gradual/percentage rollouts — flags are strictly boolean.

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Developer / API Client                                          │
│  (curl, Postman, CI scripts)                                     │
└───────────────────┬─────────────────────────────────────────────┘
                    │  HTTPS  (JWT Bearer)
                    ▼
┌───────────────────────────────────┐
│         Flag Service              │  :8001  (Render)
│  ┌────────────┐  ┌─────────────┐  │
│  │ Auth Router│  │ Flags Router│  │
│  └──────┬─────┘  └──────┬──────┘  │
│         │               │         │
│         └──────┬────────┘         │
│                │                  │
│  ┌─────────────▼──────────────┐   │
│  │      Service Layer         │   │
│  └──────┬──────────────┬──────┘   │
│         │              │          │
│  ┌──────▼──────┐  ┌────▼──────┐   │
│  │  MongoDB    │  │  Redis    │   │
│  │ Repository  │  │ Publisher │   │
│  └──────┬──────┘  └────┬──────┘   │
└─────────┼───────────────┼─────────┘
          │               │
    MongoDB Atlas      Upstash Redis
    (persistent store) (Pub/Sub channel)
                          │
                    ┌─────▼───────────────────────────┐
                    │         Order Service            │  :8002  (Render)
                    │  ┌───────────────────────────┐   │
                    │  │   Redis Subscriber         │   │
                    │  └─────────────┬─────────────┘   │
                    │                │                  │
                    │  ┌─────────────▼─────────────┐   │
                    │  │       Flag Cache           │   │
                    │  │  (in-memory Map<str,bool>) │   │
                    │  └─────────────┬─────────────┘   │
                    │                │                  │
                    │  ┌─────────────▼─────────────┐   │
                    │  │     Orders Router          │   │
                    │  │  (flag-controlled logic)   │   │
                    │  └───────────────────────────┘   │
                    └──────────────────────────────────┘
```

---

## Architecture

### Service Responsibilities

| Service           | Responsibility                                                                                                                                               |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Flag Service**  | CRUD for flags, JWT auth (Spring Security), MongoDB Atlas persistence (Spring Data MongoDB), Redis Pub/Sub publish (Spring Data Redis), SpringDoc Swagger UI |
| **Order Service** | Flag cache consumer, order processing with flag-controlled logic                                                                                             |

### Communication Patterns

1. **REST (sync)** — API clients ↔ Flag Service (flag management and auth).
2. **REST (sync, startup-only)** — Order Service → Flag Service (initial cache hydration on boot).
3. **Redis Pub/Sub (async)** — Flag Service → Redis → Order Service (real-time flag change propagation).

### Deployment Topology

```
GitHub (source) → GitHub Actions CI/CD
  ├── Docker build (Flag Service) (Maven wrapper, JAR-based image) → Render Web Service (:8001)
  └── Docker build (Order Service) → Render Web Service (:8002)

External services (managed):
  ├── MongoDB Atlas    (connected by Flag Service only)
  └── Upstash Redis    (connected by both services)

Local development:
  ├── mongo:latest     (Docker image — used when MONGODB_URI is left at its default)
  └── redis:latest     (Docker image — used when REDIS_URL is left at its default)
```

### Environment Variables

**Flag Service — Environment Variables**

There is no profile to select: the same variables are read locally and in the
cloud. Anything left unset falls back to a localhost default.

| Variable                 | Description                                                                 |
| ------------------------ | --------------------------------------------------------------------------- |
| `JWT_SECRET`             | HS256 secret for signing/verifying JWTs (32+ bytes; no default — fails fast) |
| `JWT_EXPIRY_SECONDS`     | Token lifetime in seconds (60–86400, default 3600)                          |
| `SERVER_PORT`            | Listening port (default `8001`)                                             |
| `MONGODB_URI`            | MongoDB connection string (default `mongodb://localhost:27017/ffproject`)   |
| `REDIS_URL`              | Redis connection URL (default `redis://localhost:6379`; use `rediss://` for TLS) |
| `ADMIN_USERNAME`         | Single admin account username (default `admin`)                             |
| `ADMIN_PASSWORD`         | Single admin account password (default `admin`)                             |

The defaults point at the Docker containers from `docker-compose.yml` on
localhost, so local development needs only `JWT_SECRET`. Exporting `MONGODB_URI`
and `REDIS_URL` points the same configuration at MongoDB Atlas and Upstash Redis
— locally or on Render.

**Order Service**
| Variable | Description |
|----------|-------------|
| `REDIS_URL` | Upstash Redis connection string |
| `FLAG_SERVICE_URL` | Base URL of the Flag Service REST API |
| `FLAG_SERVICE_TOKEN` | Pre-issued JWT for startup fetch |
| `PORT` | Listening port (default `8002`) |

---

## Components and Interfaces

### Flag Service — Internal Component Map

```
flag-service/
├── pom.xml                          # Maven build — Spring Boot 3.x parent
├── Dockerfile                       # Multi-stage: maven:3-eclipse-temurin-21 → eclipse-temurin:21-jre (glibc, not -alpine)
├── .dockerignore
└── src/
    └── main/
        ├── java/com/ffproject/flagservice/
        │   ├── FlagServiceApplication.java     # @SpringBootApplication entry point
        │   ├── config/
        │   │   ├── SecurityConfig.java          # Spring Security — stateless JWT filter chain
        │   │   ├── RedisConfig.java             # RedisConnectionFactory + RedisTemplate<String,String>
        │   │   └── OpenApiConfig.java           # SpringDoc OpenAPI metadata
        │   ├── auth/
        │   │   ├── AuthController.java          # POST /auth/login
        │   │   ├── AuthService.java             # Credential validation + JWT signing
        │   │   └── JwtFilter.java               # OncePerRequestFilter — JWT verification
        │   ├── flag/
        │   │   ├── Flag.java                    # @Document("flags") MongoDB document
        │   │   ├── FlagRepository.java          # MongoRepository<Flag, String>
        │   │   ├── FlagService.java             # Business logic + Redis publish
        │   │   ├── FlagController.java          # @RestController /flags CRUD
        │   │   └── dto/
        │   │       ├── CreateFlagRequest.java   # Validated DTO for POST /flags
        │   │       ├── UpdateFlagRequest.java   # Validated DTO for PATCH /flags/:name
        │   │       └── FlagResponse.java        # Outbound response shape
        │   ├── redis/
        │   │   └── FlagEventPublisher.java      # RedisTemplate.convertAndSend("flag-updates", payload)
        │   └── exception/
        │       ├── GlobalExceptionHandler.java  # @RestControllerAdvice
        │       ├── FlagNotFoundException.java
        │       ├── FlagAlreadyExistsException.java
        │       └── ServiceUnavailableException.java
        └── resources/
            └── application.properties          # The only config file — every environment value is an env var
    └── test/
        └── java/com/ffproject/flagservice/
            ├── flag/
            │   ├── FlagControllerTest.java      # @WebMvcTest — unit tests for CRUD endpoints
            │   ├── FlagServiceTest.java         # @ExtendWith(MockitoExtension) — service unit tests
            │   └── FlagPropertyTest.java        # jqwik property-based tests (Properties 1–8)
            ├── auth/
            │   ├── AuthControllerTest.java      # @WebMvcTest — login endpoint tests
            │   └── AuthPropertyTest.java        # jqwik property-based tests (Properties 9–10)
            └── integration/
                └── FlagIntegrationTest.java     # @SpringBootTest + Testcontainers (Mongo + Redis)
```

### Key Dependencies (pom.xml)

```xml
<!-- Spring Boot Parent -->
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.2.x</version>
</parent>

<dependencies>
  <!-- Web -->
  <dependency>spring-boot-starter-web</dependency>
  <!-- MongoDB -->
  <dependency>spring-boot-starter-data-mongodb</dependency>
  <!-- Security (JWT filter) -->
  <dependency>spring-boot-starter-security</dependency>
  <!-- Redis -->
  <dependency>spring-boot-starter-data-redis</dependency>
  <!-- Validation -->
  <dependency>spring-boot-starter-validation</dependency>
  <!-- JWT library -->
  <dependency>io.jsonwebtoken:jjwt-api:0.12.x</dependency>
  <dependency>io.jsonwebtoken:jjwt-impl:0.12.x</dependency>
  <dependency>io.jsonwebtoken:jjwt-jackson:0.12.x</dependency>
  <!-- OpenAPI / Swagger -->
  <dependency>org.springdoc:springdoc-openapi-starter-webmvc-ui:2.x</dependency>
  <!-- Test -->
  <dependency>spring-boot-starter-test</dependency>
  <dependency>org.testcontainers:mongodb</dependency>
  <dependency>org.testcontainers:junit-jupiter</dependency>
  <!-- Property-based testing -->
  <dependency>net.jqwik:jqwik:1.8.x</dependency>
</dependencies>
```

### Configuration (One File, Same for Local and Cloud)

The Flag Service has a single configuration file. There are no profile-specific
files and no `SPRING_PROFILES_ACTIVE` to set: the same configuration is used for
local testing and for the cloud deployment, and the only thing that changes is
where the two connection URLs point.

Every environment-specific value is read from an environment variable, and each
one has a localhost default — which is where the containers from
`docker-compose.yml` listen. The service therefore starts during local
development with only `JWT_SECRET` set, and pointing it at MongoDB Atlas and
Upstash Redis means exporting two more variables.

#### application.properties (the only configuration file)

```properties
server.port=${SERVER_PORT:8001}

jwt.secret=${JWT_SECRET}
jwt.expiry-seconds=${JWT_EXPIRY_SECONDS:3600}
app.admin.username=${ADMIN_USERNAME:admin}
app.admin.password=${ADMIN_PASSWORD:admin}

spring.data.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/ffproject}
spring.data.mongodb.auto-index-creation=true

spring.data.redis.url=${REDIS_URL:redis://localhost:6379}

springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.path=/swagger-ui.html

logging.level.com.ffproject=INFO
```

Redis TLS needs no dedicated property: Spring Boot enables SSL automatically
when `REDIS_URL` uses the `rediss://` scheme, so there is no separate flag that
can drift out of sync between environments.

> Include the database name in `MONGODB_URI` (the `/ffproject` above). Without it
> the driver falls back to a database named `test`.

#### How to activate a configuration

```bash
# Local development — docker-compose.yml provides MongoDB and Redis on localhost,
# which is where the defaults already point.
export JWT_SECRET="<32+ byte random string>"
./mvnw spring-boot:run

# MongoDB Atlas + Upstash Redis — the same variables locally and in the cloud.
export JWT_SECRET="<32+ byte random string>"
export MONGODB_URI="mongodb+srv://user:pass@cluster0.xxxxx.mongodb.net/ffproject?retryWrites=true&w=majority"
export REDIS_URL="rediss://default:password@host:port"
./mvnw spring-boot:run

# On Render, set exactly the same three variables as encrypted env vars.
```

---

### Order Service — Internal Component Map

```
order-service/
├── src/
│   ├── app.js              # Express app factory
│   ├── server.js           # Entry point
│   ├── config.js           # Env var validation
│   ├── cache/
│   │   └── flagCache.js    # In-memory Map, thread-safe read/write
│   ├── redis/
│   │   └── subscriber.js   # Upstash Redis subscribe + reconnect logic
│   ├── startup/
│   │   └── hydrate.js      # Initial fetch from Flag Service with retry
│   ├── middleware/
│   │   └── errorHandler.js
│   └── routes/
│       └── orders.js       # POST /orders, GET /orders/flags
├── Dockerfile
├── .dockerignore
└── package.json
```

### REST API Contract — Flag Service

#### Authentication

```
POST /auth/login
Content-Type: application/json

Request body:
{
  "username": string,   // 1–64 chars
  "password": string    // 1–128 chars
}

Response 200:
{
  "token": string       // signed JWT
}
Response 401:
{
  "error": "Authentication failed"
}
```

#### Flags CRUD

All flag endpoints require `Authorization: Bearer <token>`.

```
POST /flags
Request:  { "name": string, "value": boolean }
Response 201: { "id": string, "name": string, "value": boolean, "createdAt": string, "updatedAt": string }
Response 400: { "error": string }   // naming constraint violated
Response 401: { "error": string }
Response 409: { "error": "Flag already exists" }

GET /flags
Response 200: [ { "id": string, "name": string, "value": boolean, "createdAt": string, "updatedAt": string }, ... ]

GET /flags/:name
Response 200: { "id": string, "name": string, "value": boolean, "createdAt": string, "updatedAt": string }
Response 404: { "error": "Flag not found" }

PATCH /flags/:name
Request:  { "value": boolean }
Response 200: { "id": string, "name": string, "value": boolean, "createdAt": string, "updatedAt": string }
Response 404: { "error": "Flag not found" }

DELETE /flags/:name
Response 204: (no body)
Response 404: { "error": "Flag not found" }
```

#### Error responses (common)

| HTTP Status | Condition                     |
| ----------- | ----------------------------- |
| 400         | Invalid flag name or body     |
| 401         | Missing/invalid/expired JWT   |
| 404         | Flag not found                |
| 409         | Flag name collision on create |
| 503         | Database unavailable          |

### REST API Contract — Order Service

```
POST /orders
Request:  { "item": string, "quantity": number }  // minimal demo payload
Response 201:
{
  "order_id": string,     // UUID v4
  "status": "created",
  "flow": "new" | "legacy"
}

GET /orders/flags
Response 200:
{
  "<flag-name>": boolean,
  ...
}
```

### Redis Message Schema

Channel name: `flag-updates`

```json
{
  "name":  "string (≤ 256 chars)",
  "value": "boolean | null",
  "event": "created" | "updated" | "deleted"
}
```

- `value` is `null` for `deleted` events.
- Messages are serialized as JSON strings before publishing.

---

## Data Models

### MongoDB — `flags` collection

```java
// flag-service/src/main/java/com/ffproject/flagservice/flag/Flag.java
@Document(collection = "flags")
public class Flag {

    @Id
    private String id;                    // MongoDB ObjectId as String

    @Indexed(unique = true)
    @Size(max = 255)
    private String name;

    private Boolean value;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    // getters / setters or use Lombok @Data
}
```

Note: `@EnableMongoAuditing` must be set on a `@Configuration` class to activate `@CreatedDate` / `@LastModifiedDate`. It is declared in `config/MongoConfig.java` rather than on the `@SpringBootApplication` class, so that `@WebMvcTest` slices can start without the MongoDB auditing infrastructure.

### In-Memory Flag Cache (Order Service)

```typescript
// Conceptual type — implemented as a plain Map in JavaScript
type FlagCache = Map<string, boolean>;
// key:   flag name  (non-empty string)
// value: flag state (boolean)
```

The cache is read and written only through `flagCache.js`, which exposes:

```js
get(name); // returns boolean | undefined
set(name, value); // upsert
delete name; // remove on `deleted` event
getAll(); // returns plain object copy for /orders/flags
```

### JWT Payload

```json
{
  "sub": "username",
  "iat": 1700000000,
  "exp": 1700086400
}
```

---

## Correctness Properties

_A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees._

### Property 1: Flag creation round-trip

_For any_ valid flag name (1–100 characters, matching `[a-zA-Z0-9\-_]+`) and any boolean value, creating the flag via POST and then retrieving it via GET SHALL return a record with the same name and boolean value that were submitted, along with `id`, `createdAt`, and `updatedAt` fields.

**Validates: Requirements 1.1, 1.2, 6.1**

---

### Property 2: GET all returns exactly the created set

_For any_ non-empty list of valid flags created in sequence, GET /flags SHALL return a list containing each of those flags with correct names and values — and no additional flags beyond those created.

**Validates: Requirements 1.3**

---

### Property 3: Flag update persists and publishes correct message

_For any_ existing flag and any new boolean value, a PATCH request SHALL both update the stored value in the database (verifiable by subsequent GET) and cause the Redis publisher to have been called with a message containing the correct name, updated value, and `"event": "updated"`.

**Validates: Requirements 1.4, 3.1**

---

### Property 4: Flag delete removes record and publishes deletion event

_For any_ existing flag, a DELETE request SHALL remove the flag such that subsequent GET returns 404, and SHALL cause the Redis publisher to have been called with a message containing the flag name, `"value": null`, and `"event": "deleted"`.

**Validates: Requirements 1.5, 3.1**

---

### Property 5: All flag operations without a valid JWT return 401

_For any_ HTTP request to any flag management endpoint (POST, GET, PATCH, DELETE on `/flags/*`) sent without a valid JWT — whether the Authorization header is absent, non-Bearer, expired, or malformed — the Flag Service SHALL return HTTP 401 and the database state SHALL remain unchanged.

**Validates: Requirements 1.6, 2.2, 2.3, 2.4, 2.7**

---

### Property 6: Duplicate flag name always yields 409

_For any_ valid flag name, after a flag with that name has been successfully created, any subsequent POST with the same name SHALL return HTTP 409 and the original flag's value SHALL remain unchanged.

**Validates: Requirements 1.7**

---

### Property 7: Invalid flag names are always rejected with 400

_For any_ string that violates the naming constraints — empty, longer than 100 characters, or containing any character outside `[a-zA-Z0-9\-_]` — a POST or PATCH request SHALL return HTTP 400 with an error message, and no flag SHALL be created or modified.

**Validates: Requirements 1.9**

---

### Property 8: Database failure yields 503 with no Redis side-effect

_For any_ flag management operation, when the database layer throws an error (simulated via `Mockito.mock()` / `@MockBean`), the Flag Service SHALL return HTTP 503 and the Redis publisher SHALL NOT be called for that operation.

**Validates: Requirements 1.10, 6.4**

---

### Property 9: JWT lifetime equals the configured expiry seconds

_For any_ value of `JWT_EXPIRY_SECONDS` in the valid range [60, 86400], the Auth Service SHALL issue a JWT whose lifetime (`exp − iat` in seconds) equals exactly that configured value. Verified using jqwik with `@ForAll @IntRange(min=60, max=86400)`.

**Validates: Requirements 2.1**

---

### Property 10: Auth failure response is always the same generic message

_For any_ combination of username and password that fails authentication — including missing fields, empty strings, or wrong credentials — the Auth Service SHALL return HTTP 401 with a generic error message that does not identify which field was incorrect. Verified using jqwik with generated `@ForAll String` inputs.

**Validates: Requirements 2.6**

---

### Property 11: Redis cache update handles all valid event types correctly

_For any_ valid Redis message with event type `created` or `updated`, the Flag Cache SHALL contain an entry for the message's flag name with the message's boolean value after processing. _For any_ valid Redis message with event type `deleted`, the Flag Cache SHALL contain no entry for that flag name after processing.

**Validates: Requirements 3.3, 3.4**

---

### Property 12: Malformed Redis messages leave cache unchanged

_For any_ string that is not a valid flag-update message — including non-JSON strings, JSON objects missing required fields (`name`, `value`, `event`), or objects with an unrecognized event type — the Flag Cache state SHALL be byte-for-byte identical before and after processing that message.

**Validates: Requirements 3.5**

---

### Property 13: Exponential backoff delay is correctly bounded

_For any_ reconnection attempt number N ≥ 0, the computed delay SHALL equal `min(1000 × 2^N, 30000)` milliseconds, ensuring the delay never exceeds 30 seconds and doubles correctly on each attempt.

**Validates: Requirements 3.6**

---

### Property 14: Startup cache hydration reflects the full flag set

_For any_ list of flags returned by a mocked Flag Service during startup, after cache hydration completes the Flag Cache SHALL contain exactly those flag entries — same names, same boolean values — with no entries omitted and no extra entries added.

**Validates: Requirements 4.1, 4.3, 4.5**

---

### Property 15: Order flow field reflects flag cache state

_For any_ POST to `/orders`, the `"flow"` field in the response SHALL be `"new"` when the `new-checkout-flow` flag is `true` in the Flag Cache, and SHALL be `"legacy"` when that flag is `false`, absent, or the cache is unavailable.

**Validates: Requirements 5.1, 5.2, 5.5**

---

### Property 16: Order response always contains all required fields

_For any_ valid POST `/orders` request and any Flag Cache state, the response body SHALL always contain `order_id`, `status`, and `flow` fields with non-null values.

**Validates: Requirements 5.6**

---

### Property 17: Order processing never makes outbound HTTP calls

_For any_ POST `/orders` request, the Order Service SHALL resolve the flag value exclusively from the local Flag Cache and SHALL NOT invoke any HTTP client method during the processing of that request.

**Validates: Requirements 5.3**

---

## Error Handling

### Flag Service

| Failure Scenario                        | Behavior                                                                                                                                                                                                           |
| --------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Invalid JWT (missing/malformed/expired) | 401 with descriptive error message; no flag data modified                                                                                                                                                          |
| Flag not found (GET/PATCH/DELETE)       | 404; no side effects                                                                                                                                                                                               |
| Duplicate flag name (POST)              | 409; no flag created                                                                                                                                                                                               |
| Invalid flag name format                | 400 with message naming the violated rule                                                                                                                                                                          |
| MongoDB unavailable at startup          | Log error, exit with non-zero code                                                                                                                                                                                 |
| MongoDB unavailable at runtime          | 503; do NOT publish to Redis                                                                                                                                                                                       |
| Redis publish failure                   | Log error; still return success for DB write (flag is persisted) — note: this is a partial-failure trade-off documented here explicitly. Alternative would be a saga/outbox pattern; deferred to future iteration. |

### Order Service

| Failure Scenario                                         | Behavior                                                                                 |
| -------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| Flag Service unreachable at startup                      | Retry up to 5 times at 5s intervals; start with empty cache after exhausted; log warning |
| Flag Service returns error during initial fetch          | Same retry logic as above                                                                |
| Malformed Redis message received                         | Discard message, retain existing cache state, log warning                                |
| Redis connection lost                                    | Attempt reconnect with exponential backoff: 1s → 2s → 4s → ... → 30s max                 |
| Redis not reconnected within 5 minutes                   | Log error; continue serving with stale cache                                             |
| Flag Cache unavailable/corrupted during order processing | Default to `"flow": "legacy"`                                                            |

### Exponential Backoff Algorithm (Redis Reconnect)

```js
let attempt = 0;
const BASE_DELAY_MS = 1000;
const MAX_DELAY_MS = 30_000;
const GIVE_UP_MS = 5 * 60 * 1000; // 5 minutes
const startedAt = Date.now();

async function reconnectWithBackoff() {
  while (true) {
    const elapsed = Date.now() - startedAt;
    if (elapsed >= GIVE_UP_MS) {
      logger.error("Redis connection could not be restored after 5 minutes");
      return;
    }
    const delay = Math.min(BASE_DELAY_MS * 2 ** attempt, MAX_DELAY_MS);
    await sleep(delay);
    attempt++;
    try {
      await redisClient.connect();
      await redisClient.subscribe("flag-updates", onMessage);
      logger.info("Redis reconnected successfully");
      return;
    } catch (err) {
      logger.warn(`Redis reconnect attempt ${attempt} failed: ${err.message}`);
    }
  }
}
```

---

## Testing Strategy

### Framework Overview

| Layer                   | Tool                                      | Purpose                                                      |
| ----------------------- | ----------------------------------------- | ------------------------------------------------------------ |
| Unit tests (controller) | `@WebMvcTest` + MockMvc + `@MockBean`     | Test request/response mapping, validation, HTTP status codes |
| Unit tests (service)    | `@ExtendWith(MockitoExtension)` + Mockito | Test business logic, error paths, Redis publish calls        |
| Property-based tests    | **jqwik** (`net.jqwik:jqwik`)             | Verify all 10 correctness properties with generated inputs   |
| Integration tests       | `@SpringBootTest` + **Testcontainers**    | Full stack with real MongoDB and Redis containers            |

### Property-Based Testing with jqwik

**jqwik** is the Java property-based testing library used for all 17 correctness properties. It integrates with JUnit 5 and generates randomized inputs automatically.

- Minimum **100 tries** per property (`@Property(tries = 100)`)
- Each property annotated with: `@Label("Feature: ff-project-fullstack-app, Property N: <text>")`

### Unit Tests

**Controller layer** (`@WebMvcTest`):

- All 5 flag endpoints: valid requests → correct status + body shape
- Auth middleware: missing header → 401, expired token → 401 with expiry message, malformed → 401 invalid message
- Validation: bad flag name → 400 with error message

**Service layer** (`@ExtendWith(MockitoExtension)`):

- `createFlag`: success, duplicate (MongoWriteException code 11000 → `FlagAlreadyExistsException`), DB error → `ServiceUnavailableException`
- `getAllFlags`, `getFlagByName`, `updateFlag`, `deleteFlag`: success and not-found paths
- Redis publish: verify `FlagEventPublisher.publish()` called on create/update/delete; NOT called when DB throws

### Property-Based Tests (jqwik)

| Property                                       | Test class                                    | jqwik arbitraries                                                            |
| ---------------------------------------------- | --------------------------------------------- | ---------------------------------------------------------------------------- |
| 1 — Flag creation round-trip                   | `FlagPropertyTest`                            | `@ForAll @StringArb` valid name, `@ForAll boolean`                           |
| 2 — GET all returns created set                | `FlagPropertyTest`                            | `@ForAll List<@StringArb validName>`                                         |
| 3 — Update persists and publishes              | `FlagPropertyTest`                            | valid name, `@ForAll boolean`                                                |
| 4 — Delete removes and publishes               | `FlagPropertyTest`                            | valid name                                                                   |
| 5 — All ops without JWT return 401             | `AuthPropertyTest`                            | expired/missing/malformed token generators                                   |
| 6 — Duplicate name yields 409                  | `FlagPropertyTest`                            | valid name                                                                   |
| 7 — Invalid names yield 400                    | `FlagPropertyTest`                            | empty string, >100 chars, bad chars                                          |
| 8 — DB failure → 503, no Redis                 | `FlagPropertyTest`                            | any flag op, DB-throws mock                                                  |
| 9 — JWT lifetime equals config                 | `AuthPropertyTest`                            | `@ForAll @IntRange(min=60, max=86400)`                                       |
| 10 — Auth failure is generic message           | `AuthPropertyTest`                            | `@ForAll String username`, `@ForAll String password`                         |
| 11 — Redis messages update cache correctly     | `order-service/subscriber.properties.test.js` | valid name, `fc.boolean()`, `fc.constantFrom('created','updated','deleted')` |
| 12 — Malformed messages leave cache unchanged  | `order-service/subscriber.properties.test.js` | `fc.string()`, malformed JSON generators                                     |
| 13 — Backoff delay is correctly bounded        | `order-service/backoff.properties.test.js`    | `fc.nat()` (attempt number)                                                  |
| 14 — Startup hydration reflects full flag set  | `order-service/hydrate.properties.test.js`    | `fc.array(validFlagArb)`, mocked Flag Service                                |
| 15 — Order flow reflects cache state           | `order-service/orders.properties.test.js`     | `fc.boolean()` + absent (undefined) for flag value                           |
| 16 — Order response always has required fields | `order-service/orders.properties.test.js`     | random order payloads, any cache state                                       |
| 17 — Order processing never makes HTTP calls   | `order-service/orders.properties.test.js`     | HTTP client spy, any order payload                                           |

### Integration Tests (Testcontainers)

`FlagIntegrationTest.java` uses `@SpringBootTest(webEnvironment = RANDOM_PORT)` with:

- `MongoDBContainer("mongo:latest")` — real MongoDB
- `GenericContainer("redis:latest")` — real Redis

Test sequence:

1. POST /auth/login → get token
2. POST /flags → create flag, assert 201 + correct body
3. GET /flags → assert flag in list
4. GET /flags/:name → assert correct document
5. PATCH /flags/:name → update value, assert 200 + updated value
6. Verify Redis publish called (spy on `FlagEventPublisher`)
7. DELETE /flags/:name → assert 204
8. GET /flags/:name → assert 404

### CI Test Execution

```yaml
# In GitHub Actions workflow
- name: Set up JDK 21
  uses: actions/setup-java@v4
  with:
    java-version: "21"
    distribution: "temurin"

- name: Run tests (Flag Service)
  run: ./mvnw verify
  working-directory: flag-service

- name: Run unit + property tests (Order Service)
  run: npm test -- --run
  working-directory: order-service

- name: Run integration tests
  run: docker-compose -f docker-compose.test.yml up --abort-on-container-exit
```

> Note: Testcontainers pulls `mongo:latest` and `redis:latest` automatically during the Flag Service integration test run — no Docker Compose needed for Flag Service CI tests. The Order Service integration tests still use `docker-compose.test.yml`.

### Coverage Target

- **Flag Service**: Minimum **80% line coverage** enforced via JaCoCo (`maven-jacoco-plugin`). All 10 Flag Service correctness properties must have a passing `@Property` test before merge.
- **Order Service**: Unit + property tests ≥ 80% line coverage. All 7 Order Service correctness properties (11–17) must have a corresponding passing property-based test before merge.
