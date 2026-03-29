# Fever Code Challenge

A microservice that integrates event plans from an external provider into the Fever marketplace, exposing a performant search endpoint.

---

## Table of Contents

- [Quick Start](#quick-start)
- [API Reference](#api-reference)
- [Architecture](#architecture)
- [Design Decisions](#design-decisions)
- [Project Structure](#project-structure)
- [Configuration](#configuration)
- [Testing](#testing)
- [Trade-offs and Future Improvements](#trade-offs-and-future-improvements)
- [AI Usage](#ai-usage)

---

## Quick Start

### Prerequisites

- **Java 17+** (Maven wrapper included, no separate Maven install needed)
- **Docker** (optional, for containerized execution)

### Run

```bash
# Option 1: Using Make
make run

# Option 2: Using Docker
make docker-run

# Option 3: Manual
./mvnw clean package -DskipTests && java -jar target/*.jar
```

The service starts on **http://localhost:8080** and immediately begins syncing events from the provider in the background.

---

## API Reference

### `GET /search`

Returns events whose start and end dates fall within the given time range.

| Parameter   | Type       | Required | Description                        |
|-------------|------------|----------|------------------------------------|
| `starts_at` | `datetime` | No*      | Return events starting after this  |
| `ends_at`   | `datetime` | No*      | Return events ending before this   |

\* If one is provided, both must be provided. If neither is provided, all events are returned.

#### Example Requests

```bash
# Search events in a date range
curl "http://localhost:8080/search?starts_at=2021-01-01T00:00:00&ends_at=2021-12-31T23:59:59"

# Get all events (no filters)
curl "http://localhost:8080/search"
```

#### Success Response (200)

```json
{
  "data": {
    "events": [
      {
        "id": "8f61b1df-1fe1-3401-977c-09bea04acbb2",
        "title": "Camela en concierto",
        "start_date": "2021-06-30",
        "start_time": "21:00:00",
        "end_date": "2021-06-30",
        "end_time": "22:00:00",
        "min_price": 15.00,
        "max_price": 30.00
      }
    ]
  },
  "error": null
}
```

#### Error Response (400)

```json
{
  "data": null,
  "error": {
    "code": "BAD_REQUEST",
    "message": "Both 'starts_at' and 'ends_at' must be provided, or neither"
  }
}
```

---

## Architecture

### High-Level Overview

```
                          ┌─────────────────────────────────────────┐
                          │            Fever Service                 │
                          │                                         │
┌──────────────┐          │  ┌──────────────┐    ┌──────────────┐  │          ┌──────────┐
│   External   │  XML     │  │  Provider    │    │   Event      │  │  JSON    │  Client  │
│   Provider   │◄─────────│  │  Client      │    │   Search     │──│─────────►│  (API    │
│   (XML API)  │  (async) │  │  (retry x3)  │    │   Service    │  │          │  Consumer│
└──────────────┘          │  └──────┬───────┘    └──────┬───────┘  │          └──────────┘
                          │         │                    │          │
                          │         │ write              │ read     │
                          │         ▼                    ▼          │
                          │  ┌─────────────────────────────────┐   │
                          │  │         H2 Database              │   │
                          │  │  (file-based, survives restart)  │   │
                          │  └─────────────────────────────────┘   │
                          │                                         │
                          │  ┌──────────────┐                      │
                          │  │  Scheduler   │ triggers sync every  │
                          │  │  (30s)       │ 30 seconds           │
                          │  └──────────────┘                      │
                          └─────────────────────────────────────────┘
```

### Data Flow

```
1. SYNC (Background, every 30s)
   Provider API ──► XML Response ──► Parse (JAXB) ──► Filter (online only) ──► Upsert to DB

2. SEARCH (On request, hits DB only)
   GET /search ──► Query DB by date range ──► Map to DTOs ──► JSON Response
```

The key insight is that these two flows are **completely decoupled**. The search endpoint never touches the external provider. This is what guarantees sub-100ms response times regardless of provider state.

---

## Design Decisions

### 1. Background Sync + Local Database

**Problem:** The endpoint must respond in hundreds of milliseconds, even if the provider is slow or down.

**Solution:** Decouple the data ingestion from the data serving. A scheduled background job fetches from the provider and writes to a local database. The search endpoint reads only from the local database.

**Why this works:**
- Provider latency (which can be several seconds) never impacts search response time.
- If the provider goes down, we serve stale-but-available data instead of returning errors.
- Traffic spikes on our API don't cascade to the provider.

This is essentially a simplified **CQRS** (Command Query Responsibility Segregation) pattern -- writes (sync) and reads (search) are separated.

### 2. H2 File-Based Database

**Why H2:** Zero-configuration embedded database. No external infrastructure to manage. File-based mode (`jdbc:h2:file:./data/feverdb`) means data survives application restarts.

**Why not an in-memory store:** Events must persist across restarts (past plans should remain searchable). A Map or in-memory cache would lose data on restart.

**Production swap:** Since all DB access goes through JPA/Spring Data, switching to PostgreSQL requires only a dependency change and a config update -- no code changes.

### 3. Only `sell_mode="online"` Events Stored

The requirements state that plans should be included if they were "ever available with sell_mode: online." The sync service filters at ingestion time, discarding `offline` events entirely. This keeps the database clean and queries fast.

Example from the provider data: "Tributo a Juanito Valderrama" has `sell_mode="offline"` and is correctly excluded.

### 4. Events Are Never Deleted (Append/Update Only)

The requirements state: "Past plans should be retrievable even if they are no longer present in the provider's latest response."

The sync job **upserts** events (inserts new ones, updates existing ones) but never deletes. If an event disappears from the provider response (e.g., "Pantomima Full" disappears in Response 2), it remains in our database and is still searchable.

### 5. Deterministic UUIDs

**Problem:** The API spec requires UUID identifiers, but the provider uses numeric IDs (`base_plan_id`, `plan_id`).

**Solution:** Generate UUIDs deterministically from `basePlanId:planId` using `UUID.nameUUIDFromBytes()` (UUID v3/MD5-based). This guarantees:
- The same event always produces the same UUID.
- Clients can rely on stable identifiers across requests.
- No need for a separate ID mapping table.

### 6. Retry Logic with Backoff

The challenge warns that the provider API may not always respond successfully. The `ProviderClient` handles this with:
- **3 retry attempts** per sync cycle
- **Configurable delay** between retries (default 2s)
- **Connection timeout** (5s) and **read timeout** (10s) to prevent hanging
- Graceful fallback: if all retries fail, sync is skipped (existing data remains intact)

### 7. No Pagination

The OpenAPI spec defines no pagination parameters (`page`, `limit`, `offset`). Adding them would deviate from the spec. Given the bounded dataset size (the provider returns a small number of events), pagination adds complexity without solving a real problem. If the dataset were to grow significantly, pagination would be straightforward to add via Spring Data's `Pageable` support.

---

## Project Structure

```
src/main/java/com/fever/challenge/
├── FeverChallengeApplication.java      # Entry point, @EnableScheduling
│
├── config/
│   └── AppConfig.java                  # RestTemplate bean with timeouts
│
├── client/
│   ├── ProviderClient.java             # HTTP client: fetch + retry + XML parse
│   └── ProviderXmlModels.java          # JAXB models for XML deserialization
│
├── entity/
│   └── EventEntity.java               # JPA entity, DB table definition
│
├── repository/
│   └── EventRepository.java           # Spring Data JPA, date range query
│
├── service/
│   ├── EventSyncService.java          # @Scheduled: provider -> DB (write path)
│   └── EventSearchService.java        # DB -> DTOs (read path)
│
├── dto/
│   ├── ApiResponse.java               # Generic {data, error} wrapper
│   ├── EventListDto.java              # {events: [...]}
│   └── EventSummaryDto.java           # Single event fields per OpenAPI spec
│
└── controller/
    ├── EventSearchController.java      # GET /search endpoint
    └── GlobalExceptionHandler.java     # Centralized error handling (400, 500)
```

---

## Configuration

All settings are externalized in `src/main/resources/application.yml`:

| Property                     | Default   | Description                          |
|------------------------------|-----------|--------------------------------------|
| `provider.url`               | `https://provider.code-challenge.feverup.com/api/events` | External provider endpoint |
| `provider.sync-interval-ms`  | `30000`   | How often to sync (30 seconds)       |
| `provider.connect-timeout-ms`| `5000`    | TCP connection timeout               |
| `provider.read-timeout-ms`   | `10000`   | Response read timeout                |
| `provider.max-retries`       | `3`       | Max retry attempts per sync          |
| `provider.retry-delay-ms`    | `2000`    | Wait between retries                 |

All values can be overridden via environment variables (e.g., `PROVIDER_SYNC_INTERVAL_MS=60000`).

---

## Testing

```bash
# Run all tests
make test

# Or directly
./mvnw test
```

### Test Coverage

| Test Class                      | Tests | What it covers                                       |
|---------------------------------|-------|------------------------------------------------------|
| `EventSearchControllerTest`     | 7     | Endpoint: date range, no params, bad input, response shape |
| `EventSearchServiceTest`        | 2     | UUID determinism, date/time field splitting           |
| `XmlParsingTest`                | 2     | JAXB deserialization of provider XML, price extraction|

**Total: 11 tests, all passing.**

The controller tests use `@SpringBootTest` with `MockMvc` for full integration coverage. The test profile uses an in-memory H2 database (`jdbc:h2:mem:testdb`) and disables the sync scheduler to isolate test behavior.

---

## Trade-offs and Future Improvements

| Area | Current State | Production Enhancement |
|---|---|---|
| **Database** | H2 (embedded, file-based) | PostgreSQL or MySQL for concurrency, durability, and tooling |
| **Caching** | None (DB queries are fast enough) | Caffeine or Redis for sub-ms responses under heavy load |
| **Sync strategy** | Full fetch every 30s | Incremental sync with ETags or If-Modified-Since headers |
| **Multi-instance** | Single instance assumed | ShedLock or distributed lock to prevent duplicate sync jobs |
| **Observability** | Application logs only | Micrometer metrics, health checks (`/actuator/health`), structured JSON logging |
| **Pagination** | Not implemented (spec doesn't define it) | Add `Pageable` support if dataset grows significantly |
| **Rate limiting** | None | Add rate limiting to protect against abuse |
| **API versioning** | Not needed yet | URL path versioning (`/v1/search`) when breaking changes arise |

---

## AI Usage

AI tools (Claude via OpenCode) were used to assist with:
- Scaffolding the initial project structure and Maven configuration
- Generating boilerplate code (JPA entities, DTOs, JAXB models)
- Writing test cases

All design decisions -- architecture, data flow, filtering logic, UUID generation strategy, error handling -- were made deliberately and are fully understood. The README was written to reflect genuine reasoning, not generated without review.
