# Staff / Principal Interview — LLD & HLD Problem Bank

> Ruthlessly curated 5 LLD + 5 HLD problems most likely to appear in Staff/Principal Solutions Architect interviews.
> Each file: Why it exists → Core concepts → Design → Deep dives → One-line answer.

---

## Low-Level Design (LLD)

| # | Problem | Core Concepts | File |
|---|---|---|---|
| 1 | Parking Lot | OOP hierarchy, State machine, Strategy pattern | [lld-01-parking-lot.md](./lld-01-parking-lot.md) |
| 2 | Splitwise / Expense Sharing | Graph, Debt simplification, Observer pattern | [lld-02-splitwise.md](./lld-02-splitwise.md) |
| 3 | Chess Game | Polymorphism, Command pattern (undo), Move validation | [lld-03-chess.md](./lld-03-chess.md) |
| 4 | Hotel Booking System | Concurrency, Locking, Builder pattern | [lld-04-hotel-booking.md](./lld-04-hotel-booking.md) |
| 5 | Food Delivery App | State machine, Strategy, Decorator pattern | [lld-05-food-delivery.md](./lld-05-food-delivery.md) |

## High-Level Design (HLD)

| # | Problem | Core Concepts | File |
|---|---|---|---|
| 6 | URL Shortener | Base62, Cache-aside, DB sharding | [hld-01-url-shortener.md](./hld-01-url-shortener.md) |
| 7 | Notification System | Kafka, Rate limiter, Idempotency | [hld-02-notification-system.md](./hld-02-notification-system.md) |
| 8 | Twitter News Feed | Fan-out, Redis sorted sets, CDN | [hld-03-twitter-feed.md](./hld-03-twitter-feed.md) |
| 9 | Ride-Sharing (Uber) | Geohashing, WebSockets, Event-driven | [hld-04-uber.md](./hld-04-uber.md) |
| 10 | Distributed Message Queue (Kafka) | Partitions, Replication, Offset management | [hld-05-message-queue.md](./hld-05-message-queue.md) |

---

## LLD vs HLD — What the Interviewer Looks For

| | LLD | HLD |
|---|---|---|
| **Focus** | Class design, OOP, patterns, code | Architecture, components, scalability |
| **Output** | Class diagrams + working code | System diagram + component reasoning |
| **Duration** | 45-60 min | 45-60 min |
| **Signal** | Clean abstractions, extensibility, thread safety | Trade-off articulation, bottleneck identification |

## Answer Framework

### LLD
```
1. Clarify requirements (functional + non-functional)
2. Identify entities / nouns → classes
3. Identify behaviours / verbs → methods
4. Apply design patterns where they fit naturally
5. Handle concurrency and edge cases
6. Walk through a use case end-to-end
```

### HLD
```
1. Clarify scale (QPS, DAU, data size, latency SLA)
2. High-level components (API layer, services, DB, cache, queue)
3. Deep dive on 1-2 critical components the interviewer picks
4. Identify bottlenecks → explain how you'd fix them
5. Trade-offs — always name what you're giving up
```
