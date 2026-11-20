package com.example.darahkita.utils;

import android.content.Intent;
import android.graphics.Color;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.darahkita.R;
import com.example.darahkita.activities.donor.BookingHubActivity;
import com.example.darahkita.activities.donor.DonationHistoryActivity;
import com.example.darahkita.activities.donor.DonorDashboardActivity;
import com.example.darahkita.activities.donor.MapActivity;
import com.example.darahkita.activities.donor.ProfileActivity;

public class BottomNavHelper {
    public static final String TAB_HOME = "home";
    public static final String TAB_MAP = "map";
    public static final String TAB_BOOKING = "booking";
    public static final String TAB_HISTORY = "history";
    public static final String TAB_PROFILE = "profile";

    private static final String ACTIVE_COLOR = "#D32F2F";
    private static final String INACTIVE_COLOR = "#9E9E9E";

    public static void setup(AppCompatActivity activity, String activeTab) {
        ImageView ivHome = activity.findViewById(R.id.ivHome);
        ImageView ivMap = activity.findViewById(R.id.ivMap);
        ImageView ivHistory = activity.findViewById(R.id.ivHistory);
        ImageView ivProfile = activity.findViewById(R.id.ivProfile);
        TextView tvHome = activity.findViewById(R.id.tvHome);
        TextView tvMap = activity.findViewById(R.id.tvMap);
        TextView tvHistory = activity.findViewById(R.id.tvHistory);
        TextView tvProfile = activity.findViewById(R.id.tvProfile);
        TextView tvBookingLabel = activity.findViewById(R.id.tvBookingLabel);

        resetColor(ivHome, tvHome); resetColor(ivMap, tvMap);
        resetColor(ivHistory, tvHistory); resetColor(ivProfile, tvProfile);
        tvBookingLabel.setTextColor(Color.parseColor(INACTIVE_COLOR));

        switch (activeTab) {
            case TAB_HOME: highlight(ivHome, tvHome); break;
            case TAB_MAP: highlight(ivMap, tvMap); break;
            case TAB_HISTORY: highlight(ivHistory, tvHistory); break;
            case TAB_PROFILE: highlight(ivProfile, tvProfile); break;
            case TAB_BOOKING: tvBookingLabel.setTextColor(Color.parseColor(ACTIVE_COLOR)); break;
        }

        activity.findViewById(R.id.navHome).setOnClickListener(v -> {
            if (!activeTab.equals(TAB_HOME)) activity.startActivity(new Intent(activity, DonorDashboardActivity.class));
        });
        activity.findViewById(R.id.navMap).setOnClickListener(v -> {
            if (!activeTab.equals(TAB_MAP)) activity.startActivity(new Intent(activity, MapActivity.class));
        });
        activity.findViewById(R.id.navHistory).setOnClickListener(v -> {
            if (!activeTab.equals(TAB_HISTORY)) activity.startActivity(new Intent(activity, DonationHistoryActivity.class));
        });
        activity.findViewById(R.id.navProfile).setOnClickListener(v -> {
            if (!activeTab.equals(TAB_PROFILE)) activity.startActivity(new Intent(activity, ProfileActivity.class));
        });
        activity.findViewById(R.id.navBookingFab).setOnClickListener(v -> {
            if (!activeTab.equals(TAB_BOOKING)) activity.startActivity(new Intent(activity, BookingHubActivity.class));
        });
    }

    private static void resetColor(ImageView icon, TextView label) {
        icon.setColorFilter(Color.parseColor(INACTIVE_COLOR));
        label.setTextColor(Color.parseColor(INACTIVE_COLOR));
    }
    private static void highlight(ImageView icon, TextView label) {
        icon.setColorFilter(Color.parseColor(ACTIVE_COLOR));
        label.setTextColor(Color.parseColor(ACTIVE_COLOR));
    }
}