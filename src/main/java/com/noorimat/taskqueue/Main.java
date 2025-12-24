package com.noorimat.taskqueue;

import java.sql.SQLException;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== Multi-Threaded Task Queue ===\n");

        try {
            TaskRepository repo = new TaskRepository("tasks.db");
            TaskQueue queue = new TaskQueue();

            // Load pending tasks from previous runs
            var pendingTasks = repo.loadPendingTasks();
            System.out.println("Loaded " + pendingTasks.size() + " pending tasks\n");
            pendingTasks.forEach(queue::enqueue);

            // Create 10 new tasks
            System.out.println("Creating 10 new tasks...");
            for (int i = 1; i <= 10; i++) {
                Task task = new Task("Task " + i + ": Processing data batch");
                repo.save(task);
                queue.enqueue(task);
            }

            System.out.println("Queue size: " + queue.size());
            System.out.println("\nStarting 3 worker threads...\n");

            // Create executor with 3 worker threads
            TaskExecutor executor = new TaskExecutor(queue, repo, 3);
            executor.start();

            // Let it run for 15 seconds
            Thread.sleep(15000);

            // Graceful shutdown
            System.out.println("\n=== Initiating graceful shutdown ===");
            executor.shutdown();

            System.out.println("\nRemaining in queue: " + queue.size());
            System.out.println("Check database for task statuses!\n");

            repo.close();

        } catch (SQLException | InterruptedException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
