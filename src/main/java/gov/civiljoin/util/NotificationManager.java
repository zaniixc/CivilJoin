package gov.civiljoin.util;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * HIGH-PERFORMANCE Notification Manager for CivilJoin 2024
 * Optimized for zero UI lag with async operations and lightweight animations
 */
public class NotificationManager {
    private static final Logger LOGGER = Logger.getLogger(NotificationManager.class.getName());
    private static NotificationManager instance;
    private VBox notificationContainer;
    
    // Performance optimizations
    private final ExecutorService notificationExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "NotificationCreator");
        t.setDaemon(true);
        return t;
    });
    
    // Animation and resource caching
    private final ConcurrentLinkedQueue<VBox> reusableNotifications = new ConcurrentLinkedQueue<>();
    private final AtomicInteger activeNotifications = new AtomicInteger(0);
    private final Object styleLock = new Object();
    
    // Pre-cached styles for instant application
    private static final String BASE_NOTIFICATION_STYLE = 
        "-fx-background-color: #1a1a1a; " +
        "-fx-background-radius: 8px; " +
        "-fx-border-radius: 8px; " +
        "-fx-border-width: 1px; " +
        "-fx-padding: 12 16;";
    
    private static final String BUTTON_BASE_STYLE = 
        "-fx-font-weight: 500; " +
        "-fx-padding: 6 12; " +
        "-fx-background-radius: 6px; " +
        "-fx-cursor: hand; " +
        "-fx-font-size: 13px;";
    
    // Lightweight animation constants
    private static final Duration FAST_DURATION = Duration.millis(150);
    private static final Duration SLIDE_DURATION = Duration.millis(200);
    
    public enum NotificationType {
        SUCCESS("#00ff88", "✓"), 
        ERROR("#ff4444", "✕"), 
        INFO("#64b5f6", "ℹ"), 
        WARNING("#ffaa00", "⚠");
        
        private final String color;
        private final String icon;
        
        NotificationType(String color, String icon) {
            this.color = color;
            this.icon = icon;
        }
        
        public String getColor() { return color; }
        public String getIcon() { return icon; }
    }
    
    private NotificationManager() {
        // Initialize reusable notification pool
        preloadNotificationPool();
    }
    
    public static NotificationManager getInstance() {
        if (instance == null) {
            synchronized (NotificationManager.class) {
                if (instance == null) {
                    instance = new NotificationManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * OPTIMIZED: Set notification container with minimal styling
     */
    public void setNotificationContainer(VBox container) {
        this.notificationContainer = container;
        
        // Apply minimal high-performance styling
        Platform.runLater(() -> {
            container.getStyleClass().add("notification-container");
            container.setAlignment(Pos.TOP_RIGHT);
            container.setPadding(new Insets(15));
            container.setSpacing(8);
            container.setMouseTransparent(true);
            container.toFront();
            container.setViewOrder(-1000);
        });
    }
    
    /**
     * OPTIMIZED: Async notification with instant UI response
     */
    public void showNotification(String message, NotificationType type) {
        if (notificationContainer == null) return;
        
        // Immediate UI feedback - show placeholder if needed
        Platform.runLater(() -> notificationContainer.setMouseTransparent(false));
        
        // Create notification asynchronously
        CompletableFuture
            .supplyAsync(() -> createOptimizedNotification(message, type), notificationExecutor)
            .thenAcceptAsync(notificationResult -> {
                Platform.runLater(() -> {
                    if (notificationContainer != null && notificationResult != null) {
                        final VBox finalNotification = notificationResult; // Make effectively final
                        addNotificationToUI(finalNotification, true);
                        
                        // Auto-remove with optimized timer
                        Timeline autoRemove = new Timeline(
                            new KeyFrame(Duration.seconds(4), e -> removeNotificationFast(finalNotification))
                        );
                        autoRemove.play();
                    }
                });
            }, Platform::runLater);
    }
    
    /**
     * OPTIMIZED: Fast confirmation dialog
     */
    public void showConfirmation(String message, Runnable onConfirm, Runnable onCancel) {
        if (notificationContainer == null) return;
        
        CompletableFuture
            .supplyAsync(() -> createOptimizedConfirmation(message, onConfirm, onCancel), notificationExecutor)
            .thenAcceptAsync(notificationResult -> {
                Platform.runLater(() -> {
                    if (notificationResult != null) {
                        final VBox finalNotification = notificationResult; // Make effectively final
                        addNotificationToUI(finalNotification, false);
                    }
                });
            }, Platform::runLater);
    }
    
    /**
     * OPTIMIZED: Password confirmation with minimal overhead
     */
    public void showPasswordConfirmation(String message, Consumer<String> onPasswordEntered) {
        if (notificationContainer == null) return;
        
        CompletableFuture
            .supplyAsync(() -> createOptimizedPasswordConfirmation(message, onPasswordEntered), notificationExecutor)
            .thenAcceptAsync(notificationResult -> {
                Platform.runLater(() -> {
                    if (notificationResult != null) {
                        final VBox finalNotification = notificationResult; // Make effectively final
                        addNotificationToUI(finalNotification, false);
                        // Focus password field after animation
                        Timeline focusDelay = new Timeline(
                            new KeyFrame(SLIDE_DURATION.add(Duration.millis(50)), e -> {
                                finalNotification.getChildren().stream()
                                    .filter(node -> node instanceof PasswordField)
                                    .findFirst()
                                    .ifPresent(field -> field.requestFocus());
                            })
                        );
                        focusDelay.play();
                    }
                });
            }, Platform::runLater);
    }
    
    /**
     * OPTIMIZED: Text confirmation with minimal overhead
     */
    public void showTextConfirmation(String message, String expectedText, Runnable onConfirm) {
        if (notificationContainer == null) return;
        
        CompletableFuture
            .supplyAsync(() -> createOptimizedTextConfirmation(message, expectedText, onConfirm), notificationExecutor)
            .thenAcceptAsync(notificationResult -> {
                Platform.runLater(() -> {
                    if (notificationResult != null) {
                        final VBox finalNotification = notificationResult;
                        addNotificationToUI(finalNotification, false);
                        // Focus text field after animation
                        Timeline focusDelay = new Timeline(
                            new KeyFrame(SLIDE_DURATION.add(Duration.millis(50)), e -> {
                                finalNotification.getChildren().stream()
                                    .filter(node -> node instanceof TextField)
                                    .findFirst()
                                    .ifPresent(field -> field.requestFocus());
                            })
                        );
                        focusDelay.play();
                    }
                });
            }, Platform::runLater);
    }
    
    /**
     * OPTIMIZED: Custom notification with user-defined content
     */
    public void showCustomNotification(javafx.scene.Node content, int durationMs) {
        if (notificationContainer == null) return;
        
        CompletableFuture
            .supplyAsync(() -> createOptimizedCustomNotification(content, durationMs), notificationExecutor)
            .thenAcceptAsync(notificationResult -> {
                Platform.runLater(() -> {
                    if (notificationResult != null) {
                        final VBox finalNotification = notificationResult;
                        addNotificationToUI(finalNotification, durationMs > 0);
                        
                        // Auto-remove after specified duration
                        if (durationMs > 0) {
                            Timeline autoRemove = new Timeline(
                                new KeyFrame(Duration.millis(durationMs), e -> removeNotificationFast(finalNotification))
                            );
                            autoRemove.play();
                        }
                    }
                });
            }, Platform::runLater);
    }
    
    /**
     * HIGH-PERFORMANCE: Create optimized notification with reusable components
     */
    private VBox createOptimizedNotification(String message, NotificationType type) {
        // Try to reuse existing notification container
        VBox notificationBox = reusableNotifications.poll();
        if (notificationBox == null) {
            notificationBox = new VBox(8);
            notificationBox.setAlignment(Pos.CENTER_LEFT);
            notificationBox.setMaxWidth(320);
        } else {
            notificationBox.getChildren().clear();
        }
        
        // Apply cached styling immediately
        String style = BASE_NOTIFICATION_STYLE + "-fx-border-color: " + type.getColor() + ";";
        notificationBox.setStyle(style);
        
        // Create content efficiently
        HBox content = new HBox(10);
        content.setAlignment(Pos.CENTER_LEFT);
        
        // Icon label (lightweight)
        Label iconLabel = new Label(type.getIcon());
        iconLabel.setStyle("-fx-text-fill: " + type.getColor() + "; -fx-font-size: 16px; -fx-font-weight: bold;");
        
        // Message label
        Label messageLabel = new Label(message);
        messageLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 13px; -fx-wrap-text: true;");
        messageLabel.setMaxWidth(220);
        
        // Lightweight close button
        Button closeButton = new Button("×");
        closeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: rgba(255,255,255,0.6); " +
                           "-fx-font-size: 16px; -fx-padding: 0 6; -fx-cursor: hand;");
        
        // Use final reference for lambda
        final VBox finalNotificationBox = notificationBox;
        closeButton.setOnAction(e -> removeNotificationFast(finalNotificationBox));
        
        // Minimal hover effect
        closeButton.setOnMouseEntered(e -> closeButton.setStyle(closeButton.getStyle() + "-fx-text-fill: #ffffff;"));
        closeButton.setOnMouseExited(e -> closeButton.setStyle(closeButton.getStyle().replace("-fx-text-fill: #ffffff;", "-fx-text-fill: rgba(255,255,255,0.6);")));
        
        content.getChildren().addAll(iconLabel, messageLabel, closeButton);
        HBox.setHgrow(messageLabel, javafx.scene.layout.Priority.ALWAYS);
        notificationBox.getChildren().add(content);
        
        return notificationBox;
    }
    
    /**
     * OPTIMIZED: Create confirmation dialog with cached components
     */
    private VBox createOptimizedConfirmation(String message, Runnable onConfirm, Runnable onCancel) {
        VBox notificationBox = new VBox(12);
        notificationBox.setAlignment(Pos.CENTER_LEFT);
        notificationBox.setPadding(new Insets(16, 20, 16, 20));
        notificationBox.setMaxWidth(350);
        
        // Apply optimized styling
        String style = BASE_NOTIFICATION_STYLE + "-fx-border-color: #ffaa00;";
        notificationBox.setStyle(style);
        
        // Message
        Label messageLabel = new Label(message);
        messageLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 14px; -fx-wrap-text: true;");
        
        // Optimized buttons
        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        
        Button cancelButton = createOptimizedButton("Cancel", "#2a2a2a", "#333333");
        Button confirmButton = createOptimizedButton("Confirm", "#ff4444", "#ff6666");
        
        // Use final reference for lambda
        final VBox finalNotificationBox = notificationBox;
        
        cancelButton.setOnAction(e -> {
            if (onCancel != null) onCancel.run();
            removeNotificationFast(finalNotificationBox);
        });
        
        confirmButton.setOnAction(e -> {
            if (onConfirm != null) onConfirm.run();
            removeNotificationFast(finalNotificationBox);
        });
        
        buttons.getChildren().addAll(cancelButton, confirmButton);
        notificationBox.getChildren().addAll(messageLabel, buttons);
        
        return notificationBox;
    }
    
    /**
     * OPTIMIZED: Create password confirmation with minimal resources
     */
    private VBox createOptimizedPasswordConfirmation(String message, Consumer<String> onPasswordEntered) {
        VBox notificationBox = new VBox(12);
        notificationBox.setAlignment(Pos.CENTER_LEFT);
        notificationBox.setPadding(new Insets(16, 20, 16, 20));
        notificationBox.setMaxWidth(350);
        
        String style = BASE_NOTIFICATION_STYLE + "-fx-border-color: #ff4444;";
        notificationBox.setStyle(style);
        
        // Message
        Label messageLabel = new Label(message);
        messageLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 14px; -fx-wrap-text: true;");
        
        // Optimized password field
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter password");
        passwordField.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #ffffff; " +
                              "-fx-border-color: #333333; -fx-border-radius: 6px; " +
                              "-fx-background-radius: 6px; -fx-padding: 8 12; -fx-font-size: 13px;");
        
        // Lightweight focus handling
        passwordField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            String currentStyle = passwordField.getStyle();
            if (newVal) {
                if (!currentStyle.contains("-fx-border-color: #ffffff;")) {
                    passwordField.setStyle(currentStyle.replace("-fx-border-color: #333333;", "-fx-border-color: #ffffff;"));
                }
            } else {
                passwordField.setStyle(currentStyle.replace("-fx-border-color: #ffffff;", "-fx-border-color: #333333;"));
            }
        });
        
        // Optimized buttons
        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        
        Button cancelButton = createOptimizedButton("Cancel", "#2a2a2a", "#333333");
        Button confirmButton = createOptimizedButton("Confirm", "#ff4444", "#ff6666");
        
        // Use final reference for lambda
        final VBox finalNotificationBox = notificationBox;
        
        cancelButton.setOnAction(e -> removeNotificationFast(finalNotificationBox));
        confirmButton.setOnAction(e -> {
            String password = passwordField.getText();
            if (!password.isEmpty() && onPasswordEntered != null) {
                onPasswordEntered.accept(password);
            }
            removeNotificationFast(finalNotificationBox);
        });
        
        // Enter key handling
        passwordField.setOnAction(e -> confirmButton.fire());
        
        buttons.getChildren().addAll(cancelButton, confirmButton);
        notificationBox.getChildren().addAll(messageLabel, passwordField, buttons);
        
        return notificationBox;
    }
    
    /**
     * OPTIMIZED: Create text confirmation with minimal resources
     */
    private VBox createOptimizedTextConfirmation(String message, String expectedText, Runnable onConfirm) {
        VBox notificationBox = new VBox(12);
        notificationBox.setAlignment(Pos.CENTER_LEFT);
        notificationBox.setPadding(new Insets(16, 20, 16, 20));
        notificationBox.setMaxWidth(350);
        
        String style = BASE_NOTIFICATION_STYLE + "-fx-border-color: #ff4444;";
        notificationBox.setStyle(style);
        
        // Message
        Label messageLabel = new Label(message);
        messageLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 14px; -fx-wrap-text: true;");
        
        // Instruction
        Label instructionLabel = new Label("Type \"" + expectedText + "\" to confirm:");
        instructionLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.8); -fx-font-size: 12px;");
        
        // Text field
        TextField textField = new TextField();
        textField.setPromptText(expectedText);
        textField.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #ffffff; " +
                          "-fx-border-color: #333333; -fx-border-radius: 6px; " +
                          "-fx-background-radius: 6px; -fx-padding: 8 12; -fx-font-size: 13px;");
        
        // Lightweight focus handling
        textField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            String currentStyle = textField.getStyle();
            if (newVal) {
                if (!currentStyle.contains("-fx-border-color: #ffffff;")) {
                    textField.setStyle(currentStyle.replace("-fx-border-color: #333333;", "-fx-border-color: #ffffff;"));
                }
            } else {
                textField.setStyle(currentStyle.replace("-fx-border-color: #ffffff;", "-fx-border-color: #333333;"));
            }
        });
        
        // Optimized buttons
        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        
        Button cancelButton = createOptimizedButton("Cancel", "#2a2a2a", "#333333");
        Button confirmButton = createOptimizedButton("Confirm", "#ff4444", "#ff6666");
        confirmButton.setDisable(true); // Initially disabled
        
        // Use final reference for lambda
        final VBox finalNotificationBox = notificationBox;
        
        // Enable confirm button only when correct text is entered
        textField.textProperty().addListener((obs, oldText, newText) -> {
            boolean matches = expectedText.equals(newText);
            confirmButton.setDisable(!matches);
        });
        
        cancelButton.setOnAction(e -> removeNotificationFast(finalNotificationBox));
        confirmButton.setOnAction(e -> {
            if (expectedText.equals(textField.getText()) && onConfirm != null) {
                onConfirm.run();
            }
            removeNotificationFast(finalNotificationBox);
        });
        
        // Enter key handling
        textField.setOnAction(e -> {
            if (!confirmButton.isDisable()) {
                confirmButton.fire();
            }
        });
        
        buttons.getChildren().addAll(cancelButton, confirmButton);
        notificationBox.getChildren().addAll(messageLabel, instructionLabel, textField, buttons);
        
        return notificationBox;
    }
    
    /**
     * OPTIMIZED: Create custom notification with user content
     */
    private VBox createOptimizedCustomNotification(javafx.scene.Node content, int durationMs) {
        VBox notificationBox = new VBox(8);
        notificationBox.setAlignment(Pos.CENTER_LEFT);
        notificationBox.setPadding(new Insets(16, 20, 16, 20));
        notificationBox.setMaxWidth(450);
        
        String style = BASE_NOTIFICATION_STYLE + "-fx-border-color: #646cff;";
        notificationBox.setStyle(style);
        
        // Add close button header
        HBox header = new HBox();
        header.setAlignment(Pos.TOP_RIGHT);
        header.setPadding(new Insets(0, 0, 8, 0));
        
        Button closeButton = new Button("✕");
        closeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: rgba(255,255,255,0.6); " +
                           "-fx-font-size: 16px; -fx-padding: 0 6; -fx-cursor: hand; -fx-border-width: 0;");
        
        // Use final reference for lambda
        final VBox finalNotificationBox = notificationBox;
        closeButton.setOnAction(e -> removeNotificationFast(finalNotificationBox));
        
        // Minimal hover effect
        closeButton.setOnMouseEntered(e -> closeButton.setStyle(closeButton.getStyle() + "-fx-text-fill: #ffffff;"));
        closeButton.setOnMouseExited(e -> closeButton.setStyle(closeButton.getStyle().replace("-fx-text-fill: #ffffff;", "-fx-text-fill: rgba(255,255,255,0.6);")));
        
        header.getChildren().add(closeButton);
        notificationBox.getChildren().addAll(header, content);
        
        return notificationBox;
    }
    
    /**
     * OPTIMIZED: Create button with cached styling
     */
    private Button createOptimizedButton(String text, String bgColor, String hoverColor) {
        Button button = new Button(text);
        String style = BUTTON_BASE_STYLE + 
                      "-fx-background-color: " + bgColor + "; " +
                      "-fx-text-fill: #ffffff; " +
                      "-fx-border-color: #333333; -fx-border-width: 1px;";
        button.setStyle(style);
        
        // Lightweight hover effects
        button.setOnMouseEntered(e -> {
            String currentStyle = button.getStyle();
            if (!currentStyle.contains(hoverColor)) {
                button.setStyle(currentStyle.replace(bgColor, hoverColor));
            }
        });
        button.setOnMouseExited(e -> {
            button.setStyle(button.getStyle().replace(hoverColor, bgColor));
        });
        
        return button;
    }
    
    /**
     * LIGHTNING-FAST: Add notification to UI with minimal animation
     */
    private void addNotificationToUI(VBox notification, boolean autoHide) {
        if (notificationContainer == null) return;
        
        // Increment active count
        activeNotifications.incrementAndGet();
        
        // Insert at top
        notificationContainer.getChildren().add(0, notification);
        notificationContainer.setMouseTransparent(false);
        notificationContainer.toFront();
        
        // ULTRA-LIGHTWEIGHT slide-in animation
        notification.setTranslateX(300);
        notification.setOpacity(0.3);
        
        // Single optimized transition
        TranslateTransition slide = new TranslateTransition(SLIDE_DURATION, notification);
        slide.setToX(0);
        
        FadeTransition fade = new FadeTransition(FAST_DURATION, notification);
        fade.setToValue(1.0);
        
        // Parallel animation for performance
        ParallelTransition animation = new ParallelTransition(slide, fade);
        animation.setOnFinished(e -> {
            notification.setTranslateX(0);
            notification.setOpacity(1);
        });
        
        animation.play();
    }
    
    /**
     * ULTRA-FAST: Remove notification with instant cleanup
     */
    private void removeNotificationFast(VBox notification) {
        if (notification == null || !notificationContainer.getChildren().contains(notification)) {
            return;
        }
        
        // Prevent double removal
        if (notification.getOpacity() <= 0.1) return;
        
        // Lightning-fast fade out
        FadeTransition fadeOut = new FadeTransition(Duration.millis(100), notification);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            Platform.runLater(() -> {
                if (notificationContainer.getChildren().remove(notification)) {
                    activeNotifications.decrementAndGet();
                    
                    // Reuse notification if pool not full
                    if (reusableNotifications.size() < 5) {
                        notification.setOpacity(1);
                        notification.setTranslateX(0);
                        reusableNotifications.offer(notification);
                    }
                    
                    // Make container transparent if empty
                    if (notificationContainer.getChildren().isEmpty()) {
                        notificationContainer.setMouseTransparent(true);
                    }
                }
            });
        });
        
        fadeOut.play();
    }
    
    /**
     * Pre-load notification pool for instant reuse
     */
    private void preloadNotificationPool() {
        Task<Void> preloadTask = new Task<>() {
            @Override
            protected Void call() {
                for (int i = 0; i < 3; i++) {
                    VBox preloadedNotification = new VBox(8);
                    preloadedNotification.setAlignment(Pos.CENTER_LEFT);
                    preloadedNotification.setMaxWidth(320);
                    reusableNotifications.offer(preloadedNotification);
                }
                return null;
            }
        };
        
        Thread preloadThread = new Thread(preloadTask);
        preloadThread.setDaemon(true);
        preloadThread.start();
    }
    
    /**
     * Cleanup resources and shutdown executor
     */
    public void shutdown() {
        if (notificationExecutor != null && !notificationExecutor.isShutdown()) {
            notificationExecutor.shutdown();
        }
        reusableNotifications.clear();
    }
    
    /**
     * Get performance stats
     */
    public String getPerformanceStats() {
        return String.format("Active: %d, Pooled: %d, Executor: %s",
            activeNotifications.get(),
            reusableNotifications.size(),
            notificationExecutor.isShutdown() ? "Shutdown" : "Running"
        );
    }
    
    // Convenience methods for common notification types
    public void showSuccess(String message) { showNotification(message, NotificationType.SUCCESS); }
    public void showError(String message) { showNotification(message, NotificationType.ERROR); }
    public void showInfo(String message) { showNotification(message, NotificationType.INFO); }
    public void showWarning(String message) { showNotification(message, NotificationType.WARNING); }
    
    // Convenience confirmation methods
    public void showConfirmation(String message, Runnable onConfirm) {
        showConfirmation(message, onConfirm, null);
    }
} 