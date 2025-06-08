package gov.civiljoin.controller;

import gov.civiljoin.CivilJoinApplication;
import gov.civiljoin.component.PostCardComponent;
import gov.civiljoin.model.Comment;
import gov.civiljoin.model.Post;
import gov.civiljoin.model.User;
import gov.civiljoin.service.AuthService;
import gov.civiljoin.service.CommentService;
import gov.civiljoin.service.PostService;
import gov.civiljoin.service.CacheService;
import gov.civiljoin.util.AlertUtil;
import gov.civiljoin.util.DatabaseUtil;
import gov.civiljoin.util.NotificationManager;
import gov.civiljoin.util.ThemeManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.util.Duration;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import gov.civiljoin.service.AsyncTaskService;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main controller for dashboard view - refactored to delegate to view-specific controllers
 */
public class DashboardController implements Initializable {
    // Logger
    private static final Logger LOGGER = Logger.getLogger(DashboardController.class.getName());
    
    // UI Components
    @FXML private Label userLabel;
    @FXML private Button logoutButton;
    @FXML private Button adminButton;
    @FXML private Button dashboardButton;
    @FXML private Button timelineButton;
    @FXML private Button feedbackButton;
    @FXML private Button settingsButton;
    @FXML private TilePane postTilePane;
    @FXML private Button themeToggleButton;
    @FXML private VBox notificationArea;
    
    // Data
    private User currentUser;
    private List<Post> posts = new ArrayList<>();
    
    // Services
    private final AuthService authService = new AuthService();
    private final PostService postService = new PostService();
    private final CommentService commentService = new CommentService();
    
    // View state tracking
    private enum View { DASHBOARD, TIMELINE, FEEDBACK, SETTINGS, ADMIN }
    private View currentView;
    
    // Theme management
    private final ThemeManager themeManager = ThemeManager.getInstance();
    
    // View controllers
    private TimelineController timelineController;
    private FeedbackController feedbackController;
    private SettingsController settingsController;
    private Initializable currentController;  // Track current view controller
    // Admin view controllers could be added here
    
    // Performance monitoring and loading indicators
    @FXML private ProgressIndicator loadingIndicator;
    private final AsyncTaskService asyncTaskService = AsyncTaskService.getInstance();
    private final CacheService cacheService = CacheService.getInstance();
    
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        LOGGER.info("Initializing Dashboard with performance optimizations");
        
        // Setup loading indicator first
        setupLoadingIndicator();
        
        // Initialize the theme toggle button asynchronously
        Platform.runLater(this::setupThemeToggleButton);
        
        // Set up responsive grid layout
        setupResponsiveGrid();
        
        // Set up notification area
        setupNotificationArea();
        
        // Load dashboard data asynchronously to prevent UI blocking
        loadDashboardDataAsync();
        
