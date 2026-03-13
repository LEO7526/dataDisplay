package com.example.datadisplay.managers;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages offline resource access and tracking.
 */
public class OfflineResourceManager {

    private static final String PREF_NAME = "OfflineResources";
    private static final String KEY_OFFLINE_URLS = "offline_urls";
    private static final String KEY_OFFLINE_PATHS = "offline_paths";
    private static final String KEY_OFFLINE_TYPES = "offline_types";
    private static final String KEY_PRIORITY_URLS = "priority_urls";
    public static final String PUBLIC_OFFLINE_BASE_PATH = "DataDisplay/Offline";

    private final Context context;
    private final SharedPreferences prefs;

    public enum ResourceType {
        PHOTO("photos"),
        COMIC("comics"),
        AUDIO("audios"),
        JSON("json");

        private final String folderName;

        ResourceType(String folderName) {
            this.folderName = folderName;
        }

        public String getFolderName() {
            return folderName;
        }
    }

    public OfflineResourceManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isAvailableOffline(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        File file = getOfflineFile(url);
        return file != null && file.exists() && file.length() > 0;
    }

    public File getOfflineFile(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        Set<String> offlineUrls = prefs.getStringSet(KEY_OFFLINE_URLS, new HashSet<>());
        if (!offlineUrls.contains(url)) {
            return null;
        }

        String localPath = prefs.getString(KEY_OFFLINE_PATHS + "_" + url.hashCode(), null);

        if (localPath != null) {
            File file = new File(localPath);
            if (file.exists()) {
                return file;
            } else {
                android.util.Log.w("OfflineResourceMgr", "Stored path doesn't exist: " + localPath);
            }
        }

        String filename = generateFilenameFromUrl(url);
        ResourceType type = detectResourceType(url);

        File offlineDir = getOfflineDirectory(type);
        File file = new File(offlineDir, filename);

        return file.exists() ? file : null;
    }

    public void markAsOffline(String url, String localPath, ResourceType type) {
        Set<String> offlineUrls = new HashSet<>(prefs.getStringSet(KEY_OFFLINE_URLS, new HashSet<>()));
        offlineUrls.add(url);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(KEY_OFFLINE_URLS, offlineUrls);
        editor.putString(KEY_OFFLINE_PATHS + "_" + url.hashCode(), localPath);
        editor.putString(KEY_OFFLINE_TYPES + "_" + url.hashCode(), type.name());
        editor.apply();

        File file = new File(localPath);
        android.util.Log.d("OfflineResourceMgr", "Marked as offline: " + localPath);
        android.util.Log.d("OfflineResourceMgr", "URL: " + url);
        android.util.Log.d("OfflineResourceMgr", "Type: " + type.name());
        android.util.Log.d("OfflineResourceMgr", "File exists: " + file.exists());
        android.util.Log.d("OfflineResourceMgr", "Total offline items: " + offlineUrls.size());
    }

    @Deprecated
    public void markAsOffline(String url, String localPath) {
        ResourceType type = detectResourceType(url);
        markAsOffline(url, localPath, type);
    }

    public void removeOfflineStatus(String url) {
        Set<String> offlineUrls = new HashSet<>(prefs.getStringSet(KEY_OFFLINE_URLS, new HashSet<>()));
        offlineUrls.remove(url);

        Set<String> priorityUrls = new HashSet<>(prefs.getStringSet(KEY_PRIORITY_URLS, new HashSet<>()));
        priorityUrls.remove(url);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(KEY_OFFLINE_URLS, offlineUrls);
        editor.putStringSet(KEY_PRIORITY_URLS, priorityUrls);
        editor.remove(KEY_OFFLINE_PATHS + "_" + url.hashCode());
        editor.remove(KEY_OFFLINE_TYPES + "_" + url.hashCode());
        editor.apply();
    }

    public String findUrlByLocalPath(String localPath) {
        if (localPath == null || localPath.isEmpty()) {
            return null;
        }

        String targetPath = new File(localPath).getAbsolutePath();
        Set<String> offlineUrls = prefs.getStringSet(KEY_OFFLINE_URLS, new HashSet<>());

        for (String url : offlineUrls) {
            String storedPath = prefs.getString(KEY_OFFLINE_PATHS + "_" + url.hashCode(), null);
            if (storedPath == null) {
                continue;
            }

            if (targetPath.equals(new File(storedPath).getAbsolutePath())) {
                return url;
            }
        }

        return null;
    }

    public void removeOfflineStatusByLocalPath(String localPath) {
        String url = findUrlByLocalPath(localPath);
        if (url != null) {
            removeOfflineStatus(url);
        }
    }

    public void markAsOfflinePriority(String url) {
        Set<String> priorityUrls = new HashSet<>(prefs.getStringSet(KEY_PRIORITY_URLS, new HashSet<>()));
        priorityUrls.add(url);
        prefs.edit().putStringSet(KEY_PRIORITY_URLS, priorityUrls).apply();
    }

    public boolean isPriority(String url) {
        Set<String> priorityUrls = prefs.getStringSet(KEY_PRIORITY_URLS, new HashSet<>());
        return priorityUrls.contains(url);
    }

    public List<String> getOfflineUrls(ResourceType type) {
        Set<String> allUrls = prefs.getStringSet(KEY_OFFLINE_URLS, new HashSet<>());
        List<String> filteredUrls = new ArrayList<>();

        for (String url : allUrls) {
            String storedTypeName = prefs.getString(KEY_OFFLINE_TYPES + "_" + url.hashCode(), null);
            ResourceType urlType;

            if (storedTypeName != null) {
                try {
                    urlType = ResourceType.valueOf(storedTypeName);
                } catch (IllegalArgumentException e) {
                    urlType = detectResourceType(url);
                }
            } else {
                urlType = detectResourceType(url);
            }

            if (urlType == type && isAvailableOffline(url)) {
                filteredUrls.add(url);
            }
        }

        return filteredUrls;
    }

