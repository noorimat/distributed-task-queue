package com.noorimat.taskqueue;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class MetricsServer {
    private static final Logger logger = LoggerFactory.getLogger(MetricsServer.class);
    private final HttpServer server;
    private final Gson gson = new Gson();

    private final AtomicLong tasksSubmitted = new AtomicLong(0);
    private final AtomicLong tasksCompleted = new AtomicLong(0);
    private final AtomicLong tasksFailed = new AtomicLong(0);

    private final TaskQueue queue;
    private final TaskRepository repository;

    public MetricsServer(int port, TaskQueue queue, TaskRepository repository) throws IOException {
        this.queue = queue;
        this.repository = repository;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/metrics", exchange -> {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("queue_size", queue.size());
            metrics.put("tasks_submitted_total", tasksSubmitted.get());
            metrics.put("tasks_completed_total", tasksCompleted.get());
            metrics.put("tasks_failed_total", tasksFailed.get());

            try {
                int pendingCount = repository.loadPendingTasks().size();
                metrics.put("tasks_pending", pendingCount);
            } catch (SQLException e) {
                metrics.put("tasks_pending", "error");
            }

            metrics.put("uptime_seconds", getUptimeSeconds());
            metrics.put("status", "healthy");

            String response = gson.toJson(metrics);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes().length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });

        server.createContext("/health", exchange -> {
            String response = "{\"status\":\"UP\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes().length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });
    }

    private long startTime = System.currentTimeMillis();

    private long getUptimeSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000;
    }

    public void start() {
        server.start();
        logger.info("Metrics server started on port {}", server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
        logger.info("Metrics server stopped");
    }

    public void incrementSubmitted() {
        tasksSubmitted.incrementAndGet();
    }

    public void incrementCompleted() {
        tasksCompleted.incrementAndGet();
    }

    public void incrementFailed() {
        tasksFailed.incrementAndGet();
    }
}
