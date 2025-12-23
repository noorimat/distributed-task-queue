package com.noorimat.taskqueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TaskRepository {
    private static final Logger logger = LoggerFactory.getLogger(TaskRepository.class);
    private final Connection connection;

    public TaskRepository(String dbPath) throws SQLException {
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        initializeDatabase();
    }

    private void initializeDatabase() throws SQLException {
        String createTable = """
            CREATE TABLE IF NOT EXISTS tasks (
                id TEXT PRIMARY KEY,
                payload TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                attempt_count INTEGER DEFAULT 0,
                max_retries INTEGER DEFAULT 3
            )
        """;

        String createIndex = "CREATE INDEX IF NOT EXISTS idx_status ON tasks(status)";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
            stmt.execute(createIndex);
            logger.info("Database initialized successfully");
        }
    }

    public void save(Task task) throws SQLException {
        String sql = """
            INSERT OR REPLACE INTO tasks 
            (id, payload, status, created_at, attempt_count, max_retries)
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, task.getId());
            pstmt.setString(2, task.getPayload());
            pstmt.setString(3, task.getStatus().name());
            pstmt.setLong(4, task.getCreatedAt());
            pstmt.setInt(5, task.getAttemptCount());
            pstmt.setInt(6, task.getMaxRetries());
            pstmt.executeUpdate();

            logger.debug("Saved task to database: {}", task.getId());
        }
    }

    public List<Task> loadPendingTasks() throws SQLException {
        String sql = "SELECT * FROM tasks WHERE status = 'PENDING' ORDER BY created_at";
        List<Task> tasks = new ArrayList<>();

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Task task = new Task(
                        rs.getString("id"),
                        rs.getString("payload"),
                        rs.getInt("max_retries"),
                        rs.getInt("attempt_count"),
                        TaskStatus.valueOf(rs.getString("status")),
                        rs.getLong("created_at")
                );
                tasks.add(task);
            }
        }

        logger.info("Loaded {} pending tasks from database", tasks.size());
        return tasks;
    }

    public void markCompleted(String taskId) throws SQLException {
        String sql = "UPDATE tasks SET status = 'COMPLETED' WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, taskId);
            pstmt.executeUpdate();
        }
    }

    public void markFailed(String taskId) throws SQLException {
        String sql = "UPDATE tasks SET status = 'FAILED' WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, taskId);
            pstmt.executeUpdate();
        }
    }

    public Optional<Task> findById(String taskId) throws SQLException {
        String sql = "SELECT * FROM tasks WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, taskId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Task task = new Task(
                            rs.getString("id"),
                            rs.getString("payload"),
                            rs.getInt("max_retries"),
                            rs.getInt("attempt_count"),
                            TaskStatus.valueOf(rs.getString("status")),
                            rs.getLong("created_at")
                    );
                    return Optional.of(task);
                }
            }
        }

        return Optional.empty();
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("Database connection closed");
            }
        } catch (SQLException e) {
            logger.error("Error closing database connection", e);
        }
    }
}
