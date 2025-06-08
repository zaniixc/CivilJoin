package gov.civiljoin.service;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.Service;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High-performance async task service for CivilJoin
 * Manages background operations to keep UI responsive
 */
public class AsyncTaskService {
    private static final Logger LOGGER = Logger.getLogger(AsyncTaskService.class.getName());
    private static AsyncTaskService instance;
    
    // Optimized thread pools for different operation types
    private final ExecutorService dbExecutor;
    private final ExecutorService computeExecutor;
    private final ExecutorService ioExecutor;
    
    private AsyncTaskService() {
        // CPU core count for optimal thread allocation
        int coreCount = Runtime.getRuntime().availableProcessors();
        
        // Database operations pool (limited to prevent connection exhaustion)
        dbExecutor = new ThreadPoolExecutor(
            2, Math.min(coreCount, 8), 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            r -> {
                Thread t = new Thread(r, "DB-Task-" + System.currentTimeMillis());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY + 1);
                return t;
            }
        );
        
        // Compute-intensive operations (password hashing, etc.)
        computeExecutor = new ThreadPoolExecutor(
            coreCount / 2, coreCount, 30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(50),
            r -> {
                Thread t = new Thread(r, "Compute-Task-" + System.currentTimeMillis());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
            }
        );
        
        // I/O operations (file operations, network)
        ioExecutor = new ThreadPoolExecutor(
            2, coreCount * 2, 45L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(75),
            r -> {
                Thread t = new Thread(r, "IO-Task-" + System.currentTimeMillis());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            }
        );
        
