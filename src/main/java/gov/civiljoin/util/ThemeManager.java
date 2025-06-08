package gov.civiljoin.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * ThemeManager handles all styling related to dark/light theme switching
 * in the application. It centralizes theme logic and styles.
 */
public class ThemeManager {
    
    // Singleton instance
    private static ThemeManager instance;
    
    // Theme state
    private boolean isDarkMode = true; // Start with dark mode by default
    
    // CSS stylesheet paths - OPTIMIZED LOADING ORDER
    private static final String BASE_CSS = "/css/common.css";
    private static final String DARK_THEME_CSS = "/css/dark-theme.css";
    private static final String STYLES_CSS = "/gov/civiljoin/css/styles.css";
    private static final String CUSTOM_CSS = "/gov/civiljoin/css/custom-controls.css";
    
    // Track all scenes that have had themes applied
    private final List<Scene> managedScenes = new ArrayList<>();
    
    // Theme cache for performance
    private final Map<String, String> themeCache = new HashMap<>();
    
    // Performance tracking
    private long themeApplicationStartTime;
    
    // Theme colors - Dark Mode (sleek black UI)
    private static final String DARK_BACKGROUND = "#1a1a1a"; // Very dark gray
    private static final String DARK_SURFACE = "#242424";    // Dark gray
    private static final String DARK_CARD = "#2d2d2d";       // Slightly lighter for cards
    private static final String DARK_SURFACE_LIGHTER = "#404040"; // For input fields
    private static final String DARK_PRIMARY = "#505050";    // Medium gray for primary elements
    private static final String DARK_SECONDARY = "#606060";  // Lighter gray
    private static final String DARK_TEXT_PRIMARY = "#ffffff"; // Pure white
    private static final String DARK_TEXT_SECONDARY = "#cccccc"; // Light gray
    private static final String DARK_TEXT_TERTIARY = "#999999"; // Medium gray
    
    // Theme colors - Light Mode (same as dark mode for consistency)
    private static final String LIGHT_BACKGROUND = "#1a1a1a";
    private static final String LIGHT_SURFACE = "#242424";
    private static final String LIGHT_CARD = "#2d2d2d";
    private static final String LIGHT_PRIMARY = "#505050";
    private static final String LIGHT_SECONDARY = "#606060";
    private static final String LIGHT_TEXT_PRIMARY = "#ffffff";
    private static final String LIGHT_TEXT_SECONDARY = "#cccccc";
    private static final String LIGHT_TEXT_TERTIARY = "#999999";
    
    // Accent colors
    private static final String ACCENT_BLUE = "#3498DB";   // Primary action blue
    private static final String ACCENT_RED = "#E74C3C";    // Delete/error red
    private static final String ACCENT_GREEN = "#2ECC71";  // Success green
    
    // Logger
    private static final Logger LOGGER = Logger.getLogger(ThemeManager.class.getName());
    
    // Private constructor for singleton
    private ThemeManager() {
        // Preload themes at initialization for better performance
        preloadThemes();
    }
    
    /**
     * Preload all theme resources for optimal performance
     */
    public void preloadThemes() {
        LOGGER.info("Preloading theme resources for optimal performance");
        
        // Cache all CSS resource paths
        cacheThemeResource("base", BASE_CSS);
        cacheThemeResource("dark", DARK_THEME_CSS);
        cacheThemeResource("light", "/css/light-theme.css");  // Add light theme
        cacheThemeResource("styles", STYLES_CSS);
        cacheThemeResource("custom", CUSTOM_CSS);
        
        LOGGER.info("Theme resources preloaded successfully");
    }
    
    /**
     * Cache theme resource for faster access
     */
    private void cacheThemeResource(String key, String resourcePath) {
        try {
            URL resource = getClass().getResource(resourcePath);
            if (resource != null) {
                themeCache.put(key, resource.toExternalForm());
                LOGGER.fine("Cached theme resource: " + key + " -> " + resourcePath);
            } else {
                LOGGER.warning("Theme resource not found: " + resourcePath);
            }
        } catch (Exception e) {
            LOGGER.warning("Error caching theme resource " + resourcePath + ": " + e.getMessage());
        }
    }
    
