package com.example.darahkita.activities.auth;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.darahkita.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvRegister, tvForgotPassword;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);

        SpannableString span = new SpannableString("Don't have an account? Register here");
        span.setSpan(new ForegroundColorSpan(Color.parseColor("#B71C1C")), 23, 36, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new StyleSpan(Typeface.BOLD), 23, 36, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvRegister.setText(span);

        progressBar = findViewById(R.id.progressBar);

        btnLogin.setOnClickListener(v -> loginUser());
        tvRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
        tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) { etEmail.setError("Email is required"); return; }
        if (TextUtils.isEmpty(password)) { etPassword.setError("Password is required"); return; }

        progressBar.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    btnLogin.setEnabled(true);

                    if (task.isSuccessful()) {
                        Log.d(TAG, "Auth sign-in successful, uid=" + mAuth.getCurrentUser().getUid());
                        Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                        goToDashboard();
                    } else {
                        Log.e(TAG, "Auth sign-in failed", task.getException());
                        Toast.makeText(LoginActivity.this,
                                "Login failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void goToDashboard() {
        String uid = mAuth.getCurrentUser().getUid();

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Log.e(TAG, "No user document found for uid: " + uid);
                        Toast.makeText(this, "Account data not found. Contact support.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    String role = doc.getString("role");
                    String name = doc.getString("fullName");
                    Log.d(TAG, "Fetched role=[" + role + "] name=[" + name + "]");

                    if (role == null) {
                        Log.e(TAG, "Role field is null for uid: " + uid);
                        Toast.makeText(this, "User role missing. Contact support.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if ("staff".equals(role.trim())) {
                        com.example.darahkita.activities.staff.DummyDataSeeder.seedBloodStock(this);
                        com.example.darahkita.activities.staff.DummyDataSeeder.seedTodayQueue(this);

                        Intent intent = new Intent(this, com.example.darahkita.activities.staff.StaffDashboardActivity.class);
                        intent.putExtra("userName", name);
                        startActivity(intent);
                    } else {
                        Intent intent = new Intent(this, com.example.darahkita.activities.donor.DonorDashboardActivity.class);
                        intent.putExtra("userName", name);
                        startActivity(intent);
                    }
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch user document for uid: " + uid, e);
                    Toast.makeText(this, "Could not load account: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Shows a dialog with an email input (pre-filled from the login form if already typed)
     * and, on confirm, checks whether that email is registered before sending a reset link.
     */
    private void showForgotPasswordDialog() {
        final EditText input = new EditText(this);
        input.setHint("example@mail.com");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        String prefill = etEmail.getText().toString().trim();
        if (!TextUtils.isEmpty(prefill)) {
            input.setText(prefill);
            input.setSelection(prefill.length());
        }

        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(this)
                .setTitle("Reset Password")
                .setMessage("Enter your account email. We'll send you a link to reset your password.")
                .setView(input)
                .setPositiveButton("Send", (dialog, which) -> {
                    String email = input.getText().toString().trim();
                    if (TextUtils.isEmpty(email)) {
                        Toast.makeText(this, "Please enter your email.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    checkEmailThenSendReset(email);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Looks up the "users" collection by email field. Only sends the Firebase
     * reset email if a matching account is found; otherwise informs the user
     * that the email is not registered.
     */
    private void checkEmailThenSendReset(String email) {
        FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("email", email.trim())
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        Toast.makeText(this,
                                "This email is not registered with DarahKita.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        sendResetEmail(email);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check email in Firestore", e);
                    Toast.makeText(this,
                            "Could not verify email right now: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void sendResetEmail(String email) {
        mAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Reset link sent to " + email, Toast.LENGTH_LONG).show())
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send reset email", e);
                    Toast.makeText(this, "Failed to send reset email: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}