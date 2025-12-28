package com.noorimat.taskqueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TaskExecutor {
    private static final Logger logger = LoggerFactory.getLogger(TaskExecutor.class);

    private final TaskQueue queue;
    private final TaskRepository repository;
    private final ExecutorService executorService;
    private final AtomicBoolean running;
    private final int workerCount;
    private MetricsServer metricsServer;  // ADD THIS

    public TaskExecutor(TaskQueue queue, TaskRepository repository, int workerCount) {
        this.queue = queue;
        this.repository = repository;
        this.workerCount = workerCount;
        this.executorService = Executors.newFixedThreadPool(workerCount);
        this.running = new AtomicBoolean(false);
    }

    // ADD THIS METHOD
    public void setMetricsServer(MetricsServer metricsServer) {
        this.metricsServer = metricsServer;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting TaskExecutor with {} workers", workerCount);

            for (int i = 0; i < workerCount; i++) {
                final int workerId = i + 1;
                executorService.submit(() -> workerLoop(workerId));
            }
        }
    }

    private void workerLoop(int workerId) {
        logger.info("Worker-{} started", workerId);

        while (running.get()) {
            try {
                queue.dequeue().ifPresentOrElse(
                        task -> processTask(task, workerId),
                        () -> {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                );
            } catch (Exception e) {
                logger.error("Worker-{} encountered error", workerId, e);
            }
        }

        logger.info("Worker-{} stopped", workerId);
    }

    private void processTask(Task task, int workerId) {
        try {
            logger.info("Worker-{} processing: {}", workerId, task.getId());

            task.setStatus(TaskStatus.RUNNING);
            task.incrementAttempt();
            repository.save(task);

            executeTask(task);

            task.setStatus(TaskStatus.COMPLETED);
            repository.markCompleted(task.getId());

            if (metricsServer != null) {  // ADD THIS
                metricsServer.incrementCompleted();
            }

            logger.info("Worker-{} completed: {}", workerId, task.getId());

        } catch (Exception e) {
            logger.error("Worker-{} failed task: {}", workerId, task.getId(), e);
            if (metricsServer != null) {  // ADD THIS
                metricsServer.incrementFailed();
            }
            handleFailedTask(task);
        }
    }

    private void executeTask(Task task) throws InterruptedException {
        long duration = 500 + (long)(Math.random() * 1000);
        Thread.sleep(duration);

        if (Math.random() < 0.1) {
            throw new RuntimeException("Random task failure for testing");
        }
    }

    private void handleFailedTask(Task task) {
        try {
            if (task.canRetry()) {
                logger.info("Retrying task: {} (attempt {}/{})",
                        task.getId(), task.getAttemptCount(), task.getMaxRetries());
                task.setStatus(TaskStatus.PENDING);
                repository.save(task);
                queue.enqueue(task);
            } else {
                logger.warn("Task failed permanently: {}", task.getId());
                task.setStatus(TaskStatus.FAILED);
                repository.markFailed(task.getId());
            }
        } catch (SQLException e) {
            logger.error("Error handling failed task: {}", task.getId(), e);
        }
    }

    public void shutdown() {
        logger.info("Shutting down TaskExecutor...");
        running.set(false);
        executorService.shutdown();

        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("Executor did not terminate in time, forcing shutdown");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Shutdown interrupted", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("TaskExecutor shut down");
    }

    public boolean isRunning() {
        return running.get();
    }
}
