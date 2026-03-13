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
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Activity for browsing and managing offline downloaded content.
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
    private File currentDirectory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline_content);

        initViews();
        setupToolbar();
        setupTabs();
        setupRecyclerView();

        resourceManager = new OfflineResourceManager(this);

        android.util.Log.d(TAG, "Offline storage locations:");
        android.util.Log.d(TAG, "Photos: " + resourceManager.getOfflineDirectory(ResourceType.PHOTO).getAbsolutePath());
        android.util.Log.d(TAG, "Comics: " + resourceManager.getOfflineDirectory(ResourceType.COMIC).getAbsolutePath());
        android.util.Log.d(TAG, "Audio: " + resourceManager.getOfflineDirectory(ResourceType.AUDIO).getAbsolutePath());

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

        toolbar.setNavigationOnClickListener(v -> {
            if (!navigateUpDirectory()) {
                finish();
            }
        });
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
                    default:
                        currentType = ResourceType.PHOTO;
                        break;
                }
                currentDirectory = null;
                loadOfflineContent();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new OfflineContentAdapter(this);
        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener(this::openOfflineContent);
        adapter.setOnDeleteClickListener(this::showDeleteConfirmDialog);
    }

    private void loadOfflineContent() {
        loadingProgress.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);

        new Thread(() -> {
            List<OfflineContentAdapter.OfflineItem> items = new ArrayList<>();

            File rootDirectory = resourceManager.getOfflineDirectory(currentType);
            File activeDirectory = getActiveDirectory(rootDirectory);
            currentDirectory = activeDirectory;

            File[] children = activeDirectory.listFiles();
            if (children != null) {
                List<File> directories = new ArrayList<>();
                List<File> files = new ArrayList<>();

                for (File child : children) {
                    if (child.isDirectory()) {
                        directories.add(child);
                    } else if (child.isFile() && isSupportedFile(child, currentType)) {
                        files.add(child);
                    }
                }

                directories.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                files.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

                for (File directory : directories) {
                    OfflineContentAdapter.OfflineItem item = new OfflineContentAdapter.OfflineItem();
                    item.url = "";
                    item.localPath = directory.getAbsolutePath();
                    item.filename = directory.getName();
                    item.fileSize = calculateDirectorySize(directory);
                    item.isPriority = false;
                    item.isDirectory = true;
                    item.childCount = countSupportedFiles(directory, currentType);
                    item.type = currentType;
                    items.add(item);
                }

                for (File file : files) {
                    String mappedUrl = resourceManager.findUrlByLocalPath(file.getAbsolutePath());

                    OfflineContentAdapter.OfflineItem item = new OfflineContentAdapter.OfflineItem();
                    item.url = mappedUrl != null ? mappedUrl : "";
                    item.localPath = file.getAbsolutePath();
                    item.filename = file.getName();
                    item.fileSize = file.length();
                    item.isPriority = mappedUrl != null && resourceManager.isPriority(mappedUrl);
                    item.isDirectory = false;
                    item.childCount = 0;
                    item.type = currentType;
                    items.add(item);
                }
            }

            runOnUiThread(() -> {
                loadingProgress.setVisibility(View.GONE);
                updateToolbarTitle(rootDirectory, activeDirectory);

                if (items.isEmpty()) {
                    recyclerView.setVisibility(View.GONE);
                    emptyView.setVisibility(View.VISIBLE);
                    if (activeDirectory.equals(rootDirectory)) {
                        emptyText.setText("No offline " + currentType.name().toLowerCase(Locale.getDefault()) + " content");
                    } else {
                        emptyText.setText("This folder is empty");
                    }
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

        String info = "Total: " + OfflineResourceManager.formatFileSize(totalSize)
                + " | " + currentType.name() + ": " + OfflineResourceManager.formatFileSize(typeSize);
        storageInfo.setText(info);
    }

    private void openOfflineContent(String url, String localPath) {
        File file = new File(localPath);
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
            return;
        }

        if (file.isDirectory()) {
            currentDirectory = file;
            loadOfflineContent();
            return;
        }

        Intent intent;
        switch (currentType) {
            case PHOTO:
            case COMIC:
                ArrayList<String> imageList = buildSiblingMediaList(file, currentType);
                int imagePosition = imageList.indexOf(file.getAbsolutePath());
                if (imageList.isEmpty()) {
                    Toast.makeText(this, "No images found in this folder", Toast.LENGTH_SHORT).show();
                    return;
                }
                intent = new Intent(this, PhotoActivity.class);
                intent.putStringArrayListExtra("images", imageList);
                intent.putExtra("position", Math.max(imagePosition, 0));
                startActivity(intent);
                break;
            case AUDIO:
                ArrayList<String> audioList = buildSiblingMediaList(file, ResourceType.AUDIO);
                ArrayList<String> titleList = new ArrayList<>();
                for (String path : audioList) {
                    titleList.add(stripExtension(new File(path).getName()));
                }
                int audioPosition = audioList.indexOf(file.getAbsolutePath());
                if (audioList.isEmpty()) {
                    Toast.makeText(this, "No audio found in this folder", Toast.LENGTH_SHORT).show();
                    return;
                }
                intent = new Intent(this, RadioDetailActivity.class);
                intent.putExtra("title", titleList.get(Math.max(audioPosition, 0)));
                intent.putExtra("url", file.getAbsolutePath());
                intent.putStringArrayListExtra("allUrls", audioList);
                intent.putStringArrayListExtra("allTitles", titleList);
                startActivity(intent);
                break;
            default:
                Toast.makeText(this, "Unsupported content type", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void showDeleteConfirmDialog(String url, String localPath) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Offline Content")
                .setMessage("Are you sure you want to delete this offline content?")
                .setPositiveButton("Delete", (dialog, which) -> deleteOfflineContent(url, localPath))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteOfflineContent(String url, String localPath) {
        File file = new File(localPath);
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean deleted;
        if (file.isDirectory()) {
            List<String> deletedPaths = new ArrayList<>();
            deleted = deleteRecursively(file, deletedPaths);
            for (String deletedPath : deletedPaths) {
                resourceManager.removeOfflineStatusByLocalPath(deletedPath);
            }
        } else {
            deleted = file.delete();
            if (url != null && !url.isEmpty()) {
                resourceManager.removeOfflineStatus(url);
            } else {
                resourceManager.removeOfflineStatusByLocalPath(localPath);
            }
        }

        if (deleted) {
            Toast.makeText(this, "Deleted successfully", Toast.LENGTH_SHORT).show();
            loadOfflineContent();
        } else {
            Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show();
        }
    }

    private void showClearCacheDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Offline Cache")
                .setMessage("This will delete all offline content except priority items. Continue?")
                .setPositiveButton("Clear", (dialog, which) -> clearOfflineCache(false))
                .setNegativeButton("Clear All", (dialog, which) -> clearOfflineCache(true))
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

        String message = "Total Storage: " + OfflineResourceManager.formatFileSize(totalSize) + "\n\n"
                + "Photos: " + OfflineResourceManager.formatFileSize(photoSize) + "\n"
                + "Comics: " + OfflineResourceManager.formatFileSize(comicSize) + "\n"
                + "Audio: " + OfflineResourceManager.formatFileSize(audioSize);

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

    @Override
    public void onBackPressed() {
        if (!navigateUpDirectory()) {
            super.onBackPressed();
        }
    }

    private boolean navigateUpDirectory() {
        File rootDirectory = resourceManager.getOfflineDirectory(currentType);
        File activeDirectory = getActiveDirectory(rootDirectory);

        if (activeDirectory.equals(rootDirectory)) {
            return false;
        }

        File parent = activeDirectory.getParentFile();
        if (parent != null && parent.getAbsolutePath().startsWith(rootDirectory.getAbsolutePath())) {
            currentDirectory = parent.equals(rootDirectory) ? rootDirectory : parent;
        } else {
            currentDirectory = rootDirectory;
        }

        loadOfflineContent();
        return true;
    }

    private File getActiveDirectory(File rootDirectory) {
        if (currentDirectory == null || !currentDirectory.exists()) {
            return rootDirectory;
        }

        if (!currentDirectory.getAbsolutePath().startsWith(rootDirectory.getAbsolutePath())) {
            return rootDirectory;
        }

        return currentDirectory;
    }

    private void updateToolbarTitle(File rootDirectory, File activeDirectory) {
        if (getSupportActionBar() == null) {
            return;
        }

        if (activeDirectory.equals(rootDirectory)) {
            String typeName = currentType.name().toLowerCase(Locale.getDefault());
            typeName = typeName.substring(0, 1).toUpperCase(Locale.getDefault()) + typeName.substring(1);
            getSupportActionBar().setTitle("Offline " + typeName);
        } else {
            getSupportActionBar().setTitle(activeDirectory.getName());
        }
    }

    private ArrayList<String> buildSiblingMediaList(File selectedFile, ResourceType type) {
        ArrayList<String> mediaPaths = new ArrayList<>();
        File parent = selectedFile.getParentFile();
        if (parent == null || !parent.exists()) {
            return mediaPaths;
        }

        File[] siblings = parent.listFiles();
        if (siblings == null) {
            return mediaPaths;
        }

        List<File> files = new ArrayList<>();
        for (File sibling : siblings) {
            if (sibling.isFile() && isSupportedFile(sibling, type)) {
                files.add(sibling);
            }
        }
        files.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        for (File file : files) {
            mediaPaths.add(file.getAbsolutePath());
        }
        return mediaPaths;
    }

    private String stripExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }

    private boolean isSupportedFile(File file, ResourceType type) {
        String name = file.getName().toLowerCase(Locale.getDefault());
        switch (type) {
            case AUDIO:
                return name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".aac")
                        || name.endsWith(".wav") || name.endsWith(".ogg") || name.endsWith(".flac");
            case PHOTO:
            case COMIC:
                return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                        || name.endsWith(".webp") || name.endsWith(".gif");
            case JSON:
                return name.endsWith(".json");
            default:
                return false;
        }
    }

    private int countSupportedFiles(File directory, ResourceType type) {
        if (directory == null || !directory.exists()) {
            return 0;
        }

        int count = 0;
        File[] children = directory.listFiles();
        if (children == null) {
            return 0;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                count += countSupportedFiles(child, type);
            } else if (child.isFile() && isSupportedFile(child, type)) {
                count++;
            }
        }

        return count;
    }

    private long calculateDirectorySize(File directory) {
        if (directory == null || !directory.exists()) {
            return 0;
        }

        long total = 0;
        File[] children = directory.listFiles();
        if (children == null) {
            return 0;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                total += calculateDirectorySize(child);
            } else {
                total += child.length();
            }
        }

        return total;
    }

    private boolean deleteRecursively(File target, List<String> deletedPaths) {
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursively(child, deletedPaths)) {
                        return false;
                    }
                }
            }
            return target.delete();
        }

        boolean deleted = target.delete();
        if (deleted) {
            deletedPaths.add(target.getAbsolutePath());
        }
        return deleted;
    }
}
