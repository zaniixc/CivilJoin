package gov.civiljoin.controller;

import gov.civiljoin.model.User;
import gov.civiljoin.service.AuthService;
import gov.civiljoin.util.NotificationManager;
import gov.civiljoin.util.ThemeManager;
import gov.civiljoin.util.NavigationManager;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.geometry.Pos;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * Controller for the Settings view
 * Handles user profile and password management
 */
public class SettingsController implements Initializable {
    private static final Logger LOGGER = Logger.getLogger(SettingsController.class.getName());
    private final ThemeManager themeManager = ThemeManager.getInstance();
    private final AuthService authService = new AuthService();
    private final NavigationManager navigationManager = NavigationManager.getInstance();
    private final NotificationManager notificationManager = NotificationManager.getInstance();
    
    @FXML private VBox settingsContainer;
    @FXML private VBox notificationArea;
    @FXML private Label titleLabel;
    @FXML private Label profileLabel;
    @FXML private GridPane profileGrid;
    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private Button updateProfileButton;
    @FXML private Button deleteAccountButton;
    
    @FXML private Label passwordLabel;
    @FXML private GridPane passwordGrid;
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button changePasswordButton;
    
    @FXML private VBox deleteAccountSection;
    @FXML private Label deleteAccountLabel;
    @FXML private Label deleteWarningLabel;
    
    private User currentUser;
    private boolean confirmingDeletion = false;
    
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Initialize the view with theme
        applyThemeToComponents();
        
