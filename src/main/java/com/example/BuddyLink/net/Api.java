package com.example.BuddyLink.net;

import com.example.BuddyLink.Session;
import com.google.gson.*;
import okhttp3.*;

public class Api {

    public static final OkHttpClient http = new OkHttpClient();
    private static final Gson gson = new Gson();
    private static final MediaType JSON = MediaType.parse("application/json");

    public static String BASE = System.getenv().getOrDefault("BUDDYLINK_API", "http://localhost:7070");

    public static String getWsUrl(String pathAndQuery) {
        String scheme = BASE.startsWith("https") ? "wss" : "ws";
        String host = BASE.replaceFirst("^https?://", ""); // e.g. localhost:7070 or api.domain.tld
        if (!pathAndQuery.startsWith("/")) pathAndQuery = "/" + pathAndQuery;
        return scheme + "://" + host + pathAndQuery;
    }

    // ---------------------------
    // Auth
    // ---------------------------
    public static class LoginResp {
        public int userId;
        public String name;
        public String email;
        public String token;
    }

    /** Login with email+password (matches ServerMain /auth/login) */
    public static LoginResp login(String email, String password) throws Exception {
        JsonObject jo = new JsonObject();
        jo.addProperty("email", email);
        jo.addProperty("password", password);

        Request req = new Request.Builder()
                .url(BASE + "/auth/login")
                .post(RequestBody.create(jo.toString(), JSON))
                .build();

        try (Response r = http.newCall(req).execute()) {
            String respBody = (r.body() != null) ? r.body().string() : null;


            System.out.println("🟡 [CLIENT] POST " + BASE + "/auth/login -> " + r.code()
                    + (respBody != null ? (" | body: " + respBody) : " | <no body>"));

            if (!r.isSuccessful() || respBody == null) {
                throw new RuntimeException("Login failed: HTTP " + r.code()
                        + (respBody != null ? (" - " + respBody) : ""));
            }

            // ✅ Parse the already-read string, NOT r.body().string() again
            LoginResp res = gson.fromJson(respBody, LoginResp.class);

            // Stash in Session
            Session.userId = res.userId;
            Session.name   = res.name;
            Session.email  = res.email;
            Session.token  = res.token;

            return res;
        }
    }


    // ---------------------------
    // Chat
    // ---------------------------
    public static int createRoom(int peerId) throws Exception {
        JsonObject jo = new JsonObject();
        jo.addProperty("peerId", peerId);

        Request req = new Request.Builder()
                .url(BASE + "/rooms")
                .addHeader("Authorization", "Bearer " + Session.token)
                .post(RequestBody.create(jo.toString(), JSON))
                .build();

        try (Response r = http.newCall(req).execute()) {
            if (!r.isSuccessful() || r.body() == null) {
                throw new RuntimeException("Create room failed: HTTP " + r.code());
            }
            return gson.fromJson(r.body().string(), JsonObject.class)
                    .get("roomId").getAsInt();
        }
    }

    public static void sendMessage(int roomId, String text) throws Exception {
        JsonObject jo = new JsonObject();
        jo.addProperty("text", text);

        Request req = new Request.Builder()
                .url(BASE + "/rooms/" + roomId + "/messages")
                .addHeader("Authorization", "Bearer " + Session.token)
                .post(RequestBody.create(jo.toString(), JSON))
                .build();

        try (Response r = http.newCall(req).execute()) {
            if (!r.isSuccessful()) {
                throw new RuntimeException("Send message failed: HTTP " + r.code());
            }
        }
    }

    public static JsonArray fetchMessages(int roomId, long since) throws Exception {
        String url = BASE + "/rooms/" + roomId + "/messages?since=" + since;

        Request req = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + Session.token)
                .get()
                .build();

        try (Response r = http.newCall(req).execute()) {
            if (!r.isSuccessful() || r.body() == null) {
                throw new RuntimeException("Fetch messages failed: HTTP " + r.code());
            }
            return JsonParser.parseString(r.body().string()).getAsJsonArray();
        }
    }

    // ---------------------------
    // Profile
    // ---------------------------
    public static void updateProfile(boolean[] subjects, java.util.List<String> tags, boolean onboarded) throws Exception {
        JsonObject body = new JsonObject();

        JsonArray subjArr = new JsonArray();
        for (boolean b : subjects) subjArr.add(b);
        body.add("subjects", subjArr);

        JsonArray tagArr = new JsonArray();
        if (tags != null) for (String t : tags) tagArr.add(t);
        body.add("tags", tagArr);

        body.addProperty("onboarded", onboarded);

        Request req = new Request.Builder()
                .url(BASE + "/me")
                .addHeader("Authorization", "Bearer " + Session.token)
                .put(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response r = http.newCall(req).execute()) {
            if (!r.isSuccessful()) throw new RuntimeException("Profile update failed: HTTP " + r.code());
        }
    }

    public static void updateProfile(boolean[] subjects, java.util.List<String> tags, boolean onboarded, String tokenOverride) throws Exception {
        JsonObject body = new JsonObject();

        JsonArray subj = new JsonArray();
        for (boolean b : subjects) subj.add(b);
        body.add("subjects", subj);

        JsonArray t = new JsonArray();
        if (tags != null) for (String s : tags) t.add(s);
        body.add("tags", t);

        body.addProperty("onboarded", onboarded);

        Request req = new Request.Builder()
                .url(BASE + "/me")
                .addHeader("Authorization", "Bearer " + tokenOverride)
                .put(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response r = http.newCall(req).execute()) {
            if (!r.isSuccessful()) throw new RuntimeException("Profile update failed: HTTP " + r.code());
        }
    }

    public static void updateBio(String bio) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("bio", bio);

        Request req = new Request.Builder()
                .url(BASE + "/me/bio")
                .addHeader("Authorization", "Bearer " + Session.token)
                .put(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response r = http.newCall(req).execute()) {
            if (!r.isSuccessful()) throw new RuntimeException("Bio update failed: HTTP " + r.code());
        }
    }
}
