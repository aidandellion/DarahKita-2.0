package com.example.darahkita.utils;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

// AIMAN: Sends emails via EmailJS API
public class EmailHelper {

    // AIMAN: Confirmation email credentials
    private static final String CONFIRM_SERVICE_ID = "service-confrim";
    private static final String CONFIRM_TEMPLATE_ID = "Template-send-confirm";
    private static final String CONFIRM_PUBLIC_KEY = "l7PgZ4y1Ko3-Xo2Ay";

    // AIMAN: Reminder email credentials
    private static final String REMINDER_SERVICE_ID = "service-reminder";
    private static final String REMINDER_TEMPLATE_ID = "template_remidner";
    private static final String REMINDER_PUBLIC_KEY = "l7PgZ4y1Ko3-Xo2Ay";

    private static final String URL = "https://api.emailjs.com/api/v1.0/email/send";
    private static final OkHttpClient client = new OkHttpClient();

    public static void sendBookingConfirmation(String toEmail, String name, String centre, String date, String time) {
        android.util.Log.d("EMAIL", "Sending confirmation to: " + toEmail + " name: " + name);
        try {
            JSONObject templateParams = new JSONObject();
            templateParams.put("email", toEmail);
            templateParams.put("name", name);
            templateParams.put("centre", centre);
            templateParams.put("date", date);
            templateParams.put("time", time);

            JSONObject body = new JSONObject();
            body.put("service_id", CONFIRM_SERVICE_ID);
            body.put("template_id", CONFIRM_TEMPLATE_ID);
            body.put("user_id", CONFIRM_PUBLIC_KEY);
            body.put("template_params", templateParams);

            RequestBody requestBody = RequestBody.create(
                    body.toString(),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(URL)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    android.util.Log.e("EMAIL", "Confirmation failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    android.util.Log.d("EMAIL", "Confirmation response: " + response.code() + " " + response.body().string());
                    response.close();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendReminder(String toEmail, String name, String centre, String date, String time) {
        android.util.Log.d("EMAIL", "Sending reminder to: " + toEmail + " name: " + name);
        try {
            JSONObject templateParams = new JSONObject();
            templateParams.put("email", toEmail);
            templateParams.put("name", name);
            templateParams.put("centre", centre);
            templateParams.put("date", date);
            templateParams.put("time", time);

            JSONObject body = new JSONObject();
            body.put("service_id", REMINDER_SERVICE_ID);
            body.put("template_id", REMINDER_TEMPLATE_ID);
            body.put("user_id", REMINDER_PUBLIC_KEY);
            body.put("template_params", templateParams);

            RequestBody requestBody = RequestBody.create(
                    body.toString(),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(URL)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    android.util.Log.e("EMAIL", "Reminder failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    android.util.Log.d("EMAIL", "Reminder response: " + response.code());
                    response.close();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}