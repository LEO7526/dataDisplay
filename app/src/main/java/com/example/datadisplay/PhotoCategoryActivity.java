package com.example.datadisplay;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.datadisplay.adapters.PhotoCategoryAdapter;
import com.example.datadisplay.models.PhotoCategory;
import com.example.datadisplay.models.PhotoData;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class PhotoCategoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private File cacheFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_category);

        recyclerView = findViewById(R.id.categoryRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        String jsonPath = getIntent().getStringExtra("json_path");
        if (jsonPath != null) {
            cacheFile = new File(jsonPath);
            loadCategories();
        } else {
            Snackbar.make(recyclerView, "No photo data available", Snackbar.LENGTH_LONG).show();
        }
    }

    private void loadCategories() {
        PhotoData cached = PhotoDataCache.getInstance().getData();
        if (cached != null) {
            setupRecycler(cached);
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            PhotoData photoData = null;
            try (FileInputStream fis = new FileInputStream(cacheFile);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(fis))) {
                photoData = new Gson().fromJson(reader, PhotoData.class);
                PhotoDataCache.getInstance().setData(photoData);
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Snackbar.make(recyclerView, "Failed to load cached photo data", Snackbar.LENGTH_LONG).show());
            }

            PhotoData finalData = photoData;
            runOnUiThread(() -> setupRecycler(finalData));
        });
    }

    private void setupRecycler(PhotoData photoData) {
        List<PhotoCategory> categories = new ArrayList<>();
        if (photoData != null && photoData.categories != null) {
            categories.addAll(photoData.categories);
        } else {
            Snackbar.make(recyclerView, "No categories found", Snackbar.LENGTH_LONG).show();
        }

        PhotoCategoryAdapter adapter = new PhotoCategoryAdapter(categories, category -> {
            Intent intent = new Intent(PhotoCategoryActivity.this, PhotoFolderActivity.class);
            intent.putExtra("category_name", category.name);
            intent.putExtra("json_path", cacheFile.getAbsolutePath());
            startActivity(intent);
        });

        recyclerView.setAdapter(adapter);
    }
}