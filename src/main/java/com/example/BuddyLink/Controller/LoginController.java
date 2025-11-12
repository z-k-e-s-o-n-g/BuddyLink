package com.example.BuddyLink.Controller;

import com.example.BuddyLink.Navigation;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class LoginController {
    @FXML private TextField emailText;
    @FXML private PasswordField passwordText;
    @FXML private Hyperlink registerLink;
    @FXML private Button adminButton;

    @FXML
    public void register() {
        Navigation.goTo("register.fxml", registerLink);
    }

    @FXML
    public void admin() {
        // Create a password prompt dialog
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Admin Access");
        dialog.setHeaderText("Enter Admin Password");
        dialog.setContentText("Password:");

        // Show dialog and wait for user input
        dialog.showAndWait().ifPresent(input -> {
            long inputHash = hash(input);
            long correctHash = hash("admin123");

            if (inputHash == correctHash) {
                Navigation.goTo("admin.fxml", adminButton);
            } else {
                alert("Incorrect admin password.");
            }
        });
    }

    @FXML
    public void login() {
        String email = emailText.getText().trim().toLowerCase();
        String password = String.valueOf(hash(passwordText.getText()));


        System.out.println("🟡 [CLIENT] Attempting login: " + email);

        if (email.isEmpty() || password.isEmpty()) {
            alert("Please enter email and password.");
            return;
        }

        try {
            // Call the API to authenticate remotely
            com.example.BuddyLink.net.Api.LoginResp res =
                    com.example.BuddyLink.net.Api.login(email, password);

            // Update session variables
            com.example.BuddyLink.Session.userId = res.userId;
            com.example.BuddyLink.Session.name   = res.name;
            com.example.BuddyLink.Session.email  = res.email;
            com.example.BuddyLink.Session.token  = res.token;

            System.out.println("✅ [CLIENT] Logged in: " + res.name + " (id=" + res.userId + ")");
            Navigation.goTo("main.fxml", emailText);

        } catch (Exception e) {
            e.printStackTrace();
            alert("Login failed: " + e.getMessage());
        }
    }

    private static long hash(String p) {
        int l = p.length();
        long n = 7;
        for (char c : p.toCharArray()) {
            n *= c + (37 % l);
            n %= 951937;
        }
        return (n * n) % 950813;
    }

    private void alert(String msg) {
        new Alert(Alert.AlertType.ERROR, msg).showAndWait();
    }
}
