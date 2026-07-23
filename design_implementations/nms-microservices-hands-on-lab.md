# NMS Microservices Hands-On Lab (Step-by-Step)

A self-contained guide to build a **minimal but real** slice of the NMS microservices
architecture so you can speak from experience in the interview. When you finish, you will
have run a multi-service system with an API Gateway, service-to-service events over Kafka,
a database, and Docker — all locally with one command.

> Companion design doc: `nms-architecture.md`. This lab implements a thin vertical slice of it.

## What you will build

```
Client ──HTTP──> API Gateway ──routes──> Device Management Service ──> PostgreSQL
                                                │
                                                └── publishes "device.discovered" ──> Kafka
                                                                                       │
                                              Monitoring Service <── consumes ─────────┘
                                                (logs / starts "monitoring" the device)
```

- **api-gateway** — Spring Cloud Gateway, single entry point on port 8080.
- **device-management-service** — REST `POST /devices` + `GET /devices`, stores devices in
  PostgreSQL, publishes a `device.discovered` event to Kafka.
- **monitoring-service** — consumes `device.discovered` from Kafka and reacts (logs that it
  started monitoring the device).
- **PostgreSQL** and **Kafka** run as containers via `docker-compose`.

This demonstrates: microservice boundaries, an API gateway, sync REST, async event-driven
messaging, database persistence, and containerization — every talking point you need.

---

## 0. Prerequisites (install once per environment)

| Tool | Version | Check command |
|---|---|---|
| Java (JDK) | 17+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| Docker | recent | `docker --version` |
| Docker Compose | v2 | `docker compose version` |
| curl (or Postman) | any | `curl --version` |

Notes:
- On Windows, install **Docker Desktop** (includes Compose v2). Ensure it is running before the compose steps.
- You do **not** need to install PostgreSQL or Kafka locally — Docker provides them.
- If you prefer, you can generate the Spring projects from https://start.spring.io instead of
  hand-copying `pom.xml` files; the dependencies are listed in each step.

---

## 1. Create the project structure

Create a root folder and the three service folders:

```powershell
mkdir nms-lab
cd nms-lab
mkdir api-gateway
mkdir device-management-service
mkdir monitoring-service
```

Target layout when finished:

```
nms-lab/
  docker-compose.yml
  api-gateway/
    pom.xml
    Dockerfile
    src/main/java/com/nms/gateway/ApiGatewayApplication.java
    src/main/resources/application.yml
  device-management-service/
    pom.xml
    Dockerfile
    src/main/java/com/nms/device/DeviceManagementApplication.java
    src/main/java/com/nms/device/Device.java
    src/main/java/com/nms/device/DeviceRepository.java
    src/main/java/com/nms/device/DeviceController.java
    src/main/java/com/nms/device/DeviceEventPublisher.java
    src/main/resources/application.yml
  monitoring-service/
    pom.xml
    Dockerfile
    src/main/java/com/nms/monitoring/MonitoringApplication.java
    src/main/java/com/nms/monitoring/DeviceEventConsumer.java
    src/main/resources/application.yml
```

---

## 2. Device Management Service

This service owns device inventory, exposes REST, and publishes an event on create.

### 2.1 `device-management-service/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.2</version>
    <relativePath/>
  </parent>
  <groupId>com.nms</groupId>
  <artifactId>device-management-service</artifactId>
  <version>1.0.0</version>
  <properties>
    <java.version>17</java.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.kafka</groupId>
      <artifactId>spring-kafka</artifactId>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

### 2.2 `.../com/nms/device/DeviceManagementApplication.java`

```java
package com.nms.device;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DeviceManagementApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeviceManagementApplication.class, args);
    }
}
```

### 2.3 `.../com/nms/device/Device.java`

```java
package com.nms.device;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String hostname;
    private String ipAddress;
    private String vendor;

    public Device() {}

    public Device(String hostname, String ipAddress, String vendor) {
        this.hostname = hostname;
        this.ipAddress = ipAddress;
        this.vendor = vendor;
    }

    public Long getId() { return id; }
    public String getHostname() { return hostname; }
    public String getIpAddress() { return ipAddress; }
    public String getVendor() { return vendor; }

    public void setHostname(String hostname) { this.hostname = hostname; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public void setVendor(String vendor) { this.vendor = vendor; }
}
```

### 2.4 `.../com/nms/device/DeviceRepository.java`

```java
package com.nms.device;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, Long> {
}
```

### 2.5 `.../com/nms/device/DeviceEventPublisher.java`

```java
package com.nms.device;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DeviceEventPublisher {

    private static final String TOPIC = "device.discovered";
    private final KafkaTemplate<String, String> kafkaTemplate;

    public DeviceEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishDeviceDiscovered(Device device) {
        // In production this would be a versioned JSON schema; kept simple here.
        String payload = String.format(
            "{\"eventType\":\"DeviceDiscovered\",\"deviceId\":%d,\"hostname\":\"%s\",\"ipAddress\":\"%s\"}",
            device.getId(), device.getHostname(), device.getIpAddress());
        // Key by deviceId so ordering is preserved per device (partitioning talking point).
        kafkaTemplate.send(TOPIC, String.valueOf(device.getId()), payload);
    }
}
```

