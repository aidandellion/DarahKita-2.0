package com.example.darahkita.activities.donor;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.darahkita.R;
import com.example.darahkita.adapters.CentreAdapter;
import com.example.darahkita.models.Centre;
import com.example.darahkita.utils.BottomNavHelper;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SelectLocationActivity extends AppCompatActivity {

    private RecyclerView rvCentres;
    private EditText etSearch;
    private List<Centre> allCentres = new ArrayList<>();
    private List<Centre> filteredCentres = new ArrayList<>();
    private CentreAdapter adapter;

    private Map<String, Boolean> eligibilityAnswers;
    private boolean eligible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_location);

        rvCentres = findViewById(R.id.rvCentres);
        etSearch = findViewById(R.id.etSearch);

        Object answersExtra = getIntent().getSerializableExtra("eligibilityAnswers");
        if (answersExtra instanceof Map) {
            //noinspection unchecked
            eligibilityAnswers = (Map<String, Boolean>) answersExtra;
        } else {
            eligibilityAnswers = new HashMap<>();
        }
        eligible = getIntent().getBooleanExtra("eligible", true);

        rvCentres.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CentreAdapter(filteredCentres, this::onChooseTime);
        rvCentres.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterCentres(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadCentres();

        BottomNavHelper.setup(this, BottomNavHelper.TAB_BOOKING);
    }

    private void loadCentres() {
        FirebaseFirestore.getInstance().collection("centre")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    allCentres.clear();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Centre centre = doc.toObject(Centre.class);
                        if (centre != null) {
                            centre.setCentreId(doc.getId());
                            allCentres.add(centre);
                        }
                    }
                    filteredCentres.clear();
                    filteredCentres.addAll(allCentres);
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    // aiman punye - firestore fetch failed
                });
    }
    private void filterCentres(String query) {
        filteredCentres.clear();
        String lower = query.toLowerCase(Locale.getDefault());
        for (Centre centre : allCentres) {
            if (centre.getName() != null && centre.getName().toLowerCase(Locale.getDefault()).contains(lower)) {
                filteredCentres.add(centre);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void onChooseTime(Centre centre) {
        Intent intent = new Intent(this, SelectDateTimeActivity.class);
        intent.putExtra("centreId", centre.getCentreId());
        intent.putExtra("centreName", centre.getName());
        intent.putExtra("centreAddress", centre.getAddress());
        intent.putExtra("centreLat", centre.getLatitude());
        intent.putExtra("centreLng", centre.getLongitude());
        intent.putExtra("eligibilityAnswers", (Serializable) eligibilityAnswers);
        intent.putExtra("eligible", eligible);
        startActivity(intent);
    }
}