# Problem 6 — Connection Pool

> **Implement a connection pool that manages a fixed set of reusable database connections.**

---

## Why Does It Exist?

```
Creating a DB connection is EXPENSIVE:
  - TCP handshake
  - Authentication
  - Session setup
  ~100ms per new connection

Without a pool:
  Every request → create connection → query → close connection
  At 1000 req/sec → 1000 new connections/sec → DB overwhelmed

With a pool:
  Pre-create N connections at startup
  Borrow → use → return
  → Connections reused, DB protected, latency reduced
```

---

## Core Design

```
Pool = fixed set of connections (e.g. 10)

States:
  AVAILABLE → connection is idle, ready to borrow
  IN_USE    → connection is borrowed by a thread

Operations:
  borrow()  → take an available connection (block if none available)
  return()  → put connection back into available pool
  validate()→ check if connection is still alive before lending
```

---

## Data Structure Choice

```
BlockingQueue<Connection> is perfect:

  borrow() → queue.poll(timeout)   → blocks until connection available
  return() → queue.offer(conn)     → puts back immediately

No manual locking needed — BlockingQueue handles thread safety.
```

---

## Implementation

```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class ConnectionPool {

    private final BlockingQueue<Connection> pool;
    private final String url;
    private final String user;
    private final String password;
    private final int poolSize;

    public ConnectionPool(String url, String user, String password, int poolSize)
            throws SQLException {
        this.url = url;
        this.user = user;
        this.password = password;
        this.poolSize = poolSize;
        this.pool = new ArrayBlockingQueue<>(poolSize);

        // Pre-create all connections at startup
        for (int i = 0; i < poolSize; i++) {
            pool.offer(createConnection());
        }
    }

    /**
     * Borrow a connection. Blocks up to timeoutMs if none available.
     */
    public Connection borrow(long timeoutMs) throws InterruptedException, SQLException {
        Connection conn = pool.poll(timeoutMs, TimeUnit.MILLISECONDS);

        if (conn == null) {
            throw new SQLException("Connection pool exhausted — timed out after " + timeoutMs + "ms");
        }

        // Validate before lending (connection might have gone stale)
        if (!isValid(conn)) {
            conn = createConnection();   // replace dead connection
        }

        return conn;
    }

    /**
     * Return a connection back to the pool.
     */
    public void returnConnection(Connection conn) {
        if (conn != null) {
            if (!isValid(conn)) {
                try { conn = createConnection(); } catch (SQLException e) {
                    // log — pool shrinks by 1, could add monitoring alert here
                    return;
                }
            }
            pool.offer(conn);   // return to pool (non-blocking)
        }
    }

    private Connection createConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private boolean isValid(Connection conn) {
        try {
            return conn != null && !conn.isClosed() && conn.isValid(1);
        } catch (SQLException e) {
            return false;
        }
    }

    public int availableConnections() {
        return pool.size();
    }

    public void shutdown() {
        pool.forEach(conn -> {
            try { conn.close(); } catch (SQLException ignored) {}
        });
        pool.clear();
    }
}
```

---

## Usage (Try-With-Resources Pattern)

```java
ConnectionPool pool = new ConnectionPool(
    "jdbc:postgresql://localhost/mydb",
    "user", "password",
    10      // pool size
);

// Borrow, use, always return in finally
Connection conn = null;
try {
    conn = pool.borrow(5_000);   // wait up to 5 seconds
    // ... execute queries ...
} finally {
    pool.returnConnection(conn); // ALWAYS return
}
```

---

## Trace Through

```
Pool size = 3
Connections: [C1, C2, C3]

Thread 1 borrows C1 → pool: [C2, C3]
Thread 2 borrows C2 → pool: [C3]
Thread 3 borrows C3 → pool: []
Thread 4 borrows    → pool empty → BLOCKS (waiting)

Thread 1 returns C1 → pool: [C1]
Thread 4 unblocks   → gets C1 ✅
```

---

## Follow-up Depth Points

**1. What if a thread borrows and never returns?**
> Connection leak — pool shrinks to 0 over time. Solutions:
> - Always use try-finally or try-with-resources
> - Track borrow timestamps — background thread reclaims connections borrowed for > N seconds
> - HikariCP calls this "connection leak detection threshold"

**2. Pool too small vs too large?**
```
Too small → threads wait → latency spikes
Too large → DB connection limit exceeded → DB crashes
            Each DB connection uses memory on the DB side too

Rule of thumb: pool size = (core count * 2) + effective spindle count
PostgreSQL recommends: small pools (10-20) per service instance
```

**3. Min pool size vs max pool size?**
> Idle connections still hold DB resources. Production pools have:
> - `minIdle` — keep N connections warm even when idle
> - `maxPool` — never exceed this under load
> HikariCP, c3p0, DBCP all support this.

**4. Connection validation cost?**
> `isValid(1)` does a round-trip to DB. Expensive at scale. Options:
> - `testOnBorrow` — validate every borrow (safest, costliest)
> - `testWhileIdle` — background thread validates idle connections
> - `validationQuery` — lightweight query like `SELECT 1`

**5. Why not just use HikariCP?**
> In production, always use HikariCP — fastest Java connection pool, battle-tested. Custom impl is for interviews and understanding internals.

---

## One-Line Interview Answer

> *"Connection Pool pre-creates N connections and manages borrow/return with a BlockingQueue — it naturally handles thread safety and blocking when the pool is empty. Key concerns are connection leaks (try-finally, leak detection timeout), right-sizing the pool (too small = latency, too large = DB overload), and stale connection validation before lending. In production I'd use HikariCP — it handles all of this plus telemetry."*
