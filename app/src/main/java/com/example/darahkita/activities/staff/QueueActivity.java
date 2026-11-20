package com.example.darahkita.activities.staff;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.example.darahkita.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueueActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private List<DocumentSnapshot> bookingList;
    private Map<String, String> fullnameMap;
    private Map<String, String> bloodTypeMap;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private QueueAdapter adapter;
    private String staffCentreID;
    private ListenerRegistration bookingListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_queue);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        recyclerView = findViewById(R.id.rvQueue);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        bookingList = new ArrayList<>();
        fullnameMap = new HashMap<>();
        bloodTypeMap = new HashMap<>();

        // Fetch staff's centreID first
        fetchStaffCentreID();
        setupBottomNavigation();
    }

    private void fetchStaffCentreID() {
        String currentUID = auth.getCurrentUser().getUid();

        db.collection("users").document(currentUID).get()
                .addOnSuccessListener(doc -> {
                    staffCentreID = doc.getString("centreID");
                    Log.d("Queue", "Staff centre: " + staffCentreID);

                    if (staffCentreID != null) {
                        // Delay 1 second then start loading data
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            fetchUserData();
                        }, 1000);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error fetching centre: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setSelectedItemId(R.id.nav_queue);

        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_dashboard) {
                startActivity(new Intent(this, StaffDashboardActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.nav_queue) {
                return true;
            } else if (itemId == R.id.nav_search) {
                startActivity(new Intent(this, SearchActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.nav_stock) {
                startActivity(new Intent(this, StockActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.nav_profile) {
                startActivity(new Intent(this, StaffProfileActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }

    private void fetchUserData() {
        db.collection("users").get().addOnSuccessListener(queryDocumentSnapshots -> {
            fullnameMap.clear();
            bloodTypeMap.clear();
            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                String name = doc.getString("fullName");
                String blood = doc.getString("bloodType");

                fullnameMap.put(doc.getId(), name != null ? name : "Unknown");
                bloodTypeMap.put(doc.getId(), blood != null ? blood : "N/A");
            }
            fetchBookings();
        });
    }

    private void fetchBookings() {
        if (staffCentreID == null) return;

        Timestamp[] todayRange = getTodayRange();

        bookingListener = db.collection("booking")
                .whereEqualTo("centreID", staffCentreID)  // ← Filter by staff's centre
                .whereGreaterThanOrEqualTo("bookingDate", todayRange[0])
                .whereLessThanOrEqualTo("bookingDate", todayRange[1])
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null || queryDocumentSnapshots == null) {
                        Toast.makeText(this, "Error loading queue: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    bookingList.clear();
                    // Get ALL bookings for that day (not filtered by checkedIn)
                    bookingList.addAll(queryDocumentSnapshots.getDocuments());

                    if (adapter == null) {
                        adapter = new QueueAdapter(this, bookingList, fullnameMap, bloodTypeMap);
                        recyclerView.setAdapter(adapter);
                    } else {
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    /** Kira waktu mula (00:00:00) dan akhir (23:59:59.999) hari ini sebagai Firestore Timestamp */
    private Timestamp[] getTodayRange() {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        Calendar end = Calendar.getInstance();
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);

        return new Timestamp[]{ new Timestamp(start.getTime()), new Timestamp(end.getTime()) };
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bookingListener != null) {
            bookingListener.remove();
        }
    }
}