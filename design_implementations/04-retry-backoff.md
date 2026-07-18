# Problem 4 — Retry with Exponential Backoff

> **Implement a retry mechanism that retries a failed operation with exponentially increasing delays.**

---

## Why Does It Exist?

```
Remote call fails — but WHY?
  - Transient network hiccup → retry will succeed ✅
  - Service temporarily overloaded → retry after a wait ✅
  - Service is completely down → retrying immediately makes it worse ❌
  - Bug in request → retrying forever is pointless ❌

Naive retry (retry immediately, unlimited):
  Service is struggling → you hammer it with retries
  → Makes the overloaded service MORE overloaded
  → Thundering herd problem

Exponential Backoff:
  Wait longer after each failure → give the service time to recover
  Add jitter → prevent all clients retrying at the same time
```

---

## The Strategy

```
Attempt 1 → fails → wait 1s
Attempt 2 → fails → wait 2s
Attempt 3 → fails → wait 4s
Attempt 4 → fails → wait 8s
Attempt 5 → fails → give up → throw exception

Formula: waitTime = baseDelay * 2^attemptNumber
```

### With Jitter (Production Must-Have)

```
Without jitter: all 1000 clients retry at T+1s, T+2s, T+4s → synchronized waves
With jitter:    each client retries at T+(1s + random(0..1s)) → spread out

waitTime = baseDelay * 2^attempt + random(0, baseDelay)
```

---

## Implementation

```java
import java.util.function.Supplier;
import java.util.concurrent.ThreadLocalRandom;

public class RetryWithBackoff {

    private final int maxAttempts;
    private final long baseDelayMs;
    private final long maxDelayMs;
    private final boolean jitter;

    public RetryWithBackoff(int maxAttempts, long baseDelayMs, long maxDelayMs, boolean jitter) {
        this.maxAttempts = maxAttempts;
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.jitter = jitter;
    }

    public <T> T execute(Supplier<T> operation) throws Exception {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxAttempts) {
            try {
                return operation.get();    // success — return immediately
            } catch (Exception e) {
                lastException = e;
                attempt++;

                if (attempt >= maxAttempts) break;   // no more retries

                if (!isRetryable(e)) throw e;        // don't retry on non-transient errors

                long delay = calculateDelay(attempt);
                System.out.printf("Attempt %d failed. Retrying in %dms...%n", attempt, delay);
                Thread.sleep(delay);
            }
        }

        throw new RuntimeException("All " + maxAttempts + " attempts failed", lastException);
    }

    private long calculateDelay(int attempt) {
        // Exponential: baseDelay * 2^attempt
        long exponential = baseDelayMs * (1L << attempt);   // bit shift = 2^attempt

        // Cap at maxDelay
        long delay = Math.min(exponential, maxDelayMs);

        // Add jitter: random(0, baseDelay)
        if (jitter) {
            delay += ThreadLocalRandom.current().nextLong(0, baseDelayMs);
        }

        return delay;
    }

    private boolean isRetryable(Exception e) {
        // Don't retry on client errors (4xx) or business logic exceptions
        if (e instanceof IllegalArgumentException) return false;
        if (e instanceof SecurityException) return false;
        // Retry on network errors, timeouts, 5xx
        return true;
    }
}
```

---

## Usage

```java
RetryWithBackoff retry = new RetryWithBackoff(
    5,          // max 5 attempts
    1_000,      // start with 1 second
    30_000,     // cap at 30 seconds
    true        // add jitter
);

String result = retry.execute(() -> paymentService.charge(order));
```

### Delay Progression

```
Attempt 1 fails → wait: 1000ms + jitter
Attempt 2 fails → wait: 2000ms + jitter
Attempt 3 fails → wait: 4000ms + jitter
Attempt 4 fails → wait: 8000ms + jitter
Attempt 5 fails → throw exception (max attempts reached)
```

---

## Async Variant (Non-Blocking)

```java
public <T> CompletableFuture<T> executeAsync(Supplier<CompletableFuture<T>> operation,
                                              int attempt) {
    return operation.get().exceptionally(ex -> {
        if (attempt >= maxAttempts) throw new RuntimeException("Max retries exceeded", ex);

        long delay = calculateDelay(attempt);
        return CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS)
            .execute(() -> executeAsync(operation, attempt + 1));
    });
}
```

---

## Follow-up Depth Points

**1. What errors should NOT be retried?**
> Never retry on 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found — these won't succeed on retry. Only retry on 429 (rate limit), 500, 502, 503, 504, and network timeouts.

**2. Thundering herd problem?**
> Without jitter, all clients back off by the same amount and retry simultaneously. This creates synchronized waves of load. Full jitter spreads retries across the entire backoff window.

**3. Idempotency requirement?**
> Retrying a non-idempotent operation is dangerous. `POST /charge` retried twice = double charge. Ensure operations are idempotent (use idempotency keys) before enabling retry.

**4. Retry budget?**
> In microservice chains: Service A retries 3x → calls B, which retries 3x → calls C, which retries 3x = 27 downstream calls from 1 user request. Set retry budgets at the top level, not every hop.

**5. Pair with Circuit Breaker?**
> Retry handles transient failures. Circuit Breaker handles sustained failures. Together:
> - Retry first (transient)
> - If failure rate crosses threshold → Circuit Breaker opens → stop retrying
> This is the standard Resilience4j pattern.

**6. Deadline propagation?**
> Always check remaining time before each retry:
```java
if (System.currentTimeMillis() + delay > deadline) throw new TimeoutException();
```
> Don't retry if the client's request has already timed out.

---

## Comparison

| Strategy | Wait Pattern | Use Case |
|---|---|---|
| Immediate retry | No wait | Very fast transient errors only |
| Fixed delay | Same wait every time | Predictable recovery time |
| Exponential backoff | Doubling wait | General purpose |
| Exponential + jitter | Doubling + random | **Production standard** |
| Decorrelated jitter | `min(cap, random(base, prev*3))` | AWS recommendation for high concurrency |

---

## One-Line Interview Answer

> *"Exponential backoff doubles the wait time after each failure — `baseDelay * 2^attempt` — capped at a max delay. Jitter adds randomness to prevent thundering herd where all clients retry in synchronized waves. In production I'd pair it with Circuit Breaker — retry handles transient failures, circuit breaker handles sustained ones. And always check idempotency before enabling retry on write operations."*
