package gov.civiljoin.controller;

import gov.civiljoin.model.SystemActivity;
import gov.civiljoin.model.SystemActivity.ActivityType;
import gov.civiljoin.model.SystemActivity.Severity;
import gov.civiljoin.model.User;
import gov.civiljoin.service.SystemActivityService;
import gov.civiljoin.util.NotificationManager;
import gov.civiljoin.util.ThemeManager;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Enhanced Timeline Controller - Real-time Activity Stream
 * Shows live system activities instead of static posts
 */
public class TimelineController implements Initializable {
    private static final Logger LOGGER = Logger.getLogger(TimelineController.class.getName());
    private final ThemeManager themeManager = ThemeManager.getInstance();
    private final SystemActivityService activityService = new SystemActivityService();
    private final NotificationManager notificationManager = NotificationManager.getInstance();

    @FXML private VBox contentContainer;
    @FXML private HBox filterContainer;
    @FXML private Button refreshButton;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> typeFilter;
    @FXML private ComboBox<String> severityFilter;
    @FXML private ToggleButton autoRefreshToggle;
    @FXML private Label statusLabel;
    @FXML private ScrollPane activitiesScrollPane;
    @FXML private VBox activitiesContainer;
    
    private User currentUser;
    private List<SystemActivity> activities = new ArrayList<>();
    private Timeline autoRefreshTimeline;
    private boolean isAutoRefreshEnabled = true;
    private int autoRefreshInterval = 30; // seconds

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initializeTable();
        setupFilters();
        setupAutoRefresh();
        applyThemeToComponents();
        loadActivities();
    }

    /**
     * Initialize the database table
     */
    private void initializeTable() {
        try {
            activityService.initializeTable();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize activities table", e);
        }
    }

    /**
     * Setup filter components
     */
    private void setupFilters() {
        // Activity type filter
        typeFilter.getItems().addAll(
            "All Activities",
            "User Actions", 
            "System Events",
            "Security Events",
            "Admin Actions",
            "Announcements"
        );
        typeFilter.setValue("All Activities");
        typeFilter.setOnAction(e -> filterActivities());

        // Severity filter
        severityFilter.getItems().addAll(
            "All Severities",
            "Critical Only",
            "High Priority",
            "Medium & Up",
            "All Levels"
        );
        severityFilter.setValue("All Severities");
        severityFilter.setOnAction(e -> filterActivities());

        // Search field
        searchField.setPromptText("🔍 Search activities...");
        searchField.textProperty().addListener((obs, oldText, newText) -> filterActivities());

        // Auto-refresh toggle
        autoRefreshToggle.setText("🟢 Live");
        autoRefreshToggle.setSelected(true);
        autoRefreshToggle.setOnAction(e -> toggleAutoRefresh());

        // Refresh button
        refreshButton.setText("🔄 Refresh");
        refreshButton.setOnAction(e -> loadActivities());
    }

    /**
     * Setup auto-refresh functionality
     */
    private void setupAutoRefresh() {
        autoRefreshTimeline = new Timeline(new KeyFrame(
            Duration.seconds(autoRefreshInterval),
            e -> {
                if (isAutoRefreshEnabled) {
                    loadActivities();
                    updateStatusLabel("Last updated: " + java.time.LocalTime.now().format(
                        DateTimeFormatter.ofPattern("HH:mm:ss")));
                }
            }
        ));
        autoRefreshTimeline.setCycleCount(Animation.INDEFINITE);
        autoRefreshTimeline.play();
    }

    /**
     * Toggle auto-refresh on/off
     */
    private void toggleAutoRefresh() {
        isAutoRefreshEnabled = autoRefreshToggle.isSelected();
        if (isAutoRefreshEnabled) {
            autoRefreshToggle.setText("🟢 Live");
            autoRefreshTimeline.play();
            updateStatusLabel("Auto-refresh enabled (" + autoRefreshInterval + "s interval)");
        } else {
            autoRefreshToggle.setText("⏸️ Paused");
            autoRefreshTimeline.stop();
            updateStatusLabel("Auto-refresh paused");
        }
    }

    /**
     * Update status label
     */
    private void updateStatusLabel(String message) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(message);
            }
        });
    }

    /**
     * Apply theme styling to components
     */
    private void applyThemeToComponents() {
        String containerStyle = themeManager.isDarkMode() 
            ? "-fx-background-color: #1a1a1a;" 
            : "-fx-background-color: #f8f9fa;";
        contentContainer.setStyle(containerStyle);

        String buttonStyle = themeManager.isDarkMode()
            ? "-fx-background-color: #2a2a2a; -fx-text-fill: #ffffff; -fx-border-color: #444444;"
            : "-fx-background-color: #ffffff; -fx-text-fill: #000000; -fx-border-color: #cccccc;";
        
        if (refreshButton != null) refreshButton.setStyle(buttonStyle);
        if (autoRefreshToggle != null) autoRefreshToggle.setStyle(buttonStyle);
    }

    /**
     * Set up the controller with user data
     */
    public void setup(User user) {
        this.currentUser = user;
        loadActivities();
    }

    /**
     * Load activities from database
     */
    private void loadActivities() {
        Platform.runLater(() -> {
            try {
                // Get recent activities
                List<SystemActivity> recentActivities = activityService.getRecentActivities(50);
                this.activities = recentActivities;
                
                // Log current user activity
                if (currentUser != null) {
                    activityService.logActivity(
                        currentUser.getId(), 
                        ActivityType.USER_LOGIN, 
                        "Viewed timeline activities"
                    );
                }
                
                displayActivities();
                updateStatusLabel("Loaded " + activities.size() + " activities");
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to load activities", e);
                updateStatusLabel("Error loading activities");
                if (notificationManager != null) {
                    notificationManager.showNotification(
                        "Failed to load timeline activities", 
                        NotificationManager.NotificationType.ERROR
                    );
                }
            }
        });
    }

    /**
     * Filter activities based on current filter settings
     */
    private void filterActivities() {
        if (activities == null) return;

        List<SystemActivity> filteredActivities = new ArrayList<>(activities);
        
        // Filter by type
        String typeFilterValue = typeFilter.getValue();
        if (typeFilterValue != null && !typeFilterValue.equals("All Activities")) {
            filteredActivities.removeIf(activity -> !matchesTypeFilter(activity, typeFilterValue));
        }
        
        // Filter by severity
        String severityFilterValue = severityFilter.getValue();
        if (severityFilterValue != null && !severityFilterValue.equals("All Severities")) {
            Severity minSeverity = getMinSeverityFromFilter(severityFilterValue);
            if (minSeverity != null) {
                filteredActivities.removeIf(activity -> 
                    activity.getSeverity().ordinal() < minSeverity.ordinal());
            }
        }
        
        // Filter by search text
        String searchText = searchField.getText();
        if (searchText != null && !searchText.trim().isEmpty()) {
            filteredActivities.removeIf(activity -> 
                !activity.getDescription().toLowerCase().contains(searchText.toLowerCase()) &&
                (activity.getUsername() == null || !activity.getUsername().toLowerCase().contains(searchText.toLowerCase())));
        }
        
        displayFilteredActivities(filteredActivities);
    }

    /**
     * Check if activity matches type filter
     */
    private boolean matchesTypeFilter(SystemActivity activity, String filter) {
        return switch (filter) {
            case "User Actions" -> Arrays.asList(
                ActivityType.USER_REGISTRATION, ActivityType.USER_LOGIN, ActivityType.USER_LOGOUT,
                ActivityType.POST_CREATED, ActivityType.COMMENT_ADDED
            ).contains(activity.getActivityType());
            
            case "System Events" -> Arrays.asList(
                ActivityType.SYSTEM_ANNOUNCEMENT, ActivityType.POLICY_CHANGE
            ).contains(activity.getActivityType());
            
            case "Security Events" -> Arrays.asList(
                ActivityType.SECURITY_EVENT, ActivityType.EMERGENCY_ALERT
            ).contains(activity.getActivityType());
            
            case "Admin Actions" -> Arrays.asList(
                ActivityType.ADMIN_ACTION, ActivityType.KEY_GENERATED
            ).contains(activity.getActivityType());
            
            case "Announcements" -> Arrays.asList(
                ActivityType.SYSTEM_ANNOUNCEMENT, ActivityType.EMERGENCY_ALERT, ActivityType.POLICY_CHANGE
            ).contains(activity.getActivityType());
            
            default -> true;
        };
    }

    /**
     * Get minimum severity from filter value
     */
    private Severity getMinSeverityFromFilter(String filter) {
        return switch (filter) {
            case "Critical Only" -> Severity.CRITICAL;
            case "High Priority" -> Severity.HIGH;
            case "Medium & Up" -> Severity.MEDIUM;
            default -> null;
        };
    }

    /**
     * Display activities in the timeline
     */
    private void displayActivities() {
        displayFilteredActivities(this.activities);
    }

    /**
     * Display filtered activities
     */
    private void displayFilteredActivities(List<SystemActivity> activitiesToShow) {
        activitiesContainer.getChildren().clear();
        
        if (activitiesToShow.isEmpty()) {
            createEmptyState();
            return;
        }

        // Create header
        createTimelineHeader(activitiesToShow.size());
        
        // Create activity items
        for (SystemActivity activity : activitiesToShow) {
            VBox activityCard = createActivityCard(activity);
            activitiesContainer.getChildren().add(activityCard);
        }
    }

    /**
     * Create timeline header with statistics
     */
    private void createTimelineHeader(int activityCount) {
        HBox headerBox = new HBox(15);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setPadding(new Insets(15, 20, 15, 20));
        headerBox.setMaxWidth(800);
        
        String headerStyle = themeManager.isDarkMode() 
            ? "-fx-background-color: rgba(42, 42, 42, 0.95); -fx-background-radius: 12; -fx-border-color: rgba(255,255,255,0.1); -fx-border-width: 1; -fx-border-radius: 12;"
            : "-fx-background-color: rgba(255, 255, 255, 0.95); -fx-background-radius: 12; -fx-border-color: rgba(0,0,0,0.1); -fx-border-width: 1; -fx-border-radius: 12;";
        headerBox.setStyle(headerStyle);

        VBox headerTextBox = new VBox(5);
        Label timelineHeader = new Label("📡 Live Activity Stream");
        timelineHeader.setFont(Font.font("System", FontWeight.BOLD, 20));
        
        Label activityCountLabel = new Label(activityCount + " recent activities");
        
        String textStyle = themeManager.isDarkMode() ? "-fx-text-fill: #ffffff;" : "-fx-text-fill: #000000;";
        String subTextStyle = themeManager.isDarkMode() ? "-fx-text-fill: #cccccc;" : "-fx-text-fill: #666666;";
        
        timelineHeader.setStyle(textStyle);
        activityCountLabel.setStyle(subTextStyle);
        
        headerTextBox.getChildren().addAll(timelineHeader, activityCountLabel);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Live status indicator
        Label liveIndicator = new Label(isAutoRefreshEnabled ? "🟢 LIVE" : "⏸️ PAUSED");
        liveIndicator.setStyle(textStyle + "-fx-font-weight: bold;");
        
        headerBox.getChildren().addAll(headerTextBox, spacer, liveIndicator);
        activitiesContainer.getChildren().add(headerBox);
    }

    /**
     * Create individual activity card
     */
    private VBox createActivityCard(SystemActivity activity) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(15, 20, 15, 20));
        card.setMaxWidth(800);
        
        String cardStyle = themeManager.isDarkMode() 
            ? "-fx-background-color: rgba(35, 35, 35, 0.95); -fx-background-radius: 10; -fx-border-color: " + activity.getSeverity().getColor() + "; -fx-border-width: 0 0 0 4; -fx-border-radius: 10;"
            : "-fx-background-color: rgba(255, 255, 255, 0.95); -fx-background-radius: 10; -fx-border-color: " + activity.getSeverity().getColor() + "; -fx-border-width: 0 0 0 4; -fx-border-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0, 0, 2);";
        card.setStyle(cardStyle);

        // Activity header
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        Label iconLabel = new Label(activity.getActivityType().getIcon());
        iconLabel.setFont(Font.font(16));
        
        Label severityLabel = new Label(activity.getSeverity().getIcon());
        severityLabel.setFont(Font.font(14));
        
        Label timeLabel = new Label(activity.getRelativeTime());
        timeLabel.setStyle((themeManager.isDarkMode() ? "-fx-text-fill: #cccccc;" : "-fx-text-fill: #666666;") + " -fx-font-size: 12px;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        headerBox.getChildren().addAll(iconLabel, severityLabel, spacer, timeLabel);
        
        // Activity description
        TextFlow descriptionFlow = new TextFlow();
        Text descriptionText = new Text(activity.getDisplayText());
        descriptionText.setStyle((themeManager.isDarkMode() ? "-fx-fill: #ffffff;" : "-fx-fill: #000000;") + " -fx-font-size: 14px; -fx-font-weight: 500;");
        descriptionFlow.getChildren().add(descriptionText);
        
        // Activity details
        if (activity.getIpAddress() != null || (activity.getMetadata() != null && !activity.getMetadata().isEmpty())) {
            Label detailsLabel = new Label("📍 " + (activity.getIpAddress() != null ? activity.getIpAddress() : "System"));
            detailsLabel.setStyle((themeManager.isDarkMode() ? "-fx-text-fill: #999999;" : "-fx-text-fill: #777777;") + " -fx-font-size: 11px;");
            card.getChildren().addAll(headerBox, descriptionFlow, detailsLabel);
        } else {
            card.getChildren().addAll(headerBox, descriptionFlow);
        }
        
        // Click handler for detailed view
        card.setOnMouseClicked(e -> showActivityDetails(activity));
        card.setCursor(javafx.scene.Cursor.HAND);
        
        return card;
    }

    /**
     * Show detailed view of activity
     */
    private void showActivityDetails(SystemActivity activity) {
        if (notificationManager != null) {
            String details = String.format(
                "Activity Details:\n\n" +
                "Type: %s\n" +
                "User: %s\n" +
                "Severity: %s\n" +
                "Time: %s\n" +
                "Description: %s",
                activity.getActivityType().getDisplayName(),
                activity.getUsername() != null ? activity.getUsername() : "System",
                activity.getSeverity().name(),
                activity.getCreatedAt() != null ? activity.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "Unknown",
                activity.getDescription()
            );
            
            notificationManager.showNotification(details, NotificationManager.NotificationType.INFO);
        }
    }

    /**
     * Create empty state when no activities match filters
     */
    private void createEmptyState() {
        VBox emptyStateBox = new VBox(15);
        emptyStateBox.setAlignment(Pos.CENTER);
        emptyStateBox.setPadding(new Insets(40));
        emptyStateBox.setMaxWidth(600);
        
        String emptyStyle = themeManager.isDarkMode() 
            ? "-fx-background-color: rgba(35, 35, 35, 0.8); -fx-background-radius: 12;"
            : "-fx-background-color: rgba(248, 249, 250, 0.8); -fx-background-radius: 12;";
        emptyStateBox.setStyle(emptyStyle);

        Label emptyIcon = new Label("📭");
        emptyIcon.setFont(Font.font(48));
        
        Label emptyLabel = new Label("No activities match your filters");
        emptyLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        
        Label emptySubLabel = new Label("Try adjusting your filter settings or wait for new activities");
        
        String textStyle = themeManager.isDarkMode() ? "-fx-text-fill: #ffffff;" : "-fx-text-fill: #000000;";
        String subTextStyle = themeManager.isDarkMode() ? "-fx-text-fill: #cccccc;" : "-fx-text-fill: #666666;";
        
        emptyLabel.setStyle(textStyle);
        emptySubLabel.setStyle(subTextStyle);
        
        Button clearFiltersButton = new Button("Clear All Filters");
        clearFiltersButton.setOnAction(e -> clearFilters());
        clearFiltersButton.setStyle(themeManager.isDarkMode()
            ? "-fx-background-color: #4a5568; -fx-text-fill: white; -fx-background-radius: 6;"
            : "-fx-background-color: #000000; -fx-text-fill: white; -fx-background-radius: 6;");
        
        emptyStateBox.getChildren().addAll(emptyIcon, emptyLabel, emptySubLabel, clearFiltersButton);
        activitiesContainer.getChildren().add(emptyStateBox);
    }

    /**
     * Clear all filters
     */
    private void clearFilters() {
        typeFilter.setValue("All Activities");
        severityFilter.setValue("All Severities");
        searchField.clear();
        filterActivities();
    }

    /**
     * Update theme when changed
     */
    public void updateTheme() {
        applyThemeToComponents();
        displayActivities(); // Refresh display with new theme
    }

    /**
     * Get root node for embedding in other views
     */
    public Node getRoot() {
        return contentContainer;
    }

    /**
     * Cleanup resources when controller is destroyed
     */
    public void shutdown() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
        }
    }
} 