package com.example.datadisplay;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.datadisplay.adapters.OfflineContentAdapter;
import com.example.datadisplay.managers.OfflineResourceManager;
import com.example.datadisplay.managers.OfflineResourceManager.ResourceType;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity for browsing and managing offline downloaded content
 */
public class OfflineContentActivity extends AppCompatActivity {

    private static final String TAG = "OfflineContentActivity";

    private Toolbar toolbar;
    private TabLayout tabLayout;
    private RecyclerView recyclerView;
    private LinearLayout emptyView;
    private TextView emptyText;
    private TextView storageInfo;
    private ProgressBar loadingProgress;

    private OfflineResourceManager resourceManager;
    private OfflineContentAdapter adapter;
    private ResourceType currentType = ResourceType.PHOTO;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline_content);

        initViews();
        setupToolbar();
        setupTabs();
        setupRecyclerView();

        resourceManager = new OfflineResourceManager(this);
        
        // Log storage locations for debugging
        android.util.Log.d(TAG, "═══════════════════════════════════════════");
        android.util.Log.d(TAG, "📱 Offline Content Storage Locations:");
        android.util.Log.d(TAG, "   Photos: " + resourceManager.getOfflineDirectory(ResourceType.PHOTO).getAbsolutePath());
        android.util.Log.d(TAG, "   Comics: " + resourceManager.getOfflineDirectory(ResourceType.COMIC).getAbsolutePath());
        android.util.Log.d(TAG, "   Audio:  " + resourceManager.getOfflineDirectory(ResourceType.AUDIO).getAbsolutePath());
        android.util.Log.d(TAG, "═══════════════════════════════════════════");
        
        loadOfflineContent();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tabLayout = findViewById(R.id.tabLayout);
        recyclerView = findViewById(R.id.recyclerView);
        emptyView = findViewById(R.id.emptyView);
        emptyText = findViewById(R.id.emptyText);
        storageInfo = findViewById(R.id.storageInfo);
        loadingProgress = findViewById(R.id.loadingProgress);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Offline Content");
        }
        
        toolbar.setNavigationOnClickListener(v -> finish());
        
        // Add clear cache button
        toolbar.inflateMenu(R.menu.menu_offline);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_clear_cache) {
                showClearCacheDialog();
                return true;
            } else if (item.getItemId() == R.id.action_manage_storage) {
                showStorageManagementDialog();
                return true;
            }
            return false;
        });
    }

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("Photos").setIcon(R.drawable.ic_photos));
        tabLayout.addTab(tabLayout.newTab().setText("Comics").setIcon(R.drawable.ic_comic));
        tabLayout.addTab(tabLayout.newTab().setText("Audio").setIcon(R.drawable.ic_mp3));
        
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0:
                        currentType = ResourceType.PHOTO;
                        break;
                    case 1:
                        currentType = ResourceType.COMIC;
                        break;
                    case 2:
                        currentType = ResourceType.AUDIO;
                        break;
                }
                loadOfflineContent();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new OfflineContentAdapter(this);
        recyclerView.setAdapter(adapter);
        
        adapter.setOnItemClickListener((url, localPath) -> {
            openOfflineContent(url, localPath);
        });
        
        adapter.setOnDeleteClickListener((url, localPath) -> {
            showDeleteConfirmDialog(url, localPath);
        });
    }

    private void loadOfflineContent() {
        loadingProgress.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);

        // Load in background thread
        new Thread(() -> {
            File offlineDir = resourceManager.getOfflineDirectory(currentType);
            android.util.Log.d(TAG, "📂 Loading offline content for: " + currentType.name());
            android.util.Log.d(TAG, "   Directory: " + offlineDir.getAbsolutePath());
            android.util.Log.d(TAG, "   Directory exists: " + offlineDir.exists());
            
            if (offlineDir.exists()) {
                File[] files = offlineDir.listFiles();
                android.util.Log.d(TAG, "   Files in directory: " + (files != null ? files.length : 0));
                if (files != null) {
                    for (File f : files) {
                        android.util.Log.d(TAG, "     - " + f.getName() + " (" + (f.length() / 1024) + " KB)");
                    }
                }
            }
            
            List<String> offlineUrls = resourceManager.getOfflineUrls(currentType);
            android.util.Log.d(TAG, "   Tracked URLs: " + offlineUrls.size());
            
            List<OfflineContentAdapter.OfflineItem> items = new ArrayList<>();
            
            for (String url : offlineUrls) {
                android.util.Log.d(TAG, "   Checking URL: " + url);
                File file = resourceManager.getOfflineFile(url);
                android.util.Log.d(TAG, "     Retrieved file: " + (file != null ? file.getAbsolutePath() : "null"));
                android.util.Log.d(TAG, "     File exists: " + (file != null && file.exists()));
                
                if (file != null && file.exists()) {
                    OfflineContentAdapter.OfflineItem item = new OfflineContentAdapter.OfflineItem();
                    item.url = url;
                    item.localPath = file.getAbsolutePath();
                    item.filename = file.getName();
                    item.fileSize = file.length();
                    item.isPriority = resourceManager.isPriority(url);
                    item.type = currentType;
                    items.add(item);
                    android.util.Log.d(TAG, "     ✅ Added to list");
                } else {
                    android.util.Log.w(TAG, "     ❌ File not found or doesn't exist");
                }
            }

            // Update UI on main thread
            runOnUiThread(() -> {
                loadingProgress.setVisibility(View.GONE);
                
                if (items.isEmpty()) {
                    recyclerView.setVisibility(View.GONE);
                    emptyView.setVisibility(View.VISIBLE);
                    emptyText.setText("No offline " + currentType.name().toLowerCase() + " content");
                } else {
                    recyclerView.setVisibility(View.VISIBLE);
                    emptyView.setVisibility(View.GONE);
                    adapter.setItems(items);
                }
                
                updateStorageInfo();
            });
        }).start();
    }

    private void updateStorageInfo() {
        long totalSize = resourceManager.getTotalOfflineSize();
        long typeSize = resourceManager.getOfflineSize(currentType);
        
        String info = "Total: " + OfflineResourceManager.formatFileSize(totalSize) +
                     " | " + currentType.name() + ": " + OfflineResourceManager.formatFileSize(typeSize);
        storageInfo.setText(info);
    }

    private void openOfflineContent(String url, String localPath) {
        File file = new File(localPath);
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = null;
        
        switch (currentType) {
            case PHOTO:
            case COMIC:
                // Open in image viewer
                intent = new Intent(this, PhotoActivity.class);
                intent.putExtra("offline_mode", true);
                intent.putExtra("file_path", localPath);
                break;
                
            case AUDIO:
                // Open in audio player
                intent = new Intent(this, RadioDetailActivity.class);
                intent.putExtra("offline_mode", true);
                intent.putExtra("file_path", localPath);
                break;
        }
        
        if (intent != null) {
            startActivity(intent);
        }
    }

    private void showDeleteConfirmDialog(String url, String localPath) {
        new AlertDialog.Builder(this)
            .setTitle("Delete Offline Content")
            .setMessage("Are you sure you want to delete this offline content?")
            .setPositiveButton("Delete", (dialog, which) -> {
                deleteOfflineContent(url, localPath);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void deleteOfflineContent(String url, String localPath) {
        File file = new File(localPath);
        if (file.exists()) {
            if (file.delete()) {
                resourceManager.removeOfflineStatus(url);
                Toast.makeText(this, "Deleted successfully", Toast.LENGTH_SHORT).show();
                loadOfflineContent();
            } else {
                Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showClearCacheDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Clear Offline Cache")
            .setMessage("This will delete all offline content except priority items. Continue?")
            .setPositiveButton("Clear", (dialog, which) -> {
                clearOfflineCache(false);
            })
            .setNegativeButton("Clear All", (dialog, which) -> {
                clearOfflineCache(true);
            })
            .setNeutralButton("Cancel", null)
            .show();
    }

    private void clearOfflineCache(boolean includePriority) {
        loadingProgress.setVisibility(View.VISIBLE);
        
        new Thread(() -> {
            resourceManager.clearOfflineCache(includePriority);
            
            runOnUiThread(() -> {
                Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show();
                loadOfflineContent();
            });
        }).start();
    }

    private void showStorageManagementDialog() {
        long totalSize = resourceManager.getTotalOfflineSize();
        long photoSize = resourceManager.getOfflineSize(ResourceType.PHOTO);
        long comicSize = resourceManager.getOfflineSize(ResourceType.COMIC);
        long audioSize = resourceManager.getOfflineSize(ResourceType.AUDIO);
        
        String message = "Total Storage: " + OfflineResourceManager.formatFileSize(totalSize) + "\n\n" +
                        "Photos: " + OfflineResourceManager.formatFileSize(photoSize) + "\n" +
                        "Comics: " + OfflineResourceManager.formatFileSize(comicSize) + "\n" +
                        "Audio: " + OfflineResourceManager.formatFileSize(audioSize);
        
        new AlertDialog.Builder(this)
            .setTitle("Storage Management")
            .setMessage(message)
            .setPositiveButton("Clear Old (30 days)", (dialog, which) -> {
                resourceManager.clearOldOfflineResources(30);
                Toast.makeText(this, "Old content cleared", Toast.LENGTH_SHORT).show();
                loadOfflineContent();
            })
            .setNegativeButton("Clear Old (7 days)", (dialog, which) -> {
                resourceManager.clearOldOfflineResources(7);
                Toast.makeText(this, "Old content cleared", Toast.LENGTH_SHORT).show();
                loadOfflineContent();
            })
            .setNeutralButton("Close", null)
            .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadOfflineContent();
    }
}
