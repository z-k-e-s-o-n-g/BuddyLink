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

import java.util.ArrayList;
import java.util.List;

public class ChatController {

    @FXML private Label peerName, presenceLabel;
    @FXML private ListView<ChatMessage> messagesList;
    @FXML private TextField inputField;
    @FXML private Button sendBtn, attachBtn, bioBtn;

    private static final OkHttpClient HTTP = new OkHttpClient();
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

            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(url); // location set
            javafx.scene.Parent root = loader.load();

            // hand the selected user's id to the popup
            UserInfoController ctrl = loader.getController();
            ctrl.initForUserId(peer.id);

            // show as modal popup
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
        presenceLabel.setText("");

        messagesList.setCellFactory(listView -> new MessageCell());

        sendBtn.setOnAction(e -> sendMessage());
        inputField.setOnAction(e -> sendMessage());
        attachBtn.setOnAction(e -> {/* upload later */});

        openRoomAndConnect();
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
        } catch (Exception e) { e.printStackTrace(); return; }

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
                            false);
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

            // STEP 2: WS URL from Api.getWsUrl (follows BASE + picks ws/wss)
            String url = com.example.BuddyLink.net.Api.getWsUrl(
                    String.format("/ws/rooms/%d?token=%s", roomId, Session.token)
            );
            Request wsReq = new Request.Builder().url(url).build();

            ws = HTTP.newWebSocket(wsReq, new WebSocketListener() {
                @Override public void onOpen(WebSocket webSocket, Response response) {
                    Platform.runLater(() -> presenceLabel.setText("Connected"));
                }
                @Override public void onMessage(WebSocket webSocket, String text) {
                    JsonObject o = JsonParser.parseString(text).getAsJsonObject();
                    String type = o.get("type").getAsString();
                    if ("chat".equals(type)) {
                        int senderId = o.get("userId").getAsInt();
                        if (senderId == Session.userId) {
                            // It's your own message coming back — update the pending one
                            Platform.runLater(() -> {
                                // find the last pending message and mark it as delivered
                                List<ChatMessage> items = messagesList.getItems();
                                for (int i = items.size() - 1; i >= 0; i--) {
                                    ChatMessage msg = items.get(i);
                                    if (msg.pending && msg.text.equals(o.get("text").getAsString())) {
                                        msg.pending = false;
                                        messagesList.refresh();
                                        return;
                                    }
                                }
                            });
                            return; // 👈 stop here
                        }

                        // Otherwise, it's from the peer
                        ChatMessage m = new ChatMessage(senderId,
                                o.has("text") && !o.get("text").isJsonNull() ? o.get("text").getAsString() : null,
                                o.has("imageUrl") && !o.get("imageUrl").isJsonNull() ? o.get("imageUrl").getAsString() : null,
                                o.get("ts").getAsLong(),
                                false);
                        Platform.runLater(() -> {
                            messagesList.getItems().add(m);
                            scrollToBottom();
                        });
                    } else if ("presence".equals(type)) {
                        String ev = o.get("event").getAsString();
                        Platform.runLater(() -> presenceLabel.setText(ev.equals("join") ? "Online" : "Away"));
                    }
                }
                @Override public void onClosing(WebSocket webSocket, int code, String reason) {
                    webSocket.close(code, reason);
                    Platform.runLater(() -> presenceLabel.setText("Disconnecting"));
                }
                @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                    Platform.runLater(() -> presenceLabel.setText("Disconnected"));
                }
                @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    Platform.runLater(() -> presenceLabel.setText("WS error"));
                }
            });
        } catch (Exception e) {
            Platform.runLater(() -> presenceLabel.setText("WS failed"));
        }
    }

    private void sendMessage() {
        String text = inputField.getText() == null ? "" : inputField.getText().trim();
        if (text.isEmpty()) return;
        inputField.clear();

        ChatMessage pending = new ChatMessage(Session.userId, text, null, System.currentTimeMillis(), true);
        messagesList.getItems().add(pending);
        scrollToBottom();

        // WS payload
        JsonObject wsMsg = new JsonObject();
        wsMsg.addProperty("type", "chat");
        wsMsg.addProperty("text", text);

        boolean sent = false;
        try {
            if (ws != null) sent = ws.send(wsMsg.toString());
            if (!sent) {
                // REST fallback must be ONLY { "text": ... }
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

    private void scrollToBottom() {
        int last = messagesList.getItems().size() - 1;
        if (last >= 0) messagesList.scrollTo(last);
    }

    public static class ChatMessage {
        public final int userId; public final String text; public final String imageUrl;
        public final long ts; public boolean pending;
        public ChatMessage(int uid, String t, String img, long ts, boolean p) {
            this.userId = uid; this.text = t; this.imageUrl = img; this.ts = ts; this.pending = p;
        }
    }

    private static class MessageCell extends ListCell<ChatMessage> {
        private final HBox box = new HBox();
        private final Label text = new Label();
        private final ImageView image = new ImageView();

        MessageCell() {
            text.setWrapText(true);
            text.setMaxWidth(520);
            image.setFitWidth(260); image.setPreserveRatio(true); image.setSmooth(true);
        }

        @Override protected void updateItem(ChatMessage m, boolean empty) {
            super.updateItem(m, empty);
            if (empty || m == null) { setGraphic(null); return; }

            box.getChildren().clear();

            boolean mine = (m.userId == Session.userId);
            String bubble = "-fx-padding:8; -fx-background-radius:12; -fx-background-color:" +
                    (mine ? "#a9c8ff" : "#e9eef7") + ";";

            if (m.imageUrl != null) {
                image.setImage(new Image(m.imageUrl, true));
                box.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                box.getChildren().add(image);
            } else {
                text.setText(m.text + (m.pending ? "  ⏳" : ""));
                text.setStyle(bubble);
                box.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                box.getChildren().add(text);
            }
            setGraphic(box);
        }
    }
}
