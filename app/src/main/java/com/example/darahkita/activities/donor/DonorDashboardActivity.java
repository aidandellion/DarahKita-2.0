package com.example.darahkita.activities.donor;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

//dont touch
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import java.util.HashMap;
import java.util.Map;

public class DonorDashboardActivity extends AppCompatActivity {

    private TextView tvWelcome, tvBloodType;
    private TextView tvAppointmentCentre, tvAppointmentTime, tvAppointmentLocation;
    private Button btnNavigateToAppointment;
    private View tvUpcomingHeader;
    private String uid;

    private FusedLocationProviderClient fusedLocationClient;
    private Booking currentUpcomingBooking;

    private LinearLayout llAnnouncementsContainer; //this for the announcment
    private LinearLayout llLowStockContainer, cardNextAppointment, centresContainer, rowAppointmentDetails;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_donor_dashboard);


        tvWelcome = findViewById(R.id.tvWelcome);
        tvBloodType = findViewById(R.id.tvBloodType);
        tvUpcomingHeader = findViewById(R.id.tvUpcomingHeader);
        cardNextAppointment = findViewById(R.id.cardNextAppointment);
        rowAppointmentDetails = findViewById(R.id.rowAppointmentDetails);
        tvAppointmentCentre = findViewById(R.id.tvAppointmentCentre);
        tvAppointmentTime = findViewById(R.id.tvAppointmentTime);
        tvAppointmentLocation = findViewById(R.id.tvAppointmentLocation);
        centresContainer = findViewById(R.id.centresContainer);
        btnNavigateToAppointment = findViewById(R.id.btnNavigateToAppointment);
        llAnnouncementsContainer = findViewById(R.id.llAnnouncementsContainer);
        llLowStockContainer = findViewById(R.id.llLowStockContainer);

        tvWelcome.setText("Donor");

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        loadBloodType();
        loadEventAnnouncements();
        loadUpcomingBooking();
        loadLowStockAlerts();

        BottomNavHelper.setup(this, BottomNavHelper.TAB_HOME);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNearbyCentres();
    }

    private void loadBloodType() {
        if (uid == null) return;
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String name = doc.getString("name");
                    if (name == null || name.isEmpty()) name = doc.getString("fullName");
                    tvWelcome.setText(name != null && !name.isEmpty() ? name : "Donor");

                    String bloodType = doc.getString("bloodType");
                    tvBloodType.setText(bloodType != null ? bloodType : "—");
                });
    }

    private void loadLowStockAlerts() {
        if (llLowStockContainer == null) return;

        FirebaseFirestore.getInstance().collection("bloodStock")
                .whereEqualTo("outStock", true)
                .addSnapshotListener((snapshots, e) -> {
                    llLowStockContainer.removeAllViews();
                    if (e != null || snapshots == null || snapshots.isEmpty()) return;

                    // Group blood types by centreID
                    Map<String, List<String>> byCentre = new HashMap<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        String bloodType = doc.getString("bloodType");
                        String centreID = doc.getString("centreID");
                        if (bloodType == null || centreID == null) continue;

                        byCentre.computeIfAbsent(centreID, k -> new ArrayList<>()).add(bloodType);
                    }

                    for (Map.Entry<String, List<String>> entry : byCentre.entrySet()) {
                        String centreID = entry.getKey();
                        List<String> bloodTypes = entry.getValue();

                        FirebaseFirestore.getInstance().collection("centre")
                                .document(centreID)
                                .get()
                                .addOnSuccessListener(centreDoc -> {
                                    String centreName = centreDoc.getString("name");
                                    String display = centreName != null ? centreName : centreID;

                                    View item = LayoutInflater.from(this)
                                            .inflate(R.layout.item_low_stock_banner, llLowStockContainer, false);
                                    TextView tvMsg = item.findViewById(R.id.tvLowStockMessage);
                                    tvMsg.setText(buildBoldedMessage(bloodTypes, display));
                                    llLowStockContainer.addView(item);
                                });
                    }
                });
    }

    private SpannableString buildBoldedMessage(List<String> bloodTypes, String centreName) {
        String typesJoined = TextUtils.join(", ", bloodTypes);
        String full = "Type " + typesJoined + " " +
                (bloodTypes.size() > 1 ? "are" : "is") +
                " urgently required at " + centreName + ".";

        SpannableString spannable = new SpannableString(full);

        int searchStart = "Type ".length();
        for (String type : bloodTypes) {
            int start = full.indexOf(type, searchStart);
            if (start == -1) continue;
            int end = start + type.length();
            spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            searchStart = end;
        }

        return spannable;
    }

    private void loadEventAnnouncements() {
        if (llAnnouncementsContainer == null) return;

        FirebaseFirestore.getInstance().collection("event")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    llAnnouncementsContainer.removeAllViews();

                    if (e != null || snapshots == null || snapshots.isEmpty()) return;

                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snapshots) {
                        String name = doc.getString("eventName");
                        String desc = doc.getString("description");
                        String loc  = doc.getString("location");
                        String date = doc.getString("eventDate");

                        View itemView = LayoutInflater.from(this)
                                .inflate(R.layout.item_dashboard_broadcast, llAnnouncementsContainer, false);

                        TextView tvTitle = itemView.findViewById(R.id.tvBroadcastTitle);
                        TextView tvInfo  = itemView.findViewById(R.id.tvBroadcastInfo);
                        View btnDelete   = itemView.findViewById(R.id.btnDeleteBroadcast);
                        if (btnDelete != null) btnDelete.setVisibility(View.GONE);

                        tvTitle.setText(name != null ? name : "No Name");
                        tvInfo.setText((desc != null ? desc : "") +
                                "\n📍 Location: " + (loc != null ? loc : "") +
                                "\n📅 Date: " + (date != null ? date : ""));

                        llAnnouncementsContainer.addView(itemView);
                    }
                });
    }

    private void loadUpcomingBooking() {
        if (uid == null) {
            showNoUpcomingBooking();
            return;
        }

        FirebaseFirestore.getInstance().collection("booking")
                .whereEqualTo("userID", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Booking earliestUpcoming = null;

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Booking booking = doc.toObject(Booking.class);
                        if (booking == null || !"Upcoming".equals(booking.getStatus())) continue;

                        if (earliestUpcoming == null ||
                                booking.getBookingDate().compareTo(earliestUpcoming.getBookingDate()) < 0) {
                            earliestUpcoming = booking;
                        }
                    }

                    if (earliestUpcoming != null) {
                        currentUpcomingBooking = earliestUpcoming;
                        fetchCentreForUpcomingBooking(earliestUpcoming);
                    } else {
                        showNoUpcomingBooking();
                    }
                })
                .addOnFailureListener(e -> showNoUpcomingBooking());
    }

    private void fetchCentreForUpcomingBooking(Booking booking) {
        FirebaseFirestore.getInstance().collection("centre")
                .document(booking.getCentreID())
                .get()
                .addOnSuccessListener(doc -> {
                    Centre centre = doc.toObject(Centre.class);
                    if (centre != null) {
                        centre.setCentreId(doc.getId());
                        showUpcomingBooking(booking, centre);
                    } else {
                        showNoUpcomingBooking();
                    }
                })
                .addOnFailureListener(e -> showNoUpcomingBooking());
    }

    private void showUpcomingBooking(Booking booking, Centre centre) {
        tvUpcomingHeader.setVisibility(View.VISIBLE);
        cardNextAppointment.setVisibility(View.VISIBLE);
        rowAppointmentDetails.setVisibility(View.VISIBLE);

        tvAppointmentCentre.setText(centre.getName());
        tvAppointmentTime.setText(formatDisplayDate(booking.getBookingDate()) + " • " + booking.getTimeslot());
        tvAppointmentLocation.setText(centre.getAddress());

        btnNavigateToAppointment.setVisibility(View.VISIBLE);
        btnNavigateToAppointment.setOnClickListener(v ->
                openNavigation(centre.getLatitude(), centre.getLongitude(), centre.getName()));
    }

    private void showNoUpcomingBooking() {
        tvUpcomingHeader.setVisibility(View.VISIBLE);
        cardNextAppointment.setVisibility(View.VISIBLE);
        rowAppointmentDetails.setVisibility(View.GONE);
        tvAppointmentCentre.setText("None — no upcoming appointment");
        btnNavigateToAppointment.setVisibility(View.GONE);
    }

    private void openNavigation(double lat, double lng, String name) {
        try {
            PackageManager pm = getPackageManager();
            pm.getPackageInfo("com.waze", PackageManager.GET_ACTIVITIES);
            Uri wazeUri = Uri.parse("waze://?ll=" + lat + "," + lng + "&navigate=yes");
            startActivity(new Intent(Intent.ACTION_VIEW, wazeUri));
        } catch (PackageManager.NameNotFoundException e) {
            Uri mapsUri = Uri.parse("geo:" + lat + "," + lng + "?q=" + lat + "," + lng + "(" + name + ")");
            Intent mapsIntent = new Intent(Intent.ACTION_VIEW, mapsUri);
            mapsIntent.setPackage("com.google.android.apps.maps");
            startActivity(mapsIntent);
        }
    }

    private void loadNearbyCentres() {
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            loadCentresWithoutLocation();
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null) {
                loadCentresWithoutLocation();
                return;
            }

            FirebaseFirestore.getInstance().collection("centre")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        List<CentreWithDistance> list = new ArrayList<>();

                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            Centre centre = doc.toObject(Centre.class);
                            centre.setCentreId(doc.getId());

                            float[] result = new float[1];
                            Location.distanceBetween(
                                    location.getLatitude(), location.getLongitude(),
                                    centre.getLatitude(), centre.getLongitude(),
                                    result);

                            list.add(new CentreWithDistance(centre, result[0]));
                        }

                        list.sort((a, b) -> Float.compare(a.distance, b.distance));

                        centresContainer.removeAllViews();
                        int count = Math.min(3, list.size());
                        for (int i = 0; i < count; i++) {
                            addCentreCard(list.get(i).centre.getName(),
                                    list.get(i).centre.getAddress());
                        }
                    });
        });
    }

    private void loadCentresWithoutLocation() {
        FirebaseFirestore.getInstance().collection("centre")
                .limit(3)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    centresContainer.removeAllViews();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Centre centre = doc.toObject(Centre.class);
                        if (centre != null)
                            addCentreCard(centre.getName(), centre.getAddress());
                    }
                });
    }

    private void addCentreCard(String name, String address) {
        View item = LayoutInflater.from(this)
                .inflate(R.layout.item_centre_home, centresContainer, false);
        TextView tvName = item.findViewById(R.id.tvCentreName);
        TextView tvAddress = item.findViewById(R.id.tvCentreAddress);
        tvName.setText(name);
        tvAddress.setText(address);
        centresContainer.addView(item);
    }

    private String formatDisplayDate(Date date) {
        SimpleDateFormat output = new SimpleDateFormat("d MMM yyyy", Locale.US);
        return output.format(date);
    }

    private static class CentreWithDistance {
        Centre centre;
        float distance;

        CentreWithDistance(Centre centre, float distance) {
            this.centre = centre;
            this.distance = distance;
        }
    }
}