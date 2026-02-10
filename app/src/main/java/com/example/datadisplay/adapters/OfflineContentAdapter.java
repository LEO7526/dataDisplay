package com.example.datadisplay.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.datadisplay.R;
import com.example.datadisplay.managers.OfflineResourceManager;
import com.example.datadisplay.managers.OfflineResourceManager.ResourceType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying offline content in grid
 */
public class OfflineContentAdapter extends RecyclerView.Adapter<OfflineContentAdapter.ViewHolder> {

    private final Context context;
    private List<OfflineItem> items = new ArrayList<>();
    private OnItemClickListener onItemClickListener;
    private OnDeleteClickListener onDeleteClickListener;

    public OfflineContentAdapter(Context context) {
        this.context = context;
    }

    public void setItems(List<OfflineItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.onDeleteClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_offline_content, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OfflineItem item = items.get(position);
        
        // Set filename
        holder.filename.setText(item.filename);
        
        // Set file size
        holder.fileSize.setText(OfflineResourceManager.formatFileSize(item.fileSize));
        
        // Set priority indicator
        holder.priorityIndicator.setVisibility(item.isPriority ? View.VISIBLE : View.GONE);
        
        // Load thumbnail
        File file = new File(item.localPath);
        if (item.type == ResourceType.AUDIO) {
            // Show audio icon
            holder.thumbnail.setImageResource(R.drawable.ic_mp3);
        } else {
            // Show image thumbnail
            Glide.with(context)
                .load(file)
                .placeholder(R.drawable.ic_photos)
                .centerCrop()
                .into(holder.thumbnail);
        }
        
        // Click listeners
        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(item.url, item.localPath);
            }
        });
        
        holder.deleteButton.setOnClickListener(v -> {
            if (onDeleteClickListener != null) {
                onDeleteClickListener.onDeleteClick(item.url, item.localPath);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        TextView filename;
        TextView fileSize;
        ImageView priorityIndicator;
        ImageButton deleteButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            filename = itemView.findViewById(R.id.filename);
            fileSize = itemView.findViewById(R.id.fileSize);
            priorityIndicator = itemView.findViewById(R.id.priorityIndicator);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }

    public static class OfflineItem {
        public String url;
        public String localPath;
        public String filename;
        public long fileSize;
        public boolean isPriority;
        public ResourceType type;
    }

    public interface OnItemClickListener {
        void onItemClick(String url, String localPath);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(String url, String localPath);
    }
}
