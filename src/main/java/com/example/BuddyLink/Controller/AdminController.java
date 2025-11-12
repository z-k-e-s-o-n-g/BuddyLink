package com.example.BuddyLink.Controller;

import com.example.BuddyLink.Model.User;
import com.example.BuddyLink.Navigation;
import com.google.gson.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AdminController {

    // ui
    @FXML private TableView<UserRow> usersTable;
    @FXML private TableColumn<UserRow, Integer> colId;
    @FXML private TableColumn<UserRow, String>  colName;
    @FXML private TableColumn<UserRow, String>  colEmail;
    @FXML private TableColumn<UserRow, Boolean> colOnboarded;
    @FXML private Button refreshBtn;
    @FXML private Button deleteBtn;
    @FXML private Button resetBtn;
    @FXML private Button closeBtn;

    // http
    private static final String BASE = "http://localhost:7070";
    private static final String ADMIN_TOKEN = "dev-admin";
    private static final OkHttpClient http = new OkHttpClient();
    private static final Gson G = new Gson();

    @FXML
    public void close(){
        Navigation.goTo("login.fxml", closeBtn);
    }

    @FXML
    public void initialize() {
        // table columns
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colOnboarded.setCellValueFactory(new PropertyValueFactory<>("onboarded"));

        // load at start
        loadUsersAsync();
    }

    //button
    @FXML
    public void refresh() { loadUsersAsync(); }

    @FXML
    public void deleteSelected() {
        UserRow sel = usersTable.getSelectionModel().getSelectedItem();
        if (sel == null) { alert("Select a user first."); return; }
        if (!confirm("Delete user #" + sel.getId() + " (" + sel.getEmail() + ")?")) return;
        new Thread(() -> {
            Request req = new Request.Builder()
                    .url(BASE + "/admin/users/" + sel.getId())
                    .delete()
                    .addHeader("X-Admin", ADMIN_TOKEN)
                    .build();
            try (Response r = http.newCall(req).execute()) {
                if (!r.isSuccessful()) {
                    uiAlert("Delete failed: " + r.code() + " - " + bodySafe(r));
                    return;
                }
                uiInfo("User deleted.");
                loadUsersAsync(); // refresh list
            } catch (IOException e) {
                uiAlert("Delete failed: " + e.getMessage());
            }
        }).start();
    }

    @FXML
    public void resetAll() {
        if (!confirm("This will delete ALL users and ALL conversation history. Continue?")) return;
        User.resetTotalUsers();
        setButtonsDisabled(true);

        new Thread(() -> {
            int chatEndpointsHit = clearAllChatsInternal();

            // Now call your existing users reset endpoint
            boolean usersCleared = postNoBody("/admin/reset");

            StringBuilder sb = new StringBuilder();
            if (chatEndpointsHit > 0) {
                sb.append("Chats cleared (").append(chatEndpointsHit).append(" op").append(chatEndpointsHit>1?"s":"").append("). ");
            } else {
                sb.append("No chat-clear endpoint responded 2xx. ");
            }
            sb.append(usersCleared ? "Users cleared." : "Users clear FAILED.");

            if (usersCleared) {
                uiInfo(sb.toString());
                loadUsersAsync();
            } else {
                uiAlert(sb.toString());
                setButtonsDisabled(false);
            }
        }).start();
    }

    @FXML
    public void clearAllChatsOnly() {
        if (!confirm("This will delete ALL conversation history. Continue?")) return;

        setButtonsDisabled(true);
        new Thread(() -> {
            int count = clearAllChatsInternal();
            if (count > 0) {
                uiInfo("Chats cleared (" + count + " op" + (count>1?"s":"") + ").");
            } else {
                uiAlert("No chat-clear endpoint responded 2xx.");
            }
            setButtonsDisabled(false);
        }).start();
    }


    private int clearAllChatsInternal() {
        int ok = 0;
        String[] endpoints = new String[] {
                "/admin/chats/reset",
                "/admin/messages/reset",
                "/admin/rooms/reset",
                "/admin/conversations/reset",
                "/admin/files/reset"
        };
        for (String ep : endpoints) {
            if (postNoBody(ep)) ok++;
        }
        return ok;
    }

    private boolean postNoBody(String path) {
        Request req = new Request.Builder()
                .url(BASE + path)
                .post(RequestBody.create(new byte[0], null))
                .addHeader("X-Admin", ADMIN_TOKEN)
                .build();
        try (Response r = http.newCall(req).execute()) {
            return r.isSuccessful();
        } catch (IOException e) {
            return false;
        }
    }

    private void loadUsersAsync() {
        new Thread(() -> {
            setButtonsDisabled(true);
            Request req = new Request.Builder()
                    .url(BASE + "/admin/users")
                    .get()
                    .addHeader("X-Admin", ADMIN_TOKEN)
                    .build();

            try (Response r = http.newCall(req).execute()) {
                if (!r.isSuccessful()) {
                    uiAlert("Load failed: " + r.code() + " - " + bodySafe(r));
                    setButtonsDisabled(false);
                    return;
                }
                String json = bodySafe(r);
                JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
                List<UserRow> rows = new ArrayList<>();
                for (var el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    int id = o.get("id").getAsInt();
                    String name = o.get("name").isJsonNull() ? "" : o.get("name").getAsString();
                    String email = o.get("email").isJsonNull() ? "" : o.get("email").getAsString();
                    boolean onboarded = o.get("onboarded").getAsBoolean();
                    rows.add(new UserRow(id, name, email, onboarded));
                }
                Platform.runLater(() -> {
                    usersTable.getItems().setAll(rows);
                    setButtonsDisabled(false);
                });
            } catch (Exception e) {
                uiAlert("Load failed: " + e.getMessage());
                setButtonsDisabled(false);
            }
        }).start();
    }
    static void clearUploads() {
        java.nio.file.Path dir = java.nio.file.Paths.get("uploads");
        if (!java.nio.file.Files.exists(dir)) return;
        try (var files = java.nio.file.Files.list(dir)) {
            files.forEach(path -> {
                try { java.nio.file.Files.deleteIfExists(path); }
                catch (Exception e) { System.err.println("Failed to delete " + path + ": " + e); }
            });
        } catch (Exception e) {
            System.err.println("Failed to list uploads folder: " + e);
        }
        System.out.println("Uploads cleared.");
    }

    private void setButtonsDisabled(boolean b) {
        Platform.runLater(() -> {
            refreshBtn.setDisable(b);
            deleteBtn.setDisable(b);
            resetBtn.setDisable(b);
        });
    }

    private static String bodySafe(Response r) throws IOException {
        return r.body() == null ? "" : r.body().string();
    }

    // --- UI helpers ---
    private void alert(String msg) {
        new Alert(Alert.AlertType.ERROR, msg).showAndWait();
    }
    private void info(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg).showAndWait();
    }
    private boolean confirm(String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.OK, ButtonType.CANCEL);
        a.showAndWait();
        return a.getResult() == ButtonType.OK;
    }
    private void uiAlert(String msg) { Platform.runLater(() -> alert(msg)); }
    private void uiInfo(String msg)  { Platform.runLater(() -> info(msg)); }

    // --- Table row model (make sure you have getters for PropertyValueFactory) ---
    public static class UserRow {
        private final int id;
        private final String name;
        private final String email;
        private final boolean onboarded;

        public UserRow(int id, String name, String email, boolean onboarded) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.onboarded = onboarded;
        }
        public int getId() { return id; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public boolean isOnboarded() { return onboarded; }
        public Boolean getOnboarded() { return onboarded; }
    }
}
