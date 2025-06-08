package gov.civiljoin.util;

import gov.civiljoin.service.AsyncTaskService;
import gov.civiljoin.service.CacheService;
import javafx.application.Platform;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.Map;

/**
 * Performance monitoring utility for CivilJoin
 * Tracks various performance metrics to identify bottlenecks
 */
public class PerformanceMonitor {
    private static final Logger LOGGER = Logger.getLogger(PerformanceMonitor.class.getName());
    private static PerformanceMonitor instance;
    
    // JVM monitoring beans
    private final MemoryMXBean memoryBean;
    private final ThreadMXBean threadBean;
    
    // Performance metrics
    private final Map<String, AtomicLong> operationCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> operationTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> operationStartTimes = new ConcurrentHashMap<>();
    
    // UI responsiveness tracking
    private volatile long uiThreadBlockTime = 0;
    private volatile int uiUpdatesPerSecond = 0;
    private final AtomicLong uiUpdateCount = new AtomicLong(0);
    
    // Database performance
    private final AtomicLong dbQueryCount = new AtomicLong(0);
    private final AtomicLong dbQueryTime = new AtomicLong(0);
    private final AtomicLong slowQueries = new AtomicLong(0);
    
    // Memory tracking
    private volatile long heapUsed = 0;
    private volatile long heapMax = 0;
    private volatile long gcCount = 0;
    private volatile long gcTime = 0;
    
    // Background monitoring
    private final ScheduledExecutorService monitorExecutor;
    private volatile boolean isMonitoring = false;
    
    // Add notification-specific performance tracking
    private final ConcurrentHashMap<String, Long> notificationMetrics = new ConcurrentHashMap<>();
    private final AtomicLong totalNotifications = new AtomicLong(0);
    private final AtomicLong notificationRenderTime = new AtomicLong(0);
    
    private PerformanceMonitor() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
        
