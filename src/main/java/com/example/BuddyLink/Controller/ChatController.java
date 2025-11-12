package com.example.BuddyLink.Controller;

import com.example.BuddyLink.GlobalContainer;
import com.example.BuddyLink.Navigation;
import com.example.BuddyLink.Session;
import com.example.BuddyLink.Controller.MainController.UserLite;
import com.google.gson.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import okhttp3.*;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;

public class ChatController {

    @FXML private Label peerName, presenceLabel;
    @FXML private ListView<ChatMessage> messagesList;
    @FXML private TextField inputField;
    @FXML private Button sendBtn, attachBtn, bioBtn;


    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> presenceTask;
    private ScheduledFuture<?> heartbeatTask;

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private static final MediaType JSON = MediaType.parse("application/json");

    private UserLite peer;
    private WebSocket ws;
    private int roomId = -1;
    private long lastSeenTs = 0L;

    @FXML
    public void initialize() {
        if (GlobalContainer.backgroundImageUrl != null) {
            messagesList.setStyle(
                    "-fx-background-image: url('" + GlobalContainer.backgroundImageUrl + "');" +
                            "-fx-background-size: cover;" +
                            "-fx-background-position: center center;" +
                            "-fx-background-repeat: no-repeat;"
            );
        }
    }


    @FXML
    public void openBio() {
        try {
            final String FXML_PATH = "/com/example/BuddyLink/View/user_info_popup.fxml";
            java.net.URL url = java.util.Objects.requireNonNull(
                    ChatController.class.getResource(FXML_PATH),
                    "Cannot find " + FXML_PATH + " on classpath (put it in src/main/resources)"
            );

            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(url);
            javafx.scene.Parent root = loader.load();


            UserInfoController ctrl = loader.getController();
            ctrl.initForUserId(peer.id);


            javafx.stage.Stage owner = (javafx.stage.Stage) bioBtn.getScene().getWindow();
            javafx.stage.Stage dialog = new javafx.stage.Stage();
            dialog.initOwner(owner);
            dialog.initModality(javafx.stage.Modality.WINDOW_MODAL);
            dialog.setTitle("About " + peer.name);
            dialog.setScene(new javafx.scene.Scene(root));
            dialog.show();

        } catch (Exception ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR,
                    "Failed to open profile popup: " + ex.getMessage()).showAndWait();
        }
    }



    public void init(UserLite peer) {
        this.peer = peer;
        peerName.setText(peer.name);
        presenceLabel.setText("Loading…");

        messagesList.setCellFactory(listView -> new MessageCell());
        messagesList.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/chat.css")).toExternalForm()
        );

        sendBtn.setOnAction(e -> sendMessage());
        inputField.setOnAction(e -> sendMessage());
        attachBtn.setOnAction(e -> chooseAndUpload());

        openRoomAndConnect();

        startPresencePolling();
        startHeartbeat();
    }

    private void openRoomAndConnect() {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("peerId", peer.id);
            Request req = new Request.Builder()
                    .url(com.example.BuddyLink.net.Api.BASE + "/rooms")
                    .addHeader("Authorization", "Bearer " + Session.token)
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();
            try (Response r = HTTP.newCall(req).execute()) {
                JsonObject jo = JsonParser.parseString(r.body().string()).getAsJsonObject();
                roomId = jo.get("roomId").getAsInt();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        loadHistory(0);
        connectWs();
    }

    private void loadHistory(long since) {
        try {
            String url = com.example.BuddyLink.net.Api.BASE + "/rooms/" + roomId + "/messages?since=" + since;
            Request req = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + Session.token)
                    .get().build();
            try (Response r = HTTP.newCall(req).execute()) {
                JsonArray arr = JsonParser.parseString(r.body().string()).getAsJsonArray();
                List<ChatMessage> list = new ArrayList<>();
                for (JsonElement el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    ChatMessage m = new ChatMessage(
                            o.get("userId").getAsInt(),
                            o.has("text") && !o.get("text").isJsonNull() ? o.get("text").getAsString() : null,
                            o.has("imageUrl") && !o.get("imageUrl").isJsonNull() ? o.get("imageUrl").getAsString() : null,
                            o.get("ts").getAsLong(),
                            false
                    );
                    if (o.has("mime") && !o.get("mime").isJsonNull()) m.mime = o.get("mime").getAsString();
                    list.add(m);
                    lastSeenTs = Math.max(lastSeenTs, m.ts);

            }
                Platform.runLater(() -> {
                    messagesList.getItems().setAll(list);
                    scrollToBottom();
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void connectWs() {
        try {
            if (ws != null) ws.close(1000, "reconnect");

            String url = com.example.BuddyLink.net.Api.getWsUrl(
                    String.format("/ws/rooms/%d?token=%s", roomId, Session.token)
            );
            Request wsReq = new Request.Builder().url(url).build();

            ws = HTTP.newWebSocket(wsReq, new WebSocketListener() {
                @Override public void onOpen(WebSocket webSocket, Response response) {
                    Platform.runLater(() -> presenceLabel.setText("Connected"));
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    JsonObject o = JsonParser.parseString(text).getAsJsonObject();
                    String type = o.get("type").getAsString();

                    if ("chat".equals(type)) {
                        int senderId = o.get("userId").getAsInt();
                        if (senderId == Session.userId) {
                            Platform.runLater(() -> {
                                List<ChatMessage> items = messagesList.getItems();
                                for (int i = items.size() - 1; i >= 0; i--) {
                                    ChatMessage msg = items.get(i);
                                    if (msg.pending && msg.text != null &&
                                            msg.text.equals(o.get("text").getAsString())) {
                                        msg.pending = false;
                                        messagesList.refresh();
                                        return;
                                    }
                                }
                            });
                            return;
                        }

                        ChatMessage m = new ChatMessage(senderId,
                                o.has("text") && !o.get("text").isJsonNull() ? o.get("text").getAsString() : null,
                                o.has("imageUrl") && !o.get("imageUrl").isJsonNull() ? o.get("imageUrl").getAsString() : null,
                                o.get("ts").getAsLong(),
                                false);
                        if (o.has("mime") && !o.get("mime").isJsonNull()) m.mime = o.get("mime").getAsString();

                        Platform.runLater(() -> {
                            messagesList.getItems().add(m);
                            scrollToBottom();
                        });

                    } else if ("presence".equals(type)) {
                        String ev = o.get("event").getAsString();
                        int uid = o.has("userId") ? o.get("userId").getAsInt() : -1;
                        if (uid == peer.id) {
                            Platform.runLater(() ->
                                    presenceLabel.setText(ev.equals("join") ? "Online" : "Away"));
                        }

                    } else if ("reset".equals(type)) {
                        Platform.runLater(() -> {
                            messagesList.getItems().clear();
                            messagesList.setStyle("");
                            presenceLabel.setText("Away");
                            lastSeenTs = 0;
                            roomId = -1;
                            inputField.clear();
                        });
                    }
                }

                @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                    Platform.runLater(() -> presenceLabel.setText("Away"));
                }
                @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    Platform.runLater(() -> presenceLabel.setText("WS error"));
                }
            });
        } catch (Exception e) {
            Platform.runLater(() -> presenceLabel.setText("WS failed"));
        }
    }

    private void startPresencePolling() {
        stopPresencePolling();
        presenceTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                String url = com.example.BuddyLink.net.Api.BASE + "/presence/" + peer.id;
                Request req = new Request.Builder().url(url).get().build();
                try (Response r = HTTP.newCall(req).execute()) {
                    if (!r.isSuccessful() || r.body() == null) return;
                    JsonObject o = JsonParser.parseString(r.body().string()).getAsJsonObject();
                    String status = o.get("status").getAsString();
                    Platform.runLater(() -> presenceLabel.setText(
                            "online".equals(status) ? "Online" : "Away"));
                }
            } catch (Exception ignored) {}
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void stopPresencePolling() {
        if (presenceTask != null) {
            presenceTask.cancel(true);
            presenceTask = null;
        }
    }
    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                Request req = new Request.Builder()
                        .url(com.example.BuddyLink.net.Api.BASE + "/presence/ping")
                        .addHeader("Authorization", "Bearer " + Session.token)
                        .post(RequestBody.create(new byte[0], null))
                        .build();
                HTTP.newCall(req).execute().close();
            } catch (Exception ignored) {}
        }, 0, 10, TimeUnit.SECONDS);
    }

    private void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
            heartbeatTask = null;
        }
    }
    private void sendMessage() {
        String text = inputField.getText() == null ? "" : inputField.getText().trim();
        if (text.isEmpty()) return;
        inputField.clear();
        ChatMessage pending = new ChatMessage(Session.userId, text, null, System.currentTimeMillis(), true);
        messagesList.getItems().add(pending);
        scrollToBottom();
        JsonObject wsMsg = new JsonObject();
        wsMsg.addProperty("type", "chat");
        wsMsg.addProperty("text", text);
        boolean sent = false;
        try {
            if (ws != null) sent = ws.send(wsMsg.toString());
            if (!sent) {
                JsonObject restBody = new JsonObject();
                restBody.addProperty("text", text);
                Request req = new Request.Builder()
                        .url(com.example.BuddyLink.net.Api.BASE + "/rooms/" + roomId + "/messages")
                        .addHeader("Authorization", "Bearer " + Session.token)
                        .post(RequestBody.create(restBody.toString(), JSON)).build();
                HTTP.newCall(req).execute().close();
            }
        } catch (Exception ignored) {}
    }

    private void chooseAndUpload() {
        var fc = new javafx.stage.FileChooser();
        fc.setTitle("Attach");
        fc.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"),
                new javafx.stage.FileChooser.ExtensionFilter("Videos", "*.mp4", "*.mov", "*.m4v"),
                new javafx.stage.FileChooser.ExtensionFilter("Documents", "*.pdf", "*.doc", "*.docx", "*.ppt", "*.pptx", "*.txt"),
                new javafx.stage.FileChooser.ExtensionFilter("All Files", "*.*")
        );
        var file = fc.showOpenDialog(attachBtn.getScene().getWindow());
        if (file == null) return;
        ChatMessage pending = new ChatMessage(Session.userId, null, file.toURI().toString(),
                System.currentTimeMillis(), true);
        pending.mime = guessMime(file.getName());
        messagesList.getItems().add(pending);
        scrollToBottom();
        new Thread(() -> {
            try {
                RequestBody rb = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", file.getName(),
                                RequestBody.create(file, MediaType.parse(pending.mime)))
                        .build();

                Request req = new Request.Builder()
                        .url(com.example.BuddyLink.net.Api.BASE + "/rooms/" + roomId + "/files")
                        .addHeader("Authorization", "Bearer " + Session.token)
                        .post(rb).build();

                try (Response r = HTTP.newCall(req).execute()) {
                    String resp = (r.body() != null) ? r.body().string() : null;
                    if (!r.isSuccessful() || resp == null)
                        throw new RuntimeException("Upload failed: " + r.code() + " - " + resp);

                    var jo = JsonParser.parseString(resp).getAsJsonObject();
                    String fileUrl = jo.get("fileUrl").getAsString();
                    String mime = jo.get("mime").getAsString();

                    Platform.runLater(() -> {
                        for (int i = messagesList.getItems().size() - 1; i >= 0; i--) {
                            var m = messagesList.getItems().get(i);
                            if (m.pending && m.imageUrl != null && m.imageUrl.startsWith("file:")) {
                                m.imageUrl = fileUrl;
                                m.mime = mime;
                                m.pending = false;
                                messagesList.refresh();
                                break;
                            }
                        }
                    });
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    pending.text = "Upload failed: " + ex.getMessage();
                    pending.imageUrl = null;
                    pending.pending = false;
                    messagesList.refresh();
                });
            }
        }).start();
    }
    private static String guessMime(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".mov") || n.endsWith(".m4v")) return "video/mp4";
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".doc")) return "application/msword";
        if (n.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (n.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (n.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (n.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    private void scrollToBottom() {
        int last = messagesList.getItems().size() - 1;
        if (last >= 0) messagesList.scrollTo(last);
    }

    public void resetChat() {
        messagesList.getItems().clear();
        messagesList.setStyle("");
        presenceLabel.setText("Away");
        lastSeenTs = 0;
        roomId = -1;
        inputField.clear();
    }
    public static class ChatMessage {
        public final int userId;
        public String text;
        public String imageUrl;
        public String mime;
        public final long ts;
        public boolean pending;

        public ChatMessage(int uid, String t, String img, long ts, boolean p) {
            this.userId = uid;
            this.text = t;
            this.imageUrl = img;
            this.ts = ts;
            this.pending = p;
        }
    }

    private static class MessageCell extends ListCell<ChatMessage> {
        private final HBox box = new HBox();
        private final Label text = new Label();
        private final ImageView image = new ImageView();

        MessageCell() {
            text.setWrapText(true);
            text.setMaxWidth(520);
            image.setFitWidth(260);
            image.setPreserveRatio(true);
            image.setSmooth(true);
        }

        @Override
        protected void updateItem(ChatMessage m, boolean empty) {
            super.updateItem(m, empty);
            if (empty || m == null) { setGraphic(null); return; }
            box.getChildren().clear();

            boolean mine = (m.userId == Session.userId);
            String bubble = "-fx-padding:8; -fx-background-radius:12; -fx-background-color:" +
                    (mine ? "#a9c8ff" : "#e9eef7") + ";";

            if (m.imageUrl != null) {
                if (m.mime != null && m.mime.startsWith("image/")) {
                    String url = m.imageUrl;
                    if (url != null && !url.isBlank()) {
                        if (url.startsWith("/")) {
                            String api = System.getenv("BUDDYLINK_API");
                            if (api != null && !api.isBlank())
                                url = api.replaceAll("/+$", "") + url;
                            else url = "http://localhost:7070" + url;
                        }
                        try {
                            Image img = new Image(url, true);
                            image.setImage(img);
                        } catch (IllegalArgumentException ex) {
                            System.err.println("⚠️ Invalid image URL: " + url);
                            image.setImage(null);
                        }
                    }
                    box.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                    box.getChildren().add(image);
                } else {
                    Hyperlink link = new Hyperlink(
                            (m.mime != null && m.mime.startsWith("video/")) ? "Open video" : "Open file");
                    link.setOnAction(ev -> {
                        try { java.awt.Desktop.getDesktop().browse(java.net.URI.create(m.imageUrl)); }
                        catch (Exception ex) { ex.printStackTrace(); }
                    });
                    link.setStyle(bubble);
                    box.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                    box.getChildren().add(link);
                }
            } else {
                text.setText((m.text == null ? "" : m.text) + (m.pending ? "  ⏳" : ""));
                text.setStyle(bubble);
                box.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                box.getChildren().add(text);
            }
            setGraphic(box);
        }
    }
}
