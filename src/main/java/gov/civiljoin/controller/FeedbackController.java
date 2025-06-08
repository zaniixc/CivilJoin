package gov.civiljoin.controller;

import gov.civiljoin.model.User;
import gov.civiljoin.util.AlertUtil;
import gov.civiljoin.util.ThemeManager;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * Controller for the Feedback view
 * Handles user feedback submission and display
 */
public class FeedbackController implements Initializable {
    private static final Logger LOGGER = Logger.getLogger(FeedbackController.class.getName());
    private final ThemeManager themeManager = ThemeManager.getInstance();
    
    @FXML private VBox contentContainer;
    @FXML private Label titleLabel;
    @FXML private TextArea feedbackTextArea;
    @FXML private Button submitButton;
    
    private User currentUser;
    
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Initialize the view
        applyThemeToComponents();
    }
    
    /**
     * Apply theme styling to components
     */
    private void applyThemeToComponents() {
        if (themeManager.isDarkMode()) {
            contentContainer.setStyle("-fx-background-color: #2c313a;");
            titleLabel.setStyle("-fx-text-fill: #f8f9fa; -fx-font-size: 22px; -fx-font-weight: bold;");
            feedbackTextArea.setStyle("-fx-control-inner-background: #3a3f4b; -fx-text-fill: #e2e8f0;");
            submitButton.setStyle("-fx-background-color: #4a5568; -fx-text-fill: white; -fx-background-radius: 5;");
        } else {
            contentContainer.setStyle("-fx-background-color: #f8f9fa;");
            titleLabel.setStyle("-fx-text-fill: #000000; -fx-font-size: 22px; -fx-font-weight: bold;");
            feedbackTextArea.setStyle("-fx-control-inner-background: white; -fx-text-fill: #333333;");
            submitButton.setStyle("-fx-background-color: #000000; -fx-text-fill: white; -fx-background-radius: 5;");
        }
    }
    
    /**
     * Set up the controller with user data
     */
    public void setup(User user) {
        this.currentUser = user;
    }
    
    /**
     * Handle the submit button action
     */
    @FXML
    private void handleSubmit() {
        String feedbackText = feedbackTextArea.getText();
        
        if (feedbackText == null || feedbackText.trim().isEmpty()) {
            AlertUtil.showErrorAlert("Empty Feedback", "Please enter your feedback before submitting.");
            return;
        }
        
        // Here you would save the feedback to database
        // For now, just show a success message
        LOGGER.info("User " + currentUser.getUsername() + " submitted feedback: " + feedbackText);
        AlertUtil.showSuccessAlert("Feedback Submitted", 
            "Thank you for your feedback. We appreciate your input!");
        
        // Clear the text area
        feedbackTextArea.clear();
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
        return contentContainer;
    }
} 