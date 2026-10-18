# Requirements Document

## Introduction

This document defines the requirements for the **FF Project** — a full-stack Feature Flag platform deployed across a microservices architecture. The system allows developers to create and manage boolean feature flags via a REST API. Changes to flag state are propagated to consumer services in real time via Redis Pub/Sub, enabling consumer services to evaluate flags locally without per-request HTTP calls. An Order Service acts as a reference consumer, demonstrating flag-controlled behavior changes. The platform is containerized with Docker, hosted on Render (services), and uses MongoDB Atlas for persistence and Upstash Redis for real-time propagation.

---

## Glossary

- **Flag_Service**: The backend microservice responsible for creating, updating, deleting, and serving feature flags.
- **Order_Service**: A demo microservice that consumes feature flags from the Flag_Service to control order processing behavior.
- **Flag**: A named boolean configuration value (`true` or `false`) stored persistently and managed by the Flag_Service.
- **Flag_Cache**: An in-memory store within the Order_Service that holds the current state of all feature flags.
- **Redis_Channel**: An Upstash Redis Pub/Sub channel used by the Flag_Service to broadcast flag state changes.
- **JWT**: A JSON Web Token used to authenticate requests to the Flag_Service REST API.
- **Auth_Service**: The authentication component embedded in the Flag_Service responsible for issuing and validating JWTs.
- **MongoDB_DB**: The MongoDB Atlas cluster used by the Flag_Service for persistent flag storage.
- **CI_CD_Pipeline**: The GitHub Actions workflow that builds, tests, and deploys services.
- **Docker_Image**: A container image produced per service and used for deployment.

---

## Requirements

### Requirement 1: Flag Management via REST API

**User Story:** As a developer, I want to create, read, update, and delete feature flags via a REST API, so that I can manage flag state without direct database access.

#### Acceptance Criteria

1. WHEN a POST request with a valid JWT and a flag name (1–100 characters, alphanumeric, hyphens, and underscores only) and a boolean value is sent to the Flag_Service, THE Flag_Service SHALL create a new Flag and persist it to MongoDB_DB.
2. WHEN a GET request with a valid JWT is sent to the Flag_Service for a specific flag name, THE Flag_Service SHALL return the flag name and its current boolean value within 500ms.
3. WHEN a GET request with a valid JWT is sent to the Flag_Service for all flags, THE Flag_Service SHALL return a list of all Flag records including name and boolean value, or an empty list if no flags exist.
4. WHEN a PATCH request with a valid JWT is sent to the Flag_Service with a flag name and a new boolean value, THE Flag_Service SHALL update the Flag in MongoDB_DB and publish the change to the Redis_Channel within 500ms.
5. WHEN a DELETE request with a valid JWT is sent to the Flag_Service for an existing flag name, THE Flag_Service SHALL remove the Flag from MongoDB_DB and publish a deletion event to the Redis_Channel.
6. IF a request is sent to the Flag_Service without a valid JWT, THEN THE Flag_Service SHALL return an HTTP 401 Unauthorized response without modifying any flag data.
7. IF a POST request is sent to the Flag_Service with a flag name that already exists, THEN THE Flag_Service SHALL return an HTTP 409 Conflict response without creating or modifying any flag.
8. IF a GET, PATCH, or DELETE request is sent to the Flag_Service for a flag name that does not exist, THEN THE Flag_Service SHALL return an HTTP 404 Not Found response without modifying any flag data.
9. IF a POST or PATCH request is sent to the Flag_Service with a flag name that violates the naming constraints (empty, exceeds 100 characters, or contains disallowed characters), THEN THE Flag_Service SHALL return an HTTP 400 Bad Request response with an error message indicating the violated rule.
10. IF the Flag_Service fails to persist a flag change to MongoDB_DB, THEN THE Flag_Service SHALL return an HTTP 503 Service Unavailable response and SHALL NOT publish any event to the Redis_Channel.

---

### Requirement 2: Authentication and JWT Issuance

**User Story:** As a developer, I want to authenticate with the Flag_Service using a JWT, so that only authorized clients can manage flags.

#### Acceptance Criteria

