
# Building a Production-Grade Real-Time Dashboard
**Technologies:** Spring Boot, Kafka, Redis, MySQL, WebSockets (STOMP), Server-Sent Events (SSE).

---

## 1. Project Overview & Architecture

We are building a scalable real-time system with two distinct data pipelines. It is crucial to understand why we treat these differently.



### A. The Interactive Pipeline (Global Chat)
* **Goal:** Instant, bi-directional communication between users.
* **Tech Stack:** WebSockets (STOMP) + Redis Pub/Sub + MySQL.
* **Architecture:** "Hybrid Model"
    * **Redis:** Handles the "Live Feed" (Fan-out to 50k+ users). It is fast and ephemeral.
    * **MySQL:** Handles the "Source of Truth" (History). It is reliable and persistent.
    * **WebSockets:** Provides the persistent, low-latency connection to the browser.

### B. The Streaming Pipeline (Crypto Ticker)
* **Goal:** Unidirectional high-speed data push (Stock Prices).
* **Tech Stack:** Kafka + Server-Sent Events (SSE).
* **Architecture:**
    * **Producer:** Simulates market data and pushes to **Kafka** (Buffering).
    * **Consumer:** Spring Boot reads from Kafka.
    * **Push:** `SseService` broadcasts data to all open HTTP connections.

---

## 2. Deep Dive: Protocol Internals

To understand how the data moves, we must look at the network layer. Both technologies rely on **TCP**, but they utilize it differently.

### A. The Foundation: The TCP Handshake
Before either WebSockets or SSE can start, a standard TCP connection must be established at the Operating System level.
1.  **SYN:** The Client sends a packet with flag $SYN$ ("I want to connect").
2.  **SYN-ACK:** The Server responds with $SYN, ACK$ ("I acknowledge you").
3.  **ACK:** The Client sends a final $ACK$ ("Connection established").
**Result:** A bi-directional "pipe" is now open.

### B. WebSockets: The "Protocol Upgrade"
WebSockets start life as a standard HTTP request but quickly "transform" into a raw data tunnel.



**1. The Request (Client $\to$ Server)**
The browser sends a GET request with "Magic Headers":
* `Connection: Upgrade`: "I want to change the protocol."
* `Upgrade: websocket`: "Change it to WebSocket please."
* `Sec-WebSocket-Key`: A random base64 string (Security challenge).

**2. The Response (Server $\to$ Client)**
If Spring Boot accepts the connection, it sends a **101 Switching Protocols** response.
* `Sec-WebSocket-Accept`: The calculated answer to the client's challenge key.

**3. The Data (Frames)**
Once the upgrade happens, HTTP rules (headers, cookies) stop. Data flows in **Binary Frames**:
$$\text{Frame} = [\text{Opcode}] + [\text{Length}] + [\text{Masking Key}] + [\text{Payload}]$$
* **Opcode:** Tells the server if the message is Text, Binary, or a Ping.
* **Efficiency:** No HTTP overhead per message. This makes it milliseconds faster than standard HTTP.

### C. Server-Sent Events (SSE)
SSE is simpler: it is an HTTP request that *never ends*.
* **Request:** `GET /stream` with `Accept: text/event-stream`.
* **Response:** The server keeps the connection open (`Connection: keep-alive`).
* **Data Format:** Strict text-based stream.
    ```text
    data: {"price": 100}
    <Empty Line>
    ```

---

## 3. Backend Implementation (Spring Boot)

### Configuration Layer

**1. Kafka Config (`KafkaConfig.java`)**
* **Why:** Configures the topic with partitions for scalability.
* **Critical Security Detail:** By default, Kafka prevents deserializing objects from unknown packages. We use `JsonDeserializer` with `typeMapper.addTrustedPackages("*")` to allow our custom Java objects to be read.

**2. Redis Config (`RedisConfig.java`)**
* **Why:** Configures the Pub/Sub channels (`global-chat`, `typing`).
* **Critical Fix:** We manually configure `StringRedisSerializer`.
    * *Without this:* Java uses `JdkSerializationRedisSerializer`, which adds binary gibberish to the data in Redis.
    * *With this:* Data in Redis is clean, human-readable JSON strings.

**3. WebSocket Config (`WebSocketConfig.java`)**
* **Endpoint:** `/ws-endpoint` (The URL where the Handshake happens).
* **Broker:** Enables `/topic` (Outbound to client) and `/app` (Inbound from client).

### Service Layer (The Brains)

**1. Chat Service (`ChatService.java`)**
* **Hybrid Logic:**
    1.  **Save to MySQL First:** We prioritize consistency. If the DB save fails, we *do not* send the message to the chat. This prevents "Ghost Messages."
    2.  **Publish to Redis:** Once saved, we convert the message to JSON and push it to the Redis topic. This ensures other server instances receive the message.

**2. Redis Subscriber (`RedisSubscriber.java`)**
* **Role:** Listens to the Redis Cloud.
* **Action:** When a message arrives from Redis, this service uses `SimpMessagingTemplate` to push it to the *local* WebSocket users connected to this specific server instance.

**3. Market Engine (`MarketEngine.java`)**
* **Producer:** An `@Scheduled` task generates random data and pushes to Kafka.
* **Consumer:** An `@KafkaListener` reads from Kafka.
* **Why split this?** It decouples data generation from data distribution. If the frontend crashes, the Kafka queue holds the data safely.

