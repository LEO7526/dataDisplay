package com.example.datadisplay;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.datadisplay.adapters.RadioCategoryAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class RadioCategoryActivity extends AppCompatActivity implements RadioCategoryAdapter.OnCategoryClickListener {

    private static final String TAG = "RadioCategoryActivity";
    private RecyclerView recyclerView;
    private JSONObject jsonData;
    private String jsonPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_radio_category);

        recyclerView = findViewById(R.id.radioCategoryRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        jsonPath = getIntent().getStringExtra("json_path");
        String json = null;

        if (jsonPath != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(jsonPath), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                json = sb.toString();
                Log.d(TAG, "Loaded JSON from file path: " + jsonPath);
            } catch (Exception e) {
                Log.e(TAG, "Error reading JSON file", e);
            }
        } else {
            json = loadMp3JsonFromCache();
        }

        if (json != null) {
            try {
                jsonData = new JSONObject(json);
                setupRecycler(jsonData);
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing mp3_data.json", e);
                Toast.makeText(this, "Error parsing mp3_data.json", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "No mp3 data found.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private String loadMp3JsonFromCache() {
        try {
            File jsonFile = new File(getCacheDir(), "mp3_data.json");
            if (!jsonFile.exists()) return null;

            FileInputStream fis = new FileInputStream(jsonFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            reader.close();
            return builder.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error loading mp3_data.json from cache", e);
            return null;
        }
    }

    private void setupRecycler(JSONObject jsonData) {
        try {
            JSONArray categories = jsonData.getJSONArray("categories");
            List<String> categoryNames = new ArrayList<>();
            Log.d(TAG, "Loading categories, total count: " + categories.length());
            
            for (int i = 0; i < categories.length(); i++) {
                String name = categories.getJSONObject(i).getString("name");
                categoryNames.add(name);
                Log.d(TAG, "Added category: " + name);
            }

            Log.d(TAG, "Total categories loaded: " + categoryNames.size());
            RadioCategoryAdapter adapter = new RadioCategoryAdapter(categoryNames, this);
            recyclerView.setAdapter(adapter);
        } catch (JSONException e) {
            Log.e(TAG, "Error loading categories", e);
            e.printStackTrace();
            Toast.makeText(this, "Error loading categories: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onCategoryClick(String categoryName) {
        Intent intent = new Intent(this, RadioFolderActivity.class);
        intent.putExtra("category", categoryName);
        intent.putExtra("json_path", jsonPath); // ✅ pass path only
        startActivity(intent);
    }
}