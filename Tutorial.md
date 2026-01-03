You are absolutely right. The deep technical breakdown of the protocols (TCP handshakes, HTTP Upgrades, Frame structures) is critical knowledge and should be part of the documentation.

Here is the **Updated and Complete `tutorial.md**`, including the deep-dive sections on Protocol Internals.

```markdown
# Building a Production-Grade Real-Time Dashboard
**Technologies:** Spring Boot, Kafka, Redis, MySQL, WebSockets, Server-Sent Events (SSE), Docker.

---

## 1. Project Overview & Architecture

We are building a scalable real-time system with two distinct data pipelines:

### A. The Interactive Pipeline (Global Chat)
* **Goal:** Instant, bi-directional communication between users.
* **Tech Stack:** WebSockets (STOMP) + Redis Pub/Sub + MySQL.
* **Architecture:** "Hybrid Model"
    * **Redis:** Handles the "Live Feed" (Fan-out to 50k+ users). Fast, ephemeral.
    * **MySQL:** Handles the "Source of Truth" (History). Reliable, persistent.
    * **WebSockets:** Provides the low-latency connection to the browser.

### B. The Streaming Pipeline (Crypto Ticker)
* **Goal:** Unidirectional high-speed data push (Stock Prices).
* **Tech Stack:** Kafka + Server-Sent Events (SSE).
* **Architecture:**
    * **Producer:** Simulates market data and pushes to **Kafka**.
    * **Consumer:** Spring Boot reads from Kafka.
    * **Push:** `SseService` broadcasts data to all open HTTP connections.

---

## 2. Infrastructure (Docker)

We use Docker to replicate a production cloud environment locally.

**File:** `docker-compose.yml`
```yaml
version: '3.8'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on: [zookeeper]
    ports: 
      - "29092:29092" # Exposed for local Java apps
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      # Listeners: Internal (9092) for Docker, External (29092) for Host
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:29092,PLAINTEXT_INTERNAL://kafka:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_INTERNAL:PLAINTEXT
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:29092,PLAINTEXT_INTERNAL://0.0.0.0:9092
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT_INTERNAL
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1

  redis:
    image: redis:alpine
    ports: ["6379:6379"]

  mysql:
    image: mysql:8.0
    container_name: local_mysql
    environment:
      MYSQL_ROOT_PASSWORD: root
    ports: ["3306:3306"]

```

---

## 3. Backend Implementation (Spring Boot)

### Configuration Layer

**1. Kafka Config (`KafkaConfig.java`)**

* **Why:** Configures the topic with 3 partitions (scalability) and trusts all packages for JSON deserialization.
* **Key Detail:** Uses `JsonDeserializer` with `addTrustedPackages("*")` to avoid security exceptions.

**2. Redis Config (`RedisConfig.java`)**

* **Why:** Configures the Pub/Sub channels.
* **Critical Fix:** We use `StringRedisSerializer`. Default Java serialization adds binary headers that break interoperability. JSON Strings are safer and cleaner.
* **Channels:** `global-chat-channel` (Messages), `typing-channel` (Indicators).

**3. WebSocket Config (`WebSocketConfig.java`)**

* **Why:** Upgrades HTTP to WebSocket TCP connections.
* **Endpoints:** `/ws-endpoint` (Handshake URL).
* **Broker:** `/topic` (Outbound), `/app` (Inbound).

### Service Layer (The Brains)

**1. Chat Service (`ChatService.java`)**

* **Hybrid Logic:**
1. **Save to MySQL First:** Ensures data integrity ("Source of Truth"). Prevents "Ghost Messages" where users see data that isn't saved.
2. **Publish to Redis:** Sends a clean JSON string to the "Backplane" to reach other server instances.



**2. Redis Subscriber (`RedisSubscriber.java`)**

* **Role:** Listens to the Redis Cloud.
* **Action:** When a message arrives, it uses `SimpMessagingTemplate` to push it to *local* WebSocket users connected to this specific server instance.
* **Typing Handling:** Distinguishes between "Chat" and "Typing" events based on the channel name.

**3. Market Engine (`MarketEngine.java`)**

* **Producer:** `@Scheduled` task generates random data and pushes to Kafka.
* **Consumer:** `@KafkaListener` reads from Kafka. This decouples data generation from data distribution.

**4. SSE Service (`SseService.java`)**

* **Concurrency:** Uses `CopyOnWriteArrayList` to manage list of active user connections safely.
* **Cleanup:** Automatically removes dead emitters (browsers that closed the tab) to prevent memory leaks.

---

## 4. Deep Dive: Protocol Internals (WebSockets & SSE)

To understand how the data moves, we must look at the network layer. Both technologies rely on **TCP**, but they utilize it differently.

### A. The Foundation: TCP Handshake

Before *either* WebSockets or SSE can start, a standard **TCP connection** is established.

1. **SYN:** Client sends "I want to connect."
2. **SYN-ACK:** Server sends "I acknowledge. Ready."
3. **ACK:** Client sends "Okay, let's go."
   **Result:** A bi-directional pipe is open.

### B. WebSockets: The "Protocol Upgrade"

WebSockets start as HTTP but "transform" into a raw binary tunnel.

**1. The Request (Client -> Server)**
The browser sends an HTTP GET with "Magic Headers":

* `Connection: Upgrade`
* `Upgrade: websocket`
* `Sec-WebSocket-Key: [RandomString]` (A security challenge)

**2. The Response (Server -> Client)**
If Spring Boot accepts, it sends status **101 Switching Protocols**:

* `Connection: Upgrade`
* `Upgrade: websocket`
* `Sec-WebSocket-Accept: [HashedKey]` (The answer to the challenge)

**3. The "Open" State (Binary Frames)**
HTTP is now dead. The connection becomes a raw stream of **Binary Frames**.

* **Structure:** `[Opcode (2 bits)][Payload Length][Masking Key][Data]`
* **Opcodes:**
* `0x1`: Text Frame
* `0x8`: Close Connection
* `0x9`: Ping (Keep-alive)


* **Efficiency:** No HTTP headers (cookies, user-agent) are sent with messages. Extremely lightweight.

### C. Server-Sent Events (SSE): The "Eternal Request"

SSE is a standard HTTP request that **never ends**.

**1. The Request**

* `GET /stream HTTP/1.1`
* `Accept: text/event-stream`

**2. The Response**

* `Content-Type: text/event-stream` (Tells browser: "Don't close this.")
* `Connection: keep-alive`
* `Cache-Control: no-cache`

**3. The Data Stream (Text Format)**
Unlike WebSockets (binary), SSE sends **Text**:

```text
data: {"price": 50000}
<Empty Line>

