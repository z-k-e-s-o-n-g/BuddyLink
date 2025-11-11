package com.example.BuddyLink;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class Navigation {

    public static void goTo(String fxmlFile, Node eventSource) {
        try {
            // Load the FXML
            Parent root = FXMLLoader.load(Navigation.class.getResource("View/"+fxmlFile));

            // Create a new scene
            Scene scene = new Scene(root);
            URL cssUrl = Navigation.class.getResource("/light-theme.css");
            if (GlobalContainer.darkMode) {cssUrl = Navigation.class.getResource("/dark-theme.css");}

            if (cssUrl != null) {
                scene.getStylesheets().clear(); // just in case
                scene.getStylesheets().add(cssUrl.toExternalForm());
            } else {
                System.err.println("WARNING: light-theme.css not found on classpath!");
            }

            // Set the stage
            Stage stage = (Stage) eventSource.getScene().getWindow();
            stage.setScene(scene);
            stage.show();

            // Force re-apply CSS after scene is set
            scene.getRoot().applyCss();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}