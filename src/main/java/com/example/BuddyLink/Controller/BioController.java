package com.example.BuddyLink.Controller;

import com.example.BuddyLink.Navigation;
import com.example.BuddyLink.net.Api;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;

public class BioController {

    @FXML private TextArea textArea;   // fx:id from FXML
    @FXML private Button button;       // fx:id from FXML

    @FXML
    public void submit() {
        String bio = textArea.getText() == null ? "" : textArea.getText().trim();

        // Simple client-side guard (optional)
        if (bio.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "Please write a short bio.").showAndWait();
            return;
        }
        if (bio.length() > 500) { // keep it reasonable
            new Alert(Alert.AlertType.WARNING, "Bio is too long (max 500 characters).").showAndWait();
            return;
        }

        button.setDisable(true);

        new Thread(() -> {
            try {
                // Api.updateBio must exist (see earlier snippet). It uses Session.token internally.
                Api.updateBio(bio);

                Platform.runLater(() -> {
                    // Navigate back to main UI after saving
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
