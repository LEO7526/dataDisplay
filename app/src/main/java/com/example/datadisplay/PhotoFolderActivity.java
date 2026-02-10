package com.example.datadisplay;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.datadisplay.adapters.PhotoFolderAdapter;
import com.example.datadisplay.managers.OfflineDownloadManager;
import com.example.datadisplay.managers.OfflineResourceManager;
import com.example.datadisplay.managers.OfflineResourceManager.ResourceType;
import com.example.datadisplay.models.PhotoCategory;
import com.example.datadisplay.models.PhotoData;
import com.example.datadisplay.models.PhotoFolder;
import com.example.datadisplay.utils.NetworkHelper;
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
    
    private OfflineDownloadManager downloadManager;
    private OfflineResourceManager resourceManager;
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_folder);

        recyclerView = findViewById(R.id.folderRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        
        // Initialize download managers
        downloadManager = new OfflineDownloadManager(this);
        resourceManager = new OfflineResourceManager(this);
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
        
        // Create and setup adapter
        PhotoFolderAdapter adapter = new PhotoFolderAdapter(folderList, this);
        recyclerView.setAdapter(adapter);
        
        // Add long press handler for batch download
        setupLongPressDownload(adapter);
    }
    
    /**
     * Setup long press to download entire folder
     */
    private void setupLongPressDownload(PhotoFolderAdapter adapter) {
        // Set long click listener on the adapter
        adapter.setOnLongClickListener(folderName -> {
            PhotoFolder folder = findFolderInList(folderName);
            if (folder != null && folder.images != null && !folder.images.isEmpty()) {
                downloadFolder(folder);
                return true;
            }
            return false;
        });
    }
    
    /**
     * Download entire folder of images
     */
    private void downloadFolder(PhotoFolder folder) {
        // Check WiFi connection
        if (!NetworkHelper.isWiFiConnected(this)) {
            Snackbar.make(recyclerView, "WiFi connection required for batch downloads", Snackbar.LENGTH_LONG).show();
            return;
        }
        
        List<String> imageUrls = folder.images;
        if (imageUrls == null || imageUrls.isEmpty()) {
            Snackbar.make(recyclerView, "No images to download", Snackbar.LENGTH_SHORT).show();
            return;
        }
        
        // Get download destination for logging
        File downloadDir = resourceManager.getOfflineDirectory(OfflineResourceManager.ResourceType.PHOTO);
        Log.d(TAG, "📂 User triggered folder download: " + folder.name);
        Log.d(TAG, "   Images to download: " + imageUrls.size());
        Log.d(TAG, "   Download location: " + downloadDir.getAbsolutePath());
        
        // Start batch download
        List<Long> downloadIds = downloadManager.downloadFolder(folder.name, imageUrls, true);
        
        if (!downloadIds.isEmpty()) {
            Snackbar.make(recyclerView, 
                "Downloading " + downloadIds.size() + " images from " + folder.name, 
                Snackbar.LENGTH_LONG).show();
        } else {
            Snackbar.make(recyclerView, "Failed to start downloads", Snackbar.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Find folder in current list
     */
    private PhotoFolder findFolderInList(String folderName) {
        for (PhotoFolder folder : folderList) {
            if (folder.name.equals(folderName)) {
                return folder;
            }
        }
        return null;
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