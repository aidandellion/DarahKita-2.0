package com.example.darahkita.activities.staff;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.example.darahkita.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.QueueViewHolder> {

    private final Context context;
    private final List<DocumentSnapshot> bookingList;
    private final Map<String, String> fullnameMap;
    private final Map<String, String> bloodTypeMap;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public QueueAdapter(Context context, List<DocumentSnapshot> bookingList,
                        Map<String, String> fullnameMap, Map<String, String> bloodTypeMap) {
        this.context = context;
        this.bookingList = bookingList;
        this.fullnameMap = fullnameMap;
        this.bloodTypeMap = bloodTypeMap;
    }

    @NonNull
    @Override
    public QueueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_queue, parent, false);
        return new QueueViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QueueViewHolder holder, int position) {
        DocumentSnapshot doc = bookingList.get(position);
        String userId = doc.getString("userID");

        String status = doc.getString("status");
        if (status == null || status.trim().isEmpty()) status = "Pending";

        String name = fullnameMap.getOrDefault(userId, "Unknown Donor");
        holder.tvDonorName.setText(name);
        holder.tvBlood.setText(bloodTypeMap.getOrDefault(userId, "N/A"));
        holder.tvStatus.setText(status.toUpperCase());
        holder.tvInitial.setText(initialOf(name));

        String timeslot = doc.getString("timeslot");
        holder.tvTimeslot.setText(timeslot != null ? timeslot : "-");

        if (status.equalsIgnoreCase("Eligible")) {
            // Donor already passed eligibility check -> staff can now mark donation complete
            holder.btnStatus.setText("Complete");
            holder.btnStatus.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#2E9E6D")));
            holder.btnStatus.setTextColor(Color.WHITE);
            holder.btnStatus.setOnClickListener(v -> confirmCompleted(doc, holder));
        } else {
            // Default / Pending state -> staff decides eligibility
            holder.btnStatus.setText("Update");
            holder.btnStatus.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            holder.btnStatus.setTextColor(Color.parseColor("#D81B60"));
            holder.btnStatus.setOnClickListener(v -> showStatusOptions(doc, holder));
        }
    }

    private String initialOf(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        return name.trim().substring(0, 1).toUpperCase();
    }

    private void showStatusOptions(DocumentSnapshot doc, QueueViewHolder holder) {
        String[] options = {"Eligible", "Ineligible", "Absent"};
        new AlertDialog.Builder(context)
                .setTitle("Update donor status")
                .setItems(options, (dialog, which) -> {
                    switch (options[which]) {
                        case "Eligible":
                            markEligible(doc, holder);
                            break;
                        case "Ineligible":
                            finalizeStatus(doc, holder, "Ineligible",
                                    "This donor is not eligible to donate (ineligible).");
                            break;
                        case "Absent":
                            finalizeStatus(doc, holder, "Absent",
                                    "This donor was absent for the appointment (absent).");
                            break;
                    }
                })
                .show();
    }

    private void markEligible(DocumentSnapshot doc, QueueViewHolder holder) {
        // Stays in "booking" collection, just status flips -> remains visible in Today Queue
        doc.getReference().update("status", "Eligible")
                .addOnSuccessListener(unused -> {
                    int position = holder.getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        notifyItemChanged(position);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(context, "Failed to update: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void confirmCompleted(DocumentSnapshot doc, QueueViewHolder holder) {
        new AlertDialog.Builder(context)
                .setTitle("Confirm")
                .setMessage("Mark this donor's donation process as complete?")
                .setPositiveButton("Yes", (dialog, which) ->
                        finalizeStatus(doc, holder, "Completed",
                                "The donation process has been successfully recorded (completed)."))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Used for terminal states: Ineligible, Absent, Completed.
     * Copies the full booking doc into "donation", overwrites final status,
     * deletes it from "booking", shows a popup, then removes it from the
     * Today Queue list in-place (no refetch needed).
     */
    private void finalizeStatus(DocumentSnapshot doc, QueueViewHolder holder, String finalStatus, String popupMessage) {
        WriteBatch batch = db.batch();
        DocumentReference bookingRef = doc.getReference();
        DocumentReference donationRef = db.collection("donation").document();

        String userId = doc.getString("userID");

        Map<String, Object> data = doc.getData();
        if (data == null) data = new HashMap<>();
        data.put("status", finalStatus);
        data.put("bookingID", doc.getId());
        data.put("recordedAt", Timestamp.now());

        // ← ADD THIS: get bloodType from map
        String bloodType = bloodTypeMap.getOrDefault(userId, "N/A");
        data.put("bloodType", bloodType);

        batch.set(donationRef, data);
        batch.delete(bookingRef);

        batch.commit().addOnSuccessListener(unused -> {
            new AlertDialog.Builder(context)
                    .setTitle("Status updated")
                    .setMessage(popupMessage)
                    .setPositiveButton("OK", null)
                    .show();

            int position = holder.getAdapterPosition();
            if (position != RecyclerView.NO_POSITION && position < bookingList.size()) {
                bookingList.remove(position);
                notifyItemRemoved(position);
            }
        }).addOnFailureListener(e ->
                Toast.makeText(context, "Failed to save to donation: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    @Override
    public int getItemCount() {
        return bookingList.size();
    }

    public static class QueueViewHolder extends RecyclerView.ViewHolder {
        TextView tvDonorName, tvBlood, tvStatus, tvInitial, tvTimeslot;
        MaterialButton btnStatus;

        public QueueViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDonorName = itemView.findViewById(R.id.tvDonorName);
            tvBlood = itemView.findViewById(R.id.tvBlood);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvInitial = itemView.findViewById(R.id.tvInitial);
            tvTimeslot = itemView.findViewById(R.id.tvTimeslot);
            btnStatus = itemView.findViewById(R.id.btnStatus);
        }
    }
}