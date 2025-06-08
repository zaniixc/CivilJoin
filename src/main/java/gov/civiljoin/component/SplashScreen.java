package gov.civiljoin.component;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * A modern splash screen with progress bar for the application
 */
public class SplashScreen {
    private static final int WIDTH = 600;
    private static final int HEIGHT = 400;
    
    private Stage splashStage;
    private ProgressBar progressBar;
    private Label statusLabel;
    private Consumer<Void> onFinished;
    
    // Theme colors - based on material black color palette
    private static final String BACKGROUND_COLOR = "#070707"; // Almost black
    private static final String SURFACE_COLOR = "#0D0D0D";    // Very dark gray
    private static final String TEXT_COLOR = "#f5f5f5";       // Almost white
    private static final String ACCENT_COLOR = "#dadada";     // Light gray accent
    private static final String PROGRESS_FILL = "#151515";    // Dark gray for progress bar
    
    public SplashScreen() {
        initialize();
    }
    
    private void initialize() {
        splashStage = new Stage();
        splashStage.initStyle(StageStyle.UNDECORATED);
        
        // Create content
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: " + BACKGROUND_COLOR + ";");
        
        // App logo/title
        Label titleLabel = new Label("CivilJoin");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 42));
        titleLabel.setTextFill(Color.web(TEXT_COLOR));
        titleLabel.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.5)));
        
        // Subtitle
        Label subtitleLabel = new Label("Secure Government Portal");
        subtitleLabel.setFont(Font.font("System", 16));
        subtitleLabel.setTextFill(Color.web(ACCENT_COLOR));
        
        // Custom progress bar container
        VBox progressContainer = new VBox(10);
        progressContainer.setAlignment(Pos.CENTER);
        progressContainer.setMaxWidth(WIDTH * 0.8);
        
        // Modern progress bar
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(WIDTH * 0.8);
        progressBar.setPrefHeight(8);
        progressBar.setStyle(
            "-fx-accent: " + PROGRESS_FILL + ";" +
            "-fx-background-color: " + SURFACE_COLOR + ";" +
            "-fx-background-radius: 4;" +
            "-fx-border-radius: 4;"
        );
        
        // Status label
        statusLabel = new Label("Initializing...");
        statusLabel.setTextFill(Color.web(TEXT_COLOR));
        statusLabel.setFont(Font.font("System", 14));
        
        // Add components to container
        progressContainer.getChildren().addAll(progressBar, statusLabel);
        
        // Add all elements to root
        root.getChildren().addAll(titleLabel, subtitleLabel, progressContainer);
        
        // Create scene with rounded corners
        Scene scene = new Scene(root, WIDTH, HEIGHT);
        scene.setFill(Color.TRANSPARENT);
        
        // Apply corner rounding to the root container
        Rectangle clip = new Rectangle(WIDTH, HEIGHT);
        clip.setArcWidth(20);
        clip.setArcHeight(20);
        root.setClip(clip);
        
        // Add some padding
        root.setPadding(new javafx.geometry.Insets(20));
        
        // Add a subtle shadow effect to the entire window
        root.setEffect(new DropShadow(15, Color.rgb(0, 0, 0, 0.6)));
        
        splashStage.setScene(scene);
        splashStage.centerOnScreen();
    }
    
    /**
     * Show the splash screen and start loading tasks
     */
    public void show() {
        splashStage.show();
        
        // Simulate loading with a timeline
        Task<Void> loadingTask = createLoadingTask();
        new Thread(loadingTask).start();
    }
    
    /**
     * Set a callback to be executed when loading is complete
     */
    public void setOnFinished(Consumer<Void> onFinished) {
        this.onFinished = onFinished;
    }
    
    /**
     * Close the splash screen with a fade out effect
     */
    public void close() {
        FadeTransition fadeOut = new FadeTransition(Duration.seconds(1), splashStage.getScene().getRoot());
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> splashStage.close());
        fadeOut.play();
    }
    
    /**
     * Create a task that simulates loading operations
     */
    private Task<Void> createLoadingTask() {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                // Simulate different loading stages
                updateProgress(0.05, 1.0);
                updateMessage("Initializing application...");
                Thread.sleep(800);
                
                updateProgress(0.2, 1.0);
                updateMessage("Connecting to database...");
                Thread.sleep(1200);
                
                updateProgress(0.4, 1.0);
                updateMessage("Loading modules...");
                Thread.sleep(1000);
                
                updateProgress(0.6, 1.0);
                updateMessage("Loading user interface...");
                Thread.sleep(800);
                
                updateProgress(0.8, 1.0);
                updateMessage("Finalizing setup...");
                Thread.sleep(1000);
                
                updateProgress(1.0, 1.0);
                updateMessage("Ready!");
                Thread.sleep(500);
                
                // Trigger completion callback
                if (onFinished != null) {
                    javafx.application.Platform.runLater(() -> onFinished.accept(null));
                }
                
                return null;
            }
        };
    }
    
    /**
     * Update the progress bar
     */
    public void updateProgress(double progress) {
        progressBar.setProgress(progress);
    }
    
    /**
     * Update the status message
     */
    public void updateStatus(String message) {
        statusLabel.setText(message);
    }
    
    /**
     * Bind this splash screen's progress to a task
     */
    public void bindProgress(Task<?> task) {
        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());
        
        task.setOnSucceeded(e -> {
            if (onFinished != null) {
                onFinished.accept(null);
            }
        });
    }
} 