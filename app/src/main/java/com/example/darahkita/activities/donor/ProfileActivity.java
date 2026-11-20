package com.example.darahkita.activities.donor;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.darahkita.R;
import com.example.darahkita.activities.auth.LoginActivity;
import com.example.darahkita.utils.BottomNavHelper;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private TextView tvAvatarInitials, tvFullName;
    private TextView tvIcNumber, tvPhone, tvAddress, tvEmail, tvBirthday, tvGender, tvBloodType;

    public static final String COLLECTION_USERS = "users";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        bindViews();

        ImageButton btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> {
            auth.signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.btnEditProfile).setOnClickListener(v ->
                startActivity(new Intent(this, EditProfileActivity.class)));

        MaterialButton btnResetPassword = findViewById(R.id.btnResetPassword);
        btnResetPassword.setOnClickListener(v -> confirmAndSendResetEmail());

        BottomNavHelper.setup(this, BottomNavHelper.TAB_PROFILE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload every time the screen is shown again, in case the user just saved an edit
        loadUserProfile();
    }

    private void bindViews() {
        tvAvatarInitials = findViewById(R.id.tvAvatarInitials);
        tvFullName = findViewById(R.id.tvFullName);
        tvIcNumber = findViewById(R.id.tvIcNumber);
        tvPhone = findViewById(R.id.tvPhone);
        tvAddress = findViewById(R.id.tvAddress);
        tvEmail = findViewById(R.id.tvEmail);
        tvBirthday = findViewById(R.id.tvBirthday);
        tvGender = findViewById(R.id.tvGender);
        tvBloodType = findViewById(R.id.tvBloodType);
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;

        db.collection(COLLECTION_USERS).document(currentUser.getUid())
                .get()
                .addOnSuccessListener(this::populateFields)
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load profile: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void populateFields(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return;

        String fullName = doc.getString("name");
        if (TextUtils.isEmpty(fullName)) fullName = doc.getString("fullName");

        String icNumber = doc.getString("ic");
        if (TextUtils.isEmpty(icNumber)) icNumber = doc.getString("icNumber");

        String phone = doc.getString("phone");
        if (TextUtils.isEmpty(phone)) phone = doc.getString("phoneNumber");
        String address = doc.getString("address");
        String email = doc.getString("email");
        String gender = doc.getString("gender");
        String bloodType = doc.getString("bloodType");
        String dob = doc.getString("dob");

        if (!TextUtils.isEmpty(fullName)) {
            tvFullName.setText(fullName);
            tvAvatarInitials.setText(getInitials(fullName));
        }

        tvIcNumber.setText(!TextUtils.isEmpty(icNumber) ? icNumber : "IC Number not set");
        tvPhone.setText(!TextUtils.isEmpty(phone) ? phone : "Phone number not set");
        tvAddress.setText(!TextUtils.isEmpty(address) ? address : "Address not set");
        tvEmail.setText(!TextUtils.isEmpty(email) ? email : (auth.getCurrentUser() != null ? auth.getCurrentUser().getEmail() : "Email not set"));
        tvGender.setText(!TextUtils.isEmpty(gender) ? gender : "-");
        tvBloodType.setText(!TextUtils.isEmpty(bloodType) ? bloodType : "Blood type not set");
        tvBirthday.setText(formatDob(dob));
    }

    /**
     * dob is stored as "M/d/yyyy" (e.g. "5/31/1994") from RegisterActivity's DatePickerDialog.
     * Reformat it to "dd MMM yyyy" for display; fall back to the raw string if parsing fails.
     */
    private String formatDob(String dob) {
        if (TextUtils.isEmpty(dob)) return "Birthday not set";
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("M/d/yyyy", Locale.ENGLISH);
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
            return outputFormat.format(inputFormat.parse(dob));
        } catch (Exception e) {
            return dob; // show the raw value rather than hide it if format ever changes
        }
    }

    private String getInitials(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
            if (initials.length() >= 2) break;
        }
        return initials.length() > 0 ? initials.toString() : "?";
    }

    /**
     * Shows a confirmation dialog, then sends a Firebase password reset email
     * to the currently logged-in user's registered email address.
     */
    private void confirmAndSendResetEmail() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null || TextUtils.isEmpty(currentUser.getEmail())) {
            Toast.makeText(this, "No email found for this account.", Toast.LENGTH_SHORT).show();
            return;
        }

        String email = currentUser.getEmail();

        new AlertDialog.Builder(this)
                .setTitle("Reset Password")
                .setMessage("We'll send a password reset link to " + email + ". Continue?")
                .setPositiveButton("Send", (dialog, which) -> sendResetEmail(email))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sendResetEmail(String email) {
        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Reset link sent to " + email, Toast.LENGTH_LONG).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to send reset email: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}