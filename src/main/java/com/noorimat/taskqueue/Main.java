package com.noorimat.taskqueue;

import java.sql.SQLException;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== Distributed Task Queue with Persistence ===");

        try {
            TaskRepository repo = new TaskRepository("tasks.db");

            // Load any pending tasks from previous runs
            var pendingTasks = repo.loadPendingTasks();
            System.out.println("Loaded " + pendingTasks.size() + " pending tasks from database");

            TaskQueue queue = new TaskQueue();
            pendingTasks.forEach(queue::enqueue);

            // Create new test tasks
            Task task1 = new Task("Task 1: User registration");
            Task task2 = new Task("Task 2: Send email");
            Task task3 = new Task("Task 3: Analytics");
            Task task4 = new Task("Task 4: Backup database");
            Task task5 = new Task("Task 5: Generate report");

            // Save all as PENDING
            repo.save(task1);
            repo.save(task2);
            repo.save(task3);
            repo.save(task4);
            repo.save(task5);

            queue.enqueue(task1);
            queue.enqueue(task2);
            queue.enqueue(task3);
            queue.enqueue(task4);
            queue.enqueue(task5);

            System.out.println("Queue size: " + queue.size());

            // Process only 2 tasks, then "crash"
            int processed = 0;
            while (!queue.isEmpty() && processed < 2) {
                queue.dequeue().ifPresent(task -> {
                    try {
                        System.out.println("Processing: " + task);
                        task.setStatus(TaskStatus.RUNNING);
                        repo.save(task);

                        Thread.sleep(500);

                        task.setStatus(TaskStatus.COMPLETED);
                        repo.markCompleted(task.getId());
                        System.out.println("Completed: " + task);

                    } catch (Exception e) {
                        System.err.println("Error: " + e.getMessage());
                    }
                });
                processed++;
            }

            // SIMULATE CRASH - Don't process remaining tasks
            System.out.println("\n💥 SIMULATED CRASH - " + queue.size() + " tasks still pending!");
            System.out.println("Run again to recover and complete them.\n");

            repo.close();

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