        this.monitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Performance-Monitor");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY); // Low priority to not interfere
            return t;
        });
        
        LOGGER.info("PerformanceMonitor initialized");
    }
    
    public static PerformanceMonitor getInstance() {
        if (instance == null) {
            synchronized (PerformanceMonitor.class) {
                if (instance == null) {
                    instance = new PerformanceMonitor();
                }
            }
        }
        return instance;
    }
    
    /**
     * Start performance monitoring
     */
    public void startMonitoring() {
        if (isMonitoring) {
            return;
        }
        
        isMonitoring = true;
        
        // Schedule regular metric collection
        monitorExecutor.scheduleAtFixedRate(this::collectSystemMetrics, 0, 5, TimeUnit.SECONDS);
        monitorExecutor.scheduleAtFixedRate(this::calculateUIPerformance, 1, 1, TimeUnit.SECONDS);
        
        LOGGER.info("Performance monitoring started");
    }
    
    /**
     * Stop performance monitoring
     */
    public void stopMonitoring() {
        isMonitoring = false;
        monitorExecutor.shutdown();
        LOGGER.info("Performance monitoring stopped");
    }
    
    /**
     * Track the start of an operation
     */
    public void startOperation(String operationName) {
        operationStartTimes.put(operationName, System.nanoTime());
    }
    
    /**
     * Track the end of an operation
     */
    public void endOperation(String operationName) {
        Long startTime = operationStartTimes.remove(operationName);
        if (startTime != null) {
            long duration = System.nanoTime() - startTime;
            
            operationCounts.computeIfAbsent(operationName, k -> new AtomicLong(0)).incrementAndGet();
            operationTimes.computeIfAbsent(operationName, k -> new AtomicLong(0)).addAndGet(duration);
            
            // Log slow operations
            long durationMs = duration / 1_000_000;
            if (durationMs > 1000) { // Operations slower than 1 second
                LOGGER.warning("Slow operation detected: " + operationName + " took " + durationMs + "ms");
            }
        }
    }
    
    /**
     * Track database query performance
     */
    public void recordDbQuery(long durationMs) {
        dbQueryCount.incrementAndGet();
        dbQueryTime.addAndGet(durationMs);
        
        if (durationMs > 500) { // Queries slower than 500ms
            slowQueries.incrementAndGet();
            LOGGER.warning("Slow database query detected: " + durationMs + "ms");
        }
    }
    
    /**
     * Track UI update
     */
    public void recordUIUpdate() {
        uiUpdateCount.incrementAndGet();
    }
    
    /**
     * Collect system-level performance metrics
     */
    private void collectSystemMetrics() {
        try {
            // Memory metrics
            heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
            heapMax = memoryBean.getHeapMemoryUsage().getMax();
            
            // GC metrics (simplified)
            long newGcCount = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(gcBean -> gcBean.getCollectionCount())
                .sum();
            long newGcTime = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(gcBean -> gcBean.getCollectionTime())
                .sum();
            
            if (newGcCount > gcCount) {
                LOGGER.info("GC occurred: " + (newGcCount - gcCount) + " collections, " + 
                          (newGcTime - gcTime) + "ms total time");
            }
            
            gcCount = newGcCount;
            gcTime = newGcTime;
            
        } catch (Exception e) {
            LOGGER.warning("Error collecting system metrics: " + e.getMessage());
        }
    }
    
    /**
     * Calculate UI performance metrics
     */
    private void calculateUIPerformance() {
        long currentUpdates = uiUpdateCount.getAndSet(0);
        uiUpdatesPerSecond = (int) currentUpdates;
        
        // Check if UI thread is responsive with more aggressive monitoring
        long checkStart = System.nanoTime();
        Platform.runLater(() -> {
            long responseTime = (System.nanoTime() - checkStart) / 1_000_000; // Convert to ms
            
            // PRD requirement: UI operations should be under 500ms, warn at 100ms, critical at 300ms
            if (responseTime > 300) { // Critical threshold
                uiThreadBlockTime = responseTime;
                LOGGER.severe("CRITICAL: UI thread blocked for " + responseTime + "ms - This exceeds PRD requirements!");
                
                // Auto-suggest optimizations
                Platform.runLater(() -> {
                    if (responseTime > 500) {
                        LOGGER.severe("EMERGENCY: UI thread blocked " + responseTime + "ms - Triggering garbage collection");
                        System.gc(); // Emergency GC
                        
                        // Log current memory state
                        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
                        long heapMax = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
                        LOGGER.severe("Memory state during UI block: " + heapUsed + "MB/" + heapMax + "MB used");
                    }
                });
            } else if (responseTime > 100) { // Warning threshold
                uiThreadBlockTime = responseTime;
                LOGGER.warning("UI thread blocked for " + responseTime + "ms - Approaching PRD limit of 500ms");
            } else {
                uiThreadBlockTime = 0; // Reset if responsive
            }
        });
    }
    
    /**
     * Get comprehensive performance report
     */
    public PerformanceReport getPerformanceReport() {
        return new PerformanceReport(
            heapUsed, heapMax, gcCount, gcTime,
            dbQueryCount.get(), dbQueryTime.get(), slowQueries.get(),
            uiUpdatesPerSecond, uiThreadBlockTime,
            threadBean.getThreadCount(), threadBean.getDaemonThreadCount(),
            new ConcurrentHashMap<>(operationCounts), new ConcurrentHashMap<>(operationTimes)
        );
    }
    
    /**
     * Performance report data class
     */
    public static class PerformanceReport {
        public final long heapUsedMB;
        public final long heapMaxMB;
        public final double heapUsagePercent;
        public final long gcCount;
        public final long gcTimeMs;
        
        public final long dbQueryCount;
        public final long avgDbQueryTimeMs;
        public final long slowQueryCount;
        
        public final int uiUpdatesPerSecond;
        public final long uiThreadBlockTimeMs;
        
        public final int totalThreads;
        public final int daemonThreads;
        
        public final Map<String, AtomicLong> operationCounts;
        public final Map<String, AtomicLong> operationTimes;
        
        public PerformanceReport(long heapUsed, long heapMax, long gcCount, long gcTime,
                               long dbQueryCount, long dbQueryTime, long slowQueryCount,
                               int uiUpdatesPerSecond, long uiThreadBlockTime,
                               int totalThreads, int daemonThreads,
                               Map<String, AtomicLong> operationCounts, Map<String, AtomicLong> operationTimes) {
            
            this.heapUsedMB = heapUsed / (1024 * 1024);
            this.heapMaxMB = heapMax / (1024 * 1024);
            this.heapUsagePercent = heapMax > 0 ? (double) heapUsed / heapMax * 100 : 0;
            this.gcCount = gcCount;
            this.gcTimeMs = gcTime;
            
            this.dbQueryCount = dbQueryCount;
            this.avgDbQueryTimeMs = dbQueryCount > 0 ? dbQueryTime / dbQueryCount : 0;
            this.slowQueryCount = slowQueryCount;
            
            this.uiUpdatesPerSecond = uiUpdatesPerSecond;
            this.uiThreadBlockTimeMs = uiThreadBlockTime;
            
            this.totalThreads = totalThreads;
            this.daemonThreads = daemonThreads;
            
            this.operationCounts = operationCounts;
            this.operationTimes = operationTimes;
        }
        
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== CivilJoin Performance Report ===\n");
            sb.append(String.format("Memory: %d/%d MB (%.1f%%)\n", heapUsedMB, heapMaxMB, heapUsagePercent));
            sb.append(String.format("GC: %d collections, %d ms total\n", gcCount, gcTimeMs));
            sb.append(String.format("Database: %d queries, %d ms avg, %d slow\n", 
                    dbQueryCount, avgDbQueryTimeMs, slowQueryCount));
            sb.append(String.format("UI: %d updates/sec, %d ms max block time\n", 
                    uiUpdatesPerSecond, uiThreadBlockTimeMs));
            sb.append(String.format("Threads: %d total (%d daemon)\n", totalThreads, daemonThreads));
            
            // Add cache stats
            CacheService.CacheStats cacheStats = CacheService.getInstance().getStats();
            sb.append(String.format("Cache: %.1f%% hit ratio (%d hits, %d misses)\n",
                    cacheStats.hitRatio * 100, cacheStats.hits, cacheStats.misses));
            
            // Add async task stats
            sb.append("\nAsync Tasks:\n");
            sb.append(AsyncTaskService.getInstance().getPerformanceMetrics());
            
            return sb.toString();
        }
        
        public String getDetailedReport() {
            StringBuilder sb = new StringBuilder(getSummary());
            
            if (!operationCounts.isEmpty()) {
                sb.append("\n\n=== Operation Performance ===\n");
                operationCounts.forEach((operation, count) -> {
                    AtomicLong totalTime = operationTimes.get(operation);
                    long avgTime = totalTime != null ? totalTime.get() / count.get() / 1_000_000 : 0; // Convert to ms
                    sb.append(String.format("%s: %d calls, %d ms avg\n", operation, count.get(), avgTime));
                });
            }
            
            return sb.toString();
        }
        
        /**
         * Check if performance is degraded
         */
        public boolean isPerformanceDegraded() {
            return heapUsagePercent > 85 || // High memory usage
                   avgDbQueryTimeMs > 200 || // Slow database queries
                   uiThreadBlockTimeMs > 100 || // UI responsiveness issues
                   slowQueryCount > 0; // Any slow queries
        }
        
        /**
         * Get performance recommendations
         */
        public String getRecommendations() {
            StringBuilder recommendations = new StringBuilder();
            
            if (heapUsagePercent > 85) {
                recommendations.append("• High memory usage detected. Consider increasing heap size or optimizing memory usage.\n");
            }
            
            if (avgDbQueryTimeMs > 200) {
                recommendations.append("• Database queries are slow. Consider adding indexes or optimizing queries.\n");
            }
            
            if (uiThreadBlockTimeMs > 100) {
                recommendations.append("• UI thread blocking detected. Move more operations to background threads.\n");
            }
            
            if (slowQueryCount > 0) {
                recommendations.append("• Slow database queries detected. Review query performance and add caching.\n");
            }
            
            CacheService.CacheStats cacheStats = CacheService.getInstance().getStats();
            if (cacheStats.hitRatio < 0.7) {
                recommendations.append("• Low cache hit ratio. Consider adjusting cache TTL or preloading strategies.\n");
            }
            
            if (recommendations.length() == 0) {
                recommendations.append("• Performance looks good! No immediate issues detected.\n");
            }
            
            return recommendations.toString();
        }
    }
    
    /**
     * Utility method to time an operation
     */
    public static <T> T timeOperation(String operationName, java.util.function.Supplier<T> operation) {
        PerformanceMonitor monitor = getInstance();
        monitor.startOperation(operationName);
        try {
            return operation.get();
        } finally {
            monitor.endOperation(operationName);
        }
    }
    
    /**
     * Get current memory usage percentage
     */
    public double getCurrentMemoryUsage() {
        return heapMax > 0 ? (double) heapUsed / heapMax * 100 : 0;
    }
    
    /**
     * Force garbage collection and measure impact
     */
    public void forceGCAndMeasure() {
        long beforeUsed = heapUsed;
        long beforeTime = System.currentTimeMillis();
        
        System.gc();
        
        // Wait a bit for GC to complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        collectSystemMetrics();
        
        long afterUsed = heapUsed;
        long duration = System.currentTimeMillis() - beforeTime;
        long freed = beforeUsed - afterUsed;
        
        LOGGER.info(String.format("Manual GC: freed %d MB in %d ms", freed / (1024 * 1024), duration));
    }
    
    /**
     * Record notification creation time
     */
    public void recordNotificationCreation(long durationMs) {
        notificationRenderTime.addAndGet(durationMs);
        totalNotifications.incrementAndGet();
        
        if (durationMs > 50) { // Log slow notifications
            LOGGER.warning("Slow notification creation: " + durationMs + "ms");
        }
    }
    
    /**
     * Record UI thread blocking time
     */
    public void recordUIThreadBlock(String operation, long durationMs) {
        notificationMetrics.put("ui_block_" + operation, durationMs);
        
        if (durationMs > 16) { // More than one frame at 60fps
            LOGGER.warning("UI thread blocked for " + durationMs + "ms during: " + operation);
        }
    }
    
    /**
     * Get notification performance summary
     */
    public String getNotificationPerformanceReport() {
        long total = totalNotifications.get();
        long totalTime = notificationRenderTime.get();
        double avgTime = total > 0 ? (double) totalTime / total : 0;
        
        return String.format(
            "Notification Performance:\n" +
            "- Total notifications: %d\n" +
            "- Total render time: %dms\n" +
            "- Average render time: %.2fms\n" +
            "- Target: <16ms for 60fps\n" +
            "- Status: %s",
            total, totalTime, avgTime,
            avgTime < 16 ? "EXCELLENT" : avgTime < 50 ? "GOOD" : "NEEDS OPTIMIZATION"
        );
    }
} 