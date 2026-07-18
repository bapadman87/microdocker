# Staff / Principal Engineer — Interview Problem Bank

> Low-Level Design & System Design problems commonly asked at Staff / Principal Solutions Architect level.
> Each problem covers: Core Idea → Algorithms → Implementation → Follow-up Depth Points.

---

## Problem Index

| # | Problem | Core Concept | File |
|---|---|---|---|
| 1 | Rate Limiter / Throttler | Token Bucket, Sliding Window, Fixed Window | [01-rate-limiter.md](./01-rate-limiter.md) |
| 2 | Circuit Breaker | State Machine, Cascading Failure Prevention | [02-circuit-breaker.md](./02-circuit-breaker.md) |
| 3 | Task Scheduler | Min-Heap, Priority Queue, Thread Pool | [03-task-scheduler.md](./03-task-scheduler.md) |
| 4 | Retry with Exponential Backoff | Backoff Strategy, Jitter, Idempotency | [04-retry-backoff.md](./04-retry-backoff.md) |
| 5 | LRU Cache with TTL | Doubly Linked List + HashMap, Eviction | [05-lru-cache-ttl.md](./05-lru-cache-ttl.md) |
| 6 | Connection Pool | Bounded Blocking Queue, Borrow/Return | [06-connection-pool.md](./06-connection-pool.md) |
| 7 | Distributed ID Generator | Snowflake ID, Timestamp + MachineId + Sequence | [07-distributed-id-generator.md](./07-distributed-id-generator.md) |
| 8 | In-Memory Pub/Sub Event Bus | Observer Pattern, Topic Registry, Async Dispatch | [08-pubsub-event-bus.md](./08-pubsub-event-bus.md) |
| 9 | Read-Through / Write-Through Cache | Cache-Aside vs Write-Through, Consistency | [09-cache-strategies.md](./09-cache-strategies.md) |
| 10 | Leader Election | Heartbeat, Split-Brain Prevention, ZooKeeper | [10-leader-election.md](./10-leader-election.md) |

---

## How to Use This

For each problem, practice in this order:

```
1. Read the Core Idea — understand WHY it exists
2. Study the algorithms — know the tradeoffs
3. Trace through the implementation — don't just read, dry-run it
4. Answer the follow-up depth points out loud
5. Ask yourself: what breaks in a distributed setup?
```

---

## Interview Answer Framework

```
Step 1 — Clarify the problem
  "Is this single JVM or distributed?"
  "Do we need persistence across restarts?"
  "What's the expected throughput?"

Step 2 — Name the algorithms, pick one with justification
  "There are 3 approaches: X, Y, Z.
   I'll go with X because [tradeoff reason]."

Step 3 — Implement core logic
  Focus on: data structure choice, thread safety, edge cases

Step 4 — Identify failure modes
  "This breaks when..."
  "In production I'd add..."

Step 5 — Scale it
  "For distributed setup I'd use Redis/Kafka/ZooKeeper..."
```
