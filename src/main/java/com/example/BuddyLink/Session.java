package com.example.BuddyLink;

public class Session {
    public static int userId;
    public static String name;
    public static String email;
    public static String token;

    public static void clear() {
        userId = 0;
        name = null;
        email = null;
        token = null;
    }
}