    public Map<ResourceType, List<OfflineCategory>> getOfflineCategories() {
        Map<ResourceType, List<OfflineCategory>> result = new HashMap<>();

        for (ResourceType type : ResourceType.values()) {
            List<OfflineCategory> categories = getOfflineCategoriesForType(type);
            if (!categories.isEmpty()) {
                result.put(type, categories);
            }
        }

        return result;
    }

    public File getOfflineDirectory(ResourceType type) {
        File publicDownloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File baseDir = publicDownloadsDir != null ? publicDownloadsDir : context.getExternalFilesDir(null);
        if (baseDir == null) {
            baseDir = context.getFilesDir();
        }

        File offlineRoot = new File(baseDir, PUBLIC_OFFLINE_BASE_PATH);
        File typeDir = new File(offlineRoot, type.getFolderName());

        if (!typeDir.exists()) {
            typeDir.mkdirs();
        }

        return typeDir;
    }

    public long getTotalOfflineSize() {
        long totalSize = 0;

        for (ResourceType type : ResourceType.values()) {
            File dir = getOfflineDirectory(type);
            totalSize += calculateDirectorySize(dir);
        }

        return totalSize;
    }

    public long getOfflineSize(ResourceType type) {
        File dir = getOfflineDirectory(type);
        return calculateDirectorySize(dir);
    }

    public List<File> getOfflineFiles(ResourceType type) {
        File rootDir = getOfflineDirectory(type);
        List<File> files = new ArrayList<>();
        collectFilesRecursively(rootDir, files);
        files.sort((first, second) -> Long.compare(second.lastModified(), first.lastModified()));
        return files;
    }

    public String getRelativeOfflinePath(ResourceType type, File file) {
        if (file == null) {
            return "";
        }

        File rootDir = getOfflineDirectory(type);
        String rootPath = rootDir.getAbsolutePath();
        String filePath = file.getAbsolutePath();

        if (filePath.startsWith(rootPath)) {
            String relative = filePath.substring(rootPath.length());
            if (relative.startsWith(File.separator)) {
                relative = relative.substring(1);
            }
            return relative.isEmpty() ? file.getName() : relative;
        }

        return file.getName();
    }

    public void clearOfflineCache(boolean includePriority) {
        Set<String> priorityUrls = prefs.getStringSet(KEY_PRIORITY_URLS, new HashSet<>());
        Set<String> offlineUrls = new HashSet<>(prefs.getStringSet(KEY_OFFLINE_URLS, new HashSet<>()));

        for (String url : offlineUrls) {
            if (!includePriority && priorityUrls.contains(url)) {
                continue;
            }

            File file = getOfflineFile(url);
            if (file != null && file.exists()) {
                file.delete();
            }

            removeOfflineStatus(url);
        }
    }

    public void clearOldOfflineResources(int daysOld) {
        Set<String> priorityUrls = prefs.getStringSet(KEY_PRIORITY_URLS, new HashSet<>());
        Set<String> offlineUrls = new HashSet<>(prefs.getStringSet(KEY_OFFLINE_URLS, new HashSet<>()));
        long cutoffTime = System.currentTimeMillis() - (daysOld * 24L * 60L * 60L * 1000L);

        for (String url : offlineUrls) {
            if (priorityUrls.contains(url)) {
                continue;
            }

            File file = getOfflineFile(url);
            if (file != null && file.exists() && file.lastModified() < cutoffTime) {
                file.delete();
                removeOfflineStatus(url);
            }
        }
    }

    private String generateFilenameFromUrl(String url) {
        String filename = url.substring(url.lastIndexOf('/') + 1);

        if (filename.isEmpty() || !filename.contains(".")) {
            filename = String.valueOf(url.hashCode()) + getExtensionFromUrl(url);
        }

        return filename;
    }

    private String getExtensionFromUrl(String url) {
        if (url.contains("jpg") || url.contains("jpeg")) return ".jpg";
        if (url.contains("png")) return ".png";
        if (url.contains("mp3")) return ".mp3";
        if (url.contains("json")) return ".json";
        return "";
    }

    private ResourceType detectResourceType(String url) {
        String urlLower = url.toLowerCase();

        if (urlLower.contains("mp3") || urlLower.contains("audio")) {
            return ResourceType.AUDIO;
        } else if (urlLower.contains("json")) {
            return ResourceType.JSON;
        } else if (urlLower.contains("comic")) {
            return ResourceType.COMIC;
        } else {
            return ResourceType.PHOTO;
        }
    }

    private long calculateDirectorySize(File directory) {
        long size = 0;

        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        size += calculateDirectorySize(file);
                    } else {
                        size += file.length();
                    }
                }
            }
        }

        return size;
    }

    private void collectFilesRecursively(File directory, List<File> result) {
        if (directory == null || !directory.exists()) {
            return;
        }

        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                collectFilesRecursively(child, result);
            } else if (child.isFile()) {
                result.add(child);
            }
        }
    }

    private List<OfflineCategory> getOfflineCategoriesForType(ResourceType type) {
        return new ArrayList<>();
    }

    public static class OfflineCategory {
        public String name;
        public int itemCount;
        public long totalSize;
        public ResourceType type;

        public OfflineCategory(String name, int itemCount, long totalSize, ResourceType type) {
            this.name = name;
            this.itemCount = itemCount;
            this.totalSize = totalSize;
            this.type = type;
        }
    }

    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
