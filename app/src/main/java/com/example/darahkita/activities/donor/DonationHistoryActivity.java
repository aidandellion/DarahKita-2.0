package com.example.darahkita.activities.donor;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.darahkita.R;
import com.example.darahkita.adapters.HistoryAdapter;
import com.example.darahkita.models.Booking;
import com.example.darahkita.utils.BottomNavHelper;
import com.google.firebase.auth.FirebaseAuth;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class DonationHistoryActivity extends AppCompatActivity {
    private RecyclerView rvHistory;
    private TextView tvNextEligibleDate;
    private List<Booking> historyList = new ArrayList<>();
    private String nextEligibleDateIso;
    private HistoryAdapter adapter;
    private static final SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_donation_history);
        rvHistory = findViewById(R.id.rvHistory);
        tvNextEligibleDate = findViewById(R.id.tvNextEligibleDate);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(historyList);
        rvHistory.setAdapter(adapter);

        findViewById(R.id.btnViewCalendar).setOnClickListener(v -> {
            Intent intent = new Intent(this, DonationScheduleActivity.class);
            intent.putExtra("nextEligibleDateIso", nextEligibleDateIso);
            startActivity(intent);
        });

        loadHistory();
        BottomNavHelper.setup(this, BottomNavHelper.TAB_HISTORY);
    }

    private void loadHistory() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseFirestore.getInstance().collection("donation")   // aiman punye - history baca donation skang jgn kacau
                .whereEqualTo("userID", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    historyList.clear();
                    Date latestCompletedDate = null;
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Booking booking = doc.toObject(Booking.class);
                        if (booking == null || booking.getBookingDate() == null) continue;
                        historyList.add(booking);
                        if ("Completed".equals(booking.getStatus())) {
                            if (latestCompletedDate == null || booking.getBookingDate().after(latestCompletedDate))
                                latestCompletedDate = booking.getBookingDate();
                        }
                    }
                    historyList.sort((a, b) -> b.getBookingDate().compareTo(a.getBookingDate()));
                    adapter.notifyDataSetChanged();
                    updateNextEligibleDate(latestCompletedDate);
                })
                .addOnFailureListener(e -> { /* aiman punye - firestore fetch failed */ });
    }

    private void updateNextEligibleDate(Date latestCompletedDate) {
        if (latestCompletedDate == null) {
            tvNextEligibleDate.setText("You're eligible now");
            nextEligibleDateIso = null;
            return;
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(latestCompletedDate);
        cal.add(Calendar.DATE, 56);
        SimpleDateFormat display = new SimpleDateFormat("d MMMM yyyy", Locale.US);
        tvNextEligibleDate.setText(display.format(cal.getTime()));
        nextEligibleDateIso = isoFormat.format(cal.getTime());
    }
}