package com.example.BuddyLink.Controller;

import com.example.BuddyLink.GlobalContainer;
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
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MainController {

    @FXML private Button settingsButton, studyButton;
    @FXML private TextField searchBar;
    @FXML private ListView<UserLite> chatList;
    @FXML private AnchorPane chatContainer;
    @FXML private ChoiceBox<String> filterchoice;

    private static final OkHttpClient HTTP = new OkHttpClient();
    @SuppressWarnings("unused")
    private static final MediaType JSON = MediaType.parse("application/json");

    private final ObservableList<UserLite> users = FXCollections.observableArrayList();
    private FilteredList<UserLite> filtered;

    private void resizeBackground(ImageView bg) {
        if (bg.getImage() == null || chatContainer.getWidth() == 0 || chatContainer.getHeight() == 0) return;

        double containerRatio = chatContainer.getWidth() / chatContainer.getHeight();
        double imageRatio = bg.getImage().getWidth() / bg.getImage().getHeight();

        if (containerRatio > imageRatio) {
            // container wider → fit by height, crop sides
            bg.setFitHeight(chatContainer.getHeight());
            bg.setFitWidth(chatContainer.getHeight() * imageRatio);
        } else {
            // container taller → fit by width, crop top/bottom
            bg.setFitWidth(chatContainer.getWidth());
            bg.setFitHeight(chatContainer.getWidth() / imageRatio);
        }

        AnchorPane.setTopAnchor(bg, 0.0);
        AnchorPane.setLeftAnchor(bg, 0.0);
    }

    @FXML
    public void initialize() {
        if (GlobalContainer.backgroundImageUrl != null) {
            try {
                Image img = new Image(GlobalContainer.backgroundImageUrl, 0, 0, true, true);

                // ✅ "cover" = true, "contain" = false  (cropped wallpaper style)
                BackgroundSize bSize = new BackgroundSize(
                        100, 100, true, true, false, true
                );
                BackgroundImage bImg = new BackgroundImage(
                        img,
                        BackgroundRepeat.NO_REPEAT,
                        BackgroundRepeat.NO_REPEAT,
                        BackgroundPosition.CENTER,
                        bSize
                );

                chatContainer.setBackground(new Background(bImg));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        // filter choice values
        filterchoice.setItems(FXCollections.observableArrayList("A-Z", "Recent", "Recommended"));
        filterchoice.getSelectionModel().select("Recent");
        filterchoice.setOnAction(e -> applySort());

        // list cell
        chatList.setCellFactory(listView -> new ListCell<>() {
            @Override protected void updateItem(UserLite u, boolean empty) {
                super.updateItem(u, empty);
                setText(empty || u == null ? null : u.name);
            }
        });

        // filtered wrapper + search
        filtered = new FilteredList<>(users, u -> true);
        chatList.setItems(filtered);

        searchBar.textProperty().addListener((obs, ov, nv) -> {
            String q = nv == null ? "" : nv.toLowerCase().trim();
            filtered.setPredicate(u -> u != null && u.name != null && u.name.toLowerCase().contains(q));
        });

        // open chat on select
        chatList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) openChat(newV);
        });

        // load users
        new Thread(this::loadUsers).start();
    }

    private void applySort() {
        String mode = filterchoice.getSelectionModel().getSelectedItem();
        if ("A-Z".equals(mode)) {
            FXCollections.sort(users, Comparator.comparing(u -> u.name == null ? "" : u.name, String.CASE_INSENSITIVE_ORDER));
        } else if ("Recent".equals(mode)) {
            // Keeps server order
        } else if ("Recommended".equals(mode)) {
            // Placeholder
        }
    }

    private void loadUsers() {
        Request req = new Request.Builder()
                .url(com.example.BuddyLink.net.Api.BASE + "/users")
                .addHeader("Authorization", "Bearer " + Session.token)
                .get().build();

        try (Response r = HTTP.newCall(req).execute()) {
            if (!r.isSuccessful() || r.body() == null) {
                showError("Failed to load users (HTTP " + r.code() + ").");
                return;
            }
            JsonArray arr = JsonParser.parseString(r.body().string()).getAsJsonArray();
            List<UserLite> loaded = new ArrayList<>();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                int id = o.get("id").getAsInt();
                if (id == Session.userId) continue; // skip self
                String name = (o.has("name") && !o.get("name").isJsonNull())
                        ? o.get("name").getAsString()
                        : ("User " + id);
                loaded.add(new UserLite(id, name));
            }
            Platform.runLater(() -> {
                users.setAll(loaded);
                applySort(); // apply current sort mode after load
            });
        } catch (Exception e) {
            showError("Error loading users: " + e.getMessage());
        }
    }

    private void openChat(UserLite peer) {
        try {
            FXMLLoader fx = new FXMLLoader(Navigation.class.getResource("View/ChatView.fxml"));
            Parent chatUI = fx.load();
            ChatController ctrl = fx.getController();

            // Pass a ChatController.UserLite
            ctrl.init(new UserLite(peer.id, peer.name));

            chatContainer.getChildren().setAll(chatUI);
            AnchorPane.setTopAnchor(chatUI, 0.0);
            AnchorPane.setRightAnchor(chatUI, 0.0);
            AnchorPane.setBottomAnchor(chatUI, 0.0);
            AnchorPane.setLeftAnchor(chatUI, 0.0);
        } catch (IOException e) {
            showError("Could not open chat: " + e.getMessage());
        }
    }

    @FXML
    public void study() {
        Navigation.goTo("time_input.fxml", studyButton);
    }

    @FXML
    public void openSettings() throws IOException {
        Navigation.goTo("settings.fxml", settingsButton);
    }

    private void showError(String msg) {
        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, msg).showAndWait());
    }

    // Lightweight model for the list
    public static class UserLite {
        public final int id;
        public final String name;
        public UserLite(int id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }

}
