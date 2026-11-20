package com.example.darahkita.activities.donor;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.darahkita.R;
import com.example.darahkita.models.Booking;
import com.example.darahkita.models.Centre;
import com.example.darahkita.utils.BottomNavHelper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Calendar;
import java.util.Date;

public class BookingHubActivity extends AppCompatActivity {

    private static final int RECOVERY_DAYS = 56;
    private static final int CHECK_IN_LOCATION_REQUEST = 200;
    private static final float CHECK_IN_RADIUS_METRES = 200f;

    private FusedLocationProviderClient fusedLocationClient;
    private Booking currentUpcomingBooking;
    private Centre currentCentre;
    private String currentBookingKey;
    private LinearLayout cardCheckIn;
    private TextView tvCheckInStatus;

    private Location cachedLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_hub);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        cardCheckIn = findViewById(R.id.cardCheckIn);
        tvCheckInStatus = findViewById(R.id.tvCheckInStatus);

        requestNotificationPermission();

        findViewById(R.id.cardBookSlot).setOnClickListener(v -> checkEligibilityAndProceed());
        cardCheckIn.setOnClickListener(v -> handleCheckIn());

        BottomNavHelper.setup(this, BottomNavHelper.TAB_BOOKING);
    }

    @Override
    protected void onResume() {
        super.onResume();
        cachedLocation = null;
        loadUpcomingBookingThenCheckLocation();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void loadUpcomingBookingThenCheckLocation() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseFirestore.getInstance().collection("booking")
                .whereEqualTo("userID", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Booking earliest = null;
                    String earliestId = null;

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Booking booking = doc.toObject(Booking.class);
                        if (booking == null || !"Upcoming".equals(booking.getStatus())) continue;

                        if (earliest == null || booking.getBookingDate().compareTo(earliest.getBookingDate()) < 0) {
                            earliest = booking;
                            earliestId = doc.getId();
                        }
                    }

                    currentUpcomingBooking = earliest;
                    currentBookingKey = earliestId;

                    if (currentUpcomingBooking != null) {
                        fetchCentreThenLocation();
                    } else {
                        setCardGrey();
                    }
                })
                .addOnFailureListener(e -> setCardGrey());
    }

    private void fetchCentreThenLocation() {
        FirebaseFirestore.getInstance().collection("centre")
                .document(currentUpcomingBooking.getCentreID())
                .get()
                .addOnSuccessListener(doc -> {
                    currentCentre = doc.toObject(Centre.class);
                    if (currentCentre != null) currentCentre.setCentreId(doc.getId());
                    fetchLocationThenUpdateCard();
                })
                .addOnFailureListener(e -> setCardGrey());
    }

    private void fetchLocationThenUpdateCard() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            setCardGrey();
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            cachedLocation = location;
            updateCardFromCachedLocation();
        });
    }

    private void updateCardFromCachedLocation() {
        if (cachedLocation == null) {
            setCardGrey();
            return;
        }

        if (currentCentre == null) {
            setCardRed();
            tvCheckInStatus.setText("Centre location unavailable.");
            return;
        }

        double centreLat = currentCentre.getLatitude();
        double centreLng = currentCentre.getLongitude();
        if (centreLat == 0.0 && centreLng == 0.0) {
            setCardRed();
            tvCheckInStatus.setText("Centre location unavailable.");
            return;
        }

        float[] results = new float[1];
        Location.distanceBetween(
                cachedLocation.getLatitude(), cachedLocation.getLongitude(),
                centreLat, centreLng,
                results
        );

        if (results[0] <= CHECK_IN_RADIUS_METRES) {
            setCardGreen();
        } else {
            setCardRed();
        }
    }

    private void setCardGreen() {
        cardCheckIn.setBackgroundResource(R.drawable.bg_card_green);
        tvCheckInStatus.setText("You're at the centre! Tap to check in.");
        tvCheckInStatus.setVisibility(View.VISIBLE);
    }

    private void setCardRed() {
        cardCheckIn.setBackgroundResource(R.drawable.bg_card_red);
        tvCheckInStatus.setText("You're not at the centre yet.");
        tvCheckInStatus.setVisibility(View.VISIBLE);
    }

    private void setCardGrey() {
        cardCheckIn.setBackgroundResource(R.drawable.bg_card_dark);
        tvCheckInStatus.setVisibility(View.GONE);
    }

    private void handleCheckIn() {
        if (currentUpcomingBooking == null) {
            new AlertDialog.Builder(this)
                    .setTitle("No Upcoming Booking")
                    .setMessage("You don't have any upcoming appointments to check in to.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    CHECK_IN_LOCATION_REQUEST);
            return;
        }

        performLocationCheckIn();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CHECK_IN_LOCATION_REQUEST) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                performLocationCheckIn();
            } else {
                Toast.makeText(this, "Location permission needed for check-in",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void performLocationCheckIn() {
        if (currentUpcomingBooking == null || currentCentre == null) return;

        double centreLat = currentCentre.getLatitude();
        double centreLng = currentCentre.getLongitude();
        if (centreLat == 0.0 && centreLng == 0.0) {
            Toast.makeText(this, "Centre location data missing. Contact support.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (cachedLocation == null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) return;

            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                cachedLocation = location;
                doCheckInWithCachedLocation();
            });
        } else {
            doCheckInWithCachedLocation();
        }
    }

    private void doCheckInWithCachedLocation() {
        if (cachedLocation == null) {
            Toast.makeText(this, "Unable to get location. Try moving outdoors.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        float[] results = new float[1];
        Location.distanceBetween(
                cachedLocation.getLatitude(), cachedLocation.getLongitude(),
                currentCentre.getLatitude(),
                currentCentre.getLongitude(),
                results
        );

        if (results[0] <= CHECK_IN_RADIUS_METRES) {
            markAsCheckedIn();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Not at Centre")
                    .setMessage("You don't seem to be at "
                            + currentCentre.getName()
                            + " yet. Please arrive at the centre to check in.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    // NOTE: schema now stores checkedIn (boolean) + checkinLocation (GeoPoint) instead of
    // changing status to "CheckedIn". status stays "Upcoming" until staff marks it Completed.
    private void markAsCheckedIn() {
        GeoPoint checkinPoint = new GeoPoint(cachedLocation.getLatitude(), cachedLocation.getLongitude());

        FirebaseFirestore.getInstance().collection("booking")
                .document(currentBookingKey)
                .update("checkedIn", true, "checkinLocation", checkinPoint)
                .addOnSuccessListener(unused -> {
                    setCardGrey();
                    new AlertDialog.Builder(this)
                            .setTitle("Checked In!")
                            .setMessage("You have successfully checked in at "
                                    + currentCentre.getName()
                                    + ". Thank you for donating!")
                            .setCancelable(false)
                            .setPositiveButton("OK", null)
                            .show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Check-in failed. Try again.",
                                Toast.LENGTH_SHORT).show());
    }

    private void checkEligibilityAndProceed() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, EligibilityScreeningActivity.class));
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore.getInstance().collection("booking")
                .whereEqualTo("userID", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Date latestCompletedDate = null;
                    boolean hasUpcomingBooking = false;

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Booking booking = doc.toObject(Booking.class);
                        if (booking == null) continue;

                        if ("Upcoming".equals(booking.getStatus())) hasUpcomingBooking = true;

                        if ("Completed".equals(booking.getStatus())) {
                            if (latestCompletedDate == null ||
                                    booking.getBookingDate().after(latestCompletedDate)) {
                                latestCompletedDate = booking.getBookingDate();
                            }
                        }
                    }

                    if (hasUpcomingBooking) { showAlreadyHasUpcomingDialog(); return; }
                    if (latestCompletedDate == null) { proceedToScreening(); return; }
                    evaluateRecoveryWindow(latestCompletedDate);
                })
                .addOnFailureListener(e -> proceedToScreening());
    }

    private void evaluateRecoveryWindow(Date lastDonation) {
        Calendar nextEligible = Calendar.getInstance();
        nextEligible.setTime(lastDonation);
        nextEligible.add(Calendar.DATE, RECOVERY_DAYS);
        zeroOutTime(nextEligible);

        Calendar today = Calendar.getInstance();
        zeroOutTime(today);

        if (today.before(nextEligible)) {
            java.text.SimpleDateFormat display = new java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.US);
            showNotEligibleYetDialog(display.format(nextEligible.getTime()));
        } else {
            proceedToScreening();
        }
    }

    private void zeroOutTime(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private void showNotEligibleYetDialog(String nextEligibleDate) {
        new AlertDialog.Builder(this)
                .setTitle("Not Eligible Yet")
                .setMessage("Based on your last completed donation, your body needs until "
                        + nextEligibleDate
                        + " to fully recover (56-day minimum). This is for your health and safety.")
                .setCancelable(false)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showAlreadyHasUpcomingDialog() {
        new AlertDialog.Builder(this)
                .setTitle("You Already Have an Upcoming Appointment")
                .setMessage("Check your Dashboard or History to view it. If you need to change your appointment, please contact the centre directly.")
                .setCancelable(false)
                .setPositiveButton("OK", null)
                .show();
    }

    private void proceedToScreening() {
        startActivity(new Intent(this, EligibilityScreeningActivity.class));
    }
}