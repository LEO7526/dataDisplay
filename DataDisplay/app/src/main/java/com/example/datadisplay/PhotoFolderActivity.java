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
    private RecyclerView recyclerView;
    private OfflineDownloadManager downloadManager;
    private OfflineResourceManager resourceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_folder);

        recyclerView = findViewById(R.id.folderRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        downloadManager = new OfflineDownloadManager(this);
        resourceManager = new OfflineResourceManager(this);

        categoryName = getIntent().getStringExtra("category_name");
        jsonPath = getIntent().getStringExtra("json_path");
        String folderName = getIntent().getStringExtra("folder_name");

        Log.d(TAG, "🧭 onCreate route entry | category_name=" + categoryName + " | folder_name=" + folderName + " | json_path=" + jsonPath);

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
                        if (current == null) {
                            Snackbar.make(recyclerView, "Folder not found: " + folderName, Snackbar.LENGTH_LONG).show();
                        } else if (current.folders != null && !current.folders.isEmpty()) {
                            folderList.addAll(current.folders);
                            Log.d(TAG, "Loaded subfolder: " + current.name + " with " + folderList.size() + " children");
                        } else if (current.images != null && !current.images.isEmpty()) {
                            Intent intent = new Intent(this, PhotoListActivity.class);
                            intent.putExtra("category_name", categoryName);
                            intent.putExtra("folder_name", current.name);
                            intent.putExtra("json_path", jsonPath);
                            Log.d(TAG, "🧭 Auto route leaf folder -> PhotoListActivity | category_name=" + categoryName + " | folder_name=" + current.name + " | json_path=" + jsonPath);
                            startActivity(intent);
                            finish();
                            return;
                        } else {
                            Snackbar.make(recyclerView, "This folder is empty", Snackbar.LENGTH_LONG).show();
                        }
                    }
                    break;
                }
            }
        }

        PhotoFolderAdapter adapter = new PhotoFolderAdapter(folderList, this);
        recyclerView.setAdapter(adapter);
        setupLongPressDownload(adapter);
    }

    private void setupLongPressDownload(PhotoFolderAdapter adapter) {
        adapter.setOnLongClickListener(folderName -> {
            PhotoFolder folder = findFolderInList(folderName);
            if (folder != null) {
                downloadFolderTree(folder);
                return true;
            }
            return false;
        });
    }

    private void downloadFolderTree(PhotoFolder folder) {
        if (!NetworkHelper.isWiFiConnected(this)) {
            Snackbar.make(recyclerView, "WiFi connection required for batch downloads", Snackbar.LENGTH_LONG).show();
            return;
        }

        int totalImages = countImagesRecursive(folder);
        if (totalImages <= 0) {
            Snackbar.make(recyclerView, "No images to download", Snackbar.LENGTH_SHORT).show();
            return;
        }

        File downloadDir = resourceManager.getOfflineDirectory(OfflineResourceManager.ResourceType.PHOTO);
        Log.d(TAG, "User triggered folder download: " + folder.name);
        Log.d(TAG, "Images to download: " + totalImages);
        Log.d(TAG, "Download location: " + downloadDir.getAbsolutePath());

        List<Long> downloadIds = new ArrayList<>();
        enqueueFolderDownloadsRecursive(folder, OfflineResourceManager.ResourceType.PHOTO, downloadIds);

        if (!downloadIds.isEmpty()) {
            Snackbar.make(recyclerView,
                    "Downloading " + downloadIds.size() + " images from " + folder.name,
                    Snackbar.LENGTH_LONG).show();
        } else {
            Snackbar.make(recyclerView, "Failed to start downloads", Snackbar.LENGTH_SHORT).show();
        }
    }

    private void enqueueFolderDownloadsRecursive(PhotoFolder folder,
                                                 OfflineResourceManager.ResourceType type,
                                                 List<Long> downloadIds) {
        if (folder == null) {
            return;
        }

        if (folder.images != null && !folder.images.isEmpty()) {
            downloadIds.addAll(downloadManager.downloadFolder(folder.name, folder.images, type, true));
        }

        if (folder.folders != null) {
            for (PhotoFolder child : folder.folders) {
                enqueueFolderDownloadsRecursive(child, type, downloadIds);
            }
        }
    }

    private int countImagesRecursive(PhotoFolder folder) {
        if (folder == null) {
            return 0;
        }

        int total = folder.images != null ? folder.images.size() : 0;
        if (folder.folders != null) {
            for (PhotoFolder child : folder.folders) {
                total += countImagesRecursive(child);
            }
        }
        return total;
    }

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
                    Log.d(TAG, "🧭 Click folder -> PhotoFolderActivity | category_name=" + categoryName + " | folder_name=" + clicked.name + " | json_path=" + jsonPath);
                    startActivity(intent);
                } else if (clicked.images != null && !clicked.images.isEmpty()) {
                    Intent intent = new Intent(this, PhotoListActivity.class);
                    intent.putExtra("category_name", categoryName);
                    intent.putExtra("folder_name", clicked.name);
                    intent.putExtra("json_path", jsonPath);
                    Log.d(TAG, "🧭 Click folder -> PhotoListActivity | category_name=" + categoryName + " | folder_name=" + clicked.name + " | json_path=" + jsonPath);
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