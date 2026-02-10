package com.example.datadisplay.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.datadisplay.R;
import com.example.datadisplay.models.PhotoFolder;

import java.util.List;

public class PhotoFolderAdapter extends RecyclerView.Adapter<PhotoFolderAdapter.FolderViewHolder> {

    private final List<PhotoFolder> folders;
    private final OnFolderClickListener listener;
    private OnFolderLongClickListener longClickListener;

    // ✅ Change interface to pass only the folder name (lightweight)
    public interface OnFolderClickListener {
        void onFolderClick(String folderName);
    }
    
    public interface OnFolderLongClickListener {
        boolean onFolderLongClick(String folderName);
    }

    public PhotoFolderAdapter(List<PhotoFolder> folders, OnFolderClickListener listener) {
        this.folders = folders;
        this.listener = listener;
    }
    
    public void setOnLongClickListener(OnFolderLongClickListener listener) {
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_folder, parent, false);
        return new FolderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) {
        PhotoFolder folder = folders.get(position);
        holder.textView.setText(folder.name);

        // ✅ Pass only the folder name
        holder.itemView.setOnClickListener(v -> listener.onFolderClick(folder.name));
        
        // Add long click listener for batch download
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                return longClickListener.onFolderLongClick(folder.name);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return folders != null ? folders.size() : 0;
    }

    static class FolderViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        FolderViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.folderNameTextView);
        }
    }
}