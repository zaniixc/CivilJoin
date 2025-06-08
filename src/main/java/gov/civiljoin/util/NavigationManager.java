package gov.civiljoin.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Singleton class to manage navigation between different screens
 */
public class NavigationManager {
    private static final Logger LOGGER = Logger.getLogger(NavigationManager.class.getName());
    private static NavigationManager instance;
    private Stage primaryStage;

    private NavigationManager() {}

    public static NavigationManager getInstance() {
        if (instance == null) {
            instance = new NavigationManager();
        }
        return instance;
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    /**
     * Navigate to the login screen
     */
    public void navigateToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/gov/civiljoin/view/Login.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/gov/civiljoin/css/styles.css").toExternalForm());
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (IOException e) {
            LOGGER.severe("Failed to navigate to login screen: " + e.getMessage());
            AlertUtil.showErrorAlert("Navigation Error", "Failed to navigate to login screen");
        }
    }

    /**
     * Navigate to the dashboard screen
     */
    public void navigateToDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/gov/civiljoin/view/Dashboard.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/gov/civiljoin/css/styles.css").toExternalForm());
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (IOException e) {
            LOGGER.severe("Failed to navigate to dashboard: " + e.getMessage());
            AlertUtil.showErrorAlert("Navigation Error", "Failed to navigate to dashboard");
        }
    }
} 