1. WHEN a POST request with a body containing a username field (1–64 characters) and a password field (1–128 characters) is sent to the Auth_Service login endpoint, THE Auth_Service SHALL return a signed JWT with an expiry duration configurable via environment configuration, where the minimum configurable expiry is 60 seconds and the maximum is 86400 seconds.
2. WHEN the Flag_Service receives a request with a JWT in the Authorization header, THE Flag_Service SHALL validate the JWT signature and expiry before processing the request.
3. IF the Flag_Service receives a request with an expired JWT, THEN THE Flag_Service SHALL return an HTTP 401 Unauthorized response with an error message indicating that the token has expired.
4. IF the Flag_Service receives a request with a malformed or tampered JWT, THEN THE Flag_Service SHALL return an HTTP 401 Unauthorized response with an error message indicating that the token is invalid.
5. THE Auth_Service SHALL sign JWTs using a secret key loaded from environment configuration and SHALL NOT embed the secret in source code.
6. IF the Auth_Service receives a POST request to the login endpoint with missing, empty, or invalid credentials, THEN THE Auth_Service SHALL return an HTTP 401 Unauthorized response with an error message indicating authentication failure, without revealing which field was incorrect.
7. IF the Flag_Service receives a request without an Authorization header or with an Authorization header that does not contain a Bearer token, THEN THE Flag_Service SHALL return an HTTP 401 Unauthorized response with an error message indicating that authentication is required.

---

### Requirement 3: Real-Time Flag Propagation via Redis Pub/Sub

**User Story:** As a platform operator, I want flag changes to be propagated to consumer services in real time via Redis Pub/Sub, so that services reflect updated flag state without polling the Flag_Service.

#### Acceptance Criteria

1. WHEN a Flag is created, updated, or deleted in the Flag_Service, THE Flag_Service SHALL publish a message to the Redis_Channel containing the flag name (maximum 256 characters), the new boolean value (or null for deleted events), and the event type (`created`, `updated`, or `deleted`).
2. WHILE the Order_Service is running, THE Order_Service SHALL maintain an active subscription to the Redis_Channel.
3. WHEN a message is received on the Redis_Channel, THE Order_Service SHALL update its Flag_Cache to reflect the new flag state within 1 second of receiving the message.
4. WHEN a `deleted` event is received on the Redis_Channel, THE Order_Service SHALL remove the corresponding flag entry from the Flag_Cache.
5. IF the Order_Service receives a malformed or unrecognized message on the Redis_Channel, THEN THE Order_Service SHALL discard the message and retain the existing Flag_Cache state for the affected flag unchanged.
6. IF the Order_Service loses its Redis connection, THEN THE Order_Service SHALL attempt to reconnect to the Redis_Channel using exponential backoff starting at 1 second, doubling on each attempt, with a maximum retry interval of 30 seconds.
7. WHILE the Order_Service is reconnecting to Redis, THE Order_Service SHALL continue serving requests using the last known Flag_Cache state.
8. IF the Order_Service fails to reconnect to the Redis_Channel within 5 minutes, THEN THE Order_Service SHALL emit an error log indicating that the Redis connection could not be restored.

---

### Requirement 4: Flag Cache Initialization in the Order Service

**User Story:** As a developer, I want the Order Service to load all current flag values on startup, so that it can evaluate flags immediately without waiting for a Pub/Sub event.

#### Acceptance Criteria

1. WHEN the Order_Service starts, THE Order_Service SHALL fetch all current Flag records from the Flag_Service REST API and populate the Flag_Cache before accepting incoming requests.
2. IF the Flag_Service is unreachable during Order_Service startup, THEN THE Order_Service SHALL retry the initial fetch up to 5 times with a 5-second interval between each attempt before starting with an empty Flag_Cache and logging a warning message indicating the Flag_Service was unreachable.
3. THE Flag_Cache SHALL store flags as key-value pairs where the key is the flag name as a non-empty string and the value is the boolean flag state.
4. IF the Flag_Service returns an error response during the initial fetch, THEN THE Order_Service SHALL treat the response as a failed attempt, apply the same retry logic as criterion 2, and start with an empty Flag_Cache after all retry attempts are exhausted.
5. WHEN the Flag_Service returns zero Flag records during the initial fetch, THE Order_Service SHALL populate the Flag_Cache as empty and proceed to accept incoming requests.

---

### Requirement 5: Flag-Controlled Order Processing in the Order Service

**User Story:** As a developer, I want the Order Service to change its order processing behavior based on feature flag state, so that I can demonstrate real-time flag evaluation without per-request HTTP calls.

#### Acceptance Criteria

1. WHEN a POST request is sent to the Order_Service to create an order and the flag `new-checkout-flow` is `true` in the Flag_Cache, THE Order_Service SHALL process the order using the new checkout flow and include a `"flow": "new"` field in the response body.
2. WHEN a POST request is sent to the Order_Service to create an order and the flag `new-checkout-flow` is `false` or absent in the Flag_Cache, THE Order_Service SHALL process the order using the legacy checkout flow and include a `"flow": "legacy"` field in the response body.
3. THE Order_Service SHALL evaluate flags by reading directly from the Flag_Cache and SHALL NOT make an outbound network call during order request processing.
4. WHEN a GET request is sent to the Order_Service for its current flag state, THE Order_Service SHALL return all key-value entries currently stored in the Flag_Cache.
5. IF the Flag_Cache is unavailable or corrupted during order processing, THEN THE Order_Service SHALL default to the legacy checkout flow and include a `"flow": "legacy"` field in the response body.
6. WHEN the Order_Service successfully processes an order, THE Order_Service SHALL include at minimum an `order_id` and a `status` field in the response body alongside the `flow` field.

