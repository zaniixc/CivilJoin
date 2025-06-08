package gov.civiljoin;

import gov.civiljoin.component.SplashScreen;
import gov.civiljoin.service.CommentService;
import gov.civiljoin.service.SystemActivityService;
import gov.civiljoin.service.SecurityService;
import gov.civiljoin.service.AsyncTaskService;
import gov.civiljoin.service.CacheService;
import gov.civiljoin.service.AdminSecurityService;
import gov.civiljoin.util.DatabaseUtil;
import gov.civiljoin.util.ThemeManager;
import gov.civiljoin.util.NavigationManager;
import gov.civiljoin.util.PerformanceMonitor;
import gov.civiljoin.util.ResourceManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Alert;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;

import javafx.stage.Stage;
import org.kordamp.bootstrapfx.BootstrapFX;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class CivilJoinApplication extends Application {
    private static final Logger LOGGER = Logger.getLogger(CivilJoinApplication.class.getName());
    private static final String APP_TITLE = "CivilJoin";
    private static final int MIN_WIDTH = 800;
    private static final int MIN_HEIGHT = 600;

    private final CommentService commentService = new CommentService();
    private final SystemActivityService activityService = new SystemActivityService();
    private final SecurityService securityService = new SecurityService();
    private Stage primaryStage;
    private final ThemeManager themeManager = ThemeManager.getInstance();
    private final NavigationManager navigationManager = NavigationManager.getInstance();

    @Override
    public void start(Stage stage) throws IOException {
        this.primaryStage = stage;
        navigationManager.setPrimaryStage(stage);
        
        // Start performance monitoring early
        PerformanceMonitor.getInstance().startMonitoring();
        PerformanceMonitor.getInstance().startOperation("application_startup");
        
        // CRITICAL: Initialize and preload themes FIRST for dark mode default
        initializeThemeManagerWithDefaults();
        
        // Initialize critical services in parallel
        CompletableFuture<Void> servicesInit = initializeCriticalServicesAsync();
        
        // Preload common resources while services initialize
        CompletableFuture<Void> resourcesInit = preloadResourcesAsync();
        
        // Show splash screen immediately
        showOptimizedSplashScreen(servicesInit, resourcesInit);
    }
    
    /**
     * Initialize ThemeManager with dark mode as default and preload resources
     */
    private void initializeThemeManagerWithDefaults() {
        try {
            LOGGER.info("Initializing ThemeManager with dark mode as default");
            
            // Get ThemeManager instance (this triggers preloading)
            ThemeManager themeManager = ThemeManager.getInstance();
            
            // Ensure dark mode is set as default
            themeManager.setDarkMode(true);
            
            LOGGER.info("ThemeManager initialized with dark mode default");
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error initializing ThemeManager", e);
        }
    }
    
    /**
     * Initialize critical services asynchronously
     */
    private CompletableFuture<Void> initializeCriticalServicesAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                PerformanceMonitor.getInstance().startOperation("services_init");
                
                // Initialize cache service first (other services may depend on it)
                CacheService.getInstance().preloadCommonData();
                
                // Initialize database connection pool
                DatabaseUtil.getConnection().close(); // Test connection and initialize pool
                
                // Initialize async task service
                AsyncTaskService.getInstance();
                
                // Services initialized successfully (removed problematic SecurityService calls)
                
                PerformanceMonitor.getInstance().endOperation("services_init");
                LOGGER.info("Critical services initialized successfully");
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error initializing critical services", e);
                throw new RuntimeException("Service initialization failed", e);
            }
        });
    }
    
    /**
     * Preload resources asynchronously
     */
    private CompletableFuture<Void> preloadResourcesAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                PerformanceMonitor.getInstance().startOperation("resource_preload");
                
                // Preload common FXML views and CSS
                ResourceManager.getInstance().preloadCommonResources();
                
                PerformanceMonitor.getInstance().endOperation("resource_preload");
                LOGGER.info("Resources preloaded successfully");
                
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error preloading resources", e);
                // Non-critical, continue startup
            }
        });
    }
    
    /**
     * Show optimized splash screen with better progress tracking
     */
    private void showOptimizedSplashScreen(CompletableFuture<Void> servicesInit, CompletableFuture<Void> resourcesInit) {
        SplashScreen splashScreen = new SplashScreen();
        
        // Create optimized initialization task
        Task<Void> initTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateProgress(0.1, 1.0);
                updateMessage("Initializing core services...");
                
                try {
                    // Wait for services to initialize (with timeout)
                    servicesInit.get(10, TimeUnit.SECONDS);
                    updateProgress(0.6, 1.0);
                    updateMessage("Services ready");
                    
                    // Check if resources are ready
                    if (!resourcesInit.isDone()) {
                        updateMessage("Loading resources...");
                    }
                    resourcesInit.get(5, TimeUnit.SECONDS);
                    updateProgress(0.9, 1.0);
                    updateMessage("Resources loaded");
                    
                } catch (TimeoutException e) {
                    LOGGER.warning("Initialization timeout, continuing with limited functionality");
                    updateMessage("Timeout - loading with limited features");
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Critical initialization error", e);
                    updateMessage("Error during initialization");
                    throw e;
                }
                
                updateProgress(1.0, 1.0);
                updateMessage("Ready!");
                Thread.sleep(200); // Brief pause to show completion
                
                return null;
            }
        };
        
        // Handle completion
        splashScreen.setOnFinished(result -> {
            try {
                PerformanceMonitor.getInstance().endOperation("application_startup");
                showLoginScreen();
                splashScreen.close();
                
                // Log startup performance
                PerformanceMonitor.PerformanceReport report = PerformanceMonitor.getInstance().getPerformanceReport();
                LOGGER.info("Application startup completed - " + report.getSummary());
                
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error loading login screen", e);
                handleStartupError(e);
            }
        });
        
        // Bind progress and show
        splashScreen.bindProgress(initTask);
        splashScreen.show();
        
        // Start initialization
        Thread taskThread = new Thread(initTask);
        taskThread.setDaemon(true);
        taskThread.start();
    }
    
    /**
     * Handle startup errors gracefully
     */
    private void handleStartupError(Exception e) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Startup Error");
            alert.setHeaderText("CivilJoin failed to start properly");
            alert.setContentText("Error: " + e.getMessage() + "\n\nPlease check logs and try again.");
            alert.showAndWait();
            Platform.exit();
        });
    }
    
    /**
     * Show login screen after splash finishes
     */
    private void showLoginScreen() throws IOException {
        String loginViewPath = "view/login.fxml";
        URL loginViewUrl = getClass().getResource(loginViewPath);
        
        if (loginViewUrl == null) {
            LOGGER.log(Level.SEVERE, "Login view not found at path: " + loginViewPath);
            System.err.println("ERROR: Login view not found at path: " + loginViewPath);

            System.out.println("Available resources:");
            listAvailableResources();
            
            throw new IOException("Login view not found at path: " + loginViewPath);
        }
        
        LOGGER.log(Level.INFO, "Loading login view from: " + loginViewUrl);
        System.out.println("Loading login view from: " + loginViewUrl);
        
        FXMLLoader fxmlLoader = new FXMLLoader(loginViewUrl);
        Parent root = fxmlLoader.load();
        Scene scene = new Scene(root, MIN_WIDTH, MIN_HEIGHT);
        
        // Add our global stylesheet - OPTIMIZED PATH RESOLUTION
        String[] cssSearchPaths = {
            "/gov/civiljoin/css/styles.css",
            "gov/civiljoin/css/styles.css",
            "/css/styles.css",
            "css/styles.css"
        };
        
        boolean cssLoaded = false;
        for (String cssPath : cssSearchPaths) {
            URL cssUrl = getClass().getResource(cssPath);
            if (cssUrl == null) {
                cssUrl = getClass().getClassLoader().getResource(cssPath.startsWith("/") ? cssPath.substring(1) : cssPath);
            }
            
            if (cssUrl != null) {
                String cssLocation = cssUrl.toExternalForm();
                scene.getStylesheets().add(cssLocation);
                LOGGER.info("Successfully loaded CSS from: " + cssLocation);
                cssLoaded = true;
                break;
            }
        }
        
        if (!cssLoaded) {
            LOGGER.warning("Could not load CSS from any search path");
        }
        
        // Also load additional theme CSS files
        String[] additionalCSS = {
            "css/dark-theme.css",    // Dark theme
            "css/light-theme.css",   // Light theme
            "gov/civiljoin/css/dark-theme.css",
            "gov/civiljoin/css/custom-controls.css"
        };
        
        for (String additionalCssPath : additionalCSS) {
            URL additionalCssUrl = getClass().getClassLoader().getResource(additionalCssPath);
            if (additionalCssUrl != null) {
                scene.getStylesheets().add(additionalCssUrl.toExternalForm());
                LOGGER.fine("Loaded additional CSS: " + additionalCssPath);
            }
        }
        
        scene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
        
        // Make sure root element has the container class
        if (!root.getStyleClass().contains("container")) {
            root.getStyleClass().add("container");
        }
        
        // For login/register, always use dark theme regardless of the global setting
        themeManager.setDarkMode(true);
        
        // Add auth-screen class to ensure consistent login styling
        if (!root.getStyleClass().contains("auth-screen")) {
            root.getStyleClass().add("auth-screen");
        }
        
        // Apply theme to scene - this will apply appropriate classes to all nodes
        themeManager.applyTheme(scene);
        
        // Ensure all containers have appropriate theme classes
        applyThemeToAllContainers(root);
        
        // Set stage properties
        primaryStage.setTitle(APP_TITLE);
        primaryStage.setMinWidth(MIN_WIDTH);
        primaryStage.setMinHeight(MIN_HEIGHT);
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // Register the stage with the theme manager
        themeManager.applyTheme(primaryStage);
        
        String dashboardViewPath = "view/dashboard.fxml";
        URL dashboardViewUrl = getClass().getResource(dashboardViewPath);
        if (dashboardViewUrl == null) {
            System.err.println("WARNING: Dashboard view not found at path: " + dashboardViewPath);
        } else {
            System.out.println("Dashboard view found at: " + dashboardViewUrl);
        }
    }
    
    /**
     * Recursively apply theme to all containers in the scene graph
     */
    private void applyThemeToAllContainers(Node node) {
        if (node instanceof Pane) {
            Pane pane = (Pane) node;
            
            // Add container class to all panes
            if (!pane.getStyleClass().contains("container")) {
                pane.getStyleClass().add("container");
            }
            
            // Add specific classes based on container type
            if (pane instanceof BorderPane) {
                // Apply to specific regions of BorderPane
                BorderPane borderPane = (BorderPane) pane;
                
                // Apply to top (header)
                Node top = borderPane.getTop();
                if (top instanceof Pane) {
                    if (!((Pane) top).getStyleClass().contains("header")) {
                        ((Pane) top).getStyleClass().add("header");
                    }
                }
                
                // Apply to left (sidebar)
                Node left = borderPane.getLeft();
                if (left instanceof Pane) {
                    if (!((Pane) left).getStyleClass().contains("sidebar")) {
                        ((Pane) left).getStyleClass().add("sidebar");
                    }
                }
                
                // Apply to bottom (footer)
                Node bottom = borderPane.getBottom();
                if (bottom instanceof Pane) {
                    if (!((Pane) bottom).getStyleClass().contains("footer")) {
                        ((Pane) bottom).getStyleClass().add("footer");
                    }
                }
            }
        }
        
        // Apply to children
        if (node instanceof Parent) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
                applyThemeToAllContainers(child);
            }
        }
    }

    private void listAvailableResources() {

        String[] knownPaths = {"view", "view/login.fxml", "view/dashboard.fxml", "view/register.fxml"};
        
        for (String path : knownPaths) {
            URL url = getClass().getResource(path);
            System.out.println("Resource '" + path + "': " + (url != null ? "Found at " + url : "Not found"));
        }
    }

    /**
     * Initialize the database using the simplified approach with master_schema.sql directly through DatabaseUtil
     */
    private void initializeDatabase() {
        try {
            // The DatabaseUtil now automatically initializes from master_schema.sql
            // We just need to verify the connection and initialize additional services
            Connection conn = DatabaseUtil.getConnection();
            DatabaseUtil.closeConnection(conn);
            
            LOGGER.log(Level.INFO, "Database connection verified successfully");
            
            // Initialize additional services that create their own tables
            commentService.initializeCommentTable();
            
            LOGGER.log(Level.INFO, "Database initialization completed successfully");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Database initialization error", e);
            System.err.println("Database initialization error: " + e.getMessage());
        }
    }
    
    /**
     * Fallback database initialization method
     */
    private void initializeDatabaseFallback() {
        Connection conn = null;
        Statement stmt = null;
        
        try {
            LOGGER.log(Level.INFO, "Starting fallback database initialization...");
            
            // Attempt to get connection (will create database if it doesn't exist)
            conn = DatabaseUtil.getConnection();
            
            // Create a new statement for executing the schema
            stmt = conn.createStatement();
            
            // Load enhanced_schema.sql from resources first, fallback to schema.sql
            InputStream is = getClass().getResourceAsStream("/database/enhanced_schema.sql");
            if (is == null) {
                is = getClass().getClassLoader().getResourceAsStream("database/enhanced_schema.sql");
            }
            
            // Fallback to original schema if enhanced not found
            if (is == null) {
                LOGGER.log(Level.WARNING, "Enhanced schema not found, trying original schema.sql");
                is = getClass().getResourceAsStream("/database/schema.sql");
                if (is == null) {
                    is = getClass().getClassLoader().getResourceAsStream("database/schema.sql");
                }
            }
            
            if (is == null) {
                LOGGER.log(Level.SEVERE, "Could not find any database schema file in resources");
                System.err.println("ERROR: Could not find database schema file in resources");
                return;
            }
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String schema = reader.lines().collect(Collectors.joining("\n"));
                
                try {
                    // Execute the entire schema
                    stmt.execute(schema);
                    LOGGER.log(Level.INFO, "Fallback database schema initialized successfully");
                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "Error executing complete schema: " + e.getMessage());
                    
                    // Fallback to executing statements one by one
                    String[] statements = schema.split(";");
                    int successCount = 0;
                    int errorCount = 0;
                    
                    for (String statement : statements) {
                        String trimmedStatement = statement.trim();
                        if (!trimmedStatement.isEmpty() && !trimmedStatement.startsWith("--")) {
                            try {
                                stmt.execute(trimmedStatement);
                                successCount++;
                            } catch (SQLException ex) {
                                errorCount++;
                                LOGGER.log(Level.WARNING, "Error executing SQL statement: " + ex.getMessage());
                                // Don't stop on individual statement errors
                            }
                        }
                    }
                    
                    LOGGER.log(Level.INFO, "Fallback schema execution completed: " + successCount + " successful, " + errorCount + " errors");
                }
            }
            
            // Initialize the comments table after schema is loaded
            commentService.initializeCommentTable();
            
            LOGGER.log(Level.INFO, "Fallback database initialization completed successfully");
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database connection error", e);
            System.err.println("Database connection error: " + e.getMessage());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading schema file", e);
            System.err.println("Error reading schema file: " + e.getMessage());
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database resources", e);
            }
        }
    }

    @Override
    public void stop() {
        LOGGER.info("Shutting down CivilJoin application...");
        
        try {
            // Shutdown performance services gracefully
            AsyncTaskService.getInstance().shutdown();
            CacheService.getInstance().shutdown();
            PerformanceMonitor.getInstance().stopMonitoring();
            
            // Close database connections
            DatabaseUtil.shutdown();
            
            LOGGER.info("CivilJoin application shutdown complete");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during application shutdown", e);
        }
    }

    @Override
    public void init() throws Exception {
        // Database is now automatically initialized via master schema in DatabaseUtil
        // No need for individual table initialization methods
        
        // Verify database health
        boolean dbHealthy = DatabaseUtil.isDatabaseHealthy();
        if (!dbHealthy) {
            LOGGER.warning("Database health check failed during application init");
        }
        
        // Initialize comment table (via service)
        commentService.initializeCommentTable();
        
        // Initialize enhanced timeline and security services
        // These will create their own tables with the enhanced schema
        activityService.initializeTable();
    }

    public static void main(String[] args) {
        launch();
    }
} 