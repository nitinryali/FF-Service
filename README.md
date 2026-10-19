<div align="center">

# Flag Service

**Feature-flag management REST API**

Spring Boot 3.2 · Java 21 · MongoDB · Redis Pub/Sub · JWT

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.12-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MongoDB](https://img.shields.io/badge/MongoDB-latest-47A248?logo=mongodb&logoColor=white)](https://www.mongodb.com/)
[![Redis](https://img.shields.io/badge/Redis-latest-DC382D?logo=redis&logoColor=white)](https://redis.io/)

**Exposed port: `8001`** — the Docker image declares `EXPOSE 8001`

</div>

---

## Contents

| | |
|---|---|
| **[Overview](#overview)** | What the service is and what it is not |
| **[Architecture](#architecture)** | Request flow at a glance |
| **[Authentication](#authentication)** | Login and token verification |
| **[Flag change propagation](#flag-change-propagation)** | Write path out to Redis |
| **[Quick start](#quick-start)** | Run it in under two minutes |
| **[API reference](#api-reference)** | Every endpoint, expandable |
| **[Error handling](#error-handling)** | Status codes and error bodies |
| **[Configuration](#configuration)** | The single config file and environment variables |
| **[Docker](#docker)** | Build and run the image |
| **[Project structure](#project-structure)** | Where things live |
| **[Troubleshooting](#troubleshooting)** | Common failures |

> Every collapsible section below (`▶`) can be clicked to expand. The diagrams
> are rendered by GitHub and support hover, zoom, and pan.

---

## Overview

The Flag Service is a **multi-tenant** control plane for boolean feature flags.
Multiple tenants share one deployment, but a tenant can only ever see and change
its own flags. Every change is announced to the rest of the system over Redis
Pub/Sub.

| | |
|---|---|
| **Stores** | Boolean flags in a MongoDB `flags` collection, partitioned by `tenantId` |
| **Stores** | Tenants in `tenants`; login accounts (BCrypt hashes) in `users` |
| **Secures** | Every `/flags/**` route with a stateless HS256 JWT **and** a mandatory `X-Tenant-Id` header |
| **Publishes** | `{"tenantId":…,"name":…,"value":…,"version":…,"ts":…,"event":…}` on the `flag-updates` channel |
| **Documents** | Itself at `/swagger-ui.html` |

**Out of scope:** the consumer (Order Service), a frontend dashboard, CI/CD, and
percentage rollouts. Flags are strictly boolean.

---

## Architecture

A request passes through exactly one security checkpoint before it can touch any
flag data. Writes reach MongoDB first and Redis second, so a Redis outage can
never produce a phantom change event.

```mermaid
flowchart LR
    Client(["API client<br/>curl · Postman · CI"])

    subgraph SVC["Flag Service · :8001"]
        direction TB
        Sec["SecurityFilterChain<br/>stateless · CSRF off"]
        Jwt{{"JwtFilter<br/>verify HS256 + expiry, else 401"}}
        Tenant{{"TenantAccessInterceptor<br/>X-Tenant-Id required + tenant match, else 400/403"}}
        Auth["AuthController<br/>POST /auth/login"]
        Admin["TenantController · /tenants<br/>UserController · /users"]
        Flags["FlagController<br/>/flags/**"]
        AS["AuthService"]
        FS["FlagService"]
        Repo["FlagRepository"]
        Pub["FlagEventPublisher"]
    end

    Mongo[("MongoDB<br/>flags · tenants · users")]
    Redis[("Redis Pub/Sub<br/>flag-updates")]
    Order(["Order Service<br/>:8002 · not yet built"])

    Client --> Sec
    Sec -->|"public · issues a JWT"| Auth
    Sec --> Jwt
    Jwt -->|"token valid"| Tenant
    Jwt -->|"platform admin"| Admin
    Tenant -->|"tenant allowed"| Flags
    Auth --> AS
    Admin --> FS
    Flags --> FS
    FS --> Repo
    Repo --> Mongo
    FS -.->|"after successful write"| Pub
    Pub -.-> Redis
    Redis -.-> Order
```

<details>
<summary><strong>The three layers, and why each exists</strong></summary>

<br/>

1. **`SecurityFilterChain` + `JwtFilter`** — there is no session and no login
   state. A single filter verifies the token's signature and expiry and rejects
   the request with a JSON `401` before any controller runs. On success it puts
   the user's `tenantId` and `role` from the token claims into the
   `SecurityContext`.
2. **`TenantAccessInterceptor`** — every flag route must carry `X-Tenant-Id`.
   A missing header is `400`; a tenant user naming a tenant other than their own
   is `403`. Platform admins may operate across tenants, but the header is still
   required. The validated id is passed to controllers as a request attribute.
3. **Controllers** — `AuthController`, `FlagController`, `TenantController`, and
   `UserController` are deliberately thin. They never catch exceptions; all
   failure translation happens in `GlobalExceptionHandler`, which keeps the
   status-code policy in exactly one place.
4. **`FlagService`** — owns the business rules. It is the only place that knows a
   duplicate name means `409`, a missing flag means `404`, a missing tenant means
   `404`, and a Mongo outage means `503`. Every query is scoped by `tenantId`,
   and it enforces the ordering guarantee: **persist first, announce second.**

</details>

---

## Authentication

`POST /auth/login` exchanges credentials from the MongoDB `users` collection for
a signed JWT. Passwords are stored only as BCrypt hashes. The first platform
administrator is **seeded at startup** from `ADMIN_USERNAME` / `ADMIN_PASSWORD`;
those variables are bootstrap configuration, not a login shortcut. Every
credential failure returns the *same* generic body, so a caller can never learn
which field was wrong.

Each token carries three claims the rest of the service relies on: `sub` (the
username), `tenantId`, and `role` (`PLATFORM_ADMIN` or `TENANT_USER`).

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant F as JwtFilter
    participant T as TenantAccessInterceptor
    participant AC as AuthController
    participant AS as AuthService
    participant DB as MongoDB users
    participant FC as FlagController

    C->>AC: POST /auth/login
    activate AC
    AC->>AS: authenticate(username, password)
    AS->>DB: findByUsername(username)
    AS->>AS: BCrypt.matches(password, hash)
    alt credentials valid
        AS-->>AC: AppUser
        AC->>AS: generateToken(user)
        AS-->>AC: JWT with sub + tenantId + role
        AC-->>C: 200 {"token":…,"tenantId":…,"role":…}
    else bad / blank / over-length / unknown user
        AS-->>AC: BadCredentialsException
        AC-->>C: 401 {"error":"Authentication failed"}
    end
    deactivate AC

    C->>F: GET /flags + Bearer token + X-Tenant-Id
    activate F
    F->>F: verify signature, expiry, and claims
    alt token valid
        F->>T: continue the chain as AuthenticatedUser
        T->>T: header present? tenant allowed?
        alt tenant allowed
            T->>FC: continue with validated tenant
            FC-->>C: 200 [ ...flags ]
        else missing header
            T-->>C: 400 {"error":"Tenant header required (X-Tenant-Id)"}
        else another tenant's id
            T-->>C: 403 {"error":"Tenant access denied"}
        end
    else missing / expired / tampered
        F-->>C: 401 {"error":"..."}
    end
    deactivate F
```

| Rejection reason | Status | Body |
|---|---|---|
| No `Authorization` header, or not `Bearer …` | `401` | `{"error":"Authentication required"}` |
| Token expired | `401` | `{"error":"Token has expired"}` |
| Token malformed, tampered, or wrong signature | `401` | `{"error":"Invalid token"}` |
| Bad credentials at login | `401` | `{"error":"Authentication failed"}` |
| Missing or blank `X-Tenant-Id` | `400` | `{"error":"Tenant header required (X-Tenant-Id)"}` |
| Header names a tenant the user does not own | `403` | `{"error":"Tenant access denied"}` |

The `401` bodies come from `JwtFilter`; login failures come from
`AuthController`; tenant header failures come from `TenantAccessInterceptor`.
These bypass `GlobalExceptionHandler` because they must map to specific statuses
rather than the generic `400`/`500` paths.

---

## Flag change propagation

Redis publication is **fire-and-forget and strictly after the database write**.
If Mongo fails, the client gets a `503` and nothing is published. If Redis fails,
the client still gets a success response — the flag *is* persisted — and the
failure is logged at `WARN`.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant FC as FlagController
    participant FS as FlagService
    participant M as MongoDB
    participant R as Redis
    participant O as Order Service

    C->>FC: PATCH /flags/6650f1c2e4b0a1d3f8a91b23<br/>X-Tenant-Id: 66f0acme
    FC->>FS: updateFlag("66f0acme", "6650f1c2e4b0a1d3f8a91b23", true)
    FS->>M: save(flag)
    alt write succeeds
        M-->>FS: saved flag
        FS->>R: PUBLISH flag-updates
        FS-->>FC: Flag
        FC-->>C: 200 FlagResponse
        R-)O: event delivered
    else Mongo unreachable
        M-->>FS: DataAccessException
        FS-->>FC: ServiceUnavailableException
        FC-->>C: 503 {"error":"Service unavailable"}
        Note over R,M: nothing published — no phantom event
    end
```

The published payload:

```json
{
  "tenantId": "66f0acme",
  "name": "checkout-v2",
  "value": true,
  "version": 4,
  "ts": "2026-10-18T12:34:56.789Z",
  "event": "updated"
}
```

`event` is one of `created`, `updated`, or `deleted`. Deletions carry
`"value": null`. Consumers should **discard events whose `tenantId` they do not
own** and ignore versions older than the one they already hold.

---

## Quick start

<details open>
<summary><strong>Docker Compose — everything in one command</strong></summary>

<br/>

Requires Docker. MongoDB and Redis are provisioned for you; nothing needs to be
installed locally.

```bash
cd "/path/to/FF Project"

# Build the service image and start Mongo, Redis, and the API
docker compose up --build

# Flag Service is ready when it reports healthy
docker compose ps
```

Then open <http://localhost:8001/swagger-ui.html>, or grab a token:

```bash
curl -s http://localhost:8001/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}'
```

Stop everything with `docker compose down`, or wipe the database too with
`docker compose down -v`.

</details>

<details>
<summary><strong>From an IDE — databases in Docker, service on the host</strong></summary>

<br/>

```bash
# Start just the databases
docker compose up -d mongo redis

# Export the required secret (32+ bytes for HS256)
export JWT_SECRET="local-development-secret-change-me-please-32b"

# The localhost defaults already point at those containers
./mvnw spring-boot:run
```

On Windows PowerShell, set the variable with
`$env:JWT_SECRET = "local-development-secret-change-me-please-32b"` instead.

</details>

<details>
<summary><strong>Swagger UI — explore and call the API</strong></summary>

<br/>

1. Open <http://localhost:8001/swagger-ui.html>.
2. Run `POST /auth/login`, copy the `token` (and note the `tenantId`) from the response.
3. Click **Authorize** (top right), paste the token, and confirm.
4. Every `/flags` operation is callable, but each also needs the
   `X-Tenant-Id` header filled in (it is listed as a required parameter per
   operation). `/tenants` and `/users` require a `PLATFORM_ADMIN` token.

</details>

---

## API reference

Base URL: `http://localhost:8001`.

- Every route except `POST /auth/login` and the Swagger assets requires
  `Authorization: Bearer <token>`.
- Every `/flags/**` route **also** requires `X-Tenant-Id: <tenant id>`. A
  tenant user must send their own tenant id; only a platform admin may send
  another tenant's id.

| Method | Path | Success | Purpose |
|---|---|---|---|
| `POST` | `/auth/login` | `200` | Exchange credentials for a JWT |
| `POST` | `/tenants` | `201` | Create a tenant (platform admin) |
| `GET` | `/tenants` | `200` | List tenants (platform admin) |
| `GET` | `/tenants/{id}` | `200` | Fetch a tenant (platform admin) |
| `DELETE` | `/tenants/{id}` | `204` | Delete a tenant and all its flags/users (platform admin) |
| `POST` | `/users` | `201` | Create a tenant-bound user (platform admin) |
| `POST` | `/flags` | `201` | Create a flag in the `X-Tenant-Id` tenant |
| `GET` | `/flags` | `200` | List the `X-Tenant-Id` tenant's flags |
| `GET` | `/flags/{name}` | `200` | Fetch one flag by name in that tenant |
| `PATCH` | `/flags/{id}` | `200` | Update a flag's value by id in that tenant |
| `DELETE` | `/flags/{id}` | `204` | Soft-delete a flag by id in that tenant |

A flag resource looks like this:

```json
{
  "id": "6650f1c2e4b0a1d3f8a91b23",
  "tenantId": "66f0acme",
  "name": "checkout-v2",
  "value": true,
  "version": 4,
  "createdAt": "2026-10-18T12:00:00Z",
  "updatedAt": "2026-10-18T12:34:56Z"
}
```

<details>
<summary><strong><code>POST /auth/login</code></strong> — issue a JWT</summary>

<br/>

```bash
curl -X POST http://localhost:8001/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}'
```

`200`:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tenantId": "66f0acme",
  "role": "TENANT_USER"
}
```

`401` for bad credentials, unknown user, blank fields, username over 64
characters, or password over 128 characters.

On startup the service seeds one platform admin from `ADMIN_USERNAME` /
`ADMIN_PASSWORD` if that username is not already in `users`. Every later account
is created through `POST /users`.

</details>

<details>
<summary><strong><code>POST /tenants</code></strong> — create a tenant</summary>

<br/>

Platform admin only. The name must match `^[a-zA-Z0-9\-_]{1,100}$` and is
unique.

```bash
curl -X POST http://localhost:8001/tenants \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"acme"}'
```

`201` with the tenant (`id`, `name`, timestamps), `400` for an invalid name, or
`409` if the name already exists.

</details>

<details>
<summary><strong><code>DELETE /tenants/{id}</code></strong> — delete a tenant</summary>

<br/>

Platform admin only. This **cascades**: every flag and every user bound to the
tenant is permanently removed, then the tenant itself is deleted.

```bash
curl -X DELETE http://localhost:8001/tenants/66f0acme \
  -H "Authorization: Bearer $TOKEN"
```

`204` on success, or `404` if no such tenant exists.

</details>

<details>
<summary><strong><code>POST /users</code></strong> — create a tenant-bound user</summary>

<br/>

Platform admin only. The password is hashed with BCrypt before storage; the
response never contains it.

```bash
curl -X POST http://localhost:8001/users \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"username":"acme-bot","password":"s3cret","tenantId":"66f0acme"}'
```

`role` is optional and defaults to `TENANT_USER`. `201` on success, `404` if the
tenant does not exist, or `409` if the username is taken.

</details>

<details>
<summary><strong><code>POST /flags</code></strong> — create a flag</summary>

<br/>

The name must match `^[a-zA-Z0-9\-_]{1,100}$` and is unique **within the
tenant**.

```bash
curl -X POST http://localhost:8001/flags \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: 66f0acme" \
  -H 'Content-Type: application/json' \
  -d '{"name":"checkout-v2","value":false}'
```

| Status | When |
|---|---|
| `201` | Created — a `created` event is published |
| `400` | Name is blank or invalid, or `X-Tenant-Id` is missing |
| `401` | Missing or invalid token |
| `403` | The header names another tenant |
| `404` | The tenant does not exist |
| `409` | The name is already used in this tenant |
| `503` | MongoDB is unreachable |

</details>

<details>
<summary><strong><code>GET /flags</code></strong> — list all flags</summary>

<br/>

```bash
curl http://localhost:8001/flags \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: 66f0acme"
```

Always `200`. Returns only the header-selected tenant's live flags, `[]` when
there are none, and never touches Redis.

</details>

<details>
<summary><strong><code>GET /flags/{name}</code></strong> — fetch one flag</summary>

<br/>

```bash
curl http://localhost:8001/flags/checkout-v2 \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: 66f0acme"
```

`200` with the flag, or `404 {"error":"Flag not found"}`. The name is only
matched inside the header-selected tenant.

</details>

<details>
<summary><strong><code>PATCH /flags/{id}</code></strong> — update a flag</summary>

<br/>

The flag is addressed by its server-assigned `id`, not its name.

```bash
curl -X PATCH http://localhost:8001/flags/6650f1c2e4b0a1d3f8a91b23 \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: 66f0acme" \
  -H 'Content-Type: application/json' \
  -d '{"value":true}'
```

`200` with the updated flag, or `404` if that id does not exist **in the
header-selected tenant**. Publishes an `updated` event (carrying `tenantId` and
`version`) only after the write succeeds.

</details>

<details>
<summary><strong><code>DELETE /flags/{id}</code></strong> — delete a flag</summary>

<br/>

The flag is addressed by its server-assigned `id`, not its name.

```bash
curl -X DELETE http://localhost:8001/flags/6650f1c2e4b0a1d3f8a91b23 \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: 66f0acme"
```

`204` with an empty body, or `404` if that id does not exist in the tenant.
Deletion is a **soft delete** (`deletedAt` is set, the document stays in
MongoDB), which frees the name for reuse. Publishes a `deleted` event with
`"value": null`.

</details>

---

## Error handling

Every controller error funnels through `GlobalExceptionHandler`, which returns a
single-key JSON envelope: `{"error": "..."}`.

```mermaid
flowchart TD
    E{"What went wrong?"}
    E -->|"no / bad / expired JWT"| N401["401 · Authentication required<br/>Token has expired<br/>Invalid token"]
    E -->|"missing X-Tenant-Id"| N400T["400 · Tenant header required (X-Tenant-Id)"]
    E -->|"another tenant's id"| N403["403 · Tenant access denied"]
    E -->|"invalid name or field"| N400["400 · the violated rule"]
    E -->|"unknown flag name or id"| N404["404 · Flag not found"]
    E -->|"unknown tenant"| N404T["404 · Tenant not found"]
    E -->|"duplicate flag / tenant / user"| N409["409 · … already exists"]
    E -->|"MongoDB unreachable"| N503["503 · Service unavailable"]
    E -->|"anything else"| N500["500 · An unexpected error occurred"]
```

| Status | Body | Raised by |
|---|---|---|
| `400` | The violated field constraint | Bean validation on the request body |
| `400` | `Tenant header required (X-Tenant-Id)` | `TenantAccessInterceptor` |
| `401` | `Authentication required` / `Token has expired` / `Invalid token` | `JwtFilter` |
| `401` | `Authentication failed` | `AuthController` (login only) |
| `403` | `Tenant access denied` | `TenantAccessInterceptor` |
| `403` | `Access denied` | Failed `@PreAuthorize` role check |
| `404` | `Flag not found` | `FlagNotFoundException` |
| `404` | `Tenant not found` | `TenantNotFoundException` |
| `409` | `Flag already exists` | `FlagAlreadyExistsException` |
| `409` | `Tenant already exists` | `TenantAlreadyExistsException` |
| `409` | `User already exists` | `UserAlreadyExistsException` |
| `503` | `Service unavailable` | `ServiceUnavailableException` — the cause is logged |
| `500` | `An unexpected error occurred` | Catch-all; the stack trace is logged, never returned |

<details>
<summary><strong>Why a duplicate name is <code>409</code> and not a race condition</strong></summary>

<br/>

Uniqueness is enforced by a **partial compound unique index** on
`(tenantId, name)` in MongoDB, not by a read-then-write check. Two concurrent
`POST`s for the same name in the same tenant therefore cannot both succeed: the
loser gets a `DuplicateKeyException`, which is translated to
`FlagAlreadyExistsException` and returned as `409`. No flag is created or
modified by the losing request.

The index is partial on `{"deletedAt": null}`, so soft-deleted flags do not
reserve their name — the same name can be created again later. The same name in a
*different* tenant is always allowed.

</details>

---

## Configuration

There is a single configuration file, `application.properties`, and no profiles
to switch between. The same file is used for local testing and for the cloud
deployment — only where the two connection URLs point changes.

Every value below is read from an environment variable, and each one has a
localhost default. With nothing set, the service talks to the MongoDB and Redis
containers from `docker-compose.yml`; export `MONGODB_URI` and `REDIS_URL` to
point it at MongoDB Atlas and Upstash Redis instead, locally or on Render.

### Environment variables

| Variable | Default | Notes |
|---|---|---|
| `SERVER_PORT` | `8001` | Listening port |
| `JWT_SECRET` | *(none)* | **Required.** HS256 signing key, 32+ bytes |
| `JWT_EXPIRY_SECONDS` | `3600` | Must be between `60` and `86400` |
| `ADMIN_USERNAME` | `admin` | Bootstrap platform-admin username, seeded into `users` if absent |
| `ADMIN_PASSWORD` | `admin` | Bootstrap password; stored only as a BCrypt hash |
| `MONGODB_URI` | `mongodb://localhost:27017/ffproject` | Include the database name (`/ffproject`) in the URI |
| `REDIS_URL` | `redis://localhost:6379` | Use `rediss://…` for Upstash; TLS is enabled from the scheme |

Out-of-range `JWT_EXPIRY_SECONDS` makes the service fail at startup rather than
issue tokens with an unusable lifetime. A missing or too-short `JWT_SECRET`
fails at startup as well.

On startup an idempotent migration runs: existing flags are assigned to a
`default` tenant (created if missing), backfilled with `version = 1`, audit
fields, and `deletedAt = null`, and the legacy globally-unique `name_1` index is
dropped in favour of the tenant-scoped compound index.

---

## Docker

The image is a two-stage build: Maven compiles the fat JAR, then it is copied
onto a slim Alpine JRE. The Maven wrapper is baked in, so the build pins its own
Maven version.

```bash
docker build -t ffproject/flag-service:local ./flag-service
docker run --rm -p 8001:8001 \
  -e JWT_SECRET="local-development-secret-change-me-please-32b" \
  -e MONGODB_URI="mongodb://host.docker.internal:27017/ffproject" \
  -e REDIS_URL="redis://host.docker.internal:6379" \
  ffproject/flag-service:local
```

**Exposed port: `8001`** (declared via `EXPOSE 8001`). Similarly, the sibling
Order Service is specified to expose `8002`, though it is not built yet.

The container needs no install step at startup — it only runs
`java -jar /app/app.jar`. Tests are skipped during the image build.

---

## Project structure

```
FF Project/
├── docker-compose.yml                 # Mongo + Redis + Flag Service
└── flag-service/
    ├── Dockerfile                     # two-stage build, EXPOSE 8001
    ├── pom.xml
    └── src/main/
        ├── java/com/ffproject/flagservice/
        │   ├── auth/                  # AuthController, JwtFilter, AppUser, UserController,
        │   │   │                      # UserService, UserBootstrap, Role
        │   │   └── dto/               # CreateUserRequest, UserResponse
        │   ├── config/                # Security, Web (interceptors), Redis, Mongo, OpenAPI
        │   ├── exception/             # domain exceptions + GlobalExceptionHandler
        │   ├── flag/                  # Flag, FlagRepository, FlagService, FlagController
        │   │   └── dto/               # CreateFlagRequest, UpdateFlagRequest, FlagResponse
        │   ├── migration/             # FlagDataMigration (tenant backfill, index cleanup)
        │   ├── redis/                 # FlagEventPublisher
        │   └── tenant/                # Tenant, TenantService, TenantController,
        │                              # TenantAccessInterceptor
        └── resources/
            └── application.properties # the only config file — env vars with localhost defaults
```

---

## Troubleshooting

<details>
<summary><strong>Startup fails with <code>Could not resolve placeholder 'JWT_SECRET'</code></strong></summary>

<br/>

`JWT_SECRET` is not set at all. It has no default on purpose — the service would
rather refuse to start than sign tokens with a guessable key. Export a real
secret of at least 32 bytes:

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
```

</details>

<details>
<summary><strong>Startup fails with <code>WeakKeyException</code></strong></summary>

<br/>

`JWT_SECRET` is set, but shorter than 32 bytes. HS256 is HMAC-SHA256, and
RFC 7518 §3.2 requires the key to be at least as long as the hash output — 256
bits, so 32 bytes. The JWT library enforces this:

```
WeakKeyException: The specified key byte array is 64 bits which is not secure
enough for any JWT HMAC-SHA algorithm.
```

Generate one that is long enough:

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
```

</details>

<details>
<summary><strong>Startup fails with a MongoDB timeout</strong></summary>

<br/>

The service verifies its database connection at startup and exits non-zero if it
cannot connect. Check that the container is up and healthy:

```bash
docker compose ps
docker compose logs mongo
```

When running the service on the host rather than in compose, remember that
`localhost` is correct only for host networking. Inside the compose network the
URLs must use the service names `mongo` and `redis`.

</details>

<details>
<summary><strong>A flag change did not reach a consumer</strong></summary>

<br/>

This is expected to be non-fatal by design. Publishing happens after the write
and never fails the caller's request — a Redis problem leaves the flag persisted
and the HTTP response successful. Look for a `WARN` line:

```
Redis publish failed for flag checkout-v2 in tenant 66f0acme: ...
```

If you see no such line but also no event, verify the subscriber is listening on
the `flag-updates` channel, that it accepts the event's `tenantId`, and that both
processes point at the same Redis instance.

</details>

<details>
<summary><strong>Swagger UI returns 401</strong></summary>

<br/>

The UI itself is public, but `/flags` operations are not. Use **Authorize** and
paste a token from `POST /auth/login`. If the token is older than
`JWT_EXPIRY_SECONDS`, log in again.
</details>
