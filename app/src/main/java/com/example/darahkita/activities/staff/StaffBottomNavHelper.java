package com.example.darahkita.activities.staff;

import com.example.darahkita.R;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import android.content.Intent;

/**
 * Wires up clicks and selected-state styling for the shared STAFF bottom nav
 * bar (res/layout/layout_staff_bottom_nav.xml). Mirrors BottomNavHelper but
 * for the staff tab set (Dashboard/Queue/Search/Stock/Profile) — kept as a
 * separate class since donor and staff have completely different tabs.
 *
 * USAGE — in each staff Activity's onCreate():
 *
 *     View staffNav = findViewById(R.id.staffBottomNavBar);
 *     StaffBottomNavHelper.setup(this, staffNav, StaffBottomNavHelper.Tab.DASHBOARD);
 */
public class StaffBottomNavHelper {

    public enum Tab { DASHBOARD, QUEUE, SEARCH, STOCK, PROFILE }

    public static void setup(Context context, View navRoot, Tab currentTab) {
        View navDashboard = navRoot.findViewById(R.id.navDashboard);
        View navQueue = navRoot.findViewById(R.id.navQueue);
        View navSearch = navRoot.findViewById(R.id.navSearch);
        View navStock = navRoot.findViewById(R.id.navStock);
        View navProfile = navRoot.findViewById(R.id.navStaffProfile);

        navDashboard.setOnClickListener(v -> {
            if (currentTab != Tab.DASHBOARD) {
                context.startActivity(new Intent(context, StaffDashboardActivity.class));
            }
        });

        navQueue.setOnClickListener(v -> {
            if (currentTab != Tab.QUEUE) {
                // TODO: replace with -> context.startActivity(new Intent(context, QueueActivity.class));
                Toast.makeText(context, "Queue screen coming soon", Toast.LENGTH_SHORT).show();
            }
        });

        navSearch.setOnClickListener(v -> {
            if (currentTab != Tab.SEARCH) {
                // TODO: replace with -> context.startActivity(new Intent(context, StaffSearchActivity.class));
                Toast.makeText(context, "Search screen coming soon", Toast.LENGTH_SHORT).show();
            }
        });

        navStock.setOnClickListener(v -> {
            if (currentTab != Tab.STOCK) {
                // TODO: replace with -> context.startActivity(new Intent(context, BloodStockActivity.class));
                Toast.makeText(context, "Stock screen coming soon", Toast.LENGTH_SHORT).show();
            }
        });

        navProfile.setOnClickListener(v -> {
            if (currentTab != Tab.PROFILE) {
                context.startActivity(new Intent(context, StaffProfileActivity.class));
            }
        });

        highlightSelectedTab(context, navRoot, currentTab);
    }

    private static void highlightSelectedTab(Context context, View navRoot, Tab currentTab) {
        markTab(context, navRoot, R.id.iconNavDashboard, R.id.tvNavDashboard, currentTab == Tab.DASHBOARD);
        markTab(context, navRoot, R.id.iconNavQueue, R.id.tvNavQueue, currentTab == Tab.QUEUE);
        markTab(context, navRoot, R.id.iconNavSearch, R.id.tvNavSearch, currentTab == Tab.SEARCH);
        markTab(context, navRoot, R.id.iconNavStock, R.id.tvNavStock, currentTab == Tab.STOCK);
        markTab(context, navRoot, R.id.iconNavStaffProfile, R.id.tvNavStaffProfile, currentTab == Tab.PROFILE);
    }

    private static void markTab(Context context, View navRoot, int iconId, int labelId, boolean selected) {
        ImageView icon = navRoot.findViewById(iconId);
        TextView label = navRoot.findViewById(labelId);
        int color = ContextCompat.getColor(context, selected ? R.color.staff_nav_selected : R.color.staff_nav_unselected);
        icon.setColorFilter(color);
        label.setTextColor(color);
        label.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
    }
}