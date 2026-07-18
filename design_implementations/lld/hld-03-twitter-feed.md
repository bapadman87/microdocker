# HLD 03 — Twitter / X News Feed

> **Design Twitter's news feed — users post tweets, followers see them in their timeline in near-real-time.**

---

## Clarify Scale First

```
DAU: 300 million
Tweets/day: 500 million (~6,000 tweets/sec)
Read QPS: 600,000 timeline reads/sec (100:1 read/write)
Avg followers per user: 200
Celebrity (Ronaldo): 100 million followers
Latency SLA: timeline load < 500ms
```

---

## The Core Problem — Fan-Out

```
User A posts a tweet.
User A has 1,000 followers.
All 1,000 followers need to see the tweet in their timeline.

How do you "push" that tweet to 1,000 timelines?
Two approaches: Fan-Out on Write vs Fan-Out on Read
```

---

## Approach 1 — Fan-Out on Write (Push Model)

```
On post:
  Write tweet to DB
  For each follower → write tweet_id to their timeline cache in Redis

On read:
  Fetch pre-built timeline from Redis → instant!

Timeline in Redis:
  Key: timeline:{userId}
  Value: Sorted Set of tweet_ids (score = timestamp)
```

**Pros:** Read is O(1) — just fetch from Redis. Very fast timeline load.
**Cons:** Celebrity with 10M followers → posting 1 tweet = 10M Redis writes. Write amplification problem.

---

## Approach 2 — Fan-Out on Read (Pull Model)

```
On post:
  Write tweet to DB only. Nothing else.

On read:
  Fetch IDs of all users you follow
  For each: fetch their recent tweets
  Merge and sort by timestamp → your timeline

Pros: No write amplification.
Cons: Read is expensive — N DB queries for N followees.
      High latency for users following 1,000 people.
```

---

## Production Solution — Hybrid (Twitter's Actual Approach)

```
Regular users (< 10k followers): Fan-Out on Write
  → Pre-build timeline in Redis on post

Celebrity users (> 10k followers): Fan-Out on Read
  → Don't pre-push to all followers
  → On timeline read: merge pre-built timeline + celebrity's recent tweets

Algorithm:
  1. Fetch pre-built timeline from Redis (regular followees)
  2. Identify celebrities you follow
  3. Fetch celebrities' recent tweets from DB/cache
  4. Merge + rank → return final timeline
```

---

## Architecture Diagram

```
[ Tweet Post API ]
        │
        ▼
[ Tweet Service ]
        │
        ├──────────────────────┐
        ▼                      ▼
[ Tweet DB ]           [ Fan-Out Service ]
(Cassandra)                    │
                    ┌──────────┼──────────┐
                    ▼          ▼          ▼
              Regular       Check       Skip
              followers   celebrity?  (>10k flw)
                    │
                    ▼
            [ Redis Timeline ]
            timeline:{userId}
            Sorted Set: tweet_id → timestamp

[ Timeline Read API ]
        │
        ├─── Fetch Redis timeline (regular followees)
        ├─── Fetch celebrities' tweets (DB/cache)
        ├─── Merge + rank
        └─── Return top 20 tweets
```

---

## Data Models

### Tweet Storage — Cassandra

```
Cassandra is ideal: write-heavy, time-series, massive scale

CREATE TABLE tweets (
    user_id   UUID,
    tweet_id  TIMEUUID,          -- includes timestamp
    content   TEXT,
    media_url TEXT,
    created_at TIMESTAMP,
    PRIMARY KEY (user_id, tweet_id)
) WITH CLUSTERING ORDER BY (tweet_id DESC);

-- Fetch user's tweets: query by user_id, sorted by time automatically
```

### Timeline Cache — Redis Sorted Set

```
Key: timeline:{userId}
Members: tweet_ids
Score: tweet timestamp (epoch ms)

ZADD timeline:u1 1720000000 tweet123   ← add tweet
ZREVRANGE timeline:u1 0 19             ← fetch top 20 (newest)
ZREMRANGEBYRANK timeline:u1 0 -1001   ← keep only last 1000
```

### Social Graph — Who follows whom

```
Graph DB (Neo4j) or Redis Sets:

followers:{userId}  → set of follower IDs
following:{userId}  → set of followed IDs

SMEMBERS followers:ronaldo  → [u1, u2, u3 ... 100M]
SMEMBERS following:bala     → [u10, u20, celebrity1]
```

---

## Deep Dives

### Ranking (Not just chronological)

```
Simple: sort by timestamp
Real Twitter: ML-based ranking
  Signals: engagement (likes/retweets), relationship strength, recency
  Offline: ML model trains on engagement data
  Online: scoring service ranks candidate tweets before returning timeline
```

### Media (Images/Video)

```
Never store media in tweet DB.
Tweet stores: media_url pointing to CDN
Upload flow:
  1. Client gets presigned S3 URL from Media Service
  2. Client uploads directly to S3
  3. S3 event → transcoding (video) → store in CDN (CloudFront)
  4. tweet.media_url = CDN URL
```

### Real-Time Delivery (Twitter Streaming)

```
Followers want to see tweets instantly (not on next refresh).
→ WebSocket connection per client to Streaming Service
→ Fan-Out Service pushes tweet_id to connected followers via WebSocket
→ Client fetches full tweet from CDN/cache
```

---

## Follow-up Depth Points

**1. What defines a "celebrity"?**
> Threshold (e.g., > 1M followers). Stored in a Redis Set: `SET celebrity:{userId}`. Fan-Out Service checks this before deciding push vs skip.

**2. How do you handle a user with 1M followees?**
> Reading their timeline requires merging 1M users' tweets — impractical. Cap followed users at 5,000 (Twitter's actual limit) — practical bound.

**3. Eventual consistency in timeline?**
> Fan-out is async — you post a tweet and some followers see it 1-2 seconds later. This is acceptable. Guarantee: tweet is in DB immediately (strong), timeline propagation is eventual.

**4. What happens when Redis timeline cache is empty (cold start)?**
> Rebuild from DB: fetch recent tweets from all followees, populate Redis. This is the "timeline warmup" process, done on first login or cache eviction.

**5. Trending topics?**
> Count hashtag occurrences in a sliding window using Redis sorted sets. Increment score on each tweet with the hashtag. `ZREVRANGE trending 0 9` gives top 10.

---

## One-Line Interview Answer

> *"Twitter feed's core challenge is fan-out — pushing one tweet to millions of followers. The production solution is hybrid: Fan-Out on Write for regular users (pre-build Redis timeline on post), Fan-Out on Read for celebrities (merge their recent tweets at read time). Tweet storage is Cassandra (time-series, write-heavy), timelines are Redis sorted sets, social graph in Redis sets. Real-time delivery via WebSockets; ranking moves from chronological to ML-based signals."*
