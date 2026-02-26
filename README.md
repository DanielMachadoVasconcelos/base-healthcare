# Healthcare Appointment Service

[![Java CI with Gradle](https://github.com/DanielMachadoVasconcelos/base-healthcare/actions/workflows/gradle.yml/badge.svg)](https://github.com/DanielMachadoVasconcelos/base-healthcare/actions/workflows/gradle.yml)

A sample healthcare appointment booking service built with Spring Boot and Axon Framework to demonstrate **CQRS** and **Event Sourcing** in a practical domain.

The service supports the core appointment lifecycle:

- Book an appointment
- Confirm an appointment
- Reschedule an appointment
- Cancel an appointment
- Query appointments (read model)

## Overview

This project separates write and read responsibilities:

- **Command side**: REST API receives commands and routes them to an Axon aggregate (`AppointmentAggregate`)
- **Event sourcing**: state changes are persisted as domain events
- **Query side**: an event handler updates a JPA read model (`Appointment`) stored in PostgreSQL

This makes the project a good reference for learning:

- Axon command and query gateways
- Aggregate command handling and event sourcing handlers
- Projection/read-model updates with Spring Data JPA
- Integration testing with Spring Boot + Docker Compose

## Getting Started

### Prerequisites

- Java **25**
- Docker + Docker Compose
- Git

Notes:

- The project uses the Gradle Wrapper (`./gradlew`), so a local Gradle installation is optional.
- Docker is required for integration tests and local PostgreSQL.

### Clone the Project

```bash
git clone https://github.com/DanielMachadoVasconcelos/base-healthcare.git
cd base-healthcare
```

### Start PostgreSQL (Local)

```bash
docker compose up -d
```

Default database settings (from `compose.yml` / `application.yml`):

- Host: `localhost`
- Port: `5432`
- Database: `appointment_data`
- User: `appointment`
- Password: `appointment`

### Run the Application

For local development, this sample works best with JPA schema auto-update enabled:

```bash
SPRING_JPA_HIBERNATE_DDL_AUTO=update ./gradlew bootRun
```

The API will start on the default Spring Boot port:

- `http://localhost:8080`

## API Quick Start

Base path:

- `/api/v1/appointments`

### Book Appointment

`POST /api/v1/appointments/book`

Example request:

```bash
curl -X POST http://localhost:8080/api/v1/appointments/book \
  -H 'Content-Type: application/json' \
  -d '{
    "start_at": "2026-03-10T14:00:00Z",
    "end_at": "2026-03-10T15:00:00Z",
    "location": {
      "type_of_location": "digital",
      "url": "https://example.com/appointments/room-123"
    },
    "participants": [
      {
        "type_of_participant": "clinician",
        "id": "clinician-123"
      },
      {
        "type_of_participant": "patient",
        "id": "patient-456"
      }
    ]
  }'
```

Example response:

```json
{
  "appointment_id": "b8de7d24-4f1d-4f17-9f17-6e0f7f8f7c7c"
}
```

### Confirm Appointment

`PUT /api/v1/appointments/{appointmentId}/confirm`

### Reschedule Appointment

`PUT /api/v1/appointments/{appointmentId}/reschedule`

Example body:

```json
{
  "start_at": "2026-03-10T16:00:00Z",
  "end_at": "2026-03-10T17:00:00Z"
}
```

### Cancel Appointment

`PUT /api/v1/appointments/{appointmentId}/cancel`

### List Appointments (Read Model)

`GET /api/v1/appointments/`

## Running Tests Locally

### Run the Full Test Suite

```bash
./gradlew build
```

This runs unit + integration tests.

### Run Only Tests

```bash
./gradlew test
```

### Notes About Tests

- The integration test uses Spring Boot Docker Compose support and starts PostgreSQL automatically.
- Test config is set to `START_ONLY`, so the database container may remain running after the test completes.

Cleanup if needed:

```bash
docker compose down -v
```

## Technical Notes

### Stack

- Java 25
- Spring Boot 4.0.3
- Gradle Wrapper 9.3.1
- Axon Framework 4.13.0
- Spring Data JPA / Hibernate ORM
- PostgreSQL (Docker Compose uses `postgres:18.2`)
- JUnit 5 + MockMvc + Axon test fixtures

### CQRS / Event Sourcing Flow

1. `AppointmentsController` receives an HTTP request
2. The controller sends a command through Axon `CommandGateway`
3. `AppointmentAggregate` validates business rules and applies domain events
4. `AppointmentEventHandler` updates the JPA read model (`Appointment`)
5. Query endpoints use Axon `QueryGateway` and the projection-backed query handler

### Domain Rules Implemented (Examples)

- Appointment must be in the future (minimum lead time)
- Appointment must have a valid time range
- Appointment duration must be at least 15 minutes
- Appointment must have a location
- Appointment must have at least 2 participants

### JSON / API Model Notes

- JSON is configured in `snake_case`
- `Location` is polymorphic and supports:
  - `digital`
  - `physical`
- `Participant` is polymorphic and supports:
  - `clinician`
  - `patient`

## Project Structure (High Level)

```text
src/main/java/br/ead/axon
├── configurations/        # Axon/Spring configuration
├── controllers/           # REST API endpoints
├── messages/              # Axon aggregate, commands, events
├── model/
│   ├── api/               # API/domain polymorphic types (location, participant)
│   ├── entities/          # JPA read model entities
│   ├── querry/            # Query DTOs (note: package name is "querry" in source)
│   └── requests/          # HTTP request payloads
├── repositories/          # Spring Data repositories
└── services/              # Projection event handlers + query handlers
```

## Current Limitations / Next Steps

- Add authentication/authorization
- Add OpenAPI/Swagger documentation
- Expand query API (filtering / richer queries)
- Publish domain events to external systems (message broker)

## Useful Commands

```bash
# Start PostgreSQL
docker compose up -d

# Stop and remove DB + volume
docker compose down -v

# Run app (with local schema auto-update)
SPRING_JPA_HIBERNATE_DDL_AUTO=update ./gradlew bootRun

# Run full build
./gradlew build
```
