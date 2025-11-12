package com.example.BuddyLink;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MainApplication.class.getResource("View/login.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        if (GlobalContainer.darkMode) {scene.getStylesheets().add(getClass().getResource("/dark-theme.css").toExternalForm());}
        else {scene.getStylesheets().add(getClass().getResource("/light-theme.css").toExternalForm());}
        stage.setTitle("Log in");
        stage.setScene(scene);
        Image icon = new Image(getClass().getResourceAsStream("/images/books.png"));
        stage.getIcons().add(icon);
        stage.show();
    }


    public static void main(String[] args) {
        launch();
    }
}