event: market-tick
data: {"price": 50005}
<Empty Line>

```

* **Broken Pipe Error:** Since this is standard HTTP, if the client closes the tab, the OS closes the socket. The server throws `Broken Pipe` when trying to write to it. This is normal.

### Summary Comparison

| Feature | WebSocket | Server-Sent Events (SSE) |
| --- | --- | --- |
| **Transport** | TCP -> HTTP Handshake -> **Binary Protocol** | TCP -> Standard HTTP (Kept Open) |
| **Data Format** | Binary Frames (Opcodes, Masking) | UTF-8 Text Stream (`data: ... \n\n`) |
| **Direction** | **Bi-directional** (Full Duplex) | **Uni-directional** (Server -> Client) |
| **Firewalls** | Often blocked by corporate proxies. | **Passes easily** (Looks like HTTP). |
| **Reconnection** | **Manual** (Must write JS code). | **Automatic** (Browser built-in). |

---

## 5. Architectural Deep Dives

### Q: Why Save to MySQL *before* Redis?

**A:** Consistency.
If we push to Redis first, users see the message instantly. But if the subsequent DB save fails (crash/error), the message vanishes on refresh. This "Ghost Message" destroys trust. We treat the DB as the primary reality; if it's not in the DB, it didn't happen.

### Q: How do 50,000 users connect? (Hybrid Architecture)

**A:**

1. **Ingestion:** High volume writes go to Kafka (Buffered/Safe).
2. **Live Sync:** Redis handles the fan-out. It pushes to all 20+ Spring Boot servers.
3. **Local Push:** Each Spring Boot server holds ~2,500 connections in RAM (`SimpleBroker`). It loops through *only* its own local users to push the update.

* **Result:** Redis acts as the "Town Crier" so servers don't need to know about each other.

### Q: Redis vs. RabbitMQ vs. Kafka?

| Feature | Redis Pub/Sub | RabbitMQ | Kafka |
| --- | --- | --- | --- |
| **Durability** | None (Fire & Forget) | High (Queues) | Extreme (Log) |
| **Speed** | Microseconds | Milliseconds | Milliseconds |
| **Use Case** | Live Typing / Chat | Complex Routing | Massive Data Stream |

* **Decision:** We used **Redis** for Chat (Need speed) and **Kafka** for Market Data (Need replayability/buffering).

---

## 6. Frontend Implementation (Single Page)

**File:** `src/main/resources/static/index.html`

* **Tech:** SockJS (Fallback for firewalls), Stomp.js (Messaging Protocol).
* **Features:**
* **SSE:** Listens to `/stream` for `market-tick` events.
* **Chat:** Subscribes to `/topic/chat`.
* **Typing:** Subscribes to `/topic/typing`. Implements "Debouncing" (only sends event after 1s of inactivity).
* **History:** Fetches from REST API on load.



---

## 7. Troubleshooting Guide

### Issue: "Broken Pipe" Exception

* **Log:** `java.io.IOException: Broken pipe`
* **Cause:** The user closed the browser tab or reloaded the page. The server tried to push an SSE update to a closed TCP socket.
* **Fix:** Normal behavior. Catch the exception and remove the `SseEmitter` from the list.

### Issue: "NodeExistsException" (Docker)

* **Log:** `KeeperErrorCode = NodeExists`
* **Cause:** A previous Docker container crashed but didn't tell Zookeeper "I'm leaving." The new container tries to claim the same ID.
* **Fix:** `docker-compose down --volumes` (Wipes the Zookeeper data).

### Issue: "0 Active Users"

* **Observation:** You have 3 tabs open, but logs say 2 users.
* **Cause:** Modern browsers "freeze" background tabs to save RAM. The JS stops running, and the WebSocket handshake pauses. Focus the tab to connect.

---

## 8. Runbook: How to Start

1. **Start Infrastructure:**
```bash
docker-compose up -d

```


2. **Create Database:**
* `docker exec -it local_mysql mysql -u root -p` (Pass: `root`)
* `CREATE DATABASE websockets_db;`


3. **Run Application:**
* `./gradlew bootRun`


4. **Access Frontend:**
* Open `http://localhost:8080/index.html` (Do NOT double-click the file).



```

```