### 2.6 `.../com/nms/device/DeviceController.java`

```java
package com.nms.device;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/devices")
public class DeviceController {

    private final DeviceRepository repository;
    private final DeviceEventPublisher publisher;

    public DeviceController(DeviceRepository repository, DeviceEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @GetMapping
    public List<Device> listDevices() {
        return repository.findAll();
    }

    @PostMapping
    public ResponseEntity<Device> createDevice(@RequestBody Device device) {
        Device saved = repository.save(device);
        publisher.publishDeviceDiscovered(saved);
        return ResponseEntity.ok(saved);
    }
}
```

### 2.7 `device-management-service/src/main/resources/application.yml`

```yaml
server:
  port: 8081

spring:
  application:
    name: device-management-service
  datasource:
    url: jdbc:postgresql://postgres:5432/nms
    username: nms
    password: nms
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
  kafka:
    bootstrap-servers: kafka:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

### 2.8 `device-management-service/Dockerfile`

```dockerfile
# --- build stage ---
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Use the Maven wrapper-free approach: install maven in the build image
RUN apt-get update && apt-get install -y maven && mvn -q -DskipTests package

# --- runtime stage ---
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd -r -u 1001 appuser
COPY --from=build /app/target/*.jar app.jar
USER 1001
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

> Tip: for faster/repeatable builds, generate the Maven wrapper (`mvn -N wrapper:wrapper`) and
> use `./mvnw` in the Dockerfile instead of installing Maven each build.

---

## 3. Monitoring Service

Consumes the `device.discovered` event and reacts.

### 3.1 `monitoring-service/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.2</version>
    <relativePath/>
  </parent>
  <groupId>com.nms</groupId>
  <artifactId>monitoring-service</artifactId>
  <version>1.0.0</version>
  <properties>
    <java.version>17</java.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.kafka</groupId>
      <artifactId>spring-kafka</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

### 3.2 `.../com/nms/monitoring/MonitoringApplication.java`

```java
package com.nms.monitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MonitoringApplication {
    public static void main(String[] args) {
        SpringApplication.run(MonitoringApplication.class, args);
    }
}
```

### 3.3 `.../com/nms/monitoring/DeviceEventConsumer.java`

```java
package com.nms.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DeviceEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeviceEventConsumer.class);

    @KafkaListener(topics = "device.discovered", groupId = "monitoring-service")
    public void onDeviceDiscovered(String message) {
        // In a real service this would start a polling/streaming job for the device.
        log.info("Monitoring started for newly discovered device. Event = {}", message);
    }
}
```

### 3.4 `monitoring-service/src/main/resources/application.yml`

```yaml
server:
  port: 8082

spring:
  application:
    name: monitoring-service
  kafka:
    bootstrap-servers: kafka:9092
    consumer:
      group-id: monitoring-service
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

### 3.5 `monitoring-service/Dockerfile`

```dockerfile
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apt-get update && apt-get install -y maven && mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd -r -u 1001 appuser
COPY --from=build /app/target/*.jar app.jar
USER 1001
EXPOSE 8082
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

---

## 4. API Gateway

Single northbound entry point that routes `/api/v1/devices/**` to the device service.

### 4.1 `api-gateway/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.2</version>
    <relativePath/>
  </parent>
  <groupId>com.nms</groupId>
  <artifactId>api-gateway</artifactId>
  <version>1.0.0</version>
  <properties>
    <java.version>17</java.version>
    <spring-cloud.version>2023.0.3</spring-cloud.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-gateway</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
  </dependencies>
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-dependencies</artifactId>
        <version>${spring-cloud.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

### 4.2 `.../com/nms/gateway/ApiGatewayApplication.java`

```java
package com.nms.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

### 4.3 `api-gateway/src/main/resources/application.yml`

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      routes:
        - id: device-management
          uri: http://device-management-service:8081
          predicates:
            - Path=/api/v1/devices/**
          filters:
            - RewritePath=/api/v1/devices(?<segment>/?.*), /devices${segment}

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

### 4.4 `api-gateway/Dockerfile`

```dockerfile
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apt-get update && apt-get install -y maven && mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd -r -u 1001 appuser
COPY --from=build /app/target/*.jar app.jar
USER 1001
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

---

## 5. docker-compose (glue everything together)

### `nms-lab/docker-compose.yml`

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: nms
      POSTGRES_USER: nms
      POSTGRES_PASSWORD: nms
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U nms"]
      interval: 5s
      timeout: 3s
      retries: 10

  # Single-node Kafka in KRaft mode (no ZooKeeper needed)
  kafka:
    image: bitnami/kafka:3.7
    environment:
      KAFKA_CFG_NODE_ID: "1"
      KAFKA_CFG_PROCESS_ROLES: "controller,broker"
      KAFKA_CFG_CONTROLLER_QUORUM_VOTERS: "1@kafka:9093"
      KAFKA_CFG_LISTENERS: "PLAINTEXT://:9092,CONTROLLER://:9093"
      KAFKA_CFG_ADVERTISED_LISTENERS: "PLAINTEXT://kafka:9092"
      KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP: "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT"
      KAFKA_CFG_CONTROLLER_LISTENER_NAMES: "CONTROLLER"
      ALLOW_PLAINTEXT_LISTENER: "yes"
    ports:
      - "9092:9092"

  device-management-service:
    build: ./device-management-service
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_started
    ports:
      - "8081:8081"

  monitoring-service:
    build: ./monitoring-service
    depends_on:
      kafka:
        condition: service_started
    ports:
      - "8082:8082"

  api-gateway:
    build: ./api-gateway
    depends_on:
      - device-management-service
    ports:
      - "8080:8080"
```

> Service names in compose (`postgres`, `kafka`, `device-management-service`) are the DNS
> names the apps use in their `application.yml` — this is exactly how Kubernetes DNS discovery
> works, which is why we do **not** need Eureka here.

---

## 6. Build and run

From the `nms-lab` folder:

```powershell
# Build all images and start the whole stack
docker compose up --build
```

First run downloads base images and builds jars, so it takes a few minutes. When it settles you
should see the gateway, both services, Postgres, and Kafka logging as started.

To run in the background instead:

```powershell
docker compose up --build -d
docker compose ps
docker compose logs -f monitoring-service
```

---

## 7. Test the end-to-end flow

Open a new terminal.

**7.1 Create a device through the gateway** (note: port 8080 = gateway, path `/api/v1/devices`):

```powershell
curl -X POST http://localhost:8080/api/v1/devices `
  -H "Content-Type: application/json" `
  -d '{"hostname":"core-router-01","ipAddress":"10.0.0.5","vendor":"Juniper"}'
```

Expected: JSON of the saved device including a generated `id`.

**7.2 List devices through the gateway:**

```powershell
curl http://localhost:8080/api/v1/devices
```

**7.3 Confirm the event was consumed by the monitoring service:**

```powershell
docker compose logs monitoring-service | Select-String "Monitoring started"
```

You should see a log line like:
`Monitoring started for newly discovered device. Event = {"eventType":"DeviceDiscovered",...}`

**That log line is the proof of the full path:** Gateway → Device Service → PostgreSQL →
Kafka → Monitoring Service. You built and ran a real event-driven microservice system.

**7.4 Health checks (actuator):**

```powershell
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8080/actuator/health
```

---

## 8. Tear down

```powershell
docker compose down          # stop and remove containers
docker compose down -v       # also remove volumes (fresh DB next time)
```

---

## 9. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Device service exits, `Connection refused` to postgres | App started before DB ready | Compose `depends_on: service_healthy` handles it; if still failing, `docker compose up` again — Spring retries on restart. |
| `UnknownHostException: kafka` | Wrong bootstrap server | Must be `kafka:9092` (compose service name), not `localhost`. |
| Monitoring logs nothing | Consumer offset / timing | `auto-offset-reset: earliest` is set; create another device and watch logs. |
| Gateway returns 404 | Path/rewrite mismatch | Call `/api/v1/devices`, not `/devices`, on port 8080. |
| Port already in use | Local process on 8080/8081/8082/5432/9092 | Stop the other process or change the host port mapping in compose. |
| Slow/failed Maven download in Docker build | Network/proxy | Configure Maven proxy, or build jars locally with `mvn package` and switch Dockerfiles to copy the prebuilt jar. |

---

## 10. Optional extensions (only if you have time)

Each of these maps directly to a talking point in `nms-architecture.md`:

1. **Add a second device service instance** and see events still processed once per consumer
   group — demonstrates horizontal scaling and consumer groups.
2. **Key events by deviceId** (already done) and explain **partition ordering**.
3. **Add a transactional outbox table** so the event is written in the same DB transaction as
   the device — demonstrates the outbox pattern (no lost events).
4. **Add `spring-boot-starter-validation`** and validate the request body — boundary validation.
5. **Add Prometheus + Grafana** containers and scrape `/actuator/prometheus` — observability.
6. **Add a mock NETCONF server** and have the device service open a session on create —
   demonstrates the southbound adapter and device-session concepts.

---

## 11. What to say in the interview after doing this

- "I built a working slice with an API Gateway, two Spring Boot services, PostgreSQL, and
  Kafka, wired together with docker-compose."
- "The device service persists inventory and **publishes a `device.discovered` event keyed by
  deviceId**; the monitoring service **consumes it in a consumer group** and reacts — that's the
  async, decoupled, event-driven pattern."
- "Services find each other by **DNS service name**, which is exactly how Kubernetes discovery
  works — so I didn't need Eureka."
- "Each service is a **multi-stage Docker image running as a non-root user** with a JRE base and
  a health endpoint."
- "From there, scaling to production means device-session sharding, streaming telemetry,
  the outbox pattern, and observability — which I can walk through."

You now have firsthand experience to back every claim in the architecture document.
