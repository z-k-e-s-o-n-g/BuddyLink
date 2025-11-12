package com.example.BuddyLink.Controller;

import com.example.BuddyLink.Navigation;
import com.example.BuddyLink.net.Api;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;

public class BioController {

    @FXML private TextArea textArea;
    @FXML private Button button;

    @FXML
    public void submit() {
        String bio = textArea.getText() == null ? "" : textArea.getText().trim();

        if (bio.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "Please write a short bio.").showAndWait();
            return;
        }
        if (bio.length() > 500) {
            new Alert(Alert.AlertType.WARNING, "Bio is too long (max 500 characters).").showAndWait();
            return;
        }

        button.setDisable(true);

        new Thread(() -> {
            try {

                Api.updateBio(bio);

                Platform.runLater(() -> {

                    Navigation.goTo("main.fxml", button);
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    button.setDisable(false);
                    new Alert(Alert.AlertType.ERROR, "Failed to save bio: " + e.getMessage()).showAndWait();
                });
            }
        }).start();
    }
}
