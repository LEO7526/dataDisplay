package com.example.datadisplay;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.datadisplay.adapters.PhotoGridAdapter;
import com.example.datadisplay.models.PhotoCategory;
import com.example.datadisplay.models.PhotoData;
import com.example.datadisplay.models.PhotoFolder;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class PhotoListActivity extends AppCompatActivity implements PhotoGridAdapter.OnItemClickListener {

    private static final String TAG = "PhotoListActivity";

    private static final int CHUNK_SIZE = 50;       // load 50 images at a time
    private static final int PREFETCH_AHEAD = 10;   // prefetch 10 ahead

    private final List<String> allImages = new ArrayList<>();
    private final List<String> visibleImages = new ArrayList<>();

    private PhotoGridAdapter adapter;
    private RecyclerView recyclerView;
    private GridLayoutManager layoutManager;

    private String folderName;
    private String jsonPath;

    private boolean isLoadingChunk = false;
    private int loadedCount = 0; // how many images are currently loaded into visibleImages

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_list);

        recyclerView = findViewById(R.id.photoRecyclerView);
        layoutManager = new GridLayoutManager(this, 3);
        recyclerView.setLayoutManager(layoutManager);

        adapter = new PhotoGridAdapter(this, visibleImages, this);
        recyclerView.setAdapter(adapter);

        folderName = getIntent().getStringExtra("folder_name");
        jsonPath   = getIntent().getStringExtra("json_path");

        Log.d(TAG, "onCreate: folderName=" + folderName + ", jsonPath=" + jsonPath);

        loadImagesFromJson(jsonPath, folderName);

        // Infinite scroll listener for chunked loading
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);

                int lastVisible = layoutManager.findLastVisibleItemPosition();

                // Load next chunk if near the end
                if (!isLoadingChunk && lastVisible >= visibleImages.size() - 10) {
                    loadNextChunk();
                }

                // Prefetch ahead
                int prefetchEnd = Math.min(allImages.size() - 1, lastVisible + PREFETCH_AHEAD);
                for (int i = lastVisible + 1; i <= prefetchEnd; i++) {
                    Glide.with(PhotoListActivity.this)
                            .load(allImages.get(i))
                            .preload();
                }
            }
        });
    }

    private void loadImagesFromJson(String jsonPath, String folderName) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try (FileInputStream fis = new FileInputStream(new File(jsonPath));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(fis))) {

                PhotoData photoData = new Gson().fromJson(reader, PhotoData.class);

                if (photoData != null && photoData.categories != null) {
                    for (PhotoCategory category : photoData.categories) {
                        PhotoFolder folder = findFolderByName(category.folders, folderName);
                        if (folder != null && folder.images != null) {
                            allImages.clear();
                            allImages.addAll(folder.images);
                            Log.d(TAG, "Loaded folder: " + folder.name + " with " + folder.images.size() + " images");
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading images from JSON", e);
                runOnUiThread(() ->
                        Snackbar.make(recyclerView, "Failed to load images", Snackbar.LENGTH_LONG).show());
            }

            runOnUiThread(this::loadNextChunk);
        });
    }

    private void loadNextChunk() {
        if (loadedCount >= allImages.size()) return;

        isLoadingChunk = true;

        int end = Math.min(loadedCount + CHUNK_SIZE, allImages.size());
        List<String> nextChunk = allImages.subList(loadedCount, end);

        visibleImages.addAll(nextChunk);
        adapter.notifyItemRangeInserted(loadedCount, nextChunk.size());
        loadedCount = end;

        isLoadingChunk = false;
    }

    @Override
    public void onItemClick(int position) {
        Intent intent = new Intent(PhotoListActivity.this, ImagePagerActivity.class);
        intent.putStringArrayListExtra("image_urls", new ArrayList<>(allImages));
        intent.putExtra("start_position", position);
        startActivity(intent);
    }

    private PhotoFolder findFolderByName(List<PhotoFolder> folders, String targetName) {
        if (folders == null) return null;
        for (PhotoFolder f : folders) {
            if (targetName.equals(f.name)) return f;
            PhotoFolder found = findFolderByName(f.folders, targetName);
            if (found != null) return found;
        }
        return null;
    }
}