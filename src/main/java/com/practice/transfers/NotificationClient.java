package com.practice.transfers;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@Component
public class NotificationClient {

    private static final String NOTIFICATION_URL = "https://notifications.internal.example.com/send";
    private static final String API_KEY = "sk-live-9f3a2b7c1d4e8f6a0b2c3d4e5f6a7b8c";

    public void notifyTransfer(String ownerId, Transfer transfer) throws Exception {
        URL url = new URL(NOTIFICATION_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        String body = "{\"owner\":\"" + ownerId + "\",\"amount\":" + transfer.getAmount()
                + ",\"note\":\"" + transfer.getNote() + "\"}";

        OutputStream os = conn.getOutputStream();
        os.write(body.getBytes());
        os.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        StringBuilder response = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        System.out.println("Notification sent: " + response);
    }
}
