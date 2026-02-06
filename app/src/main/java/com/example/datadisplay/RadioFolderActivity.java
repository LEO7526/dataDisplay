package com.example.datadisplay;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.datadisplay.adapters.RadioFolderAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class RadioFolderActivity extends AppCompatActivity implements RadioFolderAdapter.OnFolderClickListener {

    private List<String> folderNames;
    private String categoryName;
    private String jsonPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_radio_folder);

        RecyclerView recyclerView = findViewById(R.id.radioFolderRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        categoryName = getIntent().getStringExtra("category");
        jsonPath = getIntent().getStringExtra("json_path");

        folderNames = new ArrayList<>();

        if (jsonPath != null) {
            try {
                String json = new String(Files.readAllBytes(new File(jsonPath).toPath()), StandardCharsets.UTF_8);
                JSONObject jsonData = new JSONObject(json);
                JSONArray categories = jsonData.getJSONArray("categories");

                Log.d("RadioFolderActivity", "Looking for category: " + categoryName);
                
                for (int i = 0; i < categories.length(); i++) {
                    JSONObject cat = categories.getJSONObject(i);
                    if (cat.getString("name").equals(categoryName)) {
                        JSONArray folders = cat.getJSONArray("folders");
                        Log.d("RadioFolderActivity", "Found " + folders.length() + " folders in category");
                        
                        for (int j = 0; j < folders.length(); j++) {
                            String folderName = folders.getJSONObject(j).getString("name");
                            folderNames.add(folderName);
                            Log.d("RadioFolderActivity", "Added folder: " + folderName);
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e("RadioFolderActivity", "Error loading folders", e);
                e.printStackTrace();
                Toast.makeText(this, "Error loading folders: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }

        RadioFolderAdapter adapter = new RadioFolderAdapter(folderNames, this);
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onFolderClick(String folderName) {
        Intent intent = new Intent(this, RadioListActivity.class);
        intent.putExtra("category", categoryName);
        intent.putExtra("folder", folderName);
        intent.putExtra("json_path", jsonPath); // ✅ pass path only
        startActivity(intent);
    }
}