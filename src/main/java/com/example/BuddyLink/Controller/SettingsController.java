package com.example.BuddyLink.Controller;

import com.example.BuddyLink.GlobalContainer;
import com.example.BuddyLink.Navigation;
import com.example.BuddyLink.Session;
import com.google.gson.JsonObject;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.ImageView;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.Optional;

import static com.example.BuddyLink.GlobalContainer.darkMode;
import static com.example.BuddyLink.net.Api.BASE;   // keep if you like; otherwise use Api.BASE

public class SettingsController {
    @FXML private Button closeButton;
    @FXML private Button logoutButton;
    @FXML private Button subjectsBtn;
    @FXML private ImageView toggleSwitch;
    @FXML private Button changebgBtn;

    // Local HTTP client for this controller
    private static final OkHttpClient HTTP = new OkHttpClient();
    private static final MediaType JSON = MediaType.parse("application/json");

    @FXML
    public void changebg() {
        var chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Choose Background Image");
        chooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter(
                        "Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"
                )
        );

        var file = chooser.showOpenDialog(changebgBtn.getScene().getWindow());
        if (file != null) {
            String imageUrl = file.toURI().toString();
            GlobalContainer.backgroundImageUrl = imageUrl;
            alert("Background image updated!");
        }
    }

    @FXML
    public void closeSettings() {
        Navigation.goTo("main.fxml", closeButton);
    }

    @FXML
    public void logout() {
        Navigation.goTo("login.fxml", logoutButton);
    }

    @FXML
    public void updateSubjects() {
        Navigation.goTo("onboarding.fxml", subjectsBtn);
    }

    @FXML
    public void lightdarkmode(){
        Scene scene = closeButton.getScene();
        if (darkMode) {
            scene.getStylesheets().remove(getClass().getResource("/dark-theme.css").toExternalForm());
            scene.getStylesheets().add(getClass().getResource("/light-theme.css").toExternalForm());
        } else {
            scene.getStylesheets().remove(getClass().getResource("/light-theme.css").toExternalForm());
            scene.getStylesheets().add(getClass().getResource("/dark-theme.css").toExternalForm());
        }
        darkMode = !darkMode;
    }

    private void alert(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg).showAndWait();
    }

    @FXML
    public void changename(){
        TextInputDialog dialog = new TextInputDialog(Session.name != null ? Session.name : "Name");
        dialog.setTitle("Change display name");
        dialog.setHeaderText("Please enter your name:");
        dialog.setContentText("Name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("name", name);

                Request req = new Request.Builder()
                        .url(BASE + "/me/username")
                        .put(RequestBody.create(body.toString(), JSON))
                        .addHeader("Authorization", "Bearer " + Session.token)
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    if (!r.isSuccessful()) {
                        alert("Change failed: HTTP " + r.code());
                        return;
                    }
                }

                Session.name = name; // Update locally after success
                alert("Username updated successfully!");
            } catch (Exception e) {
                e.printStackTrace();
                alert("Error: " + e.getMessage());
            }
        });
    }
}
