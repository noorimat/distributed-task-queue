package com.noorimat.taskqueue;

import com.noorimat.taskqueue.grpc.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

public class TaskServiceServer {
    private static final Logger logger = LoggerFactory.getLogger(TaskServiceServer.class);

    private final Server server;
    private final TaskQueue queue;
    private final TaskRepository repository;
    private final TaskExecutor executor;

    public TaskServiceServer(int port, TaskQueue queue, TaskRepository repository, TaskExecutor executor) {
        this.queue = queue;
        this.repository = repository;
        this.executor = executor;
        this.server = ServerBuilder.forPort(port)
                .addService(new TaskServiceImpl())
                .build();
    }

    public void start() throws IOException {
        server.start();
        logger.info("gRPC server started on port {}", server.getPort());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down gRPC server...");
            try {
                TaskServiceServer.this.stop();
            } catch (InterruptedException e) {
                logger.error("Error during shutdown", e);
            }
        }));
    }

    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
            logger.info("gRPC server stopped");
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    private class TaskServiceImpl extends TaskServiceGrpc.TaskServiceImplBase {

        @Override
        public void submitTask(SubmitTaskRequest request, StreamObserver<SubmitTaskResponse> responseObserver) {
            try {
                Task task = new Task(request.getPayload());
                repository.save(task);
                queue.enqueue(task);

                SubmitTaskResponse response = SubmitTaskResponse.newBuilder()
                        .setTaskId(task.getId())
                        .setSuccess(true)
                        .setMessage("Task submitted successfully")
                        .build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();

                logger.info("Task submitted via gRPC: {}", task.getId());

            } catch (SQLException e) {
                logger.error("Failed to submit task", e);

                SubmitTaskResponse response = SubmitTaskResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Failed to submit task: " + e.getMessage())
                        .build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        }

        @Override
        public void getTaskStatus(GetTaskStatusRequest request, StreamObserver<GetTaskStatusResponse> responseObserver) {
            try {
                var taskOpt = repository.findById(request.getTaskId());

                if (taskOpt.isPresent()) {
                    Task task = taskOpt.get();
                    GetTaskStatusResponse response = GetTaskStatusResponse.newBuilder()
                            .setTaskId(task.getId())
                            .setStatus(task.getStatus().name())
                            .setAttemptCount(task.getAttemptCount())
                            .setFound(true)
                            .build();

                    responseObserver.onNext(response);
                } else {
                    GetTaskStatusResponse response = GetTaskStatusResponse.newBuilder()
                            .setTaskId(request.getTaskId())
                            .setFound(false)
                            .build();

                    responseObserver.onNext(response);
                }

                responseObserver.onCompleted();

            } catch (SQLException e) {
                logger.error("Failed to get task status", e);
                responseObserver.onError(e);
            }
        }

        @Override
        public void listTasks(ListTasksRequest request, StreamObserver<ListTasksResponse> responseObserver) {
            try {
                var tasks = repository.loadPendingTasks();

                ListTasksResponse.Builder responseBuilder = ListTasksResponse.newBuilder();

                for (Task task : tasks) {
                    TaskInfo taskInfo = TaskInfo.newBuilder()
                            .setTaskId(task.getId())
                            .setPayload(task.getPayload())
                            .setStatus(task.getStatus().name())
                            .setAttemptCount(task.getAttemptCount())
                            .build();

                    responseBuilder.addTasks(taskInfo);
                }

                responseObserver.onNext(responseBuilder.build());
                responseObserver.onCompleted();

            } catch (SQLException e) {
                logger.error("Failed to list tasks", e);
                responseObserver.onError(e);
            }
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException, SQLException {
        TaskRepository repo = new TaskRepository("tasks.db");
        TaskQueue queue = new TaskQueue();

        // Load pending tasks
        repo.loadPendingTasks().forEach(queue::enqueue);

        // Start executor with 3 workers
        TaskExecutor executor = new TaskExecutor(queue, repo, 3);
        executor.start();

        // Start gRPC server on port 50051
        TaskServiceServer server = new TaskServiceServer(50051, queue, repo, executor);
        server.start();

        logger.info("Task Queue Server is running. Press Ctrl+C to stop.");

        server.blockUntilShutdown();

        executor.shutdown();
        repo.close();
    }
}
