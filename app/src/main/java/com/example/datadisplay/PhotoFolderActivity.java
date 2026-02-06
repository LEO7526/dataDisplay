package com.example.datadisplay;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.datadisplay.adapters.PhotoFolderAdapter;
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

public class PhotoFolderActivity extends AppCompatActivity implements PhotoFolderAdapter.OnFolderClickListener {

    private static final String TAG = "PhotoFolderActivity";

    private List<PhotoFolder> folderList = new ArrayList<>();
    private String categoryName;
    private String jsonPath;
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_folder);

        recyclerView = findViewById(R.id.folderRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        categoryName = getIntent().getStringExtra("category_name");
        jsonPath = getIntent().getStringExtra("json_path");
        String folderName = getIntent().getStringExtra("folder_name");

        loadFolders(jsonPath, categoryName, folderName);
    }

    private void loadFolders(String jsonPath, String categoryName, String folderName) {
        PhotoData cached = PhotoDataCache.getInstance().getData();
        if (cached != null) {
            setupRecycler(cached, categoryName, folderName);
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            PhotoData photoData = null;
            try (FileInputStream fis = new FileInputStream(new File(jsonPath));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(fis))) {
                photoData = new Gson().fromJson(reader, PhotoData.class);
                PhotoDataCache.getInstance().setData(photoData);
            } catch (Exception e) {
                Log.e(TAG, "Error loading folders", e);
                runOnUiThread(() ->
                        Snackbar.make(recyclerView, "Failed to load folders", Snackbar.LENGTH_LONG).show());
            }

            PhotoData finalData = photoData;
            runOnUiThread(() -> setupRecycler(finalData, categoryName, folderName));
        });
    }

    private void setupRecycler(PhotoData photoData, String categoryName, String folderName) {
        folderList.clear();
        if (photoData != null && photoData.categories != null) {
            for (PhotoCategory category : photoData.categories) {
                if (category.name.equals(categoryName)) {
                    if (folderName == null) {
                        folderList.addAll(category.folders != null ? category.folders : new ArrayList<>());
                    } else {
                        PhotoFolder current = findFolderByName(category.folders, folderName);
                        if (current != null && current.folders != null) {
                            folderList.addAll(current.folders);
                            Log.d(TAG, "Loaded subfolder: " + current.name + " with " + folderList.size() + " children");
                        } else {
                            Snackbar.make(recyclerView, "Folder not found: " + folderName, Snackbar.LENGTH_LONG).show();
                        }
                    }
                    break;
                }
            }
        }

        PhotoFolderAdapter adapter = new PhotoFolderAdapter(folderList, this);
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onFolderClick(String folderName) {
        PhotoData photoData = PhotoDataCache.getInstance().getData();
        if (photoData == null) {
            Snackbar.make(recyclerView, "Data not loaded", Snackbar.LENGTH_LONG).show();
            return;
        }

        for (PhotoCategory category : photoData.categories) {
            if (categoryName.equals(category.name)) {
                PhotoFolder clicked = findFolderByName(category.folders, folderName);
                if (clicked == null) {
                    Snackbar.make(recyclerView, "Folder not found: " + folderName, Snackbar.LENGTH_LONG).show();
                    return;
                }

                if (clicked.folders != null && !clicked.folders.isEmpty()) {
                    Intent intent = new Intent(this, PhotoFolderActivity.class);
                    intent.putExtra("category_name", categoryName);
                    intent.putExtra("folder_name", clicked.name);
                    intent.putExtra("json_path", jsonPath);
                    startActivity(intent);
                } else if (clicked.images != null && !clicked.images.isEmpty()) {
                    Intent intent = new Intent(this, PhotoListActivity.class);
                    intent.putExtra("category_name", categoryName);
                    intent.putExtra("folder_name", clicked.name);
                    intent.putExtra("json_path", jsonPath);
                    startActivity(intent);
                } else {
                    Snackbar.make(recyclerView, "This folder is empty", Snackbar.LENGTH_LONG).show();
                }
                return;
            }
        }
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