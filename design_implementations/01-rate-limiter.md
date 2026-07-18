# Problem 1 — Rate Limiter / Throttler

> **Implement a throttler that allows a maximum of 10 requests per second.**

---

## Why Does It Exist?

```
Without a rate limiter:
  - A single client can flood your API
  - Downstream services get overwhelmed
  - Everyone's experience degrades
  → Need to enforce fair usage and protect the system
```

---

## Algorithms

| Algorithm | Burst Safe | Memory | Accuracy | Use Case |
|---|---|---|---|---|
| Fixed Window | ❌ | O(1) | Low | Simple internal tools |
| Sliding Window Log | ✅ | O(n) | Exact | Low traffic, strict limits |
| Sliding Window Counter | ✅ | O(1) | ~95% | Most APIs (Nginx, Redis) |
| Token Bucket | ⚠️ Burst allowed | O(1) | High | Public APIs, SDKs |
| Leaky Bucket | ✅ | O(n) queue | High | Network QoS, stream processing |

---

## Algorithm 1 — Fixed Window Counter (Simplest)

### How It Works

```
Limit = 10 requests per second
One counter per second window. Reset every second.

[  Window: 0s → 1s  ]
 req1  ✅  counter = 1
 req2  ✅  counter = 2
 ...
 req10 ✅  counter = 10
 req11 ❌  BLOCKED

[  Window: 1s → 2s  ] ← counter RESETS to 0
 req1  ✅  counter = 1
```

### The Boundary Burst Problem

```
0.9s → req1..req10 arrive  ✅  (window 1 full)
1.0s → counter RESETS to 0
1.1s → req1..req10 arrive  ✅  (window 2 full)

Result: 20 requests served in ~200ms!
        Each window looks fine individually.
```

---

## Algorithm 2 — Token Bucket (Recommended)

### How It Works

```
Bucket holds max N tokens (= rate limit).
Each request consumes 1 token.
Tokens are NOT returned after a request completes.
Refiller resets bucket every second.

Tokens = time slot permission, not a reusable resource.
```

### Visual

```
Start:  [■■■■■■■■■■]  10 tokens

req1 → [■■■■■■■■■□]  9  ✅
req2 → [■■■■■■■■□□]  8  ✅
...
req10→ [□□□□□□□□□□]  0  ✅
req11→ NO token → ❌

--- 1 second passes → refill to 10 ---
req1 → [■■■■■■■■■□]  9  ✅
```

### Burst Accumulation (Advanced Variant)

```
Scenario: quiet for 3 seconds, then 25 requests arrive

Fixed Window (limit=10):
  first 10 → ✅, next 15 → ❌

Token Bucket (refill=10/sec, maxCap=30):
  3 quiet seconds → bucket accumulated 30 tokens
  25 requests arrive → all 25 ✅ served instantly!
```

---

## Implementation — Token Bucket

```java
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TokenBucketThrottler {

    private final int maxTokens;
    private final AtomicInteger tokens;
    private final ScheduledExecutorService refiller;

    public TokenBucketThrottler(int ratePerSecond) {
        this.maxTokens = ratePerSecond;
        this.tokens = new AtomicInteger(ratePerSecond);

        this.refiller = Executors.newSingleThreadScheduledExecutor();
        this.refiller.scheduleAtFixedRate(
            () -> tokens.set(maxTokens),   // reset to full every second
            1, 1, TimeUnit.SECONDS
        );
    }

    public boolean tryAcquire() {
        while (true) {
            int current = tokens.get();
            if (current <= 0) return false;                      // throttled
            if (tokens.compareAndSet(current, current - 1)) {   // CAS — thread safe
                return true;
            }
            // CAS failed due to race condition, retry
        }
    }

    public void shutdown() {
        refiller.shutdown();
    }
}
```

### Usage

```java
TokenBucketThrottler throttler = new TokenBucketThrottler(10);

public Response handleRequest(Request req) {
    if (!throttler.tryAcquire()) {
        return Response.status(429).body("Too Many Requests").build();
    }
    return processRequest(req);
}
```

---

## Timestamp-Based Variant (No Scheduler Thread)

```java
private final AtomicLong lastRefillTime = new AtomicLong(System.currentTimeMillis());

public boolean tryAcquire() {
    long now = System.currentTimeMillis();
    long elapsed = now - lastRefillTime.get();
    if (elapsed >= 1000) {
        tokens.set(maxTokens);
        lastRefillTime.set(now);
    }
    while (true) {
        int current = tokens.get();
        if (current <= 0) return false;
        if (tokens.compareAndSet(current, current - 1)) return true;
    }
}
```

> No background thread — more resilient. Refill happens lazily on the next request after the window passes.

---

## Follow-up Depth Points

**1. Distributed rate limiting?**
> Single JVM won't work across microservice instances. Use Redis + Lua script for atomic token decrement. Key = `throttle:{userId}`, TTL = 1s.

**2. Per-user vs global throttling?**
> Partition token bucket by `userId` or `apiKey`. Each user gets their own counter in Redis.

**3. What if the refiller thread dies?**
> Use timestamp-based approach — calculate tokens from `(now - lastRefillTime) * rate`. No thread dependency.

**4. Leaky Bucket difference?**
> Leaky bucket queues requests and processes at a fixed rate (1 per 100ms for 10/sec). Zero burst. Adds latency. Used for network traffic shaping, not API throttling.

**5. Sliding Window Counter (production choice)?**
> Used by Nginx and Redis rate limiters. Uses 2 counters (current + previous window) and interpolates. O(1) memory, ~95% accuracy, no burst edge case.

---

## One-Line Interview Answer

> *"I'd use a Token Bucket — AtomicInteger with CAS for thread safety, a scheduler to refill every second. For distributed setup I'd move to Redis with a Lua script for atomic decrement. Nginx's production rate limiter uses Sliding Window Counter which gives O(1) memory with no burst boundary problem."*
