package com.example.datadisplay.managers;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.example.datadisplay.managers.OfflineResourceManager.ResourceType;
import com.example.datadisplay.utils.NetworkHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages batch downloads for offline access
 */
public class OfflineDownloadManager {

    private static final String TAG = "OfflineDownloadManager";
    private static final int MAX_CONCURRENT_DOWNLOADS = 3;
    private static final String PREF_DOWNLOADS = "ActiveDownloads";

    private final Context context;
    private final DownloadManager downloadManager;
    private final OfflineResourceManager resourceManager;
    private final SharedPreferences downloadPrefs;
    
    // Track download IDs to URLs
    private final Map<Long, DownloadInfo> activeDownloads = new ConcurrentHashMap<>();
    
    // Download listeners
    private final List<DownloadListener> listeners = new ArrayList<>();

    public OfflineDownloadManager(Context context) {
        this.context = context.getApplicationContext();
        this.downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        this.resourceManager = new OfflineResourceManager(context);
        this.downloadPrefs = context.getSharedPreferences(PREF_DOWNLOADS, Context.MODE_PRIVATE);
        
        // Restore active downloads from SharedPreferences
        restoreActiveDownloads();
    }

    /**
     * Download a single resource
     */
    public long downloadResource(String url, String title, ResourceType type) {
        return downloadResource(url, title, type, false);
    }