**4. SSE Service (`SseService.java`)**
* **Concurrency:** Uses `CopyOnWriteArrayList` to manage the list of active user connections. Standard lists would throw exceptions if a user disconnects while we are looping through the list.

---

## 4. The Code Flow: Lifecycle of a Message

Here is the exact path data takes through the system.

### Scenario A: A User Sends a Chat Message
**Flow:** `Browser` $\to$ `Spring Boot` $\to$ `MySQL` $\to$ `Redis` $\to$ `Spring Boot` $\to$ `All Browsers`

1.  **Ingest:** The Browser (STOMP) sends a JSON payload to `/app/chat`.
2.  **Controller:** `@MessageMapping` captures the message and calls `ChatService`.
3.  **Persistence:** `ChatService` saves the entity to the **MySQL** database.
4.  **Fan-Out (Redis):** `ChatService` serializes the object and publishes it to the **Redis** `global-chat` channel.
5.  **Sync:** Every Spring Boot instance (including the sender) receives the Redis event in `RedisSubscriber`.
6.  **Broadcast:** `RedisSubscriber` asks the internal `SimpleBroker` to push the message to all WebSocket sessions subscribed to `/topic/messages`.

### Scenario B: Market Ticker Update
**Flow:** `MarketEngine` $\to$ `Kafka` $\to$ `Listener` $\to$ `SSE Controller` $\to$ `Browser`

1.  **Generate:** `MarketEngine` creates a random stock price.
2.  **Buffer:** The engine pushes the price to **Kafka** topic `stock-prices`.
3.  **Consume:** The `@KafkaListener` pulls the record from the topic.
4.  **Broadcast:** The listener calls `SseService.sendToAll()`.
5.  **Write:** The service loops through all active `SseEmitter` objects and writes the JSON to the open HTTP output stream.

---

## 5. Frontend Implementation (The "Layer Cake")

**File:** `src/main/resources/static/index.html`

To make WebSockets reliable in production, we use two specific libraries. It is important to know which does what.

### 1. SockJS (The "Safety Net")
* **Role:** The Connection Manager.
* **The Problem:** Corporate firewalls and mobile networks often block non-standard ports or the `Upgrade` header required for WebSockets.
* **The Solution:** If `new WebSocket()` fails, SockJS automatically downgrades to **HTTP Long Polling**. This ensures the user *always* connects, even on a restricted network.

### 2. STOMP.js (The "Language")
* **Role:** The Protocol Wrapper.
* **The Problem:** WebSockets are just a raw pipe. They have no concept of "Channels" or "Routing."
* **The Solution:** STOMP runs *on top* of the WebSocket. It gives us standard commands:
    * `SUBSCRIBE /topic/chat` (Listen here)
    * `SEND /app/chat` (Send here)
* **Analogy:** If WebSockets are the **Telephone Line**, STOMP is the **Language** you speak (English/Spanish) so the other person understands you.

### Features Implemented
* **SSE Listener:** Uses standard `EventSource` to listen for `market-tick`.
* **Typing Indicator:** Implements "Debouncing." It only sends a "User is typing" event if the user has been typing for >500ms. This saves network bandwidth.

---

## 6. Architectural Deep Dives

### Q: Why Save to MySQL *before* Redis?
**A:** Consistency. If we push to Redis first, users see the message instantly. But if the subsequent DB save fails (crash/error), the message vanishes on refresh. This "Ghost Message" destroys trust. We treat the DB as the primary reality.

### Q: Redis vs. RabbitMQ vs. Kafka?
| Feature | Redis Pub/Sub | Kafka |
| --- | --- | --- |
| **Durability** | None (Fire & Forget) | Extreme (Log-based) |
| **Latency** | Microseconds | Milliseconds |
| **Our Use** | **Chat** (Needs speed) | **Market Data** (Needs buffering) |

---

## 7. Troubleshooting Guide

### Issue: "Broken Pipe" Exception
* **Log:** `java.io.IOException: Broken pipe`
* **Cause:** The user closed the browser tab. The server tried to push an SSE update, but the TCP socket was already closed by the Client OS.
* **Fix:** This is normal behavior. Catch the exception and remove the `SseEmitter` from the list.

### Issue: "NodeExistsException"
* **Cause:** Zookeeper/Kafka issues. A previous Docker container crashed but didn't release its ID.
* **Fix:** Run `docker-compose down --volumes` to wipe the slate clean.

### Issue: "0 Active Users"
* **Observation:** You have tabs open, but the server says 0 users.
* **Cause:** Browser Tab Discarding. Chrome freezes JavaScript in background tabs to save RAM. The WebSocket heartbeat fails, and the connection drops. Focus the tab to reconnect.

---

## 8. Runbook: How to Start

1.  **Start Infrastructure:**
    * Ensure Docker is running for Redis, Kafka, MySQL.

2.  **Create Database:**
    * Connect to MySQL and run: `CREATE DATABASE websockets_db;`

3.  **Run Application:**
    * `./gradlew bootRun`

4.  **Access Frontend:**
    * Open `http://localhost:8080/index.html` (Do NOT double-click the file; it must be served by the backend).

    