package com.example.BuddyLink.Model;

import java.util.ArrayList;
import java.util.List;

public class User {
    private int id;
    private String name;
    private String email;
    private long passwordHash;         // store a hash, not the raw password
    private boolean[] subjects;       // e.g. ["math","chem"]
    private List<String> tags;
    private boolean onboarded;           // finished onboarding?
    private long createdAt;
    private long updatedAt;
    private static int totalUsers = 0;
    private String bio;

    // --- constructors ---
    public User(String name, String email, long passwordHash,
                boolean[] subjects, List<String> tags, boolean onboarded, String bio) {
        this.id = totalUsers++;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.subjects = subjects;
        this.tags = (tags != null) ? new ArrayList<>(tags) : new ArrayList<>();
        this.onboarded = onboarded;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
        this.bio = bio;
    }

    // minimal convenience ctor (before onboarding)
    public User(String email, long passwordHash) {
        this("", email, passwordHash, new boolean[16], new ArrayList<>(), false, "");
    }

    // --- getters/setters ---
    public int getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public long getPasswordHash() { return passwordHash; }
    public boolean[] getSubjects() { return subjects; }
    public List<String> getTags() { return tags; }
    public boolean isOnboarded() { return onboarded; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public String getBio() {return bio; }

    public void setName(String name) { this.name = name; touch(); }
    public void setEmail(String email) { this.email = email; touch(); }
    public void setPasswordHash(long passwordHash) { this.passwordHash = passwordHash; touch(); }
    public void setSubjects(boolean[] subjects) { this.subjects = subjects; touch(); }
    public void setTags(List<String> tags) { this.tags = new ArrayList<>(tags); touch(); }
    public void setOnboarded(boolean onboarded) { this.onboarded = onboarded; touch(); }
    public void setBio(String bio) { this.bio = bio; touch(); }

    private void touch() { this.updatedAt = System.currentTimeMillis(); }
}
