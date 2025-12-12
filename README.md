# Guest Service

Guest-facing microservice for managing event invitations, RSVP responses, and check-ins.

## Purpose

Handles all guest interactions with events:
- View received invitations
- Respond to invitations (accept/decline/maybe)
- Check in at event venues
- Track invitation view history

## Architecture

- **Port**: 8083
- **Database**: PostgreSQL (schema: guest_service)
- **Messaging**: Kafka consumer & producer
- **API Documentation**: Swagger UI at http://localhost:8083/swagger-ui.html

## Database Schema

### invitations table
| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| event_id | BIGINT | Reference to event (no FK - cross-database) |
| user_id | UUID | Reference to user in user-service |
| rsvp_status | VARCHAR(50) | PENDING, ACCEPTED, DECLINED, MAYBE |
| responded_at | TIMESTAMP | When guest responded |
| checked_in | BOOLEAN | Whether guest checked in |
| checked_in_at | TIMESTAMP | When guest checked in |
| invitation_received_at | TIMESTAMP | When invitation was created |
| last_viewed_at | TIMESTAMP | Last time guest viewed invitation |

**Constraints:**
- UNIQUE(event_id, user_id) - one invitation per guest per event

## API Endpoints

### Guest Perspective

| Action                   | Method | Endpoint                                                               |
|--------------------------|--------|-------------------------------------------------------------------------|
| Get My Invitations       | GET    | `/api/guests/my-invitations?userId={uuid}`                             |
| Get Specific Invitation  | GET    | `/api/guests/my-invitations/{eventId}?userId={uuid}`                   |
| Get My Accepted Events   | GET    | `/api/guests/my-events?userId={uuid}`                                  |
| Accept Invitation        | PUT    | `/api/guests/my-invitations/{eventId}/accept?userId={uuid}`            |
| Decline Invitation       | PUT    | `/api/guests/my-invitations/{eventId}/decline?userId={uuid}`           |
| Respond Maybe            | PUT    | `/api/guests/my-invitations/{eventId}/maybe?userId={uuid}`             |
| Check In                 | PUT    | `/api/guests/my-invitations/{eventId}/check-in?userId={uuid}`          |
| Mark as Viewed           | POST   | `/api/guests/my-invitations/{eventId}/mark-viewed?userId={uuid}`       |

### Internal API (for event-manager-service)

| Action                | Method | Endpoint                                                       |
|-----------------------|--------|-----------------------------------------------------------------|
| Get Event Invitations | GET    | `/api/guests/internal/events/{eventId}/invitations`            |
| Count Accepted Guests | GET    | `/api/guests/internal/events/{eventId}/accepted-count`         |

## Kafka Integration

### Consumed Topics

**guest-invited**
- Triggered when: Organizer invites guest in event-manager-service
- Action: Creates invitation record with PENDING status
- Payload:
```json
{
  "eventId": 1,
  "userId": "uuid-string",
  "invitedBy": "uuid-string",
  "invitedAt": "2025-12-12T10:00:00"
}
```

**guest-removed**
- Triggered when: Organizer removes guest in event-manager-service
- Action: Deletes invitation record
- Payload:
```json
{
  "eventId": 1,
  "userId": "uuid-string",
  "removedBy": "uuid-string",
  "removedAt": "2025-12-12T10:00:00"
}
```

**event-deleted**
- Triggered when: Event is deleted in event-manager-service
- Action: Deletes all invitations for that event
- Payload:
```json
{
  "eventId": 1,
  "deletedAt": "2025-12-12T10:00:00"
}
```

### Published Topics

**rsvp-accepted**
- Triggered when: Guest accepts invitation
- Consumed by: event-manager-service (increments current_attendees)
- Payload:
```json
{
  "eventId": 1,
  "userId": "uuid-string",
  "wasAccepted": false,
  "timestamp": "2025-12-12T10:00:00"
}
```

**rsvp-declined**
- Triggered when: Guest declines invitation
- Consumed by: event-manager-service (decrements current_attendees if wasAccepted=true)
- Payload:
```json
{
  "eventId": 1,
  "userId": "uuid-string",
  "wasAccepted": true,
  "timestamp": "2025-12-12T10:00:00"
}
```

**guest-checked-in**
- Triggered when: Guest checks in at event
- Consumed by: analytics-service (future)
- Payload:
```json
{
  "eventId": 1,
  "userId": "uuid-string",
  "timestamp": "2025-12-12T10:00:00"
}
```

## Integration with Other Services

### user-service
- Uses UUID for userId (references users table)
- No direct API calls - data sync via Kafka

### event-manager-service
- Receives invitations via Kafka (guest-invited, guest-removed)
- Sends RSVP updates via Kafka (rsvp-accepted, rsvp-declined)
- event-manager queries guest-service for invitation counts

## Running Locally

1. Start infrastructure:
```bash
cd infrastructure
docker compose up -d
```

2. Run the service:
```bash
./mvnw spring-boot:run
```

3. Access Swagger UI:
```
http://localhost:8083/swagger-ui.html
```

## Configuration

Key application properties:
- `server.port`: 8083
- `spring.datasource.url`: jdbc:postgresql://localhost:5432/planify
- `spring.jpa.properties.hibernate.default_schema`: guest_service
- `spring.kafka.bootstrap-servers`: localhost:9092
- `spring.kafka.consumer.group-id`: guest-service

## Business Logic

### RSVP Status Transitions
- PENDING → ACCEPTED (publishes rsvp-accepted with wasAccepted=false)
- PENDING → DECLINED (publishes rsvp-declined with wasAccepted=false)
- ACCEPTED → DECLINED (publishes rsvp-declined with wasAccepted=true)
- DECLINED → ACCEPTED (publishes rsvp-accepted with wasAccepted=false)

### Check-in Rules
- Guest must have ACCEPTED status to check in
- Check-in is idempotent (can be called multiple times)
- Publishes guest-checked-in event for analytics