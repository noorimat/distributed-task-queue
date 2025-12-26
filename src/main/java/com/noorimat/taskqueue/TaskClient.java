package com.noorimat.taskqueue;

import com.noorimat.taskqueue.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class TaskClient {
    private static final Logger logger = LoggerFactory.getLogger(TaskClient.class);

    private final ManagedChannel channel;
    private final TaskServiceGrpc.TaskServiceBlockingStub blockingStub;

    public TaskClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.blockingStub = TaskServiceGrpc.newBlockingStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public String submitTask(String payload) {
        SubmitTaskRequest request = SubmitTaskRequest.newBuilder()
                .setPayload(payload)
                .setMaxRetries(3)
                .build();

        SubmitTaskResponse response = blockingStub.submitTask(request);

        if (response.getSuccess()) {
            logger.info("Task submitted: {} - {}", response.getTaskId(), response.getMessage());
            return response.getTaskId();
        } else {
            logger.error("Failed to submit task: {}", response.getMessage());
            return null;
        }
    }

    public void getTaskStatus(String taskId) {
        GetTaskStatusRequest request = GetTaskStatusRequest.newBuilder()
                .setTaskId(taskId)
                .build();

        GetTaskStatusResponse response = blockingStub.getTaskStatus(request);

        if (response.getFound()) {
            logger.info("Task {}: status={}, attempts={}",
                    taskId, response.getStatus(), response.getAttemptCount());
        } else {
            logger.warn("Task not found: {}", taskId);
        }
    }

    public void listTasks() {
        ListTasksRequest request = ListTasksRequest.newBuilder()
                .setStatusFilter("PENDING")
                .build();

        ListTasksResponse response = blockingStub.listTasks(request);

        logger.info("Found {} pending tasks:", response.getTasksCount());
        for (TaskInfo task : response.getTasksList()) {
            logger.info("  - {}: {} (attempts: {})",
                    task.getTaskId(), task.getPayload(), task.getAttemptCount());
        }
    }

    public static void main(String[] args) throws Exception {
        TaskClient client = new TaskClient("localhost", 50051);

        try {
            System.out.println("=== Task Queue Client Demo ===\n");

            // Submit 5 tasks
            System.out.println("Submitting 5 tasks...");
            String taskId1 = client.submitTask("Process payment for order #1001");
            String taskId2 = client.submitTask("Send confirmation email");
            String taskId3 = client.submitTask("Update inventory");
            String taskId4 = client.submitTask("Generate invoice");
            String taskId5 = client.submitTask("Archive old records");

            System.out.println("\nWaiting 2 seconds...\n");
            Thread.sleep(2000);

            // Check status
            System.out.println("Checking task status...");
            if (taskId1 != null) client.getTaskStatus(taskId1);
            if (taskId3 != null) client.getTaskStatus(taskId3);

            System.out.println("\nListing pending tasks...");
            client.listTasks();

        } finally {
            client.shutdown();
        }
    }
}