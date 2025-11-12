package com.example.BuddyLink.Controller;

import com.example.BuddyLink.Session;
import com.example.BuddyLink.net.Api;
import com.google.gson.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import okhttp3.Request;
import okhttp3.Response;

import java.util.ArrayList;
import java.util.List;

public class UserInfoController {

    @FXML private ListView<String> interestsList;
    @FXML private TextArea aboutArea;
    @FXML private Button closeBtn;
    private Integer targetUserId = null;
    public void initForUserId(int userId) {
        this.targetUserId = userId;
        loadUserInfo();
    }

    public void initialize() {
        closeBtn.setOnAction(e -> ((Stage) closeBtn.getScene().getWindow()).close());
        if (targetUserId == null) {
            loadUserInfo();
        }
    }

    private void loadUserInfo() {
        new Thread(() -> {
            try {
                String url = (targetUserId != null)
                        ? Api.BASE + "/users/" + targetUserId
                        : Api.BASE + "/me";

                Request req = new Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer " + Session.token)
                        .get().build();

                try (Response r = Api.http.newCall(req).execute()) {
                    if (!r.isSuccessful() || r.body() == null)
                        throw new RuntimeException("Failed to load profile: " + r.code());

                    JsonObject me = JsonParser.parseString(r.body().string()).getAsJsonObject();
                    JsonArray subjects = me.getAsJsonArray("subjects");
                    String bio = me.has("bio") && !me.get("bio").isJsonNull()
                            ? me.get("bio").getAsString() : "(no bio provided)";

                    List<String> interests = new ArrayList<>();
                    String[] subjectNames = {
                            "Math", "English", "Other languages", "Physics", "Chemistry", "Biology", "Computer Science",
                            "Geography", "History", "Literature", "Economics", "Art", "Music", "Astronomy", "Olympiads", "Engineering"
                    };

                    if (subjects != null) {
                        for (int i = 0; i < subjects.size() && i < subjectNames.length; i++) {
                            if (subjects.get(i).getAsBoolean()) interests.add(subjectNames[i]);
                        }
                    }

                    Platform.runLater(() -> {
                        interestsList.getItems().setAll(interests);
                        aboutArea.setText(bio);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR,
                        "Failed to load user info: " + e.getMessage()).showAndWait());
            }
        }).start();
    }
}
