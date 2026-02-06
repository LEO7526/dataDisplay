package com.example.datadisplay;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

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
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ComicCategoryActivity extends AppCompatActivity {

    private static final String TAG = "ComicCategoryActivity";

    private RecyclerView recyclerView;
    private final String jsonUrl =
            "https://raw.githubusercontent.com/leowong7527-spec/Android_data_storage/main/comic_data.json";
    private File cacheFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_category); // consider renaming layout

        recyclerView = findViewById(R.id.categoryRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Use external files dir for consistency with HomeActivity
        cacheFile = new File(getExternalFilesDir(null), "comic_data.json");

        // ✅ 1. Load from cache immediately if available
        if (cacheFile.exists()) {
            try {
                String cachedJson = readFileAsString(cacheFile);
                setupRecyclerWithJson(cachedJson);
                Log.d(TAG, "Loaded categories from cache");
            } catch (Exception e) {
                Log.e(TAG, "Failed to read cache", e);
            }
        }

        // ✅ 2. Always refresh in background
        fetchCategories();
    }

    private void fetchCategories() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(jsonUrl).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                Log.e(TAG, "Network fetch failed", e);
                runOnUiThread(() -> Snackbar.make(
                        recyclerView,
                        "Failed to refresh comic categories",
                        Snackbar.LENGTH_LONG).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws java.io.IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    Log.d(TAG, "Fetched fresh JSON, length=" + json.length());

                    // Save JSON to cache
                    try (FileWriter writer = new FileWriter(cacheFile)) {
                        writer.write(json);
                        Log.d(TAG, "Cache updated at " + cacheFile.getAbsolutePath());
                    }

                    // Update UI with fresh data
                    runOnUiThread(() -> setupRecyclerWithJson(json));
                } else {
                    Log.e(TAG, "Response not successful, code=" + response.code());
                }
            }
        });
    }

    private void setupRecyclerWithJson(String json) {
        try {
            Gson gson = new Gson();
            PhotoData comicData = gson.fromJson(json, PhotoData.class);

            List<PhotoCategory> categories = new ArrayList<>();
            if (comicData != null && comicData.categories != null) {
                categories.addAll(comicData.categories);
            }

            PhotoCategoryAdapter adapter = new PhotoCategoryAdapter(categories, category -> {
                Log.d(TAG, "Category clicked: " + category.name);
                Intent intent = new Intent(ComicCategoryActivity.this, ComicFolderActivity.class);
                intent.putExtra("category_name", category.name);
                intent.putExtra("json_path", cacheFile.getAbsolutePath());
                startActivity(intent);
            });

            recyclerView.setAdapter(adapter);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing JSON", e);
        }
    }

    // ✅ Utility: stream file safely
    private String readFileAsString(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}