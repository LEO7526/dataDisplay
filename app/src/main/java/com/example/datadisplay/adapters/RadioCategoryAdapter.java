package com.example.datadisplay.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.datadisplay.R;

import java.util.List;

public class RadioCategoryAdapter extends RecyclerView.Adapter<RadioCategoryAdapter.ViewHolder> {

    private final List<String> categoryNames;
    private final OnCategoryClickListener listener;

    // ✅ Public interface so it can be implemented in your Activity
    public interface OnCategoryClickListener {
        void onCategoryClick(String categoryName);
    }

    public RadioCategoryAdapter(List<String> categoryNames, OnCategoryClickListener listener) {
        this.categoryNames = categoryNames;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_radio_category, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String name = categoryNames.get(position);
        holder.textView.setText(name);
        holder.itemView.setOnClickListener(v -> listener.onCategoryClick(name));
    }

    @Override
    public int getItemCount() {
        return categoryNames.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.radioCategoryText);
        }
    }
}