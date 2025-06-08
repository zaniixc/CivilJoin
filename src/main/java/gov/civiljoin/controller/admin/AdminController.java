package gov.civiljoin.controller.admin;

import gov.civiljoin.model.User;
import gov.civiljoin.service.AdminSecurityService;
import gov.civiljoin.util.DatabaseUtil;
import gov.civiljoin.util.NotificationManager;
import gov.civiljoin.util.ThemeManager;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Arrays;

/**
 * Enhanced Admin Controller with Advanced Security Features
 * Features: Real-time monitoring, Emergency response, Comprehensive auditing
 */
public class AdminController implements Initializable {
    private static final Logger LOGGER = Logger.getLogger(AdminController.class.getName());
    private final ThemeManager themeManager = ThemeManager.getInstance();
    private final AdminSecurityService securityService = AdminSecurityService.getInstance();

    // FXML Components
    @FXML private ScrollPane rootScrollPane;
    @FXML private VBox adminContainer;
    @FXML private TabPane tabPane;
    @FXML private Tab securityTab;
    @FXML private Tab userTab;
    @FXML private Tab keyManagementTab;
    @FXML private Tab activityTab;
    
    // Real-time security monitoring components
    private VBox securityDashboard;
    private Label emergencyStatusLabel;
    private Label systemHealthLabel;
    private VBox threatsList;
    private TableView<SecurityEventDisplay> securityEventsTable;
    private Timeline refreshTimeline;
    
