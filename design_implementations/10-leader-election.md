# Problem 10 — Leader Election

> **In a distributed system with N nodes, elect one leader who coordinates work. Handle leader failure and re-election.**

---

## Why Does It Exist?

```
Some tasks must be done by exactly ONE node:
  - Scheduler that triggers jobs (else double-execution)
  - Primary DB writer (else split-brain)
  - Shard rebalancer
  - Config publisher

Without leader election:
  All 5 nodes think they're the leader → same job runs 5 times
  Or no node acts → nothing runs

Leader Election ensures: exactly one node is active leader at any time.
```

---

## The Core Problem

```
Distributed systems have no shared clock, no shared memory.
Nodes communicate only via messages / network.
Network can partition — nodes can't distinguish:
  "The leader is slow" vs "The leader is dead"

Key challenges:
  → Split-brain: two nodes both think they're leader
  → False death: leader is alive but network partitioned
  → Re-election: fast enough to resume operations
```

---

## Algorithm 1 — Bully Algorithm (Simple, Single DC)

```
Nodes have IDs. Highest ID wins.

When a node detects leader is down:
  1. Send ELECTION message to all nodes with higher ID
  2. If no response → I am the leader, broadcast COORDINATOR message
  3. If response → higher-ID node takes over, wait for COORDINATOR

"Bully" because the highest ID bullies everyone else.
```

```java
public class BullyElection {
    private final int nodeId;
    private final List<Integer> higherNodes;
    private volatile boolean isLeader = false;

    public void startElection() {
        if (higherNodes.isEmpty()) {
            becomeLeader();
            return;
        }
        // Send ELECTION to higher nodes
        boolean anyAlive = higherNodes.stream()
            .anyMatch(id -> sendElection(id));  // true if node responds

        if (!anyAlive) {
            becomeLeader();  // no higher node alive — I win
        }
        // else wait — higher node will broadcast COORDINATOR
    }

    private void becomeLeader() {
        isLeader = true;
        broadcastCoordinator();  // tell everyone I'm the leader
    }
}
```

**Pros:** Simple, deterministic.
**Cons:** O(n²) messages. Doesn't handle network partitions well. Split-brain risk.

---

## Algorithm 2 — Redis-Based Leader Election (Production Simple)

```
Use Redis as the single source of truth.

ACQUIRE LOCK:
  SET leader:{service} {nodeId} NX PX {ttlMs}
  NX = only set if Not eXists
  PX = with TTL in milliseconds

If SET succeeds → I am leader
If SET fails → someone else is leader

RENEW (heartbeat):
  Every TTL/3 seconds, leader renews: SET leader:{service} {nodeId} XX PX {ttlMs}
  XX = only set if eXists (prevents stealing)

ON LEADER FAILURE:
  Key expires → next node to SET wins
```

```java
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RedisLeaderElection {

    private final Jedis redis;
    private final String nodeId;
    private final String lockKey;
    private final long ttlMs;
    private volatile boolean isLeader = false;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public RedisLeaderElection(Jedis redis, String nodeId, String service, long ttlMs) {
        this.redis = redis;
        this.nodeId = nodeId;
        this.lockKey = "leader:" + service;
        this.ttlMs = ttlMs;
    }

    public void start() {
        // Try to acquire leadership every second
        scheduler.scheduleAtFixedRate(this::tryAcquire, 0, 1, TimeUnit.SECONDS);
    }

    private void tryAcquire() {
        if (isLeader) {
            // Already leader — renew TTL
            String result = redis.set(lockKey, nodeId,
                SetParams.setParams().xx().px(ttlMs));  // XX = only if exists
            if (result == null) {
                // Renewal failed — someone else took over (shouldn't happen, but handle it)
                isLeader = false;
                onLeadershipLost();
            }
        } else {
            // Try to acquire
            String result = redis.set(lockKey, nodeId,
                SetParams.setParams().nx().px(ttlMs));  // NX = only if not exists
            if ("OK".equals(result)) {
                isLeader = true;
                onLeadershipAcquired();
            }
        }
    }

    private void onLeadershipAcquired() {
        System.out.println(nodeId + " became leader");
        // Start leader-only work: schedule jobs, coordinate shards, etc.
    }

    private void onLeadershipLost() {
        System.out.println(nodeId + " lost leadership");
        // Stop leader-only work
    }

    public boolean isLeader() { return isLeader; }

    public void shutdown() { scheduler.shutdown(); }
}
```

