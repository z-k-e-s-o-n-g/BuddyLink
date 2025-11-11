package com.example.BuddyLink.Controller;

public class UserRow {
    private int id;
    private String name;
    private String email;
    private boolean onboarded;

    public UserRow(int id, String name, String email, boolean onboarded) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.onboarded = onboarded;
    }

    // getters for TableView
    public int getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public boolean isOnboarded() { return onboarded; }

    // PropertyValueFactory looks for getOnboarded() or isOnboarded()
    public Boolean getOnboarded() { return onboarded; }
}
