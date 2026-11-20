package com.example.darahkita.activities.donor;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.darahkita.R;
import com.example.darahkita.adapters.CalendarDayAdapter;
import com.example.darahkita.adapters.TimeSlotAdapter;
import com.example.darahkita.models.Booking;
import com.example.darahkita.utils.BottomNavHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import com.example.darahkita.utils.NotificationHelper;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import java.util.Date;
import com.example.darahkita.utils.ReminderReceiver;
import android.os.Build;
import com.example.darahkita.utils.EmailHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Date;

public class SelectDateTimeActivity extends AppCompatActivity {

    private TextView tvMonthYear, btnConfirm;
    private RecyclerView rvCalendarDays, rvTimeSlots;

    private Calendar displayedMonth = Calendar.getInstance();
    private Calendar selectedDate = null;
    private String selectedSlot = null;

    private CalendarDayAdapter dayAdapter;
    private TimeSlotAdapter slotAdapter;

    // centreName/centreAddress/centreLat/centreLng kept LOCAL ONLY for email + notification text,
    // NOT saved to Firestore anymore (Aida: keep centreId only in booking doc)
    private String centreId, centreName, centreAddress;
    private double centreLat, centreLng;
    private Map<String, Boolean> healthScreening;
    private boolean eligible;
    private String donorName = "Donor"; // LOCAL ONLY, not saved to Firestore

