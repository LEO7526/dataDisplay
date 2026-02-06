package com.example.datadisplay;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PhotoActivity extends AppCompatActivity {

    private static final int PREFETCH_AHEAD = 3;   // how many pages ahead
    private static final int PREFETCH_BEHIND = 2;  // how many pages behind

    private ArrayList<String> images;
    private RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;

    // Track which URLs are currently prefetched
    private final Set<String> prefetched = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo);

        images = getIntent().getStringArrayListExtra("images");
        if (images == null) images = new ArrayList<>();

        recyclerView = findViewById(R.id.photoRecyclerView);
        layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(new ComicScrollAdapter(images));

        if (!images.isEmpty()) {
            prefetchWindow(0);
        }

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);
                int firstVisible = layoutManager.findFirstVisibleItemPosition();
                if (firstVisible >= 0) {
                    prefetchWindow(firstVisible);
                }
            }
        });
    }

    private void prefetchWindow(int currentIndex) {
        if (images.isEmpty() || currentIndex < 0 || currentIndex >= images.size()) return;

        int start = Math.max(0, currentIndex - PREFETCH_BEHIND);
        int end   = Math.min(images.size() - 1, currentIndex + PREFETCH_AHEAD);

        // Build the new window set
        Set<String> newWindow = new HashSet<>();
        for (int i = start; i <= end; i++) {
            newWindow.add(images.get(i));
        }

        // Prefetch only new items
        for (String url : newWindow) {
            if (!prefetched.contains(url)) {
                Glide.with(this)
                        .downloadOnly()
                        .load(url)
                        .preload();
            }
        }

        // Update tracking set
        prefetched.clear();
        prefetched.addAll(newWindow);
    }

    static class ComicScrollAdapter extends RecyclerView.Adapter<ComicScrollAdapter.PageViewHolder> {
        private final List<String> images;

        ComicScrollAdapter(List<String> images) {
            this.images = images;
        }

        @NonNull
        @Override
        public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_fullscreen_page, parent, false);
            return new PageViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
            String url = images.get(position);

            Glide.with(holder.imageView.getContext())
                    .downloadOnly()
                    .load(url)
                    .into(new CustomTarget<File>() {
                        @Override
                        public void onResourceReady(@NonNull File resource, @Nullable Transition<? super File> transition) {
                            holder.imageView.setImage(ImageSource.uri(Uri.fromFile(resource)));

                            // Fit width, maintain aspect ratio
                            holder.imageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE);
                            holder.imageView.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER);

                            // Force initial scale to fit width
                            holder.imageView.post(() -> {
                                float viewWidth = holder.imageView.getWidth();
                                float sWidth = holder.imageView.getSWidth();
                                float scale = viewWidth / sWidth;
                                holder.imageView.setScaleAndCenter(scale, holder.imageView.getCenter());
                            });
                        }

                        @Override public void onLoadCleared(@Nullable Drawable placeholder) {}
                    });
        }

        @Override
        public int getItemCount() {
            return images.size();
        }

        static class PageViewHolder extends RecyclerView.ViewHolder {
            SubsamplingScaleImageView imageView;
            PageViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.fullscreenImageView);
            }
        }
    }
}