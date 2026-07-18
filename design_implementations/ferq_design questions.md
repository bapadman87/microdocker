# F.E.R.Q — Design Questions (Organized)

This document lists small system design exercises with the core concepts and testing/implementation notes.

| # | Problem | Core Concept(s) | Implementation / Test Notes |
|---:|---|---|---|
| 1 | Implement a Rate Limiter | Token bucket, sliding window, distributed Redis | Local token-bucket + Redis-based global limiter for distributed tests |
| 2 | Design a Cache with TTL eviction | LRU + expiry, ConcurrentHashMap, background eviction thread | Use ConcurrentHashMap + periodic eviction; test TTL and concurrency |
| 3 | Implement a Circuit Breaker | State machine (Closed → Open → Half-Open), failure threshold | Use failure counters, timeout windows, and reset logic; unit test transitions |
| 4 | Design a Task Scheduler | Priority queue, min-heap, thread pool | Schedule delayed jobs, support persistence for restart recovery |
| 5 | Implement a Connection Pool | Bounded blocking queue, borrow/return, timeout handling | Handle leases, max connections, idle eviction, and timeout behavior |
| 6 | Design a Pub/Sub Event Bus (in-memory) | Observer pattern, topic registry, async dispatch | Topic registration, fan-out semantics, and back-pressure testing |
| 7 | Implement a Distributed ID Generator | Snowflake ID — timestamp + machineId + sequence | Ensure monotonicity, handle clock skew, and multi-node uniqueness |
| 8 | Design a Read-Through / Write-Through Cache | Cache-aside vs write-through, consistency guarantees | Compare strategies; include invalidation and write-back tests |
| 9 | Implement a Retry Mechanism with Backoff | Exponential backoff, jitter, max attempts | Add idempotency support and verify backoff + jitter distribution |
| 10 | Design a Leader Election algorithm | Heartbeat, ZooKeeper approach, split-brain prevention | Use leader lease, fencing tokens, and observe failover behavior |

## Short Implementation Notes

- Rate Limiter: start with an in-memory token bucket for single-node, then show a Redis Lua-scripted implementation for atomic distributed token checks.
- Cache: show both cache-aside and write-through examples; include TTL tests and concurrency stress tests.
- Circuit Breaker: model as an explicit enum state with transition rules and a simple sliding window failure counter.
- Scheduler: provide a simple in-memory min-heap scheduler and note how to persist jobs to a DB for resilience.
- Connection Pool: highlight borrow/return semantics, connection validation, and recovery from leaked connections.
- Pub/Sub Bus: provide synchronous and asynchronous delivery modes and show how to simulate slow subscribers.
- ID Generator: provide Snowflake spec and fallback if clock moves backwards (wait or bump sequence/machine id).
- Retry: include configurable backoff, jitter strategies (full/half jitter), and max attempt policies.
- Leader Election: describe lease-based leader election with a short TTL and fencing to avoid split-brain.

---
*File organized and formatted for clarity.*
