package gov.civiljoin.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Optimized resource manager for FXML, CSS, and other assets
 * Provides caching and async loading capabilities
 */
public class ResourceManager {
    private static final Logger LOGGER = Logger.getLogger(ResourceManager.class.getName());
    private static ResourceManager instance;
    
    // Cached resources
    private final ConcurrentHashMap<String, URL> urlCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Parent> fxmlCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> cssCache = new ConcurrentHashMap<>();
    
    // Common resource paths for quick lookup
    private static final String[] FXML_SEARCH_PATHS = {
        "view/",
        "/gov/civiljoin/view/",
        "gov/civiljoin/view/"
    };
    
    private static final String[] CSS_SEARCH_PATHS = {
        "css/",
        "/gov/civiljoin/css/",
        "gov/civiljoin/css/"
    };
    
    private ResourceManager() {}
    
    public static ResourceManager getInstance() {
        if (instance == null) {
            synchronized (ResourceManager.class) {
                if (instance == null) {
                    instance = new ResourceManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Load FXML asynchronously with caching
     */
    public CompletableFuture<LoadedView> loadFXMLAsync(String fxmlName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check cache first
                Parent cachedRoot = fxmlCache.get(fxmlName);
                if (cachedRoot != null) {
                    LOGGER.fine("Using cached FXML: " + fxmlName);
                    return new LoadedView(cachedRoot, null); // Controller not cached
                }
                
                // Find FXML URL
                URL fxmlUrl = findResource(fxmlName, FXML_SEARCH_PATHS);
                if (fxmlUrl == null) {
                    throw new IOException("FXML not found: " + fxmlName);
                }
                
                // Load FXML
                FXMLLoader loader = new FXMLLoader(fxmlUrl);
                Parent root = loader.load();
                Object controller = loader.getController();
                
                // Cache for future use (optional for memory management)
                if (shouldCache(fxmlName)) {
                    fxmlCache.put(fxmlName, root);
                }
                
                LOGGER.fine("Loaded FXML: " + fxmlName);
                return new LoadedView(root, controller);
                
            } catch (IOException e) {
                LOGGER.severe("Failed to load FXML: " + fxmlName + " - " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Find resource URL with caching
     */
    public URL findResource(String resourceName, String[] searchPaths) {
        // Check cache first
        String cacheKey = resourceName + "_url";
        URL cachedUrl = urlCache.get(cacheKey);
        if (cachedUrl != null) {
            return cachedUrl;
        }
        
        // Search through paths
        for (String basePath : searchPaths) {
            String fullPath = basePath + resourceName;
            
            // Try class resource first
            URL url = getClass().getResource(fullPath);
            if (url == null) {
                // Try classloader
                url = getClass().getClassLoader().getResource(fullPath.startsWith("/") ? fullPath.substring(1) : fullPath);
            }
            
            if (url != null) {
                urlCache.put(cacheKey, url);
                LOGGER.fine("Found resource: " + fullPath + " -> " + url);
                return url;
            }
        }
        
        LOGGER.warning("Resource not found: " + resourceName);
        return null;
    }
    
    /**
     * Get CSS URL with caching
     */
    public URL getCSSResource(String cssName) {
        return findResource(cssName, CSS_SEARCH_PATHS);
    }
    
    /**
     * Create optimized scene with preloaded resources
     */
    public CompletableFuture<Scene> createSceneAsync(String fxmlName, String cssName) {
        return loadFXMLAsync(fxmlName).thenApply(loadedView -> {
            Scene scene = new Scene(loadedView.root);
            
            // Add CSS if specified
            if (cssName != null) {
                URL cssUrl = getCSSResource(cssName);
                if (cssUrl != null) {
                    scene.getStylesheets().add(cssUrl.toExternalForm());
                }
            }
            
            return scene;
        });
    }
    
    /**
     * Preload commonly used resources
     */
    public void preloadCommonResources() {
        CompletableFuture.runAsync(() -> {
            String[] commonFXMLs = {
                "dashboard.fxml",
                "login.fxml",
                "register.fxml",
                "settings.fxml"
            };
            
            for (String fxml : commonFXMLs) {
                try {
                    loadFXMLAsync(fxml).join();
                    LOGGER.fine("Preloaded: " + fxml);
                } catch (Exception e) {
                    LOGGER.warning("Failed to preload: " + fxml + " - " + e.getMessage());
                }
            }
            
            LOGGER.info("Common resources preloaded");
        });
    }
    
    /**
     * Clear cache to manage memory
     */
    public void clearCache() {
        fxmlCache.clear();
        cssCache.clear();
        urlCache.clear();
        LOGGER.info("Resource cache cleared");
    }
    
    /**
     * Check if resource should be cached (avoid caching large/rarely used views)
     */
    private boolean shouldCache(String resourceName) {
        // Don't cache admin panels and large views to save memory
        return !resourceName.contains("admin") && !resourceName.contains("large");
    }
    
    /**
     * Get cache statistics
     */
    public String getCacheStats() {
        return String.format("ResourceManager Cache - FXML: %d, CSS: %d, URLs: %d",
            fxmlCache.size(), cssCache.size(), urlCache.size());
    }
    
    /**
     * Loaded view container
     */
    public static class LoadedView {
        public final Parent root;
        public final Object controller;
        
        public LoadedView(Parent root, Object controller) {
            this.root = root;
            this.controller = controller;
        }
    }
} 