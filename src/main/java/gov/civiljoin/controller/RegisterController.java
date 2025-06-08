package gov.civiljoin.controller;

import gov.civiljoin.CivilJoinApplication;
import gov.civiljoin.model.User;
import gov.civiljoin.service.AuthService;
import gov.civiljoin.util.ThemeManager;
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

/**
 * Controller for registration view
 */
public class RegisterController {
    @FXML
    private TextField usernameField;
    
    @FXML
    private TextField emailField;
    
    @FXML
    private PasswordField passwordField;
    
    @FXML
    private PasswordField confirmPasswordField;
    
    @FXML
    private TextField keyIdField;
    
    @FXML
    private Button registerButton;
    
    @FXML
    private Label errorLabel;
    
    private final AuthService authService = new AuthService();
    private final ThemeManager themeManager = ThemeManager.getInstance();
    
    /**
     * Handle register button action
     */
    @FXML
    private void handleRegister(ActionEvent event) {
        // Clear previous error messages
        errorLabel.setText("");
        
        // Get form values
        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        String keyId = keyIdField.getText().trim();
        
        // Validate form input
        if (!validateForm(username, email, password, confirmPassword, keyId)) {
            return;
        }
        
        // Register user
        try {
            User user = authService.register(username, password, keyId, email);
            if (user != null) {
                // Registration successful, navigate to dashboard
                navigateToDashboard(user);
            } else {
                errorLabel.setText("Registration failed: Username already exists or invalid key ID");
            }
        } catch (Exception e) {
            errorLabel.setText("Error during registration: " + e.getMessage());
        }
    }
    
    /**
     * Validate form input
     */
    private boolean validateForm(String username, String email, String password, 
                              String confirmPassword, String keyId) {
        // Check if all fields are filled
        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || 
            confirmPassword.isEmpty() || keyId.isEmpty()) {
            errorLabel.setText("All fields are required");
            return false;
        }
        
        // Validate username (at least 3 characters)
        if (username.length() < 3) {
            errorLabel.setText("Username must be at least 3 characters");
            return false;
        }
        
        // Validate email format
        if (!email.matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) {
            errorLabel.setText("Please enter a valid email address");
            return false;
        }
        
        // Validate password (at least 6 characters)
        if (password.length() < 6) {
            errorLabel.setText("Password must be at least 6 characters");
            return false;
        }
        
        // Check if passwords match
        if (!password.equals(confirmPassword)) {
            errorLabel.setText("Passwords do not match");
            return false;
        }
        
        // Validate key ID format (16 alphanumeric characters)
        if (!keyId.matches("[A-Za-z0-9]{16}")) {
            errorLabel.setText("Key ID must be exactly 16 alphanumeric characters");
            return false;
        }
        
        return true;
    }
    
    /**
     * Handle back to login button action
     */
    @FXML
    private void handleBackToLogin(ActionEvent event) {
        try {
            // Load the login view
            FXMLLoader fxmlLoader = new FXMLLoader(CivilJoinApplication.class.getResource("view/login.fxml"));
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
            Stage stage = (Stage) registerButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("CivilJoin - Login");
            stage.show();
        } catch (IOException e) {
            errorLabel.setText("Error loading login view: " + e.getMessage());
        }
    }
    
    /**
     * Navigate to dashboard view
     */
    private void navigateToDashboard(User user) {
        try {
            // Try multiple ways to load the dashboard
            URL resourceUrl = null;
            String[] possiblePaths = {
                "view/dashboard.fxml",                  // Relative path
                "/gov/civiljoin/view/dashboard.fxml",   // Absolute path
                "../view/dashboard.fxml",               // Go up one level
                "../../resources/gov/civiljoin/view/dashboard.fxml" // Try resources dir
            };
            
            for (String path : possiblePaths) {
                resourceUrl = CivilJoinApplication.class.getResource(path);
                if (resourceUrl != null) {
                    break;
                }
            }
            
            // If still not found, try class loader
            if (resourceUrl == null) {
                resourceUrl = CivilJoinApplication.class.getClassLoader().getResource("gov/civiljoin/view/dashboard.fxml");
            }
            
            if (resourceUrl == null) {
                // Last resort - try direct file path
                String targetPath = "/home/dan/IdeaProjects/VibeCoded/target/classes/gov/civiljoin/view/dashboard.fxml";
                var file = new java.io.File(targetPath);
                if (file.exists()) {
                    resourceUrl = file.toURI().toURL();
                } else {
                    throw new IOException("Dashboard view not found after trying multiple paths");
                }
            }
            
            FXMLLoader fxmlLoader = new FXMLLoader(resourceUrl);
            Scene scene = new Scene(fxmlLoader.load());
            
            // Add the CSS stylesheet
            String cssPath = "/gov/civiljoin/css/styles.css";
            URL cssUrl = getClass().getResource(cssPath);
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
            
            // Apply current theme to the new scene
            themeManager.applyTheme(scene);
            
            // Set user in the controller
            DashboardController dashboardController = fxmlLoader.getController();
            dashboardController.setUser(user);
            
            // Get current stage and set new scene
            Stage stage = (Stage) registerButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("CivilJoin - Dashboard");
            stage.show();
        } catch (IOException e) {
            errorLabel.setText("Error loading dashboard: " + e.getMessage());
        }
    }
} 