---

### Requirement 6: Persistent Flag Storage

**User Story:** As a platform operator, I want feature flags to be persisted in a database, so that flag state survives service restarts.

#### Acceptance Criteria

1. THE Flag_Service SHALL persist all Flag records to MongoDB_DB in a `flags` collection where each document contains at minimum the fields: `_id` (ObjectId), `name` (unique string, maximum 255 characters), `value` (boolean), `createdAt`, and `updatedAt`.
2. WHEN the Flag_Service starts, THE Flag_Service SHALL connect to MongoDB_DB and verify connectivity within 30 seconds before accepting incoming requests.
3. IF the Flag_Service cannot connect to MongoDB_DB during startup within 30 seconds, THEN THE Flag_Service SHALL log the connection error and terminate the startup process with a non-zero exit code.
4. IF the Flag_Service loses its MongoDB_DB connection during operation, THEN THE Flag_Service SHALL return an HTTP 503 Service Unavailable response to any flag management request and SHALL log the connection error.
5. WHEN the Flag_Service reestablishes its MongoDB_DB connection after a loss, THE Flag_Service SHALL resume processing flag management requests normally.

---

### Requirement 7: Containerization with Docker

**User Story:** As a developer, I want each service to be packaged as a Docker image, so that services can be deployed consistently across environments.

#### Acceptance Criteria

1. THE Flag_Service SHALL be packaged as a Docker_Image using a Dockerfile at the root of the flag-service directory.
2. THE Order_Service SHALL be packaged as a Docker_Image using a Dockerfile at the root of the order-service directory.
3. WHEN a Docker_Image is built, THE Docker_Image SHALL contain all packages and files needed to start and serve requests without executing install commands at container startup.
4. THE Flag_Service Docker_Image SHALL expose port 8001 and THE Order_Service Docker_Image SHALL expose port 8002 via the `EXPOSE` Dockerfile instruction, with the exposed ports documented in each service's README.
5. WHERE a local development environment is configured, THE Flag_Service and THE Order_Service SHALL be runnable together using a `docker-compose.yml` file at the project root, with both services reporting healthy status within 30 seconds of `docker-compose up`.
6. WHERE local development is configured using `docker-compose.yml`, THE compose file SHALL define a shared network enabling service-name-based communication between the Flag_Service and Order_Service containers.
7. WHERE a local development environment is configured, THE `docker-compose.yml` SHALL include a MongoDB container (using the official `mongo` Docker image) and a Redis container (using the official `redis` Docker image) so that developers do not need to install MongoDB or Redis locally.

---

### Requirement 8: CI/CD Pipeline with GitHub Actions

**User Story:** As a developer, I want a CI/CD pipeline that builds, tests, and deploys services automatically on code push, so that deployments are consistent and repeatable.

#### Acceptance Criteria

1. WHEN code is pushed to the `main` branch, THE CI_CD_Pipeline SHALL build the Docker_Image for each service and run all unit and integration tests for each service.
2. IF the Docker_Image build fails for any service during a CI_CD_Pipeline run, THEN THE CI_CD_Pipeline SHALL fail the build, emit an error message indicating which service failed to build, and SHALL NOT proceed to the test or deployment steps.
3. IF any automated test fails during a CI_CD_Pipeline run, THEN THE CI_CD_Pipeline SHALL fail the build, emit an error message indicating which test suite failed, and SHALL NOT proceed to deployment.
4. WHEN all tests pass on the `main` branch, THE CI_CD_Pipeline SHALL deploy the Flag_Service Docker_Image to Render and SHALL verify deployment success by confirming that Render returns a successful deployment status within 300 seconds.
5. IF a deployment to Render fails or does not return a successful status within 300 seconds, THEN THE CI_CD_Pipeline SHALL fail the pipeline run and emit an error message indicating that the Render deployment failed.
6. THE CI_CD_Pipeline SHALL read all secrets (JWT secret, MongoDB Atlas connection string, Redis URL, Render API keys) from GitHub Actions encrypted secrets and SHALL NOT embed secrets in workflow files.
7. WHEN a pull request is opened against the `main` branch, THE CI_CD_Pipeline SHALL run the build and test steps and SHALL report a pass or fail status check on the pull request for each of the build and test stages individually.
