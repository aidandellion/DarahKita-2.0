package com.example.darahkita.adapters;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.darahkita.R;
import com.example.darahkita.models.Booking;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {
    private List<Booking> bookings;
    private final Map<String, String> centreNameCache = new HashMap<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM yyyy", Locale.US);

    public HistoryAdapter(List<Booking> bookings) { this.bookings = bookings; }

    @NonNull @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_donation_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        Booking booking = bookings.get(position);

        holder.tvCentreName.setText("...");
        resolveCentreName(booking.getCentreID(), holder.tvCentreName);

        String dateStr = booking.getBookingDate() != null ? dateFormat.format(booking.getBookingDate()) : "";
        holder.tvDateType.setText(dateStr + " • " + booking.getTimeslot());

        // aiman punye - status pill warna jgn kacau
        String status = booking.getStatus() != null ? booking.getStatus() : "";
        holder.tvStatus.setText(status);
        int bgColor, textColor;
        switch (status) {
            case "Completed":
                bgColor = Color.parseColor("#E6F4EA"); textColor = Color.parseColor("#2E7D32"); break;
            case "Ineligible":
                bgColor = Color.parseColor("#FFF3E0"); textColor = Color.parseColor("#E8850C"); break;
            case "Absent":
                bgColor = Color.parseColor("#FDECEA"); textColor = Color.parseColor("#C62828"); break;
            default:
                bgColor = Color.parseColor("#EEEEEE"); textColor = Color.parseColor("#616161"); break;
        }
        holder.tvStatus.setBackgroundTintList(ColorStateList.valueOf(bgColor));
        holder.tvStatus.setTextColor(textColor);

        // aiman punye - whole card ikut status warna jgn kacau
        holder.cardRoot.setBackgroundResource(R.drawable.bg_card_history_gray); // shape asas, kita tint atas ni
        holder.cardRoot.setBackgroundTintList(ColorStateList.valueOf(bgColor));
        holder.ivHistoryIcon.setImageResource(R.drawable.ic_history_red);
    }

    private void resolveCentreName(String centreId, TextView target) {
        if (centreId == null) {
            target.setText("Unknown Centre");
            return;
        }

        String cached = centreNameCache.get(centreId);
        if (cached != null) {
            target.setText(cached);
            return;
        }

        FirebaseFirestore.getInstance().collection("centre")
                .document(centreId)
                .get()
                .addOnSuccessListener(doc -> {
                    String name = doc.getString("name");
                    if (name == null) name = "Unknown Centre";
                    centreNameCache.put(centreId, name);
                    if (centreId.equals(target.getTag())) {
                        target.setText(name);
                    }
                });
        target.setTag(centreId);
    }

    @Override
    public int getItemCount() { return bookings.size(); }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        LinearLayout cardRoot;
        ImageView ivHistoryIcon;
        TextView tvCentreName, tvDateType, tvStatus;
        HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.cardRoot);
            ivHistoryIcon = itemView.findViewById(R.id.ivHistoryIcon);
            tvCentreName = itemView.findViewById(R.id.tvCentreName);
            tvDateType = itemView.findViewById(R.id.tvDateType);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }
    }
}