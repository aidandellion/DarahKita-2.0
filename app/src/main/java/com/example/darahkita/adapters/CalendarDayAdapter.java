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

public class CalendarDayAdapter extends RecyclerView.Adapter<CalendarDayAdapter.DayViewHolder> {

    public interface OnDaySelected { void onDaySelected(int day, int position); }

    private List<Integer> days;
    private boolean[] disabled;
    private int selectedPosition = -1;
    private OnDaySelected listener;

    public CalendarDayAdapter(List<Integer> days, boolean[] disabled, OnDaySelected listener) {
        this.days = days;
        this.disabled = disabled;
        this.listener = listener;
    }

    public void setSelectedPosition(int position) {
        int previous = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(previous);
        notifyItemChanged(selectedPosition);
    }

    @NonNull @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_day, parent, false);
        return new DayViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        Integer day = days.get(position);
        if (day == null) {
            holder.tvDay.setText("");
            holder.tvDay.setBackground(null);
            holder.tvDay.setOnClickListener(null);
            holder.tvDay.setClickable(false);
            return;
        }

        holder.tvDay.setText(String.valueOf(day));
        boolean isDisabledDay = disabled[position];
        boolean isSelected = position == selectedPosition;

        if (isSelected) {
            holder.tvDay.setBackgroundResource(R.drawable.bg_day_selected);
            holder.tvDay.setTextColor(Color.parseColor("#8E2A4A"));
        } else {
            holder.tvDay.setBackground(null);
            holder.tvDay.setTextColor(isDisabledDay ? Color.parseColor("#CCCCCC") : Color.parseColor("#1A1A2E"));
        }

        if (isDisabledDay) {
            holder.tvDay.setOnClickListener(null);
            holder.tvDay.setClickable(false);
        } else {
            holder.tvDay.setClickable(true);
            holder.tvDay.setOnClickListener(v -> listener.onDaySelected(day, position));
        }
    }

    @Override
    public int getItemCount() { return days.size(); }

    static class DayViewHolder extends RecyclerView.ViewHolder {
        TextView tvDay;
        DayViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDay = itemView.findViewById(R.id.tvDay);
        }
    }
}