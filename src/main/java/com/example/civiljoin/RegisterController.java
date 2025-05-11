package com.example.civiljoin;

/*  Import lahat ng JavaFX classes 
    https://docs.oracle.com/javafx/2/get_started/fxml_tutorial.html
    https://docs.oracle.com/javase/8/javafx/api/javafx/fxml/FXMLLoader.html
    https://docs.oracle.com/javase/8/javafx/api/javafx/scene/Scene.html
    https://docs.oracle.com/javase/8/javafx/api/javafx/scene/control/Button.html
    https://docs.oracle.com/javafx/2/api/javafx/scene/control/PasswordField.html
    https://docs.oracle.com/javase/8/javafx/api/javafx/scene/control/TextField.html
    https://docs.oracle.com/javase/8/javafx/api/javafx/stage/Stage.html
    https://stackoverflow.com/questions/2397714/java-try-and-catch-ioexception-must-be-caught-or-declared-to-be-thrown
*/
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import java.io.IOException;

public class RegisterController {

    /*  This is the field that calls for the 'onCreateAccountClick' method,
        and the 'rootPane.setOnMousePressed' method, and the 'rootPane.setOnMouseDragged' method */
    @FXML
    private AnchorPane rootPane;
        
    /*  This is the field for the username. Make sure to import the necessary JavaFX classes use "onMouseClicked"
        https://docs.oracle.com/javase/8/javafx/api/javafx/scene/input/MouseEvent.html
        This is unused for now. Use this for setting up the Username Logic */
    @FXML
    @SuppressWarnings("unused")
    private TextField usernameField;

    // This is the field for the password. This is unused for now. Use this to for setting up the Password Logic
    @FXML
    @SuppressWarnings("unused")
    private PasswordField passwordField;

    // This is the field for the password confirmation This is unused for now. Use this for setting up the Confirm Password Logic
    @FXML
    @SuppressWarnings("unused")
    private PasswordField confirmPasswordField;

    // This is the field for the key This is unused for now. Use this for setting up the 16-Digit UUID Key Logic
    @FXML
    @SuppressWarnings("unused")
    private TextField keyField;

    // This is the button for creating an account. Use this to Sign up Button Logic
    @FXML
    @SuppressWarnings("unused")
    private Button signUpButton;

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
        Lahat ng may 'stage', 'scene' mga variables for window design */
    private Stage stage;

    // this method is responsible for the dragging of the window. offset for the x and y coordinates
    private double xOffset = 0;
    private double yOffset = 0;

    // This is the offset for the window specifically for the x-axis for minimize, maximize and close buttons
    public void setStage(Stage stage) {

        // Add hover effects for minimizeButton
        minimizeButton.setOnMouseEntered(event -> minimizeButton.setStyle("-fx-background-color: #444444; -fx-text-fill: #00ff00;"));
        minimizeButton.setOnMouseExited(event -> minimizeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white;"));

        // Add hover effects for maximizeButton
        maximizeButton.setOnMouseEntered(event -> maximizeButton.setStyle("-fx-background-color: #444444; -fx-text-fill: #323232;"));
        maximizeButton.setOnMouseExited(event -> maximizeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white;"));

        // Add hover effects for closeButton
        closeButton.setOnMouseEntered(event -> closeButton.setStyle("-fx-background-color: #ff0000; -fx-text-fill: white;"));
        closeButton.setOnMouseExited(event -> closeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white;"));

        // Make the window draggable
        rootPane.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        // Make the window draggable
        rootPane.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });
        this.stage = stage;
    }


    // This method will return to the LoginPage.fxml file and set it as the current scene. Salamat kay ChatGPT
    @FXML
    private void onAlreadyHaveAccountClick() {
        try {
            // Load LoginPage.fxml
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("LoginPage.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 450, 385);
            
            // Get the current stage
            Stage stage = (Stage) signUpButton.getScene().getWindow();
            
            // Set a 'scene background' to transparent
            scene.setFill(null);
            
            // Pass the stage to the LoginController
            LoginController loginController = fxmlLoader.getController();
            loginController.setStage(stage);

            // Set the new scene
            stage.setScene(scene);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // This method is used to make the window minimize
    @FXML
    private void onMinimizeButtonClick() {
        if (stage != null) {
            stage.setIconified(true);
        }
    }

    // This method is used to make the window close
    @FXML
    private void onCloseButtonClick() {
        if (stage != null) {
            stage.close();
        }
    }
}
