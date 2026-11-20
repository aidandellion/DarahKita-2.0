package com.example.darahkita.activities.donor;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.darahkita.R;
import com.example.darahkita.adapters.ScheduleDayAdapter;
import com.example.darahkita.utils.BottomNavHelper;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DonationScheduleActivity extends AppCompatActivity {

    private TextView tvMonthYear;
    private RecyclerView rvScheduleDays;
    private Calendar displayedMonth = Calendar.getInstance();
    private Date nextEligibleDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_donation_schedule);

        tvMonthYear = findViewById(R.id.tvMonthYear);
        rvScheduleDays = findViewById(R.id.rvScheduleDays);

        String nextEligibleIso = getIntent().getStringExtra("nextEligibleDateIso");
        if (nextEligibleIso != null) {
            try {
                nextEligibleDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(nextEligibleIso);
            } catch (ParseException e) {
                nextEligibleDate = null;
            }
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnPrevMonth).setOnClickListener(v -> changeMonth(-1));
        findViewById(R.id.btnNextMonth).setOnClickListener(v -> changeMonth(1));

        rvScheduleDays.setLayoutManager(new GridLayoutManager(this, 7));
        rvScheduleDays.setNestedScrollingEnabled(false);

        renderMonth();

        BottomNavHelper.setup(this, BottomNavHelper.TAB_HISTORY);
    }

    private void changeMonth(int delta) {
        displayedMonth.add(Calendar.MONTH, delta);
        renderMonth();
    }

    private void renderMonth() {
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.US);
        tvMonthYear.setText(monthFormat.format(displayedMonth.getTime()));

        Calendar firstDay = (Calendar) displayedMonth.clone();
        firstDay.set(Calendar.DAY_OF_MONTH, 1);
        int leadingBlanks = firstDay.get(Calendar.DAY_OF_WEEK) - 1;
        int daysInMonth = displayedMonth.getActualMaximum(Calendar.DAY_OF_MONTH);

        List<Integer> days = new ArrayList<>();
        for (int i = 0; i < leadingBlanks; i++) days.add(null);
        for (int d = 1; d <= daysInMonth; d++) days.add(d);

        int[] states = new int[days.size()];

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0); today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0);

        for (int i = 0; i < days.size(); i++) {
            Integer day = days.get(i);
            if (day == null) continue;

            Calendar cellDate = (Calendar) displayedMonth.clone();
            cellDate.set(Calendar.DAY_OF_MONTH, day);
            cellDate.set(Calendar.HOUR_OF_DAY, 0); cellDate.set(Calendar.MINUTE, 0); cellDate.set(Calendar.SECOND, 0); cellDate.set(Calendar.MILLISECOND, 0);

            if (cellDate.getTimeInMillis() == today.getTimeInMillis()) {
                states[i] = ScheduleDayAdapter.STATE_TODAY;
            } else if (cellDate.before(today)) {
                states[i] = ScheduleDayAdapter.STATE_PAST;
            } else if (nextEligibleDate == null || !cellDate.getTime().before(nextEligibleDate)) {
                states[i] = ScheduleDayAdapter.STATE_ELIGIBLE;
            } else {
                states[i] = ScheduleDayAdapter.STATE_NORMAL;
            }
        }

        rvScheduleDays.setAdapter(new ScheduleDayAdapter(days, states));
    }
}