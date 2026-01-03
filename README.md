# Distributed Task Queue

A production-grade distributed task execution system with gRPC communication, persistent storage, concurrent processing, and real-time observability. Built to demonstrate advanced software engineering concepts for distributed systems.

## ✨ Key Features

- **🌐 Distributed Architecture** - gRPC-based client-server communication with Protocol Buffers
- **⚡ Concurrent Execution** - Multi-threaded worker pool processes tasks in parallel
- **💾 Persistent Storage** - SQLite backend with crash recovery and transaction safety
- **🔄 Automatic Retries** - Failed tasks retry up to 3 times with exponential backoff potential
- **📊 Real-time Metrics** - HTTP endpoints expose live queue statistics and health status
- **🛡️ Thread-Safe Operations** - Lock-based synchronization prevents race conditions
- **🎯 Graceful Shutdown** - Workers complete in-flight tasks before termination
- **📝 Production Logging** - Structured logging with SLF4J for monitoring and debugging

## 🚀 Quick Start

### Prerequisites
- Java 17 or higher
- Gradle 7.x or higher

### Start the Server
```bash
./gradlew run -PmainClass=com.noorimat.taskqueue.TaskServiceServer
```

Server starts on:
- **gRPC**: `localhost:50051`
- **Metrics**: `http://localhost:9090/metrics`
- **Health**: `http://localhost:9090/health`

### Run the Client (in a separate terminal)
```bash
./gradlew run -PmainClass=com.noorimat.taskqueue.TaskClient
```

The client submits 5 tasks via gRPC and displays their status.

### View Live Metrics

Open in browser: `http://localhost:9090/metrics`
```json
{
  "tasks_submitted_total": 5,
  "tasks_completed_total": 5,
  "tasks_failed_total": 0,
  "queue_size": 0,
  "tasks_pending": 0,
  "uptime_seconds": 142,
  "status": "healthy"
}
```

## 🏗️ Architecture
```
┌──────────────┐
│ TaskClient   │ (Remote gRPC Client)
└──────┬───────┘
       │ gRPC over network (port 50051)
       │ Protocol Buffers serialization
       ▼
┌─────────────────────────────────┐
│ TaskServiceServer               │
│  ┌───────────────────────────┐  │
│  │ gRPC Service Layer        │  │
│  └───────────┬───────────────┘  │
│              ▼                   │
│  ┌───────────────────────────┐  │
│  │ TaskQueue (Thread-safe)   │  │
│  │ ConcurrentLinkedQueue     │  │
│  └───────────┬───────────────┘  │
│              ▼                   │
│  ┌───────────────────────────┐  │
│  │ TaskExecutor              │  │
│  │  ├─ Worker-1 (Thread)     │  │
│  │  ├─ Worker-2 (Thread)     │  │
│  │  └─ Worker-3 (Thread)     │  │
│  └───────────┬───────────────┘  │
│              ▼                   │
│  ┌───────────────────────────┐  │
│  │ TaskRepository (SQLite)   │  │
│  └───────────────────────────┘  │
│                                  │
│  ┌───────────────────────────┐  │
│  │ MetricsServer (HTTP)      │  │
│  │ Port 9090                 │  │
│  └───────────────────────────┘  │
└──────────────────────────────────┘
         │
         ▼
    ┌──────────┐
    │ tasks.db │ (SQLite Database)
    └──────────┘
```

## 📦 Core Components

### Task
Immutable work unit with:
- Unique ID (UUID)
- Payload (task description)
- Status tracking (PENDING → RUNNING → COMPLETED/FAILED)
- Retry metadata (attempt count, max retries)
- Timestamp (creation time)

### TaskQueue
Thread-safe FIFO queue using `ConcurrentLinkedQueue` with `ReentrantLock`:
- Non-blocking enqueue/dequeue operations
- Lock-based synchronization for safety
- O(1) size checking

### TaskExecutor
Manages worker thread pool (`ExecutorService`) for concurrent processing:
- Configurable worker count (default: 3)
- Automatic retry logic with attempt tracking
- Graceful shutdown with timeout
- Metrics integration

### TaskRepository
SQLite persistence layer with:
- ACID transaction support
- Indexed queries for performance (`CREATE INDEX idx_status`)
- Prepared statements (SQL injection protection)
- Crash recovery on startup

### TaskServiceServer (gRPC)
Network interface supporting:
- `SubmitTask` - Submit tasks remotely
- `GetTaskStatus` - Query task status by ID
- `ListTasks` - List pending tasks

### MetricsServer (HTTP)
Observability endpoints:
- `/metrics` - Real-time queue statistics
- `/health` - Service health check

## 🔧 Technical Deep Dive

### gRPC Communication

**Protocol Buffer Schema** (`task_service.proto`):
```protobuf
service TaskService {
  rpc SubmitTask(SubmitTaskRequest) returns (SubmitTaskResponse);
  rpc GetTaskStatus(GetTaskStatusRequest) returns (GetTaskStatusResponse);
  rpc ListTasks(ListTasksRequest) returns (ListTasksResponse);
}
```

**Client Usage**:
```java
TaskClient client = new TaskClient("localhost", 50051);
String taskId = client.submitTask("Process payment for order #1001");
client.getTaskStatus(taskId);
```

