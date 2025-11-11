module com.example.BuddyLink {
    requires javafx.controls;
    requires javafx.fxml;
    requires io.javalin;
    requires com.google.gson;
    requires okhttp3;
    requires java.sql;

    // FXML
    opens com.example.BuddyLink to javafx.fxml;
    opens com.example.BuddyLink.Controller to javafx.fxml;

    // ✅ Let Gson reflect on Api.LoginResp and other network models
    opens com.example.BuddyLink.net to com.google.gson;

    exports com.example.BuddyLink;
    exports com.example.BuddyLink.Controller;
    exports com.example.BuddyLink.net;
}
