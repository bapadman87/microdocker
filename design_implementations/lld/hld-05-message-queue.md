# HLD 05 — Distributed Message Queue (Kafka)

> **Design a distributed message queue that supports high-throughput publish/subscribe with durability, ordering, replay, and consumer group semantics.**

---

## Clarify Scale First

```
Write throughput: 1 million messages/sec
Message size: avg 1KB
Retention: 7 days
Consumers: thousands of consumer groups
Delivery guarantee: at-least-once
Ordering: within a partition
Latency: end-to-end < 10ms
```

---

## Why Not Just Use a DB or Redis?

```
DB (polling):   High latency, polling overhead, no consumer group semantics
Redis PubSub:   No persistence, no replay, message lost if consumer down
RabbitMQ:       Good for task queues, poor for event streaming / replay
Kafka:          Log-based, persistent, replayable, high throughput ✅
```

---

## Core Concepts

### Topics and Partitions

```
Topic = a named stream of messages (e.g., "orders", "payments")

Topic is split into Partitions for parallelism:
  Topic "orders" → [Partition 0] [Partition 1] [Partition 2]

Each partition is an ordered, immutable append-only log.
Messages within a partition are strictly ordered.
Messages across partitions have NO ordering guarantee.

Partition key → determines which partition a message goes to:
  hash(orderId) % numPartitions → ensures all events for orderId are in order
```

### Offsets

```
Each message in a partition has a monotonically increasing offset.
Consumer tracks its own offset — tells Kafka how far it has read.

Partition 0:  [msg0] [msg1] [msg2] [msg3] [msg4]
               off=0  off=1  off=2  off=3  off=4
                                     ↑
                            consumer offset = 2 (read up to here)

On crash + restart → consumer resumes from offset 2.
Replay: reset offset to 0 → reprocess all messages.
```

### Consumer Groups

```
Group = multiple consumers sharing the work of consuming a topic.
Each partition is assigned to exactly ONE consumer in a group.

Topic: 3 partitions, Consumer Group with 3 consumers:
  Consumer 1 → Partition 0
  Consumer 2 → Partition 1
  Consumer 3 → Partition 2

If Consumer 2 dies → Kafka rebalances:
  Consumer 1 → Partitions 0, 1
  Consumer 3 → Partition 2

Multiple groups can independently consume the same topic:
  OrderService (group 1) reads all orders
  AnalyticsService (group 2) also reads all orders (independently!)
```

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Kafka Cluster                        │
│                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │  Broker 1   │  │  Broker 2   │  │  Broker 3   │        │
│  │  (Leader:   │  │  (Leader:   │  │  (Leader:   │        │
│  │   P0, P3)   │  │   P1, P4)   │  │   P2, P5)   │        │
│  └─────────────┘  └─────────────┘  └─────────────┘        │
│         │                │                │                 │
│         └────────────────┴────────────────┘                 │
│                          │                                  │
│                  [ ZooKeeper / KRaft ]                      │
│                  (Leader election,                          │
│                   cluster metadata)                         │
└─────────────────────────────────────────────────────────────┘
         ▲                                  │
         │ publish                          │ consume
         │                                  ▼
  [ Producers ]                    [ Consumer Groups ]
  (Order Service,                  (OrderService, Analytics,
   Payment Service)                 Notification Service)
```

---

## Replication

```
Each partition has 1 leader + N-1 replicas (N = replication factor, typically 3).

Leader handles all reads and writes.
Replicas sync from leader.

In-Sync Replicas (ISR): replicas caught up with leader.

On leader failure:
  ZooKeeper / KRaft detects failure
  Elects new leader from ISR
  Clients reconnect to new leader

Producer acks:
  acks=0  → fire and forget (fastest, no durability guarantee)
  acks=1  → leader acknowledges (leader dies = message lost)
  acks=all → all ISR acknowledge (strongest durability) ✅
