package com.example.datadisplay;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.datadisplay.adapters.RadioFileAdapter;
import com.example.datadisplay.managers.OfflineDownloadManager;
import com.example.datadisplay.managers.OfflineResourceManager;
import com.example.datadisplay.managers.OfflineResourceManager.ResourceType;
import com.example.datadisplay.utils.NetworkHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class RadioListActivity extends AppCompatActivity {

    private static final String TAG = "RadioListActivity";

    private List<String> titles;
    private List<String> urls;
    private MediaPlayer mediaPlayer;
    private String categoryName;
    private String folderName;
    private String jsonPath;
    
    private OfflineDownloadManager downloadManager;
    private OfflineResourceManager resourceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_radio_list);

        ListView listView = findViewById(R.id.radioFileListView);

        categoryName = getIntent().getStringExtra("category");
        folderName = getIntent().getStringExtra("folder");
        jsonPath = getIntent().getStringExtra("json_path");

        titles = new ArrayList<>();
        urls = new ArrayList<>();
        
        // Initialize download managers
        downloadManager = new OfflineDownloadManager(this);
        resourceManager = new OfflineResourceManager(this);

        if (jsonPath != null) {
            try {
                String json = new String(Files.readAllBytes(new File(jsonPath).toPath()), StandardCharsets.UTF_8);
                JSONObject jsonData = new JSONObject(json);
                JSONArray categories = jsonData.getJSONArray("categories");
                
                Log.d(TAG, "Looking for category: " + categoryName + ", folder: " + folderName);

                for (int i = 0; i < categories.length(); i++) {
                    JSONObject cat = categories.getJSONObject(i);
                    String currentCategoryName = cat.getString("name");
                    Log.d(TAG, "Checking category: " + currentCategoryName);
                    
                    if (currentCategoryName.equals(categoryName)) {
                        JSONArray folders = cat.getJSONArray("folders");
                        Log.d(TAG, "Found category, folders count: " + folders.length());
                        
                        for (int j = 0; j < folders.length(); j++) {
                            JSONObject folder = folders.getJSONObject(j);
                            String currentFolderName = folder.getString("name");
                            Log.d(TAG, "Checking folder: " + currentFolderName);
                            
                            if (currentFolderName.equals(folderName)) {
                                JSONArray files = folder.getJSONArray("files");
                                Log.d(TAG, "Found folder, files count: " + files.length());
                                
                                for (int k = 0; k < files.length(); k++) {
                                    JSONObject fileObj = files.getJSONObject(k);
                                    String title = fileObj.getString("title");
                                    String path = fileObj.getString("path");
                                    titles.add(title);
                                    urls.add(path);
                                    Log.d(TAG, "Loaded: " + title + " → " + path);
                                }
                                break;
                            }
                        }
                        break;
                    }
                }
                
                Log.d(TAG, "Total files loaded: " + titles.size());
                if (titles.isEmpty()) {
                    Log.w(TAG, "WARNING: No files loaded! Category: " + categoryName + ", Folder: " + folderName);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading files", e);
                e.printStackTrace();
                Toast.makeText(this, "Error loading files: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        
        // Setup adapter
        RadioFileAdapter adapter = new RadioFileAdapter(this, titles);
        listView.setAdapter(adapter);
        
        // Click to play
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < titles.size() && position < urls.size()) {
                Intent intent = new Intent(this, RadioDetailActivity.class);
                intent.putExtra("title", titles.get(position));
                intent.putExtra("url", urls.get(position));
                intent.putStringArrayListExtra("allUrls", new ArrayList<>(urls));
                intent.putStringArrayListExtra("allTitles", new ArrayList<>(titles));
                startActivity(intent);
            } else {
                Log.w(TAG, "Invalid position: " + position + ", max: " + titles.size());
            }
        });
        
        // Long press to download
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < titles.size() && position < urls.size()) {
                downloadAudioFile(urls.get(position), titles.get(position));
                return true;
            }
            return false;
        });
    }
    
    /**
     * Download audio file for offline access
     * Requires WiFi connection
     */
    private void downloadAudioFile(String url, String title) {
        // Check if already downloaded
        if (resourceManager.isAvailableOffline(url)) {
            Toast.makeText(this, "Already downloaded: " + title, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Check WiFi connection
        if (!NetworkHelper.isWiFiConnected(this)) {
            Toast.makeText(this, "WiFi connection required for downloads", Toast.LENGTH_LONG).show();
            return;
        }
        
        // Get download destination for logging
        File downloadDir = resourceManager.getOfflineDirectory(OfflineResourceManager.ResourceType.AUDIO);
        Log.d(TAG, "🎵 User triggered audio download: " + title);
        Log.d(TAG, "   URL: " + url);
        Log.d(TAG, "   Download location: " + downloadDir.getAbsolutePath());
        
        // Start download
        long downloadId = downloadManager.downloadResource(url, title, ResourceType.AUDIO, true);
        
        if (downloadId != -1) {
            Toast.makeText(this, "Download started: " + title, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Failed to start download", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            Log.d(TAG, "MediaPlayer released");
        }
    }
}