# Problem 7 — Distributed ID Generator (Snowflake)

> **Generate unique, sortable IDs across multiple distributed nodes without coordination.**

---

## Why Does It Exist?

```
Single DB auto-increment IDs fail at scale:
  - Multiple DB shards → each has its own counter → collisions
  - UUID → unique, but random → not sortable, not human-readable, 128 bits
  - DB sequence → single point of contention → bottleneck

Requirements for a distributed ID:
  ✅ Globally unique across all nodes
  ✅ Sortable by time (newer IDs > older IDs)
  ✅ Generated without coordination between nodes
  ✅ Fast — millions per second per node
  ✅ Fits in 64 bits (long)
```

---

## The Snowflake Structure (Twitter's Design)

```
64 bits total (fits in a Java long):

 1 bit       41 bits              10 bits         12 bits
┌───┬──────────────────────────┬──────────────┬────────────────┐
│ 0 │    timestamp (ms)        │  machine ID  │   sequence     │
└───┴──────────────────────────┴──────────────┴────────────────┘
  │           │                      │               │
  │           │                      │               └── 4096 IDs per ms per node
  │           │                      └────────────────── 1024 unique machines
  │           └───────────────────────────────────────── ~69 years from epoch
  └───────────────────────────────────────────────────── always 0 (sign bit)
```

### What Each Part Gives You

| Part | Bits | Max Value | Purpose |
|---|---|---|---|
| Sign | 1 | 0 | Always 0 — keeps ID positive |
| Timestamp | 41 | ~69 years | Milliseconds since custom epoch |
| Machine ID | 10 | 1023 nodes | Unique per node — no coordination needed |
| Sequence | 12 | 4095 | Counter within same millisecond |

---

## Implementation

```java
public class SnowflakeIdGenerator {

    // Custom epoch — Jan 1, 2020 (reduces timestamp bits needed)
    private static final long CUSTOM_EPOCH = 1577836800000L;

    // Bit lengths
    private static final int MACHINE_ID_BITS = 10;
    private static final int SEQUENCE_BITS   = 12;

    // Max values
    private static final long MAX_MACHINE_ID = (1L << MACHINE_ID_BITS) - 1;  // 1023
    private static final long MAX_SEQUENCE   = (1L << SEQUENCE_BITS) - 1;    // 4095

    // Bit shift positions
    private static final int MACHINE_ID_SHIFT  = SEQUENCE_BITS;               // 12
    private static final int TIMESTAMP_SHIFT   = SEQUENCE_BITS + MACHINE_ID_BITS; // 22

    private final long machineId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long machineId) {
        if (machineId < 0 || machineId > MAX_MACHINE_ID) {
            throw new IllegalArgumentException("Machine ID must be between 0 and " + MAX_MACHINE_ID);
        }
        this.machineId = machineId;
    }

    public synchronized long nextId() {
        long timestamp = currentMs();

        if (timestamp < lastTimestamp) {
            // Clock moved backward — clock skew!
            throw new IllegalStateException(
                "Clock moved backwards. Refusing to generate ID for "
                + (lastTimestamp - timestamp) + "ms"
            );
        }

        if (timestamp == lastTimestamp) {
            // Same millisecond — increment sequence
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // Sequence exhausted — wait for next millisecond
                timestamp = waitNextMs(lastTimestamp);
            }
        } else {
            // New millisecond — reset sequence
            sequence = 0;
        }

        lastTimestamp = timestamp;

        // Assemble the ID
        return ((timestamp - CUSTOM_EPOCH) << TIMESTAMP_SHIFT)
             | (machineId << MACHINE_ID_SHIFT)
             | sequence;
    }

    private long waitNextMs(long lastTimestamp) {
        long timestamp = currentMs();
        while (timestamp <= lastTimestamp) {
            timestamp = currentMs();
        }
        return timestamp;
    }

    private long currentMs() {
        return System.currentTimeMillis();
    }
}
```

---

## Usage

```java
// Each node gets a unique machineId (0-1023)
SnowflakeIdGenerator gen = new SnowflakeIdGenerator(42);

long id1 = gen.nextId();
long id2 = gen.nextId();

// id2 > id1 always (monotonically increasing)
// IDs from different machines never collide (different machineId bits)
```

---

## Trace Through

```
machineId = 5, CUSTOM_EPOCH = Jan 1 2020

T=1000ms (since epoch):
  sequence = 0
  id = (1000 << 22) | (5 << 12) | 0
     = 4194304000 | 20480 | 0
     = 4194324480

T=1000ms (same ms, next call):
  sequence = 1
  id = (1000 << 22) | (5 << 12) | 1
     = 4194324481   ← 1 more than previous

T=1001ms (next ms):
  sequence resets to 0
  id = (1001 << 22) | (5 << 12) | 0
     = larger than any T=1000ms ID ✅ (monotonically increasing)
```

---

## Follow-up Depth Points

**1. How do you assign machine IDs without coordination?**
Options:
> - Read from config file or environment variable at startup
> - Zookeeper — node registers at startup, gets unique ID from ZK ephemeral node
> - Redis — `INCR machineId` at startup, use result as ID
> - IP-based — last 10 bits of IP address (fragile — IP can change)

**2. Clock skew problem?**
> NTP can move the clock backward. Current impl throws exception. Options:
> - Wait until clock catches up (if skew is small, <10ms)
> - Use a separate monotonic clock
> - Store `lastTimestamp` in Redis — if local clock < Redis timestamp, use Redis timestamp
> Twitter's original Snowflake waited up to 1 second, then threw an error.

**3. What if sequence exhausts within 1ms?**
> 4096 IDs per ms = 4 million IDs per second per node. Extremely rare to exhaust. Current impl waits for next ms — this is correct behavior.

**4. How do you extract the timestamp from an ID?**
```java
public long extractTimestamp(long id) {
    return (id >> TIMESTAMP_SHIFT) + CUSTOM_EPOCH;
}
// Useful for debugging — tells you exactly when the ID was generated
```

**5. Alternatives to Snowflake?**
| Approach | Pros | Cons |
|---|---|---|
| UUID v4 | No coordination | Random, unsortable, 128 bits |
| UUID v7 | Time-sortable | Still 128 bits |
| Snowflake | Fast, sortable, 64 bits | Clock skew risk |
| ULID | Sortable, 128 bits | Less common |
| DB sequence | Simple | Single point of contention |

---

## One-Line Interview Answer

> *"Snowflake ID packs timestamp + machineId + sequence into a 64-bit long. The timestamp prefix makes IDs naturally sortable. MachineId (10 bits = 1024 nodes) eliminates coordination — each node generates IDs independently. Sequence (12 bits) handles 4096 IDs per ms per node. The main risk is clock skew — NTP moving time backward — which I'd handle by throwing an error and alerting, or waiting for the clock to catch up if skew is small."*
