package com.example.darahkita.activities.staff;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.darahkita.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

public class StockActivity extends AppCompatActivity {

    private static final String TAG = "StockActivity";
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration stockListener;

    // Dynamic centreID from logged-in staff
    private String staffCentreID;

    // Blood type list — ikut urutan
    private final String[] BLOOD_TYPES = {"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"};

    // View arrays — ikut urutan sama macam BLOOD_TYPES
    private TextView[] tvStatus;
    private MaterialButton[] btnToggle;

    // State semasa — true = outStock (insufficient), false = sufficient
    private boolean[] outStockState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        outStockState = new boolean[8];

        initViews();
        fetchStaffCentreID();
        setupBottomNav();

        // ⚠️ TEMPORARY — run once to clean up bad trailing-space docs, then remove this line
        cleanupBadBloodStockDocs();
    }

    /**
     * Fetch staff's centreID from user profile
     */
    private void fetchStaffCentreID() {
        String currentUID = auth.getCurrentUser().getUid();

        db.collection("users").document(currentUID).get()
                .addOnSuccessListener(doc -> {
                    String rawCentreID = doc.getString("centreID");
                    staffCentreID = rawCentreID != null ? rawCentreID.trim() : null;
                    Log.d(TAG, "Staff centre: " + staffCentreID);

                    if (staffCentreID != null) {
                        loadAllStockStatus();  // Only load after getting centreID
                    } else {
                        Toast.makeText(this, "Centre ID not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error fetching centre: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void initViews() {
        tvStatus = new TextView[]{
                findViewById(R.id.tvStatusAPlus),
                findViewById(R.id.tvStatusAMinus),
                findViewById(R.id.tvStatusBPlus),
                findViewById(R.id.tvStatusBMinus),
                findViewById(R.id.tvStatusABPlus),
                findViewById(R.id.tvStatusABMinus),
                findViewById(R.id.tvStatusOPlus),
                findViewById(R.id.tvStatusOMinus)
        };

        btnToggle = new MaterialButton[]{
                findViewById(R.id.btnToggleAPlus),
                findViewById(R.id.btnToggleAMinus),
                findViewById(R.id.btnToggleBPlus),
                findViewById(R.id.btnToggleBMinus),
                findViewById(R.id.btnToggleABPlus),
                findViewById(R.id.btnToggleABMinus),
                findViewById(R.id.btnToggleOPlus),
                findViewById(R.id.btnToggleOMinus)
        };

        // Setup click listener untuk setiap toggle
        for (int i = 0; i < btnToggle.length; i++) {
            final int idx = i;
            btnToggle[i].setOnClickListener(v -> toggleStock(idx));
        }
    }

    /**
     * Load semua stock status dari Firestore dengan real-time listener
     */
    private void loadAllStockStatus() {
        if (staffCentreID == null) {
            Toast.makeText(this, "Centre ID not available", Toast.LENGTH_SHORT).show();
            return;
        }

        // Real-time listener instead of one-time .get()
        stockListener = db.collection("bloodStock")
                .whereEqualTo("centreID", staffCentreID)  // ← Filter by staff's centre
                .addSnapshotListener((querySnapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error loading stock", e);
                        Toast.makeText(this, "Error loading blood stock: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (querySnapshot != null) {
                        // Reset all states first
                        for (int i = 0; i < outStockState.length; i++) {
                            outStockState[i] = false;
                        }

                        // Update with latest data
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            String bloodType = doc.getString("bloodType");
                            Boolean outStock = doc.getBoolean("outStock");

                            if (bloodType == null || outStock == null) continue;

                            // Cari index dalam array
                            for (int i = 0; i < BLOOD_TYPES.length; i++) {
                                if (BLOOD_TYPES[i].equals(bloodType)) {
                                    outStockState[i] = outStock;
                                    updateUI(i);
                                    break;
                                }
                            }
                        }
                    }
                });
    }

    /**
     * Toggle state dan save ke Firestore
     */
    private void toggleStock(int idx) {
        // Flip state
        outStockState[idx] = !outStockState[idx];
        updateUI(idx);
        saveToFirestore(idx);
    }

    /**
     * Update UI ikut state semasa
     */
    private void updateUI(int idx) {
        boolean isOutStock = outStockState[idx];

        if (isOutStock) {
            // Insufficient — merah
            tvStatus[idx].setText("Insufficient");
            tvStatus[idx].setTextColor(Color.parseColor("#C62828"));
            btnToggle[idx].setText("Set Sufficient");
            btnToggle[idx].setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#C62828"))
            );
        } else {
            // Sufficient — hijau
            tvStatus[idx].setText("Sufficient");
            tvStatus[idx].setTextColor(Color.parseColor("#2E7D32"));
            btnToggle[idx].setText("Set Insufficient");
            btnToggle[idx].setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#2E7D32"))
            );
        }
    }

    /**
     * Save outStock ke Firestore dengan centreID
     */
    private void saveToFirestore(int idx) {
        if (staffCentreID == null) {
            Toast.makeText(this, "Centre ID not available", Toast.LENGTH_SHORT).show();
            return;
        }

        String bloodType = BLOOD_TYPES[idx];
        String docId = staffCentreID + "_" + bloodType.replace("+", "Plus").replace("-", "Minus");

        Map<String, Object> data = new HashMap<>();
        data.put("centreID", staffCentreID);  // ← Always use staff's centre
        data.put("bloodType", bloodType);
        data.put("outStock", outStockState[idx]);
        data.put("lastUpdated", Timestamp.now());

        db.collection("bloodStock")
                .document(docId)
                .set(data)
                .addOnSuccessListener(unused -> {
                    String msg = outStockState[idx] ? "Insufficient" : "Sufficient";
                    Toast.makeText(this, bloodType + " → " + msg, Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Saved: " + docId + " outStock=" + outStockState[idx]);
                })
                .addOnFailureListener(e -> {
                    // Rollback UI kalau save gagal
                    outStockState[idx] = !outStockState[idx];
                    updateUI(idx);
                    Toast.makeText(this, "Failed to save. Try again.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed save: " + docId, e);
                });
    }

    /**
     * ⚠️ TEMPORARY CLEANUP — run once to fix docs created with a trailing-space centreID.
     * Finds bloodStock docs where centreID has leading/trailing whitespace, recreates
     * a clean doc under the trimmed centreID (set to sufficient), and deletes the bad one.
     * REMOVE the call to this in onCreate() after running it once successfully.
     */
    private void cleanupBadBloodStockDocs() {
        db.collection("bloodStock").get()
                .addOnSuccessListener(querySnapshot -> {
                    int fixedCount = 0;
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String centreID = doc.getString("centreID");
                        String bloodType = doc.getString("bloodType");
                        if (centreID == null || bloodType == null) continue;

                        String trimmedCentreID = centreID.trim();

                        if (!centreID.equals(trimmedCentreID)) {
                            Log.d(TAG, "Fixing bad doc: " + doc.getId());

                            String correctDocId = trimmedCentreID + "_" +
                                    bloodType.replace("+", "Plus").replace("-", "Minus");

                            Map<String, Object> fixedData = new HashMap<>();
                            fixedData.put("centreID", trimmedCentreID);
                            fixedData.put("bloodType", bloodType);
                            fixedData.put("outStock", false);
                            fixedData.put("lastUpdated", Timestamp.now());

                            db.collection("bloodStock").document(correctDocId).set(fixedData);
                            db.collection("bloodStock").document(doc.getId()).delete();
                            fixedCount++;
                        }
                    }
                    Log.d(TAG, "Cleanup done. Fixed " + fixedCount + " doc(s).");
                    if (fixedCount > 0) {
                        Toast.makeText(this, "Cleanup: fixed " + fixedCount + " doc(s)", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Cleanup failed: " + e.getMessage()));
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_stock);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_dashboard) {
                startActivity(new Intent(this, StaffDashboardActivity.class));
                finish(); overridePendingTransition(0, 0); return true;
            } else if (id == R.id.nav_queue) {
                startActivity(new Intent(this, QueueActivity.class));
                finish(); overridePendingTransition(0, 0); return true;
            } else if (id == R.id.nav_search) {
                startActivity(new Intent(this, SearchActivity.class));
                finish(); overridePendingTransition(0, 0); return true;
            } else if (id == R.id.nav_stock) {
                overridePendingTransition(0, 0); return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, StaffProfileActivity.class));
                finish(); overridePendingTransition(0, 0); return true;
            }
            return false;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (stockListener != null) {
            stockListener.remove();
        }
    }
}