---

## Algorithm 3 — ZooKeeper-Based (Production Grade)

```
ZooKeeper uses ephemeral sequential znodes.

ELECTION:
  Each node creates: /election/node-{sequentialId}  (ephemeral)

  Node with LOWEST sequence number = leader

  Every non-leader watches the node just BEFORE it in the sequence:
    node-0001 = leader
    node-0002 watches node-0001
    node-0003 watches node-0002
    node-0004 watches node-0003

LEADER FAILS:
  node-0001 dies → its ephemeral znode deleted automatically
  node-0002 gets notified → node-0002 checks — it's now the lowest → becomes leader
  node-0003 now watches node-0002

This is the "chain watch" pattern — avoids thundering herd.
```

```
Why NOT all nodes watch the leader?
  If 100 nodes all watch /election/node-0001
  → Leader dies → 100 simultaneous notifications to ZK → thundering herd
  
Chain watch: each node only watches its predecessor → only 1 notification per failure
```

---

## Trace Through — Redis Election

```
3 nodes: N1, N2, N3 (all start simultaneously)

T=0:   All 3 try: SET leader:scheduler {nodeId} NX PX 5000
       N1 wins (first to arrive) → isLeader = true
       N2, N3 fail → isLeader = false

T=1s:  N1 renews: SET leader:scheduler N1 XX PX 5000  ✅
       N2, N3 try NX → fail (key exists) → still followers

T=3s:  N1 crashes — no more renewals

T=5s:  TTL expires → key deleted

T=6s:  N2 and N3 try NX → N2 wins → isLeader = true
       N2 onLeadershipAcquired() called
       N3 → still follower
```

---

## Follow-up Depth Points

**1. Split-Brain scenario?**
> Redis: N1 thinks it's still leader (slow renewal), TTL expires, N2 becomes leader → both N1 and N2 are acting as leaders simultaneously. Solution: N1 must check lock ownership before acting on each leader-only operation.

**2. Fencing tokens?**
> Every time leadership changes, increment a token. Leader must include fencing token in all operations. Storage layer rejects operations with stale tokens. Prevents split-brain side effects.

**3. Why not just use a DB row as a lock?**
> Works but: DB becomes a single point of failure. Also harder to handle TTL and automatic expiry. Redis is purpose-built for this.

**4. Raft vs Paxos?**
> Production consensus algorithms used by etcd (Raft) and ZooKeeper (ZAB, similar to Paxos). These handle network partitions correctly — guarantee safety (no split-brain) even during partitions. Much harder to implement than Bully or Redis.

**5. What happens during a network partition?**
> Redis: minority partition can't reach Redis → can't renew → loses leadership (correct)
> ZooKeeper: minority partition loses quorum → ZK unavailable → no election (safe, but unavailable)
> Tradeoff: CP (ZooKeeper, etcd) vs AP (custom Redis — risk of split-brain)

---

## Comparison

| Approach | Complexity | Split-Brain Risk | Use Case |
|---|---|---|---|
| Bully Algorithm | Low | High | Learning, single DC |
| Redis NX + TTL | Medium | Low (with fencing) | Most production services |
| ZooKeeper / etcd | High | Very Low | Mission-critical, financial systems |
| Raft (etcd) | Very High | None | Database replication, K8s |

---

## One-Line Interview Answer

> *"Leader election ensures exactly one node coordinates work at any time. For production, I'd use Redis SET NX with a TTL — the leader heartbeats to renew, and if it dies the TTL expires and another node acquires it. The key risk is split-brain during network partitions, which I'd handle with fencing tokens — each leadership term gets an incrementing token, and the storage layer rejects stale-token operations. For stricter consistency requirements I'd use etcd or ZooKeeper, which use Raft/ZAB to guarantee no split-brain even during partitions, at the cost of availability."*
