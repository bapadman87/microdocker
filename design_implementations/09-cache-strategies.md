# Problem 9 — Cache Strategies (Read-Through / Write-Through / Cache-Aside)

> **Explain and implement the main caching patterns and their consistency trade-offs.**

---

## Why It Matters

```
Cache accelerates reads. But:
  - What happens on a cache MISS?         → Read strategy
  - What happens on a WRITE?              → Write strategy
  - How do cache and DB stay in sync?     → Consistency strategy

Wrong choice → stale data, cache stampede, data loss
```

---

## Read Strategies

### 1. Cache-Aside (Lazy Loading) — Most Common

```
Application controls the cache explicitly.

READ:
  1. Check cache
  2. HIT  → return from cache
  3. MISS → fetch from DB → store in cache → return

WRITE:
  1. Write to DB
  2. Invalidate (or update) cache entry
```

```java
public User getUser(String userId) {
    // 1. Check cache
    User cached = cache.get(userId);
    if (cached != null) return cached;

    // 2. Cache miss — fetch from DB
    User user = db.findById(userId);

    // 3. Populate cache
    cache.put(userId, user, Duration.ofMinutes(10));

    return user;
}

public void updateUser(String userId, User user) {
    db.save(user);
    cache.invalidate(userId);   // evict stale entry
}
```

**Pros:** Simple. Cache only stores what's actually requested.
**Cons:** First request always hits DB (cache miss penalty). Stale data window between write and invalidation.

---

### 2. Read-Through

```
Cache sits in front of DB. Application talks only to cache.
On miss, CACHE fetches from DB automatically (not the app).

READ:
  1. App asks cache for key
  2. Cache HIT  → return
  3. Cache MISS → cache fetches from DB → stores → returns to app
```

```java
// Cache implementation handles DB fetch on miss
public class ReadThroughCache {

    private final Map<String, User> store = new HashMap<>();
    private final UserRepository db;

    public User get(String userId) {
        return store.computeIfAbsent(userId, db::findById);  // auto-loads on miss
    }
}
```

**Pros:** App logic simpler — just talk to cache. Always consistent within the cache.
**Cons:** First access is slow (miss penalty). Cache and DB coupled.

---

## Write Strategies

### 3. Write-Through

```
Every write goes to BOTH cache and DB synchronously.
Cache is always in sync with DB.

WRITE:
  1. Write to cache
  2. Write to DB
  3. Return success (both must succeed)
```

```java
public void updateUser(String userId, User user) {
    cache.put(userId, user);   // update cache
    db.save(user);             // update DB
    // both happen synchronously
}
```

**Pros:** Cache always fresh. No stale reads.
**Cons:** Write latency = cache write + DB write. Writes are slower.

---

### 4. Write-Behind (Write-Back)

```
Write to cache immediately, write to DB asynchronously (later).

WRITE:
  1. Write to cache → return success immediately (fast!)
  2. Background: flush cache changes to DB

READ:
  → Always from cache (always fresh, even before DB flush)
```

```java
public class WriteBehindCache {

    private final Map<String, User> cache = new ConcurrentHashMap<>();
    private final Queue<WriteOperation> writeQueue = new LinkedBlockingQueue<>();

    public void updateUser(String userId, User user) {
        cache.put(userId, user);                    // immediate
        writeQueue.offer(new WriteOperation(userId, user));  // async DB write
    }

    // Background flusher
    @Scheduled(fixedDelay = 1000)
    private void flush() {
        while (!writeQueue.isEmpty()) {
            WriteOperation op = writeQueue.poll();
            db.save(op.user);
        }
    }
}
```

**Pros:** Fastest writes — DB write is async.
**Cons:** Data loss risk if cache crashes before flush. Eventual consistency.

---

### 5. Write-Around

```
Writes go DIRECTLY to DB, bypassing cache.
Cache is only populated on reads (like cache-aside).

Use when: write-heavy workloads where written data is rarely re-read.
          (e.g. logging, analytics, bulk imports)
```

---

## Comparison Table

| Strategy | Read Latency | Write Latency | Consistency | Data Loss Risk | Use Case |
|---|---|---|---|---|---|
| Cache-Aside | Fast (hit) / Slow (miss) | Fast (invalidate) | Eventual | None | General purpose — most common |
| Read-Through | Fast (hit) / Slow (miss) | — | Strong | None | Read-heavy, simple app logic |
| Write-Through | Fast | Slow (double write) | Strong | None | Read-heavy, consistency critical |
| Write-Behind | Fast | Very Fast | Eventual | Yes (crash) | Write-heavy, can tolerate lag |
| Write-Around | Slow (always miss on new writes) | Fast | Eventual | None | Write-once, rarely-read data |

---

## Cache Stampede Problem

```
Popular key expires → 1000 concurrent requests all hit DB simultaneously
→ DB overloaded → latency spike → possible crash

Solutions:
  1. Probabilistic early expiration
     Refresh the cache BEFORE it expires based on probability
     → No sudden expiry cliff

  2. Mutex / Lock on miss
     Only first thread fetches from DB, others wait
     → Prevents thundering herd

  3. Stale-while-revalidate
     Return stale value immediately, refresh in background
     → Zero latency, eventual freshness
```

```java
// Mutex approach
public User getUser(String userId) {
    User cached = cache.get(userId);
    if (cached != null) return cached;

    // Only one thread fetches, others wait
    synchronized (("lock:" + userId).intern()) {
        // Double-check after acquiring lock
        cached = cache.get(userId);
        if (cached != null) return cached;

        User user = db.findById(userId);
        cache.put(userId, user, Duration.ofMinutes(10));
        return user;
    }
}
```

---

## Follow-up Depth Points

**1. Cache invalidation is one of the hardest problems in CS — why?**
> Race condition: Thread A reads from DB, Thread B updates DB + invalidates cache, Thread A writes stale value to cache → stale data lives until TTL expires.
> Solution: version numbers or ETag on cache entries.

**2. What's the right TTL?**
> Too short → too many cache misses → DB load increases
> Too long → stale data risk
> For user profiles: 5-15 minutes is common
> For product catalog: hours
> For real-time prices: seconds or no cache

**3. Cache eviction vs cache invalidation?**
> Eviction: cache removes entry due to capacity (LRU, LFU)
> Invalidation: application explicitly removes stale entry after a write
> Both are different mechanisms, often used together.

**4. Distributed cache consistency?**
> With multiple app nodes, each may have a local cache. A write on Node A invalidates Node A's cache but not Node B's. Solutions: Redis as shared cache, broadcast invalidation events via Pub/Sub.

---

## One-Line Interview Answer

> *"Cache-Aside is the most common pattern — app checks cache, on miss loads from DB and populates cache, on write invalidates cache. Write-Through keeps cache always in sync but doubles write latency. Write-Behind is fastest for writes but risks data loss on crash. The hardest part is cache invalidation — race conditions can introduce stale data, which I'd handle with versioning or short TTLs depending on the consistency requirement."*
