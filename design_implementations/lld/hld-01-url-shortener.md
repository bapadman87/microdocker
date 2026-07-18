# HLD 01 — URL Shortener (bit.ly)

> **Design a URL shortening service that converts long URLs into short aliases and redirects users at high throughput.**

---

## Clarify Scale First

```
DAU: 100 million users
Write QPS: ~1,000 new URLs/sec
Read QPS:  ~100,000 redirects/sec  (reads >> writes, 100:1 ratio)
URL lifespan: 5 years
Storage: 1000 req/sec × 86400 × 365 × 5 × 500 bytes ≈ ~70 TB
Latency SLA: redirect < 10ms
```

---

## High-Level Components

```
Client
  │
  ▼
[ Load Balancer ]
  │
  ├──────────────────────┐
  ▼                      ▼
[ Write API ]        [ Read API ]
  │                      │
  ▼                      ▼
[ ID Generator ]    [ Cache (Redis) ]
  │                      │  miss
  ▼                      ▼
[ DB (Primary) ] ←── [ DB (Read Replica) ]
```

---

## Core Design Decisions

### 1. Short Code Generation — Base62 Encoding

```
Options:
  A. Hash (MD5/SHA) → take first 7 chars → collision risk
  B. Auto-increment ID → encode to Base62 → unique, no collision ✅
  C. Snowflake ID → encode to Base62 → unique, distributed ✅

Base62 = [a-z][A-Z][0-9] = 62 characters
7 chars = 62^7 = 3.5 trillion unique URLs — more than enough

Example:
  ID: 123456789
  Base62: "8M0kX"  (5 chars)
```

```java
public class Base62Encoder {
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    public String encode(long id) {
        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(CHARS.charAt((int)(id % 62)));
            id /= 62;
        }
        return sb.reverse().toString();
    }

    public long decode(String code) {
        long id = 0;
        for (char c : code.toCharArray()) {
            id = id * 62 + CHARS.indexOf(c);
        }
        return id;
    }
}
```

### 2. Write Flow

```
POST /shorten { "longUrl": "https://very-long-url.com/..." }

1. Validate URL
2. Check if URL already shortened (optional dedup)
3. Generate unique ID (Snowflake or DB auto-increment)
4. Encode ID → Base62 short code
5. Store in DB: short_code → long_url, created_at, expiry, user_id
6. Return: { "shortUrl": "https://bit.ly/8M0kX" }
```

### 3. Read Flow (Critical Path — must be < 10ms)

```
GET /8M0kX

1. Check Redis cache: key = "8M0kX" → value = long URL
2. HIT  → return 301/302 redirect immediately
3. MISS → query DB read replica → store in Redis (TTL 24h) → redirect

301 = Permanent redirect (browser caches — less server load, but can't track clicks)
302 = Temporary redirect (browser always hits server — enables analytics) ✅
```

---

## Database Schema

```sql
CREATE TABLE url_mappings (
    id           BIGINT PRIMARY KEY,         -- Snowflake ID
    short_code   VARCHAR(10) UNIQUE NOT NULL,
    long_url     TEXT NOT NULL,
    user_id      VARCHAR(50),
    created_at   TIMESTAMP DEFAULT NOW(),
    expires_at   TIMESTAMP,
    click_count  BIGINT DEFAULT 0
);

CREATE INDEX idx_short_code ON url_mappings(short_code);
```

---

## Architecture Diagram

```
                 ┌──────────────────────────────────────────┐
                 │              Load Balancer                │
                 └───────────────┬──────────────────────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              ▼                  ▼                   ▼
        [ Write API ]      [ Read API ]       [ Analytics API ]
              │                  │
              ▼                  ▼
       [ ID Generator ]   [ Redis Cluster ]
       (Snowflake)          (100k QPS)
              │                  │ miss
              ▼                  ▼
       [ PostgreSQL ]    [ Read Replicas ]
        (Primary)          (3 replicas)
              │
              ▼
       [ Async Worker ]
       (click analytics
        → Kafka → ClickHouse)
```

---

## Deep Dives

### Cache Strategy
```
Key: short_code
Value: long_url
TTL: 24 hours (popular URLs stay cached, cold ones evict)
Eviction: LRU
Cache hit rate target: >99% (reads are hot — Zipf distribution, 20% URLs = 80% traffic)
```

### Handling Custom Aliases
```
POST /shorten { "longUrl": "...", "alias": "my-brand" }

1. Check if alias already taken in DB
2. If free → store with alias as short_code
3. If taken → return 409 Conflict
```

### Expiry / Cleanup
```
Soft delete: set expires_at in DB
Background worker: daily scan for expired rows, delete + evict from cache
Or: check expiry on read path — if expired, return 410 Gone
```

### Analytics
```
Redirect is on critical path — don't write click analytics synchronously
Publish to Kafka on each redirect:
  { shortCode, timestamp, userAgent, ip, referrer }
Kafka → consumer → ClickHouse (OLAP) → dashboard
```

---

## Follow-up Depth Points

**1. How do you prevent the same long URL being shortened multiple times?**
> Add a hash of the long URL as a DB column with unique index. On shorten, check if hash exists → return existing short code. Trade-off: extra DB lookup on write.

**2. What if a user shares a short URL and millions hit it at once (viral)?**
> Redis absorbs the spike. If Redis is down, circuit breaker falls back to DB read replica. CDN can cache the 301 redirect response itself (if you don't need click analytics).

**3. DB sharding strategy?**
> Shard by `short_code` first character (62 shards = 1 per Base62 char). Or consistent hashing. Avoid sharding by user_id — hot user = hot shard.

**4. 301 vs 302?**
> 301 Permanent: browser caches, no server hit on subsequent clicks — can't count clicks, can't update destination.
> 302 Temporary: every redirect hits your server — full analytics, can change destination. Use 302 for bit.ly-style analytics.

**5. Rate limiting?**
> Apply Rate Limiter (Problem 1!) on write API — max 100 URLs/min per user. Prevents abuse of short code namespace.

---

## One-Line Interview Answer

> *"URL shortener is a read-heavy system (100:1 read/write). Writes: generate Snowflake ID → Base62 encode → store in DB. Reads: check Redis first (>99% hit rate), miss falls to DB read replica, then cache the result. Use 302 redirect to enable analytics — publish to Kafka on each redirect, consume into ClickHouse for dashboards. Key scale decisions: Redis cluster for reads, DB sharding by short_code hash for writes, CDN for viral URLs."*