### Thread Safety

**Lock-based Synchronization**:
```java
private final ReentrantLock lock = new ReentrantLock();

public boolean enqueue(Task task) {
    lock.lock();
    try {
        return queue.offer(task);
    } finally {
        lock.unlock();
    }
}
```

### Crash Recovery

Tasks persist to SQLite before execution:
```java
// On startup
var pendingTasks = repository.loadPendingTasks();
pendingTasks.forEach(queue::enqueue);

// Before execution
repository.save(task);
queue.enqueue(task);
```

**Database Schema**:
```sql
CREATE TABLE tasks (
    id TEXT PRIMARY KEY,
    payload TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    attempt_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3
);

CREATE INDEX idx_status ON tasks(status);
```

### Automatic Retry Logic

**Exponential Backoff Pattern**:
```java
if (task.canRetry()) {
    task.setStatus(TaskStatus.PENDING);
    task.incrementAttempt();
    repository.save(task);
    queue.enqueue(task); // Re-queue for retry
} else {
    repository.markFailed(task.getId()); // Dead letter
}
```

### Graceful Shutdown

**Connection Draining**:
```java
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    // 1. Stop accepting new tasks
    server.shutdown();
    
    // 2. Wait for workers to finish (30s timeout)
    executorService.shutdown();
    executorService.awaitTermination(30, TimeUnit.SECONDS);
    
    // 3. Persist remaining queue
    queue.forEach(repository::save);
    
    // 4. Close connections
    repository.close();
}));
```

## 📊 Demo Output

### Server Console
```
[main] INFO - gRPC server started on port 50051
[main] INFO - Metrics available at http://localhost:9090/metrics
[main] INFO - Task Queue Server is running. Press Ctrl+C to stop.

[Worker-1] INFO - Worker-1 processing: 480b7d03-c58a-4602-94b4-42206e2daa6d
[Worker-2] INFO - Worker-2 processing: 7a29edc9-cff1-46d6-80fa-2c9d1eaf8b53
[Worker-3] INFO - Worker-3 processing: 68fc07a6-9cc5-4980-8f1e-ac9834e2e7ab

[Worker-1] INFO - Worker-1 completed: 480b7d03-c58a-4602-94b4-42206e2daa6d
[Worker-2] INFO - Worker-2 completed: 7a29edc9-cff1-46d6-80fa-2c9d1eaf8b53
```

### Client Console
```
=== Task Queue Client Demo ===

Submitting 5 tasks...
Task submitted: 480b7d03... - Task submitted successfully
Task submitted: 7a29edc9... - Task submitted successfully
Task submitted: 68fc07a6... - Task submitted successfully

Checking task status...
Task 480b7d03...: status=COMPLETED, attempts=1
Task 68fc07a6...: status=COMPLETED, attempts=1

Found 0 pending tasks
```

## 🛠️ Tech Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Language | Java 17 | Modern features, records, pattern matching |
| RPC Framework | gRPC 1.60.0 | High-performance network communication |
| Serialization | Protocol Buffers 3.25 | Efficient binary serialization |
| Database | SQLite 3.44 | Embedded persistent storage |
| Concurrency | ExecutorService | Thread pool management |
| Logging | SLF4J 2.0 | Structured logging |
| Build Tool | Gradle 8.x | Dependency management, builds |
| Metrics | HTTP Server | Observability endpoints |

## 🎓 What This Demonstrates

### Distributed Systems Concepts
- Client-server architecture with network boundaries
- Protocol buffer schema design and versioning
- gRPC service implementation and streaming
- Network serialization and deserialization
- Remote procedure calls (RPC)

### Concurrent Programming
- Thread pool management with `ExecutorService`
- Thread-safe data structures (`ConcurrentLinkedQueue`)
- Lock-based synchronization (`ReentrantLock`)
- Race condition prevention
- Atomic operations (`AtomicBoolean`, `AtomicLong`)

### Database Design & Management
- ACID transaction handling
- Schema design with proper indexing
- CRUD operations with prepared statements
- Connection management and pooling
- SQL injection prevention

### Production Engineering Patterns
- Graceful shutdown with connection draining
- Automatic retry with exponential backoff
- Crash recovery and fault tolerance
- Health checks and observability
- Structured logging for debugging
- Metrics collection and exposure

### Software Engineering Best Practices
- Clean architecture and separation of concerns
- Dependency injection
- Builder pattern for complex objects
- Optional pattern for null safety
- Error handling and validation
- Resource management (try-with-resources)

## 📈 Future Enhancements

- [ ] Work stealing algorithm for dynamic load balancing
- [ ] Prometheus metrics integration
- [ ] Dead letter queue with retry policies
- [ ] Priority-based task scheduling
- [ ] Web UI dashboard with WebSockets
- [ ] Docker containerization
- [ ] Kubernetes deployment manifests
- [ ] Horizontal scaling with multiple server nodes
- [ ] Task dependencies and DAG execution
- [ ] Rate limiting and backpressure

## 📝 License

MIT License

---

**Built as a portfolio project to demonstrate distributed systems, concurrent programming, and production engineering skills.**

**Technologies**: Java 17 • gRPC • Protocol Buffers • SQLite • Gradle
