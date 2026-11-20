package com.example.darahkita.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// AIMAN: Receives alarm and fires reminder notification + email
public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String centreName = intent.getStringExtra("centreName");
        String time = intent.getStringExtra("time");
        String date = intent.getStringExtra("date");
        String email = intent.getStringExtra("email");
        String name = intent.getStringExtra("name");

        // AIMAN: Local notification
        NotificationHelper.sendReminder(context, centreName, time);

        // AIMAN: Send reminder email
        if (email != null && !email.isEmpty()) {
            EmailHelper.sendReminder(email, name, centreName, date, time);
        }
    }
}