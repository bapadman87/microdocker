# Problem 5 — LRU Cache with TTL

> **Implement an LRU (Least Recently Used) Cache that evicts entries both when capacity is exceeded and when entries expire.**

---

## Why Does It Exist?

```
Cache with no eviction → memory grows unbounded → OOM
Cache with random eviction → poor hit rate
LRU eviction → keeps recently used items → best hit rate for typical access patterns
TTL → ensures stale data doesn't live forever (correctness guarantee)
```

---

## Two Eviction Policies Combined

```
1. LRU Eviction    → when cache is FULL, evict the Least Recently Used entry
2. TTL Eviction    → when entry's age > TTL, evict regardless of how recently used
```

---

## Core Data Structures

```
HashMap alone → O(1) get/put, but no ordering → can't find LRU
LinkedList alone → ordering, but O(n) get → too slow

Solution: HashMap + Doubly Linked List together

HashMap  → key → node (O(1) lookup)
DLL      → maintains access order (MRU at head, LRU at tail)

On GET:  find node via map → move to head → O(1)
On PUT:  add node at head → if full, remove tail → O(1)
On TTL:  check timestamp on get → evict if expired
```

---

## Implementation

```java
import java.util.HashMap;
import java.util.Map;

public class LRUCacheWithTTL<K, V> {

    private final int capacity;
    private final long ttlMs;
    private final Map<K, Node<K, V>> map;
    private final Node<K, V> head;   // dummy head (MRU side)
    private final Node<K, V> tail;   // dummy tail (LRU side)

    static class Node<K, V> {
        K key;
        V value;
        long expiryTime;
        Node<K, V> prev, next;

        Node(K key, V value, long expiryTime) {
            this.key = key;
            this.value = value;
            this.expiryTime = expiryTime;
        }
    }

    public LRUCacheWithTTL(int capacity, long ttlMs) {
        this.capacity = capacity;
        this.ttlMs = ttlMs;
        this.map = new HashMap<>();

        // Dummy head and tail simplify edge cases
        head = new Node<>(null, null, 0);
        tail = new Node<>(null, null, 0);
        head.next = tail;
        tail.prev = head;
    }

    public synchronized V get(K key) {
        Node<K, V> node = map.get(key);
        if (node == null) return null;

        // Check TTL
        if (System.currentTimeMillis() > node.expiryTime) {
            evict(node);
            return null;   // expired
        }

        moveToHead(node);  // mark as recently used
        return node.value;
    }

    public synchronized void put(K key, V value) {
        Node<K, V> existing = map.get(key);
        if (existing != null) {
            existing.value = value;
            existing.expiryTime = System.currentTimeMillis() + ttlMs;
            moveToHead(existing);
            return;
        }

        Node<K, V> newNode = new Node<>(key, value, System.currentTimeMillis() + ttlMs);
        map.put(key, newNode);
        addToHead(newNode);

        if (map.size() > capacity) {
            Node<K, V> lru = removeTail();   // evict LRU
            map.remove(lru.key);
        }
    }

    // --- DLL operations ---

    private void addToHead(Node<K, V> node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void moveToHead(Node<K, V> node) {
        removeNode(node);
        addToHead(node);
    }

    private Node<K, V> removeTail() {
        Node<K, V> lru = tail.prev;
        removeNode(lru);
        return lru;
    }

    private void evict(Node<K, V> node) {
        removeNode(node);
        map.remove(node.key);
    }

    public synchronized int size() { return map.size(); }
}
```

---

## Usage

```java
LRUCacheWithTTL<String, User> cache = new LRUCacheWithTTL<>(100, 60_000); // 100 entries, 60s TTL

cache.put("user:123", user);

User u = cache.get("user:123");  // null if expired or evicted
```

---

## Trace Through

```
Cache capacity = 3, TTL = 60s

put(A) → [A] ← head          tail has nothing
put(B) → [B, A]
put(C) → [C, B, A]

get(A) → A found, not expired → move to head
         [A, C, B]

put(D) → capacity exceeded → evict LRU (B, at tail)
         [D, A, C]   map removes B
```

---

## Follow-up Depth Points

**1. Lazy vs Eager TTL eviction?**
> Current impl is lazy — expired entries are evicted only on `get()`. Memory can fill with expired entries.
> For eager eviction: background thread scans and removes expired entries periodically.
> Trade-off: lazy is simpler, eager uses more CPU but keeps memory clean.

**2. Thread safety?**
> `synchronized` on get/put is simple but coarse-grained. For high concurrency use `ConcurrentHashMap` + `ReentrantReadWriteLock` on the DLL, or segment the cache into buckets.

**3. Why dummy head and tail?**
> Avoids null checks on edge cases (empty list, single element). `head.next` is always the MRU node. `tail.prev` is always the LRU node. Simplifies `addToHead` and `removeTail`.

**4. Production alternative?**
> Use Caffeine — the state-of-the-art Java caching library. W-TinyLFU eviction policy, async TTL refresh, stats, better hit rate than pure LRU.

**5. LRU vs LFU?**
> LRU: evicts least recently used (good for recency-based access)
> LFU: evicts least frequently used (good for frequency-based access, e.g. CDN)
> LFU is harder to implement efficiently — needs frequency counter + min-heap.

**6. Write-Through vs Cache-Aside?**
> On `put()`, do you also write to DB? That's write-through. Or do you let the caller manage DB writes? That's cache-aside. LRU cache is a building block — the consistency policy is a separate design decision.

---

## One-Line Interview Answer

> *"LRU Cache is a HashMap + Doubly Linked List. The map gives O(1) lookup; the DLL maintains access order with MRU at head and LRU at tail. On get, move to head. On eviction, remove tail. TTL is stored per-node and checked on access — lazy eviction. For production I'd use Caffeine which implements W-TinyLFU — better hit rate than pure LRU with built-in TTL and async refresh."*