        // Initialize view controllers (lazy loading)
        // Controllers will be created when their views are first accessed
    }
    
    /**
     * Setup loading indicator for performance feedback
     */
    private void setupLoadingIndicator() {
        if (loadingIndicator == null) {
            loadingIndicator = new ProgressIndicator();
            loadingIndicator.setMaxSize(50, 50);
            loadingIndicator.setVisible(false);
            loadingIndicator.getStyleClass().add("loading-indicator");
        }
    }
    
    /**
     * Load dashboard data asynchronously to prevent UI freezing
     */
    private void loadDashboardDataAsync() {
        // Show loading indicator
        if (loadingIndicator != null) {
            Platform.runLater(() -> loadingIndicator.setVisible(true));
        }
        
        Task<Void> loadTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    // Check cache first for better performance
                    Optional<List<Post>> cachedPosts = cacheService.getCachedPosts("dashboard_posts");
                    if (cachedPosts.isPresent()) {
                        Platform.runLater(() -> {
                            posts.clear();
                            posts.addAll(cachedPosts.get());
                            updatePostDisplay();
                        });
                    } else {
                        // Load from database in background
                        loadPostsFromDatabaseAsync();
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error loading dashboard data", e);
                }
                return null;
            }
            
            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    if (loadingIndicator != null) {
                        loadingIndicator.setVisible(false);
                    }
                    LOGGER.info("Dashboard data loaded successfully");
                });
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    if (loadingIndicator != null) {
                        loadingIndicator.setVisible(false);
                    }
                    // Show error notification
                    NotificationManager.getInstance().showError("Failed to load dashboard data");
                });
            }
        };
        
        // Execute task on background thread
        Thread taskThread = new Thread(loadTask);
        taskThread.setDaemon(true);
        taskThread.start();
    }
    
    /**
     * Update post display on UI thread
     */
    private void updatePostDisplay() {
        if (postTilePane != null) {
            postTilePane.getChildren().clear();
            
            // Progressive loading - show first 6 posts immediately
            int initialLoadCount = Math.min(posts.size(), 6);
            for (int i = 0; i < initialLoadCount; i++) {
                try {
                    // Create a simple VBox for the post instead of PostCardComponent
                    VBox postCard = createPostCard(posts.get(i));
                    postTilePane.getChildren().add(postCard);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error creating post card", e);
                }
            }
            
            // Load remaining posts progressively
            if (posts.size() > initialLoadCount) {
                loadRemainingPostsAsync(initialLoadCount);
            }
        }
    }
    
    /**
     * Create a simple post card for display
     */
    private VBox createPostCard(Post post) {
        VBox postCard = new VBox(8);
        postCard.getStyleClass().add("post-card");
        postCard.setPadding(new Insets(12));
        postCard.setPrefWidth(200);
        
        // Title
        Label titleLabel = new Label(post.getTitle());
        titleLabel.getStyleClass().addAll("post-title", "label");
        titleLabel.setWrapText(true);
        
        // Content preview
        String contentPreview = post.getContent();
        if (contentPreview.length() > 100) {
            contentPreview = contentPreview.substring(0, 100) + "...";
        }
        Label contentLabel = new Label(contentPreview);
        contentLabel.getStyleClass().addAll("post-content", "label");
        contentLabel.setWrapText(true);
        
        // Author and date
        Label metaLabel = new Label("By " + post.getAuthorName() + " • " + post.getCreatedAt());
        metaLabel.getStyleClass().addAll("post-metadata", "text-muted", "label");
        
        postCard.getChildren().addAll(titleLabel, contentLabel, metaLabel);
        
        return postCard;
    }
    
    /**
     * Set up responsive grid layout for TilePane based on PRD requirements:
     * Mobile: < 768px (1 column)
     * Tablet: 768px - 1024px (2 columns)  
     * Desktop: > 1024px (4 columns)
     */
    private void setupResponsiveGrid() {
        // Add listener to scene width property to adjust columns dynamically
        Platform.runLater(() -> {
            if (postTilePane.getScene() != null) {
                postTilePane.getScene().widthProperty().addListener((obs, oldWidth, newWidth) -> {
                    updateGridColumns(newWidth.doubleValue());
                });
                
                // Set initial column count
                updateGridColumns(postTilePane.getScene().getWidth());
            }
        });
    }
    
    /**
     * Update TilePane column count based on screen width
     */
    private void updateGridColumns(double sceneWidth) {
        int columns;
        
        if (sceneWidth < 768) {
            columns = 1;  // Mobile
        } else if (sceneWidth < 1024) {
            columns = 2;  // Tablet
        } else {
            columns = 4;  // Desktop
        }
        
        postTilePane.setPrefColumns(columns);
        LOGGER.info("Updated grid columns to " + columns + " for scene width: " + sceneWidth);
    }
    
    /**
     * Set up the theme toggle button
     */
    private void setupThemeToggleButton() {
        // Create theme toggle button with better styling
        if (themeToggleButton == null) {
            themeToggleButton = new Button(themeManager.isDarkMode() ? "☀️ Light Mode" : "🌙 Dark Mode");
        }
        
        // Apply theme-specific styling
        themeManager.styleThemeToggleButton(themeToggleButton);
        themeToggleButton.setOnAction(this::handleThemeToggle);
        
        // Add to scene after it's available
        Platform.runLater(() -> {
            // Try to find the main layout container
            Scene scene = postTilePane.getScene();
            if (scene != null) {
                Parent root = scene.getRoot();
                
                // Find the appropriate container to add our button
                if (root instanceof BorderPane) {
                    BorderPane borderPane = (BorderPane) root;
                    
                    // Create a container for the button to position it
                    HBox buttonContainer = new HBox(themeToggleButton);
                    buttonContainer.setAlignment(Pos.TOP_RIGHT);
                    buttonContainer.setPadding(new Insets(10));
                    
                    // Add to top right of border pane
                    if (borderPane.getTop() instanceof HBox) {
                        HBox topBox = (HBox) borderPane.getTop();
                        Region spacer = new Region();
                        HBox.setHgrow(spacer, Priority.ALWAYS);
                        topBox.getChildren().addAll(spacer, themeToggleButton);
                    } else {
                        borderPane.setRight(buttonContainer);
                    }
                } else {
                    // Fall back to absolute positioning if we can't find a better container
                    AnchorPane.setTopAnchor(themeToggleButton, 10.0);
                    AnchorPane.setRightAnchor(themeToggleButton, 10.0);
                    
                    if (root instanceof Pane) {
                        ((Pane) root).getChildren().add(themeToggleButton);
                    }
                }
            }
        });
    }
    
    /**
     * Set up notification area
     */
    private void setupNotificationArea() {
        if (notificationArea == null) {
            notificationArea = new VBox(10);
        }
        notificationArea.setAlignment(Pos.TOP_RIGHT);
        notificationArea.setPadding(new Insets(20));
        notificationArea.setMouseTransparent(true);
        notificationArea.getStyleClass().add("notification-container");
        
        // Set up the notification manager with this container
        NotificationManager.getInstance().setNotificationContainer(notificationArea);
        
        // Add to the scene after it's available
        Platform.runLater(() -> {
            if (postTilePane.getScene() != null && postTilePane.getScene().getRoot() instanceof BorderPane) {
                BorderPane root = (BorderPane) postTilePane.getScene().getRoot();
                
                // Create a stack pane to overlay notifications
                StackPane overlay = new StackPane();
                overlay.getChildren().add(notificationArea);
                overlay.setAlignment(Pos.TOP_RIGHT);
                overlay.setMouseTransparent(true);
                
                // Add the overlay to the root
                if (root.getChildren().stream().noneMatch(child -> child instanceof StackPane && child.getStyleClass().contains("notification-overlay"))) {
                    overlay.getStyleClass().add("notification-overlay");
                    root.getChildren().add(overlay);
                    overlay.toFront();
                }
            }
        });
    }
    
    /**
     * Show notification in the app
     */
    private void showNotification(String message, NotificationManager.NotificationType type) {
        NotificationManager.getInstance().showNotification(message, type);
    }
    
    /**
     * OPTIMIZED setUser method - Non-blocking according to PRD requirements
     * UI operations must not block the JavaFX Application Thread
     */
    public void setUser(User user) {
        this.currentUser = user;
        
        // OPTIMIZATION 1: Update UI immediately without waiting for data
        userLabel.setText("Welcome, " + user.getUsername());
        
        // OPTIMIZATION 2: Show admin panel button if user is admin or owner (immediate UI update)
        adminButton.setVisible(user.getRole() == User.Role.ADMIN || user.getRole() == User.Role.OWNER);
        
        // OPTIMIZATION 3: Set view first (instant UI feedback)
        setCurrentView(View.DASHBOARD);
        
        // OPTIMIZATION 4: Load posts asynchronously to prevent UI blocking
        loadPostsFromDatabaseAsync();
    }
    
    /**
     * CRITICAL FIX: Async post loading to prevent UI thread blocking
     * PRD REQUIREMENT: No blocking operations on JavaFX Application Thread
     */
    private void loadPostsFromDatabaseAsync() {
        // Show loading state immediately
        Platform.runLater(() -> {
            postTilePane.getChildren().clear();
            Label loadingLabel = new Label("Loading posts...");
            loadingLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #64b5f6; -fx-font-weight: bold;");
            postTilePane.getChildren().add(loadingLabel);
        });
        
        // CRITICAL: Execute database operation asynchronously
        AsyncTaskService.getInstance().executeDbTask(
            // Background task (runs on worker thread, NOT UI thread)
            () -> {
                try {
                    long startTime = System.nanoTime();
                    List<Post> loadedPosts = postService.getAllPosts();
                    long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                    
                    LOGGER.info("Posts loaded asynchronously in " + durationMs + "ms");
                    
                    // PRD compliance check
                    if (durationMs > 500) {
                        LOGGER.warning("Slow post loading: " + durationMs + "ms");
                    }
                    
                    return loadedPosts;
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error loading posts asynchronously", e);
                    throw new RuntimeException("Failed to load posts", e);
                }
            },
            // Success callback (runs on JavaFX Application Thread)
            loadedPosts -> {
                this.posts = loadedPosts;
                
                // Update UI with loaded posts
                if (currentView == View.DASHBOARD) {
                    showDashboardView(); // Refresh dashboard with new data
                }
                
                showNotification("Posts loaded successfully (" + posts.size() + " posts)", 
                               NotificationManager.NotificationType.SUCCESS);
            },
            // Error callback (runs on JavaFX Application Thread)
            throwable -> {
                LOGGER.log(Level.SEVERE, "Failed to load posts", throwable);
                this.posts = new ArrayList<>(); // Empty list as fallback
                
                // Show error state
                postTilePane.getChildren().clear();
                Label errorLabel = new Label("Failed to load posts: " + throwable.getMessage());
                errorLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #ff4444;");
                postTilePane.getChildren().add(errorLabel);
                
                showNotification("Failed to load posts: " + throwable.getMessage(), 
                               NotificationManager.NotificationType.ERROR);
            }
        );
    }
    
    /**
     * DEPRECATED: Synchronous post loading - replaced with async version
     * This method was causing UI thread blocking and violating PRD requirements
     */
    @Deprecated
    private void loadPostsFromDatabase() {
        // This method is deprecated - use loadPostsFromDatabaseAsync() instead
        LOGGER.warning("DEPRECATED: Synchronous loadPostsFromDatabase() called - use async version");
        posts = postService.getAllPosts();
    }
    
    /**
     * Handle logout button action
     */
    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            // Return to login screen
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/gov/civiljoin/view/login.fxml"));
            Scene scene = new Scene(fxmlLoader.load());
            
            // Add CSS to ensure styling is consistent
            String cssPath = "gov/civiljoin/css/styles.css";  // Remove leading slash
            URL cssUrl = getClass().getClassLoader().getResource(cssPath);
            
            if (cssUrl != null) {
                String cssLocation = cssUrl.toExternalForm();
                LOGGER.info("Found CSS at: " + cssLocation);
                scene.getStylesheets().add(cssLocation);
            } else {
                LOGGER.warning("CSS file not found at: " + cssPath);
                
                // Try alternative locations
                String[] altPaths = {
                    "/gov/civiljoin/css/styles.css",
                    "css/styles.css",
                    "/css/styles.css",
                    "../css/styles.css"
                };
                
                for (String altPath : altPaths) {
                    cssUrl = getClass().getResource(altPath);
                    if (cssUrl != null) {
                        String cssLocation = cssUrl.toExternalForm();
                        LOGGER.info("Found CSS at alternative path: " + cssLocation);
                        scene.getStylesheets().add(cssLocation);
                        break;
                    }
                }
            }
            
            // Apply theme - the auth-screen class will override theme settings for login/register
            // We don't need to reset the theme manager's state since login will use dark styling
            // regardless of the current theme setting
            themeManager.applyTheme(scene);
            
            Stage stage = (Stage) logoutButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("CivilJoin - Login");
            stage.show();
        } catch (IOException e) {
            // Handle error
            AlertUtil.showErrorAlert("Error Loading View", "Could not load login view: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Handle dashboard button action
     */
    @FXML
    private void handleDashboard(ActionEvent event) {
        setCurrentView(View.DASHBOARD);
    }
    
    /**
     * Handle timeline button action
     */
    @FXML
    private void handleTimeline(ActionEvent event) {
        setCurrentView(View.TIMELINE);
    }
    
    /**
     * Handle feedback button action
     */
    @FXML
    private void handleFeedback(ActionEvent event) {
        setCurrentView(View.FEEDBACK);
    }
    
    /**
     * Handle settings button action
     */
    @FXML
    private void handleSettings(ActionEvent event) {
        setCurrentView(View.SETTINGS);
    }
    
    /**
     * Handle admin button action
     */
    @FXML
    private void handleAdmin(ActionEvent event) {
        setCurrentView(View.ADMIN);
    }
    
    /**
     * Set the current view and update UI
     */
    private void setCurrentView(View view) {
        currentView = view;
        
        // Apply styleClass to sidebar buttons using ThemeManager
        themeManager.styleSidebarButton(dashboardButton, view == View.DASHBOARD);
        themeManager.styleSidebarButton(timelineButton, view == View.TIMELINE);
        themeManager.styleSidebarButton(feedbackButton, view == View.FEEDBACK);
        themeManager.styleSidebarButton(settingsButton, view == View.SETTINGS);
        themeManager.styleSidebarButton(adminButton, view == View.ADMIN);
        
        // Set active button style and show appropriate view
        switch (view) {
            case DASHBOARD:
                showDashboardView();
                break;
            case TIMELINE:
                showTimelineView();
                break;
            case FEEDBACK:
                showFeedbackView();
                break;
            case SETTINGS:
                showSettingsView();
                break;
            case ADMIN:
                showAdminView();
                break;
        }
    }
    
    /**
     * OPTIMIZED: Show the dashboard view with lazy loading to prevent UI blocking
     */
    private void showDashboardView() {
        // OPTIMIZATION 1: Clear content immediately without delay
        postTilePane.getChildren().clear();
        
        // OPTIMIZATION 2: Set layout properties quickly
        postTilePane.setAlignment(Pos.CENTER);
        postTilePane.setHgap(15);
        postTilePane.setVgap(15);
        postTilePane.setPadding(new Insets(20));
        
        // OPTIMIZATION 3: Handle empty state immediately
        if (posts == null || posts.isEmpty()) {
            Label noPostsLabel = new Label("No posts available");
            noPostsLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: " + 
                               (themeManager.isDarkMode() ? "#e2e8f0" : "#555555") + ";");
            postTilePane.getChildren().add(noPostsLabel);
            return;
        }
        
        // OPTIMIZATION 4: Load posts asynchronously in batches to prevent UI blocking
        AsyncTaskService.getInstance().executeIOTask(
            () -> {
                // Create post components in background thread
                List<PostCardComponent> postComponents = new ArrayList<>();
                
                // Process posts in smaller batches to prevent blocking
                final int BATCH_SIZE = 5;
                for (int i = 0; i < Math.min(posts.size(), BATCH_SIZE); i++) {
                    Post post = posts.get(i);
                    PostCardComponent postComponent = new PostCardComponent(
                        post, currentUser, postService, this::handleDeletePost, this::handleDownloadPost);
                    postComponents.add(postComponent);
                }
                
                return postComponents;
            },
            // Success callback - add to UI thread
            postComponents -> {
                Platform.runLater(() -> {
                    // Add components in small batches to prevent frame drops
                    for (int i = 0; i < postComponents.size(); i++) {
                        final int index = i;
                        Platform.runLater(() -> {
                            if (index < postComponents.size()) {
                                postTilePane.getChildren().add(postComponents.get(index));
                            }
                        });
                    }
                    
                    // Load remaining posts asynchronously if there are more
                    if (posts.size() > 5) {
                        loadRemainingPostsAsync(5);
                    }
                });
            },
            // Error callback
            throwable -> {
                Platform.runLater(() -> {
                    Label errorLabel = new Label("Error loading posts: " + throwable.getMessage());
                    errorLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #ff4444;");
                    postTilePane.getChildren().add(errorLabel);
                });
            }
        );
    }
    
    /**
     * OPTIMIZED: Load remaining posts in small batches to prevent UI blocking
     */
    private void loadRemainingPostsAsync(int startIndex) {
        if (startIndex >= posts.size()) return;
        
        AsyncTaskService.getInstance().executeIOTask(
            () -> {
                // Load next batch
                final int BATCH_SIZE = 3;
                List<PostCardComponent> batchComponents = new ArrayList<>();
                
                for (int i = startIndex; i < Math.min(posts.size(), startIndex + BATCH_SIZE); i++) {
                    Post post = posts.get(i);
                    PostCardComponent postComponent = new PostCardComponent(
                        post, currentUser, postService, this::handleDeletePost, this::handleDownloadPost);
                    batchComponents.add(postComponent);
                }
                
                return batchComponents;
            },
            batchComponents -> {
                Platform.runLater(() -> {
                    // Add batch components
                    postTilePane.getChildren().addAll(batchComponents);
                    
                    // Continue loading if more posts remain
                    if (startIndex + 3 < posts.size()) {
                        // Small delay to prevent overwhelming the UI thread
                        Timeline delay = new Timeline(new KeyFrame(Duration.millis(50), e -> {
                            loadRemainingPostsAsync(startIndex + 3);
                        }));
                        delay.play();
                    }
                });
            },
            throwable -> {
                LOGGER.log(Level.WARNING, "Error loading post batch", throwable);
            }
        );
    }
    
    /**
     * OPTIMIZED: Handle download with minimal UI impact
     */
    private void handleDownloadPost(Post post) {
        if (post.getAttachments() != null && !post.getAttachments().isEmpty()) {
            String filePath = post.getAttachments().get(0);
            showNotification("Downloading: " + filePath, NotificationManager.NotificationType.INFO);
            
            // Simulate download in background
            AsyncTaskService.getInstance().executeIOTask(
                () -> {
                    // Simulate download time
                    try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    return filePath;
                },
                result -> showNotification("Download complete: " + result, NotificationManager.NotificationType.SUCCESS),
                error -> showNotification("Download failed: " + error.getMessage(), NotificationManager.NotificationType.ERROR)
            );
        } else {
            showNotification("No attachments available for this post", NotificationManager.NotificationType.ERROR);
        }
    }
    
    /**
     * Show timeline view - delegates to TimelineController
     */
    private void showTimelineView() {
        // Clear existing content
        postTilePane.getChildren().clear();
        
        try {
            // Load the Timeline FXML if not already loaded
            if (timelineController == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/gov/civiljoin/view/timeline.fxml"));
                VBox timelineView = loader.load();
                timelineController = loader.getController();
            }
            
            // Setup the timeline with current data
            timelineController.setup(currentUser);
            
            // Add the timeline view to the content area
            postTilePane.getChildren().add(timelineController.getRoot());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading timeline view", e);
            Label errorLabel = new Label("Error loading timeline. Please try again.");
            errorLabel.setStyle("-fx-text-fill: #e74c3c;");
            postTilePane.getChildren().add(errorLabel);
        }
    }
    
    /**
     * Show feedback view - delegates to FeedbackController
     */
    private void showFeedbackView() {
        // Clear existing content
        postTilePane.getChildren().clear();
        
        try {
            // Load the Feedback FXML if not already loaded
            if (feedbackController == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/gov/civiljoin/view/feedback.fxml"));
                VBox feedbackView = loader.load();
                feedbackController = loader.getController();
            }
            
            // Setup the feedback with current user
            feedbackController.setup(currentUser);
            
            // Add the feedback view to the content area
            postTilePane.getChildren().add(feedbackController.getRoot());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading feedback view", e);
            Label errorLabel = new Label("Error loading feedback view. Please try again.");
            errorLabel.setStyle("-fx-text-fill: #e74c3c;");
            postTilePane.getChildren().add(errorLabel);
        }
    }
    
    /**
     * Show settings view
     */
    private void showSettingsView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/gov/civiljoin/view/settings.fxml"));
            StackPane settingsView = loader.load();
            SettingsController settingsController = loader.getController();
            settingsController.setup(currentUser);
            
            // Update the current view
            postTilePane.getChildren().clear();
            postTilePane.getChildren().add(settingsView);
            
            // Store the current controller
            this.settingsController = settingsController;
            this.currentController = settingsController;
            
            // Apply theme to the settings view
            themeManager.applyTheme(settingsView.getScene());
        } catch (IOException e) {
            LOGGER.severe("Error loading settings view: " + e.getMessage());
            e.printStackTrace();
            
            // Show error to user
            Label errorLabel = new Label("Error loading settings view. Please try again.");
            errorLabel.setStyle("-fx-text-fill: #e74c3c;");
            postTilePane.getChildren().add(errorLabel);
        }
    }
    
    /**
     * Show admin view - delegates to admin controllers
     */
    private void showAdminView() {
        // Clear existing content
        postTilePane.getChildren().clear();
        
        try {
            // Load admin view from FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/gov/civiljoin/view/admin/admin.fxml"));
            ScrollPane adminView = loader.load();
            gov.civiljoin.controller.admin.AdminController adminController = loader.getController();
            
            // Setup the admin controller with current user
            adminController.setup(currentUser);
            
            // Add the admin view to the content area
            postTilePane.getChildren().add(adminController.getRoot());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading admin view", e);
            Label errorLabel = new Label("Error loading admin panel. Please try again.");
            errorLabel.setStyle("-fx-text-fill: #e74c3c;");
            postTilePane.getChildren().add(errorLabel);
        }
    }

    // Updated theme handling is now done through the ThemeManager class and CSS
    
    /**
     * Style post cards in the dashboard view
     */
    private void stylePostCards() {
        if (postTilePane == null) return;
        
        // Style all post cards
        for (Node node : postTilePane.getChildren()) {
            if (node instanceof PostCardComponent) {
                PostCardComponent postCard = (PostCardComponent) node;
                themeManager.stylePostCard(postCard);
                
                // Style buttons within the post card
                for (Node childNode : postCard.lookupAll(".button")) {
                    if (childNode instanceof Button) {
                        Button button = (Button) childNode;
                        if (button.getText() != null && button.getText().contains("Delete")) {
                            themeManager.styleDangerButton(button);
                        } else if (button.getText() != null && button.getText().contains("Download")) {
                            button.getStyleClass().add("button");
                        } else {
                            themeManager.stylePrimaryButton(button);
                        }
                    }
                }
            }
        }
    }

    /**
     * Handle theme toggle button action - GLOBAL APPLICATION THEME SWITCH
     */
    private void handleThemeToggle(ActionEvent event) {
        LOGGER.info("Theme toggle button clicked - switching application theme");
        
        // Toggle theme using ThemeManager (this updates all managed scenes)
        boolean isDarkMode = themeManager.toggleTheme();
        
        // Update the toggle button text and style
        themeToggleButton.setText(isDarkMode ? "☀️ Light Mode" : "🌙 Dark Mode");
        themeManager.styleThemeToggleButton(themeToggleButton);
        
        // Apply theme to current scene (ThemeManager will handle all scenes)
        if (themeToggleButton.getScene() != null) {
            Scene currentScene = themeToggleButton.getScene();
            themeManager.applyTheme(currentScene);
            
            LOGGER.info("Applied " + (isDarkMode ? "dark" : "light") + " theme to current scene");
        }
        
        // Show notification to user
        showNotification("Switched to " + (isDarkMode ? "Dark" : "Light") + " Mode", 
                        NotificationManager.NotificationType.INFO);
        
        // Log the theme change
        logAction("Changed theme to " + (isDarkMode ? "dark mode" : "light mode"));
        
        LOGGER.info("Theme toggle completed successfully");
    }
    
    /**
     * OPTIMIZED: Apply theme to essential nodes only to prevent UI blocking
     */
    private void applyThemeToAllNodes(Node node) {
        // OPTIMIZATION: Use AsyncTaskService to prevent UI blocking during theme application
        AsyncTaskService.getInstance().executeIOTask(
            () -> {
                // Do minimal theme application in background
                return node.getClass().getSimpleName();
            },
            nodeType -> {
                // Apply essential styling only on UI thread
                Platform.runLater(() -> {
                    if (node instanceof BorderPane) {
                        applyThemeToEssentialBorderPaneNodes((BorderPane) node);
                    } else if (node instanceof Pane) {
                        applyThemeToEssentialPaneNodes((Pane) node);
                    }
                });
            },
            throwable -> {
                LOGGER.log(Level.WARNING, "Error in async theme application", throwable);
            }
        );
    }
    
    /**
     * OPTIMIZED: Apply theme to essential BorderPane nodes only
     */
    private void applyThemeToEssentialBorderPaneNodes(BorderPane borderPane) {
        // Only style immediate children, no deep recursion
        Node top = borderPane.getTop();
        if (top instanceof Pane) {
            ((Pane) top).getStyleClass().removeAll("light-mode", "dark-mode");
            ((Pane) top).getStyleClass().add(themeManager.isDarkMode() ? "dark-mode" : "light-mode");
        }
        
        Node left = borderPane.getLeft();
        if (left instanceof Pane) {
            ((Pane) left).getStyleClass().removeAll("light-mode", "dark-mode");
            ((Pane) left).getStyleClass().add(themeManager.isDarkMode() ? "dark-mode" : "light-mode");
        }
        
        Node bottom = borderPane.getBottom();
        if (bottom instanceof Pane) {
            ((Pane) bottom).getStyleClass().removeAll("light-mode", "dark-mode");
            ((Pane) bottom).getStyleClass().add(themeManager.isDarkMode() ? "dark-mode" : "light-mode");
        }
    }
    
    /**
     * OPTIMIZED: Apply theme to essential Pane nodes only
     */
    private void applyThemeToEssentialPaneNodes(Pane pane) {
        pane.getStyleClass().removeAll("light-mode", "dark-mode");
        pane.getStyleClass().add(themeManager.isDarkMode() ? "dark-mode" : "light-mode");
        
        // Style post cards if present
        if (pane instanceof PostCardComponent) {
            themeManager.stylePostCard(pane);
        }
    }
    
    /**
     * Log an action in the activity log
     */
    private void logAction(String action) {
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseUtil.getConnection();
            String sql = "INSERT INTO activity_log (user_id, action) VALUES (?, ?)";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, currentUser.getId());
            stmt.setString(2, action);
            stmt.executeUpdate();
        } catch (SQLException e) {
            // Just log, don't show error to user
            System.err.println("Error logging action: " + e.getMessage());
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing resources", e);
            }
        }
    }

    /**
     * Handle create post button action
     */
    @FXML
    private void handleCreatePost(ActionEvent event) {
        VBox createPostForm = new VBox(10);
        createPostForm.setStyle("-fx-background-color: #2d2d2d; -fx-padding: 20;");
        createPostForm.setMaxWidth(400);
        
        TextField titleField = new TextField();
        titleField.setPromptText("Title");
        titleField.setStyle("-fx-background-color: #333333; -fx-text-fill: white;");
        
        TextArea contentArea = new TextArea();
        contentArea.setPromptText("Content");
        contentArea.setPrefRowCount(5);
        contentArea.setStyle("-fx-background-color: #333333; -fx-text-fill: white;");
        
        Label attachmentLabel = new Label("No file selected");
        attachmentLabel.setStyle("-fx-text-fill: white;");
        
        Button attachmentButton = new Button("Upload Attachment");
        attachmentButton.getStyleClass().add("button");
        
        HBox attachmentBox = new HBox(10);
        attachmentBox.getChildren().addAll(attachmentButton, attachmentLabel);
        
        Button submitButton = new Button("Post");
        submitButton.getStyleClass().add("button");
        
        createPostForm.getChildren().addAll(
            new Label("Create New Post"),
            titleField,
            contentArea,
            attachmentBox,
            submitButton
        );
        
        // Clear existing content and show form
        postTilePane.getChildren().clear();
        postTilePane.getChildren().add(createPostForm);
        
        final List<String> attachments = new ArrayList<>();
        
        // Handle attachment selection
        attachmentButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Attachment");
            File file = fileChooser.showOpenDialog(attachmentButton.getScene().getWindow());
            if (file != null) {
                attachmentLabel.setText(file.getName());
                attachments.clear(); // Only one attachment for simplicity
                attachments.add(file.getAbsolutePath());
            }
        });
        
        // Handle post submission
        submitButton.setOnAction(e -> {
            if (titleField.getText().isEmpty() || contentArea.getText().isEmpty()) {
                showNotification("Title and content cannot be empty", NotificationManager.NotificationType.ERROR);
                return;
            }
            
            Post newPost = new Post();
            newPost.setTitle(titleField.getText());
            newPost.setContent(contentArea.getText());
            newPost.setUserId(currentUser.getId());
            newPost.setCreatedAt(LocalDateTime.now());
            
            if (!attachments.isEmpty()) {
                newPost.setAttachments(attachments);
            }
            
            // Add to database via service
            if (postService.addPost(newPost)) {
                // Add new post to list and refresh
                posts.add(0, newPost);
                showDashboardView(); // Refresh view
                showNotification("Post created successfully", NotificationManager.NotificationType.SUCCESS);
            } else {
                showNotification("Could not create post. Please try again later", NotificationManager.NotificationType.ERROR);
            }
        });
    }
    
    /**
     * Handle post deletion
     */
    private void handleDeletePost(Post post) {
        if (post.getUserId() != currentUser.getId()) {
            showNotification("You can only delete your own posts", NotificationManager.NotificationType.ERROR);
            return;
        }
        
        if (postService.deletePost(post.getId())) {
            posts.remove(post);
            showDashboardView(); // Refresh view
            showNotification("Post deleted successfully", NotificationManager.NotificationType.SUCCESS);
        } else {
            showNotification("Could not delete post. Please try again later", NotificationManager.NotificationType.ERROR);
        }
    }
} 