package com.example.darahkita.activities.staff;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.darahkita.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchActivity extends AppCompatActivity {

    private EditText etSearchDonor;
    private RecyclerView rvSearchResult;
    private DonorAdapter adapter;
    private List<DocumentSnapshot> allUsers = new ArrayList<>();
    private List<DocumentSnapshot> filteredUsers = new ArrayList<>();
    private FirebaseFirestore db;
    private ListenerRegistration userListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        etSearchDonor = findViewById(R.id.etSearchDonor);
        rvSearchResult = findViewById(R.id.rvSearchResult);
        rvSearchResult.setLayoutManager(new LinearLayoutManager(this));

        db = FirebaseFirestore.getInstance();

        // Real-time listener for all users (no centre filter)
        userListener = db.collection("users").addSnapshotListener((queryDocumentSnapshots, e) -> {
            if (e != null) {
                Toast.makeText(this, "Error loading donors: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }

            if (queryDocumentSnapshots != null) {
                allUsers.clear();
                for (DocumentSnapshot doc : queryDocumentSnapshots) {
                    String role = doc.getString("role");
                    // Only show donors (not staff/admin)
                    if (!"admin".equals(role) && !"staff".equals(role)) {
                        allUsers.add(doc);
                    }
                }
                // Refresh filtered list with current search text
                String searchInput = etSearchDonor.getText().toString().toLowerCase();
                refreshFilteredList(searchInput);
            }
        });

        // Search Logic
        etSearchDonor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String input = s.toString().toLowerCase();
                refreshFilteredList(input);
            }
        });

        // Nav Bar
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_search);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_dashboard) { startActivity(new Intent(this, StaffDashboardActivity.class)); finish(); overridePendingTransition(0, 0); return true; }
            else if (id == R.id.nav_queue) { startActivity(new Intent(this, QueueActivity.class)); finish(); overridePendingTransition(0, 0); return true; }
            else if (id == R.id.nav_search) { return true; }
            else if (id == R.id.nav_stock) { startActivity(new Intent(this, StockActivity.class)); finish(); overridePendingTransition(0, 0); return true; }
            else if (id == R.id.nav_profile) { startActivity(new Intent(this, StaffProfileActivity.class)); finish(); overridePendingTransition(0, 0); return true; }
            return false;
        });
    }

    private void refreshFilteredList(String input) {
        filteredUsers.clear();
        for (DocumentSnapshot doc : allUsers) {
            String name = doc.getString("fullName");      // ← Fixed
            String ic = doc.getString("icNumber");         // ← Fixed
            if ((name != null && name.toLowerCase().contains(input)) ||
                    (ic != null && ic.contains(input))) {
                filteredUsers.add(doc);
            }
        }
        if (adapter == null) {
            adapter = new DonorAdapter();
            rvSearchResult.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }
    }

    // ─── Show booking history (ALL bookings across ALL centres - Option B) ────────────────────────

    private void showBookingHistory(String userId, String donorName, String phone, String ic) {
        db.collection("booking").whereEqualTo("userID", userId).get()
                .addOnSuccessListener(bookingSnapshots -> {
                    db.collection("donation").whereEqualTo("userID", userId).get()
                            .addOnSuccessListener(donationSnapshots -> {

                                // Collect all unique centreIDs
                                List<String> centreIDs = new ArrayList<>();
                                for (QueryDocumentSnapshot doc : bookingSnapshots) {
                                    String cid = doc.getString("centreID");
                                    if (cid != null && !centreIDs.contains(cid)) centreIDs.add(cid);
                                }
                                for (QueryDocumentSnapshot doc : donationSnapshots) {
                                    String cid = doc.getString("centreID");
                                    if (cid != null && !centreIDs.contains(cid)) centreIDs.add(cid);
                                }

                                if (centreIDs.isEmpty()) {
                                    // No records at all
                                    showHistoryDialog(donorName, ic, phone, bookingSnapshots, donationSnapshots, new HashMap<>());
                                    return;
                                }

                                // Fetch all centre names first
                                Map<String, String> centreNameMap = new HashMap<>();
                                final int[] remaining = {centreIDs.size()};

                                for (String centreID : centreIDs) {
                                    db.collection("centre").document(centreID).get()
                                            .addOnSuccessListener(centreDoc -> {
                                                String cname = centreDoc.exists() ? centreDoc.getString("name") : centreID;
                                                centreNameMap.put(centreDoc.getId(), cname != null ? cname : centreID);

                                                remaining[0]--;
                                                if (remaining[0] == 0) {
                                                    // All centres fetched, now show dialog
                                                    showHistoryDialog(donorName, ic, phone, bookingSnapshots, donationSnapshots, centreNameMap);
                                                }
                                            })
                                            .addOnFailureListener(e -> {
                                                centreNameMap.put(centreID, centreID);  // Fallback
                                                remaining[0]--;
                                                if (remaining[0] == 0) {
                                                    showHistoryDialog(donorName, ic, phone, bookingSnapshots, donationSnapshots, centreNameMap);
                                                }
                                            });
                                }
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Error fetching donations", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error fetching bookings", Toast.LENGTH_SHORT).show());
    }

    private void showHistoryDialog(String donorName, String ic, String phone,
                                   com.google.firebase.firestore.QuerySnapshot bookingSnapshots,
                                   com.google.firebase.firestore.QuerySnapshot donationSnapshots,
                                   Map<String, String> centreNameMap) {

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        StringBuilder sb = new StringBuilder();
        sb.append("IC: ").append(ic != null ? ic : "N/A").append("\n");
        sb.append("Phone: ").append(phone != null ? phone : "N/A").append("\n\n");

        boolean hasRecords = false;

        // Booking records (Pending/Eligible - not yet finalized)
        if (!bookingSnapshots.isEmpty()) {
            hasRecords = true;
            sb.append("🕒 UPCOMING / PENDING BOOKINGS\n");
            sb.append("──────────────────────\n");
            for (QueryDocumentSnapshot doc : bookingSnapshots) {
                Timestamp ts = doc.getTimestamp("date"); // adjust field name if different
                String dateStr = ts != null ? sdf.format(ts.toDate()) : "N/A";
                String status = doc.getString("status");
                String centreID = doc.getString("centreID");
                String centreName = centreNameMap.getOrDefault(centreID, centreID);
                String timeslot = doc.getString("timeslot");

                sb.append("📅 ").append(dateStr).append("\n");
                sb.append("🕐 ").append(timeslot != null ? timeslot : "-").append("\n");
                sb.append("🏥 ").append(centreName).append("\n");
                sb.append("📌 Status: ").append(status != null ? status : "-").append("\n\n");
            }
        }

        // Donation records (Completed/Absent/Ineligible)
        if (!donationSnapshots.isEmpty()) {
            hasRecords = true;
            sb.append("✅ PAST DONATIONS\n");
            sb.append("──────────────────────\n");
            for (QueryDocumentSnapshot doc : donationSnapshots) {
                Timestamp ts = doc.getTimestamp("recordedAt");
                String dateStr = ts != null ? sdf.format(ts.toDate()) : "N/A";
                String status = doc.getString("status");
                String centreID = doc.getString("centreID");
                String centreName = centreNameMap.getOrDefault(centreID, centreID);
                String bloodType = doc.getString("bloodType");
                String timeslot = doc.getString("timeslot");

                sb.append("📅 ").append(dateStr).append("\n");
                sb.append("🕐 ").append(timeslot != null ? timeslot : "-").append("\n");
                sb.append("🏥 ").append(centreName).append("\n");
                sb.append("🩸 ").append(bloodType != null ? bloodType : "N/A").append("\n");
                sb.append("📌 Status: ").append(status != null ? status : "-").append("\n\n");
            }
        }

        if (!hasRecords) {
            sb.append("No appointment history found.");
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("History: " + donorName)
                .setMessage(sb.toString())
                .setPositiveButton("Close", null)
                .show();
    }

    private void fetchAndShowBookingDetail(StringBuilder historyBuilder, String dateStr, String timeslot, String status, String centreID, String type) {
        db.collection("centre").document(centreID).get()
                .addOnSuccessListener(doc -> {
                    String centreName = doc.exists() ? doc.getString("name") : centreID;
                    historyBuilder.append("📅 Date: ").append(dateStr).append("\n");
                    historyBuilder.append("🕐 Time: ").append(timeslot).append("\n");
                    historyBuilder.append("📍 Centre: ").append(centreName).append("\n");
                    historyBuilder.append("📋 Status: ").append(status).append(" (").append(type).append(")\n");
                    historyBuilder.append("--------------------------\n");
                })
                .addOnFailureListener(e -> {
                    // Fallback if centre fetch fails
                    historyBuilder.append("📅 Date: ").append(dateStr).append("\n");
                    historyBuilder.append("🕐 Time: ").append(timeslot).append("\n");
                    historyBuilder.append("📍 Centre: ").append(centreID).append("\n");
                    historyBuilder.append("📋 Status: ").append(status).append(" (").append(type).append(")\n");
                    historyBuilder.append("--------------------------\n");
                });
    }

    private void fetchAndShowDonationDetail(StringBuilder historyBuilder, String dateStr, String status, String centreID, String bloodType) {
        db.collection("centre").document(centreID).get()
                .addOnSuccessListener(doc -> {
                    String centreName = doc.exists() ? doc.getString("name") : centreID;
                    historyBuilder.append("📅 Date: ").append(dateStr).append("\n");
                    historyBuilder.append("📍 Centre: ").append(centreName).append("\n");
                    historyBuilder.append("📋 Status: ").append(status).append(" (Completed)\n");
                    historyBuilder.append("🩸 Blood Type: ").append(bloodType).append("\n");
                    historyBuilder.append("--------------------------\n");
                })
                .addOnFailureListener(e -> {
                    // Fallback if centre fetch fails
                    historyBuilder.append("📅 Date: ").append(dateStr).append("\n");
                    historyBuilder.append("📍 Centre: ").append(centreID).append("\n");
                    historyBuilder.append("📋 Status: ").append(status).append(" (Completed)\n");
                    historyBuilder.append("🩸 Blood Type: ").append(bloodType).append("\n");
                    historyBuilder.append("--------------------------\n");
                });
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userListener != null) {
            userListener.remove();
        }
    }

    private class DonorAdapter extends RecyclerView.Adapter<DonorAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_donor, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DocumentSnapshot doc = filteredUsers.get(position);

            String name = doc.getString("fullName");          // ← Fixed
            String phone = doc.getString("phoneNumber");      // ← Fixed
            String ic = doc.getString("icNumber");            // ← Fixed
            String email = doc.getString("email");
            String blood = doc.getString("bloodType");

            holder.tvName.setText(name != null ? name : "Unknown");
            holder.tvBlood.setText(blood != null ? blood : "N/A");
            holder.tvEmail.setText(email != null ? email : "-");
            holder.tvPhone.setText("Phone: " + (phone != null ? phone : "-"));
            holder.tvIC.setText("IC: " + (ic != null ? ic : "-"));

            holder.btnViewHistory.setOnClickListener(v -> {
                showBookingHistory(doc.getId(), name, phone, ic);
            });
        }

        @Override
        public int getItemCount() { return filteredUsers.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvBlood, tvEmail, tvPhone, tvIC;
            MaterialButton btnViewHistory;

            public ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.itemDonorName);
                tvBlood = itemView.findViewById(R.id.itemBloodType);
                tvEmail = itemView.findViewById(R.id.itemDonorEmail);
                tvPhone = itemView.findViewById(R.id.itemDonorPhone);
                tvIC = itemView.findViewById(R.id.itemDonorIC);
                btnViewHistory = itemView.findViewById(R.id.btnViewHistory);
            }
        }
    }
}