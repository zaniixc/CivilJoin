package com.example.civiljoin;

/*  Import all necessary JavaFX classes
    https://docs.oracle.com/javase/8/javafx/api/javafx/scene/layout/AnchorPane.html
*/
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.scene.layout.AnchorPane;
import java.io.IOException;

public class LoginController {

    /*  This is the field that calls for the 'onCreateAccountClick' method,
        and the 'rootPane.setOnMousePressed' method, and the 'rootPane.setOnMouseDragged' method */
    @FXML
    private AnchorPane rootPane;

    // This is the field for the username
    @FXML
    private TextField usernameField;

    // This is the field for the password
    @FXML
    private PasswordField passwordField;

    // This is the button for minimizing the window
    @FXML
    private Button minimizeButton;

    /*  This is the button for maximizing the window, Although it is not used in this code either only used for the hover effect
        Kasi nasa Register Page naman e unnecessary no need mag maximize */
    @FXML
    private Button maximizeButton;

    // This is the button for close the window
    @FXML
    private Button closeButton;

    /*  This is responsible for the window designs https://docs.oracle.com/javase/8/javafx/api/javafx/stage/Stage.html
        Lahat ng may 'stage', 'scene' mga variables for windwow design */
    private Stage stage;

    // This method is responsible for the dragging of the window. offset for the x and y coordinates
    private double xOffset = 0;
    private double yOffset = 0;

    public void setStage(Stage stage) {
        this.stage = stage;

        // Add hover effects for minimizeButton
        minimizeButton.setOnMouseEntered(event -> minimizeButton.setStyle("-fx-background-color: #444444; -fx-text-fill: #00ff00;"));
        minimizeButton.setOnMouseExited(event -> minimizeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white;"));

        // Add hover effects for maximizeButton
        maximizeButton.setOnMouseEntered(event -> maximizeButton.setStyle("-fx-background-color: #444444; -fx-text-fill: #323232;"));
        maximizeButton.setOnMouseExited(event -> maximizeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white;"));

        // Add hover effects for closeButton
        closeButton.setOnMouseEntered(event -> closeButton.setStyle("-fx-background-color: #ff0000; -fx-text-fill: white;"));
        closeButton.setOnMouseExited(event -> closeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white;"));

        // Initialize window dragging functionality
        enableWindowDragging();
    }

    private void enableWindowDragging() {
        // Make the window draggable
        rootPane.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        // Make the window draggable
        rootPane.setOnMouseDragged(event -> {
            if (stage != null) {
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            }
        });
    }

    @FXML
    private void onCreateAccountClick() {
        try {
            // Load RegisterPage.fxml
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("RegisterPage.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 450, 385);
            
            // Get the current stage
            Stage stage = (Stage) rootPane.getScene().getWindow();
            
            // Set scene background to transparent
            scene.setFill(null);
            
            // Pass the stage to the RegisterController
            RegisterController registerController = fxmlLoader.getController();
            registerController.setStage(stage);

            // Set the new scene
            stage.setScene(scene);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // This method is used to make the window minimize
    @FXML
    protected void onMinimizeButtonClick() {
        if (stage != null) {
            stage.setIconified(true);
        }
    }

    // This method is used to make the window close
    @FXML
    protected void onCloseButtonClick() {
        if (stage != null) {
            stage.close();
        }
    }
}
