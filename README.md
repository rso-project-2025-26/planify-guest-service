# Guest Service

Microservice for managing event invitations and RSVP responses from the guest perspective in the Planify platform. Provides RESTful APIs secured with Keycloak authentication, consumes events from event-manager-service via Kafka, and publishes RSVP status updates.

## Technologies

### Backend Framework & Language
- **Java 21** - Programming language
- **Spring Boot 3.5.7** - Application framework
- **Spring Security** - Security and authentication
- **Spring Data JPA** - Database access
- **Hibernate** - ORM framework
- **Lombok** - Boilerplate code reduction

### Database
- **PostgreSQL** - Database
- **Flyway** - Database migrations
- **HikariCP** - Connection pooling

### Security & Authentication
- **Keycloak** - OAuth2/OIDC authentication and authorization
- **Spring OAuth2 Resource Server** - JWT validation

### Messaging System
- **Apache Kafka** - Event streaming platform
- **Spring Kafka** - Kafka integration

### Monitoring & Health
- **Spring Boot Actuator** - Health checks and metrics
- **Micrometer Prometheus** - Metrics export
- **Resilience4j** - Circuit breakers, retry, rate limiting, bulkheads

### API Documentation
- **SpringDoc OpenAPI 3** - OpenAPI/Swagger documentation

### Containerization
- **Docker** - Application containerization
- **Kubernetes/Helm** - Orchestration (Helm charts included)

## System Integrations

- **Keycloak**: OAuth2/OIDC authentication and authorization. All endpoints require a valid JWT Bearer token.
- **Kafka**: Consumes events from event-manager-service (guest invitations, event updates) and publishes RSVP status changes consumed by event-manager-service, notification-service, and analytics-service.
- **PostgreSQL**: Stores all invitation and RSVP data via Hibernate/JPA with Flyway migrations in the `guest` schema.
- **event-manager-service**: Receives guest invitation events and provides internal API for querying invitation statuses.
- **notification-service**: Receives RSVP events to send confirmation emails/notifications.
- **analytics-service**: Receives RSVP events to track guest engagement metrics.

## Roles

### Keycloak Roles

Application-wide roles managed by Keycloak and enforced in this service:

- **UPORABNIK** — Standard authenticated user (can view and respond to their own invitations)
- **ORGANISER** — Can view invitation details for events in their organization
- **ORG_ADMIN** — Full organization admin permissions + organiser capabilities
- **ADMINISTRATOR** — System administrator with access to all invitations

## API Endpoints

All endpoints require `Authorization: Bearer <JWT_TOKEN>` header unless otherwise specified.

### Guest Perspective (`/api/guests`)

- `GET /api/guests/my-invitations?userId={userId}` — Get all invitations for authenticated user
- `GET /api/guests/my-invitations/{eventId}?orgId={orgId}&userId={userId}` — Get specific invitation details (ORG_ADMIN or ORGANISER)
- `GET /api/guests/my-events?userId={userId}` — Get all accepted events for authenticated user

### RSVP Actions

- `PUT /api/guests/my-invitations/{eventId}/accept?userId={userId}` — Accept invitation (publishes Kafka event)
- `PUT /api/guests/my-invitations/{eventId}/decline?userId={userId}` — Decline invitation (publishes Kafka event)

### Internal API (for event-manager-service)

- `GET /api/guests/internal/events/{eventId}/invitations` — Get all invitations for an event

### Minimal curl examples

```bash
# Get my invitations
curl -H "Authorization: Bearer $TOKEN" \
     "http://localhost:8085/api/guests/my-invitations?userId=990e8400-e29b-41d4-a716-446655440004"

# Accept invitation
curl -X PUT -H "Authorization: Bearer $TOKEN" \
     "http://localhost:8085/api/guests/my-invitations/550e8400-e29b-41d4-a716-446655440000/accept?userId=990e8400-e29b-41d4-a716-446655440004"

# Decline invitation
curl -X PUT -H "Authorization: Bearer $TOKEN" \
     "http://localhost:8085/api/guests/my-invitations/550e8400-e29b-41d4-a716-446655440000/decline?userId=990e8400-e29b-41d4-a716-446655440004"

# Get my accepted events
curl -H "Authorization: Bearer $TOKEN" \
     "http://localhost:8085/api/guests/my-events?userId=990e8400-e29b-41d4-a716-446655440004"
```

## Database Structure

The service uses PostgreSQL with the following core entity in the `guest` schema:

### Invitations

Guest invitation records with RSVP status tracking. Contains:

- `id` (UUID, PK)
- `event_id` (UUID) - Reference to event in event-manager-service
- `user_id` (UUID) - Reference to user in user-service
- `organization_id` (UUID) - Organization ID for permission checks
- `rsvp_status` (VARCHAR) - RSVP status: `PENDING`, `ACCEPTED`, `DECLINED`, `MAYBE`
- `responded_at` (TIMESTAMP) - Time when guest responded to invitation
- `invitation_received_at` (TIMESTAMP) - Time when invitation was created

**Indexes:**
- `idx_invitations_event` on `event_id`
- `idx_invitations_user` on `user_id`
- `idx_invitations_organization` on `organization_id`
- `idx_invitations_rsvp_status` on `rsvp_status`

**Constraints:**
- Unique constraint on `(event_id, user_id)` - prevents duplicate invitations
- No foreign key constraints as references cross database schemas

**Relationships**: All entity references use UUIDs for cross-service lookups without foreign key constraints. Audit fields (`invitation_received_at`, `responded_at`) track invitation lifecycle. Database schema is versioned via Flyway migrations in `src/main/resources/db/migration/`.

**Note**: This service tracks RSVP status from the guest perspective. The `event-manager-service` maintains a separate `guest_list` table tracking who was invited from the organizer's perspective.

