package com.example.darahkita.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.darahkita.R;
import java.util.List;

public class TimeSlotAdapter extends RecyclerView.Adapter<TimeSlotAdapter.SlotViewHolder> {

    public interface OnSlotSelected { void onSlotSelected(String slot); }

    private List<String> slots;
    private int selectedPosition = -1;
    private OnSlotSelected listener;

    public TimeSlotAdapter(List<String> slots, OnSlotSelected listener) {
        this.slots = slots;
        this.listener = listener;
    }

    @NonNull @Override
    public SlotViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_time_slot, parent, false);
        return new SlotViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SlotViewHolder holder, int position) {
        String slot = slots.get(position);
        holder.tvSlot.setText(slot);

        boolean isSelected = position == selectedPosition;
        holder.tvSlot.setBackgroundResource(isSelected ? R.drawable.bg_slot_box_selected : R.drawable.bg_slot_box);
        holder.tvSlot.setTextColor(isSelected ? 0xFFFFFFFF : 0xFF1A1A2E);

        holder.tvSlot.setOnClickListener(v -> {
            int previous = selectedPosition;
            selectedPosition = position;
            notifyItemChanged(previous);
            notifyItemChanged(selectedPosition);
            listener.onSlotSelected(slot);
        });
    }

    @Override
    public int getItemCount() { return slots.size(); }

    static class SlotViewHolder extends RecyclerView.ViewHolder {
        TextView tvSlot;
        SlotViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSlot = itemView.findViewById(R.id.tvSlot);
        }
    }
}