    private User currentUser;
    
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        applyTheme();
        setupSecurityMonitoring();
    }
    
    public void setup(User user) {
        this.currentUser = user;
        
        // Enhanced security check
        if (!securityService.checkPermission(user, AdminSecurityService.SecurityAction.ADMIN_ACCESS, "panel")) {
            showAccessDenied();
            return;
        }
        
        // Log admin access
        securityService.logSecurityEvent(user.getId(), AdminSecurityService.SecurityAction.ADMIN_ACCESS,
            "ADMIN_PANEL_ACCESS", AdminSecurityService.ThreatLevel.LOW,
            "Admin panel accessed by " + user.getUsername());
        
        setupEnhancedAdminPanel();
        startRealTimeMonitoring();
    }
    
    /**
     * Setup enhanced admin panel with security dashboard
     */
    private void setupEnhancedAdminPanel() {
        adminContainer.getChildren().clear();
        
        // Create header with emergency controls
        setupSecurityHeader();
        
        // Setup tabs
        setupSecurityDashboardTab();
        setupUserManagementTab();
        setupKeyManagementTab();
        setupActivityAuditTab();
        
        adminContainer.getChildren().add(tabPane);
    }
    
    /**
     * Setup security header with emergency controls
     */
    private void setupSecurityHeader() {
        VBox header = new VBox(15);
        header.setPadding(new Insets(20));
        header.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 10;");
        
        // Title and status
        HBox titleRow = new HBox(20);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label("Security Command Center");
        titleLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 24px; -fx-font-weight: bold;");
        
        emergencyStatusLabel = new Label();
        updateEmergencyStatus();
        
        titleRow.getChildren().addAll(titleLabel, emergencyStatusLabel);
        
        // System health and emergency controls
        HBox controlsRow = new HBox(15);
        controlsRow.setAlignment(Pos.CENTER_LEFT);
        
        systemHealthLabel = new Label();
        updateSystemHealth();
        
        // Emergency controls (owner only)
        if (currentUser.getRole() == User.Role.OWNER) {
            Button emergencyButton = createEmergencyButton();
            Button lockdownButton = createLockdownButton();
            controlsRow.getChildren().addAll(systemHealthLabel, emergencyButton, lockdownButton);
        } else {
            controlsRow.getChildren().add(systemHealthLabel);
        }
        
        header.getChildren().addAll(titleRow, controlsRow);
        adminContainer.getChildren().add(header);
    }
    
    /**
     * Setup real-time security dashboard
     */
    private void setupSecurityDashboardTab() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        
        // Security overview cards
        HBox overviewCards = new HBox(15);
        overviewCards.getChildren().addAll(
            createSecurityCard("Active Threats", "0", "#ff4444"),
            createSecurityCard("Recent Events", "0", "#ffaa00"),
            createSecurityCard("System Health", "100%", "#00ff88")
        );
        
        // Active threats list
        Label threatsLabel = new Label("Active Security Threats");
        threatsLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 16px; -fx-font-weight: bold;");
        
        threatsList = new VBox(10);
        threatsList.setPadding(new Insets(15));
        threatsList.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 8;");
        
        // Security events table
        Label eventsLabel = new Label("Recent Security Events");
        eventsLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 16px; -fx-font-weight: bold;");
        
        securityEventsTable = createSecurityEventsTable();
        
        content.getChildren().addAll(overviewCards, threatsLabel, threatsList, eventsLabel, securityEventsTable);
        
        securityTab.setContent(new ScrollPane(content));
    }
    
    /**
     * Setup enhanced user management with security profiles
     */
    private void setupUserManagementTab() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        
        // User security overview
        Label headerLabel = new Label("User Security Management");
        headerLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 18px; -fx-font-weight: bold;");
        
        // Users table with security info
        TableView<UserSecurityDisplay> usersTable = createUsersSecurityTable();
        
        // Security actions panel
        VBox actionsPanel = createSecurityActionsPanel();
        
        content.getChildren().addAll(headerLabel, usersTable, actionsPanel);
        userTab.setContent(new ScrollPane(content));
    }
    
    /**
     * Setup key management tab for generating and managing registration keys
     */
    private void setupKeyManagementTab() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        
        // Header
        Label headerLabel = new Label("Registration Key Management");
        headerLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 18px; -fx-font-weight: bold;");
        
        // Key generation section
        VBox keyGenSection = createKeyGenerationSection();
        
        // Existing keys table
        VBox existingKeysSection = createExistingKeysSection();
        
        content.getChildren().addAll(headerLabel, keyGenSection, existingKeysSection);
        keyManagementTab.setContent(new ScrollPane(content));
    }
    
    /**
     * Setup comprehensive activity audit
     */
    private void setupActivityAuditTab() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        
        Label headerLabel = new Label("Security Audit Trail");
        headerLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 18px; -fx-font-weight: bold;");
        
        // Audit filters
        HBox filters = createAuditFilters();
        
        // Detailed audit table
        TableView<AuditEntry> auditTable = createAuditTable();
        
        content.getChildren().addAll(headerLabel, filters, auditTable);
        activityTab.setContent(new ScrollPane(content));
    }
    
    /**
     * Start real-time monitoring updates
     */
    private void startRealTimeMonitoring() {
        // Update every 5 seconds
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(5), e -> updateSecurityDashboard()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
        this.refreshTimeline = timeline;
    }
    
    /**
     * Update security dashboard with real-time data
     */
    private void updateSecurityDashboard() {
        Platform.runLater(() -> {
            try {
                Map<String, Object> dashboard = securityService.getSecurityDashboard();
                
                updateEmergencyStatus();
                updateSystemHealth();
                updateActiveThreats(dashboard);
                updateSecurityEvents(dashboard);
                
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error updating security dashboard", e);
            }
        });
    }
    
    // UI Creation Helper Methods
    
    private Button createEmergencyButton() {
        Button button = new Button("Emergency Response");
        button.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20;");
        button.setOnAction(e -> showEmergencyOptions());
        return button;
    }
    
    private Button createLockdownButton() {
        Button button = new Button("System Lockdown");
        button.setStyle("-fx-background-color: #ff8800; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20;");
        button.setOnAction(e -> initiateSystemLockdown());
        return button;
    }
    
    private VBox createSecurityCard(String title, String value, String color) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 8; -fx-border-color: " + color + "; -fx-border-width: 1;");
        card.setPrefWidth(200);
        
        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 28px; -fx-font-weight: bold;");
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 14px;");
        
        card.getChildren().addAll(valueLabel, titleLabel);
        return card;
    }
    
    private TableView<SecurityEventDisplay> createSecurityEventsTable() {
        TableView<SecurityEventDisplay> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(300);
        
        TableColumn<SecurityEventDisplay, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        timeCol.setPrefWidth(120);
        
        TableColumn<SecurityEventDisplay, String> userCol = new TableColumn<>("User");
        userCol.setCellValueFactory(new PropertyValueFactory<>("username"));
        timeCol.setPrefWidth(100);
        
        TableColumn<SecurityEventDisplay, String> eventCol = new TableColumn<>("Event");
        eventCol.setCellValueFactory(new PropertyValueFactory<>("eventType"));
        eventCol.setPrefWidth(150);
        
        TableColumn<SecurityEventDisplay, String> severityCol = new TableColumn<>("Severity");
        severityCol.setCellValueFactory(new PropertyValueFactory<>("severity"));
        severityCol.setPrefWidth(80);
        
        TableColumn<SecurityEventDisplay, String> detailsCol = new TableColumn<>("Details");
        detailsCol.setCellValueFactory(new PropertyValueFactory<>("details"));
        
        table.getColumns().addAll(Arrays.asList(timeCol, userCol, eventCol, severityCol, detailsCol));
        return table;
    }
    
    // Event Handlers
    
    private void showEmergencyOptions() {
        NotificationManager.getInstance().showConfirmation(
            "Activate Emergency Mode? This will lock out all non-owner users and restrict system access.",
            () -> {
                NotificationManager.getInstance().showTextConfirmation(
                    "Enter emergency reason:",
                    "EMERGENCY",
                    () -> securityService.activateEmergencyMode(currentUser, "Manual activation via admin panel")
                );
            }
        );
    }
    
    private void initiateSystemLockdown() {
        securityService.executeSecurityAction(currentUser, "SYSTEM_LOCKDOWN", new HashMap<>());
    }
    
    // Update Methods
    
    private void updateEmergencyStatus() {
        Map<String, Object> dashboard = securityService.getSecurityDashboard();
        boolean emergencyMode = (boolean) dashboard.get("emergencyMode");
        
        if (emergencyMode) {
            emergencyStatusLabel.setText("🚨 EMERGENCY MODE ACTIVE");
            emergencyStatusLabel.setStyle("-fx-text-fill: #ff4444; -fx-font-weight: bold; -fx-background-color: rgba(255,68,68,0.2); -fx-padding: 8 12; -fx-background-radius: 4;");
        } else {
            emergencyStatusLabel.setText("✅ System Normal");
            emergencyStatusLabel.setStyle("-fx-text-fill: #00ff88; -fx-font-weight: bold; -fx-background-color: rgba(0,255,136,0.2); -fx-padding: 8 12; -fx-background-radius: 4;");
        }
    }
    
    private void updateSystemHealth() {
        Map<String, Object> dashboard = securityService.getSecurityDashboard();
        double health = (double) dashboard.get("systemHealth");
        
        String healthText = String.format("System Health: %.1f%%", health);
        String color = health > 80 ? "#00ff88" : health > 50 ? "#ffaa00" : "#ff4444";
        
        systemHealthLabel.setText(healthText);
        systemHealthLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
    }
    
    private void updateActiveThreats(Map<String, Object> dashboard) {
        // Implementation for updating threats display
    }
    
    private void updateSecurityEvents(Map<String, Object> dashboard) {
        // Implementation for updating events table
    }
    
    // Helper classes and methods
    
    private void showAccessDenied() {
        adminContainer.getChildren().clear();
        
        Label deniedLabel = new Label("Access Denied - Insufficient Privileges");
        deniedLabel.setStyle("-fx-text-fill: #ff4444; -fx-font-size: 18px; -fx-font-weight: bold;");
        adminContainer.getChildren().add(deniedLabel);
        
        NotificationManager.getInstance().showNotification(
            "Access denied to admin panel - insufficient privileges",
            NotificationManager.NotificationType.ERROR
        );
    }
    
    private void applyTheme() {
        if (themeManager != null) {
            String theme = themeManager.isDarkMode() ? "dark" : "light";
            adminContainer.getStyleClass().add("admin-container-" + theme);
            rootScrollPane.getStyleClass().add("admin-scroll-" + theme);
            
            String bgColor = themeManager.isDarkMode() ? "#1a1a1a" : "#ffffff";
            adminContainer.setStyle("-fx-background-color: " + bgColor + ";");
            rootScrollPane.setStyle("-fx-background-color: " + bgColor + ";");
        }
    }
    
    private void setupSecurityMonitoring() {
        // Initialize security monitoring components
    }
    
    public Node getRoot() {
        return rootScrollPane;
    }
    
    public void cleanup() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
    }
    
    // Data classes for display
    public static class SecurityEventDisplay {
        private final String timestamp;
        private final String username;
        private final String eventType;
        private final String severity;
        private final String details;
        
        public SecurityEventDisplay(String timestamp, String username, String eventType, String severity, String details) {
            this.timestamp = timestamp;
            this.username = username;
            this.eventType = eventType;
            this.severity = severity;
            this.details = details;
        }
        
        // Getters for TableView
        public String getTimestamp() { return timestamp; }
        public String getUsername() { return username; }
        public String getEventType() { return eventType; }
        public String getSeverity() { return severity; }
        public String getDetails() { return details; }
    }
    
    public static class UserSecurityDisplay {
        // Implementation for user security display
    }
    
    public static class AuditEntry {
        // Implementation for audit entries
    }
    
    // Helper methods that were missing
    
    private TableView<UserSecurityDisplay> createUsersSecurityTable() {
        TableView<UserSecurityDisplay> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(400);
        
        // Basic columns for now
        TableColumn<UserSecurityDisplay, String> usernameCol = new TableColumn<>("Username");
        TableColumn<UserSecurityDisplay, String> roleCol = new TableColumn<>("Role");
        TableColumn<UserSecurityDisplay, String> statusCol = new TableColumn<>("Status");
        
        table.getColumns().addAll(Arrays.asList(usernameCol, roleCol, statusCol));
        return table;
    }
    
    private VBox createSecurityActionsPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 8;");
        
        Label titleLabel = new Label("Security Actions");
        titleLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 16px; -fx-font-weight: bold;");
        
        HBox buttonRow = new HBox(10);
        Button forceLogoutButton = new Button("Force Logout All");
        Button clearThreatsButton = new Button("Clear Threats");
        
        forceLogoutButton.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white;");
        clearThreatsButton.setStyle("-fx-background-color: #00ff88; -fx-text-fill: black;");
        
        forceLogoutButton.setOnAction(e -> {
            if (currentUser.getRole() == User.Role.OWNER) {
                securityService.executeSecurityAction(currentUser, "FORCE_LOGOUT_ALL", new HashMap<>());
            }
        });
        
        clearThreatsButton.setOnAction(e -> {
            if (currentUser.getRole() == User.Role.OWNER) {
                securityService.executeSecurityAction(currentUser, "CLEAR_THREATS", new HashMap<>());
            }
        });
        
        buttonRow.getChildren().addAll(forceLogoutButton, clearThreatsButton);
        panel.getChildren().addAll(titleLabel, buttonRow);
        
        return panel;
    }
    
    private HBox createAuditFilters() {
        HBox filters = new HBox(15);
        filters.setAlignment(Pos.CENTER_LEFT);
        
        ComboBox<String> severityFilter = new ComboBox<>();
        severityFilter.getItems().addAll("All", "LOW", "MEDIUM", "HIGH", "CRITICAL", "EMERGENCY");
        severityFilter.setValue("All");
        
        ComboBox<String> timeFilter = new ComboBox<>();
        timeFilter.getItems().addAll("Last Hour", "Last 24 Hours", "Last Week", "Last Month");
        timeFilter.setValue("Last 24 Hours");
        
        Button refreshButton = new Button("Refresh");
        refreshButton.setStyle("-fx-background-color: #0066cc; -fx-text-fill: white;");
        
        filters.getChildren().addAll(
            new Label("Severity:"), severityFilter,
            new Label("Time:"), timeFilter,
            refreshButton
        );
        
        return filters;
    }
    
    private TableView<AuditEntry> createAuditTable() {
        TableView<AuditEntry> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(400);
        
        // Basic columns for audit trail
        TableColumn<AuditEntry, String> timestampCol = new TableColumn<>("Timestamp");
        TableColumn<AuditEntry, String> userCol = new TableColumn<>("User");
        TableColumn<AuditEntry, String> actionCol = new TableColumn<>("Action");
        TableColumn<AuditEntry, String> resourceCol = new TableColumn<>("Resource");
        TableColumn<AuditEntry, String> resultCol = new TableColumn<>("Result");
        
        table.getColumns().addAll(Arrays.asList(timestampCol, userCol, actionCol, resourceCol, resultCol));
        return table;
    }
    
    /**
     * Create key generation section
     */
    private VBox createKeyGenerationSection() {
        VBox section = new VBox(15);
        section.setPadding(new Insets(20));
        section.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 8; -fx-border-color: rgba(255,255,255,0.1); -fx-border-width: 1; -fx-border-radius: 8;");
        
        Label titleLabel = new Label("Generate New Registration Keys");
        titleLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 16px; -fx-font-weight: bold;");
        
        // Key type selection
        HBox keyTypeRow = new HBox(15);
        keyTypeRow.setAlignment(Pos.CENTER_LEFT);
        
        Label typeLabel = new Label("Key Type:");
        typeLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 14px;");
        
        ComboBox<String> keyTypeCombo = new ComboBox<>();
        keyTypeCombo.getItems().addAll("USER", "ADMIN", "MODERATOR", "TEMPORARY");
        keyTypeCombo.setValue("USER");
        keyTypeCombo.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #ffffff;");
        
        keyTypeRow.getChildren().addAll(typeLabel, keyTypeCombo);
        
        // Number of keys to generate
        HBox countRow = new HBox(15);
        countRow.setAlignment(Pos.CENTER_LEFT);
        
        Label countLabel = new Label("Number of Keys:");
        countLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 14px;");
        
        ComboBox<Integer> countCombo = new ComboBox<>();
        countCombo.getItems().addAll(1, 5, 10, 25, 50);
        countCombo.setValue(1);
        countCombo.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #ffffff;");
        
        countRow.getChildren().addAll(countLabel, countCombo);
        
        // Description field
        HBox descRow = new HBox(15);
        descRow.setAlignment(Pos.CENTER_LEFT);
        
        Label descLabel = new Label("Description:");
        descLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 14px;");
        
        TextField descField = new TextField();
        descField.setPromptText("Optional description for these keys");
        descField.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #ffffff; -fx-prompt-text-fill: rgba(255,255,255,0.5);");
        descField.setPrefWidth(300);
        
        descRow.getChildren().addAll(descLabel, descField);
        
        // Generate button
        Button generateButton = new Button("Generate Keys");
        generateButton.setStyle("-fx-background-color: #00ff88; -fx-text-fill: #000000; -fx-font-weight: bold; -fx-padding: 12 24; -fx-background-radius: 6;");
        generateButton.setOnAction(e -> generateKeys(keyTypeCombo.getValue(), countCombo.getValue(), descField.getText()));
        
        // Results area
        VBox resultsArea = new VBox(10);
        resultsArea.setVisible(false);
        resultsArea.setStyle("-fx-background-color: #2a2a2a; -fx-padding: 15; -fx-background-radius: 6;");
        
        section.getChildren().addAll(titleLabel, keyTypeRow, countRow, descRow, generateButton, resultsArea);
        
        return section;
    }
    
    /**
     * Create existing keys management section
     */
    private VBox createExistingKeysSection() {
        VBox section = new VBox(15);
        section.setPadding(new Insets(20));
        section.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 8; -fx-border-color: rgba(255,255,255,0.1); -fx-border-width: 1; -fx-border-radius: 8;");
        
        Label titleLabel = new Label("Existing Registration Keys");
        titleLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 16px; -fx-font-weight: bold;");
        
        // Filter controls
        HBox filterRow = new HBox(15);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        
        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("All", "Available", "Used", "Expired");
        statusFilter.setValue("All");
        statusFilter.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #ffffff;");
        
        ComboBox<String> typeFilter = new ComboBox<>();
        typeFilter.getItems().addAll("All", "USER", "ADMIN", "MODERATOR", "TEMPORARY");
        typeFilter.setValue("All");
        typeFilter.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #ffffff;");
        
        Button refreshButton = new Button("Refresh");
        refreshButton.setStyle("-fx-background-color: #0066cc; -fx-text-fill: #ffffff; -fx-padding: 8 16;");
        refreshButton.setOnAction(e -> refreshKeysTable());
        
        filterRow.getChildren().addAll(
            new Label("Status:") {{ setStyle("-fx-text-fill: #ffffff;"); }}, statusFilter,
            new Label("Type:") {{ setStyle("-fx-text-fill: #ffffff;"); }}, typeFilter,
            refreshButton
        );
        
        // Keys table
        TableView<KeyDisplay> keysTable = createKeysTable();
        keysTable.setPrefHeight(400);
        
        section.getChildren().addAll(titleLabel, filterRow, keysTable);
        
        return section;
    }
    
    /**
     * Create table for displaying registration keys
     */
    private TableView<KeyDisplay> createKeysTable() {
        TableView<KeyDisplay> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #ffffff;");
        
        TableColumn<KeyDisplay, String> keyCol = new TableColumn<>("Key ID");
        keyCol.setCellValueFactory(new PropertyValueFactory<>("keyValue"));
        keyCol.setPrefWidth(150);
        
        TableColumn<KeyDisplay, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("keyType"));
        typeCol.setPrefWidth(80);
        
        TableColumn<KeyDisplay, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(80);
        
        TableColumn<KeyDisplay, String> usedByCol = new TableColumn<>("Used By");
        usedByCol.setCellValueFactory(new PropertyValueFactory<>("usedBy"));
        usedByCol.setPrefWidth(100);
        
        TableColumn<KeyDisplay, String> createdCol = new TableColumn<>("Created");
        createdCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        createdCol.setPrefWidth(120);
        
        TableColumn<KeyDisplay, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
         table.getColumns().addAll(Arrays.asList(keyCol, typeCol, statusCol, usedByCol, createdCol, descCol));

        // Load initial data
        refreshKeysTable(table);
        
        return table;
    }
    
    /**
     * Generate new registration keys
     */
    private void generateKeys(String keyType, int count, String description) {
        try {
            // Security check
            if (!securityService.checkPermission(currentUser, AdminSecurityService.SecurityAction.ADMIN_ACCESS, "key_generation")) {
                NotificationManager.getInstance().showNotification(
                    "Access denied - insufficient privileges for key generation",
                    NotificationManager.NotificationType.ERROR
                );
                return;
            }
            
            // Log the key generation action
            securityService.logSecurityEvent(currentUser.getId(), AdminSecurityService.SecurityAction.ADMIN_ACCESS,
                "KEY_GENERATION", AdminSecurityService.ThreatLevel.MEDIUM,
                "Generating " + count + " " + keyType + " keys");
            
            List<String> generatedKeys = new ArrayList<>();
            
            try (Connection conn = DatabaseUtil.getConnection()) {
                String sql = "INSERT INTO key_ids (key_value, key_type, description, generated_by) VALUES (?, ?, ?, ?)";
                PreparedStatement stmt = conn.prepareStatement(sql);
                
                for (int i = 0; i < count; i++) {
                    String keyValue = generateKeyId(keyType);
                    
                    stmt.setString(1, keyValue);
                    stmt.setString(2, keyType);
                    stmt.setString(3, description.isEmpty() ? "Generated by " + currentUser.getUsername() : description);
                    stmt.setInt(4, currentUser.getId());
                    
                    stmt.executeUpdate();
                    generatedKeys.add(keyValue);
                }
                
                stmt.close();
                
                // Show success notification
                NotificationManager.getInstance().showNotification(
                    "Successfully generated " + count + " " + keyType + " key(s)",
                    NotificationManager.NotificationType.SUCCESS
                );
                
                // Display generated keys
                showGeneratedKeys(generatedKeys);
                
                // Refresh the keys table
                refreshKeysTable();
                
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Error generating keys", e);
                NotificationManager.getInstance().showNotification(
                    "Error generating keys: " + e.getMessage(),
                    NotificationManager.NotificationType.ERROR
                );
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error during key generation", e);
            NotificationManager.getInstance().showNotification(
                "Unexpected error during key generation",
                NotificationManager.NotificationType.ERROR
            );
        }
    }
    
    /**
     * Generate a unique key ID based on type
     */
    private String generateKeyId(String keyType) {
        String prefix;
        switch (keyType) {
            case "ADMIN": prefix = "ADMIN"; break;
            case "MODERATOR": prefix = "MOD"; break;
            case "TEMPORARY": prefix = "TEMP"; break;
            default: prefix = "USER"; break;
        }
        
        // Generate random suffix
        long timestamp = System.currentTimeMillis();
        int random = (int) (Math.random() * 9999);
        
        return String.format("%s%08d%04d", prefix, timestamp % 100000000, random);
    }
    
    /**
     * Show generated keys in a dialog-like notification
     */
    private void showGeneratedKeys(List<String> keys) {
        StringBuilder keysText = new StringBuilder("Generated Keys:\n\n");
        for (String key : keys) {
            keysText.append("• ").append(key).append("\n");
        }
        keysText.append("\nThese keys can be used for user registration.");
        
        // Create a larger notification for displaying keys
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 12; -fx-border-color: #00ff88; -fx-border-width: 2; -fx-border-radius: 12;");
        
        Label titleLabel = new Label("Generated Registration Keys");
        titleLabel.setStyle("-fx-text-fill: #00ff88; -fx-font-size: 16px; -fx-font-weight: bold;");
        
        TextArea keysTextArea = new TextArea(keysText.toString());
        keysTextArea.setEditable(false);
        keysTextArea.setPrefRowCount(keys.size() + 3);
        keysTextArea.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #ffffff; -fx-font-family: 'Courier New', monospace;");
        
        Button copyButton = new Button("Copy to Clipboard");
        copyButton.setStyle("-fx-background-color: #0066cc; -fx-text-fill: #ffffff; -fx-padding: 8 16;");
        copyButton.setOnAction(e -> {
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            final ClipboardContent content2 = new ClipboardContent();
            content2.putString(String.join("\n", keys));
            clipboard.setContent(content2);
            
            NotificationManager.getInstance().showNotification(
                "Keys copied to clipboard",
                NotificationManager.NotificationType.SUCCESS
            );
        });
        
        content.getChildren().addAll(titleLabel, keysTextArea, copyButton);
        
        // Add to notification area temporarily
        NotificationManager.getInstance().showCustomNotification(content, 15000); // Show for 15 seconds
    }
    
    /**
     * Refresh the keys table
     */
    private void refreshKeysTable() {
        // Find the keys table and refresh it
        // This would be called when the refresh button is clicked
    }
    
    /**
     * Refresh specific keys table
     */
    private void refreshKeysTable(TableView<KeyDisplay> table) {
        CompletableFuture.supplyAsync(() -> loadKeysFromDatabase())
            .thenAccept(keys -> Platform.runLater(() -> {
                table.getItems().clear();
                table.getItems().addAll(keys);
            }))
            .exceptionally(throwable -> {
                Platform.runLater(() -> {
                    LOGGER.log(Level.WARNING, "Error loading keys", throwable);
                    NotificationManager.getInstance().showNotification(
                        "Error loading keys: " + throwable.getMessage(),
                        NotificationManager.NotificationType.ERROR
                    );
                });
                return null;
            });
    }
    
    /**
     * Load keys from database
     */
    private List<KeyDisplay> loadKeysFromDatabase() {
        List<KeyDisplay> keys = new ArrayList<>();
        
        try (Connection conn = DatabaseUtil.getConnection()) {
            String sql = """
                SELECT k.key_value, k.key_type, k.is_used, k.description, k.created_at, k.used_at,
                       u.username as used_by_username
                FROM key_ids k
                LEFT JOIN users u ON k.used_by = u.id
                ORDER BY k.created_at DESC
                LIMIT 100
                """;
            
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                KeyDisplay key = new KeyDisplay(
                    rs.getString("key_value"),
                    rs.getString("key_type"),
                    rs.getBoolean("is_used") ? "Used" : "Available",
                    rs.getString("used_by_username"),
                    rs.getTimestamp("created_at").toString(),
                    rs.getString("description")
                );
                keys.add(key);
            }
            
            rs.close();
            stmt.close();
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error loading keys from database", e);
        }
        
        return keys;
    }

    // Data classes for display
    public static class KeyDisplay {
        private final String keyValue;
        private final String keyType;
        private final String status;
        private final String usedBy;
        private final String createdAt;
        private final String description;
        
        public KeyDisplay(String keyValue, String keyType, String status, String usedBy, String createdAt, String description) {
            this.keyValue = keyValue;
            this.keyType = keyType;
            this.status = status;
            this.usedBy = usedBy != null ? usedBy : "-";
            this.createdAt = createdAt;
            this.description = description != null ? description : "-";
        }
        
        // Getters for TableView
        public String getKeyValue() { return keyValue; }
        public String getKeyType() { return keyType; }
        public String getStatus() { return status; }
        public String getUsedBy() { return usedBy; }
        public String getCreatedAt() { return createdAt; }
        public String getDescription() { return description; }
    }
}