        LOGGER.info("AsyncTaskService initialized with " + coreCount + " cores available");
    }
    
    public static AsyncTaskService getInstance() {
        if (instance == null) {
            synchronized (AsyncTaskService.class) {
                if (instance == null) {
                    instance = new AsyncTaskService();
                }
            }
        }
        return instance;
    }
    
    /**
     * Execute database operation asynchronously
     */
    public <T> CompletableFuture<T> executeDbTask(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, dbExecutor)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    LOGGER.log(Level.SEVERE, "Database task failed", throwable);
                }
            });
    }
    
    /**
     * Execute database operation with UI callback
     */
    public <T> void executeDbTask(Supplier<T> task, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        executeDbTask(task)
            .whenComplete((result, throwable) -> {
                Platform.runLater(() -> {
                    if (throwable != null) {
                        if (onError != null) onError.accept(throwable);
                    } else {
                        if (onSuccess != null) onSuccess.accept(result);
                    }
                });
            });
    }
    
    /**
     * Execute compute-intensive operation asynchronously
     */
    public <T> CompletableFuture<T> executeComputeTask(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, computeExecutor)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    LOGGER.log(Level.SEVERE, "Compute task failed", throwable);
                }
            });
    }
    
    /**
     * Execute compute operation with UI callback  
     */
    public <T> void executeComputeTask(Supplier<T> task, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        executeComputeTask(task)
            .whenComplete((result, throwable) -> {
                Platform.runLater(() -> {
                    if (throwable != null) {
                        if (onError != null) onError.accept(throwable);
                    } else {
                        if (onSuccess != null) onSuccess.accept(result);
                    }
                });
            });
    }
    
    /**
     * Execute I/O operation asynchronously
     */
    public <T> CompletableFuture<T> executeIOTask(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, ioExecutor)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    LOGGER.log(Level.SEVERE, "I/O task failed", throwable);
                }
            });
    }
    
    /**
     * Execute I/O operation with UI callback
     */
    public <T> void executeIOTask(Supplier<T> task, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        executeIOTask(task)
            .whenComplete((result, throwable) -> {
                Platform.runLater(() -> {
                    if (throwable != null) {
                        if (onError != null) onError.accept(throwable);
                    } else {
                        if (onSuccess != null) onSuccess.accept(result);
                    }
                });
            });
    }
    
    /**
     * Create a JavaFX Task for database operations with proper error handling
     */
    public <T> Task<T> createDbTask(Supplier<T> taskSupplier) {
        return new Task<T>() {
            @Override
            protected T call() throws Exception {
                long startTime = System.currentTimeMillis();
                try {
                    T result = taskSupplier.get();
                    long duration = System.currentTimeMillis() - startTime;
                    if (duration > 1000) { // Log slow operations
                        LOGGER.warning("Slow database operation took " + duration + "ms");
                    }
                    return result;
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Database task failed after " + 
                              (System.currentTimeMillis() - startTime) + "ms", e);
                    throw e;
                }
            }
        };
    }
    
    /**
     * Execute task with progress tracking and timeout
     */
    public <T> void executeTaskWithProgress(Supplier<T> task, Consumer<T> onSuccess, 
                                          Consumer<Throwable> onError, Consumer<Double> onProgress,
                                          long timeoutSeconds) {
        Task<T> taskWrapper = new Task<T>() {
            @Override
            protected T call() throws Exception {
                // Simulate progress updates for long-running tasks
                updateProgress(0.1, 1.0);
                T result = task.get();
                updateProgress(1.0, 1.0);
                return result;
            }
        };
        
        if (onProgress != null) {
            taskWrapper.progressProperty().addListener((obs, oldProgress, newProgress) -> {
                Platform.runLater(() -> onProgress.accept(newProgress.doubleValue()));
            });
        }
        
        taskWrapper.setOnSucceeded(e -> {
            if (onSuccess != null) {
                Platform.runLater(() -> onSuccess.accept(taskWrapper.getValue()));
            }
        });
        
        taskWrapper.setOnFailed(e -> {
            if (onError != null) {
                Platform.runLater(() -> onError.accept(taskWrapper.getException()));
            }
        });
        
        // Add timeout handling
        CompletableFuture.runAsync(taskWrapper, dbExecutor)
            .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .exceptionally(throwable -> {
                if (onError != null) {
                    Platform.runLater(() -> onError.accept(throwable));
                }
                return null;
            });
    }
    
    /**
     * Batch database operations for better performance
     */
    public <T> CompletableFuture<java.util.List<T>> executeBatchDbTasks(java.util.List<Supplier<T>> tasks, 
                                                          Consumer<java.util.List<T>> onAllComplete,
                                                          Consumer<Throwable> onError) {
        CompletableFuture<java.util.List<T>> batchFuture = CompletableFuture.allOf(
            tasks.stream()
                .map(this::executeDbTask)
                .toArray(CompletableFuture[]::new)
        ).thenApply(v -> {
            java.util.List<T> results = new java.util.ArrayList<>();
            for (Supplier<T> task : tasks) {
                try {
                    results.add(executeDbTask(task).join());
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Individual batch task failed", e);
                }
            }
            return results;
        });
        
        batchFuture.whenComplete((results, throwable) -> {
            Platform.runLater(() -> {
                if (throwable != null) {
                    if (onError != null) onError.accept(throwable);
                } else {
                    if (onAllComplete != null) onAllComplete.accept(results);
                }
            });
        });
        
        return batchFuture;
    }
    
    /**
     * Get performance metrics
     */
    public String getPerformanceMetrics() {
        ThreadPoolExecutor dbPool = (ThreadPoolExecutor) dbExecutor;
        ThreadPoolExecutor computePool = (ThreadPoolExecutor) computeExecutor;
        ThreadPoolExecutor ioPool = (ThreadPoolExecutor) ioExecutor;
        
        return String.format(
            "AsyncTaskService Metrics:\n" +
            "DB Pool: Active=%d, Queue=%d, Completed=%d\n" +
            "Compute Pool: Active=%d, Queue=%d, Completed=%d\n" +
            "I/O Pool: Active=%d, Queue=%d, Completed=%d",
            dbPool.getActiveCount(), dbPool.getQueue().size(), dbPool.getCompletedTaskCount(),
            computePool.getActiveCount(), computePool.getQueue().size(), computePool.getCompletedTaskCount(),
            ioPool.getActiveCount(), ioPool.getQueue().size(), ioPool.getCompletedTaskCount()
        );
    }
    
    /**
     * Shutdown all executors gracefully
     */
    public void shutdown() {
        LOGGER.info("Shutting down AsyncTaskService...");
        shutdownExecutor(dbExecutor, "Database");
        shutdownExecutor(computeExecutor, "Compute");
        shutdownExecutor(ioExecutor, "I/O");
    }
    
    private void shutdownExecutor(ExecutorService executor, String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warning(name + " executor did not terminate gracefully, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            LOGGER.warning(name + " executor shutdown interrupted");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
} 