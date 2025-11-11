package com.example.BuddyLink.net;

import okhttp3.*;

class TestOkHttp {
    public static void main(String[] args) throws Exception {
        OkHttpClient client = new OkHttpClient();
        Request req = new Request.Builder()
                .url("https://httpbin.org/get")
                .build();
        try (Response r = client.newCall(req).execute()) {
            System.out.println(r.code());
        }
    }
}
