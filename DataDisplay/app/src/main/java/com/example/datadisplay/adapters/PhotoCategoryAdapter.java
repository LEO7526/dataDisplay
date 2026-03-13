package com.example.datadisplay.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.datadisplay.R;
import com.example.datadisplay.models.PhotoCategory;

import java.util.List;

public class PhotoCategoryAdapter extends RecyclerView.Adapter<PhotoCategoryAdapter.CategoryViewHolder> {

    private final List<PhotoCategory> categories;
    private final OnCategoryClickListener listener;

    // ✅ Pass full object instead of just name
    public interface OnCategoryClickListener {
        void onCategoryClick(PhotoCategory category);
    }

    public PhotoCategoryAdapter(List<PhotoCategory> categories, OnCategoryClickListener listener) {
        this.categories = categories;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        PhotoCategory category = categories.get(position);
        holder.textView.setText(category.name);

        holder.itemView.setOnClickListener(v -> listener.onCategoryClick(category));
    }

    @Override
    public int getItemCount() {
        return categories != null ? categories.size() : 0;
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        CategoryViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.categoryNameTextView);
        }
    }
}