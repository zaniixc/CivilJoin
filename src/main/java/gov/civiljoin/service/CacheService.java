package gov.civiljoin.service;

import gov.civiljoin.model.User;
import gov.civiljoin.model.Post;
import gov.civiljoin.model.SystemActivity;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.function.Supplier;

/**
 * High-performance caching service for CivilJoin
 * Implements multi-level caching with TTL and automatic eviction
 */
public class CacheService {
    private static final Logger LOGGER = Logger.getLogger(CacheService.class.getName());
    private static CacheService instance;
    
    // Cache configurations
    private static final int DEFAULT_TTL_MINUTES = 15;
    private static final int USER_CACHE_TTL_MINUTES = 30;
    private static final int POST_CACHE_TTL_MINUTES = 10;
    private static final int ACTIVITY_CACHE_TTL_MINUTES = 5;
    private static final int MAX_CACHE_SIZE = 1000;
    
    // Cache containers with thread-safe maps
    private final Map<String, CacheEntry<User>> userCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<Post>>> postCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<SystemActivity>>> activityCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<Object>> genericCache = new ConcurrentHashMap<>();
    
    // Cache statistics
    private long cacheHits = 0;
    private long cacheMisses = 0;
    private long evictions = 0;
    
    // Background cleanup service
    private final ScheduledExecutorService cleanupExecutor;
    
