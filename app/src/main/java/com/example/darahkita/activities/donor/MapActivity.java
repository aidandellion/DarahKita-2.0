package com.example.darahkita.activities.donor;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.darahkita.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import com.example.darahkita.adapters.MapCentreAdapter;
import com.example.darahkita.utils.BottomNavHelper;
import com.example.darahkita.models.Booking;

import java.util.ArrayList;
import java.util.List;

// jgn tuch ni sume aiman punye - Maps & Nearby Centres
public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "MapActivity";
    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private RecyclerView rvNearbyCentres;
    private MapCentreAdapter adapter;
    private List<CentreItem> centreList = new ArrayList<>();

    private double userLat = 0, userLng = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        rvNearbyCentres = findViewById(R.id.rvNearbyCentres);
        rvNearbyCentres.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MapCentreAdapter(centreList);
        rvNearbyCentres.setAdapter(adapter);


        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
        BottomNavHelper.setup(this, BottomNavHelper.TAB_MAP);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        checkLocationPermission();
    }

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
        }
    }

    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        mMap.setMyLocationEnabled(true);

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                userLat = location.getLatitude();
                userLng = location.getLongitude();

                LatLng userLatLng = new LatLng(userLat, userLng);
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 12f));

                // now fetch centres from Firebase
                loadCentresFromFirebase();
            } else {
                Toast.makeText(this, "Can't get location, try again", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadCentresFromFirebase() {
        // first get upcoming booking to know which centre to highlight
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
        Log.d("MAPDBG", "loadCentres called, uid=" + uid);

        if (uid == null) {
            fetchAndPlotCentres(null, null);
            return;
        }

        FirebaseFirestore.getInstance().collection("booking")
                .whereEqualTo("userID", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    String bookedCentreId = null;

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        com.example.darahkita.models.Booking booking =
                                doc.toObject(com.example.darahkita.models.Booking.class);
                        if (booking != null && "Upcoming".equals(booking.getStatus())) {
                            bookedCentreId = booking.getCentreID();
                            break;
                        }
                    }

                    fetchAndPlotCentres(bookedCentreId, null);
                })
                .addOnFailureListener(e -> fetchAndPlotCentres(null, null));
    }

    private void fetchAndPlotCentres(String bookedCentreId, Object unused) {
        FirebaseFirestore.getInstance().collection("centre")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    centreList.clear();
                    mMap.clear();

                    for (QueryDocumentSnapshot centreDoc : querySnapshot) {
                        String name = centreDoc.getString("name");
                        String address = centreDoc.getString("address");
                        Double lat = centreDoc.getDouble("latitude");
                        Double lng = centreDoc.getDouble("longitude");

                        if (name == null || lat == null || lng == null) continue;

                        float[] result = new float[1];
                        Location.distanceBetween(userLat, userLng, lat, lng, result);
                        float distance = result[0];

                        centreList.add(new CentreItem(name, address, lat, lng, distance));

                        // green if booked centre, red for the rest
                        LatLng pos = new LatLng(lat, lng);
                        boolean isBooked = centreDoc.getId().equals(bookedCentreId);
                        mMap.addMarker(new MarkerOptions()
                                .position(pos)
                                .title(name)
                                .snippet(isBooked ? "📅 Your appointment is here!" : address)
                                .icon(BitmapDescriptorFactory.defaultMarker(
                                        isBooked
                                                ? BitmapDescriptorFactory.HUE_GREEN
                                                : BitmapDescriptorFactory.HUE_RED)));
                    }

                    centreList.sort((a, b) -> Float.compare(a.distance, b.distance));
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Log.e(TAG, "Firestore error: " + e.getMessage()));
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
            } else {
                Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // simple inline model - no separate file needed
    public static class CentreItem {
        public String name, address;
        public double lat, lng;
        public float distance;

        public CentreItem(String name, String address, double lat, double lng, float distance) {
            this.name = name;
            this.address = address;
            this.lat = lat;
            this.lng = lng;
            this.distance = distance;
        }
    }
}