package com.example.darahkita.activities.staff;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.darahkita.R;
import com.example.darahkita.activities.auth.LoginActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import android.widget.TextView;
import java.util.Locale;

public class StaffProfileActivity extends AppCompatActivity {

    private static final String TAG = "StaffProfileActivity";

    // UI Components
    private TextView tvProfileName, tvProfileRole, tvProfileEmail, tvAvatarInitials, tvProfileCentre;
    private MaterialButton btnLogout;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Multi-centre variables
    private String staffCentreID;
    private String centreName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff_profile);

        // 1. Bind UI Components
        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfileRole = findViewById(R.id.tvProfileRole);
        tvProfileEmail = findViewById(R.id.tvProfileEmail);
        tvAvatarInitials = findViewById(R.id.tvAvatarInitials);
        tvProfileCentre = findViewById(R.id.tvProfileCentre);
        btnLogout = findViewById(R.id.btnLogout);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // 2. Fetch Data from Firestore
        if (mAuth.getCurrentUser() != null) {
            String uid = mAuth.getCurrentUser().getUid();

            db.collection("users").document(uid).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String name = documentSnapshot.getString("fullName");
                            String role = documentSnapshot.getString("role");
                            String email = documentSnapshot.getString("email");
                            staffCentreID = documentSnapshot.getString("centreID");

                            // Set Name & Initials (Null-safe)
                            if (name != null && !name.isEmpty() && tvProfileName != null) {
                                tvProfileName.setText(name);
                                if (tvAvatarInitials != null) {
                                    String initial = name.substring(0, 1).toUpperCase(Locale.ROOT);
                                    tvAvatarInitials.setText(initial);
                                }
                            }

                            // Set Role (Null-safe)
                            if (role != null && tvProfileRole != null) {
                                tvProfileRole.setText(role);
                            }

                            // Set Email (Null-safe)
                            if (email != null && tvProfileEmail != null) {
                                tvProfileEmail.setText(email);
                            }

                            // Fetch centre name using centreID
                            if (staffCentreID != null && !staffCentreID.isEmpty()) {
                                fetchCentreName(staffCentreID.trim());
                            } else {
                                Log.e(TAG, "centreID missing on user document for uid: " + uid);
                                if (tvProfileCentre != null) {
                                    tvProfileCentre.setText("Centre: Unknown");
                                }
                            }

                        } else {
                            if (tvProfileName != null) tvProfileName.setText("User Not Found");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to fetch user document", e);
                        Toast.makeText(StaffProfileActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }

        // 3. Logout Button Logic
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                mAuth.signOut();
                Intent intent = new Intent(StaffProfileActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                Toast.makeText(StaffProfileActivity.this, "Successfully logged out", Toast.LENGTH_SHORT).show();
            });
        }

        // 4. Set Navigation Bar
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_profile);

            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_dashboard) {
                    startActivity(new Intent(StaffProfileActivity.this, StaffDashboardActivity.class));
                    overridePendingTransition(0, 0);
                    finish();
                    return true;
                } else if (id == R.id.nav_queue) {
                    startActivity(new Intent(StaffProfileActivity.this, QueueActivity.class));
                    overridePendingTransition(0, 0);
                    finish();
                    return true;
                } else if (id == R.id.nav_search) {
                    startActivity(new Intent(StaffProfileActivity.this, SearchActivity.class));
                    overridePendingTransition(0, 0);
                    finish();
                    return true;
                } else if (id == R.id.nav_stock) {
                    startActivity(new Intent(StaffProfileActivity.this, StockActivity.class));
                    overridePendingTransition(0, 0);
                    finish();
                    return true;
                } else if (id == R.id.nav_profile) {
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * Fetch centre name from centre collection using centreID
     */
    private void fetchCentreName(String centreID) {
        // Show loading state
        if (tvProfileCentre != null) {
            tvProfileCentre.setText("Loading centre...");
        }

        // Add 500ms delay to ensure centre collection is ready
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            db.collection("centre").document(centreID).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            centreName = doc.getString("name");
                            Log.d(TAG, "Centre name fetched: " + centreName);
                            if (tvProfileCentre != null && centreName != null) {
                                tvProfileCentre.setText(centreName);  // ✅ Show actual name
                            }
                        } else {
                            Log.w(TAG, "Centre document not found for centreID: " + centreID);
                            if (tvProfileCentre != null) {
                                tvProfileCentre.setText("Centre: " + centreID);
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error fetching centre: " + e.getMessage());
                        if (tvProfileCentre != null) {
                            tvProfileCentre.setText("Centre: " + centreID);
                        }
                    });
        }, 500);  // 500ms delay
    }
}