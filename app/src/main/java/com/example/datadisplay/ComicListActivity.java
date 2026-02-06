package com.example.datadisplay;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.datadisplay.adapters.ComicGridAdapter;
import com.example.datadisplay.models.PhotoCategory;
import com.example.datadisplay.models.PhotoData;
import com.example.datadisplay.models.PhotoFolder;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class ComicListActivity extends AppCompatActivity implements ComicGridAdapter.OnItemClickListener {

    private static final String TAG = "ComicListActivity";

    private final List<String> imageUrls = new ArrayList<>();
    private ComicGridAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comic_list);

        RecyclerView recyclerView = findViewById(R.id.photoRecyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        recyclerView.setHasFixedSize(true);

        adapter = new ComicGridAdapter(this, imageUrls, this);
        recyclerView.setAdapter(adapter);

        String folderName = getIntent().getStringExtra("folder_name");
        String jsonPath   = getIntent().getStringExtra("json_path");

        Log.d(TAG, "onCreate: folderName=" + folderName + ", jsonPath=" + jsonPath);

        // Load JSON + prefetch in background
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                if (jsonPath != null && folderName != null) {
                    PhotoData comicData = loadComicData(jsonPath);

                    if (comicData != null && comicData.categories != null) {
                        for (PhotoCategory category : comicData.categories) {
                            PhotoFolder folder = findFolderByName(category.folders, folderName);
                            if (folder != null && folder.images != null) {
                                synchronized (imageUrls) {
                                    imageUrls.clear();
                                    imageUrls.addAll(folder.images);
                                }
                                Log.d(TAG, "Loaded folder: " + folder.name + " with " + folder.images.size() + " images");

                                // 🔥 Prefetch all images into Glide cache
                                prefetchImages(folder.images);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading images from JSON", e);
            }

            runOnUiThread(() -> {
                Log.d(TAG, "Total images: " + imageUrls.size());
                adapter.notifyDataSetChanged();
            });
        });
    }

    @Override
    public void onItemClick(String imageUrl, int position) {
        Intent intent = new Intent(this, PhotoActivity.class);
        intent.putStringArrayListExtra("images", new ArrayList<>(imageUrls));
        intent.putExtra("position", position);
        startActivity(intent);
    }

    // ✅ Recursive lookup for nested folders
    private PhotoFolder findFolderByName(List<PhotoFolder> folders, String targetName) {
        if (folders == null) return null;
        for (PhotoFolder f : folders) {
            if (targetName.equals(f.name)) return f;
            PhotoFolder found = findFolderByName(f.folders, targetName);
            if (found != null) return found;
        }
        return null;
    }

    // ✅ Prefetch images into Glide disk cache
    private void prefetchImages(List<String> urls) {
        for (String url : urls) {
            Glide.with(this)
                    .downloadOnly()
                    .load(url)
                    .preload();  // enqueue into Glide’s cache
        }
    }

    // ✅ Utility: safely load JSON into PhotoData
    private PhotoData loadComicData(String path) {
        File file = new File(path);
        if (!file.exists()) {
            Log.w(TAG, "JSON file not found: " + path);
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis))) {
            return new Gson().fromJson(reader, PhotoData.class);
        } catch (Exception e) {
            Log.e(TAG, "Error reading JSON file: " + path, e);
            return null;
        }
    }
}