package com.example.datadisplay.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.datadisplay.R;

import java.util.List;

public class RadioFolderAdapter extends RecyclerView.Adapter<RadioFolderAdapter.ViewHolder> {

    private final List<String> folderNames;
    private final OnFolderClickListener listener;

    public interface OnFolderClickListener {
        void onFolderClick(String folderName);
    }

    public RadioFolderAdapter(List<String> folderNames, OnFolderClickListener listener) {
        this.folderNames = folderNames;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_radio_folder, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String name = folderNames.get(position);
        holder.textView.setText(name);
        holder.itemView.setOnClickListener(v -> listener.onFolderClick(name));
    }

    @Override
    public int getItemCount() {
        return folderNames.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.radioFolderText);
        }
    }
}