    // Get the singleton instance
    public static ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }
    
    // Set the current theme mode
    public void setDarkMode(boolean darkMode) {
        if (this.isDarkMode != darkMode) {
            this.isDarkMode = darkMode;
            updateAllManagedScenes();
        }
    }
    
    // Get current theme state
    public boolean isDarkMode() {
        return isDarkMode;
    }
    
    // Toggle theme - OPTIMIZED for entire application
    public boolean toggleTheme() {
        isDarkMode = !isDarkMode;
        LOGGER.info("Toggling theme to: " + (isDarkMode ? "Dark" : "Light") + " mode");
        
        // Update all managed scenes with the new theme
        updateAllManagedScenes();
        
        LOGGER.info("Theme toggle completed - now in " + (isDarkMode ? "Dark" : "Light") + " mode");
        return isDarkMode;
    }
    
    /**
     * OPTIMIZED: Apply appropriate theme to a scene with minimal UI blocking
     * Enhanced loading order for better text visibility - TARGET: <500ms
     */
    public void applyTheme(Scene scene) {
        if (scene == null) return;
        
        themeApplicationStartTime = System.currentTimeMillis();
        
        try {
            // OPTIMIZATION 1: Use cached CSS resources for faster loading
            scene.getStylesheets().clear();
            
            // Apply CSS in optimal order from cache based on current theme
            String baseResource = themeCache.get("base");
            String themeResource = themeCache.get(isDarkMode ? "dark" : "light");
            String stylesResource = themeCache.get("styles");
            String customResource = themeCache.get("custom");
            
            // Apply in order: base -> theme -> styles -> custom
            if (baseResource != null) scene.getStylesheets().add(baseResource);
            if (themeResource != null) scene.getStylesheets().add(themeResource);
            if (stylesResource != null) scene.getStylesheets().add(stylesResource);
            if (customResource != null) scene.getStylesheets().add(customResource);
            
            // OPTIMIZATION 2: Track this scene efficiently
            if (!managedScenes.contains(scene)) {
                managedScenes.add(scene);
            }
            
            // OPTIMIZATION 3: Apply theme to root with minimal overhead
            Parent root = scene.getRoot();
            if (root != null) {
                applyThemeToRootOptimized(root);
            }
            
            // Performance logging
            long duration = System.currentTimeMillis() - themeApplicationStartTime;
            LOGGER.info("Theme (" + (isDarkMode ? "Dark" : "Light") + ") applied in " + duration + "ms (target: <500ms)");
            
        } catch (Exception e) {
            LOGGER.warning("Error applying theme: " + e.getMessage());
        }
    }
    
    /**
     * FAST: Switch themes with optimal performance - TARGET: <500ms
     */
    public void switchTheme(Scene scene, String themeName) {
        if (scene == null || themeName == null) return;
        
        themeApplicationStartTime = System.currentTimeMillis();
        
        // Clear and reapply for theme switching
        scene.getStylesheets().clear();
        
        // Apply base styles
        String baseResource = themeCache.get("base");
        if (baseResource != null) {
            scene.getStylesheets().add(baseResource);
        }
        
        // Apply theme-specific styles
        String themeResource = themeCache.get(themeName.toLowerCase());
        if (themeResource != null) {
            scene.getStylesheets().add(themeResource);
        }
        
        // Apply component and custom styles
        String stylesResource = themeCache.get("styles");
        String customResource = themeCache.get("custom");
        if (stylesResource != null) scene.getStylesheets().add(stylesResource);
        if (customResource != null) scene.getStylesheets().add(customResource);
        
        // Update theme state
        isDarkMode = "dark".equals(themeName.toLowerCase());
        
        long duration = System.currentTimeMillis() - themeApplicationStartTime;
        LOGGER.info("Theme switched to " + themeName + " in " + duration + "ms");
    }
    
    /**
     * OPTIMIZED: Fast theme application to root node with minimal recursion
     */
    private void applyThemeToRootOptimized(Parent root) {
        // Add base classes immediately
        if (!root.getStyleClass().contains("container")) {
            root.getStyleClass().add("container");
        }
        
        // Always apply dark theme for consistency (PRD requirement)
        root.getStyleClass().remove("light-theme");
        if (!root.getStyleClass().contains("dark-theme")) {
            root.getStyleClass().add("dark-theme");
        }
        
        // Apply theme to immediate children only (avoid deep recursion)
        applyThemeToImmediateChildren(root);
    }
    
    /**
     * OPTIMIZED: Apply theme to immediate children without deep recursion
     */
    private void applyThemeToImmediateChildren(Parent parent) {
        for (Node child : parent.getChildrenUnmodifiable()) {
            // Apply styling based on common node types only
            if (child instanceof Pane) {
                Pane pane = (Pane) child;
                
                // Add appropriate class based on layout type
                if (child instanceof BorderPane) {
                    if (!pane.getStyleClass().contains("container")) {
                        pane.getStyleClass().add("container");
                    }
                } else if (child instanceof VBox || child instanceof HBox) {
                    if (!pane.getStyleClass().contains("container")) {
                        pane.getStyleClass().add("container");
                    }
                }
            } else if (child instanceof Button) {
                Button button = (Button) child;
                if (!button.getStyleClass().contains("button")) {
                    button.getStyleClass().add("button");
                }
            }
            
            // Only recurse one level deeper for critical containers
            if (child instanceof BorderPane) {
                BorderPane borderPane = (BorderPane) child;
                if (borderPane.getChildrenUnmodifiable().size() < 10) {
                    applyThemeToImmediateChildren(borderPane);
                }
            }
        }
    }
    
    /**
     * Apply theme to a specific stage (all scenes in this stage)
     */
    public void applyTheme(Stage stage) {
        if (stage == null) return;
        
        if (stage.getScene() != null) {
            applyTheme(stage.getScene());
        }
    }
    
    /**
     * Update all managed scenes with current theme
     */
    private void updateAllManagedScenes() {
        // Create a copy to avoid concurrent modification
        List<Scene> scenes = new ArrayList<>(managedScenes);
        
        // Remove any null scenes or scenes whose windows are no longer showing
        scenes.removeIf(scene -> scene == null || 
                               scene.getWindow() == null || 
                               !scene.getWindow().isShowing());
        
        // Apply theme to all remaining scenes
        for (Scene scene : scenes) {
            applyTheme(scene);
        }
        
        // Update managed scenes list
        managedScenes.clear();
        managedScenes.addAll(scenes);
    }
    
    /**
     * Style button with primary style (blue accent)
     */
    public void stylePrimaryButton(Button button) {
        button.getStyleClass().add("primary");
    }
    
    /**
     * Style button with danger style (red accent)
     */
    public void styleDangerButton(Button button) {
        button.getStyleClass().add("danger");
    }
    
    /**
     * Style a card/panel element
     */
    public void styleCard(Pane card) {
        card.getStyleClass().add("card");
    }
    
    /**
     * Style sidebar button
     */
    public void styleSidebarButton(Button button, boolean isActive) {
        if (!button.getStyleClass().contains("sidebar-button")) {
            button.getStyleClass().add("sidebar-button");
        }
        
        if (isActive) {
            if (!button.getStyleClass().contains("active")) {
                button.getStyleClass().add("active");
            }
        } else {
            button.getStyleClass().remove("active");
        }
    }
    
    /**
     * Style secondary text
     */
    public void styleSecondaryText(Labeled label) {
        label.getStyleClass().add("secondary");
    }
    
    /**
     * Style tertiary text
     */
    public void styleTertiaryText(Labeled label) {
        label.getStyleClass().add("tertiary");
    }
    
    /**
     * Apply auth card styling for login/register
     */
    public void styleAuthCard(Pane card) {
        card.getStyleClass().add("auth-card");
    }
    
    /**
     * Apply post card styling
     */
    public void stylePostCard(Pane card) {
        card.getStyleClass().add("post-card");
    }
    
    /**
     * Style theme toggle button
     */
    public void styleThemeToggleButton(Button button) {
        button.getStyleClass().add("theme-toggle");
        button.setText(isDarkMode ? "☀️ Light Mode" : "🌙 Dark Mode");
    }
} 