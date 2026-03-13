package com.example.datadisplay;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Menu;
import android.view.View;
import android.view.ViewParent;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.datadisplay.managers.OfflineDownloadManager;
import com.example.datadisplay.utils.DataUrlManager;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.navigation.NavigationView;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import android.util.JsonReader;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";
    private static final int MAX_RENDERED_SEARCH_RESULTS = 50;
    private static final String SEARCH_DIAG_BUILD = "2026-03-06-r4";
    private static final String SEARCH_FILTER_ALL = "all";
    private static final String SEARCH_FILTER_MP3 = "mp3";
    private static final String SEARCH_FILTER_BOOK = "book";
    private static final String SEARCH_FILTER_COMIC = "comic";
    private static final String SEARCH_FILTER_PHOTO = "photo";

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle drawerToggle;
    private Toolbar toolbar;

    private EditText searchBox;
    private LinearLayout searchResultsContainer;
    private LinearLayout dashboardContainer;
    private GridLayout quickAccessGrid;

    // Dashboard stats
    private TextView statsText;
    private int totalMp3s = 0;
    private int totalBooks = 0;
    private int totalComics = 0;
    private int totalPhotos = 0;

    // Download management
    private DownloadManager downloadManager;
    private BroadcastReceiver downloadCompleteReceiver;
    private final java.util.HashMap<String, Long> downloadQueue = new java.util.HashMap<>();
    private final java.util.HashSet<String> downloadingFiles = new java.util.HashSet<>();
    private Handler downloadProgressHandler = null;
    
    // Pending navigation after download
    private String pendingNavigationFile = null;
    private Class<?> pendingNavigationActivity = null;
    private Article pendingSearchResult = null;

    private DataUrlManager dataUrlManager;
    private static OfflineDownloadManager offlineDownloadManager;

    private final Map<String, Class<?>> searchActivityMap = new HashMap<>();
    private volatile int latestSearchRequestId = 0;
    private volatile String latestNormalizedSearchQuery = "";
    private final List<Article> latestSearchResults = new ArrayList<>();
    private String activeSearchFilter = SEARCH_FILTER_ALL;
    private int currentSearchRenderLimit = MAX_RENDERED_SEARCH_RESULTS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Log.e(TAG, "SEARCH_DIAG_BUILD | " + SEARCH_DIAG_BUILD);

        try {
            dataUrlManager = new DataUrlManager(this);

            if (offlineDownloadManager == null) {
                offlineDownloadManager = new OfflineDownloadManager(this);
                Log.d(TAG, "OfflineDownloadManager initialized");
            }

            // Check and request storage permissions (non-blocking)
            Log.d(TAG, "🔐 Checking storage permissions...");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ uses READ_MEDIA_* permissions
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "📱 Requesting READ_MEDIA permissions...");
                    ActivityCompat.requestPermissions(this,
                            new String[]{
                                    Manifest.permission.READ_MEDIA_AUDIO,
                                    Manifest.permission.READ_MEDIA_VIDEO,
                                    Manifest.permission.READ_MEDIA_IMAGES
                            }, 100);
                } else {
                    Log.d(TAG, "✅ All media permissions already granted");
                }
            } else {
                // Android 12 and below
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "📱 Requesting READ_EXTERNAL_STORAGE permission...");
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
                } else {
                    Log.d(TAG, "✅ READ_EXTERNAL_STORAGE permission already granted");
                }
            }
            
            // Initialize download manager FIRST
            downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager == null) {
                Log.e(TAG, "ERROR: DownloadManager is NULL! Cannot initialize downloads.");
                Toast.makeText(this, "❌ 下載管理器初始化失敗", Toast.LENGTH_SHORT).show();
            } else {
                Log.d(TAG, "✓ DownloadManager initialized successfully");
            }
            
            // Register download complete receiver
            downloadCompleteReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(TAG, "📥 Download complete broadcast received");
                    long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (offlineDownloadManager != null && downloadId != -1) {
                        offlineDownloadManager.onDownloadComplete(downloadId);
                    }
                    onDownloadComplete(intent);
                }
            };
            registerReceiver(downloadCompleteReceiver, 
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED);
            Log.d(TAG, "✓ BroadcastReceiver registered");

            // Download JSON files BEFORE initializing views
            Log.d(TAG, "🌐 Starting JSON file downloads...");
            ensureFile("mp3_data.json", dataUrlManager.getMp3DownloadUrl());
            ensureFile("data.json", dataUrlManager.getBookDownloadUrl());
            ensureFile("comic_data.json", dataUrlManager.getComicDownloadUrl());
            ensureFile("photo_data.json", dataUrlManager.getPhotoDownloadUrl());
            // Copy quiz files from assets (offline preferred)
            try {
                copyFileFromAssets("index.html", new File(getExternalFilesDir("Downloads"), "index.html"));
                copyFileFromAssets("questions-data.js", new File(getExternalFilesDir("Downloads"), "questions-data.js"));
                copyFileFromAssets("script.js", new File(getExternalFilesDir("Downloads"), "script.js"));
                copyFileFromAssets("styles.css", new File(getExternalFilesDir("Downloads"), "styles.css"));
            } catch (Exception e) {
                Log.d(TAG, "⚠️ OCP assets not present locally, falling back to Drive downloads");
                ensureFile("index.html", dataUrlManager.getOcpIndexDownloadUrl());
                ensureFile("questions-data.js", dataUrlManager.getOcpQuestionsDownloadUrl());
                ensureFile("script.js", dataUrlManager.getOcpScriptDownloadUrl());
                ensureFile("styles.css", dataUrlManager.getOcpStyleDownloadUrl());
            }

            Log.d(TAG, "📋 Initializing views...");
            // Initialize views
            initializeViews();
            setupNavigation();
            initializeSearchActivityMap();
            Log.d(TAG, "✓ Views initialized");
            
            // Load statistics after files are ensured
            loadStatistics();
            setupSearchFunctionality();
            setupQuickAccessCards();
            
            // Start periodic download progress checking
            startDownloadProgressCheck();
            
            // Refresh statistics every 2 seconds to catch file loads
            Handler refreshHandler = new Handler(Looper.getMainLooper());
            refreshHandler.postDelayed(() -> {
                Log.d(TAG, "🔄 Refreshing statistics...");
                loadStatistics();
            }, 2000);
            
            Log.d(TAG, "✓ HomeActivity onCreate completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "💥 FATAL ERROR in onCreate(): " + e.getMessage(), e);
            e.printStackTrace();
            Toast.makeText(this, "❌ 應用程式初始化失敗: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * 啟動定期檢查下載進度
     */
    private void startDownloadProgressCheck() {
        downloadProgressHandler = new Handler(Looper.getMainLooper());
        downloadProgressHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkDownloadProgress();
                // Schedule next check after 3 seconds
                downloadProgressHandler.postDelayed(this, 3000);
            }
        }, 3000);
        Log.d(TAG, "📊 Download progress checking started");
    }
    
    /**
     * 檢查所有下載的進度和狀態
     */
    private void checkDownloadProgress() {
        if (downloadQueue.isEmpty()) {
            return;
        }
        
        try {
            DownloadManager.Query query = new DownloadManager.Query();
            Cursor cursor = downloadManager.query(query);
            
            if (cursor != null) {
                int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID);
                int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                
                while (cursor.moveToNext()) {
                    long downloadId = cursor.getLong(idIndex);
                    int status = cursor.getInt(statusIndex);
                    
                    // Check if this is one of our downloads
                    for (java.util.Map.Entry<String, Long> entry : downloadQueue.entrySet()) {
                        if (entry.getValue() == downloadId) {
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                Log.d(TAG, "✅ Download completed: " + entry.getKey());
                                // Trigger onDownloadComplete manually
                                Intent intent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
                                intent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId);
                                onDownloadComplete(intent);
                            } else if (status == DownloadManager.STATUS_FAILED) {
                                int reason = cursor.getInt(reasonIndex);
                                Log.e(TAG, "❌ Download failed for " + entry.getKey() + " (reason: " + reason + ")");
                                downloadingFiles.remove(entry.getKey());
                            }
                            break;
                        }
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking download progress: " + e.getMessage(), e);
        }
    }
    
    /**
     * 從 assets 複製文件到應用數據目錄
     */
    private void copyFileFromAssets(String filename, File destinationFile) throws Exception {
        // Create parent directory if it doesn't exist
        File parentDir = destinationFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        // Check if file already exists and is valid
        if (destinationFile.exists() && destinationFile.length() > 0) {
            Log.d(TAG, "✅ File already exists: " + filename + " (" + (destinationFile.length() / 1024) + " KB)");
            return;
        }
        
        Log.d(TAG, "📋 Copying " + filename + " from assets...");
        
        try (java.io.InputStream inputStream = getAssets().open(filename);
             java.io.FileOutputStream outputStream = new java.io.FileOutputStream(destinationFile)) {
            
            byte[] buffer = new byte[8192]; // Larger buffer for better performance
            int length;
            long totalWritten = 0;
            
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
                totalWritten += length;
            }
            
            outputStream.flush();
            
            long copiedSize = destinationFile.length();
            Log.d(TAG, "✅ Successfully copied " + filename + " (" + (copiedSize / 1024) + " KB) to " + destinationFile.getAbsolutePath());
            
            if (copiedSize == 0) {
                Log.e(TAG, "⚠️ WARNING: " + filename + " copied but file is empty!");
                throw new IOException("Destination file is empty after copy");
            }
        } catch (Exception e) {
            // Clean up partial file on error
            if (destinationFile.exists()) {
                destinationFile.delete();
            }
            Log.e(TAG, "❌ Error copying " + filename + ": " + e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop download progress checking
        if (downloadProgressHandler != null) {
            downloadProgressHandler.removeCallbacksAndMessages(null);
            downloadProgressHandler = null;
        }
        if (downloadCompleteReceiver != null) {
            unregisterReceiver(downloadCompleteReceiver);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            boolean allGranted = true;
            StringBuilder grantLog = new StringBuilder();
            
            for (int i = 0; i < grantResults.length; i++) {
                boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                String status = granted ? "✅ GRANTED" : "❌ DENIED";
                grantLog.append(permissions[i]).append(" = ").append(status).append("\n");
                Log.d(TAG, "Permission " + permissions[i] + " = " + status);
                if (!granted) {
                    allGranted = false;
                }
            }
            
            Log.d(TAG, "Permission Summary:\n" + grantLog.toString());
            
            if (allGranted) {
                Log.d(TAG, "✅ All permissions granted!");
                Toast.makeText(this, "✅ 全部權限已授予，下載開始", Toast.LENGTH_SHORT).show();
            } else {
                Log.w(TAG, "⚠️ Some permissions were denied - app will use assets-based files and limited external storage");
                Toast.makeText(this, "⚠️ 部分權限被拒 - 將使用本機檔案", Toast.LENGTH_LONG).show();
                // 應用程式可以繼續使用 getExternalFilesDir()，這不需要特殊權限
                Log.d(TAG, "📁 Using app-specific external files directory: " + getExternalFilesDir("Downloads"));
            }
        }
    }
    
    /**
     * 確保 JSON 文件存在 - 優先從 assets 複製,然後嘗試下載
     */
    private void ensureFile(String filename, String url) {
        try {
            File destinationFile = new File(getExternalFilesDir("Downloads"), filename);
            
            // Check if file already exists and has content
            if (destinationFile.exists() && destinationFile.length() > 0) {
                Log.d(TAG, "✅ File already exists: " + filename + " (" + (destinationFile.length() / 1024) + " KB)");
                return;
            }
            
            // First, try to copy from assets
            try {
                Log.d(TAG, "🔄 Attempting to copy " + filename + " from assets...");
                copyFileFromAssets(filename, destinationFile);
                Log.d(TAG, "✅ File copied from assets: " + filename + " (" + (destinationFile.length() / 1024) + " KB)");
                return;
            } catch (Exception e) {
                Log.w(TAG, "⚠️ Could not copy from assets (" + filename + "): " + e.getMessage());
            }
            
            // If not in assets, try to download
            if (downloadManager == null) {
                Log.e(TAG, "❌ ensureFile(" + filename + "): DownloadManager is NULL - cannot download");
                return;
            }

            // Skip if already downloading
            if (downloadingFiles.contains(filename)) {
                Log.d(TAG, "⏳ Already downloading " + filename + ", skipping...");
                return;
            }
            
            // Download from GitHub
            Log.d(TAG, "📥 Downloading " + filename + " from " + url);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setDestinationInExternalFilesDir(this, "Downloads", filename);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setTitle("正在下載 " + filename);
            
            // 允許在任何网络下下载
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            request.setAllowedOverRoaming(true);
            
            long downloadId = downloadManager.enqueue(request);
            Log.d(TAG, "✓ Download enqueued with ID: " + downloadId + " for " + filename);
            
            // Track download
            downloadQueue.put(filename, downloadId);
            downloadingFiles.add(filename);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in ensureFile(" + filename + "): " + e.getMessage(), e);
        }
    }
    
    /**
     * 下載完成處理
     */
    private void onDownloadComplete(Intent intent) {
        try {
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (downloadId == -1) {
                Log.e(TAG, "❌ Invalid download ID received");
                return;
            }
            
            Log.d(TAG, "🔍 Querying download status for ID: " + downloadId);
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);
            
            Cursor cursor = null;
            String completedFile = null;
            
            try {
                cursor = downloadManager.query(query);
                if (cursor != null && cursor.moveToFirst()) {
                    int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int status = cursor.getInt(statusIndex);
                    
                    Log.d(TAG, "📊 Download status: " + status);
                    
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                        String localUri = cursor.getString(uriIndex);
                        Log.d(TAG, "✅ Download SUCCESSFUL: " + localUri);
                        
                        // Find which file completed
                        for (java.util.Map.Entry<String, Long> entry : downloadQueue.entrySet()) {
                            if (entry.getValue() == downloadId) {
                                completedFile = entry.getKey();
                                downloadingFiles.remove(completedFile);
                                Log.d(TAG, "✓ Matched to file: " + completedFile);
                                break;
                            }
                        }
                        
                        // Reload statistics after download
                        loadStatistics();
                        
                        // Auto-navigate if pending
                        if (completedFile != null && completedFile.equals(pendingNavigationFile) 
                            && pendingNavigationActivity != null) {
                            File file = new File(getExternalFilesDir("Downloads"), completedFile);
                            if (file.exists()) {
                                Log.d(TAG, "🚀 Auto-navigating to " + pendingNavigationActivity.getSimpleName());
                                Intent navIntent = new Intent(this, pendingNavigationActivity);
                                navIntent.putExtra("json_path", file.getAbsolutePath());
                                logNavigationDirection(pendingNavigationActivity.getSimpleName(),
                                        "download-complete:auto-category", null, navIntent);
                                startActivity(navIntent);
                                
                                // Clear pending navigation
                                pendingNavigationFile = null;
                                pendingNavigationActivity = null;
                                
                                Toast.makeText(this, "數據已就緒,正在打開...", Toast.LENGTH_SHORT).show();
                            }
                        }

                        if (completedFile != null
                                && pendingSearchResult != null
                                && pendingSearchResult.filename != null
                                && completedFile.equals(pendingSearchResult.filename)) {
                            File file = new File(getExternalFilesDir("Downloads"), completedFile);
                            if (file.exists()) {
                                Log.d(TAG, "🚀 Auto-navigating search result for: " + completedFile);
                                navigateToSearchResult(pendingSearchResult);
                                pendingSearchResult = null;
                                Toast.makeText(this, "搜尋結果數據已就緒,正在打開...", Toast.LENGTH_SHORT).show();
                            }
                        }
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        Log.e(TAG, "❌ Download FAILED with status code: " + status);
                        int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                        int reason = cursor.getInt(reasonIndex);
                        Log.e(TAG, "   Failure reason: " + reason);
                    } else {
                        Log.d(TAG, "⏳ Download still in progress, status: " + status);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in onDownloadComplete(): " + e.getMessage(), e);
        }
    }

    private void initializeViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        searchBox = findViewById(R.id.searchBox);
        searchResultsContainer = findViewById(R.id.searchResultsContainer);
        dashboardContainer = findViewById(R.id.dashboardContainer);
        quickAccessGrid = findViewById(R.id.quickAccessGrid);
        statsText = findViewById(R.id.statsText);
    }

    private void setupNavigation() {
        drawerToggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();
        ensureGamesMenuItem();

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                logClickAction("drawer", "home");
                drawerLayout.closeDrawers();
            } else if (id == R.id.nav_books) {
                logClickAction("drawer", "books");
                navigateToCategory("data.json", BookActivity.class, "book");
            } else if (id == R.id.nav_settings) {
                logClickAction("drawer", "settings");
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                logNavigationDirection("SettingsActivity", "drawer", null, settingsIntent);
                startActivity(settingsIntent);
            } else if (id == R.id.nav_photos) {
                logClickAction("drawer", "photos");
                navigateToCategory("photo_data.json", PhotoCategoryActivity.class, "photo");
            } else if (id == R.id.nav_mp3) {
                logClickAction("drawer", "mp3");
                navigateToCategory("mp3_data.json", RadioCategoryActivity.class, "MP3");
            } else if (id == R.id.nav_comics) {
                logClickAction("drawer", "comics");
                navigateToCategory("comic_data.json", ComicCategoryActivity.class, "comic");
            } else if (id == R.id.nav_games) {
                logClickAction("drawer", "games");
                Intent gamesIntent = new Intent(this, GameHomeActivity.class);
                logNavigationDirection("GameHomeActivity", "drawer", null, gamesIntent);
                startActivity(gamesIntent);
            } else if (id == R.id.nav_offline) {
                logClickAction("drawer", "offline-content");
                Intent offlineIntent = new Intent(this, OfflineContentActivity.class);
                logNavigationDirection("OfflineContentActivity", "drawer", null, offlineIntent);
                startActivity(offlineIntent);
            } else if (id == R.id.nav_cached_files) {
                logClickAction("drawer", "cached-files");
                Intent cachedIntent = new Intent(this, CachedFilesActivity.class);
                logNavigationDirection("CachedFilesActivity", "drawer", null, cachedIntent);
                startActivity(cachedIntent);
            }
            return true;
        });
    }

    private void ensureGamesMenuItem() {
        if (navigationView == null) {
            return;
        }
        Menu menu = navigationView.getMenu();
        if (menu.findItem(R.id.nav_games) == null) {
            menu.add(Menu.NONE, R.id.nav_games, 50, "Games").setIcon(R.drawable.ic_home);
        }
    }

    /**
     * 加載統計信息從 JSON 文件
     */
    private void loadStatistics() {
        new Thread(() -> {
            try {
                Log.d(TAG, "📊 Loading statistics...");
                // Load MP3 stats
                totalMp3s = countItemsInJson("mp3_data.json", "categories");
                Log.d(TAG, "   📝 MP3s: " + totalMp3s);

                // Load Books stats
                totalBooks = countItemsInJson("data.json", "categories");
                Log.d(TAG, "   📝 Books: " + totalBooks);

                // Load Comics stats
                totalComics = countItemsInJson("comic_data.json", "categories");
                Log.d(TAG, "   📝 Comics: " + totalComics);

                // Load Photos stats
                totalPhotos = countItemsInJson("photo_data.json", "categories");
                Log.d(TAG, "   📝 Photos: " + totalPhotos);
                
                Log.d(TAG, "✅ Statistics loaded: MP3=" + totalMp3s + " Books=" + totalBooks + " Comics=" + totalComics + " Photos=" + totalPhotos);

                runOnUiThread(this::updateDashboard);
            } catch (Exception e) {
                Log.e(TAG, "❌ Error loading statistics", e);
            }
        }).start();
    }

    /**
     * 統計 JSON 中所有檔案項目總數（支援多種格式）
     */
    private int countItemsInJson(String filename, String key) {
        int totalCount = 0;
        try {
            File jsonFile = new File(getExternalFilesDir("Downloads"), filename);

            if (!jsonFile.exists()) {
                Log.w(TAG, "⚠️ File not found for counting: " + filename);
                return 0;
            }
            
            long fileSize = jsonFile.length();
            if (fileSize == 0) {
                Log.w(TAG, "⚠️ File is empty: " + filename);
                return 0;
            }
            
            Log.d(TAG, "📋 Counting items in " + filename + " (size: " + (fileSize / 1024) + " KB)");

            try (JsonReader reader = new JsonReader(
                    new InputStreamReader(new FileInputStream(jsonFile), StandardCharsets.UTF_8))) {
                
                if ("BEGIN_ARRAY".equals(reader.peek().toString())) {
                    // data.json 格式：根是 ARRAY，每個元素就是一項
                    Log.d(TAG, "  📊 JSON root is ARRAY format (data.json style)");
                    reader.beginArray();
                    while (reader.hasNext()) {
                        reader.beginObject();
                        totalCount++;  // 計算陣列中的每個對象
                        while (reader.hasNext()) {
                            reader.skipValue();
                        }
                        reader.endObject();
                    }
                    reader.endArray();
                } else {
                    // OBJECT 格式：包含 categories
                    Log.d(TAG, "  📊 JSON root is OBJECT format");
                    reader.beginObject();
                    
                    while (reader.hasNext()) {
                        String topKey = reader.nextName();
                        
                        if ("categories".equals(topKey)) {
                            reader.beginArray();
                            while (reader.hasNext()) {
                                reader.beginObject();
                                totalCount += countCategoryItems(reader);
                                reader.endObject();
                            }
                            reader.endArray();
                        } else {
                            reader.skipValue();
                        }
                    }
                    reader.endObject();
                }
            }
            
            Log.d(TAG, "✅ Counted " + totalCount + " items in " + filename);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error counting items in " + filename, e);
        }
        return totalCount;
    }

    /**
     * 計算單個分類中的項目數（支援 folders/files 和 images）
     */
    private int countCategoryItems(JsonReader reader) throws IOException {
        int count = 0;
        
        while (reader.hasNext()) {
            String key = reader.nextName();
            
            if ("folders".equals(key)) {
                // 有 folders 結構
                reader.beginArray();
                while (reader.hasNext()) {
                    reader.beginObject();
                    count += countFolderItems(reader);
                    reader.endObject();
                }
                reader.endArray();
            } else if ("images".equals(key)) {
                // 直接有 images（comic_data/photo_data 可能用此格式）
                reader.beginArray();
                while (reader.hasNext()) {
                    reader.skipValue();
                    count++;
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        
        return count;
    }

    /**
     * 計算單個資料夾中的項目數
     */
    private int countFolderItems(JsonReader reader) throws IOException {
        int count = 0;
        
        while (reader.hasNext()) {
            String key = reader.nextName();
            
            if ("files".equals(key)) {
                // MP3 和圖書用 files
                reader.beginArray();
                while (reader.hasNext()) {
                    reader.skipValue();
                    count++;
                }
                reader.endArray();
            } else if ("images".equals(key)) {
                // 漫畫和圖片可能用 images
                reader.beginArray();
                while (reader.hasNext()) {
                    reader.skipValue();
                    count++;
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        
        return count;
    }

    /**
     * 更新儀表板顯示
     */
    private void updateDashboard() {
        int total = totalMp3s + totalBooks + totalComics + totalPhotos;
        String stats = String.format(
                "📊 統計\n🎵 %d 首歌曲  📚 %d 本書籍  🎭 %d 部漫畫  📸 %d 張照片\n總計：%d 項",
                totalMp3s, totalBooks, totalComics, totalPhotos, total
        );
        statsText.setText(stats);
    }

    /**
     * 設置搜索功能（跨所有類別搜索）
     */
    private void setupSearchFunctionality() {
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String rawQuery = s == null ? "" : s.toString();
                String normalizedQuery = normalizeSearchQuery(rawQuery);

                if (isNotEmpty(normalizedQuery)) {
                    performGlobalSearch(normalizedQuery);
                } else {
                    latestNormalizedSearchQuery = "";
                    latestSearchRequestId++;
                    latestSearchResults.clear();
                    activeSearchFilter = SEARCH_FILTER_ALL;
                    currentSearchRenderLimit = MAX_RENDERED_SEARCH_RESULTS;
                    searchResultsContainer.removeAllViews();
                    dashboardContainer.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    /**
     * 全局搜索 - 跨所有媒體類別搜索
     */
    private void performGlobalSearch(String query) {
        String normalizedQuery = normalizeSearchQuery(query);
        if (!isNotEmpty(normalizedQuery)) {
            latestNormalizedSearchQuery = "";
            latestSearchRequestId++;
            runOnUiThread(() -> {
                latestSearchResults.clear();
                activeSearchFilter = SEARCH_FILTER_ALL;
                currentSearchRenderLimit = MAX_RENDERED_SEARCH_RESULTS;
                searchResultsContainer.removeAllViews();
                dashboardContainer.setVisibility(View.VISIBLE);
            });
            return;
        }

        if (normalizedQuery.equals(latestNormalizedSearchQuery)) {
            return;
        }

        latestNormalizedSearchQuery = normalizedQuery;
        final int requestId = ++latestSearchRequestId;
        final String finalQuery = normalizedQuery;
        Log.e(TAG, "SEARCH_REQUEST_START | id=" + requestId + " | query=\"" + finalQuery + "\"");

        new Thread(() -> {
            List<Article> results = new ArrayList<>();

            // 搜索 MP3
            results.addAll(searchInJson("mp3_data.json", finalQuery, "🎵"));

            // 搜索 Books
            results.addAll(searchInJson("data.json", finalQuery, "📚"));

            // 搜索 Comics
            results.addAll(searchInJson("comic_data.json", finalQuery, "🎭"));

            // 搜索 Photos
            results.addAll(searchInJson("photo_data.json", finalQuery, "📸"));

            if (requestId != latestSearchRequestId) {
                Log.d(TAG, "🧹 Drop stale search result id=" + requestId + " latest=" + latestSearchRequestId
                        + " query=\"" + finalQuery + "\"");
                return;
            }

            runOnUiThread(() -> {
                if (requestId != latestSearchRequestId) {
                    Log.d(TAG, "🧹 Drop stale search result on UI id=" + requestId + " latest=" + latestSearchRequestId
                            + " query=\"" + finalQuery + "\"");
                    return;
                }
                Log.e(TAG, "SEARCH_REQUEST_APPLY | id=" + requestId + " | query=\"" + finalQuery
                        + "\" | results=" + results.size());
                latestSearchResults.clear();
                latestSearchResults.addAll(results);
                activeSearchFilter = SEARCH_FILTER_ALL;
                currentSearchRenderLimit = MAX_RENDERED_SEARCH_RESULTS;
                displaySearchResults(getFilteredSearchResults());
            });
        }).start();
    }

    /**
     * 在 JSON 文件中搜索（支援 ARRAY、folders/files、images 等多種格式）
     */
    private List<Article> searchInJson(String filename, String query, String icon) {
        List<Article> results = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        int fileCount = 0;
        
        try {
            File jsonFile = new File(getExternalFilesDir("Downloads"), filename);
            if (!jsonFile.exists()) {
                Log.w(TAG, "⚠️ Search: File not found: " + filename);
                return results;
            }

            try (JsonReader reader = new JsonReader(
                    new InputStreamReader(new FileInputStream(jsonFile), StandardCharsets.UTF_8))) {
                
                if ("BEGIN_ARRAY".equals(reader.peek().toString())) {
                    // data.json：ARRAY 格式，每個元素就是一項
                    Log.d(TAG, "🔍 Search JSON root is ARRAY format in " + filename);
                    reader.beginArray();
                    
                    while (reader.hasNext()) {
                        reader.beginObject();
                        String itemName = "";
                        String bookAuthor = "";
                        String bookContent = "";
                        String bookTag = "General";
                        
                        while (reader.hasNext()) {
                            String key = reader.nextName();
                            
                            if ("name".equals(key) || "title".equals(key)) {
                                itemName = readReaderStringSafely(reader);
                            } else if ("author".equals(key)) {
                                bookAuthor = readReaderStringSafely(reader);
                            } else if ("content".equals(key)) {
                                bookContent = readReaderStringSafely(reader);
                            } else if ("tag".equals(key)) {
                                bookTag = readTagTextSafely(reader);
                            } else {
                                reader.skipValue();
                            }
                        }
                        reader.endObject();

                        boolean itemMatched = isNotEmpty(itemName) && itemName.toLowerCase().contains(lowerQuery);
                        if (itemMatched) {
                            Article article = new Article(
                                    icon + " " + itemName,
                                    filename,
                                    "item",
                                    "",
                                    "",
                                    itemName
                            );
                            article.bookAuthor = bookAuthor;
                            article.bookContent = bookContent;
                            article.bookTag = bookTag;
                            article.tag = bookTag;
                            article.content = bookContent;
                            article.activityName = resolveActivityName(article);
                            results.add(article);
                            fileCount++;
                        }
                    }
                    reader.endArray();
                } else {
                    // OBJECT 格式：包含 categories
                    Log.d(TAG, "🔍 Search JSON root is OBJECT format in " + filename);
                    reader.beginObject();
                    
                    while (reader.hasNext()) {
                        String key = reader.nextName();
                        
                        if ("categories".equals(key)) {
                            reader.beginArray();
                            while (reader.hasNext()) {
                                reader.beginObject();
                                String categoryName = "";
                                List<Article> categoryScopedResults = new ArrayList<>();
                                int categoryMatchedFileCount = 0;
                                
                                while (reader.hasNext()) {
                                    String catKey = reader.nextName();
                                    
                                    if ("name".equals(catKey)) {
                                        categoryName = readReaderStringSafely(reader);
                                        if (isNotEmpty(categoryName)
                                                && categoryName.toLowerCase().contains(lowerQuery)) {
                                            Article article = new Article(
                                                    icon + " " + categoryName,
                                                    filename,
                                                    "category",
                                                    categoryName,
                                                    "",
                                                    ""
                                            );
                                            article.activityName = resolveActivityName(article);
                                            categoryScopedResults.add(article);
                                        }
                                    } else if ("folders".equals(catKey)) {
                                        categoryMatchedFileCount += searchFolders(
                                                reader, lowerQuery, icon, filename, categoryName, categoryScopedResults);
                                    } else if ("images".equals(catKey)) {
                                        // 分類下直接有 images（photo_data 可能用此格式）
                                        categoryMatchedFileCount += searchImageArray(
                                                reader, lowerQuery, icon, categoryName, categoryScopedResults);
                                    } else {
                                        reader.skipValue();
                                    }
                                }
                                reader.endObject();

                                if (isNotEmpty(categoryName)) {
                                    for (Article article : categoryScopedResults) {
                                        if (!isNotEmpty(article.category)) {
                                            article.category = categoryName;
                                        }
                                        if (!isNotEmpty(article.activityName)) {
                                            article.activityName = resolveActivityName(article);
                                        }
                                        if ("folder".equals(article.type) && article.title.contains("()")) {
                                            article.title = article.title.replace("()", "(" + categoryName + ")");
                                        }
                                    }
                                }

                                results.addAll(categoryScopedResults);
                                fileCount += categoryMatchedFileCount;
                            }
                            reader.endArray();
                        } else {
                            reader.skipValue();
                        }
                    }
                    reader.endObject();
                }
            }
            
            Log.d(TAG, "🔍 Search in " + filename + " for \"" + query + "\": found " + results.size() + " results (" + fileCount + " files)");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error searching " + filename, e);
        }
        return results;
    }

    /**
     * 搜索資料夾結構（支援 files 和 images）
     */
    private int searchFolders(JsonReader reader, String lowerQuery, String icon, String filename,
                             String categoryName,
                             List<Article> results) throws IOException {
        int count = 0;
        reader.beginArray();
        
        while (reader.hasNext()) {
            reader.beginObject();
            String folderName = "";
            boolean folderMatched = false;
            List<String> matchedFileTitles = new ArrayList<>();
            
            while (reader.hasNext()) {
                String folderKey = reader.nextName();
                
                if ("name".equals(folderKey)) {
                    folderName = readReaderStringSafely(reader);
                    folderMatched = isNotEmpty(folderName) && folderName.toLowerCase().contains(lowerQuery);
                } else if ("files".equals(folderKey)) {
                    count += searchFileArray(reader, lowerQuery, matchedFileTitles);
                } else if ("images".equals(folderKey)) {
                    count += searchImageArray(reader, lowerQuery, icon, folderName, results);
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();

            if (folderMatched) {
                Article article = new Article(
                        icon + " " + folderName + " (" + categoryName + ")",
                        filename,
                        "folder",
                        categoryName,
                        folderName,
                        ""
                );
                article.activityName = resolveActivityName(article);
                results.add(article);
            }

            for (String title : matchedFileTitles) {
                Article article = new Article(
                        icon + " " + title + " (" + folderName + ")",
                        filename,
                        "file",
                        categoryName,
                        folderName,
                        title
                );
                article.activityName = resolveActivityName(article);
                results.add(article);
            }
        }
        reader.endArray();
        
        return count;
    }

    /**
     * 搜索 files 陣列
     */
    private int searchFileArray(JsonReader reader, String lowerQuery,
                               List<String> matchedTitles) throws IOException {
        int count = 0;
        reader.beginArray();
        
        while (reader.hasNext()) {
            reader.beginObject();
            String title = "";
            
            while (reader.hasNext()) {
                String fileKey = reader.nextName();
                
                if ("title".equals(fileKey)) {
                    title = readReaderStringSafely(reader);
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();

            if (isNotEmpty(title) && title.toLowerCase().contains(lowerQuery)) {
                matchedTitles.add(title);
                count++;
            }
        }
        reader.endArray();
        
        return count;
    }

    /**
     * 搜索 images 陣列（圖片/漫畫的數據格式）
     */
    private int searchImageArray(JsonReader reader, String lowerQuery, String icon, String contextName,
                                List<Article> results) throws IOException {
        int count = 0;
        reader.beginArray();
        
        while (reader.hasNext()) {
            // images 通常是簡單字符串或 URL，直接跳過並計算
            reader.skipValue();
            count++;
        }
        reader.endArray();
        
        return count;
    }

    /**
     * 顯示搜索結果
     */
    private void displaySearchResults(List<Article> results) {
        searchResultsContainer.removeAllViews();
        dashboardContainer.setVisibility(View.GONE);

        int totalResults = latestSearchResults.size();
        int filteredResults = results.size();
        int renderCount = Math.min(filteredResults, currentSearchRenderLimit);
        boolean isTruncated = filteredResults > currentSearchRenderLimit;

        Log.e(TAG, "SEARCH_UI_RENDER | build=" + SEARCH_DIAG_BUILD
                + " | filter=" + activeSearchFilter
                + " | renderCount=" + renderCount
                + " | filtered=" + filteredResults
                + " | total=" + totalResults
                + " | query=\"" + latestNormalizedSearchQuery + "\"");
        Log.d(TAG, "📊 Displaying " + renderCount + " search results (filtered=" + filteredResults + ", total=" + totalResults + ")");

        boolean shouldShowSearchControls = isNotEmpty(latestNormalizedSearchQuery);
        if (!shouldShowSearchControls && !latestSearchResults.isEmpty()) {
            shouldShowSearchControls = true;
            Log.w(TAG, "SEARCH_FILTER_UI_RECOVER | query empty but results exist, force-show controls");
        }

        if (shouldShowSearchControls) {
            TextView resultCountHeader = new TextView(this);
            resultCountHeader.setText(buildSearchResultHeader(totalResults, filteredResults, renderCount, isTruncated));
            resultCountHeader.setPadding(16, 16, 16, 8);
            resultCountHeader.setTextColor(getResources().getColor(android.R.color.black));
            resultCountHeader.setTextSize(14);
            searchResultsContainer.addView(resultCountHeader);

            addSearchFilterButtons();
        }

        if (results.isEmpty()) {
            TextView noResults = new TextView(this);
            noResults.setText("❌ 未找到結果");
            noResults.setPadding(16, 16, 16, 16);
            noResults.setTextColor(getResources().getColor(android.R.color.darker_gray));
            searchResultsContainer.addView(noResults);
            return;
        }

        for (int i = 0; i < renderCount; i++) {
            Article result = results.get(i);
            final int displayIndex = i + 1;
            final int totalCount = filteredResults;
            MaterialCardView card = new MaterialCardView(this);
            card.setCardElevation(4);
            card.setCardBackgroundColor(getResources().getColor(android.R.color.white));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(8, 8, 8, 8);
            card.setLayoutParams(params);

            LinearLayout cardContent = new LinearLayout(this);
            cardContent.setOrientation(LinearLayout.VERTICAL);
            cardContent.setPadding(16, 12, 16, 12);

            TextView textView = new TextView(this);
            textView.setText(buildHighlightedSearchTitle(result.title));
            textView.setTextSize(14);
            cardContent.addView(textView);

            TextView typeView = new TextView(this);
            typeView.setText("類型: " + result.type);
            typeView.setTextSize(12);
            typeView.setTextColor(getResources().getColor(android.R.color.darker_gray));
            typeView.setPadding(0, 8, 0, 0);
            cardContent.addView(typeView);

            card.addView(cardContent);
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnTouchListener((v, event) -> {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    hideKeyboardAndClearSearchFocus();
                    ViewParent parent = v.getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    ViewParent parent = v.getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(false);
                    }
                }

                if (action == MotionEvent.ACTION_DOWN
                        || action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL) {
                    Log.e(TAG, "SEARCH_TOUCH_" + motionActionToText(action)
                            + " | index=" + displayIndex + "/" + totalCount
                            + " | title=" + result.title);
                }
                return false;
            });
            card.setOnClickListener(v -> {
                Log.e(TAG, "SEARCH_CLICK_DETECTED | index=" + displayIndex + "/" + totalCount
                        + " | title=" + result.title);
                logSearchTitleResultClick(result, displayIndex, totalCount);
                handleSearchResultClick(result);
            });
            searchResultsContainer.addView(card);
        }

        if (isTruncated) {
            final int nextRenderLimit = Math.min(filteredResults, currentSearchRenderLimit + MAX_RENDERED_SEARCH_RESULTS);
            Button loadMoreButton = new Button(this);
            loadMoreButton.setAllCaps(false);
            loadMoreButton.setText("載入更多（" + renderCount + "/" + filteredResults + "）");

            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            buttonParams.setMargins(16, 8, 16, 16);
            loadMoreButton.setLayoutParams(buttonParams);
            loadMoreButton.setOnClickListener(v -> {
                currentSearchRenderLimit = nextRenderLimit;
                Log.e(TAG, "SEARCH_LOAD_MORE | nextRenderLimit=" + currentSearchRenderLimit
                        + " | filtered=" + filteredResults
                        + " | filter=" + activeSearchFilter);
                displaySearchResults(getFilteredSearchResults());
            });
            searchResultsContainer.addView(loadMoreButton);
        }
    }

    private void addSearchFilterButtons() {
        TextView filterLabel = new TextView(this);
        filterLabel.setText("篩選：");
        filterLabel.setTextSize(13);
        filterLabel.setTypeface(null, Typeface.BOLD);
        filterLabel.setTextColor(getResources().getColor(android.R.color.black));
        filterLabel.setPadding(16, 0, 16, 8);
        searchResultsContainer.addView(filterLabel);

        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(16, 0, 16, 8);
        filterRow.setBackgroundColor(getResources().getColor(android.R.color.transparent));

        addSearchFilterButton(filterRow, SEARCH_FILTER_ALL);
        addSearchFilterButton(filterRow, SEARCH_FILTER_MP3);
        addSearchFilterButton(filterRow, SEARCH_FILTER_BOOK);
        addSearchFilterButton(filterRow, SEARCH_FILTER_COMIC);
        addSearchFilterButton(filterRow, SEARCH_FILTER_PHOTO);

        filterScroll.addView(filterRow);
        Log.e(TAG, "SEARCH_FILTER_UI_SHOWN | filter=" + activeSearchFilter
                + " | total=" + latestSearchResults.size());
        searchResultsContainer.addView(filterScroll);
    }

    private void addSearchFilterButton(LinearLayout parent, String filterKey) {
        int matchedCount = countSearchResultsByFilter(filterKey);
        Button filterButton = new Button(this);
        filterButton.setAllCaps(false);
        filterButton.setText(getSearchFilterLabel(filterKey) + " (" + matchedCount + ")");

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        buttonParams.setMargins(0, 0, 12, 0);
        filterButton.setLayoutParams(buttonParams);

        boolean isActiveFilter = filterKey.equals(activeSearchFilter);
        applySearchFilterButtonStyle(filterButton, isActiveFilter);
        filterButton.setOnClickListener(v -> {
            if (filterKey.equals(activeSearchFilter)) {
                return;
            }
            activeSearchFilter = filterKey;
            currentSearchRenderLimit = MAX_RENDERED_SEARCH_RESULTS;
            List<Article> filteredResults = getFilteredSearchResults();
            Log.e(TAG, "SEARCH_FILTER_APPLY | filter=" + filterKey + " | count=" + filteredResults.size());
            displaySearchResults(filteredResults);
        });

        parent.addView(filterButton);
    }

    private void applySearchFilterButtonStyle(Button button, boolean isActive) {
        int activeBackground = ContextCompat.getColor(this, android.R.color.holo_orange_dark);
        int activeTextColor = ContextCompat.getColor(this, android.R.color.white);
        int inactiveBackground = ContextCompat.getColor(this, android.R.color.white);
        int inactiveTextColor = ContextCompat.getColor(this, android.R.color.black);

        if (isActive) {
            button.setBackgroundTintList(ColorStateList.valueOf(activeBackground));
            button.setTextColor(activeTextColor);
            button.setTypeface(null, Typeface.BOLD);
        } else {
            button.setBackgroundTintList(ColorStateList.valueOf(inactiveBackground));
            button.setTextColor(inactiveTextColor);
            button.setTypeface(null, Typeface.NORMAL);
        }
    }

    private CharSequence buildHighlightedSearchTitle(String title) {
        String safeTitle = title == null ? "" : title;
        if (!isNotEmpty(safeTitle) || !isNotEmpty(latestNormalizedSearchQuery)) {
            return safeTitle;
        }

        String lowerTitle = safeTitle.toLowerCase(Locale.ROOT);
        String[] queryKeywords = latestNormalizedSearchQuery.toLowerCase(Locale.ROOT).split("\\s+");
        if (queryKeywords.length == 0) {
            return safeTitle;
        }

        SpannableString highlightedTitle = new SpannableString(safeTitle);
        int highlightColor = ContextCompat.getColor(this, android.R.color.holo_blue_dark);
        boolean hasMatch = false;

        for (String lowerQuery : queryKeywords) {
            if (!isNotEmpty(lowerQuery)) {
                continue;
            }

            int searchStart = 0;
            while (searchStart < lowerTitle.length()) {
                int matchedIndex = lowerTitle.indexOf(lowerQuery, searchStart);
                if (matchedIndex < 0) {
                    break;
                }

                int matchedEnd = matchedIndex + lowerQuery.length();
                highlightedTitle.setSpan(new ForegroundColorSpan(highlightColor),
                        matchedIndex,
                        matchedEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                highlightedTitle.setSpan(new StyleSpan(Typeface.BOLD),
                        matchedIndex,
                        matchedEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                hasMatch = true;
                searchStart = matchedEnd;
            }
        }

        if (!hasMatch) {
            return safeTitle;
        }

        return highlightedTitle;
    }

    private void hideKeyboardAndClearSearchFocus() {
        if (searchBox != null) {
            searchBox.clearFocus();
        }

        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager == null) {
            return;
        }

        View targetView = getCurrentFocus();
        if (targetView == null) {
            targetView = searchBox;
        }

        if (targetView != null && targetView.getWindowToken() != null) {
            inputMethodManager.hideSoftInputFromWindow(targetView.getWindowToken(), 0);
        }
    }

    private List<Article> getFilteredSearchResults() {
        if (SEARCH_FILTER_ALL.equals(activeSearchFilter)) {
            return new ArrayList<>(latestSearchResults);
        }

        List<Article> filteredResults = new ArrayList<>();
        for (Article article : latestSearchResults) {
            if (activeSearchFilter.equals(resolveSearchFilterKey(article))) {
                filteredResults.add(article);
            }
        }
        return filteredResults;
    }

    private int countSearchResultsByFilter(String filterKey) {
        if (SEARCH_FILTER_ALL.equals(filterKey)) {
            return latestSearchResults.size();
        }

        int count = 0;
        for (Article article : latestSearchResults) {
            if (filterKey.equals(resolveSearchFilterKey(article))) {
                count++;
            }
        }
        return count;
    }

    private String resolveSearchFilterKey(Article article) {
        if (article == null || !isNotEmpty(article.filename)) {
            return SEARCH_FILTER_ALL;
        }

        switch (article.filename) {
            case "mp3_data.json":
                return SEARCH_FILTER_MP3;
            case "data.json":
                return SEARCH_FILTER_BOOK;
            case "comic_data.json":
                return SEARCH_FILTER_COMIC;
            case "photo_data.json":
                return SEARCH_FILTER_PHOTO;
            default:
                return SEARCH_FILTER_ALL;
        }
    }

    private String getSearchFilterLabel(String filterKey) {
        switch (filterKey) {
            case SEARCH_FILTER_MP3:
                return "MP3";
            case SEARCH_FILTER_BOOK:
                return "書籍";
            case SEARCH_FILTER_COMIC:
                return "漫畫";
            case SEARCH_FILTER_PHOTO:
                return "照片";
            case SEARCH_FILTER_ALL:
            default:
                return "全部";
        }
    }

    private String buildSearchResultHeader(int totalResults, int filteredResults, int renderCount, boolean isTruncated) {
        StringBuilder headerBuilder = new StringBuilder();
        headerBuilder.append("🔍 找到 ").append(totalResults).append(" 個結果");

        if (!SEARCH_FILTER_ALL.equals(activeSearchFilter)) {
            headerBuilder.append("（").append(getSearchFilterLabel(activeSearchFilter))
                    .append("：").append(filteredResults).append(" 筆）");
        }

        if (isTruncated) {
            headerBuilder.append("（顯示前 ").append(renderCount).append(" 筆）");
        }

        return headerBuilder.toString();
    }

    /**
     * 點擊搜索結果後導航
     */
    private void handleSearchResultClick(Article result) {
        if (result == null) {
            Log.w(TAG, "⚠️ Clicked null search result");
            Toast.makeText(this, "❌ 搜索結果無效", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.e(TAG, "SEARCH_CLICK_HANDLE_START | title=" + result.title
                + " | type=" + result.type
                + " | activityName=" + result.activityName
                + " | file=" + result.filename);
        if (isDebugBuild()) {
            String titlePreview = result.title == null ? "(null)" : result.title;
            if (titlePreview.length() > 24) {
                titlePreview = titlePreview.substring(0, 24) + "...";
            }
            Toast.makeText(this, "點擊: " + titlePreview, Toast.LENGTH_SHORT).show();
        }

        logClickAction("search_result",
                "title=" + result.title
                        + " | type=" + result.type
                + " | activityName=" + result.activityName
                        + " | filename=" + result.filename
                        + " | category=" + result.category
                        + " | folder=" + result.folder
                        + " | item=" + result.itemName);

        if (!isNotEmpty(result.filename)) {
            Toast.makeText(this, "❌ 搜索結果缺少來源資訊", Toast.LENGTH_SHORT).show();
            return;
        }

        File cacheFile = new File(getExternalFilesDir("Downloads"), result.filename);

        if (cacheFile.exists() && !downloadingFiles.contains(result.filename)) {
            Log.d(TAG, "🧭 Search source file ready, direct navigation: " + result.filename);
            navigateToSearchResult(result);
            return;
        }

        if (downloadingFiles.contains(result.filename)) {
            pendingSearchResult = result;
            Log.d(TAG, "⏳ Search source is downloading, set pending route for: " + result.filename);
            Toast.makeText(this, "正在下載搜索數據,完成後將自動跳轉...", Toast.LENGTH_LONG).show();
            return;
        }

        String url = getDownloadUrl(result.filename);
        if (url != null) {
            pendingSearchResult = result;
            Log.d(TAG, "📥 Search source missing, start download then route: " + result.filename);
            ensureFile(result.filename, url);

            File refreshedFile = new File(getExternalFilesDir("Downloads"), result.filename);
            if (refreshedFile.exists() && !downloadingFiles.contains(result.filename)) {
                Log.d(TAG, "✅ Search source ready immediately after ensureFile, navigate now: " + result.filename);
                pendingSearchResult = null;
                navigateToSearchResult(result);
                return;
            }

            Toast.makeText(this, "正在下載搜索數據,完成後將自動跳轉...", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "❌ 無法定位搜索結果來源", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 記錄「點擊搜索結果標題」的詳細資訊，便於追蹤點擊與導頁
     */
    private void logSearchTitleResultClick(Article result, int displayIndex, int totalCount) {
        if (result == null) {
            Log.w(TAG, "⚠️ SearchTitleResultClick with null result");
            return;
        }

        int contentLength = isNotEmpty(result.bookContent) ? result.bookContent.length() : 0;
        String logMessage =
                "🧩 SearchTitleResultClick"
                        + " | index=" + displayIndex + "/" + totalCount
                        + " | title=" + result.title
                        + " | type=" + result.type
                    + " | activityName=" + result.activityName
                        + " | filename=" + result.filename
                        + " | category=" + result.category
                        + " | folder=" + result.folder
                        + " | item=" + result.itemName
                        + " | bookAuthor=" + result.bookAuthor
                        + " | bookTag=" + result.bookTag
                        + " | bookContentLength=" + contentLength;
        Log.e(TAG, "SEARCH_CLICK_TRACE | " + logMessage);
        Log.d(TAG, logMessage);
    }

    private boolean isDebugBuild() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private String motionActionToText(int action) {
        if (action == MotionEvent.ACTION_DOWN) {
            return "DOWN";
        }
        if (action == MotionEvent.ACTION_UP) {
            return "UP";
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            return "CANCEL";
        }
        return "OTHER_" + action;
    }

    /**
     * 根據搜索結果精準導航
     */
    private void navigateToSearchResult(Article result) {
        File jsonFile = new File(getExternalFilesDir("Downloads"), result.filename);
        if (!jsonFile.exists()) {
            Toast.makeText(this, "❌ 數據文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isNotEmpty(result.activityName)) {
            result.activityName = resolveActivityName(result);
        }

        Log.d(TAG,
                "🧭 Resolving search route: type=" + result.type
                        + " | file=" + result.filename
                        + " | category=" + result.category
                        + " | folder=" + result.folder
                        + " | item=" + result.itemName
                        + " | activityName=" + result.activityName);

        if ("BookDetailActivity".equals(result.activityName)
                && isNotEmpty(result.itemName)
                && openBookDetailFromSearchResult(result)) {
            return;
        }

        if ("BookDetailActivity".equals(result.activityName)
                && isNotEmpty(result.itemName)
                && openBookDetailByName(jsonFile, result.itemName)) {
            return;
        }

        if ("mp3_data.json".equals(result.filename)
                && "file".equals(result.type)
                && isNotEmpty(result.category)
                && isNotEmpty(result.folder)
                && isNotEmpty(result.itemName)) {
            if (openMp3DetailByTitle(jsonFile, result)) {
                return;
            }
            Log.w(TAG, "⚠️ MP3 direct-open failed, fallback to RadioListActivity");
            result.activityName = "RadioListActivity";
        }

        if (!launchMappedSearchActivity(result, jsonFile)) {
            Toast.makeText(this, "❌ 尚未支援的搜索類型", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 由搜索結果已攜帶的書籍資料直接開啟詳情（避免二次解析失敗）
     */
    private boolean openBookDetailFromSearchResult(Article result) {
        if (result == null || !isNotEmpty(result.itemName)) {
            return false;
        }

        if (!isNotEmpty(result.bookAuthor)
                && !isNotEmpty(result.bookContent)
                && !isNotEmpty(result.bookTag)) {
            return false;
        }

        Intent intent = new Intent(this, BookDetailActivity.class);
        intent.putExtra("title", result.itemName);
        intent.putExtra("name", result.itemName);
        intent.putExtra("author", isNotEmpty(result.bookAuthor) ? result.bookAuthor : "Unknown");
        intent.putExtra("content", isNotEmpty(result.bookContent)
                ? result.bookContent.replace("\\n", "\n")
                : "");
        intent.putExtra("tag", isNotEmpty(result.bookTag) ? result.bookTag : "General");
        logNavigationDirection("BookDetailActivity", "search:book-item-cached", result, intent);
        startActivity(intent);
        return true;
    }

    /**
     * 依書名直接開啟書籍詳情
     */
    private boolean openBookDetailByName(File jsonFile, String targetBookName) {
        try (JsonReader reader = new JsonReader(
                new InputStreamReader(new FileInputStream(jsonFile), StandardCharsets.UTF_8))) {

            reader.beginArray();
            while (reader.hasNext()) {
                reader.beginObject();

                String name = "";
                String title = "";
                String author = "";
                String content = "";
                String tagText = "General";

                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if ("name".equals(key)) {
                        name = readReaderStringSafely(reader);
                    } else if ("title".equals(key)) {
                        title = readReaderStringSafely(reader);
                    } else if ("author".equals(key)) {
                        author = readReaderStringSafely(reader);
                    } else if ("content".equals(key)) {
                        content = readReaderStringSafely(reader);
                    } else if ("tag".equals(key)) {
                        tagText = readTagTextSafely(reader);
                    } else {
                        reader.skipValue();
                    }
                }

                reader.endObject();

                if (isBookNameMatched(targetBookName, name, title)) {
                    String resolvedName = isNotEmpty(name) ? name : title;

                    Intent intent = new Intent(this, BookDetailActivity.class);
                    intent.putExtra("title", resolvedName);
                    intent.putExtra("name", resolvedName);
                    intent.putExtra("author", author);
                    intent.putExtra("content", content != null ? content.replace("\\n", "\n") : "");
                    intent.putExtra("tag", isNotEmpty(tagText) ? tagText : "General");
                    logNavigationDirection("BookDetailActivity", "search:book-item", null, intent);
                    startActivity(intent);
                    return true;
                }
            }
            reader.endArray();
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to open book detail by name: " + targetBookName, e);
        }

        return false;
    }

    /**
     * 依標題直接開啟 MP3 詳情（命中檔案時）
     */
    private boolean openMp3DetailByTitle(File jsonFile, Article result) {
        if (result == null
                || !isNotEmpty(result.category)
                || !isNotEmpty(result.folder)
                || !isNotEmpty(result.itemName)) {
            return false;
        }

        try {
            StringBuilder jsonBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(jsonFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBuilder.append(line);
                }
            }

            JSONObject root = new JSONObject(jsonBuilder.toString());
            JSONArray categories = root.optJSONArray("categories");
            if (categories == null) {
                return false;
            }

            for (int i = 0; i < categories.length(); i++) {
                JSONObject category = categories.optJSONObject(i);
                if (category == null) {
                    continue;
                }

                if (!result.category.equals(category.optString("name"))) {
                    continue;
                }

                JSONArray folders = category.optJSONArray("folders");
                if (folders == null) {
                    return false;
                }

                for (int j = 0; j < folders.length(); j++) {
                    JSONObject folder = folders.optJSONObject(j);
                    if (folder == null) {
                        continue;
                    }

                    if (!result.folder.equals(folder.optString("name"))) {
                        continue;
                    }

                    JSONArray files = folder.optJSONArray("files");
                    if (files == null || files.length() == 0) {
                        return false;
                    }

                    ArrayList<String> allTitles = new ArrayList<>();
                    ArrayList<String> allUrls = new ArrayList<>();

                    String matchedTitle = null;
                    String matchedUrl = null;

                    for (int k = 0; k < files.length(); k++) {
                        JSONObject fileObj = files.optJSONObject(k);
                        if (fileObj == null) {
                            continue;
                        }

                        String title = fileObj.optString("title");
                        String url = fileObj.optString("path");

                        allTitles.add(title);
                        allUrls.add(url);

                        if (matchedUrl == null && result.itemName.equals(title)) {
                            matchedTitle = title;
                            matchedUrl = url;
                        }
                    }

                    if (matchedUrl == null) {
                        for (int k = 0; k < allTitles.size(); k++) {
                            String title = allTitles.get(k);
                            if (isNotEmpty(title)
                                    && title.toLowerCase().contains(result.itemName.toLowerCase())) {
                                matchedTitle = title;
                                matchedUrl = allUrls.get(k);
                                break;
                            }
                        }
                    }

                    if (!isNotEmpty(matchedUrl)) {
                        Log.w(TAG, "⚠️ MP3 direct-open target not found: " + result.itemName);
                        return false;
                    }

                    Intent intent = new Intent(this, RadioDetailActivity.class);
                    intent.putExtra("title", matchedTitle != null ? matchedTitle : result.itemName);
                    intent.putExtra("url", matchedUrl);
                    intent.putStringArrayListExtra("allUrls", allUrls);
                    intent.putStringArrayListExtra("allTitles", allTitles);
                    logNavigationDirection("RadioDetailActivity", "search:mp3-file-direct", result, intent);
                    startActivity(intent);
                    return true;
                }

                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to open MP3 detail by title: " + result.itemName, e);
        }

        return false;
    }

    private boolean isNotEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalizeSearchQuery(String query) {
        if (query == null) {
            return "";
        }

        return query
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void initializeSearchActivityMap() {
        searchActivityMap.put("BookDetailActivity", BookDetailActivity.class);
        searchActivityMap.put("BookActivity", BookActivity.class);
        searchActivityMap.put("RadioDetailActivity", RadioDetailActivity.class);
        searchActivityMap.put("RadioListActivity", RadioListActivity.class);
        searchActivityMap.put("RadioFolderActivity", RadioFolderActivity.class);
        searchActivityMap.put("RadioCategoryActivity", RadioCategoryActivity.class);
        searchActivityMap.put("ComicListActivity", ComicListActivity.class);
        searchActivityMap.put("ComicFolderActivity", ComicFolderActivity.class);
        searchActivityMap.put("ComicCategoryActivity", ComicCategoryActivity.class);
        searchActivityMap.put("PhotoListActivity", PhotoListActivity.class);
        searchActivityMap.put("PhotoFolderActivity", PhotoFolderActivity.class);
        searchActivityMap.put("PhotoCategoryActivity", PhotoCategoryActivity.class);
    }

    private String resolveActivityName(Article article) {
        if (article == null || !isNotEmpty(article.filename)) {
            return "";
        }

        switch (article.filename) {
            case "data.json":
                return "item".equals(article.type) ? "BookDetailActivity" : "BookActivity";
            case "mp3_data.json":
                if ("file".equals(article.type)) {
                    return "RadioListActivity";
                }
                if ("folder".equals(article.type)) {
                    return "RadioListActivity";
                }
                if ("category".equals(article.type)) {
                    return "RadioFolderActivity";
                }
                return "RadioCategoryActivity";
            case "comic_data.json":
                if ("file".equals(article.type)) {
                    return "ComicListActivity";
                }
                if ("folder".equals(article.type)) {
                    return "ComicFolderActivity";
                }
                if ("category".equals(article.type)) {
                    return "ComicCategoryActivity";
                }
                return "ComicCategoryActivity";
            case "photo_data.json":
                if ("file".equals(article.type)) {
                    return "PhotoListActivity";
                }
                if ("folder".equals(article.type)) {
                    return "PhotoFolderActivity";
                }
                if ("category".equals(article.type)) {
                    return "PhotoCategoryActivity";
                }
                return "PhotoCategoryActivity";
            default:
                return "";
        }
    }

    private Class<?> resolveActivityClass(Article article) {
        if (article == null) {
            return null;
        }

        if (!isNotEmpty(article.activityName)) {
            article.activityName = resolveActivityName(article);
        }

        if (!isNotEmpty(article.activityName)) {
            return null;
        }

        return searchActivityMap.get(article.activityName);
    }

    private boolean launchMappedSearchActivity(Article article, File jsonFile) {
        Class<?> targetActivity = resolveActivityClass(article);
        if (targetActivity == null) {
            return false;
        }

        if (targetActivity == RadioDetailActivity.class) {
            Log.w(TAG, "⚠️ Blocked invalid route: RadioDetailActivity needs url/title extras");
            return false;
        }

        Intent intent = new Intent(this, targetActivity);
        intent.putExtra("json_path", jsonFile.getAbsolutePath());

        if (targetActivity == BookDetailActivity.class) {
            intent.putExtra("title", article.itemName);
            intent.putExtra("name", article.itemName);
            intent.putExtra("author", isNotEmpty(article.bookAuthor) ? article.bookAuthor : "Unknown");
            intent.putExtra("content", isNotEmpty(article.bookContent)
                    ? article.bookContent.replace("\\n", "\n")
                    : article.content);
            intent.putExtra("tag", isNotEmpty(article.bookTag) ? article.bookTag : article.tag);
        } else if (targetActivity == RadioListActivity.class) {
            intent.putExtra("category", article.category);
            intent.putExtra("folder", article.folder);
        } else if (targetActivity == RadioFolderActivity.class) {
            intent.putExtra("category", article.category);
        } else if (targetActivity == ComicListActivity.class || targetActivity == PhotoListActivity.class) {
            intent.putExtra("category_name", article.category);
            intent.putExtra("folder_name", article.folder);
        } else if (targetActivity == ComicFolderActivity.class || targetActivity == PhotoFolderActivity.class) {
            intent.putExtra("category_name", article.category);
            if (isNotEmpty(article.folder)) {
                intent.putExtra("folder_name", article.folder);
            }
        }

        logNavigationDirection(targetActivity.getSimpleName(), "search:mapped-activity", article, intent);
        startActivity(intent);
        return true;
    }

    private String readReaderStringSafely(JsonReader reader) throws IOException {
        String token = reader.peek().toString();
        if ("NULL".equals(token)) {
            reader.nextNull();
            return "";
        }
        if ("STRING".equals(token) || "NUMBER".equals(token)) {
            return reader.nextString();
        }
        if ("BOOLEAN".equals(token)) {
            return String.valueOf(reader.nextBoolean());
        }
        reader.skipValue();
        return "";
    }

    private String readTagTextSafely(JsonReader reader) throws IOException {
        List<String> tags = new ArrayList<>();
        String token = reader.peek().toString();

        if ("BEGIN_ARRAY".equals(token)) {
        } else {
            String tag = readReaderStringSafely(reader);
            if (isNotEmpty(tag)) {
                tags.add(tag);
            }
        }

        if (tags.isEmpty()) {
            return "General";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(tags.get(i));
        }
        return builder.toString();
    }

    private boolean isBookNameMatched(String targetBookName, String name, String title) {
        String normalizedTarget = normalizeBookName(targetBookName);
        if (!isNotEmpty(normalizedTarget)) {
            return false;
        }

        String normalizedName = normalizeBookName(name);
        String normalizedTitle = normalizeBookName(title);

        if (normalizedTarget.equalsIgnoreCase(normalizedName)
                || normalizedTarget.equalsIgnoreCase(normalizedTitle)) {
            return true;
        }

        return (isNotEmpty(normalizedName) && normalizedName.toLowerCase().contains(normalizedTarget.toLowerCase()))
                || (isNotEmpty(normalizedTitle) && normalizedTitle.toLowerCase().contains(normalizedTarget.toLowerCase()));
    }

    private String normalizeBookName(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceFirst("^[\\p{So}\\p{Cntrl}\\s]+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 記錄點擊事件來源
     */
    private void logClickAction(String source, String detail) {
        Log.d(TAG, "🖱️ Click[" + source + "] " + detail);
    }

    /**
     * 記錄導頁方向與 extras
     */
    private void logNavigationDirection(String destination, String reason, Article result, Intent intent) {
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("🧭 Route[")
                .append(reason)
                .append("] -> ")
                .append(destination);

        if (result != null) {
            logBuilder.append(" | result={title=")
                    .append(result.title)
                    .append(", type=")
                    .append(result.type)
                    .append(", activityName=")
                    .append(result.activityName)
                    .append(", filename=")
                    .append(result.filename)
                    .append(", category=")
                    .append(result.category)
                    .append(", folder=")
                    .append(result.folder)
                    .append(", item=")
                    .append(result.itemName)
                    .append("}");
        }

        Bundle extras = intent != null ? intent.getExtras() : null;
        if (extras == null || extras.isEmpty()) {
            logBuilder.append(" | extras={}");
        } else {
            StringBuilder extrasBuilder = new StringBuilder();
            for (String key : extras.keySet()) {
                if (extrasBuilder.length() > 0) {
                    extrasBuilder.append(", ");
                }
                Object value = extras.get(key);
                extrasBuilder.append(key).append("=").append(value);
            }
            logBuilder.append(" | extras={").append(extrasBuilder).append("}");
        }

        Log.d(TAG, logBuilder.toString());
    }

    /**
     * 設置快速訪問卡片
     */
    private void setupQuickAccessCards() {
        addQuickAccessCard("🎵 MP3 / 音樂", "mp3_data.json", RadioCategoryActivity.class);
        addQuickAccessCard("📚 書籍", "data.json", BookActivity.class);
        addQuickAccessCard("🎭 漫畫", "comic_data.json", ComicCategoryActivity.class);
        addQuickAccessCard("📸 照片", "photo_data.json", PhotoCategoryActivity.class);
        // OCP Quiz quick access
        addQuickAccessCard("📝 OCP Quiz", "index.html", OcpQuizActivity.class);
    }

    /**
     * 添加快速訪問卡片
     */
    private void addQuickAccessCard(String title, String filename, Class<?> targetActivity) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardElevation(4);
        card.setClickable(true);
        card.setFocusable(true);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(8, 8, 8, 8);
        card.setLayoutParams(params);

        TextView textView = new TextView(this);
        textView.setText(title);
        textView.setPadding(16, 24, 16, 24);
        textView.setTextSize(16);
        textView.setTypeface(null, android.graphics.Typeface.BOLD);
        textView.setGravity(android.view.Gravity.CENTER);

        card.addView(textView);
        card.setOnClickListener(v -> {
            logClickAction("quick_access", title + " | filename=" + filename);
            navigateToCategory(filename, targetActivity, title);
        });

        quickAccessGrid.addView(card);
    }

    /**
     * 導航到特定類別
     */
    private void navigateToCategory(String filename, Class<?> targetActivity, String label) {
        File cacheFile = new File(getExternalFilesDir("Downloads"), filename);
        if (cacheFile.exists() && !downloadingFiles.contains(filename)) {
            // File exists and not currently downloading, navigate immediately
            Intent intent = new Intent(this, targetActivity);
            intent.putExtra("json_path", cacheFile.getAbsolutePath());
            logNavigationDirection(targetActivity.getSimpleName(), "category-direct:" + label, null, intent);
            startActivity(intent);
        } else if (downloadingFiles.contains(filename)) {
            // Already downloading, set up auto-navigation
            pendingNavigationFile = filename;
            pendingNavigationActivity = targetActivity;
            Log.d(TAG, "⏳ Category file is downloading, pending route set: " + filename + " -> " + targetActivity.getSimpleName());
            Toast.makeText(this, "正在下載 " + label + " 數據,下載完成後將自動打開...", Toast.LENGTH_LONG).show();
        } else {
            // File doesn't exist and not downloading, start download
            String url = getDownloadUrl(filename);
            if (url != null) {
                pendingNavigationFile = filename;
                pendingNavigationActivity = targetActivity;
                Log.d(TAG, "📥 Category file missing, trigger download: " + filename + " -> " + targetActivity.getSimpleName());
                ensureFile(filename, url);

                File refreshedFile = new File(getExternalFilesDir("Downloads"), filename);
                if (refreshedFile.exists() && !downloadingFiles.contains(filename)) {
                    Log.d(TAG, "✅ Category file ready immediately after ensureFile: " + filename);
                    Intent intent = new Intent(this, targetActivity);
                    intent.putExtra("json_path", refreshedFile.getAbsolutePath());
                    logNavigationDirection(targetActivity.getSimpleName(), "category-post-ensure:" + label, null, intent);
                    pendingNavigationFile = null;
                    pendingNavigationActivity = null;
                    startActivity(intent);
                    return;
                }

                Toast.makeText(this, "正在下載 " + label + " 數據,下載完成後將自動打開...", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    /**
     * 獲取文件的下載 URL
     */
    private String getDownloadUrl(String filename) {
        if (dataUrlManager == null) {
            dataUrlManager = new DataUrlManager(this);
        }

        switch (filename) {
            case "mp3_data.json":
                return dataUrlManager.getMp3DownloadUrl();
            case "data.json":
                return dataUrlManager.getBookDownloadUrl();
            case "comic_data.json":
                return dataUrlManager.getComicDownloadUrl();
            case "photo_data.json":
                return dataUrlManager.getPhotoDownloadUrl();
            case "index.html":
                return dataUrlManager.getOcpIndexDownloadUrl();
            case "questions-data.js":
                return dataUrlManager.getOcpQuestionsDownloadUrl();
            case "script.js":
                return dataUrlManager.getOcpScriptDownloadUrl();
            case "styles.css":
                return dataUrlManager.getOcpStyleDownloadUrl();
            default:
                return null;
        }
    }

}
