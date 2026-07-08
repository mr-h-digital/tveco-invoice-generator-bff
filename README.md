<p align="center">
	<img src="assets/tveco-logo.png" alt="TVECO Logo" width="180" />
</p>

<h1 align="center">TVECO Operations Hub BFF</h1>

<p align="center">
  <strong>Backend-for-Frontend API powering TVECO Operations Hub for Timeline Vehicle Export Company (Pty) Ltd.</strong>
</p>

<p align="center">
	<a href="https://tveco.co.za">tveco.co.za</a>
	·
	<a href="mailto:enquiries@tveco.co.za">enquiries@tveco.co.za</a>
</p>

<p align="center">
	<img src="https://img.shields.io/badge/java-21-ED8B00?logo=openjdk&logoColor=white" alt="Java 21" />
	<img src="https://img.shields.io/badge/spring_boot-3.3.5-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 3.3.5" />
	<img src="https://img.shields.io/badge/postgresql-16-4169E1?logo=postgresql&logoColor=white" alt="PostgreSQL" />
	<img src="https://img.shields.io/badge/flyway-migrations-CC0200?logo=flyway&logoColor=white" alt="Flyway" />
	<img src="https://img.shields.io/badge/deploy-railway-0B0D0E?logo=railway&logoColor=white" alt="Railway" />
	<img src="https://img.shields.io/badge/license-proprietary-FF6B00" alt="License" />
</p>

---

<p align="center">
	<img src="assets/tveco-nav-bg.jpg" alt="TVECO vehicles" style="width:100%;border-radius:8px;" />
</p>

## Overview

This repository contains the Spring Boot Backend-for-Frontend (BFF) for **TVECO Operations Hub** — the internal commercial operations system used by Timeline Vehicle Export Company (Pty) Ltd.

The BFF acts as the persistence and business-logic layer behind the React web UI. When the front-end is configured with `VITE_USE_API=true`, all invoice and client data flows through this API instead of `localStorage`.

The API provides:

- Full CRUD for invoices and export clients
- Export Jobs pipeline APIs (create/update/tracking/delete)
- In-app notifications and email outbox APIs
- Invoice duplication and status lifecycle management
- Auto-generated sequential invoice numbers (`TVECO-YYYY-NNN`)
- Analytics endpoint for dashboard revenue and status summaries
- Schema-managed PostgreSQL database via Flyway migrations
- Health check endpoint compatible with Railway deployment

---

## Features

- **Invoice management** — create, read, update, delete, and duplicate invoices with full line-item support
- **Quote management** — create, read, update, delete, and duplicate quotes with full line-item support
- **Status lifecycle** — `DRAFT → SENT → PAID / OVERDUE` transitions via a dedicated PATCH endpoint
- **Client management** — full CRUD for saved export clients; client snapshot is embedded in each invoice at creation time so records are preserved if a client is later deleted
- **Auto invoice numbering** — generates the next `TVECO-YYYY-NNN` number based on the highest existing invoice for the current year
- **Auto quote numbering** — generates the next `QUO-YYYY-NNN` number based on the highest existing quote for the current year
- **Export Jobs APIs** — supports operational pipeline records with milestones, document checklist, payment milestones, vault metadata, and public tracking token lookup
- **Notifications + outbox APIs** — supports in-app notification feed, unread count, outbox queue stats/list, retry, cleanup, and dispatch
- **Analytics API** — aggregates total revenue, outstanding amount, invoice counts by status, and monthly revenue breakdown for a configurable date range (defaults to last 6 months)
- **Validation** — Bean Validation on all request bodies; a `GlobalExceptionHandler` returns consistent `ApiResponse` error envelopes
- **CORS** — configurable allowed origins via `CORS_ALLOWED_ORIGINS` environment variable
- **Railway-ready** — `railway.toml` configures Nixpacks build, health check path, and restart policy out of the box

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.5 |
| Web | Spring MVC (REST) |
| Persistence | Spring Data JPA + Hibernate |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Validation | Jakarta Bean Validation |
| Boilerplate | Lombok |
| Build | Maven 3 |
| Test DB | H2 (in-memory, test scope) |
| Deploy | Railway (Nixpacks) |
| Health | Spring Boot Actuator |

