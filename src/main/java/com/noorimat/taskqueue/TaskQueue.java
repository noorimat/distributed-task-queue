package com.noorimat.taskqueue;

import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskQueue {
    private static final Logger logger = LoggerFactory.getLogger(TaskQueue.class);
    private final ConcurrentLinkedQueue<Task> queue;
    private final ReentrantLock lock;

    public TaskQueue() {
        this.queue = new ConcurrentLinkedQueue<>();
        this.lock = new ReentrantLock();
    }

    public boolean enqueue(Task task) {
        lock.lock();
        try {
            boolean added = queue.offer(task);
            if (added) {
                logger.info("Enqueued task: {}", task.getId());
            }
            return added;
        } finally {
            lock.unlock();
        }
    }

    public Optional<Task> dequeue() {
        lock.lock();
        try {
            Task task = queue.poll();
            if (task != null) {
                logger.info("Dequeued task: {}", task.getId());
            }
            return Optional.ofNullable(task);
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
