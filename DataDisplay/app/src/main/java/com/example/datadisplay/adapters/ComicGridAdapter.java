package com.example.datadisplay.adapters;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.example.datadisplay.R;

import java.io.File;
import java.util.List;

public class ComicGridAdapter extends RecyclerView.Adapter<ComicGridAdapter.ComicViewHolder> {

    // ✅ Pass the actual image URL instead of just position
    public interface OnItemClickListener {
        void onItemClick(String imageUrl, int position);
    }

    private final Context context;
    private final List<String> imageUrls;
    private final OnItemClickListener listener;

    public ComicGridAdapter(Context context, List<String> imageUrls, OnItemClickListener listener) {
        this.context = context;
        this.imageUrls = imageUrls;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ComicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_comic_page, parent, false);
        return new ComicViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ComicViewHolder holder, int position) {
        String imageUrl = imageUrls.get(position);

        Glide.with(context)
                .load(imageUrl)
                .placeholder(R.drawable.outline_error_24) // optional
                .error(R.drawable.outline_error_24)       // optional
                .into(holder.imageView);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onItemClick(imageUrl, pos);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return imageUrls != null ? imageUrls.size() : 0;
    }

    static class ComicViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        ComicViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.comicImageView);
        }

    }
}