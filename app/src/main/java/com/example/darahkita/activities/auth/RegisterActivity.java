package com.example.darahkita.activities.auth;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.darahkita.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

public class RegisterActivity extends AppCompatActivity {

    private EditText etFullName, etIcNumber, etEmail, etPhone, etAddress, etDob, etPassword;
    private Spinner spinnerBloodType, spinnerGender;
    private Button btnRegister;
    private TextView tvLogin;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etFullName = findViewById(R.id.etFullName);
        etIcNumber = findViewById(R.id.etIcNumber);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etDob = findViewById(R.id.etDob);
        etPassword = findViewById(R.id.etPassword);
        etAddress = findViewById(R.id.etAddress);
        spinnerBloodType = findViewById(R.id.spinnerBloodType);
        spinnerGender = findViewById(R.id.spinnerGender);
        btnRegister = findViewById(R.id.btnRegister);
        tvLogin = findViewById(R.id.tvLogin);
        SpannableString span2 = new SpannableString("Already have an account? Login");
        span2.setSpan(new ForegroundColorSpan(Color.parseColor("#B71C1C")), 25, 30, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span2.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 25, 30, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvLogin.setText(span2);
        progressBar = findViewById(R.id.progressBar);

        // Blood type spinner
        String[] bloodTypes = {"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, bloodTypes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBloodType.setAdapter(adapter);

        // Gender spinner
        String[] genders = {"Male", "Female"};
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, genders);
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGender.setAdapter(genderAdapter);

        // Date picker
        etDob.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, day) -> {
                etDob.setText((month + 1) + "/" + day + "/" + year);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnRegister.setOnClickListener(v -> registerUser());
        tvLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void registerUser() {
        String name = etFullName.getText().toString().trim();
        String ic = etIcNumber.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String dob = etDob.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String bloodType = spinnerBloodType.getSelectedItem().toString();
        String gender = spinnerGender.getSelectedItem().toString();
        String address = etAddress.getText().toString().trim();

        if (TextUtils.isEmpty(name)) { etFullName.setError("Required"); return; }
        if (TextUtils.isEmpty(ic)) { etIcNumber.setError("Required"); return; }
        if (TextUtils.isEmpty(email)) { etEmail.setError("Required"); return; }
        if (TextUtils.isEmpty(phone)) { etPhone.setError("Required"); return; }
        if (TextUtils.isEmpty(address)) { etAddress.setError("Required"); return; }
        if (TextUtils.isEmpty(dob)) { etDob.setError("Required"); return; }
        if (TextUtils.isEmpty(password)) { etPassword.setError("Required"); return; }
        if (password.length() < 6) { etPassword.setError("Min 6 characters"); return; }

        progressBar.setVisibility(View.VISIBLE);
        btnRegister.setEnabled(false);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String uid = mAuth.getCurrentUser().getUid();

                        // Save user data to Firestore
                        Map<String, Object> user = new HashMap<>();
                        user.put("fullName", name);
                        user.put("icNumber", ic);
                        user.put("email", email);
                        user.put("phoneNumber", phone);
                        user.put("address", address);
                        user.put("dob", dob);
                        user.put("bloodType", bloodType);
                        user.put("gender", gender);
                        user.put("role", "donor"); // default role

                        db.collection("users").document(uid).set(user)
                                .addOnSuccessListener(unused -> {
                                    progressBar.setVisibility(View.GONE);
                                    btnRegister.setEnabled(true);
                                    Toast.makeText(this, "Registration successful!", Toast.LENGTH_LONG).show();
                                    startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    progressBar.setVisibility(View.GONE);
                                    btnRegister.setEnabled(true);
                                    Toast.makeText(this, "Failed to save data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                    } else {
                        progressBar.setVisibility(View.GONE);
                        btnRegister.setEnabled(true);
                        Toast.makeText(this, "Registration failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}