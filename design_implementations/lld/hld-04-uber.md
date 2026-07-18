# HLD 04 — Ride-Sharing System (Uber)

> **Design a ride-sharing platform supporting rider requests, driver matching, real-time location tracking, trip lifecycle, and surge pricing.**

---

## Clarify Scale First

```
DAU: 5 million riders + 1 million drivers
Ride requests: ~100,000/min peak
Location updates: every 5 sec per driver → 1M drivers ÷ 5 = 200,000 updates/sec
Matching latency SLA: find driver within 5 seconds
```

---

## High-Level Components

```
[ Rider App ]  [ Driver App ]
      │               │
      ▼               ▼
[ API Gateway / Load Balancer ]
      │
      ├──────────────────────────────────┐
      ▼                                  ▼
[ Ride Request Service ]      [ Location Service ]
      │                                  │
      ▼                                  ▼
[ Matching Service ]          [ Redis (Geospatial) ]
      │
      ├────────────────────┐
      ▼                    ▼
[ Trip Service ]    [ Pricing Service ]
      │
      ▼
[ Notification Service ]
(WebSocket / Push)
```

---

## Core Design — Geospatial Matching

### The Problem
```
Rider requests a ride at location (lat, lng).
Find all available drivers within 5km radius.
Must be fast — < 200ms.

Naive: scan all 1M driver locations, compute distance → O(n) ❌
```

### Geohashing — The Solution

```
Geohash divides the world into a grid of cells.
Each cell has a string key: longer string = smaller cell.

Example:
  Pallavaram, Chennai → Geohash: "tf0n8"  (precision 5 = ~5km cell)

To find nearby drivers:
  1. Compute rider's geohash
  2. Look up drivers in same cell + 8 neighboring cells
  3. Filter by exact distance

In Redis:
  GEOADD drivers:available {lng} {lat} {driverId}
  GEORADIUS drivers:available {lng} {lat} 5 km ASC COUNT 10
  → Returns 10 nearest drivers within 5km, sorted by distance
```

---

## Location Update Flow

```
Driver app sends location every 5 seconds:
  POST /location { driverId, lat, lng, status }

Location Service:
  1. Update Redis:  GEOADD drivers:available {lng} {lat} {driverId}
  2. Publish to Kafka: topic = "driver.location.updates"
  3. Trip Service subscribes → sends real-time location to rider via WebSocket
```

---

## Ride Request & Matching Flow

```
Rider requests ride:
  POST /ride/request { riderId, pickupLat, pickupLng, destLat, destLng }

Matching Service:
  1. GEORADIUS → find 10 nearest available drivers
  2. Score each driver: distance + acceptance rate + rating
  3. Offer to best driver (push notification via WebSocket)
  4. If driver accepts → create Trip, notify rider
  5. If driver rejects or no response in 10s → offer to next driver
  6. Repeat until matched or timeout (no drivers available)
```

---

## Trip Lifecycle (State Machine)

```
REQUESTED → DRIVER_ASSIGNED → DRIVER_ARRIVING → TRIP_STARTED → COMPLETED
                                                              → CANCELLED
```

```java
public enum TripStatus {
    REQUESTED, DRIVER_ASSIGNED, DRIVER_ARRIVING,
    TRIP_STARTED, COMPLETED, CANCELLED
}
```

---

## Architecture Diagram

```
Driver App                        Rider App
    │ (location every 5s)             │ (ride request)
    ▼                                 ▼
[ Location Service ]         [ Ride Request Service ]
    │                                 │
    ▼                                 ▼
[ Redis Geo ]  ◄──────── [ Matching Service ]
    │                                 │
    │ stream                          ▼
    ▼                         [ Trip DB (PostgreSQL) ]
[ Kafka ]                            │
    │                                ▼
    ▼                    [ Notification / WebSocket ]
[ Trip Service ]              │               │
(real-time tracking)       Rider App      Driver App
                         (driver live     (navigation)
                          location)

[ Pricing Service ]
    ├── Base fare
    ├── Per km rate
    └── Surge multiplier (demand/supply ratio)
```

---

## Surge Pricing

```
surge_multiplier = demand / supply

demand = ride requests in last 5 min in geohash cell
supply = available drivers in geohash cell

if demand/supply > 2.0 → surge = 1.5x
if demand/supply > 3.0 → surge = 2.0x

Computed per geohash cell every 30 seconds.
Stored in Redis: SET surge:{geohash} 1.5 EX 30
```

---

## Database Design

```sql
-- Trips
CREATE TABLE trips (
    id            UUID PRIMARY KEY,
    rider_id      VARCHAR(50),
    driver_id     VARCHAR(50),
    status        VARCHAR(30),
    pickup_lat    DECIMAL(9,6),
    pickup_lng    DECIMAL(9,6),
    dest_lat      DECIMAL(9,6),
    dest_lng      DECIMAL(9,6),
    fare          DECIMAL(10,2),
    surge_mult    DECIMAL(4,2),
    started_at    TIMESTAMP,
    ended_at      TIMESTAMP,
    created_at    TIMESTAMP DEFAULT NOW()
);

-- Driver real-time state (Redis, not DB)
-- Key: driver:{driverId}:status → AVAILABLE / ON_TRIP / OFFLINE
-- Key: drivers:available (Redis GEO set)
```

---

## Real-Time Communication — WebSockets

```
Rider needs live updates:
  - Driver assigned (name, photo, car, ETA)
  - Driver location (live map)
  - Trip started / completed

Driver needs live updates:
  - New ride request
  - Navigation instructions
  - Rider cancellation

Solution: WebSocket connection from app → WebSocket Server
WebSocket Server subscribes to Kafka topic for the specific tripId
→ Pushes updates to connected client as they arrive
```

---

## Follow-up Depth Points

**1. What if no drivers are available?**
> Return "no drivers available" immediately. Or expand search radius: 2km → 5km → 10km. Or put rider in a queue and notify when a driver becomes available nearby.

**2. Driver goes offline mid-search?**
> Location updates stop → TTL on Redis GEO entry expires (e.g., 30s TTL). Driver is automatically removed from available set.

**3. ETA calculation?**
> Call routing engine (Google Maps API, or internal OSRM). Factor in: current traffic, driver's current location, route to pickup.

**4. Payment processing?**
> After trip COMPLETED:
> 1. Calculate fare (distance × rate × surge)
> 2. Charge rider's saved card via Stripe/Razorpay (async)
> 3. On success → transfer driver's cut to driver wallet
> 4. Saga pattern: payment failure → retry, escalate to support

**5. Driver rating and safety?**
> Both rider and driver rate each other after trip. Ratings stored in DB. Low-rated drivers deprioritized in matching (lower score). Abuse detection: flag rapid location jumps, unusual trip patterns.

---

## One-Line Interview Answer

> *"Uber's core challenge is geospatial driver matching at scale. Redis GEORADIUS on a live driver location set finds nearest available drivers in milliseconds. Location updates (every 5s per driver) flow through Kafka for durability, and WebSockets push real-time location to the rider's map. The trip is a state machine. Surge pricing is demand/supply ratio computed per geohash cell every 30 seconds. At very high scale, partition the driver location store by city/region to avoid a single hot Redis instance."*