## Installation and Setup

### Prerequisites

- Java 21 or newer
- Maven 3.6+
- Docker and Docker Compose
- Git

### Infrastructure Setup

This service requires PostgreSQL, Kafka, Keycloak, and event-manager-service to run. These dependencies are provided via Docker containers in the main Planify repository.

Clone and setup the infrastructure:

```bash
# Clone the main Planify repository
git clone https://github.com/rso-project-2025-26/planify.git
cd planify

# Follow the setup instructions in the main repository README
# This will start all required infrastructure services (PostgreSQL, Kafka, Keycloak)
```

Refer to the main Planify repository (https://github.com/rso-project-2025-26/planify) documentation for detailed infrastructure setup instructions.

### Configuration

The application uses a single `application.yaml` configuration file located in `src/main/resources/`.

Important environment variables:

```
SERVER_PORT=8085
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/planify
SPRING_DATASOURCE_USERNAME=planify
SPRING_DATASOURCE_PASSWORD=planify
DB_SCHEMA=guest
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
SPRING_KAFKA_CONSUMER_GROUP_ID=guest-service
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=http://localhost:9080/realms/planify
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://localhost:9080/realms/planify/protocol/openid-connect/certs
```

### Local Run

```bash
# Build project
mvn clean package

# Run application
mvn spring-boot:run
```

### Using Makefile

```bash
# Build project
make build

# Docker build
make docker-build

# Docker run
make docker-run

# Tests
make test
```

### Docker Run

```bash
# Build Docker image
docker build -t planify/guest-service:0.0.1 .

# Run container
docker run -p 8085:8085 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/planify \
  -e SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=http://host.docker.internal:9080/realms/planify \
  -e SPRING_KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
  planify/guest-service:0.0.1
```

### Kubernetes/Helm Deployment

```bash
# Install with Helm
helm install guest-service ./helm/guest

# Install with specific environment values
helm install guest-service ./helm/guest -f ./helm/guest/values-dev.yaml

# Upgrade
helm upgrade guest-service ./helm/guest

# Uninstall
helm uninstall guest-service
```

### Flyway Migrations

Migrations are located in `src/main/resources/db/migration/`:

- `V1__init.sql` - Initial schema with invitations table and indexes

Manual migration run:

```bash
mvn flyway:migrate
```

## Health Check & Monitoring

### Actuator Endpoints

- **GET** `/actuator/health` — Health check endpoint
- **GET** `/actuator/health/liveness` — Liveness probe
- **GET** `/actuator/health/readiness` — Readiness probe
- **GET** `/actuator/prometheus` — Prometheus metrics
- **GET** `/actuator/info` — Application information
- **GET** `/actuator/metrics` — Application metrics

### API Documentation

After starting the application, Swagger UI is available at:
```
http://localhost:8085/swagger-ui.html
```

OpenAPI specification:
```
http://localhost:8085/api-docs
```

## Kafka Events

### Events Consumed

The service listens to the following Kafka topics:

**guest-invited** — Published by event-manager-service when organizer invites a guest

Consumed payload:
```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "990e8400-e29b-41d4-a716-446655440004",
  "organizationId": "880e8400-e29b-41d4-a716-446655440003",
  "timestamp": "2024-12-24T10:00:00Z"
}
```

Action: Creates new invitation record with status `PENDING`

**guest-removed** — Published by event-manager-service when organizer removes a guest

Consumed payload:
```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "990e8400-e29b-41d4-a716-446655440004",
  "timestamp": "2024-12-24T10:00:00Z"
}
```

Action: Deletes invitation record. If guest had previously accepted, publishes `rsvp-declined` event to update attendee count.

**event-deleted** — Published by event-manager-service when event is deleted

Consumed payload:
```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2024-12-24T10:00:00Z"
}
```

Action: Deletes all invitations associated with the event

Additional consumed topics (for future features):
- `event-created` - Track new events (currently logged only)
- `event-updated` - Track event changes (currently logged only)

### Events Published

The service publishes the following events to Kafka:

**rsvp-accepted** — Published when a guest accepts an event invitation

Published payload:
```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "990e8400-e29b-41d4-a716-446655440004",
  "wasAccepted": false,
  "timestamp": "2024-12-24T10:00:00Z"
}
```

Consumed by:
- **event-manager-service** - Updates `current_attendees` count
- **notification-service** - Sends confirmation email/notification to guest
- **analytics-service** - Tracks RSVP metrics

**rsvp-declined** — Published when a guest declines an event invitation

Published payload:
```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "990e8400-e29b-41d4-a716-446655440004",
  "wasAccepted": true,
  "timestamp": "2024-12-24T10:00:00Z"
}
```

Note: `wasAccepted` indicates if the guest had previously accepted (useful for decrementing attendee count)

Consumed by:
- **event-manager-service** - Updates `current_attendees` count if guest had previously accepted
- **notification-service** - Sends notification to organizer
- **analytics-service** - Tracks RSVP metrics

## Resilience4j

The service implements:

- **Circuit Breakers** - Prevention of cascading failures for:
  - `keycloakService` - Protects user validation calls
  - `defaultCircuitBreaker` - General protection for other operations
- **Retry** - Automatic retry of failed calls with exponential backoff
- **Rate Limiting** - Request rate limiting to prevent overload
- **Bulkheads** - Resource isolation for concurrent operations
- **Time Limiters** - Timeout protection for long-running operations

Configuration is managed via `application.yaml` with health indicators exposed through Actuator.

**Example Configuration:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      keycloakService:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
```

## Testing

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=GuestServiceTest

# Run with coverage report
mvn test jacoco:report
```

Tests are located in `src/test/java/com/planify/guest/` and include:

- `GuestServiceTest` - Invitation management and RSVP logic
- Integration tests for Kafka event processing