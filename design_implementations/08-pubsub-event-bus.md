# Problem 8 — In-Memory Pub/Sub Event Bus

> **Implement an in-process event bus where publishers emit events to named topics and subscribers receive them.**

---

## Why Does It Exist?

```
Without an event bus:
  OrderService → directly calls PaymentService, NotificationService, InventoryService
  → Tight coupling
  → OrderService must know about all downstream services
  → Adding a new consumer requires changing OrderService

With an event bus:
  OrderService → publishes "ORDER_PLACED" event
  PaymentService subscribes to "ORDER_PLACED"
  NotificationService subscribes to "ORDER_PLACED"
  InventoryService subscribes to "ORDER_PLACED"

  → OrderService doesn't know who's listening
  → Add new consumer without touching OrderService
  → Clean separation of concerns
```

---

## Core Design

```
Topic Registry:
  topic → [subscriber1, subscriber2, subscriber3]

publish(topic, event):
  find all subscribers for topic
  dispatch event to each

subscribe(topic, handler):
  add handler to topic's subscriber list

unsubscribe(topic, handler):
  remove handler from topic's subscriber list
```

---

## Implementation — Sync + Async Variants

```java
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class EventBus {

    // topic → list of subscribers
    private final Map<String, List<Consumer<Object>>> subscribers = new ConcurrentHashMap<>();
    private final ExecutorService asyncExecutor;
    private final boolean async;

    public EventBus(boolean async, int threadPoolSize) {
        this.async = async;
        this.asyncExecutor = async
            ? Executors.newFixedThreadPool(threadPoolSize)
            : null;
    }

    /**
     * Subscribe to a topic.
     * Returns a token that can be used to unsubscribe.
     */
    @SuppressWarnings("unchecked")
    public <T> Consumer<T> subscribe(String topic, Consumer<T> handler) {
        subscribers
            .computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
            .add((Consumer<Object>) handler);
        return handler;
    }

    /**
     * Unsubscribe a handler from a topic.
     */
    public <T> void unsubscribe(String topic, Consumer<T> handler) {
        List<Consumer<Object>> handlers = subscribers.get(topic);
        if (handlers != null) {
            handlers.remove(handler);
        }
    }

    /**
     * Publish an event to all subscribers of the topic.
     */
    public void publish(String topic, Object event) {
        List<Consumer<Object>> handlers = subscribers.get(topic);
        if (handlers == null || handlers.isEmpty()) return;

        for (Consumer<Object> handler : handlers) {
            if (async) {
                // Dispatch each subscriber in a separate thread
                asyncExecutor.submit(() -> safeInvoke(handler, event));
            } else {
                // Synchronous — publisher blocks until all handlers complete
                safeInvoke(handler, event);
            }
        }
    }

    private void safeInvoke(Consumer<Object> handler, Object event) {
        try {
            handler.accept(event);
        } catch (Exception e) {
            // Log but don't propagate — one bad subscriber shouldn't affect others
            System.err.println("Subscriber threw exception: " + e.getMessage());
        }
    }

    public void shutdown() {
        if (asyncExecutor != null) asyncExecutor.shutdown();
    }
}
```

---

## Usage

```java
EventBus bus = new EventBus(true, 10);  // async, 10 threads

// Define event
record OrderPlacedEvent(String orderId, double amount) {}

// Subscribe
bus.subscribe("ORDER_PLACED", (OrderPlacedEvent e) -> {
    paymentService.charge(e.orderId(), e.amount());
});

bus.subscribe("ORDER_PLACED", (OrderPlacedEvent e) -> {
    notificationService.sendConfirmation(e.orderId());
});

bus.subscribe("ORDER_PLACED", (OrderPlacedEvent e) -> {
    inventoryService.reserve(e.orderId());
});

// Publish — all 3 subscribers notified
bus.publish("ORDER_PLACED", new OrderPlacedEvent("ORD-123", 299.99));
```

---

## Trace Through

```
Subscribers:
  "ORDER_PLACED" → [PaymentHandler, NotificationHandler, InventoryHandler]

publish("ORDER_PLACED", event):
  async = true
  → thread1: PaymentHandler.accept(event)
  → thread2: NotificationHandler.accept(event)
  → thread3: InventoryHandler.accept(event)
  
  All 3 run concurrently.
  Publisher returns immediately (non-blocking).

If NotificationHandler throws:
  → safeInvoke catches exception, logs it
  → PaymentHandler and InventoryHandler still run ✅
```

---

## Follow-up Depth Points

**1. Sync vs Async — when to use which?**
```
Sync:  publisher waits for ALL handlers to complete
       → simple, easy to test, error propagation is clear
       → handler failure can block the publisher
       → use for: simple apps, unit tests

Async: publisher dispatches and returns immediately
       → handlers run concurrently
       → publisher is never blocked by slow handlers
       → use for: production, high-throughput systems
```

**2. What if a subscriber is slow?**
> In async mode, slow subscriber just consumes a thread from the pool. Other subscribers aren't affected. If pool is exhausted — new events queue up. Add bounded queue + rejection policy.

**3. Event ordering guarantee?**
> No ordering guarantee in async mode — ORDER_PLACED event #2 might be processed before #1. If ordering matters, use a single-threaded executor per topic, or switch to Kafka which gives partition-level ordering.

**4. Memory leak — forgotten subscriptions?**
> Subscribers that are never unsubscribed accumulate. Solutions:
> - Weak references to subscriber handlers (GC removes them when handler is garbage collected)
> - Subscription tokens with explicit `close()` method
> - TTL on subscriptions

**5. In-memory vs Kafka/RabbitMQ?**
| | In-Memory Event Bus | Kafka/RabbitMQ |
|---|---|---|
| Scope | Single JVM | Cross-service, distributed |
| Persistence | ❌ | ✅ |
| Replay | ❌ | ✅ |
| Throughput | Very high | High |
| Use case | Internal decoupling | Cross-service communication |

**6. Dead letter queue?**
> If a subscriber fails repeatedly, route the event to a Dead Letter Topic for inspection and manual replay. Same concept as Kafka DLQ.

---

## One-Line Interview Answer

> *"In-memory Pub/Sub is a topic registry — a ConcurrentHashMap of topic to handler list. Publish finds all subscribers and dispatches the event, either synchronously (publisher blocks) or asynchronously (thread pool per handler). Key concerns: subscriber exceptions must be isolated so one bad handler doesn't affect others; async introduces ordering non-guarantees; and subscriptions must be cleaned up to avoid memory leaks. For cross-service communication, this pattern scales out to Kafka."*
