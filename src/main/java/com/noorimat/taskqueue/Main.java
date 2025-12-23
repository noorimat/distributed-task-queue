package com.noorimat.taskqueue;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== Distributed Task Queue Started ===");

        // Create the queue
        TaskQueue queue = new TaskQueue();

        // Create some test tasks
        Task task1 = new Task("Process user registration");
        Task task2 = new Task("Send welcome email");
        Task task3 = new Task("Generate analytics report");

        // Add tasks to queue
        queue.enqueue(task1);
        queue.enqueue(task2);
        queue.enqueue(task3);

        System.out.println("Queue size: " + queue.size());

        // Process tasks
        while (!queue.isEmpty()) {
            queue.dequeue().ifPresent(task -> {
                System.out.println("Processing: " + task);
                task.setStatus(TaskStatus.RUNNING);

                // Simulate work
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                task.setStatus(TaskStatus.COMPLETED);
                System.out.println("Completed: " + task);
            });
        }

        System.out.println("=== All tasks processed ===");
    }
}
