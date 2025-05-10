package com.example.civiljoin;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle; // for changing window background like adding a rounded corners https://docs.oracle.com/javase/8/javafx/api/javafx/stage/StageStyle.html
import javafx.scene.paint.Color; // import colors

import java.io.IOException;

public class HelloApplication extends Application {    
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("RegisterPage.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 450, 385);
        stage.setTitle("CivilJoin");
        stage.initStyle(StageStyle.TRANSPARENT); // Glass-like window effect
        scene.setFill(Color.TRANSPARENT); // Set the scene background to transparent
        RegisterController controller = fxmlLoader.getController(); // // Added a variable responsible for window dragging
        controller.setStage(stage);

        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}