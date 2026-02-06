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
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ComicFolderActivity extends AppCompatActivity implements PhotoFolderAdapter.OnFolderClickListener {

    private static final String TAG = "ComicFolderActivity";

    private List<PhotoFolder> folderList;
    private String jsonPath;
    private String categoryName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_folder);

        Log.d(TAG, "onCreate: ComicFolderActivity started");

        RecyclerView recyclerView = findViewById(R.id.folderRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        String folderName = getIntent().getStringExtra("folder_name");
        categoryName = getIntent().getStringExtra("category_name");
        jsonPath = getIntent().getStringExtra("json_path");

        Log.d(TAG, "onCreate: folderName=" + folderName + ", categoryName=" + categoryName + ", jsonPath=" + jsonPath);

        folderList = new ArrayList<>();

        PhotoData comicData = loadComicData(jsonPath);
        if (comicData != null && comicData.categories != null) {
            for (PhotoCategory category : comicData.categories) {
                if (category.name.equals(categoryName) && category.folders != null) {
                    if (folderName == null) {
                        // First entry point → load top-level folders
                        folderList = category.folders;
                        Log.d(TAG, "Loaded top-level category: " + category.name + " with " + folderList.size() + " folders");
                    } else {
                        // Find the matching subfolder
                        for (PhotoFolder folder : category.folders) {
                            if (folder.name.equals(folderName)) {
                                folderList = folder.folders != null ? folder.folders : new ArrayList<>();
                                Log.d(TAG, "Loaded subfolder: " + folder.name + " with " + folderList.size() + " children");
                                break;
                            }
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
        Log.d(TAG, "Clicked folder: " + folderName);

        PhotoData comicData = loadComicData(jsonPath);
        if (comicData != null && comicData.categories != null) {
            for (PhotoCategory category : comicData.categories) {
                if (category.name.equals(categoryName) && category.folders != null) {
                    for (PhotoFolder folder : category.folders) {
                        if (folder.name.equals(folderName)) {
                            if (folder.folders != null && !folder.folders.isEmpty()) {
                                // ✅ Has subfolders → open ComicFolderActivity again
                                Intent intent = new Intent(this, ComicFolderActivity.class);
                                intent.putExtra("category_name", categoryName);
                                intent.putExtra("folder_name", folder.name);
                                intent.putExtra("json_path", jsonPath);
                                startActivity(intent);
                            } else {
                                // ✅ No subfolders → open ComicListActivity
                                Intent intent = new Intent(this, ComicListActivity.class);
                                intent.putExtra("category_name", categoryName);
                                intent.putExtra("folder_name", folder.name);
                                intent.putExtra("json_path", jsonPath);
                                startActivity(intent);
                            }
                            return;
                        }
                    }
                }
            }
        }
    }

    // ✅ Utility: safely load JSON into PhotoData
    private PhotoData loadComicData(String path) {
        if (path == null) return null;
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