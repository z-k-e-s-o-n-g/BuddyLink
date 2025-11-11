package com.example.BuddyLink.Controller;

import com.example.BuddyLink.Navigation;
import com.example.BuddyLink.Session;
import com.example.BuddyLink.net.Api;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.sql.ResultSet;
import java.util.Collections;

public class OnboardingController {
    @FXML private CheckBox maBox, elBox, mtBox, pcBox, cmBox, blBox, csBox, geBox,
            hsBox, liBox, ecBox, arBox, muBox, asBox, olBox, enBox;
    @FXML private Button confirmButton;

//    public void initialize(){
//        new Thread(() -> {
//            try {
//                Request req = new Request.Builder()
//                        .url(Api.BASE + "/me")
//                        .addHeader("Authorization", "Bearer " + Session.token)
//                        .get()
//                        .build();
//
//                try (Response r = Api.http.newCall(req).execute()) {
//                    if (!r.isSuccessful() || r.body() == null)
//                        throw new RuntimeException("Failed to load profile: HTTP " + r.code());
//
//                    JsonObject me = JsonParser.parseString(r.body().string()).getAsJsonObject();
//                    JsonArray subjects = me.getAsJsonArray("subjects");
//
//                    if (subjects != null && subjects.size() == 15) {
//                        Platform.runLater(() -> {
//                            maBox.setSelected(subjects.get(0).getAsBoolean());
//                            elBox.setSelected(subjects.get(1).getAsBoolean());
//                            mtBox.setSelected(subjects.get(2).getAsBoolean());
//                            pcBox.setSelected(subjects.get(3).getAsBoolean());
//                            cmBox.setSelected(subjects.get(4).getAsBoolean());
//                            blBox.setSelected(subjects.get(5).getAsBoolean());
//                            csBox.setSelected(subjects.get(6).getAsBoolean());
//                            geBox.setSelected(subjects.get(7).getAsBoolean());
//                            hsBox.setSelected(subjects.get(8).getAsBoolean());
//                            liBox.setSelected(subjects.get(9).getAsBoolean());
//                            ecBox.setSelected(subjects.get(10).getAsBoolean());
//                            arBox.setSelected(subjects.get(11).getAsBoolean());
//                            muBox.setSelected(subjects.get(12).getAsBoolean());
//                            asBox.setSelected(subjects.get(13).getAsBoolean());
//                            olBox.setSelected(subjects.get(14).getAsBoolean());
//                            // enBox left unchecked intentionally if not stored
//                            if (subjects.size() > 15)
//                                enBox.setSelected(subjects.get(15).getAsBoolean());
//                        });
//                    }
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR,
//                        "Failed to load user subjects: " + e.getMessage()).showAndWait());
//            }
//        }).start();
//    }

    @FXML
    public void confirm() {
        boolean[] subjects = {
                maBox.isSelected(), elBox.isSelected(), mtBox.isSelected(), pcBox.isSelected(),
                cmBox.isSelected(), blBox.isSelected(), csBox.isSelected(), geBox.isSelected(),
                hsBox.isSelected(), liBox.isSelected(), ecBox.isSelected(), arBox.isSelected(),
                muBox.isSelected(), asBox.isSelected(), olBox.isSelected(), enBox.isSelected()
        };

        new Thread(() -> {
            try {
                Api.updateProfile(subjects, Collections.emptyList(), true);
                Platform.runLater(() -> Navigation.goTo("bio.fxml", confirmButton));
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR,
                        "Failed to update user: " + e.getMessage()).showAndWait());
            }
        }).start();
    }
}