---

## Project Structure

```text
tveco-invoice-generator-bff/
├── pom.xml
├── railway.toml
├── .gitignore
├── README.md
│
└── src/
    ├── main/
    │   ├── java/co/za/tveco/bff/
    │   │   ├── TvecoInvoiceGeneratorBffApplication.java   # Entry point
    │   │   │
    │   │   ├── config/
    │   │   │   └── CorsConfig.java                        # Allowed origins from env var
    │   │   │
    │   │   ├── controller/
    │   │   │   ├── InvoiceController.java                 # /api/invoices
    │   │   │   ├── QuoteController.java                   # /api/quotes
    │   │   │   ├── ClientController.java                  # /api/clients
    │   │   │   ├── ExportJobController.java               # /api/export-jobs
    │   │   │   ├── NotificationController.java            # /api/notifications
    │   │   │   └── AnalyticsController.java               # /api/analytics
    │   │   │
    │   │   ├── dto/
    │   │   │   ├── ApiResponse.java                       # Uniform response envelope
    │   │   │   ├── InvoiceDto.java
    │   │   │   ├── InvoiceRequest.java
    │   │   │   ├── ClientDto.java
    │   │   │   ├── ClientRequest.java
    │   │   │   ├── ClientSnapshotDto.java
    │   │   │   ├── LineItemDto.java
    │   │   │   ├── LineItemRequest.java
    │   │   │   ├── AnalyticsDto.java
    │   │   │   ├── NextInvoiceNumberDto.java
    │   │   │   ├── PaymentDetailsDto.java
    │   │   │   └── StatusUpdateRequest.java
    │   │   │
    │   │   ├── entity/
    │   │   │   ├── Invoice.java
    │   │   │   ├── Quote.java
    │   │   │   ├── Client.java
    │   │   │   ├── LineItem.java
    │   │   │   ├── QuoteLineItem.java
    │   │   │   ├── ExportJob.java
    │   │   │   ├── AppNotification.java
    │   │   │   └── EmailOutboxMessage.java
    │   │   │
    │   │   ├── exception/
    │   │   │   ├── GlobalExceptionHandler.java            # @ControllerAdvice error handling
    │   │   │   ├── ResourceNotFoundException.java         # 404
    │   │   │   └── ConflictException.java                 # 409
    │   │   │
    │   │   ├── repository/
    │   │   │   ├── InvoiceRepository.java
    │   │   │   ├── QuoteRepository.java
    │   │   │   ├── ClientRepository.java
    │   │   │   ├── ExportJobRepository.java
    │   │   │   ├── AppNotificationRepository.java
    │   │   │   └── EmailOutboxRepository.java
    │   │   │
    │   │   └── service/
    │   │       ├── InvoiceService.java
    │   │       ├── QuoteService.java
    │   │       ├── InvoiceCalculator.java                 # Subtotal / VAT / total logic
    │   │       ├── ClientService.java
    │   │       ├── ExportJobService.java
    │   │       ├── NotificationService.java
    │   │       └── AnalyticsService.java
    │   │
    │   └── resources/
    │       ├── application.properties
    │       └── db/migration/
    │           └── V1__init_schema.sql                    # Tables: clients, invoices, line_items
    │           ├── V2__add_quotes.sql                     # Tables: quotes, quote_line_items
    │           └── V3__add_export_jobs_and_notifications.sql
    │
    └── test/
        ├── java/co/za/tveco/bff/
        │   └── TvecoInvoiceGeneratorBffApplicationTests.java
        └── resources/
            └── application-test.properties                # H2 in-memory datasource
```

---

<p align="center">
	<img src="assets/tveco-invoices-bg.jpg" alt="TVECO invoices" style="width:100%;border-radius:8px;" />
</p>

## API Reference

All endpoints return a uniform JSON envelope:

```json
{
  "success": true,
  "data": { ... }
}
```

Errors return `"success": false` with an `"error"` field and the appropriate HTTP status.

