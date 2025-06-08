package gov.civiljoin.controller;

import gov.civiljoin.CivilJoinApplication;
import gov.civiljoin.model.User;
import gov.civiljoin.service.AuthService;
import gov.civiljoin.service.AsyncTaskService;
import gov.civiljoin.util.ThemeManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for login view
 */
public class LoginController {
    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());
    
    @FXML
    private TextField usernameField;
    
    @FXML
    private PasswordField passwordField;
    
    @FXML
    private Button loginButton;
    
    @FXML
    private Label errorLabel;
    
    private final AuthService authService = new AuthService();
    private final ThemeManager themeManager = ThemeManager.getInstance();
    
    /**
     * Handle login button action
     */
    @FXML
    private void handleLogin(ActionEvent event) {
        // Clear any previous error messages
        errorLabel.setText("");
        
        // Get form values
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        
        // Validate form input
        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Username and password are required");
            return;
        }
        
        // Disable the login button and show loading state
        loginButton.setDisable(true);
        loginButton.setText("Authenticating...");
        errorLabel.setText("Validating credentials...");
        errorLabel.setStyle("-fx-text-fill: #64b5f6;"); // Blue color for info
        
        LOGGER.info("Starting async authentication for user: " + username);
        
        // Execute authentication asynchronously
        AsyncTaskService.getInstance().executeDbTask(
            // Background task
            () -> {
                try {
                    // This runs on background thread, won't block UI
                    return authService.authenticate(username, password);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Authentication error for user: " + username, e);
                    throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
                }
            },
            // Success callback (runs on JavaFX Application Thread)
            user -> {
                loginButton.setText("Login");
                errorLabel.setStyle(""); // Reset style
                
                if (user != null) {
                    LOGGER.info("Authentication successful for user: " + username);
                    errorLabel.setText("Login successful! Loading dashboard...");
                    errorLabel.setStyle("-fx-text-fill: #4caf50;"); // Green color for success
                    
                    // Small delay to show success message before navigation
                    Platform.runLater(() -> {
                        try {
                            ensureResourcesExist();
                            navigateToDashboard(user);
                        } catch (Exception e) {
                            LOGGER.log(Level.SEVERE, "Error navigating to dashboard", e);
                            errorLabel.setText("Error loading dashboard: " + e.getMessage());
                            errorLabel.setStyle("-fx-text-fill: #f44336;"); // Red for error
                            loginButton.setDisable(false);
                        }
                    });
                } else {
                    LOGGER.warning("Authentication failed for user: " + username);
                    errorLabel.setText("Invalid username or password");
                    errorLabel.setStyle("-fx-text-fill: #f44336;"); // Red for error
                    loginButton.setDisable(false);
                }
            },
            // Error callback (runs on JavaFX Application Thread)
            throwable -> {
                LOGGER.log(Level.SEVERE, "Login error for user: " + username, throwable);
                loginButton.setText("Login");
                loginButton.setDisable(false);
                errorLabel.setText("Login failed: " + throwable.getMessage());
                errorLabel.setStyle("-fx-text-fill: #f44336;"); // Red for error
            }
        );
    }
    
    /**
     * Handle go to register button action
     */
    @FXML
    private void handleGoToRegister(ActionEvent event) {
        try {
            // Load the register view
            FXMLLoader fxmlLoader = new FXMLLoader(CivilJoinApplication.class.getResource("view/register.fxml"));
            Scene scene = new Scene(fxmlLoader.load());
            
            // Add CSS to ensure styling is consistent
            String cssPath = "/gov/civiljoin/css/styles.css";
            URL cssUrl = getClass().getResource(cssPath);
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
            
            // Apply theme - the auth-screen class will override theme settings for login/register
            themeManager.applyTheme(scene);
            
            // Get current stage and set new scene
            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("CivilJoin - Register");
            stage.show();
        } catch (IOException e) {
            errorLabel.setText("Error loading register view: " + e.getMessage());
        }
    }
    
    /**
     * Navigate to dashboard view - OPTIMIZED VERSION
     */
    private void navigateToDashboard(User user) {
        try {
            LOGGER.info("Navigating to dashboard for user: " + user.getUsername() + ", Role: " + user.getRole());
            
            // Preload dashboard resources asynchronously
            AsyncTaskService.getInstance().executeIOTask(
                () -> preloadDashboardResources(),
                resources -> {
                    // Single Platform.runLater call to avoid race conditions
                    Platform.runLater(() -> {
                        try {
                            // Use preloaded resources
                            FXMLLoader fxmlLoader = new FXMLLoader(resources.dashboardUrl);
                            Scene scene = new Scene(fxmlLoader.load());
                            
                            // Apply CSS and theme immediately
                            if (resources.cssUrl != null) {
                                scene.getStylesheets().add(resources.cssUrl.toExternalForm());
                            }
                            themeManager.applyTheme(scene);
                            
                            // Get controller and set user
                            DashboardController dashboardController = fxmlLoader.getController();
                            if (dashboardController == null) {
                                throw new IOException("Failed to get DashboardController from FXML loader");
                            }
                            
                            // Set user and update stage in one atomic operation
                            dashboardController.setUser(user);
                            
                            Stage stage = (Stage) loginButton.getScene().getWindow();
                            stage.setScene(scene);
                            stage.setTitle("CivilJoin - Dashboard");
                            stage.show();
                            
                            LOGGER.info("Dashboard navigation completed successfully");
                            
                        } catch (Exception e) {
                            LOGGER.log(Level.SEVERE, "Error in dashboard navigation", e);
                            handleNavigationError(e);
                        }
                    });
                },
                this::handleNavigationError
            );
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error initiating dashboard navigation", e);
            handleNavigationError(e);
        }
    }
    
    /**
     * Preload dashboard resources for faster loading
     */
    private DashboardResources preloadDashboardResources() {
        // Cache frequently used paths
        String[] dashboardPaths = {
            "view/dashboard.fxml",
            "/gov/civiljoin/view/dashboard.fxml"
        };
        
        URL dashboardUrl = null;
        for (String path : dashboardPaths) {
            dashboardUrl = CivilJoinApplication.class.getResource(path);
            if (dashboardUrl != null) break;
        }
        
        if (dashboardUrl == null) {
            dashboardUrl = CivilJoinApplication.class.getClassLoader()
                .getResource("gov/civiljoin/view/dashboard.fxml");
        }
        
        URL cssUrl = getClass().getResource("/gov/civiljoin/css/styles.css");
        
        return new DashboardResources(dashboardUrl, cssUrl);
    }
    
    /**
     * Handle navigation errors gracefully
     */
    private void handleNavigationError(Throwable error) {
        Platform.runLater(() -> {
            errorLabel.setText("Error loading dashboard: " + error.getMessage());
            errorLabel.setStyle("-fx-text-fill: #f44336;");
            loginButton.setDisable(false);
            loginButton.setText("Login");
        });
    }
    
    /**
     * Resource holder for preloaded dashboard assets
     */
    private static class DashboardResources {
        final URL dashboardUrl;
        final URL cssUrl;
        
        DashboardResources(URL dashboardUrl, URL cssUrl) {
            this.dashboardUrl = dashboardUrl;
            this.cssUrl = cssUrl;
        }
    }
    
    /**
     * Utility method to create fallback copies of FXML files if needed
     */
    private void ensureResourcesExist() {
        LOGGER.info("Ensuring resources exist...");
        try {
            // Paths to check
            String[] fxmlFiles = {
                "dashboard.fxml",
                "login.fxml",
                "register.fxml"
            };
            
            // First check if target directory exists
            String targetDir = "/home/dan/IdeaProjects/VibeCoded/target/classes/gov/civiljoin/view/";
            java.io.File dir = new java.io.File(targetDir);
            if (!dir.exists()) {
                LOGGER.info("Creating directory: " + targetDir);
                dir.mkdirs();
            }
            
            // Copy files if needed
            for (String file : fxmlFiles) {
                String sourcePath = "/home/dan/IdeaProjects/VibeCoded/src/main/resources/gov/civiljoin/view/" + file;
                String targetPath = targetDir + file;
                
                java.io.File sourceFile = new java.io.File(sourcePath);
                java.io.File targetFile = new java.io.File(targetPath);
                
                if (sourceFile.exists() && (!targetFile.exists() || sourceFile.lastModified() > targetFile.lastModified())) {
                    LOGGER.info("Copying file: " + sourcePath + " to " + targetPath);
                    java.nio.file.Files.copy(
                        sourceFile.toPath(),
                        targetFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    );
                }
            }
            
            // Also ensure CSS file is copied
            String cssSourcePath = "/home/dan/IdeaProjects/VibeCoded/src/main/resources/gov/civiljoin/css/styles.css";
            String cssTargetDir = "/home/dan/IdeaProjects/VibeCoded/target/classes/gov/civiljoin/css/";
            String cssTargetPath = cssTargetDir + "styles.css";
            
            java.io.File cssSourceFile = new java.io.File(cssSourcePath);
            java.io.File cssTargetDirFile = new java.io.File(cssTargetDir);
            java.io.File cssTargetFile = new java.io.File(cssTargetPath);
            
            if (cssSourceFile.exists()) {
                if (!cssTargetDirFile.exists()) {
                    LOGGER.info("Creating CSS directory: " + cssTargetDir);
                    cssTargetDirFile.mkdirs();
                }
                
                if (!cssTargetFile.exists() || cssSourceFile.lastModified() > cssTargetFile.lastModified()) {
                    LOGGER.info("Copying CSS file: " + cssSourcePath + " to " + cssTargetPath);
                    java.nio.file.Files.copy(
                        cssSourceFile.toPath(),
                        cssTargetFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    );
                }
            }
            LOGGER.info("Resources exist check completed");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error ensuring resources exist", e);
        }
    }
} 