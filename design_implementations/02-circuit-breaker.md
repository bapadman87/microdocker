# Problem 2 — Circuit Breaker

> **Implement a Circuit Breaker that prevents cascading failures when a downstream service is down.**

---

## Why Does It Exist?

```
Service A → calls → Service B (which is DOWN or slow)

Without Circuit Breaker:
  Every request to A → waits → times out → error
  Thread pool fills up waiting
  Service A itself becomes slow / crashes
  → Cascading failure across the entire system

With Circuit Breaker:
  After N failures → stop calling B entirely
  Return fast-fail response immediately
  Periodically test if B recovered
  → Protect the caller, allow downstream to recover
```

> Like the electrical fuse in your house — when something is wrong downstream,
> it stops the current instead of letting everything burn.

---

## The State Machine — Core of the Pattern

```
         failures cross threshold
CLOSED ──────────────────────────→ OPEN
  ↑                                  │
  │                                  │ after timeout period
  │                                  ↓
  └──────────────────────────── HALF-OPEN
         test request succeeded        │
                                       │ test request failed
                                       ↓
                                     OPEN (reset timer)
```

### 3 States

| State | Meaning | Behavior |
|---|---|---|
| **CLOSED** | Everything normal | All requests pass through |
| **OPEN** | Downstream is broken | All requests fail fast — don't even try |
| **HALF-OPEN** | Testing if downstream recovered | Allow 1 test request only |

---

## Implementation

```java
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class CircuitBreaker {

    private final int failureThreshold;
    private final long retryTimeoutMs;

    private enum State { CLOSED, OPEN, HALF_OPEN }
    private final AtomicReference<State> state;
    private final AtomicInteger failureCount;
    private final AtomicLong openedAt;

    public CircuitBreaker(int failureThreshold, long retryTimeoutMs) {
        this.failureThreshold = failureThreshold;
        this.retryTimeoutMs = retryTimeoutMs;
        this.state = new AtomicReference<>(State.CLOSED);
        this.failureCount = new AtomicInteger(0);
        this.openedAt = new AtomicLong(0);
    }

    public <T> T execute(SupplierWithException<T> remoteCall) throws Exception {
        if (!allowRequest()) {
            throw new RuntimeException("Circuit OPEN — fast fail");
        }
        try {
            T result = remoteCall.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }

    private boolean allowRequest() {
        State current = state.get();

        if (current == State.CLOSED) return true;

        if (current == State.OPEN) {
            long elapsed = System.currentTimeMillis() - openedAt.get();
            if (elapsed >= retryTimeoutMs) {
                // Transition to HALF-OPEN — allow one test request
                state.compareAndSet(State.OPEN, State.HALF_OPEN);
                return true;
            }
            return false;   // still OPEN, fast fail
        }

        // HALF-OPEN: only the thread that transitioned gets through
        return state.get() == State.HALF_OPEN;
    }

    private void onSuccess() {
        failureCount.set(0);
        state.set(State.CLOSED);
    }

    private void onFailure() {
        int failures = failureCount.incrementAndGet();
        if (failures >= failureThreshold) {
            openedAt.set(System.currentTimeMillis());
            state.set(State.OPEN);
        }
    }

    @FunctionalInterface
    public interface SupplierWithException<T> {
        T get() throws Exception;
    }
}
```

---

## Usage

```java
CircuitBreaker cb = new CircuitBreaker(
    5,       // open after 5 consecutive failures
    10_000   // retry after 10 seconds
);

public Response chargePayment(Order order) {
    try {
        return cb.execute(() -> paymentService.charge(order));
    } catch (RuntimeException e) {
        // Circuit is OPEN — serve fallback
        return fallbackCache.getLastKnownResponse(order.getId());
    }
}
```

---

## Trace Through

```
Requests 1-4   → PaymentService fails
                 failureCount = 4, state = CLOSED

Request 5      → fails → failureCount = 5 → threshold hit!
                 state = OPEN, openedAt = now

Requests 6-10  → allowRequest() → OPEN → fast fail (no network call!)

... 10 seconds pass ...

Request 11     → elapsed >= retryTimeout
                 state → HALF_OPEN
                 test request goes through

  If success   → onSuccess() → state = CLOSED, failureCount = 0 ✅
  If failure   → onFailure() → state = OPEN again, timer resets  ❌
```

---

## Follow-up Depth Points

**1. What's the fallback when OPEN?**
> Never just throw an error to the user. Return stale cache, default response, or degraded mode.
```java
catch (RuntimeException e) {
    return fallbackCache.get(userId);  // graceful degradation
}
```

**2. Count-based threshold is naive — why?**
> 5 failures out of 10,000 requests is noise. Use failure rate — e.g. open if >50% of last 100 requests failed. This is what Resilience4j does internally.

**3. Half-Open concurrency problem?**
> Multiple threads can pass `allowRequest()` simultaneously in HALF-OPEN. Use `compareAndSet` — only the first thread transitions, rest fast-fail until state resolves.

**4. What metrics should you track?**
> State transitions, fast-fail count, recovery time. Feed into Micrometer/Prometheus. Circuit breaker without observability is useless in production.

**5. Custom vs library?**
> In production always use Resilience4j (successor to Hystrix). It handles sliding window failure rates, concurrent half-open requests, metric emission, and fallback chaining out of the box.

---

## Rate Limiter vs Circuit Breaker — Don't Confuse Them

| | Rate Limiter | Circuit Breaker |
|---|---|---|
| **Protects** | Downstream from too many requests | Caller from a broken downstream |
| **Trigger** | Request volume | Failure rate |
| **Direction** | Inbound traffic control | Outbound call protection |
| **State** | Counter / token bucket | 3-state machine |

---

## One-Line Interview Answer

> *"Circuit Breaker is a state machine — CLOSED (normal), OPEN (fast-fail), HALF-OPEN (recovery test). After N failures it opens and stops all downstream calls, periodically letting one test request through. In production I'd use Resilience4j with rate-based thresholds instead of count-based, and always pair it with a fallback strategy — never return a raw error to the user."*
