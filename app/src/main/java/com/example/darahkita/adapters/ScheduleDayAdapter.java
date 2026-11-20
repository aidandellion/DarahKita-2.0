package com.example.darahkita.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.darahkita.R;
import java.util.List;

public class ScheduleDayAdapter extends RecyclerView.Adapter<ScheduleDayAdapter.ScheduleViewHolder> {

    public static final int STATE_NORMAL = 0;
    public static final int STATE_PAST = 1;
    public static final int STATE_ELIGIBLE = 2;
    public static final int STATE_TODAY = 3;

    private List<Integer> days;
    private int[] states;

    public ScheduleDayAdapter(List<Integer> days, int[] states) {
        this.days = days;
        this.states = states;
    }

    @NonNull @Override
    public ScheduleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_schedule_day, parent, false);
        return new ScheduleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScheduleViewHolder holder, int position) {
        Integer day = days.get(position);
        if (day == null) {
            holder.tvDay.setText("");
            holder.tvDay.setBackground(null);
            return;
        }

        holder.tvDay.setText(String.valueOf(day));

        switch (states[position]) {
            case STATE_PAST:
                holder.tvDay.setBackgroundResource(R.drawable.bg_day_past);
                holder.tvDay.setTextColor(Color.parseColor("#B71C1C"));
                break;
            case STATE_ELIGIBLE:
                holder.tvDay.setBackgroundResource(R.drawable.bg_day_eligible);
                holder.tvDay.setTextColor(Color.parseColor("#1565C0"));
                break;
            case STATE_TODAY:
                holder.tvDay.setBackgroundResource(R.drawable.bg_day_today_outline);
                holder.tvDay.setTextColor(Color.parseColor("#1A1A2E"));
                break;
            default:
                holder.tvDay.setBackground(null);
                holder.tvDay.setTextColor(Color.parseColor("#1A1A2E"));
        }
    }

    @Override
    public int getItemCount() { return days.size(); }

    static class ScheduleViewHolder extends RecyclerView.ViewHolder {
        TextView tvDay;
        ScheduleViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDay = itemView.findViewById(R.id.tvScheduleDay);
        }
    }
}