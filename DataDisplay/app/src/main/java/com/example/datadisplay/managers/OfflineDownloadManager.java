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

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages batch downloads for offline access.
 */
public class OfflineDownloadManager {

    private static final String TAG = "OfflineDownloadManager";
    private static final String PREF_DOWNLOADS = "ActiveDownloads";

    private final Context context;
    private final DownloadManager downloadManager;
    private final OfflineResourceManager resourceManager;
    private final SharedPreferences downloadPrefs;

    private final Map<Long, DownloadInfo> activeDownloads = new ConcurrentHashMap<>();
    private final List<DownloadListener> listeners = new ArrayList<>();

    public OfflineDownloadManager(Context context) {
        this.context = context.getApplicationContext();
        this.downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        this.resourceManager = new OfflineResourceManager(context);
        this.downloadPrefs = context.getSharedPreferences(PREF_DOWNLOADS, Context.MODE_PRIVATE);

        restoreActiveDownloads();
    }

    public long downloadResource(String url, String title, ResourceType type) {
        return downloadResource(url, title, type, false);
    }

    public long downloadResource(String url, String title, ResourceType type, boolean wifiOnly) {
        return downloadResource(url, title, type, wifiOnly, null);
    }

    private long downloadResource(String url, String title, ResourceType type, boolean wifiOnly, String subFolderName) {
        if (url == null || url.isEmpty()) {
            Log.e(TAG, "Invalid URL for download");
            return -1;
        }

        if (resourceManager.isAvailableOffline(url)) {
            Log.d(TAG, "Resource already available offline: " + url);
            notifyDownloadComplete(url, title);
            return -1;
        }

        if (wifiOnly && !NetworkHelper.isWiFiConnected(context)) {
            Log.w(TAG, "WiFi required but not connected");
            notifyDownloadFailed(url, title, "WiFi connection required");
            return -1;
        }

        try {
            String filename = generateFilename(url, title, type);
            File destinationDir = resourceManager.getOfflineDirectory(type);

            String relativePath = OfflineResourceManager.PUBLIC_OFFLINE_BASE_PATH
                    + "/" + type.getFolderName();

            if (subFolderName != null && !subFolderName.trim().isEmpty()) {
                String safeFolderName = sanitizeFolderName(subFolderName);
                destinationDir = new File(destinationDir, safeFolderName);
                if (!destinationDir.exists()) {
                    destinationDir.mkdirs();
                }
                relativePath += "/" + safeFolderName;
            }

            File destinationFile = new File(destinationDir, filename);

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(title);
            request.setDescription("Downloading for offline access");

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

            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    relativePath + "/" + filename
            );

            long downloadId = downloadManager.enqueue(request);

            DownloadInfo info = new DownloadInfo(downloadId, url, title, type, destinationFile.getAbsolutePath());
            activeDownloads.put(downloadId, info);
            saveDownloadInfo(downloadId, info);

            notifyDownloadStarted(url, title);
            return downloadId;

        } catch (Exception e) {
            Log.e(TAG, "Error starting download: " + e.getMessage(), e);
            notifyDownloadFailed(url, title, e.getMessage());
            return -1;
        }
    }

    public List<Long> downloadFolder(String folderName, List<String> imageUrls, boolean wifiOnly) {
        return downloadFolder(folderName, imageUrls, ResourceType.PHOTO, wifiOnly);
    }

    public List<Long> downloadFolder(String folderName, List<String> imageUrls, ResourceType type, boolean wifiOnly) {
        List<Long> downloadIds = new ArrayList<>();

        if (imageUrls == null || imageUrls.isEmpty()) {
            return downloadIds;
        }

        for (String url : imageUrls) {
            String title = folderName + " - Image " + (downloadIds.size() + 1);
            long id = downloadResource(url, title, type, wifiOnly, folderName);

            if (id != -1) {
                downloadIds.add(id);
            }
        }

        return downloadIds;
    }

    public List<Long> downloadAudioCategory(String categoryName, List<AudioFile> audioFiles, boolean wifiOnly) {
        List<Long> downloadIds = new ArrayList<>();

        if (audioFiles == null || audioFiles.isEmpty()) {
            return downloadIds;
        }

        for (AudioFile audio : audioFiles) {
            String title = audio.title;
            long id = downloadResource(audio.url, title, ResourceType.AUDIO, wifiOnly);

            if (id != -1) {
                downloadIds.add(id);
            }
        }

        return downloadIds;
    }

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
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting download status: " + e.getMessage());
        }

        return DownloadStatus.UNKNOWN;
    }

    public void cancelDownload(long downloadId) {
        downloadManager.remove(downloadId);

        DownloadInfo info = activeDownloads.remove(downloadId);
        if (info != null) {
            notifyDownloadCancelled(info.url, info.title);

            File file = new File(info.localPath);
            if (file.exists()) {
                file.delete();
            }
        }

        removeDownloadInfo(downloadId);
    }

    public void cancelAllDownloads() {
        List<Long> downloadIds = new ArrayList<>(activeDownloads.keySet());
        for (Long id : downloadIds) {
            cancelDownload(id);
        }
    }

    public void onDownloadComplete(long downloadId) {
        DownloadInfo info = activeDownloads.remove(downloadId);

        if (info == null) {
            info = loadDownloadInfo(downloadId);
        }

        if (info == null) {
            return;
        }

        DownloadStatus status = getDownloadStatus(downloadId);

        if (status == DownloadStatus.COMPLETED) {
            resourceManager.markAsOffline(info.url, info.localPath, info.type);
            notifyDownloadComplete(info.url, info.title);
        } else {
            notifyDownloadFailed(info.url, info.title, "Download failed");
        }

        removeDownloadInfo(downloadId);
    }

    public boolean isDownloading(String url) {
        for (DownloadInfo info : activeDownloads.values()) {
            if (info.url.equals(url)) {
                return true;
            }
        }
        return false;
    }

    public List<DownloadInfo> getActiveDownloads() {
        return new ArrayList<>(activeDownloads.values());
    }

    public void pauseAllDownloads() {
        Log.d(TAG, "Downloads will pause automatically without WiFi");
    }

    public void resumeAllDownloads() {
        Log.d(TAG, "Downloads will resume automatically with WiFi");
    }

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

    private String generateFilename(String url, String title, ResourceType type) {
        if (type == ResourceType.AUDIO && title != null && !title.trim().isEmpty()) {
            String audioTitle = sanitizeFileName(title.trim());
            return ensureExtension(audioTitle, getExtension(type));
        }

        String filename = url.substring(url.lastIndexOf('/') + 1);
        int queryIndex = filename.indexOf('?');
        if (queryIndex >= 0) {
            filename = filename.substring(0, queryIndex);
        }

        filename = sanitizeFileName(filename);

        if (filename.isEmpty() || !filename.contains(".")) {
            filename = "file_" + System.currentTimeMillis() + getExtension(type);
        }

        return filename;
    }

    private String ensureExtension(String filename, String extension) {
        if (extension == null || extension.isEmpty()) {
            return filename;
        }

        return filename.toLowerCase().endsWith(extension) ? filename : filename + extension;
    }

    private String sanitizeFileName(String name) {
        String sanitized = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return sanitized.isEmpty() ? "file" : sanitized;
    }

    private String sanitizeFolderName(String folderName) {
        String sanitized = folderName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return sanitized.isEmpty() ? "folder" : sanitized;
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
    }

    private void restoreActiveDownloads() {
        Map<String, ?> allPrefs = downloadPrefs.getAll();

        for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
            if (entry.getKey().startsWith("download_")) {
                try {
                    long downloadId = Long.parseLong(entry.getKey().substring(9));
                    DownloadInfo info = loadDownloadInfo(downloadId);
                    if (info != null) {
                        activeDownloads.put(downloadId, info);
                    }
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Invalid download ID in prefs: " + entry.getKey());
                }
            }
        }
    }

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
