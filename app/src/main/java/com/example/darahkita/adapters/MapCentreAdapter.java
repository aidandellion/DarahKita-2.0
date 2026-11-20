package com.example.darahkita.adapters;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.darahkita.R;
import com.example.darahkita.activities.donor.MapActivity;

import java.util.List;

public class MapCentreAdapter extends RecyclerView.Adapter<MapCentreAdapter.ViewHolder> {

    private List<MapActivity.CentreItem> centreList;
    private Context context;

    public MapCentreAdapter(List<MapActivity.CentreItem> centreList) {
        this.centreList = centreList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_map_centre, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MapActivity.CentreItem centre = centreList.get(position);

        holder.tvCentreName.setText(centre.name);

        // format distance nicely
        float dist = centre.distance;
        if (dist >= 1000) {
            holder.tvDistance.setText(String.format("%.1f km away", dist / 1000));
        } else {
            holder.tvDistance.setText(String.format("%.0f m away", dist));
        }

        holder.btnNavigate.setOnClickListener(v -> openNavigation(centre));
    }

    private void openNavigation(MapActivity.CentreItem centre) {
        double lat = centre.lat;
        double lng = centre.lng;
        String name = centre.name;

        // try Waze first, fallback to Google Maps
        try {
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo("com.waze", PackageManager.GET_ACTIVITIES);

            Uri wazeUri = Uri.parse("waze://?ll=" + lat + "," + lng + "&navigate=yes");
            Intent wazeIntent = new Intent(Intent.ACTION_VIEW, wazeUri);
            context.startActivity(wazeIntent);

        } catch (PackageManager.NameNotFoundException e) {
            Uri mapsUri = Uri.parse(
                    "geo:" + lat + "," + lng + "?q=" + lat + "," + lng + "(" + name + ")"
            );
            Intent mapsIntent = new Intent(Intent.ACTION_VIEW, mapsUri);
            mapsIntent.setPackage("com.google.android.apps.maps");
            context.startActivity(mapsIntent);
        }
    }

    @Override
    public int getItemCount() {
        return centreList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCentreName, tvDistance;
        Button btnNavigate;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCentreName = itemView.findViewById(R.id.tvCentreName);
            tvDistance = itemView.findViewById(R.id.tvDistance);
            btnNavigate = itemView.findViewById(R.id.btnNavigate);
        }
    }
}