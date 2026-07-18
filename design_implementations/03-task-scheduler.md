# Problem 3 — Task Scheduler

> **Implement a task scheduler that can run jobs once after a delay, or repeatedly at a fixed interval.**

---

## Why Does It Exist?

```
You need to run jobs:
  - At a specific time     → "send email at 10:00 AM"
  - After a delay          → "retry payment after 30 seconds"
  - Repeatedly             → "sync data every 5 minutes" (cron-like)

Think of it as your own mini cron + delayed queue inside a JVM.
```

---

## Key Insight — Why a Min-Heap?

```
You have 1000 scheduled jobs.
Every millisecond you need to answer:
  "Which job is due next?"

Naive: scan all 1000 jobs every tick → O(n) ❌

Min-Heap:
  → Job with earliest execution time is always at the TOP
  → Check only the top → O(1)
  → Add new job        → O(log n)
  → Remove executed    → O(log n)
```

> Min-Heap ordered by next execution time is the core data structure.

---

## The 3 Components

```
┌─────────────────────────────────────────┐
│           Task Scheduler                │
│                                         │
│  ┌─────────────┐    ┌─────────────┐    │
│  │  Min-Heap   │    │  Dispatcher │    │
│  │  (job queue)│───▶│  Thread     │    │
│  └─────────────┘    └──────┬──────┘    │
│                             │           │
│                    ┌────────▼────────┐  │
│                    │  Thread Pool    │  │
│                    │  (workers)      │  │
│                    └─────────────────┘  │
└─────────────────────────────────────────┘
```

- **Min-Heap** — stores all jobs sorted by next run time
- **Dispatcher thread** — single thread, checks heap, fires due jobs
- **Thread Pool** — actually executes job logic (never block the dispatcher!)

---

## Implementation

```java
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class TaskScheduler {

    static class Job implements Comparable<Job> {
        long id;
        Runnable task;
        long nextRunAt;       // epoch ms
        long intervalMs;      // 0 = one-time, >0 = recurring

        Job(long id, Runnable task, long nextRunAt, long intervalMs) {
            this.id = id;
            this.task = task;
            this.nextRunAt = nextRunAt;
            this.intervalMs = intervalMs;
        }

        @Override
        public int compareTo(Job other) {
            return Long.compare(this.nextRunAt, other.nextRunAt); // min-heap
        }
    }

    private final PriorityQueue<Job> heap;
    private final ExecutorService workerPool;
    private final ScheduledExecutorService dispatcher;
    private final AtomicLong idGen = new AtomicLong(0);

    public TaskScheduler(int workerThreads) {
        this.heap = new PriorityQueue<>();
        this.workerPool = Executors.newFixedThreadPool(workerThreads);
        this.dispatcher = Executors.newSingleThreadScheduledExecutor();

        // Dispatcher checks every 100ms if any job is due
        dispatcher.scheduleAtFixedRate(this::dispatch, 0, 100, TimeUnit.MILLISECONDS);
    }

    public long scheduleOnce(Runnable task, long delayMs) {
        long runAt = System.currentTimeMillis() + delayMs;
        return addJob(task, runAt, 0);
    }

    public long scheduleRecurring(Runnable task, long initialDelayMs, long intervalMs) {
        long runAt = System.currentTimeMillis() + initialDelayMs;
        return addJob(task, runAt, intervalMs);
    }

    private synchronized long addJob(Runnable task, long runAt, long intervalMs) {
        long id = idGen.incrementAndGet();
        heap.offer(new Job(id, task, runAt, intervalMs));
        return id;
    }

    private synchronized void dispatch() {
        long now = System.currentTimeMillis();

        while (!heap.isEmpty() && heap.peek().nextRunAt <= now) {
            Job job = heap.poll();

            workerPool.submit(job.task);    // execute in worker — dispatcher never blocks!

            if (job.intervalMs > 0) {       // recurring? reschedule
                job.nextRunAt = now + job.intervalMs;
                heap.offer(job);
            }
        }
    }

    public void shutdown() {
        dispatcher.shutdown();
        workerPool.shutdown();
    }
}
```

---

## Usage

```java
TaskScheduler scheduler = new TaskScheduler(10);

// One-time: send email after 5 seconds
scheduler.scheduleOnce(
    () -> emailService.send("welcome@user.com"),
    5_000
);

// Recurring: sync every 1 minute
scheduler.scheduleRecurring(
    () -> dataSync.run(),
    0,          // start immediately
    60_000      // repeat every 60s
);
```

---

## Trace Through

```
Jobs added:
  Job A → run at T+5000ms   (one-time)
  Job B → run at T+1000ms   (recurring, every 2000ms)
  Job C → run at T+3000ms   (one-time)

Heap (min by nextRunAt):
  top → [B:T+1000, C:T+3000, A:T+5000]

Dispatcher ticks:

  T+1000 → B due → submit to workerPool
                 → recurring → reschedule B at T+3000
           heap = [C:T+3000, B:T+3000, A:T+5000]

  T+3000 → C due → submit → discard (one-time)
           B due → submit → reschedule B at T+5000
           heap = [A:T+5000, B:T+5000]

  T+5000 → A due → submit → discard
           B due → submit → reschedule B at T+7000
```

---

## Follow-up Depth Points

**1. Dispatcher polls every 100ms — is that a problem?**
> Max latency is 100ms. For precision, use `Object.wait(timeUntilNextJob)` — dispatcher sleeps exactly until next job is due, woken up early when a new job is added.

```java
synchronized (lock) {
    while (true) {
        if (heap.isEmpty()) lock.wait();
        else {
            long delay = heap.peek().nextRunAt - System.currentTimeMillis();
            if (delay <= 0) dispatch();
            else lock.wait(delay);    // sleep exactly until next job
        }
    }
}
```

**2. What if a worker job throws an exception?**
> Uncaught exception in `workerPool.submit()` is silently swallowed. Always wrap job execution in try-catch and log failures.

**3. `scheduleAtFixedRate` vs `scheduleWithFixedDelay`?**
```
scheduleAtFixedRate   → next run = last START + interval
                        (doesn't wait for job to finish)

scheduleWithFixedDelay → next run = last END + interval
                         (waits for job to finish first)
```
> If a job takes longer than the interval, `AtFixedRate` jobs pile up — dangerous.

**4. Distributed scheduling?**
> Single JVM scheduler dies on restart. Options:
> - Redis sorted set (score = nextRunAt) — workers poll for due jobs
> - Quartz Scheduler — clustered, DB-backed, handles failover
> - Kafka — delayed topics with timestamp-based routing

**5. Job persistence?**
> In-memory heap dies on restart. Persist jobs to DB on add, update `nextRunAt` after each execution, recover heap from DB on startup.

**6. Cancel a job?**
> Add a `Map<Long, Job> jobRegistry`. On cancel, mark the job with a cancelled flag and skip execution in dispatch. PriorityQueue doesn't support O(1) removal by ID.

---

## Comparison

| | Java's ScheduledExecutorService | Custom Scheduler |
|---|---|---|
| Recurring jobs | ✅ | ✅ |
| Cron expressions | ❌ | Can add |
| Persistence | ❌ | Can add DB layer |
| Distributed | ❌ | Can add Redis layer |
| Cancel by ID | ✅ | Can add with HashMap |

---

## One-Line Interview Answer

> *"I'd use a Min-Heap ordered by next execution time — dispatcher thread polls the top every tick and submits due jobs to a worker thread pool. Recurring jobs get rescheduled back into the heap. For precision I'd replace polling with Object.wait(delay). For distributed, Quartz with a DB cluster or Redis sorted set where score is the scheduled timestamp."*
