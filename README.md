# Clojure Event Sourcing

This repository demonstrates a practical implementation of Event Sourcing in Clojure, showcasing the transition from pure event sourcing to persistent projections with schema validation.

## Concepts

### 1. Pure Event Sourcing
In this approach, the source of truth is a sequence of immutable events stored in the database. To get the current state of an entity (aggregate), we fetch all its events and "project" them into a single state object by applying them one by one.

**Key functions:**
- `insert-event!`: Persists a new event to the `events` table.
- `get-events-by-aggregate-id`: Retrieves all events for a given ID and reduces them using `apply-event` to build the current state.

### 2. Persistent Projections (Resources)
As the number of events grows, projecting them on every request can become inefficient. This project demonstrates how to maintain a "read model" or "projection" in a separate `resources` table. Whenever a new event is published, the corresponding resource is updated.

**Key features:**
- **Atomic Updates**: Events and projection updates are performed within a database transaction.
- **Pessimistic Locking**: Uses `SELECT ... FOR UPDATE` to ensure sequential processing of events for the same aggregate.
- **Schema Validation**: Uses [Malli](https://github.com/metosin/malli) to ensure that the resulting projection matches the expected structure before saving.

**Key functions:**
- `publish!`: The main entry point for adding an event and updating its projection.
- `lock-resource!`: Ensures exclusive access to the resource during update.
- `update-resource!`: Calculates the new state from missing events and validates it against a schema.

---

## Data Examples

### `events` table
This table stores every state change as a discrete event.

| id | type              | aggregate_id                           | aggregate_type | payload                                                         | created_at            |
|:---|:------------------|:---------------------------------------|:---------------|:----------------------------------------------------------------|:----------------------|
| 1  | `OrderCreated`    | `550e8400-e29b-41d4-a716-446655440000` | `Order`        | `{"items": ["x", "y"], "price": "100.45", "status": "pending"}` | `2025-12-21 21:00:00` |
| 2  | `OrderPaid`       | `550e8400-e29b-41d4-a716-446655440000` | `Order`        | `{"status": "paid", "payment_method": "CARD"}`                  | `2025-12-21 21:05:00` |
| 3  | `OrderDispatched` | `550e8400-e29b-41d4-a716-446655440000` | `Order`        | `{"status": "dispatched", "tracking_number": "TX-123"}`         | `2025-12-21 21:10:00` |

### `resources` table (Projections)
This table stores the current state, updated incrementally from events.

| id                                     | type    | last_event_id | payload                                                                                                                                      | updated_at            |
|:---------------------------------------|:--------|:--------------|:---------------------------------------------------------------------------------------------------------------------------------------------|:----------------------|
| `550e8400-e29b-41d4-a716-446655440000` | `Order` | 3             | `{"order_id": "...", "status": "dispatched", "price": "100.45", "items": ["x", "y"], "payment_method": "CARD", "tracking_number": "TX-123"}` | `2025-12-21 21:10:00` |

---

## Development

### Prerequisites
- Clojure
- PostgreSQL (or Testcontainers for tests)

### Running Tests
The project uses `testcontainers` to run a real PostgreSQL instance for integration testing.
```bash
lein test
```