### Invoices — `/api/invoices`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/invoices` | List all invoices (paginated, newest first) |
| `GET` | `/api/invoices/{id}` | Get single invoice by UUID |
| `GET` | `/api/invoices/next-number` | Get the next auto-generated invoice number |
| `POST` | `/api/invoices` | Create a new invoice |
| `PUT` | `/api/invoices/{id}` | Full update of an invoice |
| `PATCH` | `/api/invoices/{id}/status` | Update invoice status only |
| `POST` | `/api/invoices/{id}/duplicate` | Duplicate an invoice as a new DRAFT |
| `DELETE` | `/api/invoices/{id}` | Delete an invoice |

### Quotes — `/api/quotes`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/quotes` | List all quotes (paginated, newest first) |
| `GET` | `/api/quotes/{id}` | Get single quote by UUID |
| `GET` | `/api/quotes/next-number` | Get the next auto-generated quote number |
| `POST` | `/api/quotes` | Create a new quote |
| `PUT` | `/api/quotes/{id}` | Full update of a quote |
| `PATCH` | `/api/quotes/{id}/status` | Update quote status only |
| `POST` | `/api/quotes/{id}/duplicate` | Duplicate a quote as a new DRAFT |
| `DELETE` | `/api/quotes/{id}` | Delete a quote |

### Clients — `/api/clients`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/clients` | List all clients |
| `GET` | `/api/clients/{id}` | Get single client by UUID |
| `POST` | `/api/clients` | Create a new client |
| `PUT` | `/api/clients/{id}` | Full update of a client |
| `DELETE` | `/api/clients/{id}` | Delete a client |

### Analytics — `/api/analytics`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/analytics` | Revenue summary for a date range |

Query parameters: `from` and `to` (ISO date, e.g. `2026-01-01`). Both are optional and default to the last 6 months.

### Export Jobs — `/api/export-jobs`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/export-jobs` | List all export jobs |
| `POST` | `/api/export-jobs` | Create a new export job |
| `PATCH` | `/api/export-jobs/{id}` | Partial update for export job fields |
| `DELETE` | `/api/export-jobs/{id}` | Delete an export job |
| `GET` | `/api/export-jobs/tracking/{token}` | Lookup by public tracking token |

### Notifications — `/api/notifications`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/notifications` | List notifications (newest first) |
| `PATCH` | `/api/notifications/{id}/read` | Mark notification as read |
| `POST` | `/api/notifications/emit` | Emit notification and optionally queue outbox email |
| `GET` | `/api/notifications/unread-count` | Fetch unread notification count |
| `GET` | `/api/notifications/outbox/stats` | Fetch outbox status counts |
| `GET` | `/api/notifications/outbox` | List outbox messages |
| `POST` | `/api/notifications/outbox/{id}/retry` | Reset a failed message to pending |
| `DELETE` | `/api/notifications/outbox/sent` | Delete all sent outbox messages |
| `POST` | `/api/notifications/outbox/dispatch` | Dispatch pending outbox emails to webhook |

### Auth — `/api/auth`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/auth/login` | Validate admin credentials for UI login |

### Auth — `/api/auth`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/auth/login` | Validate admin credentials for UI login |

### Health — `/actuator/health`

Returns `{ "status": "UP" }`. Used by Railway for health checks.

---

## Invoice Number Format

```
TVECO-YYYY-NNN
```

Example: `TVECO-2026-004`

Numbers are sequential per calendar year and generated by querying the highest existing invoice number for the current year.

---

## Invoice Status Lifecycle

```
DRAFT  →  SENT  →  PAID
                →  OVERDUE
```

| Status | Meaning |
|---|---|
| `DRAFT` | Created but not yet sent to the client |
| `SENT` | Issued to the client, awaiting payment |
| `PAID` | Payment received |
| `OVERDUE` | Past due date with no payment |

---

<p align="center">
	<img src="assets/tveco-dashboard-bg.jpg" alt="TVECO dashboard" style="width:100%;border-radius:8px;" />
</p>

## Database Schema

Three tables, managed by Flyway (`V1__init_schema.sql`):

