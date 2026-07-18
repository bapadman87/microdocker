# HLD 02 — Notification System

> **Design a notification system that sends Push, Email, and SMS notifications reliably at scale with retry, deduplication, and user preferences.**

---

## Clarify Scale First

```
DAU: 10 million users
Notifications/day: 100 million (10 per user)
Peak QPS: ~50,000 notifications/sec
Channels: Push (FCM/APNs), Email (SendGrid), SMS (Twilio)
SLA: delivery within 30 seconds for high-priority, 5 min for low-priority
```

---

## High-Level Components

```
[ Producers ]            [ Notification Service ]         [ Channels ]
  Order Service    ──▶   API Gateway
  Payment Service  ──▶   │
  Marketing        ──▶   ▼
                   [ Message Router ]
                         │
              ┌──────────┼──────────┐
              ▼          ▼          ▼
         [Push Queue] [Email Q] [SMS Queue]
              │          │          │
              ▼          ▼          ▼
         [Push Worker] [Email W] [SMS Worker]
              │          │          │
              ▼          ▼          ▼
            FCM/APNs  SendGrid   Twilio
```

---

## Core Design Decisions

### 1. Event-Driven with Kafka

```
Each notification is a Kafka event:
  Topic: notifications.push
  Topic: notifications.email
  Topic: notifications.sms

Producers publish → Kafka buffers → Workers consume

Why Kafka:
  ✅ Decouples producers from delivery speed
  ✅ Handles burst (peak traffic absorbed in queue)
  ✅ Replay on failure
  ✅ At-least-once delivery guarantee
```

### 2. Message Router — Fanout + Preference Filtering

```
Incoming event: { userId, type: "ORDER_SHIPPED", payload }

Router steps:
  1. Fetch user preferences from cache
     → User opted out of SMS? Skip SMS topic.
     → User set "email only"? Only publish to email topic.
  2. Fetch user's device tokens (for push)
  3. Publish to appropriate channel topics
```

```java
public class MessageRouter {
    private final KafkaProducer<String, Notification> producer;
    private final UserPreferenceService prefService;

    public void route(NotificationRequest request) {
        UserPreference pref = prefService.get(request.getUserId());

        if (pref.isPushEnabled()) {
            producer.send(new ProducerRecord<>("notifications.push",
                request.getUserId(), toNotification(request, Channel.PUSH)));
        }
        if (pref.isEmailEnabled()) {
            producer.send(new ProducerRecord<>("notifications.email",
                request.getUserId(), toNotification(request, Channel.EMAIL)));
        }
        if (pref.isSmsEnabled()) {
            producer.send(new ProducerRecord<>("notifications.sms",
                request.getUserId(), toNotification(request, Channel.SMS)));
        }
    }
}
```

### 3. Priority Queues

```
High priority (OTP, payment alert): separate Kafka topic, more consumer threads
Low priority (marketing, newsletter): standard topic, fewer consumers

Topics:
  notifications.push.high
  notifications.push.low
  notifications.email.high
  notifications.email.low
```

### 4. Retry with Exponential Backoff

```
FCM/APNs call fails:
  Attempt 1 → wait 1s → retry
  Attempt 2 → wait 2s → retry
  Attempt 3 → wait 4s → retry
  Attempt 4 → Dead Letter Queue (DLQ)

DLQ: manual inspection + alert on high DLQ volume
```

### 5. Deduplication

```
Problem: Kafka at-least-once → same notification sent twice

Solution:
  Generate idempotency key per notification:
    key = hash(userId + notificationType + referenceId)

  Before sending → check Redis:
    SET dedup:{key} 1 NX EX 86400   ← 24h TTL
    If SET fails (key exists) → skip, already sent
    If SET succeeds → send notification
```

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────┐
│                   Notification Service                   │
│                                                         │
│  ┌────────────┐    ┌───────────────┐    ┌────────────┐  │
│  │ REST API   │───▶│ Message Router│───▶│   Kafka    │  │
│  └────────────┘    └───────────────┘    └─────┬──────┘  │
│                           │                   │          │
│                    ┌──────▼──────┐            │          │
│                    │  User Pref  │            │          │
│                    │  Cache(Redis│            │          │
│                    └─────────────┘            │          │
└───────────────────────────────────────────────┼─────────┘
                                                │
         ┌──────────────────┬───────────────────┤
         ▼                  ▼                   ▼
  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
  │ Push Worker │  │Email Worker │  │ SMS Worker  │
  │  (FCM/APNs) │  │ (SendGrid)  │  │  (Twilio)   │
  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘
         │                │                 │
         ▼                ▼                 ▼
  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
  │ Retry Queue │  │ Retry Queue │  │ Retry Queue │
  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘
         └────────────────┴─────────────────┘
                          │
                    ┌─────▼─────┐
                    │    DLQ    │
                    └───────────┘
```

---

## Database Schema

```sql
-- Notification log (for audit + resend)
CREATE TABLE notification_log (
    id             VARCHAR(50) PRIMARY KEY,
    user_id        VARCHAR(50),
    channel        ENUM('PUSH', 'EMAIL', 'SMS'),
    type           VARCHAR(100),
    status         ENUM('PENDING', 'SENT', 'FAILED', 'SKIPPED'),
    idempotency_key VARCHAR(100) UNIQUE,
    sent_at        TIMESTAMP,
    created_at     TIMESTAMP DEFAULT NOW()
);

-- User preferences
CREATE TABLE user_preferences (
    user_id     VARCHAR(50) PRIMARY KEY,
    push_enabled  BOOLEAN DEFAULT TRUE,
    email_enabled BOOLEAN DEFAULT TRUE,
    sms_enabled   BOOLEAN DEFAULT TRUE,
    quiet_hours_start TIME,   -- e.g. 22:00
    quiet_hours_end   TIME    -- e.g. 08:00
);
```

---

## Follow-up Depth Points

**1. Quiet hours — don't disturb at night?**
> Check `quiet_hours` from user preferences in router. If current time is in quiet window → delay delivery: schedule for next morning in Task Scheduler, or use a delayed Kafka topic.

**2. User has 5 devices — push to all?**
> Store multiple device tokens per user in a `device_tokens` table. Push to all active tokens. If token returns `INVALID_REGISTRATION` from FCM → delete that token.

**3. What if FCM is down?**
> Circuit Breaker (Problem 2!) on FCM client. On OPEN state → messages stay in Kafka. When FCM recovers → circuit closes → workers resume consuming.

**4. Rate limiting per channel?**
> Email: max 1 email/min per user (avoid spam complaints → affects sender reputation)
> SMS: max 5 SMS/day per user (Twilio charges per SMS)
> Apply Rate Limiter (Problem 1!) at the worker level before calling provider.

**5. Analytics — open rate, click rate?**
> Add a tracking pixel in email (1x1 image → hit your server on open).
> Wrap links with tracking redirect (like URL shortener).
> Push: FCM/APNs return delivery receipts.
> Store in ClickHouse for real-time dashboard.

---

## One-Line Interview Answer

> *"Notification system is event-driven — producers publish to Kafka, a Message Router fans out to per-channel topics (push/email/SMS) after checking user preferences. Workers consume and call FCM/SendGrid/Twilio with exponential backoff retry and a DLQ for permanent failures. Deduplication with Redis NX prevents double delivery on Kafka at-least-once redelivery. Priority is handled with separate high/low topics and proportional consumer threads."*
