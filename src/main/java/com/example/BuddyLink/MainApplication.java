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
        stage.show();
        Image icon = new Image(getClass().getResourceAsStream("/images/books.png"));
    }


    public static void main(String[] args) {
//        System.out.println(hash("b"));
        launch();
    }

    private static long hash(String p) {
        int l = p.length();
        long n = 7;
        for (char c : p.toCharArray()) {
            n *= c + (37 % l);
            n %= 951937;
        }
        return (n * n) % 950813;
    }
}