```

---

## Write Path (Producer → Kafka)

```
1. Producer calls: producer.send(record)
2. Serialization: key + value → bytes
3. Partitioner: hash(key) % numPartitions → picks partition
4. Batch accumulator: batches messages by partition (improves throughput)
5. Network: batch sent to partition leader broker
6. Broker: appends to partition log (sequential disk write — fast!)
7. Replication: followers pull from leader, write to their logs
8. Broker: sends ack to producer (if acks=all, waits for ISR)
```

---

## Read Path (Consumer)

```
1. Consumer sends fetch request to broker: { partition, offset, maxBytes }
2. Broker reads from partition log starting at offset
3. Returns batch of messages
4. Consumer processes messages
5. Consumer commits offset (auto or manual)

Manual offset commit (recommended):
  Process message → business logic succeeds → THEN commit offset
  If crash before commit → reprocessed on restart (at-least-once)
  If crash after commit but before business logic completes → message skipped (at-most-once)
```

---

## Delivery Guarantees

| Guarantee | How | Risk |
|---|---|---|
| At-most-once | Commit offset before processing | Message loss on crash |
| At-least-once | Commit offset after processing | Duplicate processing |
| Exactly-once | Kafka transactions + idempotent producer | Complex, lower throughput |

> Production standard: **at-least-once + idempotent consumers** (deduplicate by message key)

---

## Storage — Log Segments

```
Partition on disk = series of segment files:
  segment-0.log (messages 0-999)
  segment-1000.log (messages 1000-1999)
  ...
  segment-N.log (active segment, append-only)

Retention policy:
  Time-based: delete segments older than 7 days
  Size-based: delete oldest segments when partition exceeds 50GB

Log compaction (for event sourcing):
  Keep only the LATEST message per key
  Useful for: user profile updates, config changes
```

---

## Follow-up Depth Points

**1. How does Kafka achieve high throughput?**
> Sequential disk writes (append-only log) → much faster than random writes.
> Zero-copy: sendfile() syscall sends data from disk directly to network without copying to user space.
> Batching: producers and consumers batch messages → fewer network round trips.
> Compression: GZIP/Snappy/LZ4 on batches → less network bandwidth.

**2. How many partitions should a topic have?**
> More partitions = more parallelism = higher throughput.
> But: more partitions = more files, more ZooKeeper znodes, slower leader election.
> Rule of thumb: throughput_needed / throughput_per_partition. Start with 10-20, scale up.

**3. Consumer lag — consumers falling behind?**
> Monitor: `kafka-consumer-groups --describe` shows lag per partition.
> Fix: add more consumers (up to number of partitions).
> Or: increase partition count → allows more consumers.
> Alert: lag > X messages → PagerDuty alert.

**4. Kafka vs RabbitMQ — when to use which?**
```
Kafka:
  ✅ Event streaming, log aggregation, replayable events
  ✅ High throughput (millions/sec)
  ✅ Multiple independent consumers of same event stream
  ❌ Complex task routing, per-message TTL, priority queues

RabbitMQ:
  ✅ Task queues, RPC-style messaging
  ✅ Complex routing (topic/fanout/direct exchanges)
  ✅ Per-message TTL and priority
  ❌ Replay, high-throughput event streaming
```

**5. Exactly-once semantics (EOS)?**
> Kafka supports EOS with: idempotent producer (producer ID + sequence number) + transactions (atomic write across partitions). Complex to implement but available since Kafka 0.11. Used in Flink/Spark Structured Streaming integrations.

---

## One-Line Interview Answer

> *"Kafka is a distributed commit log — topics split into partitions for parallelism, each partition an ordered append-only log. Replication factor 3 with acks=all gives durability. Consumer groups let multiple services independently consume the same topic with partition-level parallelism. High throughput comes from sequential disk writes, zero-copy network transfer, and batching. At-least-once is the practical guarantee — pair with idempotent consumers using message key deduplication. Key design decision: number of partitions = your maximum consumer parallelism ceiling, so plan ahead."*
