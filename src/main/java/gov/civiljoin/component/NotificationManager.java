package gov.civiljoin.component;

import gov.civiljoin.util.ThemeManager;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.logging.Logger;

/**
 * In-app notification system for CivilJoin
 * Provides toast-style notifications that appear at the top-right of the application
 */
public class NotificationManager {
    
    private static final Logger LOGGER = Logger.getLogger(NotificationManager.class.getName());
    private static NotificationManager instance;
    private final ThemeManager themeManager = ThemeManager.getInstance();
    
    // Notification types
    public enum NotificationType {
        INFO, SUCCESS, WARNING, ERROR
    }
    
    private NotificationManager() {}
    
    public static NotificationManager getInstance() {
        if (instance == null) {
            instance = new NotificationManager();
        }
        return instance;
    }
    
    /**
     * Show a notification message
     */
    public void showNotification(String title, String message, NotificationType type) {
        Platform.runLater(() -> {
            try {
                // Find the current scene from any active window
                Scene currentScene = getCurrentScene();
                if (currentScene == null) {
                    LOGGER.warning("No active scene found for notification");
                    return;
                }
                
                // Create notification UI
                VBox notification = createNotificationBox(title, message, type);
                
                // Add to scene
                addNotificationToScene(currentScene, notification);
                
                // Auto-hide after 5 seconds
                autoHideNotification(notification, Duration.seconds(5));
                
            } catch (Exception e) {
                LOGGER.severe("Error showing notification: " + e.getMessage());
            }
        });
    }
    
    /**
     * Show an info notification
     */
    public void showInfo(String title, String message) {
        showNotification(title, message, NotificationType.INFO);
    }
    
    /**
     * Show a success notification
     */
    public void showSuccess(String title, String message) {
        showNotification(title, message, NotificationType.SUCCESS);
    }
    
    /**
     * Show a warning notification
     */
    public void showWarning(String title, String message) {
        showNotification(title, message, NotificationType.WARNING);
    }
    
    /**
     * Show an error notification
     */
    public void showError(String title, String message) {
        showNotification(title, message, NotificationType.ERROR);
    }
    
    /**
     * Get the current active scene
     */
    private Scene getCurrentScene() {
        return Stage.getWindows().stream()
            .filter(window -> window.isShowing() && window instanceof Stage)
            .map(window -> ((Stage) window).getScene())
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Create the notification UI box
     */
    private VBox createNotificationBox(String title, String message, NotificationType type) {
        VBox notification = new VBox(5);
        notification.setPadding(new Insets(15));
        notification.setMaxWidth(300);
        notification.setAlignment(Pos.TOP_LEFT);
        
        // Style based on type and theme
        String baseStyle = "-fx-background-radius: 8; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0, 0, 2);";
        String colorStyle = getNotificationColorStyle(type);
        notification.setStyle(baseStyle + colorStyle);
        
        // Create title label
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: white;");
        
        // Create message label
        Label messageLabel = new Label(message);
        messageLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: white; -fx-wrap-text: true;");
        messageLabel.setWrapText(true);
        
        // Create close button
        Button closeButton = new Button("×");
        closeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 16px; -fx-padding: 0;");
        closeButton.setOnAction(e -> hideNotification(notification));
        
        // Create header with title and close button
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleLabel, javafx.scene.layout.Priority.ALWAYS);
        header.getChildren().addAll(titleLabel, closeButton);
        
        notification.getChildren().addAll(header, messageLabel);
        
        // Add CSS classes for theme management
        notification.getStyleClass().addAll("notification", type.name().toLowerCase());
        
        return notification;
    }
    
    /**
     * Get color style based on notification type
     */
    private String getNotificationColorStyle(NotificationType type) {
        boolean isDark = themeManager.isDarkMode();
        
        switch (type) {
            case SUCCESS:
                return "-fx-background-color: #4CAF50;";
            case WARNING:
                return "-fx-background-color: #FF9800;";
            case ERROR:
                return "-fx-background-color: #F44336;";
            case INFO:
            default:
                return "-fx-background-color: #2196F3;";
        }
    }
    
    /**
     * Add notification to the scene
     */
    private void addNotificationToScene(Scene scene, VBox notification) {
        Node root = scene.getRoot();
        
        if (root instanceof Pane) {
            Pane rootPane = (Pane) root;
            
            // Position at top-right
            notification.setLayoutX(scene.getWidth() - 320);
            notification.setLayoutY(20);
            
            // Add to scene
            rootPane.getChildren().add(notification);
            
            // Ensure it's on top
            notification.toFront();
            
            // Add entrance animation
            showNotificationAnimation(notification);
            
            // Update position if window is resized
            scene.widthProperty().addListener((obs, oldWidth, newWidth) -> {
                notification.setLayoutX(newWidth.doubleValue() - 320);
            });
        }
    }
    
    /**
     * Show notification with fade-in animation
     */
    private void showNotificationAnimation(VBox notification) {
        notification.setOpacity(0);
        
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), notification);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
    }
    
    /**
     * Auto-hide notification after specified duration
     */
    private void autoHideNotification(VBox notification, Duration delay) {
        PauseTransition pause = new PauseTransition(delay);
        pause.setOnFinished(e -> hideNotification(notification));
        pause.play();
    }
    
    /**
     * Hide notification with fade-out animation
     */
    private void hideNotification(VBox notification) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), notification);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            // Remove from parent
            if (notification.getParent() instanceof Pane) {
                ((Pane) notification.getParent()).getChildren().remove(notification);
            }
        });
        fadeOut.play();
    }
} 