# 🐢 Tortoise

*Slow and fair wins the race.*

A fair, bot-proof and broker-proof way to give out anything limited: match tickets, concert seats,
workshop places, trips.

Most ticketing works as "first to click wins", so bots, brokers and fast connections win. Like the
tortoise in the fable, Tortoise doesn't reward speed: registration stays open for a window, winners
are picked by a provably fair draw, tickets are tied to the winner, and unclaimed tickets go to the
waitlist automatically.

> **Status:** Level 1 complete. Event management with in-memory storage, review fixes and a
> concurrency test.

## Run it

Requirements: JDK 21+.

```bash
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`. Open `requests.http` in IntelliJ to try every endpoint,
including each error case. Storage is in memory, so restarting the app wipes all data.

## Event lifecycle

```
DRAFT ──publish──► PUBLISHED
  │                    │
  └──────cancel────────┴──► CANCELLED
```

| Status | What can change |
|---|---|
| `DRAFT` | Any field (all rules are checked again) |
| `PUBLISHED` | Only `description` |
| `CANCELLED` | Nothing |

Whether registration is open (`registrationOpen` in responses) is computed from the time window,
not stored.

## Endpoints

| Method | Path | Success | Errors |
|---|---|---|---|
| `POST` | `/events` | `201` + `Location` | `400`, `409 DUPLICATE_EVENT` |
| `GET` | `/events?from&to&status&page&size` | `200` (empty list is still `200`) | `400` |
| `GET` | `/events/{id}` | `200` | `404` |
| `PATCH` | `/events/{id}` | `200` | `400`, `404`, `409` |
| `POST` | `/events/{id}/publish` | `200` | `400`, `404`, `409` |
| `POST` | `/events/{id}/cancel` | `200` | `404`, `409` |

There is no `DELETE`: events are cancelled, never erased.

## Rules

1. All dates are ISO-8601 with an offset (`2027-01-10T19:00:00Z`) and stored in UTC.
2. All dates must be in the future when an event is created, edited or published.
3. `registrationOpensAt < registrationClosesAt < eventStartsAt < eventEndsAt`.
4. Registration closes at least 24 hours before the event starts, to leave time for the draw.
5. `totalSeats` is between 1 and 200,000.
6. No two active events share the same name and venue (case-insensitive) on the same UTC day.
   The repository checks this and writes in one atomic step, so parallel requests can't both get
   through. Cancelling an event frees its name.
7. Unknown JSON fields are rejected with `400`, never silently ignored. Status changes only go
   through `/publish` and `/cancel`.

## Tests

```bash
./mvnw test
```

`EventServiceTest` fires 50 parallel creates of the same event (10 rounds) and expects exactly one
winner. It also covers duplicate edits and cancelled events freeing their name.

## Error format

Every error uses the same shape:

```json
{
  "status": 400,
  "error": "VALIDATION_FAILED",
  "message": "Request has invalid fields",
  "path": "/events",
  "timestamp": "2026-11-02T12:00:00Z",
  "fieldErrors": [{ "field": "totalSeats", "message": "must be greater than or equal to 1" }]
}
```

| Code | Status |
|---|---|
| `VALIDATION_FAILED`, `MALFORMED_REQUEST` | 400 |
| `EVENT_NOT_FOUND` | 404 |
| `DUPLICATE_EVENT`, `INVALID_STATE_TRANSITION` | 409 |
| `INTERNAL_ERROR` | 500 (details are logged, never sent to the client) |

## Project layout

```
com.tortoise
├── event        controller → service → repository, plus DTOs
└── common       paging, clock, and the global error handling
```

The repository sits behind an interface (`EventRepository`), so Level 2 can replace the in-memory
store with PostgreSQL without touching the service or controller.

## Troubleshooting

- **`PKIX path building failed` while Maven downloads dependencies:** a proxy or antivirus is
  inspecting HTTPS. `.mvn/jvm.config` already tells Maven to trust the Windows certificate store.
- **`Unable to establish loopback connection` on startup:** add
  `-Djdk.net.unixdomain.tmpdir=<a short local folder>` to the JVM options.
