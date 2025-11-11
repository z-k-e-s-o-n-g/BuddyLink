package com.example.BuddyLink.Controller;

import com.example.BuddyLink.Navigation;
import com.example.BuddyLink.Session;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainController {
    @FXML private Button settingsButton;
    @FXML private TextField searchBar;
    @FXML private ListView<UserLite> chatList;
    @FXML private AnchorPane chatContainer;

    private static final OkHttpClient HTTP = new OkHttpClient();
    @SuppressWarnings("unused")
    private static final MediaType JSON = MediaType.parse("application/json");
    private static final String BASE = com.example.BuddyLink.net.Api.BASE;

    private final ObservableList<UserLite> users = FXCollections.observableArrayList();
    private FilteredList<UserLite> filtered;

    @FXML
    public void initialize() {
        chatList.setCellFactory(listView -> new ListCell<UserLite>() {
            @Override protected void updateItem(UserLite u, boolean empty) {
                super.updateItem(u, empty);
                setText(empty || u == null ? null : u.name);
            }
        });

        filtered = new FilteredList<>(users, u -> true);
        chatList.setItems(filtered);

        searchBar.textProperty().addListener((obs, ov, nv) -> {
            final String q = nv == null ? "" : nv.toLowerCase().trim();
            filtered.setPredicate(u -> u != null && u.name != null && u.name.toLowerCase().contains(q));
        });

        chatList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) openChat(newV);
        });

        new Thread(this::loadUsers).start();
    }

    private void loadUsers() {
        Request req = new Request.Builder()
                .url(com.example.BuddyLink.net.Api.BASE + "/users")
                .addHeader("Authorization", "Bearer " + Session.token)
                .get().build();

        try (Response r = HTTP.newCall(req).execute()) {
            if (!r.isSuccessful() || r.body() == null) { showError("Failed to load users (HTTP " + r.code() + ")."); return; }

            JsonArray arr = JsonParser.parseString(r.body().string()).getAsJsonArray();
            List<UserLite> loaded = new ArrayList<>();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                int id = o.get("id").getAsInt();
                if (id == Session.userId) continue;
                String name = o.has("name") && !o.get("name").isJsonNull() ? o.get("name").getAsString() : ("User " + id);
                loaded.add(new UserLite(id, name));
            }
            Platform.runLater(() -> users.setAll(loaded));
        } catch (Exception e) { showError("Error loading users: " + e.getMessage()); }
    }

    private void openChat(UserLite peer) {
        try {
            // Load from the same base as Navigation: /com/example/BuddyLink/View/ChatView.fxml
            FXMLLoader fx = new FXMLLoader(Navigation.class.getResource("View/ChatView.fxml"));
            Parent chatUI = fx.load();
            ChatController ctrl = fx.getController();
            ctrl.init(peer);

            chatContainer.getChildren().setAll(chatUI);
            AnchorPane.setTopAnchor(chatUI, 0.0);
            AnchorPane.setRightAnchor(chatUI, 0.0);
            AnchorPane.setBottomAnchor(chatUI, 0.0);
            AnchorPane.setLeftAnchor(chatUI, 0.0);
        } catch (IOException e) {
            showError("Could not open chat: " + e.getMessage());
        }
    }

    @FXML public void studyButton() { /* TODO */ }
    @FXML public void sendHelp() { /* TODO */ }
    @FXML public void openSettings() throws IOException { Navigation.goTo("settings.fxml", settingsButton); }

    private void showError(String msg) {
        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, msg).showAndWait());
    }

    public static class UserLite {
        public final int id; public final String name;
        public UserLite(int id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }
}