    private String donorEmail = ""; // AIMAN: for email notifications

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_date_time);

        readIntentExtras();

        tvMonthYear = findViewById(R.id.tvMonthYear);
        btnConfirm = findViewById(R.id.btnConfirm);
        rvCalendarDays = findViewById(R.id.rvCalendarDays);
        rvTimeSlots = findViewById(R.id.rvTimeSlots);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnPrevMonth).setOnClickListener(v -> changeMonth(-1));
        findViewById(R.id.btnNextMonth).setOnClickListener(v -> changeMonth(1));

        setupCalendar();
        setupTimeSlots();
        loadDonorName();

        btnConfirm.setOnClickListener(v -> confirmAppointment());

        BottomNavHelper.setup(this, BottomNavHelper.TAB_BOOKING);
    }

    @SuppressWarnings("unchecked")
    private void readIntentExtras() {
        centreId = getIntent().getStringExtra("centreId");
        centreName = getIntent().getStringExtra("centreName");
        centreAddress = getIntent().getStringExtra("centreAddress");
        centreLat = getIntent().getDoubleExtra("centreLat", 0);
        centreLng = getIntent().getDoubleExtra("centreLng", 0);
        eligible = getIntent().getBooleanExtra("eligible", true);

        Object answersExtra = getIntent().getSerializableExtra("eligibilityAnswers");
        healthScreening = (answersExtra instanceof Map) ? (Map<String, Boolean>) answersExtra : new HashMap<>();
    }

    private void loadDonorName() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        donorEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String name = doc.getString("name");
                    if (name == null || name.isEmpty()) name = doc.getString("fullName");
                    if (name != null && !name.isEmpty()) donorName = name;
                });
    }

    private void setupCalendar() {
        rvCalendarDays.setLayoutManager(new GridLayoutManager(this, 7));
        rvCalendarDays.setNestedScrollingEnabled(false);
        renderMonth();
    }

    private void setupTimeSlots() {
        List<String> slots = Arrays.asList("09:00 AM", "10:00 AM", "11:00 AM", "12:00 PM", "03:00 PM", "04:00 PM");
        rvTimeSlots.setLayoutManager(new GridLayoutManager(this, 3));
        rvTimeSlots.setNestedScrollingEnabled(false);
        slotAdapter = new TimeSlotAdapter(slots, slot -> selectedSlot = slot);
        rvTimeSlots.setAdapter(slotAdapter);
    }

    private void changeMonth(int delta) {
        displayedMonth.add(Calendar.MONTH, delta);
        renderMonth();
    }

    private void renderMonth() {
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.US);
        tvMonthYear.setText(monthFormat.format(displayedMonth.getTime()));

        Calendar firstDay = (Calendar) displayedMonth.clone();
        firstDay.set(Calendar.DAY_OF_MONTH, 1);
        int leadingBlanks = firstDay.get(Calendar.DAY_OF_WEEK) - 1;
        int daysInMonth = displayedMonth.getActualMaximum(Calendar.DAY_OF_MONTH);

        List<Integer> days = new ArrayList<>();
        for (int i = 0; i < leadingBlanks; i++) days.add(null);
        for (int d = 1; d <= daysInMonth; d++) days.add(d);

        boolean[] disabled = new boolean[days.size()];
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0); today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0);

        int selectedPosition = -1;

        for (int i = 0; i < days.size(); i++) {
            Integer day = days.get(i);
            if (day == null) continue;

            Calendar cellDate = (Calendar) displayedMonth.clone();
            cellDate.set(Calendar.DAY_OF_MONTH, day);
            cellDate.set(Calendar.HOUR_OF_DAY, 0); cellDate.set(Calendar.MINUTE, 0); cellDate.set(Calendar.SECOND, 0); cellDate.set(Calendar.MILLISECOND, 0);

            disabled[i] = cellDate.before(today);

            if (selectedDate != null
                    && selectedDate.get(Calendar.YEAR) == cellDate.get(Calendar.YEAR)
                    && selectedDate.get(Calendar.MONTH) == cellDate.get(Calendar.MONTH)
                    && selectedDate.get(Calendar.DAY_OF_MONTH) == cellDate.get(Calendar.DAY_OF_MONTH)) {
                selectedPosition = i;
            }
        }

        dayAdapter = new CalendarDayAdapter(days, disabled, (day, position) -> {
            selectedDate = (Calendar) displayedMonth.clone();
            selectedDate.set(Calendar.DAY_OF_MONTH, day);
            dayAdapter.setSelectedPosition(position);
        });
        dayAdapter.setSelectedPosition(selectedPosition);
        rvCalendarDays.setAdapter(dayAdapter);
    }

    private void confirmAppointment() {
        if (selectedDate == null) {
            showQuickMessage("Please select a date.");
            return;
        }
        if (selectedSlot == null) {
            showQuickMessage("Please select a time slot.");
            return;
        }
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            showQuickMessage("You're not logged in. Please log in again.");
            return;
        }

        String donorId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String dateStr = dateFormat.format(selectedDate.getTime()); // still used for email/notification/reminder text only
        Date bookingDate = selectedDate.getTime();

        Booking booking = new Booking(donorId, centreId, bookingDate, selectedSlot);

        FirebaseFirestore.getInstance().collection("booking").add(booking)
                .addOnSuccessListener(documentReference -> {
                    android.util.Log.d("EMAIL", "Success block reached, email: " + donorEmail);
                    EmailHelper.sendBookingConfirmation(donorEmail, donorName, centreName, dateStr, selectedSlot);
                    NotificationHelper.sendBookingConfirmation(this, centreName, dateStr, selectedSlot);
                    scheduleReminder(centreName, dateStr, selectedSlot);
                    showSuccessDialog();
                })
                .addOnFailureListener(e -> showQuickMessage("Failed to confirm booking: " + e.getMessage()));
    }

    private void scheduleReminder(String centreName, String dateStr, String time) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.US);
            Date appointmentDate = sdf.parse(dateStr + " " + time);
            if (appointmentDate == null) return;

            Calendar reminderTime = Calendar.getInstance();
            reminderTime.setTime(appointmentDate);
            reminderTime.add(Calendar.DAY_OF_YEAR, -1);

            if (reminderTime.getTimeInMillis() <= System.currentTimeMillis()) return;

            Intent intent = new Intent(this, ReminderReceiver.class);
            intent.putExtra("centreName", centreName);
            intent.putExtra("time", time);
            intent.putExtra("date", dateStr);
            intent.putExtra("email", donorEmail);
            intent.putExtra("name", donorName);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            android.util.Log.d("REMINDER", "Scheduled for: " + reminderTime.getTime().toString());
            android.util.Log.d("REMINDER", "Current time: " + new java.util.Date().toString());

            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.util.Log.d("REMINDER", "canScheduleExact: " + alarmManager.canScheduleExactAlarms());
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminderTime.getTimeInMillis(), pendingIntent);
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminderTime.getTimeInMillis(), pendingIntent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showSuccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Appointment Confirmed!")
                .setMessage("Your blood donation slot has been booked. See you there!")
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> goBackToBookingHub())
                .show();
    }

    private void goBackToBookingHub() {
        Intent intent = new Intent(this, BookingHubActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void showQuickMessage(String message) {
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show();
    }
}