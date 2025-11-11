// src/main/java/com/example/BuddyLink/Controller/RegisterController.java
package com.example.BuddyLink.Controller;

import com.example.BuddyLink.Navigation;
import com.example.BuddyLink.Session;
import com.example.BuddyLink.net.Api;      // ✅ use shared API base/client
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

    private static final MediaType JSON = MediaType.parse("application/json");
    private static final Gson G = new Gson();

    @FXML
    public void register() {
        String name    = nameText.getText().trim();
        String email   = emailText.getText().trim().toLowerCase();
        String pw      = passwordText.getText();
        String confirm = confirmPasswordText.getText();

        if (name.isEmpty() || email.isEmpty() || pw.isEmpty() || confirm.isEmpty()) {
            alert("Please fill in all fields.");
            return;
        }
        if (!pw.equals(confirm)) {
            alert("Passwords do not match.");
            return;
        }

        // keep your lightweight hash; store as STRING to match server compare
        String hashed = String.valueOf(hash(pw));

        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("email", email);
        body.addProperty("password", hashed);
        body.addProperty("onboarded", false);

        Request req = new Request.Builder()
                .url(Api.BASE + "/auth/register")   // ✅ no localhost hardcode
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response r = Api.http.newCall(req).execute()) {  // ✅ shared OkHttpClient
            String respBody = (r.body() != null) ? r.body().string() : "";
            if (!r.isSuccessful()) {
                if (r.code() == 409) alert("Email already used.");
                else alert("Register failed: " + r.code() + "\n" + respBody);
                return;
            }

            JsonObject res = JsonParser.parseString(respBody).getAsJsonObject();
            Session.userId = res.get("userId").getAsInt();
            Session.name   = res.get("name").getAsString();
            Session.email  = res.get("email").getAsString();
            Session.token  = res.get("token").getAsString();

            System.out.println("✅ Registered: " + Session.email + " via " + Api.BASE);
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
