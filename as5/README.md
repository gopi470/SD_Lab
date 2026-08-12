# Assignment 5 — URL Shortener with Redis Caching & Consistent Hashing

> **System Design Lab | Semester 5**
> Topics Covered: URL Shortening, Redis Caching, MongoDB Persistence, Consistent Hashing, Docker Deployment

---

## Table of Contents

1. [Repository Overview](#repository-overview)
2. [Project 1 — URL Shortener with Redis (URLWthRedis)](#project-1--url-shortener-with-redis-urlwthredis)
3. [Project 2 — Consistent Hashing (consistent-hashing)](#project-2--consistent-hashing-consistent-hashing)
4. [Common Viva/Demo Questions and Answers](#common-vivademo-questions-and-answers)
5. [Full Repository File Tree](#full-repository-file-tree)

---

## Repository Overview

This assignment has **two Spring Boot projects** inside `URLWthRedis/`:

| Sub-project | Description | Port |
|---|---|---|
| `URLWthRedis` (root) | URL Shortener backed by MongoDB + Redis cache | `8082` |
| `consistent-hashing` | Student record storage using Consistent Hashing over 3 MongoDB nodes | `8082` |

There is also a reference PDF — `EXP5-Consistent Hashing.pdf` — which is the lab experiment sheet.

---

## Project 1 — URL Shortener with Redis (URLWthRedis)

### Objective

Build a **URL shortening service** that:
- Generates a unique 6-character short code for any long URL.
- Stores the mapping in **MongoDB** (persistent storage).
- Uses **Redis** as an in-memory cache to serve repeat lookups without hitting the database.
- Demonstrates the performance benefit of caching via logged metrics (hit/miss counts).

---

### Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.1.0 |
| Database | MongoDB (via Spring Data MongoDB) |
| Cache | Redis (via Spring Data Redis / StringRedisTemplate) |
| Build Tool | Maven (with Maven Wrapper mvnw) |
| Utilities | Lombok (@Data annotation) |
| Container | Docker (Debian Bookworm base image) |
| Logging | SLF4J + Logback (file: logs/urlshortener.log) |

---

### Architecture

```
Client
  |
  v
URLcontroller  (REST Layer — port 8082)
  |
  v
URLService     (Business Logic)
  |          |
  v          v
Redis      MongoDB
(Cache)    (Persistent Store — urlWthR DB)
```

**Cache-Aside Pattern:**
1. On a GET request, check Redis first.
2. If found (cache hit) — return immediately (no DB query).
3. If not found (cache miss) — query MongoDB, store result in Redis for 1 hour, then return.

---

### Project Structure

```
URLWthRedis/
├── src/
│   └── main/
│       ├── java/org/example/url/
│       │   ├── UrlApplication.java          # Spring Boot entry point
│       │   ├── controller/
│       │   │   └── URLcontroller.java       # REST endpoints
│       │   ├── service/
│       │   │   └── URLService.java          # Core business logic + caching
│       │   ├── model/
│       │   │   └── URLSh.java               # MongoDB document model
│       │   └── Repo/
│       │       └── URLrepo.java             # MongoRepository interface
│       └── resources/
│           └── application.properties       # Config: MongoDB, Redis, port
├── Dockerfile                               # Docker container definition
├── start.sh                                 # Docker entrypoint script
├── pom.xml                                  # Maven dependencies
├── consistent-hashing/                      # Sub-project (see Project 2)
└── logs/                                    # Runtime log output directory
```

---

### Key Files Explained

#### UrlApplication.java

The Spring Boot application entry point. Annotated with `@SpringBootApplication` which enables auto-configuration, component scanning, and bean registration.

```java
@SpringBootApplication
public class UrlApplication {
    public static void main(String[] args) {
        SpringApplication.run(UrlApplication.class, args);
    }
}
```

---

#### URLSh.java — MongoDB Document Model

```java
@Data
@Document()
public class URLSh {
    @Id
    private String id;    // MongoDB auto-generated _id
    private String url;   // Original long URL
    private String surl;  // 6-character short code
}
```

- `@Document` maps this class to a MongoDB collection.
- `@Data` (Lombok) auto-generates getters, setters, toString, equals, hashCode.
- `@Id` marks the MongoDB primary key.

---

#### URLrepo.java — Repository Interface

```java
public interface URLrepo extends MongoRepository<URLSh, String> {
    URLSh findBySurl(String surl);
}
```

- Extends `MongoRepository` — gives free CRUD operations (`save`, `findById`, etc.).
- `findBySurl(String surl)` — Spring Data auto-generates the MongoDB query `{ surl: <value> }` from the method name. No query code needed.

---

#### URLService.java — Core Logic

This is the most important class. It contains four main operations:

**1. shorten(String url) — Create Short URL**
- Generates a 6-character UUID substring as shortCode.
- Checks MongoDB to ensure no collision. Re-generates if collision found (loop).
- Creates URLSh object, saves to MongoDB.
- Returns the short code.

**2. findbyurlwr(String shortCode) — Resolve URL WITH Redis**
- Increments totalRequests counter.
- Calls `redisTemplate.opsForValue().get(shortCode)` — checks Redis cache.
- **Cache HIT:** Increments cacheHits, logs the metrics, returns the URL from Redis.
- **Cache MISS:** Increments cacheMisses and mongoQueries, queries MongoDB, stores in Redis (TTL = 1 hour), logs metrics, returns URL.

**3. findbyurl(String shortCode) — Resolve URL WITHOUT Redis (DB-only)**
- Goes directly to MongoDB. Used by the `/db/{url}` endpoint for comparison/testing.

**4. shorten(String url, String alias) — Create Short URL with Custom Alias**
- If alias is provided and not blank, checks if it already exists in MongoDB.
- If alias already taken — throws RuntimeException("Alias already exists").
- Otherwise uses alias as the short code directly.

**Counters (AtomicLong):**

```java
private final AtomicLong totalRequests = new AtomicLong(0);
private final AtomicLong cacheHits     = new AtomicLong(0);
private final AtomicLong cacheMisses   = new AtomicLong(0);
private final AtomicLong mongoQueries  = new AtomicLong(0);
```

`AtomicLong` is used because it is thread-safe — multiple HTTP threads can update counters concurrently without race conditions.

---

#### URLcontroller.java — REST Controller

```java
@RestController
@RequestMapping()
public class URLcontroller { ... }
```

Exposes four endpoints. Uses `@Autowired` to inject URLService.

---

#### application.properties

```properties
spring.application.name=URL
spring.mongodb.uri=mongodb://localhost:27017/urlWthR
logging.file.name=logs/urlshortener.log
server.port=8082
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

- MongoDB URI points to local instance, database name `urlWthR`.
- Redis on default port `6379`.
- App runs on port `8082`.
- Logs written to `logs/urlshortener.log`.

> **Note:** In Docker mode, commented-out lines replace `localhost` with `mongodb` and `redis` (Docker service names).

---

#### Dockerfile

Uses **Debian Bookworm** as base image. Installs:
- `openjdk-17-jdk` — Java runtime
- `maven` — to build the project inside the container
- `redis-server` — Redis instance
- `mongodb-org 7.0` — MongoDB instance

Build steps inside Dockerfile:

```dockerfile
COPY pom.xml . && COPY src ./src
RUN mvn clean package -DskipTests    # Build JAR inside container
COPY start.sh /start.sh
EXPOSE 8082 27017 6379
ENTRYPOINT ["/start.sh"]
```

> The Dockerfile has commented-out multi-stage alternatives. The active version is a monolithic container containing all services.

---

#### start.sh — Container Entrypoint

```bash
#!/bin/bash
mongod --fork --logpath /var/log/mongodb.log   # Start MongoDB as background daemon
redis-server --daemonize yes                   # Start Redis as background daemon
java -jar /app/target/*.jar                    # Start the Spring Boot app (foreground)
```

This runs all three services in one container.

---

### How It Works — Step by Step

#### Shortening a URL

1. Client sends `POST /` with body `https://www.example.com/some/very/long/url`.
2. URLcontroller.shorten() calls URLService.shorten(url).
3. Service generates a random UUID, takes first 6 chars — `"a3f9bc"`.
4. Checks MongoDB: `repo.findBySurl("a3f9bc")`. If exists, regenerates.
5. Saves `URLSh { url: "https://...", surl: "a3f9bc" }` to MongoDB.
6. Returns `"a3f9bc"` to client.

#### Resolving a URL (with Redis)

1. Client sends `GET /a3f9bc`.
2. URLcontroller.getURLwR() calls URLService.findbyurlwr("a3f9bc").
3. Service checks Redis: `redisTemplate.opsForValue().get("a3f9bc")`.
4. **First visit (Cache MISS):**
   - Redis returns null.
   - Queries MongoDB: `repo.findBySurl("a3f9bc")` — gets original URL.
   - Stores in Redis with TTL 1 hour.
   - Logs `CACHE MISS | Total=1 | Hits=0 | Misses=1 | MongoQueries=1`.
   - Returns HTTP `302 Found` with `Location` header.
5. **Second visit (Cache HIT):**
   - Redis returns the URL directly.
   - Logs `CACHE HIT | Total=2 | Hits=1 | Misses=1 | MongoQueries=1`.
   - Returns HTTP `302 Found` — no MongoDB query made.

---

### API Endpoints

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/` | `https://long-url.com` (plain text) | `"abc123"` | Shorten a URL (auto-generate code) |
| `GET` | `/{url}` | — | `302 Location: <original>` | Redirect using Redis cache |
| `GET` | `/db/{url}` | — | `302 Location: <original>` | Redirect using DB only (no Redis) |
| `POST` | `/{alias}` | `https://long-url.com` (plain text) | `"myalias"` | Shorten with custom alias |

**Example Requests (curl):**

```bash
# Shorten a URL
curl -X POST http://localhost:8082/ -H "Content-Type: text/plain" -d "https://www.google.com"
# Response: "a3f9bc"

# Redirect using Redis
curl -L http://localhost:8082/a3f9bc

# Redirect using DB only
curl -L http://localhost:8082/db/a3f9bc

# Custom alias
curl -X POST http://localhost:8082/google -H "Content-Type: text/plain" -d "https://www.google.com"
# Response: "google"
```

---

### Redis Caching Logic (Cache-Aside Pattern)

The **Cache-Aside (Lazy Loading)** pattern:

```
Request comes in for shortCode X
        |
        v
  Check Redis for X
        |
   +----+----+
   |         |
Found      Not Found
(HIT)      (MISS)
   |         |
   |         v
   |    Query MongoDB for X
   |         |
   |         v
   |    Store X -> URL in Redis (TTL = 1 hour)
   |         |
   +----+----+
        |
        v
   Return original URL as 302 redirect
```

**Key Redis operations:**
- `redisTemplate.opsForValue().get(key)` — O(1) lookup
- `redisTemplate.opsForValue().set(key, value, 1, TimeUnit.HOURS)` — store with TTL

**Why Redis instead of a simple HashMap?**
- Redis survives app restarts (persistence optional).
- Redis is shared across multiple app instances (horizontal scaling).
- Redis TTL automatically evicts stale entries.

---

### Logging and Metrics

Every request through `findbyurlwr` logs:

```
INFO  CACHE HIT  | Total=5 | Hits=4 | Misses=1 | MongoQueries=1
INFO  CACHE MISS | Total=2 | Hits=0 | Misses=2 | MongoQueries=2
WARN  URL NOT FOUND | shortCode=xyz999
```

Log file: `logs/urlshortener.log`

---

### Configuration

| Property | Value | Purpose |
|---|---|---|
| `server.port` | `8082` | App HTTP port |
| `spring.mongodb.uri` | `mongodb://localhost:27017/urlWthR` | MongoDB connection |
| `spring.data.redis.host` | `localhost` | Redis host |
| `spring.data.redis.port` | `6379` | Redis port |
| `logging.file.name` | `logs/urlshortener.log` | Log file path |

---

### Running Locally

**Prerequisites:** Java 17, Maven, MongoDB running on 27017, Redis running on 6379.

```bash
cd URLWthRedis
./mvnw spring-boot:run
```

---

### Running with Docker

```bash
cd URLWthRedis
docker build -t url-shortener .
docker run -p 8082:8082 url-shortener
```

---

## Project 2 — Consistent Hashing (consistent-hashing)

### Objective

Demonstrate **Consistent Hashing** as a distributed data partitioning strategy:
- Student records are distributed across **3 MongoDB nodes** (simulating separate database servers).
- The node responsible for storing/retrieving a student is determined by hashing the `student.id`.
- Adding or removing nodes causes **minimal data migration** (only affected keys move), unlike simple modulo hashing.

---

### Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2.0 |
| Database | MongoDB x 3 nodes (ports 27017, 27018, 27019) |
| Hashing | SHA-256 (java.security.MessageDigest) |
| Data Structure | TreeMap<Long, StorageNode> (the hash ring) |
| Build Tool | Maven |
| Container | Docker Compose (4 MongoDB containers) |

---

### Architecture

```
Student ID  --SHA-256-->  Hash Value  --TreeMap.ceilingEntry()-->  StorageNode
                                                                        |
                                                                    MongoTemplate
                                                                        |
                                                               MongoDB Instance (27017/27018/27019)
```

3 MongoDB nodes are pre-configured:
- `Node-1` — `mongodb://localhost:27017/studentdb`
- `Node-2` — `mongodb://localhost:27018/studentdb`
- `Node-3` — `mongodb://localhost:27019/studentdb`

---

### Project Structure

```
consistent-hashing/
├── src/main/java/com/example/consistenthashing/
│   ├── controller/
│   │   └── StudentController.java      # REST endpoints
│   ├── service/
│   │   ├── HashRing.java               # The consistent hash ring implementation
│   │   └── StudentService.java         # Node management + CRUD operations
│   └── model/
│       ├── Student.java                # Student data model
│       └── StorageNode.java            # Represents a MongoDB node (name + URI)
├── src/main/resources/
│   └── application.properties          # App name, port
├── ConsistentHashingApplication.java   # Spring Boot entry point
├── docker-compose.yml                  # 4 MongoDB containers
└── pom.xml                             # Maven dependencies
```

---

### Key Files Explained

#### StorageNode.java

Represents one MongoDB server:

```java
public class StorageNode {
    private String name;   // e.g. "Node-1"
    private String uri;    // e.g. "mongodb://localhost:27017/studentdb"
}
```

---

#### Student.java

The data model stored in MongoDB:

```java
public class Student {
    private String id;         // Used as the hash key
    private String name;
    private String department;
    private int age;
}
```

`id` is the critical field — it determines which node the student is stored on.

---

#### HashRing.java — The Core Algorithm

```java
@Component
public class HashRing {
    private final TreeMap<Long, StorageNode> ring = new TreeMap<>();
    private static final int VIRTUAL_NODES = 10;
    ...
}
```

**addNode(StorageNode node)**
- For each of 10 virtual node slots, computes hash of `"NodeName-i"` and places (hash -> node) in TreeMap.
- 10 virtual nodes per physical node — better distribution.

**removeNode(StorageNode node)**
- Removes all 10 virtual node entries for that node from the TreeMap.

**getNode(String key)**
- Computes hash of the key.
- Calls `ring.ceilingEntry(hash)` — finds the first node clockwise on the ring.
- If no entry found, wraps around using `ring.firstEntry()`.
- Returns the StorageNode.

**hash(String value)**

```java
MessageDigest md = MessageDigest.getInstance("SHA-256");
byte[] bytes = md.digest(value.getBytes());
return ByteBuffer.wrap(bytes).getLong() & Long.MAX_VALUE;
```

- SHA-256 gives a deterministic, well-distributed hash.
- `& Long.MAX_VALUE` ensures a positive 63-bit long (avoids negative keys in TreeMap).

---

#### StudentService.java — Node Management and Data Operations

**Constructor — Pre-loads 3 nodes:**

```java
public StudentService(HashRing hashRing) {
    this.hashRing = hashRing;
    addInitialNode(new StorageNode("Node-1", "mongodb://localhost:27017/studentdb"));
    addInitialNode(new StorageNode("Node-2", "mongodb://localhost:27018/studentdb"));
    addInitialNode(new StorageNode("Node-3", "mongodb://localhost:27019/studentdb"));
}
```

Each node gets its own MongoTemplate (direct MongoDB connection, bypassing Spring Data auto-config).

**save(Student student)**
- Calls `hashRing.getNode(student.getId())` — finds responsible node.
- Gets the MongoTemplate for that node.
- Calls `template.save(student)` — stores directly in that MongoDB instance.
- Returns `"Student stored in Node-X"`.

**find(String id)**
- Calls `hashRing.getNode(id)` — same deterministic mapping as save.
- Fetches from the correct MongoDB node.

**addNode(StorageNode node)**
- Creates a MongoTemplate for the new node.
- Adds the node to the ring (10 new virtual entries).
- Calls `migrateRecords()` — redistributes records that should now go to the new node.

**removeNode(String nodeName)**
- Fetches all students from the departing node.
- Removes the node from the ring.
- Re-maps each student to their new correct node using the updated ring.
- Saves them to their new nodes, drops the old node's collection.

**migrateRecords()**
- Iterates all nodes and all their students.
- For each student, re-computes the correct node.
- If the student is on the wrong node, moves them.

**distribution()**
- Returns `Map<String, Long>` showing student count per node.
- Useful for verifying distribution balance.

---

### How Consistent Hashing Works

#### The Ring Concept

Imagine all possible hash values arranged in a circle (0 to MAX_LONG, wraps back to 0). Nodes are placed at positions on this ring. When storing a key, it is hashed to a position on the ring, and the **next node clockwise** is chosen.

When you add a new node, **only the keys between the new node's predecessor and the new node** need to migrate. All other keys stay on their original nodes. This is the main advantage over modulo hashing.

#### Modulo Hashing vs Consistent Hashing

| Property | Modulo Hashing `hash(key) % N` | Consistent Hashing |
|---|---|---|
| Add a node | Almost all keys reassigned | Only ~1/N keys move |
| Remove a node | Almost all keys reassigned | Only keys on removed node move |
| Distribution | Uniform | Good with virtual nodes |
| Complexity | O(1) | O(log N) — TreeMap lookup |

---

### Virtual Nodes

Each physical node creates **10 virtual nodes** on the ring:

```
hash("Node-1-0"), hash("Node-1-1"), ..., hash("Node-1-9")
hash("Node-2-0"), ..., hash("Node-2-9")
hash("Node-3-0"), ..., hash("Node-3-9")
```

**Why virtual nodes?**
- Without virtual nodes, node placement may be uneven on the ring, causing one node to handle far more keys.
- Virtual nodes spread each physical node across 10 ring positions — better load balance.
- With 10 virtual nodes per physical node and 3 physical nodes = 30 ring entries total.

---

### API Endpoints

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/students` | `{ id, name, department, age }` | `"Student stored in Node-X"` | Save student to consistent-hashed node |
| `GET` | `/students/{id}` | — | `{ id, name, department, age }` | Fetch student from correct node |
| `POST` | `/students/nodes` | `{ name, uri }` | `"Node-X added successfully"` | Add a new MongoDB node |
| `DELETE` | `/students/nodes/{name}` | — | `"Node-X removed successfully"` | Remove a node (migrates data) |
| `GET` | `/students/distribution` | — | `{ "Node-1": 4, "Node-2": 3, "Node-3": 3 }` | Check record count per node |

**Example Requests (curl):**

```bash
# Save a student
curl -X POST http://localhost:8082/students \
  -H "Content-Type: application/json" \
  -d '{"id":"S001","name":"Alice","department":"CSE","age":21}'
# Response: "Student stored in Node-2"

# Fetch a student
curl http://localhost:8082/students/S001

# Check distribution
curl http://localhost:8082/students/distribution
# Response: {"Node-1":3,"Node-2":4,"Node-3":3}

# Add a 4th node
curl -X POST http://localhost:8082/students/nodes \
  -H "Content-Type: application/json" \
  -d '{"name":"Node-4","uri":"mongodb://localhost:27020/studentdb"}'

# Remove a node
curl -X DELETE http://localhost:8082/students/nodes/Node-3
```

---

### Node Operations — Add and Remove

#### Adding a Node (Step by Step)

1. `POST /students/nodes` with `{ "name": "Node-4", "uri": "mongodb://localhost:27020/studentdb" }`.
2. `StudentService.addNode()` creates a MongoTemplate for Node-4.
3. Node-4 is added to the ring (30 new virtual node entries in TreeMap).
4. `migrateRecords()` scans all existing students on all existing nodes.
5. For each student, re-computes correct node using the updated ring.
6. Students that now hash to Node-4 are moved there, and removed from their old node.

#### Removing a Node (Step by Step)

1. `DELETE /students/nodes/Node-3`.
2. `StudentService.removeNode("Node-3")`:
   - Fetches all students from Node-3's MongoDB.
   - Removes Node-3 from the ring (30 virtual nodes deleted from TreeMap).
   - For each student formerly on Node-3, calls `hashRing.getNode(student.getId())` to find new node.
   - Saves student to new node and drops Node-3's collection.

---

### Running with Docker Compose

Start the MongoDB cluster:

```bash
cd URLWthRedis/consistent-hashing
docker-compose up -d
```

This spins up:
- `mongo1` on port `27017`
- `mongo2` on port `27018`
- `mongo3` on port `27019`
- `mongo4` on port `27020` (available for adding as Node-4)

Then run the Spring Boot app:

```bash
./mvnw spring-boot:run
```

App starts on `http://localhost:8082`.

---

## Common Viva/Demo Questions and Answers

### URL Shortener Questions

**Q: Why use Redis when MongoDB is already fast?**

Redis is an in-memory store — reads are sub-millisecond. MongoDB requires disk I/O for reads not in its buffer cache. For a URL shortener, the same short URL can be accessed thousands of times per second. Redis eliminates repeated DB reads for hot URLs. MongoDB is still used for durability — data persists after restart while Redis is typically volatile.

**Q: What happens if Redis goes down?**

The `findbyurlwr` method would receive a `null` from Redis (connection failure throws an exception in production). In this implementation, the exception would propagate up. In a production-grade system, you would catch `RedisConnectionFailureException` and fall through to MongoDB. The `/db/{url}` endpoint always bypasses Redis completely and always works.

**Q: What is the TTL of Redis entries? Why 1 hour?**

1 hour (`TimeUnit.HOURS`). This prevents stale cache buildup. If the original URL changes or is deleted from MongoDB, the cache auto-expires within 1 hour. TTL can be tuned based on URL change frequency — shorter TTL for URLs that may change frequently.

**Q: How is the short code generated?**

`UUID.randomUUID().toString().substring(0, 6)` — takes first 6 characters of a UUID v4 (random). UUID is a 128-bit random number represented as hex with dashes. The first 6 chars give ~16^6 = ~16.7 million possible codes. A collision-detection loop ensures uniqueness by checking MongoDB before finalizing the code.

**Q: What is AtomicLong and why is it used?**

`AtomicLong` is a thread-safe counter from `java.util.concurrent.atomic`. Spring Boot's embedded Tomcat handles multiple HTTP requests concurrently on different threads. If a regular `long` were used, two threads could read and increment simultaneously causing a race condition (lost updates). `AtomicLong.incrementAndGet()` is an atomic CPU instruction — guaranteed no race conditions even under concurrent access.

**Q: What does @Document do on URLSh?**

It marks the class as a MongoDB document (equivalent to declaring a table in SQL). Spring Data MongoDB maps instances of this class to documents in a MongoDB collection. The collection name defaults to the camel-cased class name.

**Q: What is MongoRepository and why extend it?**

`MongoRepository<T, ID>` is a Spring Data interface that provides CRUD operations automatically at runtime through dynamic JDK proxying. `findBySurl(String surl)` is a **derived query method** — Spring Data reads the method name, parses it, and builds the MongoDB query `{ surl: <value> }` automatically using reflection. No query implementation code is needed.

**Q: What is the Cache-Aside (Lazy Loading) pattern?**

The application code is responsible for loading data into the cache when a miss occurs. The cache does not automatically populate itself. On a cache miss, the application queries the database and manually writes the result to cache. This is contrasted with:
- Write-Through: write to both cache and DB simultaneously on every write.
- Read-Through: cache is responsible for loading from DB on miss (transparent to app).

Cache-Aside is the simplest pattern and gives the app full control.

**Q: What is HTTP 302 and why is it used for redirects?**

HTTP 302 means "Found" (temporary redirect). The browser receives `302` with a `Location` header pointing to the original URL, then automatically navigates there. The URL shortener returns `302` so browsers redirect transparently. The `ResponseEntity` is built with `HttpHeaders` containing the `Location` header and `HttpStatus.FOUND`.

**Q: What does start.sh do in the Docker container?**

It starts three services in sequence:
1. MongoDB as a forked daemon (`mongod --fork`).
2. Redis as a daemon (`redis-server --daemonize yes`).
3. The Java Spring Boot app in the foreground (`java -jar`).

The container stays alive as long as the Java process runs.

**Q: Why is there a /db/{url} endpoint alongside /{url}?**

The `/db/{url}` endpoint is for comparison and testing. It bypasses Redis entirely and always queries MongoDB. This lets you observe the latency difference between cache-served and DB-served responses, and confirms correct behavior when Redis is unavailable.

**Q: What is StringRedisTemplate vs RedisTemplate?**

`StringRedisTemplate` is a pre-configured `RedisTemplate<String, String>`. It serializes keys and values as plain strings using `StringRedisSerializer`. This project stores short codes (keys) and original URLs (values) — both strings. Using `StringRedisTemplate` is simpler and avoids Java object serialization overhead that generic `RedisTemplate` would use.

---

### Consistent Hashing Questions

**Q: What is Consistent Hashing?**

A technique for distributing keys across nodes such that when nodes are added or removed, only K/N keys need to be remapped (where K = total keys, N = number of nodes). In naive modulo hashing (`hash(key) % N`), changing N causes almost all keys to remap. Consistent hashing places both nodes and keys on a conceptual circular hash ring; a key maps to the first node encountered clockwise from its hash position.

**Q: What is a virtual node and why use 10?**

A virtual node is a phantom entry on the ring that maps back to a physical node. Each physical node creates 10 virtual nodes by hashing `"Node-X-0"` through `"Node-X-9"`. This ensures each physical node occupies multiple non-contiguous positions on the ring, improving load balance. Without virtual nodes, random node placement could cluster nodes in one ring region, making one node handle far more keys than others. With 3 physical nodes x 10 virtual nodes = 30 ring entries, distribution is significantly smoother.

**Q: Why TreeMap instead of HashMap for the ring?**

`TreeMap` keeps keys (Long hash values) sorted in ascending order. The operation `ceilingEntry(hash)` finds the smallest key >= `hash` in O(log N) time — this represents the "next node clockwise" on the ring. A `HashMap` is unordered — it cannot support range queries or sorted traversal, making the clockwise-lookup operation impossible without sorting on every request.

**Q: What is ceilingEntry and why is wrap-around needed?**

`TreeMap.ceilingEntry(key)` returns the entry with the smallest key >= the given key. If the student's hash value is greater than all node positions in the TreeMap (i.e., it falls "past the last node going clockwise"), `ceilingEntry` returns `null`. The code then uses `ring.firstEntry()` to wrap around to the node at the smallest hash position — simulating the circular nature of the consistent hash ring.

**Q: Why SHA-256 for hashing instead of Java's hashCode()?**

Java's `String.hashCode()` produces a 32-bit integer with known distribution issues for certain input patterns and is not designed for consistent hashing. SHA-256 produces a 256-bit cryptographically uniform hash. The code takes the first 8 bytes as a `long` and forces it positive with `& Long.MAX_VALUE`, giving a well-distributed 63-bit address space. SHA-256 is deterministic — same input always gives same output — and collision-resistant.

**Q: How does data migration work when a node is added?**

After inserting the new node's virtual entries into the TreeMap, `migrateRecords()` runs. It scans every student on every existing node and re-evaluates their correct node using the updated ring. If a student's computed node has changed (they now map to the new node), they are moved. Data integrity is maintained throughout.

**Q: What happens to data when a node is removed?**

All students on the removed node are fetched first (before modifying the ring). The node is removed from the ring. Each student is re-mapped using the updated ring — they now route to one of the surviving nodes. Students are saved to their new nodes. The old node's MongoDB collection is dropped.

**Q: What is MongoTemplate and why not use MongoRepository?**

`MongoTemplate` is the lower-level Spring Data MongoDB API that allows specifying which MongoDB database/server to connect to at runtime via `SimpleMongoClientDatabaseFactory`. `MongoRepository` relies on Spring's auto-configuration which sets up a single MongoDB connection from `application.properties` at startup. Since consistent hashing needs dynamic connections to 3 different MongoDB URIs (ports 27017, 27018, 27019), `MongoTemplate` is used — one template instance per node, stored in a `Map<String, MongoTemplate>`.

**Q: What is the difference between the URL shortener's MongoDB and consistent hashing's MongoDB?**

The URL shortener uses a single MongoDB instance on port 27017 with Spring Data MongoDB auto-configuration via `application.properties`. Consistent hashing uses three separate MongoDB instances (27017, 27018, 27019), each with its own `MongoTemplate` created manually in `StudentService`. They both use the same underlying Spring Data library but at different abstraction levels.

**Q: What is the distribution() endpoint for?**

It reports how many student records are stored on each node: `{ "Node-1": 4, "Node-2": 3, "Node-3": 3 }`. This demonstrates that consistent hashing achieves roughly uniform distribution. After adding or removing nodes, call this endpoint again to verify that only a minimal subset of records moved.

**Q: Can two students go to the same node? Can a node be empty?**

Yes to both. The distribution depends on the SHA-256 hash of student IDs relative to node positions. It is probabilistic — most students spread roughly evenly, but with a small dataset some nodes may get fewer or even zero records. Virtual nodes help reduce this imbalance for larger datasets.

**Q: What is the time complexity of looking up a node?**

O(log N) where N = total virtual nodes in the ring (physical nodes x VIRTUAL_NODES = 3 x 10 = 30). `TreeMap.ceilingEntry()` performs a binary search on a Red-Black tree (balanced BST), giving guaranteed O(log N) worst-case performance.

**Q: What happens if the hash ring is empty and getNode is called?**

The `getNode` method explicitly checks `if (ring.isEmpty())` and throws `IllegalStateException("No storage nodes available")`. This guards against the edge case of calling getNode before any nodes are added to the ring.

---

## Full Repository File Tree

```
as5/
├── README.md                                     <- This file
├── EXP5-Consistent Hashing.pdf                   <- Lab experiment sheet
├── .github/
│   └── modernize/
│       └── java-upgrade/
│           └── hooks/
│               └── scripts/
│                   ├── recordToolUse.sh           <- GitHub Modernize tool hook (bash)
│                   └── recordToolUse.ps1          <- GitHub Modernize tool hook (PowerShell)
├── .idea/                                         <- IntelliJ IDEA project files
│   ├── as5.iml
│   ├── misc.xml
│   ├── modules.xml
│   ├── vcs.xml
│   └── workspace.xml
└── URLWthRedis/                                   <- Main project directory
    ├── pom.xml                                    <- Maven build (Spring Boot 4.1.0, Java 17)
    ├── Dockerfile                                 <- Monolithic Docker container
    ├── start.sh                                   <- Container entrypoint
    ├── mvnw / mvnw.cmd                            <- Maven wrapper scripts
    ├── HELP.md                                    <- Spring Initializr help page
    ├── .gitignore                                 <- Ignores: target/, .idea/, *.iml, .vscode/
    ├── logs/                                      <- Runtime log output
    ├── src/
    │   ├── main/
    │   │   ├── java/org/example/url/
    │   │   │   ├── UrlApplication.java            <- @SpringBootApplication entry point
    │   │   │   ├── controller/
    │   │   │   │   └── URLcontroller.java         <- REST: POST /, GET /{url}, GET /db/{url}, POST /{alias}
    │   │   │   ├── service/
    │   │   │   │   └── URLService.java            <- Business logic, Redis cache-aside, metrics
    │   │   │   ├── model/
    │   │   │   │   └── URLSh.java                 <- @Document: { id, url, surl }
    │   │   │   └── Repo/
    │   │   │       └── URLrepo.java               <- MongoRepository with findBySurl()
    │   │   └── resources/
    │   │       └── application.properties         <- Port 8082, MongoDB URI, Redis, log file
    │   └── test/
    ├── target/                                    <- Maven build output (JAR)
    └── consistent-hashing/                        <- Sub-project: Consistent Hashing
        ├── ConsistentHashingApplication.java      <- @SpringBootApplication entry point
        ├── pom.xml                                <- Maven build (Spring Boot 3.2.0, Java 21)
        ├── docker-compose.yml                     <- 4 MongoDB containers (ports 27017-27020)
        ├── mvnw / mvnw.cmd                        <- Maven wrapper
        ├── src/
        │   └── main/
        │       ├── java/com/example/consistenthashing/
        │       │   ├── controller/
        │       │   │   └── StudentController.java <- REST: /students CRUD + /nodes + /distribution
        │       │   ├── service/
        │       │   │   ├── HashRing.java          <- TreeMap ring, SHA-256 hashing, 10 virtual nodes
        │       │   │   └── StudentService.java    <- Node management, save/find, migrate, distribution
        │       │   └── model/
        │       │       ├── Student.java           <- { id, name, department, age }
        │       │       └── StorageNode.java       <- { name, uri }
        │       └── resources/
        │           └── application.properties     <- Port 8082, app name
        └── target/                                <- Maven build output
```

---

> **Note on .github/modernize scripts:** These are automation hooks from a GitHub Modernize / Java Upgrade tool integration. The `recordToolUse.sh` (bash) and `recordToolUse.ps1` (PowerShell) scripts intercept tool calls with `tool_name = run_in_terminal` or `appmod-*` and log them as JSONL for the IDE extension to process. They are not part of the core assignment functionality.

---

*Last updated: August 2026 | System Design Lab — Assignment 5*
