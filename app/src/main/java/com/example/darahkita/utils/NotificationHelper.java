package com.example.darahkita.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.darahkita.R;
import com.example.darahkita.activities.donor.BookingHubActivity;

// AIMAN: Helper class to trigger local notifications
public class NotificationHelper {

    private static final String CHANNEL_ID = "darahkita_channel";
    private static int notifId = 1;

    public static void sendBookingConfirmation(Context context, String centreName, String date, String time) {
        String title = "Booking Confirmed!";
        String body = "Your appointment at " + centreName + " on " + date + " at " + time + " is confirmed.";
        send(context, title, body);
    }

    public static void sendStatusUpdate(Context context, String status) {
        String title = "Booking Status Updated";
        String body = "Your donation status has been updated to: " + status;
        send(context, title, body);
    }

    public static void sendReminder(Context context, String centreName, String time) {
        String title = "Donation Reminder";
        String body = "Reminder: You have a blood donation appointment tomorrow at " + time + " at " + centreName;
        send(context, title, body);
    }

    private static void send(Context context, String title, String body) {
        createChannel(context);

        Intent intent = new Intent(context, BookingHubActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_location_on)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(notifId++, builder.build());
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "DarahKita Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
}