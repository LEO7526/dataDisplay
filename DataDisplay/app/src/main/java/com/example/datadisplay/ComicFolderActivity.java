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

public class ComicFolderActivity extends AppCompatActivity implements PhotoFolderAdapter.OnFolderClickListener {

    private static final String TAG = "ComicFolderActivity";

    private List<PhotoFolder> folderList;
    private String jsonPath;
    private String categoryName;
    private RecyclerView recyclerView;
    private OfflineDownloadManager downloadManager;
    private OfflineResourceManager resourceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_folder);

        Log.d(TAG, "onCreate: ComicFolderActivity started");

        recyclerView = findViewById(R.id.folderRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        downloadManager = new OfflineDownloadManager(this);
        resourceManager = new OfflineResourceManager(this);

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
                        PhotoFolder folder = findFolderByName(category.folders, folderName);
                        if (folder != null && folder.folders != null && !folder.folders.isEmpty()) {
                            folderList = folder.folders;
                            Log.d(TAG, "Loaded subfolder: " + folder.name + " with " + folderList.size() + " children");
                        } else if (folder != null && folder.images != null && !folder.images.isEmpty()) {
                            Intent intent = new Intent(this, ComicListActivity.class);
                            intent.putExtra("category_name", categoryName);
                            intent.putExtra("folder_name", folder.name);
                            intent.putExtra("json_path", jsonPath);
                            Log.d(TAG, "🧭 Auto route leaf folder -> ComicListActivity | category_name=" + categoryName + " | folder_name=" + folder.name + " | json_path=" + jsonPath);
                            startActivity(intent);
                            finish();
                            return;
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
            Snackbar.make(recyclerView, "No comic pages to download", Snackbar.LENGTH_SHORT).show();
            return;
        }

        File downloadDir = resourceManager.getOfflineDirectory(OfflineResourceManager.ResourceType.COMIC);
        Log.d(TAG, "User triggered comic folder download: " + folder.name);
        Log.d(TAG, "Comic pages to download: " + totalImages);
        Log.d(TAG, "Download location: " + downloadDir.getAbsolutePath());

        List<Long> downloadIds = new ArrayList<>();
        enqueueFolderDownloadsRecursive(folder, downloadIds);

        if (!downloadIds.isEmpty()) {
            Snackbar.make(recyclerView,
                    "Downloading " + downloadIds.size() + " pages from " + folder.name,
                    Snackbar.LENGTH_LONG).show();
        } else {
            Snackbar.make(recyclerView, "Failed to start downloads", Snackbar.LENGTH_SHORT).show();
        }
    }

    private void enqueueFolderDownloadsRecursive(PhotoFolder folder, List<Long> downloadIds) {
        if (folder == null) {
            return;
        }

        if (folder.images != null && !folder.images.isEmpty()) {
            downloadIds.addAll(downloadManager.downloadFolder(
                    folder.name,
                    folder.images,
                    OfflineResourceManager.ResourceType.COMIC,
                    true
            ));
        }

        if (folder.folders != null) {
            for (PhotoFolder child : folder.folders) {
                enqueueFolderDownloadsRecursive(child, downloadIds);
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
        Log.d(TAG, "Clicked folder: " + folderName);

        PhotoData comicData = loadComicData(jsonPath);
        if (comicData != null && comicData.categories != null) {
            for (PhotoCategory category : comicData.categories) {
                if (category.name.equals(categoryName) && category.folders != null) {
                    PhotoFolder folder = findFolderByName(category.folders, folderName);
                    if (folder == null) {
                        return;
                    }

                    if (folder.folders != null && !folder.folders.isEmpty()) {
                        // ✅ Has subfolders → open ComicFolderActivity again
                        Intent intent = new Intent(this, ComicFolderActivity.class);
                        intent.putExtra("category_name", categoryName);
                        intent.putExtra("folder_name", folder.name);
                        intent.putExtra("json_path", jsonPath);
                        Log.d(TAG, "🧭 Click folder -> ComicFolderActivity | category_name=" + categoryName + " | folder_name=" + folder.name + " | json_path=" + jsonPath);
                        startActivity(intent);
                    } else {
                        // ✅ No subfolders → open ComicListActivity
                        Intent intent = new Intent(this, ComicListActivity.class);
                        intent.putExtra("category_name", categoryName);
                        intent.putExtra("folder_name", folder.name);
                        intent.putExtra("json_path", jsonPath);
                        Log.d(TAG, "🧭 Click folder -> ComicListActivity | category_name=" + categoryName + " | folder_name=" + folder.name + " | json_path=" + jsonPath);
                        startActivity(intent);
                    }
                    return;
                }
            }
        }
    }

    private PhotoFolder findFolderByName(List<PhotoFolder> folders, String targetName) {
        if (folders == null) {
            return null;
        }

        for (PhotoFolder folder : folders) {
            if (targetName.equals(folder.name)) {
                return folder;
            }

            PhotoFolder found = findFolderByName(folder.folders, targetName);
            if (found != null) {
                return found;
            }
        }

        return null;
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