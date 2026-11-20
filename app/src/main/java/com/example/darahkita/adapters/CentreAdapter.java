package com.example.darahkita.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.darahkita.R;
import com.example.darahkita.models.Centre;
import java.util.List;

public class CentreAdapter extends RecyclerView.Adapter<CentreAdapter.CentreViewHolder> {

    public interface OnChooseTimeClick {
        void onChooseTime(Centre centre);
    }

    private List<Centre> centres;
    private OnChooseTimeClick listener;

    public CentreAdapter(List<Centre> centres, OnChooseTimeClick listener) {
        this.centres = centres;
        this.listener = listener;
    }

    @NonNull @Override
    public CentreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_centre_select, parent, false);
        return new CentreViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CentreViewHolder holder, int position) {
        Centre centre = centres.get(position);
        holder.tvCentreName.setText(centre.getName());
        holder.tvCentreAddress.setText(centre.getAddress());
        holder.btnChooseTime.setOnClickListener(v -> listener.onChooseTime(centre));
    }

    @Override
    public int getItemCount() { return centres.size(); }

    static class CentreViewHolder extends RecyclerView.ViewHolder {
        TextView tvCentreName, tvCentreAddress;
        View btnChooseTime;
        CentreViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCentreName = itemView.findViewById(R.id.tvCentreName);
            tvCentreAddress = itemView.findViewById(R.id.tvCentreAddress);
            btnChooseTime = itemView.findViewById(R.id.btnChooseTime);
        }
    }
}