| Table | Description |
|---|---|
| `clients` | Export client records; `email` unique where non-empty |
| `invoices` | Invoice header, totals, payment details, and a client snapshot |
| `line_items` | Invoice line items; cascade-deleted with their parent invoice |

The `invoices` table stores a **snapshot** of the client's details at the time of creation (`snap_*` columns). This preserves the invoice record accurately even if the client is later edited or deleted. A nullable `client_id` FK links to the live client record for reference.

---

<p align="center">
	<img src="assets/tveco-clients-bg.jpg" alt="TVECO clients" style="width:100%;border-radius:8px;" />
</p>

## Local Setup

### Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 16 (running locally or via Docker)

### Database

Create a local database:

```sql
CREATE DATABASE tveco;
```

### Configure

Copy the defaults from `application.properties`. For local overrides, create `application-local.properties` (already gitignored):

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/tveco
spring.datasource.username=your_user
spring.datasource.password=your_password
```

Then run with the `local` profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Build & run

```bash
./mvnw spring-boot:run
```

Or build a JAR and run it directly:

```bash
./mvnw clean package -DskipTests
java -jar target/tveco-invoice-generator-bff-1.0.0.jar
```

API runs at **http://localhost:8080**

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/tveco` | Full PostgreSQL JDBC URL (Railway sets this automatically) |
| `PORT` | `8080` | HTTP port the server binds to |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:4173` | Comma-separated list of allowed front-end origins |
| `NOTIFICATION_WEBHOOK_URL` | _(empty)_ | Outbox dispatch webhook URL (optional) |
| `NOTIFICATION_WEBHOOK_SECRET` | _(empty)_ | Shared secret header value for outbox webhook |

On Railway, `DATABASE_URL` is injected automatically when a PostgreSQL service is linked to this deployment.

---

## Connecting the Front-End

Set these variables in the web UI's `.env.local`:

```env
VITE_USE_API=true
VITE_API_URL=http://localhost:8080/api
```

In production:

```env
VITE_USE_API=true
VITE_API_URL=https://your-railway-app.railway.app/api
```

---

## Deployment (Railway)

The included `railway.toml` configures everything:

```toml
[build]
builder = "NIXPACKS"

[deploy]
startCommand = "java -jar target/tveco-invoice-generator-bff-1.0.0.jar"
healthcheckPath = "/actuator/health"
healthcheckTimeout = 60
restartPolicyType = "ON_FAILURE"
restartPolicyMaxRetries = 3
```

Steps:

1. Push this repository to GitHub
2. Create a new Railway project → **Deploy from GitHub repo**
3. Add a **PostgreSQL** service and link it to this deployment
4. Set `CORS_ALLOWED_ORIGINS` to your deployed front-end URL
5. Railway injects `DATABASE_URL` and `PORT` automatically
6. Flyway runs migrations on first boot — no manual setup required

---

## Running Tests

```bash
./mvnw test
```

Tests use an H2 in-memory database (configured in `application-test.properties`) so no PostgreSQL instance is required.

Key integration coverage now includes:

- Export Jobs lifecycle (create, list, patch status, tracking lookup, delete)
- Notifications and outbox lifecycle (emit, unread count, mark read, outbox list/stats, retry, dispatch, clear sent)

---

## Notes

- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate validates the schema against the entities but never mutates it. All schema changes go through Flyway versioned migrations.
- `spring.jpa.open-in-view=false` — disabled to avoid lazy-loading anti-patterns; all data is fetched within the service layer.
- The Actuator exposes only `health` and `info` — no sensitive endpoints are publicly reachable.

---

## Credits

- Client: Timeline Vehicle Export Company (Pty) Ltd
- Development and branding: Mr. H Digital

---

<p align="center">
	<strong>Development Signature</strong>
</p>

<p align="center">
	<img src="assets/mrh-digital-logo.png" alt="Mr. H Digital" width="260" />
</p>

<p align="center">
	Designed and developed by <a href="https://mrhdigital.co.za" target="_blank" rel="noopener noreferrer"><strong>Mr. H Digital</strong></a>
</p>
