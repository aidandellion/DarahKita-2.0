package com.example.darahkita.activities.staff;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.app.DatePickerDialog;
import java.text.SimpleDateFormat;
import java.util.Locale;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.darahkita.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StaffDashboardActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String staffCentreID;
    private String centreName;

    private TextView tvTodayDonors;
    private TextView tvCurrentlyProcessing, tvWaitingRoom;
    private TextView tvCentreName;  // Show centre name
    private CardView cardBloodAnnouncement;
    private TextView tvBloodAnnouncementText;
    private MaterialButton btnBroadcast;
    private LinearLayout llBloodStockRow;
    private LinearLayout llBroadcastContainer;

    private ListenerRegistration bookingListener;
    private ListenerRegistration stockListener;
    private ListenerRegistration eventListener;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff_dashboard);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        initViews();
        fetchStaffCentreID();
        setupBottomNav();
        setupBroadcastButton();  // ← IS THIS HERE?
    }

    private void fetchStaffCentreID() {
        String currentUID = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection("users").document(currentUID).get()
                .addOnSuccessListener(doc -> {
                    staffCentreID = doc.getString("centreID");
                    Log.d("Dashboard", "Staff centre: " + staffCentreID);

                    if (staffCentreID != null) {
                        fetchCentreName();

                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            listenToBookings();
                            listenToBloodStock();
                            listenToBroadcastEvents();
                        }, 3000);
                    }
                });
    }

    private void fetchCentreName() {
        db.collection("centre").document(staffCentreID).get()
                .addOnSuccessListener(doc -> {
                    centreName = doc.getString("name");
                    if (tvCentreName != null && centreName != null) {
                        tvCentreName.setText(centreName);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Dashboard", "Error fetching centre name: " + e.getMessage());
                });
    }

    // ─── Init Views ────────────────────────────────────────────────────────────

    private void initViews() {
        tvTodayDonors         = findViewById(R.id.tvTodayDonors);
        tvCurrentlyProcessing = findViewById(R.id.tvCurrentlyProcessing);
        tvWaitingRoom         = findViewById(R.id.tvWaitingRoom);
        tvCentreName          = findViewById(R.id.tvCentreName);  // Add this
        cardBloodAnnouncement = findViewById(R.id.cardBloodAnnouncement);
        tvBloodAnnouncementText = findViewById(R.id.tvBloodAnnouncementText);
        llBloodStockRow       = findViewById(R.id.llBloodStockRow);
        llBroadcastContainer  = findViewById(R.id.llBroadcastContainer);
        btnBroadcast = findViewById(R.id.btnBroadcastStandalone);
    }

    // ─── Realtime listener: Booking ────────────────────────────────────────────

    private void listenToBookings() {
        if (staffCentreID == null) return;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Timestamp startOfDay = new Timestamp(cal.getTime());

        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        Timestamp endOfDay = new Timestamp(cal.getTime());

        bookingListener = db.collection("booking")
                .whereEqualTo("centreID", staffCentreID)  // ← Filter by centre
                .whereGreaterThanOrEqualTo("bookingDate", startOfDay)
                .whereLessThanOrEqualTo("bookingDate", endOfDay)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    int totalToday = 0;
                    int processing = 0;
                    int waiting    = 0;

                    for (QueryDocumentSnapshot doc : snapshots) {
                        totalToday++;

                        Boolean checkedIn = doc.getBoolean("checkedIn");
                        String status = doc.getString("status");

                        if (!Boolean.TRUE.equals(checkedIn)) continue;

                        if ("Pending".equals(status)) {
                            processing++;
                        } else if ("Eligible".equals(status)) {
                            waiting++;
                        }
                    }

                    tvTodayDonors.setText(String.valueOf(totalToday));
                    tvCurrentlyProcessing.setText(String.valueOf(processing));
                    tvWaitingRoom.setText(String.valueOf(waiting));
                });
    }

    // ─── Realtime listener: Blood Stock ────────────────────────────────────────

    private void listenToBloodStock() {
        if (staffCentreID == null) return;

        stockListener = db.collection("bloodStock")
                .whereEqualTo("centreID", staffCentreID)  // ← Filter by centre
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    List<Map<String, Object>> insufficient = new ArrayList<>();
                    List<Map<String, Object>> sufficient   = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : snapshots) {
                        String bloodType = doc.getString("bloodType");
                        Boolean outStock = doc.getBoolean("outStock");
                        if (bloodType == null) continue;

                        Map<String, Object> item = new HashMap<>();
                        item.put("bloodType", bloodType);
                        item.put("outStock", Boolean.TRUE.equals(outStock));

                        if (Boolean.TRUE.equals(outStock)) {
                            insufficient.add(item);
                        } else {
                            sufficient.add(item);
                        }
                    }

                    List<Map<String, Object>> sorted = new ArrayList<>();
                    sorted.addAll(insufficient);
                    sorted.addAll(sufficient);

                    llBloodStockRow.removeAllViews();
                    for (Map<String, Object> item : sorted) {
                        String bt  = (String) item.get("bloodType");
                        boolean out = (boolean) item.get("outStock");
                        llBloodStockRow.addView(makeStockChip(bt, out));
                    }

                    List<String> insufficientNames = new ArrayList<>();
                    for (Map<String, Object> item : insufficient) {
                        insufficientNames.add((String) item.get("bloodType"));
                    }

                    if (!insufficientNames.isEmpty()) {
                        cardBloodAnnouncement.setVisibility(View.VISIBLE);
                        String centreNameDisplay = centreName != null ? centreName : staffCentreID;
                        tvBloodAnnouncementText.setText(
                                "Stock " + String.join(", ", insufficientNames) +
                                        " is insufficient for " + centreNameDisplay + ". Donors needed urgently."
                        );
                    } else {
                        cardBloodAnnouncement.setVisibility(View.GONE);
                    }
                });
    }

    // ─── Realtime listener: Broadcast Events ─────────────────────────────────────

    private void listenToBroadcastEvents() {
        if (staffCentreID == null) return;

        eventListener = db.collection("event")
                .whereEqualTo("centreID", staffCentreID)  // ← Filter by centre
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (llBroadcastContainer != null) {
                        llBroadcastContainer.removeAllViews();
                    }

                    if (e != null) {
                        Toast.makeText(this, "Error loading events: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (snapshots == null || snapshots.isEmpty()) {
                        return;
                    }

                    for (QueryDocumentSnapshot doc : snapshots) {
                        String docId = doc.getId();
                        String name  = doc.getString("eventName");
                        String desc  = doc.getString("description");
                        String loc   = doc.getString("location");
                        String date  = doc.getString("eventDate");

                        View itemView = LayoutInflater.from(this).inflate(R.layout.item_dashboard_broadcast, llBroadcastContainer, false);

                        TextView tvTitle = itemView.findViewById(R.id.tvBroadcastTitle);
                        TextView tvInfo  = itemView.findViewById(R.id.tvBroadcastInfo);
                        MaterialButton btnDelete = itemView.findViewById(R.id.btnDeleteBroadcast);

                        tvTitle.setText(name != null ? name : "No Name");
                        tvInfo.setText((desc != null ? desc : "") + "\n📍 Location: " + (loc != null ? loc : "") + "\n📅 Date: " + (date != null ? date : ""));

                        btnDelete.setOnClickListener(v -> {
                            // Only allow delete if staff's centre matches event's centre
                            String eventCentreID = doc.getString("centreID");
                            if (!staffCentreID.equals(eventCentreID)) {
                                Toast.makeText(this, "You can only delete events from your centre", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            new AlertDialog.Builder(this)
                                    .setTitle("Delete Broadcast")
                                    .setMessage("Are you sure you want to delete this event broadcast?")
                                    .setPositiveButton("Yes, Delete", (dialog, which) -> {
                                        db.collection("event").document(docId).delete()
                                                .addOnSuccessListener(unused -> {
                                                    Toast.makeText(this, "Broadcast deleted successfully", Toast.LENGTH_SHORT).show();
                                                    if (llBroadcastContainer != null) {
                                                        llBroadcastContainer.removeView(itemView);
                                                    }
                                                })
                                                .addOnFailureListener(err -> {
                                                    Toast.makeText(this, "Delete failed: " + err.getMessage(), Toast.LENGTH_SHORT).show();
                                                });
                                    })
                                    .setNegativeButton("No", null)
                                    .show();
                        });

                        if (llBroadcastContainer != null) {
                            llBroadcastContainer.addView(itemView);
                        }
                    }
                });
    }

    // ─── Build stock chip ──────────────────────────────────────────────────────

    private View makeStockChip(String bloodType, boolean outStock) {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                dpToPx(76), LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMarginEnd(dpToPx(10));
        card.setLayoutParams(cardParams);
        card.setRadius(dpToPx(12));
        card.setCardElevation(0);
        card.setCardBackgroundColor(outStock
                ? Color.parseColor("#FFF0F0")
                : Color.parseColor("#F0FFF4"));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(android.view.Gravity.CENTER);
        int pad = dpToPx(12);
        inner.setPadding(pad, pad, pad, pad);

        TextView tvType = new TextView(this);
        tvType.setText(bloodType);
        tvType.setTextSize(16);
        tvType.setTypeface(null, android.graphics.Typeface.BOLD);
        tvType.setTextColor(outStock
                ? Color.parseColor("#C62828")
                : Color.parseColor("#1B5E20"));
        tvType.setGravity(android.view.Gravity.CENTER);

        TextView tvStatus = new TextView(this);
        tvStatus.setText(outStock ? "Low" : "OK");
        tvStatus.setTextSize(10);
        tvStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        tvStatus.setTextColor(outStock
                ? Color.parseColor("#E53935")
                : Color.parseColor("#43A047"));
        tvStatus.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.topMargin = dpToPx(4);
        tvStatus.setLayoutParams(statusParams);

        inner.addView(tvType);
        inner.addView(tvStatus);
        card.addView(inner);
        return card;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ─── Broadcast ─────────────────────────────────────────────────────────────

    private void setupBroadcastButton() {
        if (btnBroadcast != null) {
            btnBroadcast.setOnClickListener(v -> showBroadcastDialog());
        }
    }

    private void showBroadcastDialog() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_broadcast_event, null);

        EditText etEventName   = dialogView.findViewById(R.id.etEventName);
        EditText etDescription = dialogView.findViewById(R.id.etDescription);
        EditText etEventDate   = dialogView.findViewById(R.id.etEventDate);

        etEventDate.setOnClickListener(v -> showDatePicker(etEventDate));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Broadcast New Event")
                .setView(dialogView)
                .setPositiveButton("Broadcast", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etEventName.getText().toString().trim();
                String desc = etDescription.getText().toString().trim();
                String date = etEventDate.getText().toString().trim();

                if (name.isEmpty()) {
                    etEventName.setError("Event name is required");
                    etEventName.requestFocus();
                    return;
                }
                if (desc.isEmpty()) {
                    etDescription.setError("Description is required");
                    etDescription.requestFocus();
                    return;
                }
                if (date.isEmpty()) {
                    Toast.makeText(this, "Please select an event date", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Location is auto-filled with the staff's centre name
                String loc = centreName != null ? centreName : staffCentreID;

                saveEventToFirestore(name, desc, loc, date);
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void showDatePicker(EditText targetField) {
        Calendar calendar = Calendar.getInstance();
        int year  = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day   = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.set(selectedYear, selectedMonth, selectedDay);

                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                    String formattedDate = sdf.format(selected.getTime());

                    targetField.setText(formattedDate);
                },
                year, month, day
        );

        // Optional: prevent picking past dates for events
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);

        datePickerDialog.show();
    }
    private void saveEventToFirestore(String name, String desc, String loc, String date) {
        String eventId = "E" + System.currentTimeMillis();

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("centreID",      staffCentreID);  // ← Save with staff's centre
        eventData.put("eventName",     name);
        eventData.put("description",   desc);
        eventData.put("location",      loc);
        eventData.put("eventDate",     date);
        eventData.put("createdAt",     Timestamp.now());
        eventData.put("broadcastedAt", Timestamp.now());

        db.collection("event")
                .document(eventId)
                .set(eventData)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this,
                                "Event \"" + name + "\" has been broadcast!",
                                Toast.LENGTH_LONG).show()
                )
                .addOnFailureListener(ex ->
                        Toast.makeText(this,
                                "Broadcast failed. Please try again.",
                                Toast.LENGTH_SHORT).show()
                );
    }

    // ─── Cleanup ───────────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bookingListener != null) bookingListener.remove();
        if (stockListener != null) stockListener.remove();
        if (eventListener != null) eventListener.remove();
    }

    // ─── Bottom Nav ────────────────────────────────────────────────────────────

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_dashboard);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_dashboard) {
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_queue) {
                startActivity(new Intent(this, QueueActivity.class));
                finish(); overridePendingTransition(0, 0); return true;
            } else if (id == R.id.nav_search) {
                startActivity(new Intent(this, SearchActivity.class));
                finish(); overridePendingTransition(0, 0); return true;
            } else if (id == R.id.nav_stock) {
                startActivity(new Intent(this, StockActivity.class));
                finish(); overridePendingTransition(0, 0); return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, StaffProfileActivity.class));
                finish(); overridePendingTransition(0, 0); return true;
            }
            return false;
        });
    }
}