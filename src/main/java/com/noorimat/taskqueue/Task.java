package com.noorimat.taskqueue;

import java.time.Instant;
import java.util.UUID;

public class Task {
    private final String id;
    private final String payload;
    private final int maxRetries;
    private int attemptCount;
    private TaskStatus status;
    private final long createdAt;

    public Task(String payload) {
        this(UUID.randomUUID().toString(), payload, 3);
    }

    public Task(String id, String payload, int maxRetries) {
        this.id = id;
        this.payload = payload;
        this.maxRetries = maxRetries;
        this.attemptCount = 0;
        this.status = TaskStatus.PENDING;
        this.createdAt = Instant.now().toEpochMilli();
    }

    public Task(String id, String payload, int maxRetries, int attemptCount, TaskStatus status, long createdAt) {
        this.id = id;
        this.payload = payload;
        this.maxRetries = maxRetries;
        this.attemptCount = attemptCount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public void incrementAttempt() {
        this.attemptCount++;
    }

    public boolean canRetry() {
        return attemptCount < maxRetries;
    }

    public String getId() { return id; }
    public String getPayload() { return payload; }
    public int getMaxRetries() { return maxRetries; }
    public int getAttemptCount() { return attemptCount; }
    public TaskStatus getStatus() { return status; }
    public long getCreatedAt() { return createdAt; }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "Task{id='" + id + "', status=" + status + ", attempts=" + attemptCount + "/" + maxRetries + "}";
    }
}