        // Set up notification area
        notificationManager.setNotificationContainer(notificationArea);
    }
    
    /**
     * Apply theme styling to components
     */
    private void applyThemeToComponents() {
        boolean isDark = themeManager.isDarkMode();
        
        // Main container
        settingsContainer.setStyle("-fx-background-color: " + 
            (isDark ? "#2C3E50" : "#f8f9fa") + ";");
        
        // Heading labels
        titleLabel.setStyle("-fx-text-fill: " + 
            (isDark ? "#ECF0F1" : "#2C3E50") + "; -fx-font-size: 22px; -fx-font-weight: bold;");
            
        profileLabel.setStyle("-fx-text-fill: " + 
            (isDark ? "#ECF0F1" : "#2C3E50") + "; -fx-font-size: 16px; -fx-font-weight: bold;");
            
        passwordLabel.setStyle("-fx-text-fill: " + 
            (isDark ? "#ECF0F1" : "#2C3E50") + "; -fx-font-size: 16px; -fx-font-weight: bold;");
        
        // Form fields - handle all labels in both grids
        profileGrid.getChildren().stream()
            .filter(node -> node instanceof Label)
            .forEach(label -> label.setStyle("-fx-text-fill: " + 
                (isDark ? "#ECF0F1" : "#2C3E50") + ";"));
                
        passwordGrid.getChildren().stream()
            .filter(node -> node instanceof Label)
            .forEach(label -> label.setStyle("-fx-text-fill: " + 
                (isDark ? "#ECF0F1" : "#2C3E50") + ";"));
                
        // Text fields styling
        String textFieldStyle = isDark ? 
            "-fx-background-color: #34495E; -fx-text-fill: #ECF0F1; -fx-prompt-text-fill: #95A5A6;" :
            "-fx-background-color: white; -fx-text-fill: #2C3E50;";
            
        usernameField.setStyle(textFieldStyle);
        emailField.setStyle(textFieldStyle);
        currentPasswordField.setStyle(textFieldStyle);
        newPasswordField.setStyle(textFieldStyle);
        confirmPasswordField.setStyle(textFieldStyle);
        
        // Buttons styling
        String buttonStyle = isDark ?
            "-fx-background-color: #34495E; -fx-text-fill: #ECF0F1; -fx-background-radius: 5;" :
            "-fx-background-color: #2C3E50; -fx-text-fill: #ECF0F1; -fx-background-radius: 5;";
            
        updateProfileButton.setStyle(buttonStyle);
        changePasswordButton.setStyle(buttonStyle);
        
        // Delete account button styling
        String dangerButtonStyle = isDark ?
            "-fx-background-color: #E74C3C; -fx-text-fill: #ECF0F1; -fx-background-radius: 5;" :
            "-fx-background-color: #E74C3C; -fx-text-fill: #ECF0F1; -fx-background-radius: 5;";
        deleteAccountButton.setStyle(dangerButtonStyle);
    }
    
    /**
     * Set up the controller with user data
     */
    public void setup(User user) {
        this.currentUser = user;
        
        // Populate user data
        usernameField.setText(user.getUsername());
        emailField.setText(user.getEmail());
        
        // Clear password fields
        currentPasswordField.clear();
        newPasswordField.clear();
        confirmPasswordField.clear();
        
        // Reset deletion confirmation
        confirmingDeletion = false;
        deleteAccountButton.setText("Delete Account");
        
        // Show/hide delete account section based on role
        boolean isRegularUser = user.getRole() == User.Role.USER;
        deleteAccountSection.setVisible(isRegularUser);
        deleteAccountSection.setManaged(isRegularUser);
    }
    
    /**
     * Handle update profile button action
     */
    @FXML
    private void handleUpdateProfile() {
        String newUsername = usernameField.getText().trim();
        String newEmail = emailField.getText().trim();
        
        // Simple validation
        if (newUsername.isEmpty()) {
            notificationManager.showNotification("Username cannot be empty", NotificationManager.NotificationType.ERROR);
            return;
        }
        
        if (newEmail.isEmpty() || !newEmail.contains("@")) {
            notificationManager.showNotification("Please enter a valid email address", NotificationManager.NotificationType.ERROR);
            return;
        }
        
        // Check if data is the same as current data
        boolean usernameChanged = !newUsername.equals(currentUser.getUsername());
        boolean emailChanged = !newEmail.equals(currentUser.getEmail());
        
        if (!usernameChanged && !emailChanged) {
            notificationManager.showNotification("No changes detected. Profile information is the same as current data.", NotificationManager.NotificationType.INFO);
            return;
        }
        
        try {
            // Update the user profile in the database
            boolean success = authService.updateUser(
                currentUser.getId(), 
                newUsername, 
                newEmail, 
                null,  // Don't update role when changing profile 
                currentUser
            );
            
            if (success) {
                // Update the current user object with new values
                currentUser.setUsername(newUsername);
                currentUser.setEmail(newEmail);
                
                // Show what was updated
                String changeMessage = "Profile updated successfully";
                if (usernameChanged && emailChanged) {
                    changeMessage += " (username and email changed)";
                } else if (usernameChanged) {
                    changeMessage += " (username changed)";
                } else if (emailChanged) {
                    changeMessage += " (email changed)";
                }
                
                LOGGER.info("Profile updated successfully for user: " + currentUser.getId());
                notificationManager.showNotification(changeMessage, NotificationManager.NotificationType.SUCCESS);
            } else {
                LOGGER.warning("Profile update failed for user: " + currentUser.getId());
                notificationManager.showNotification("Could not update profile. Username might already be taken.", NotificationManager.NotificationType.ERROR);
            }
        } catch (Exception e) {
            LOGGER.severe("Exception during profile update: " + e.getMessage());
            notificationManager.showNotification("An error occurred: " + e.getMessage(), NotificationManager.NotificationType.ERROR);
        }
    }
    
    /**
     * Handle change password button action
     */
    @FXML
    private void handleChangePassword() {
        String currentPassword = currentPasswordField.getText();
        String newPassword = newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        
        if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            notificationManager.showNotification("All password fields are required", NotificationManager.NotificationType.ERROR);
            return;
        }
        
        if (!newPassword.equals(confirmPassword)) {
            notificationManager.showNotification("New passwords do not match", NotificationManager.NotificationType.ERROR);
            return;
        }
        
        // Check if new password is the same as current password
        if (currentPassword.equals(newPassword)) {
            notificationManager.showNotification("New password cannot be the same as current password", NotificationManager.NotificationType.WARNING);
            return;
        }
        
        // Verify current password first
        if (!authService.verifyPassword(currentUser, currentPassword)) {
            notificationManager.showNotification("Current password is incorrect", NotificationManager.NotificationType.ERROR);
            return;
        }
        
        if (authService.changePassword(currentUser, currentPassword, newPassword)) {
            notificationManager.showNotification("Password changed successfully", NotificationManager.NotificationType.SUCCESS);
            clearPasswordFields();
        } else {
            notificationManager.showNotification("Failed to change password. Please try again.", NotificationManager.NotificationType.ERROR);
        }
    }
    
    /**
     * Handle account deletion with sophisticated multi-step confirmation
     */
    @FXML
    private void handleDeleteAccount() {
        if (currentUser.getRole() != User.Role.USER) {
            notificationManager.showNotification("Only regular users can delete their accounts", NotificationManager.NotificationType.ERROR);
            return;
        }
        
        // Step 1: Initial confirmation
        notificationManager.showConfirmation(
            "⚠️ Account Deletion Warning\n\n" +
            "This will permanently delete your account and all associated data:\n" +
            "• All your posts and comments\n" +
            "• Your feedback submissions\n" +
            "• Your activity history\n\n" +
            "This action cannot be undone!",
            () -> {
                // Step 2: Password verification
                notificationManager.showPasswordConfirmation(
                    "Enter your password to confirm account deletion:",
                    password -> {
                        // Verify password first
                        if (!authService.verifyPassword(currentUser, password)) {
                            notificationManager.showNotification("Incorrect password. Account deletion cancelled.", NotificationManager.NotificationType.ERROR);
                            return;
                        }
                        
                        // Step 3: Final confirmation with text input
                        notificationManager.showTextConfirmation(
                            "⚠️ FINAL WARNING ⚠️\n\n" +
                            "You are about to permanently delete your account.\n" +
                            "ALL your data will be lost forever.\n\n" +
                            "This action is irreversible!",
                            "DELETE",
                            () -> {
                                // Execute the account deletion
                                executeAccountDeletion(password);
                            }
                        );
                    }
                );
            },
            () -> {
                // User cancelled - show cancellation message
                notificationManager.showNotification("Account deletion cancelled", NotificationManager.NotificationType.INFO);
            }
        );
    }
    
    /**
     * Execute the actual account deletion
     */
    private void executeAccountDeletion(String password) {
        try {
            boolean success = authService.deleteUserAccount(currentUser.getId(), password);
            
            if (success) {
                notificationManager.showNotification("Account deleted successfully. Redirecting to login...", NotificationManager.NotificationType.SUCCESS);
                
                // Navigate back to login after a short delay
                PauseTransition delay = new PauseTransition(Duration.seconds(3));
                delay.setOnFinished(e -> {
                    try {
                        navigationManager.navigateToLogin();
                    } catch (Exception ex) {
                        LOGGER.severe("Error navigating to login after account deletion: " + ex.getMessage());
                    }
                });
                delay.play();
            } else {
                notificationManager.showNotification("Failed to delete account. Please try again or contact support.", NotificationManager.NotificationType.ERROR);
            }
        } catch (Exception e) {
            LOGGER.severe("Exception during account deletion: " + e.getMessage());
            notificationManager.showNotification("An error occurred while deleting your account: " + e.getMessage(), NotificationManager.NotificationType.ERROR);
        }
    }
    
    private void clearPasswordFields() {
        currentPasswordField.clear();
        newPasswordField.clear();
        confirmPasswordField.clear();
    }
    
    /**
     * Update view when theme changes
     */
    public void updateTheme() {
        applyThemeToComponents();
    }
    
    /**
     * Get the root node for this controller
     * @return The root VBox container
     */
    public Node getRoot() {
        return settingsContainer;
    }
}