    /**
     * Download a single resource with WiFi-only option
     */
    public long downloadResource(String url, String title, ResourceType type, boolean wifiOnly) {
        if (url == null || url.isEmpty()) {
            Log.e(TAG, "Invalid URL for download");
            return -1;
        }

        // Check if already downloaded
        if (resourceManager.isAvailableOffline(url)) {
            Log.d(TAG, "Resource already available offline: " + url);
            notifyDownloadComplete(url, title);
            return -1;
        }

        // Check if WiFi required and not available
        if (wifiOnly && !NetworkHelper.isWiFiConnected(context)) {
            Log.w(TAG, "WiFi required but not connected");
            notifyDownloadFailed(url, title, "WiFi connection required");
            return -1;
        }

        try {
            // Generate filename
            String filename = generateFilename(url, type);
            File destinationDir = resourceManager.getOfflineDirectory(type);
            File destinationFile = new File(destinationDir, filename);

            // Create download request
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(title);
            request.setDescription("Downloading for offline access");
            
            // Set network restrictions
            if (wifiOnly) {
                request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
            } else {
                request.setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI | 
                    DownloadManager.Request.NETWORK_MOBILE
                );
            }
            
            request.setAllowedOverRoaming(false);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            
            // Use setDestinationInExternalFilesDir for Android 10+ compatibility
            // This avoids SecurityException with Scoped Storage
            String relativePath = "Offline/" + type.getFolderName() + "/" + filename;
            request.setDestinationInExternalFilesDir(context, null, relativePath);

            // Enqueue download
            long downloadId = downloadManager.enqueue(request);
            
            // Track download
            DownloadInfo info = new DownloadInfo(downloadId, url, title, type, destinationFile.getAbsolutePath());
            activeDownloads.put(downloadId, info);
            
            // Persist download info
            saveDownloadInfo(downloadId, info);
            
            Log.d(TAG, "✅ Started download: " + title);
            Log.d(TAG, "   URL: " + url);
            Log.d(TAG, "   Type: " + type.name());
            Log.d(TAG, "   Save to: " + destinationFile.getAbsolutePath());
            Log.d(TAG, "   Download ID: " + downloadId);
            notifyDownloadStarted(url, title);
            
            return downloadId;
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting download: " + e.getMessage(), e);
            notifyDownloadFailed(url, title, e.getMessage());
            return -1;
        }
    }

    /**
     * Download a folder of images
     */
    public List<Long> downloadFolder(String folderName, List<String> imageUrls, boolean wifiOnly) {
        List<Long> downloadIds = new ArrayList<>();
        
        if (imageUrls == null || imageUrls.isEmpty()) {
            return downloadIds;
        }

        File destinationDir = resourceManager.getOfflineDirectory(ResourceType.PHOTO);
        Log.d(TAG, "📂 Starting folder download: " + folderName);
        Log.d(TAG, "   Total images: " + imageUrls.size());
        Log.d(TAG, "   Destination: " + destinationDir.getAbsolutePath());
        Log.d(TAG, "   WiFi only: " + wifiOnly);
        
        for (String url : imageUrls) {
            String title = folderName + " - Image " + (downloadIds.size() + 1);
            long id = downloadResource(url, title, ResourceType.PHOTO, wifiOnly);
            
            if (id != -1) {
                downloadIds.add(id);
            }
            
            // Limit concurrent downloads
            if (downloadIds.size() >= MAX_CONCURRENT_DOWNLOADS) {
                break;
            }
        }
        
        return downloadIds;
    }

    /**
     * Download all audio files from a category
     */
    public List<Long> downloadAudioCategory(String categoryName, List<AudioFile> audioFiles, boolean wifiOnly) {
        List<Long> downloadIds = new ArrayList<>();
        
        if (audioFiles == null || audioFiles.isEmpty()) {
            return downloadIds;
        }

        File destinationDir = resourceManager.getOfflineDirectory(ResourceType.AUDIO);
        Log.d(TAG, "🎵 Starting audio category download: " + categoryName);
        Log.d(TAG, "   Total files: " + audioFiles.size());
        Log.d(TAG, "   Destination: " + destinationDir.getAbsolutePath());
        Log.d(TAG, "   WiFi only: " + wifiOnly);
        
        for (AudioFile audio : audioFiles) {
            String title = categoryName + " - " + audio.title;
            long id = downloadResource(audio.url, title, ResourceType.AUDIO, wifiOnly);
            
            if (id != -1) {
                downloadIds.add(id);
            }
        }
        
        return downloadIds;
    }

    /**
     * Check download progress
     */
    public int getDownloadProgress(long downloadId) {
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);
        
        try (Cursor cursor = downloadManager.query(query)) {
            if (cursor != null && cursor.moveToFirst()) {
                int bytesDownloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                int totalBytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                
                long bytesDownloaded = cursor.getLong(bytesDownloadedIdx);
                long totalBytes = cursor.getLong(totalBytesIdx);
                
                if (totalBytes > 0) {
                    return (int) ((bytesDownloaded * 100) / totalBytes);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting download progress: " + e.getMessage());
        }
        
        return 0;
    }

    /**
     * Get download status
     */
    public DownloadStatus getDownloadStatus(long downloadId) {
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);
        
        try (Cursor cursor = downloadManager.query(query)) {
            if (cursor != null && cursor.moveToFirst()) {
                int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int status = cursor.getInt(statusIdx);
                
                switch (status) {
                    case DownloadManager.STATUS_SUCCESSFUL:
                        return DownloadStatus.COMPLETED;
                    case DownloadManager.STATUS_FAILED:
                        return DownloadStatus.FAILED;
                    case DownloadManager.STATUS_PAUSED:
                        return DownloadStatus.PAUSED;
                    case DownloadManager.STATUS_PENDING:
                        return DownloadStatus.PENDING;
                    case DownloadManager.STATUS_RUNNING:
                        return DownloadStatus.DOWNLOADING;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting download status: " + e.getMessage());
        }
        
        return DownloadStatus.UNKNOWN;
    }

    /**
     * Cancel download
     */
    public void cancelDownload(long downloadId) {
        downloadManager.remove(downloadId);
        
        DownloadInfo info = activeDownloads.remove(downloadId);
        if (info != null) {
            notifyDownloadCancelled(info.url, info.title);
            
            // Delete incomplete file
            File file = new File(info.localPath);
            if (file.exists()) {
                file.delete();
            }
        }
        
        // Remove from persistent storage
        removeDownloadInfo(downloadId);
    }

    /**
     * Cancel all downloads
     */
    public void cancelAllDownloads() {
        List<Long> downloadIds = new ArrayList<>(activeDownloads.keySet());
        for (Long id : downloadIds) {
            cancelDownload(id);
        }
    }

    /**
     * Handle download completion
     */
    public void onDownloadComplete(long downloadId) {
        DownloadInfo info = activeDownloads.remove(downloadId);
        
        if (info == null) {
            // Try to restore from SharedPreferences
            info = loadDownloadInfo(downloadId);
            Log.d(TAG, "🔄 Restored download info from SharedPreferences: " + (info != null));
        }
        
        if (info == null) {
            Log.w(TAG, "⚠️ No download info found for ID: " + downloadId);
            return;
        }

        DownloadStatus status = getDownloadStatus(downloadId);
        
        if (status == DownloadStatus.COMPLETED) {
            // Mark as offline with type information
            resourceManager.markAsOffline(info.url, info.localPath, info.type);
            notifyDownloadComplete(info.url, info.title);
            
            File file = new File(info.localPath);
            Log.d(TAG, "✅ Download completed: " + info.title);
            Log.d(TAG, "   Type: " + info.type.name());
            Log.d(TAG, "   File path: " + file.getAbsolutePath());
            Log.d(TAG, "   File size: " + (file.length() / 1024) + " KB");
            Log.d(TAG, "   File exists: " + file.exists());
        } else {
            notifyDownloadFailed(info.url, info.title, "Download failed");
            
            Log.e(TAG, "❌ Download failed: " + info.title);
        }
        
        // Remove from persistent storage
        removeDownloadInfo(downloadId);
    }

    /**
     * Check if resource is downloading
     */
    public boolean isDownloading(String url) {
        for (DownloadInfo info : activeDownloads.values()) {
            if (info.url.equals(url)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all active downloads
     */
    public List<DownloadInfo> getActiveDownloads() {
        return new ArrayList<>(activeDownloads.values());
    }

    /**
     * Pause all downloads (called when WiFi disconnects)
     */
    public void pauseAllDownloads() {
        // DownloadManager automatically pauses WiFi-only downloads
        // when WiFi disconnects
        Log.d(TAG, "Downloads will pause automatically without WiFi");
    }

    /**
     * Resume downloads (called when WiFi connects)
     */
    public void resumeAllDownloads() {
        // DownloadManager automatically resumes when WiFi connects
        Log.d(TAG, "Downloads will resume automatically with WiFi");
    }

    // Listener management

    public void addListener(DownloadListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(DownloadListener listener) {
        listeners.remove(listener);
    }

    private void notifyDownloadStarted(String url, String title) {
        for (DownloadListener listener : listeners) {
            listener.onDownloadStarted(url, title);
        }
    }

    private void notifyDownloadComplete(String url, String title) {
        for (DownloadListener listener : listeners) {
            listener.onDownloadComplete(url, title);
        }
    }

    private void notifyDownloadFailed(String url, String title, String error) {
        for (DownloadListener listener : listeners) {
            listener.onDownloadFailed(url, title, error);
        }
    }

    private void notifyDownloadCancelled(String url, String title) {
        for (DownloadListener listener : listeners) {
            listener.onDownloadCancelled(url, title);
        }
    }

    // Helper methods

    private String generateFilename(String url, ResourceType type) {
        String filename = url.substring(url.lastIndexOf('/') + 1);
        
        if (filename.isEmpty() || !filename.contains(".")) {
            filename = "file_" + System.currentTimeMillis() + getExtension(type);
        }
        
        return filename;
    }

    private String getExtension(ResourceType type) {
        switch (type) {
            case AUDIO:
                return ".mp3";
            case PHOTO:
            case COMIC:
                return ".jpg";
            case JSON:
                return ".json";
            default:
                return "";
        }
    }
    
    // Persistent download tracking methods
    
    private void saveDownloadInfo(long downloadId, DownloadInfo info) {
        try {
            JSONObject json = new JSONObject();
            json.put("url", info.url);
            json.put("title", info.title);
            json.put("type", info.type.name());
            json.put("localPath", info.localPath);
            json.put("startTime", info.startTime);
            
            downloadPrefs.edit()
                .putString("download_" + downloadId, json.toString())
                .apply();
                
            Log.d(TAG, "💾 Saved download info for ID: " + downloadId);
        } catch (Exception e) {
            Log.e(TAG, "Error saving download info: " + e.getMessage());
        }
    }
    
    private DownloadInfo loadDownloadInfo(long downloadId) {
        try {
            String jsonStr = downloadPrefs.getString("download_" + downloadId, null);
            if (jsonStr != null) {
                JSONObject json = new JSONObject(jsonStr);
                String url = json.getString("url");
                String title = json.getString("title");
                String typeName = json.getString("type");
                String localPath = json.getString("localPath");
                
                ResourceType type = ResourceType.valueOf(typeName);
                
                Log.d(TAG, "📥 Loaded download info for ID: " + downloadId);
                return new DownloadInfo(downloadId, url, title, type, localPath);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading download info: " + e.getMessage());
        }
        return null;
    }
    
    private void removeDownloadInfo(long downloadId) {
        downloadPrefs.edit()
            .remove("download_" + downloadId)
            .apply();
        Log.d(TAG, "🗑️ Removed download info for ID: " + downloadId);
    }
    
    private void restoreActiveDownloads() {
        Map<String, ?> allPrefs = downloadPrefs.getAll();
        int restored = 0;
        
        for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
            if (entry.getKey().startsWith("download_")) {
                try {
                    long downloadId = Long.parseLong(entry.getKey().substring(9));
                    DownloadInfo info = loadDownloadInfo(downloadId);
                    if (info != null) {
                        activeDownloads.put(downloadId, info);
                        restored++;
                    }
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Invalid download ID in prefs: " + entry.getKey());
                }
            }
        }
        
        Log.d(TAG, "🔄 Restored " + restored + " active downloads from SharedPreferences");
    }

    // Inner classes

    public static class DownloadInfo {
        public final long downloadId;
        public final String url;
        public final String title;
        public final ResourceType type;
        public final String localPath;
        public final long startTime;

        public DownloadInfo(long downloadId, String url, String title, ResourceType type, String localPath) {
            this.downloadId = downloadId;
            this.url = url;
            this.title = title;
            this.type = type;
            this.localPath = localPath;
            this.startTime = System.currentTimeMillis();
        }
    }

    public static class AudioFile {
        public String url;
        public String title;

        public AudioFile(String url, String title) {
            this.url = url;
            this.title = title;
        }
    }

    public enum DownloadStatus {
        PENDING,
        DOWNLOADING,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELLED,
        UNKNOWN
    }

    public interface DownloadListener {
        void onDownloadStarted(String url, String title);
        void onDownloadComplete(String url, String title);
        void onDownloadFailed(String url, String title, String error);
        void onDownloadCancelled(String url, String title);
        void onDownloadProgress(String url, int progress);
    }
}
