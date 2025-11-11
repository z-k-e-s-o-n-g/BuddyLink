package com.example.BuddyLink.Controller;

import com.example.BuddyLink.Navigation;
import com.example.BuddyLink.Session;
import com.google.gson.*;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextField;
import okhttp3.*;

public class RegisterController {
    @FXML private TextField nameText;
    @FXML private TextField emailText;
    @FXML private TextField passwordText;
    @FXML private TextField confirmPasswordText;
    @FXML private Hyperlink loginLink;

    private static final String BASE = "http://localhost:7070";
    private static final OkHttpClient http = new OkHttpClient();
    private static final Gson G = new Gson();

    @FXML
    public void register() {
        String name  = nameText.getText().trim();
        String email = emailText.getText().trim().toLowerCase();
        String pw    = passwordText.getText();
        String confirm = confirmPasswordText.getText();

        if (name.isEmpty() || email.isEmpty() || pw.isEmpty() || confirm.isEmpty()) {
            alert("Please fill in all fields.");
            return;
        }
        if (!pw.equals(confirm)) {
            alert("Passwords do not match.");
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("email", email);
        body.addProperty("password", hash(pw));
        body.addProperty("onboarded", false);

        Request req = new Request.Builder()
                .url(BASE + "/auth/register")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();

        try (Response r = http.newCall(req).execute()) {
            String respBody = (r.body() != null) ? r.body().string() : "";
            if (!r.isSuccessful()) {
                System.err.println("❌ Register failed: " + r.code() + " - " + respBody);
                if (r.code() == 409) alert("Email already used.");
                else alert("Register failed: " + r.code() + "\n" + respBody);
                return;
            }

            JsonObject res = JsonParser.parseString(respBody).getAsJsonObject();
            Session.userId = res.get("userId").getAsInt();
            Session.name   = res.get("name").getAsString();
            Session.email  = res.get("email").getAsString();
            Session.token  = res.get("token").getAsString();

            System.out.println("✅ Registered: " + Session.email);
            Navigation.goTo("onboarding.fxml", nameText);

        } catch (Exception e) {
            e.printStackTrace();
            alert("Register failed: " + e.getMessage());
        }
    }

    @FXML
    public void login() {
        Navigation.goTo("login.fxml", loginLink);
    }

    private void alert(String msg) {
        new Alert(Alert.AlertType.ERROR, msg).showAndWait();
    }

    private static long hash(String p){
        int l = p.length();
        long n = 7;
        for (char c: p.toCharArray()){
            n *= c + (37 % l);
            n %= 951937;
        }
        return (n * n) % 950813;
    }
}
