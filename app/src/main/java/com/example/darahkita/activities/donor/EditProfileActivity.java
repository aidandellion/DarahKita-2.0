package com.example.darahkita.activities.donor;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.darahkita.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private TextInputEditText etFullName;
    private EditText etIcNumber, etPhone, etAddress, etEmail;
    private TextView etBirthday, tvAvatarInitials;
    private AutoCompleteTextView actvGender, actvBloodType;
    private View loadingOverlay;

    private String selectedDob = null;

    private static final String[] GENDER_OPTIONS = {"Male", "Female"};
    private static final String[] BLOOD_TYPE_OPTIONS = {
            "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"
    };

    private static final java.util.regex.Pattern IC_PATTERN =
            java.util.regex.Pattern.compile("^\\d{6}-?\\d{2}-?\\d{4}$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        bindViews();
        setupDropdowns();
        setupBirthdayPicker();
        loadExistingProfile();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());
        findViewById(R.id.btnSave).setOnClickListener(v -> attemptSave());
    }

    private void bindViews() {
        etFullName = findViewById(R.id.etFullName);
        etIcNumber = findViewById(R.id.etIcNumber);
        etPhone = findViewById(R.id.etPhone);
        etAddress = findViewById(R.id.etAddress);
        etEmail = findViewById(R.id.etEmail);           // now an EditText, not tvEmailReadOnly
        etBirthday = findViewById(R.id.etBirthday);
        tvAvatarInitials = findViewById(R.id.tvAvatarInitials);
        actvGender = findViewById(R.id.actvGender);
        actvBloodType = findViewById(R.id.actvBloodType);
        loadingOverlay = findViewById(R.id.loadingOverlay);
    }

    private void setupDropdowns() {
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, GENDER_OPTIONS);
        actvGender.setAdapter(genderAdapter);

        ArrayAdapter<String> bloodAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, BLOOD_TYPE_OPTIONS);
        actvBloodType.setAdapter(bloodAdapter);
    }

    private void setupBirthdayPicker() {
        etBirthday.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            if (!TextUtils.isEmpty(selectedDob)) {
                try {
                    SimpleDateFormat parser = new SimpleDateFormat("M/d/yyyy", Locale.ENGLISH);
                    calendar.setTime(parser.parse(selectedDob));
                } catch (Exception ignored) {}
            }
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog dialog = new DatePickerDialog(this, (view, selYear, selMonth, selDay) -> {
                selectedDob = (selMonth + 1) + "/" + selDay + "/" + selYear;
                Calendar selected = Calendar.getInstance();
                selected.set(selYear, selMonth, selDay, 0, 0, 0);
                SimpleDateFormat display = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
                etBirthday.setText(display.format(selected.getTime()));
            }, year, month, day);

            dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
            dialog.show();
        });
    }

    private void loadExistingProfile() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;

        // Pre-fill email from Auth (editable now)
        if (currentUser.getEmail() != null) {
            etEmail.setText(currentUser.getEmail());
        }

        db.collection(ProfileActivity.COLLECTION_USERS).document(currentUser.getUid())
                .get()
                .addOnSuccessListener(this::populateFields)
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load profile: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void populateFields(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return;

        String fullName = doc.getString("fullName");
        String icNumber = doc.getString("icNumber");
        String phone = doc.getString("phoneNumber");
        String address = doc.getString("address");
        String gender = doc.getString("gender");
        String bloodType = doc.getString("bloodType");
        String dob = doc.getString("dob");

        if (!TextUtils.isEmpty(fullName)) {
            etFullName.setText(fullName);
            tvAvatarInitials.setText(getInitials(fullName));
        }
        if (!TextUtils.isEmpty(icNumber)) etIcNumber.setText(icNumber);
        if (!TextUtils.isEmpty(phone)) etPhone.setText(phone);
        if (!TextUtils.isEmpty(address)) etAddress.setText(address);
        if (!TextUtils.isEmpty(gender)) actvGender.setText(gender, false);
        if (!TextUtils.isEmpty(bloodType)) actvBloodType.setText(bloodType, false);

        if (!TextUtils.isEmpty(dob)) {
            selectedDob = dob;
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("M/d/yyyy", Locale.ENGLISH);
                SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
                etBirthday.setText(outputFormat.format(inputFormat.parse(dob)));
            } catch (Exception e) {
                etBirthday.setText(dob);
            }
        }
    }

    private String getInitials(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) initials.append(Character.toUpperCase(part.charAt(0)));
            if (initials.length() >= 2) break;
        }
        return initials.length() > 0 ? initials.toString() : "?";
    }

    private void attemptSave() {
        String fullName = safeText(etFullName);
        String icNumber = safeText(etIcNumber);
        String phone = safeText(etPhone);
        String address = safeText(etAddress);
        String newEmail = safeText(etEmail);
        String gender = actvGender.getText().toString().trim();
        String bloodType = actvBloodType.getText().toString().trim();

        if (TextUtils.isEmpty(fullName)) {
            etFullName.setError("Full name is required");
            etFullName.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(newEmail)) {
            etEmail.setError("Email is required");
            etEmail.requestFocus();
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
            etEmail.setError("Enter a valid email address");
            etEmail.requestFocus();
            return;
        }
        if (!TextUtils.isEmpty(icNumber) && !IC_PATTERN.matcher(icNumber).matches()) {
            etIcNumber.setError("Enter a valid IC number (e.g. 030214-14-0123)");
            etIcNumber.requestFocus();
            return;
        }

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean emailChanged = !newEmail.equalsIgnoreCase(currentUser.getEmail());

        if (emailChanged) {
            promptReauthAndSave(currentUser, newEmail,
                    fullName, icNumber, phone, address, gender, bloodType);
        } else {
            saveToFirestore(currentUser, newEmail,
                    fullName, icNumber, phone, address, gender, bloodType);
        }
    }

    private void promptReauthAndSave(FirebaseUser user, String newEmail,
                                     String fullName, String icNumber, String phone,
                                     String address, String gender, String bloodType) {
        EditText passwordInput = new EditText(this);
        passwordInput.setHint("Current password");
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setPadding(48, 32, 48, 16);

        new AlertDialog.Builder(this)
                .setTitle("Confirm your identity")
                .setMessage("Enter your current password to update your email.")
                .setView(passwordInput)
                .setPositiveButton("Confirm", (dialog, which) -> {
                    String password = passwordInput.getText().toString().trim();
                    if (TextUtils.isEmpty(password)) {
                        Toast.makeText(this, "Password is required to change email", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    setLoading(true);
                    AuthCredential credential = EmailAuthProvider
                            .getCredential(user.getEmail(), password);
                    user.reauthenticate(credential)
                            .addOnSuccessListener(unused ->
                                    user.verifyBeforeUpdateEmail(newEmail)
                                            .addOnSuccessListener(unused2 -> {
                                                setLoading(false);
                                                // Save new email to Firestore immediately,
                                                // but Auth email only updates after user clicks the verification link
                                                saveToFirestore(user, newEmail,
                                                        fullName, icNumber, phone,
                                                        address, gender, bloodType);
                                                Toast.makeText(this,
                                                        "Verification email sent to " + newEmail + ". Please verify to complete the change.",
                                                        Toast.LENGTH_LONG).show();
                                            })
                                            .addOnFailureListener(e -> {
                                                setLoading(false);
                                                Toast.makeText(this, "Failed to update email: "
                                                        + e.getMessage(), Toast.LENGTH_LONG).show();
                                            }))
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                Toast.makeText(this, "Wrong password. Please try again.",
                                        Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void saveToFirestore(FirebaseUser user, String email,
                                 String fullName, String icNumber, String phone,
                                 String address, String gender, String bloodType) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("fullName", fullName);
        updates.put("icNumber", icNumber);
        updates.put("phoneNumber", phone);
        updates.put("address", address);
        updates.put("email", email);
        updates.put("gender", gender);
        updates.put("bloodType", bloodType);
        if (!TextUtils.isEmpty(selectedDob)) {
            updates.put("dob", selectedDob);
        }

        db.collection(ProfileActivity.COLLECTION_USERS).document(user.getUid())
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    setLoading(false);
                    Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private String safeText(TextView tv) {
        return tv.getText() != null ? tv.getText().toString().trim() : "";
    }

    private void setLoading(boolean loading) {
        loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnSave).setEnabled(!loading);
        findViewById(R.id.btnCancel).setEnabled(!loading);
    }
}