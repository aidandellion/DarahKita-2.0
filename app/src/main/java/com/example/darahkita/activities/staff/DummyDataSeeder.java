package com.example.darahkita.activities.staff;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.util.HashMap;
import java.util.Map;

/**
 * DEV/TEST ONLY. Jangan panggil dalam production build.
 * - Users created ONCE (fixed IDs: dummy_tiramisu, dummy_waffle, etc.)
 * - New BOOKING created every time seedTodayQueue() is called
 * - Called from LoginActivity (role = staff only)
 */
public class DummyDataSeeder {
    public static void seedBloodStock(Context context) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String[] bloodTypes = {"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"};
        String centreID = "C001";

        for (String bloodType : bloodTypes) {
            String docId = centreID + "_" + bloodType.replace("+", "Plus").replace("-", "Minus");

            Map<String, Object> data = new HashMap<>();
            data.put("centreID", centreID);
            data.put("bloodType", bloodType);
            data.put("outStock", false);
            data.put("lastUpdated", Timestamp.now());

            db.collection("bloodStock").document(docId).set(data);
        }

        Log.d("DummyDataSeeder", "Blood stock seeded for " + centreID);
    }

    public static void seedTodayQueue(Context context) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Fixed user IDs — same ID every time = no duplicate users
        String[] dummyUserIDs = {
                "dummy_tiramisu",
                "dummy_waffle",
                "dummy_croissant",
                "dummy_macaron",
                "dummy_eclair"
        };

        // name, email, ic, phone, bloodType, dob, address, gender
        String[][] donors = {
                {"Tiramisu Binti Tart", "tiramisu@example.com", "990101-10-1111", "0111111111", "A+", "1/1/1999", "1 Jalan Kek, Shah Alam", "Female"},
                {"Waffle Bin Crepe", "waffle@example.com", "950505-08-2222", "0122222222", "O-", "5/5/1995", "2 Jalan Wafel, Petaling Jaya", "Male"},
                {"Croissant Binti Éclair", "croissant@example.com", "920808-14-3333", "0133333333", "B+", "8/8/1992", "3 Lorong Pastri, Subang", "Female"},
                {"Macaron Bin Mochi", "macaron@example.com", "001234-56-4444", "0144444444", "AB+", "12/3/2000", "4 Persiaran Kuih, Klang", "Male"},
                {"Eclair Binti Brownie", "eclair@example.com", "880707-10-5555", "0155555555", "AB-", "7/7/1988", "5 Jalan Coklat, Ampang", "Female"}
        };

        // null = Pending, "Eligible" = already eligible (to test complete button)
        String[] statuses = {null, "Eligible", null, null, "Eligible"};

        for (int i = 0; i < dummyUserIDs.length; i++) {
            final int idx = i;
            final String userID = dummyUserIDs[i];

            // Check if user already exists (fixed ID)
            db.collection("users").document(userID).get()
                    .addOnSuccessListener(userDoc -> {
                        if (!userDoc.exists()) {
                            // First time — create user
                            Map<String, Object> userData = new HashMap<>();
                            userData.put("fullName", donors[idx][0]);
                            userData.put("email", donors[idx][1]);
                            userData.put("icNumber", donors[idx][2]);
                            userData.put("phoneNumber", donors[idx][3]);
                            userData.put("bloodType", donors[idx][4]);
                            userData.put("dob", donors[idx][5]);
                            userData.put("address", donors[idx][6]);
                            userData.put("gender", donors[idx][7]);
                            userData.put("role", "donor");

                            db.collection("users").document(userID).set(userData)
                                    .addOnSuccessListener(unused -> {
                                        Log.d("DummyDataSeeder", "User created: " + donors[idx][0]);
                                        createBooking(db, userID, idx, statuses);
                                    })
                                    .addOnFailureListener(e ->
                                            Log.e("DummyDataSeeder", "Error creating user: " + e.getMessage()));
                        } else {
                            // User exists — just create new booking
                            Log.d("DummyDataSeeder", "User exists, creating booking: " + donors[idx][0]);
                            createBooking(db, userID, idx, statuses);
                        }
                    })
                    .addOnFailureListener(e ->
                            Log.e("DummyDataSeeder", "Error checking user: " + e.getMessage()));
        }

        Toast.makeText(context, "Preparing today's queue...", Toast.LENGTH_SHORT).show();
    }

    private static void createBooking(FirebaseFirestore db, String userID, int idx, String[] statuses) {
        Map<String, Object> bookingData = new HashMap<>();
        bookingData.put("userID", userID);
        bookingData.put("centreID", "C001");
        bookingData.put("bookingDate", Timestamp.now());
        bookingData.put("checkedIn", true);
        bookingData.put("checkinLocation", new GeoPoint(3.1390, 101.6869));
        bookingData.put("timeslot", "10:00 AM");
        bookingData.put("status", statuses[idx] != null ? statuses[idx] : "Pending");

        db.collection("booking").add(bookingData)
                .addOnSuccessListener(ref ->
                        Log.d("DummyDataSeeder", "Booking created: " + ref.getId()))
                .addOnFailureListener(e ->
                        Log.e("DummyDataSeeder", "Booking failed: " + e.getMessage()));
    }
}