    private CacheService() {
        // Initialize cleanup service that runs every 5 minutes
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Cache-Cleanup");
            t.setDaemon(true);
            return t;
        });
        
        // Schedule periodic cleanup
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredEntries, 5, 5, TimeUnit.MINUTES);
        
        LOGGER.info("CacheService initialized with periodic cleanup every 5 minutes");
    }
    
    public static CacheService getInstance() {
        if (instance == null) {
            synchronized (CacheService.class) {
                if (instance == null) {
                    instance = new CacheService();
                }
            }
        }
        return instance;
    }
    
    /**
     * Cache entry with TTL support
     */
    private static class CacheEntry<T> {
        final T value;
        final LocalDateTime expiryTime;
        final LocalDateTime createdTime;
        
        CacheEntry(T value, int ttlMinutes) {
            this.value = value;
            this.createdTime = LocalDateTime.now();
            this.expiryTime = createdTime.plusMinutes(ttlMinutes);
        }
        
        boolean isExpired() {
            return LocalDateTime.now().isAfter(expiryTime);
        }
        
        long getAgeMinutes() {
            return ChronoUnit.MINUTES.between(createdTime, LocalDateTime.now());
        }
    }
    
    // User caching methods
    public void cacheUser(String key, User user) {
        userCache.put(key, new CacheEntry<>(user, USER_CACHE_TTL_MINUTES));
        enforceMaxSize(userCache);
    }
    
    public Optional<User> getCachedUser(String key) {
        CacheEntry<User> entry = userCache.get(key);
        if (entry != null) {
            if (!entry.isExpired()) {
                cacheHits++;
                return Optional.of(entry.value);
            } else {
                userCache.remove(key);
                evictions++;
            }
        }
        cacheMisses++;
        return Optional.empty();
    }
    
    public User getUserOrLoad(String key, Supplier<User> loader) {
        Optional<User> cached = getCachedUser(key);
        if (cached.isPresent()) {
            return cached.get();
        }
        
        User user = loader.get();
        if (user != null) {
            cacheUser(key, user);
        }
        return user;
    }
    
    // Post caching methods
    public void cachePosts(String key, List<Post> posts) {
        postCache.put(key, new CacheEntry<>(posts, POST_CACHE_TTL_MINUTES));
        enforceMaxSize(postCache);
    }
    
    public Optional<List<Post>> getCachedPosts(String key) {
        CacheEntry<List<Post>> entry = postCache.get(key);
        if (entry != null) {
            if (!entry.isExpired()) {
                cacheHits++;
                return Optional.of(entry.value);
            } else {
                postCache.remove(key);
                evictions++;
            }
        }
        cacheMisses++;
        return Optional.empty();
    }
    
    public List<Post> getPostsOrLoad(String key, Supplier<List<Post>> loader) {
        Optional<List<Post>> cached = getCachedPosts(key);
        if (cached.isPresent()) {
            return cached.get();
        }
        
        List<Post> posts = loader.get();
        if (posts != null && !posts.isEmpty()) {
            cachePosts(key, posts);
        }
        return posts;
    }
    
    // Activity caching methods
    public void cacheActivities(String key, List<SystemActivity> activities) {
        activityCache.put(key, new CacheEntry<>(activities, ACTIVITY_CACHE_TTL_MINUTES));
        enforceMaxSize(activityCache);
    }
    
    public Optional<List<SystemActivity>> getCachedActivities(String key) {
        CacheEntry<List<SystemActivity>> entry = activityCache.get(key);
        if (entry != null) {
            if (!entry.isExpired()) {
                cacheHits++;
                return Optional.of(entry.value);
            } else {
                activityCache.remove(key);
                evictions++;
            }
        }
        cacheMisses++;
        return Optional.empty();
    }
    
    public List<SystemActivity> getActivitiesOrLoad(String key, Supplier<List<SystemActivity>> loader) {
        Optional<List<SystemActivity>> cached = getCachedActivities(key);
        if (cached.isPresent()) {
            return cached.get();
        }
        
        List<SystemActivity> activities = loader.get();
        if (activities != null && !activities.isEmpty()) {
            cacheActivities(key, activities);
        }
        return activities;
    }
    
    // DASHBOARD-SPECIFIC CACHING for Performance Optimization
    
    /**
     * Cache dashboard data with optimized TTL for better performance
     */
    public void cacheDashboardData(String key, Object data) {
        cache("dashboard_" + key, data, 10); // 10-minute TTL for dashboard data
        LOGGER.info("Cached dashboard data: " + key);
    }
    
    /**
     * Get cached dashboard data
     */
    public Object getDashboardData(String key) {
        Optional<Object> cached = getCached("dashboard_" + key, Object.class);
        if (cached.isPresent()) {
            LOGGER.fine("Cache hit for dashboard data: " + key);
            return cached.get();
        }
        LOGGER.fine("Cache miss for dashboard data: " + key);
        return null;
    }
    
    /**
     * Check if dashboard data is cached
     */
    public boolean hasDashboardData(String key) {
        return isCached("dashboard_" + key);
    }
    
    /**
     * Cache theme resources for faster theme switching
     */
    public void cacheThemeResource(String theme, String resource) {
        cache("theme_" + theme, resource, 60); // 1-hour TTL for theme resources
        LOGGER.fine("Cached theme resource: " + theme);
    }
    
    /**
     * Get cached theme resource
     */
    public String getThemeResource(String theme) {
        Optional<String> cached = getCached("theme_" + theme, String.class);
        return cached.orElse(null);
    }
    
    // Generic caching methods
    @SuppressWarnings("unchecked")
    public <T> void cache(String key, T value, int ttlMinutes) {
        genericCache.put(key, new CacheEntry<>(value, ttlMinutes));
        enforceMaxSize(genericCache);
    }
    
    public <T> void cache(String key, T value) {
        cache(key, value, DEFAULT_TTL_MINUTES);
    }
    
    /**
     * Check if a key exists in cache (regardless of type)
     * Used for fast cache existence checks
     */
    public boolean isCached(String key) {
        // Check all cache types for the key
        CacheEntry<?> entry = genericCache.get(key);
        if (entry != null && !entry.isExpired()) {
            return true;
        }
        
        // Check user cache
        CacheEntry<User> userEntry = userCache.get(key);
        if (userEntry != null && !userEntry.isExpired()) {
            return true;
        }
        
        // Check post cache
        CacheEntry<List<Post>> postEntry = postCache.get(key);
        if (postEntry != null && !postEntry.isExpired()) {
            return true;
        }
        
        // Check activity cache
        CacheEntry<List<SystemActivity>> activityEntry = activityCache.get(key);
        if (activityEntry != null && !activityEntry.isExpired()) {
            return true;
        }
        
        return false;
    }
    
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getCached(String key, Class<T> type) {
        CacheEntry<Object> entry = genericCache.get(key);
        if (entry != null) {
            if (!entry.isExpired()) {
                cacheHits++;
                try {
                    return Optional.of((T) entry.value);
                } catch (ClassCastException e) {
                    LOGGER.warning("Cache type mismatch for key: " + key);
                    genericCache.remove(key);
                }
            } else {
                genericCache.remove(key);
                evictions++;
            }
        }
        cacheMisses++;
        return Optional.empty();
    }
    
    public <T> T getOrLoad(String key, Class<T> type, Supplier<T> loader) {
        Optional<T> cached = getCached(key, type);
        if (cached.isPresent()) {
            return cached.get();
        }
        
        T value = loader.get();
        if (value != null) {
            cache(key, value);
        }
        return value;
    }
    
    // Cache invalidation methods
    public void invalidateUser(String key) {
        userCache.remove(key);
    }
    
    public void invalidateUserByUsername(String username) {
        userCache.entrySet().removeIf(entry -> 
            entry.getKey().contains(username) || 
            (entry.getValue().value != null && username.equals(entry.getValue().value.getUsername()))
        );
    }
    
    public void invalidatePosts(String key) {
        postCache.remove(key);
    }
    
    public void invalidateAllPosts() {
        postCache.clear();
    }
    
    public void invalidateActivities(String key) {
        activityCache.remove(key);
    }
    
    public void invalidateAllActivities() {
        activityCache.clear();
    }
    
    public void invalidate(String key) {
        userCache.remove(key);
        postCache.remove(key);
        activityCache.remove(key);
        genericCache.remove(key);
    }
    
    public void invalidateAll() {
        userCache.clear();
        postCache.clear();
        activityCache.clear();
        genericCache.clear();
        LOGGER.info("All caches cleared");
    }
    
    // Cache maintenance
    private <T> void enforceMaxSize(Map<String, CacheEntry<T>> cache) {
        if (cache.size() > MAX_CACHE_SIZE) {
            // Remove oldest entries when cache is full
            List<Map.Entry<String, CacheEntry<T>>> entries = new ArrayList<>(cache.entrySet());
            entries.sort((e1, e2) -> e1.getValue().createdTime.compareTo(e2.getValue().createdTime));
            
            int toRemove = cache.size() - (MAX_CACHE_SIZE * 3 / 4); // Remove 25% when full
            for (int i = 0; i < toRemove && i < entries.size(); i++) {
                cache.remove(entries.get(i).getKey());
                evictions++;
            }
        }
    }
    
    /**
     * Clean up expired cache entries
     */
    private void cleanupExpiredEntries() {
        long startTime = System.currentTimeMillis();
        int totalCleaned = 0;
        
        totalCleaned += cleanupExpiredFromCache(userCache);
        totalCleaned += cleanupExpiredFromCache(postCache);
        totalCleaned += cleanupExpiredFromCache(activityCache);
        totalCleaned += cleanupExpiredFromCache(genericCache);
        
        long duration = System.currentTimeMillis() - startTime;
        if (totalCleaned > 0) {
            LOGGER.info("Cache cleanup completed: removed " + totalCleaned + " expired entries in " + duration + "ms");
        }
        evictions += totalCleaned;
    }
    
    private <T> int cleanupExpiredFromCache(Map<String, CacheEntry<T>> cache) {
        long sizeBefore = cache.size();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        return (int) (sizeBefore - cache.size());
    }
    
    /**
     * Get cache statistics
     */
    public CacheStats getStats() {
        return new CacheStats(
            cacheHits, cacheMisses, evictions,
            userCache.size(), postCache.size(), activityCache.size(), genericCache.size()
        );
    }
    
    /**
     * Cache statistics class
     */
    public static class CacheStats {
        public final long hits;
        public final long misses;
        public final long evictions;
        public final int userCacheSize;
        public final int postCacheSize;
        public final int activityCacheSize;
        public final int genericCacheSize;
        public final double hitRatio;
        
        CacheStats(long hits, long misses, long evictions, int userSize, int postSize, int activitySize, int genericSize) {
            this.hits = hits;
            this.misses = misses;
            this.evictions = evictions;
            this.userCacheSize = userSize;
            this.postCacheSize = postSize;
            this.activityCacheSize = activitySize;
            this.genericCacheSize = genericSize;
            
            long total = hits + misses;
            this.hitRatio = total > 0 ? (double) hits / total : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Cache Stats: %.1f%% hit ratio (%d hits, %d misses, %d evictions)\n" +
                "Cache Sizes: Users=%d, Posts=%d, Activities=%d, Generic=%d",
                hitRatio * 100, hits, misses, evictions,
                userCacheSize, postCacheSize, activityCacheSize, genericCacheSize
            );
        }
    }
    
    /**
     * Preload commonly accessed data
     */
    public void preloadCommonData() {
        // This method can be called during application startup
        // to preload frequently accessed data
        LOGGER.info("Preloading common cache data...");
        
        // Example: preload recent posts, active users, etc.
        // Implementation would depend on specific business needs
    }
    
    /**
     * Shutdown the cache service
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        invalidateAll();
        LOGGER.info("CacheService shutdown completed");
    }
} 