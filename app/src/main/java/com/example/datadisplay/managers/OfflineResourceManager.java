package com.example.datadisplay.managers;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages offline resource access and tracking
 */
public class OfflineResourceManager {

    private static final String PREF_NAME = "OfflineResources";
    private static final String KEY_OFFLINE_URLS = "offline_urls";
    private static final String KEY_OFFLINE_PATHS = "offline_paths";  // URL -> LocalPath mapping
    private static final String KEY_OFFLINE_TYPES = "offline_types";  // URL -> ResourceType mapping
    private static final String KEY_PRIORITY_URLS = "priority_urls";
    
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

    /**
     * Check if a resource is available offline
     */
    public boolean isAvailableOffline(String url) {
        if (url == null || url.isEmpty()) return false;
        
        File file = getOfflineFile(url);
        return file != null && file.exists() && file.length() > 0;
    }

    /**
     * Get the offline file for a given URL
     */
    public File getOfflineFile(String url) {
        if (url == null || url.isEmpty()) return null;

        // Check if URL is tracked in preferences
        Set<String> offlineUrls = prefs.getStringSet(KEY_OFFLINE_URLS, new HashSet<>());
        if (!offlineUrls.contains(url)) {
            return null;
        }

        // Get the stored local path
        String localPath = prefs.getString(KEY_OFFLINE_PATHS + "_" + url.hashCode(), null);
        
        if (localPath != null) {
            File file = new File(localPath);
            if (file.exists()) {
                return file;
            } else {
                android.util.Log.w("OfflineResourceMgr", "Stored path doesn't exist: " + localPath);
            }
        }
        
        // Fallback: try to generate filename from URL (for legacy data)
        String filename = generateFilenameFromUrl(url);
        ResourceType type = detectResourceType(url);
        
        File offlineDir = getOfflineDirectory(type);
        File file = new File(offlineDir, filename);
        
        return file.exists() ? file : null;
    }

    /**
     * Mark a URL as available offline
     */
    public void markAsOffline(String url, String localPath, ResourceType type) {
        // Store URL in the set
        Set<String> offlineUrls = new HashSet<>(prefs.getStringSet(KEY_OFFLINE_URLS, new HashSet<>()));
        offlineUrls.add(url);
        
        // Store the URL -> LocalPath mapping and ResourceType
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(KEY_OFFLINE_URLS, offlineUrls);
        editor.putString(KEY_OFFLINE_PATHS + "_" + url.hashCode(), localPath);
        editor.putString(KEY_OFFLINE_TYPES + "_" + url.hashCode(), type.name());
        editor.apply();
        
        File file = new File(localPath);
        android.util.Log.d("OfflineResourceMgr", "💾 Marked as offline: " + localPath);
        android.util.Log.d("OfflineResourceMgr", "   URL: " + url);
        android.util.Log.d("OfflineResourceMgr", "   Type: " + type.name());
        android.util.Log.d("OfflineResourceMgr", "   Path key: " + (KEY_OFFLINE_PATHS + "_" + url.hashCode()));
        android.util.Log.d("OfflineResourceMgr", "   File exists: " + file.exists());
        android.util.Log.d("OfflineResourceMgr", "   Total offline items: " + offlineUrls.size());
    }
    
    /**
     * Mark a URL as available offline (legacy method without type)
     */
    @Deprecated
    public void markAsOffline(String url, String localPath) {
        ResourceType type = detectResourceType(url);
        markAsOffline(url, localPath, type);
    }

    /**
     * Remove offline status for a URL
     */
    public void removeOfflineStatus(String url) {
        Set<String> offlineUrls = new HashSet<>(prefs.getStringSet(KEY_OFFLINE_URLS, new HashSet<>()));
        offlineUrls.remove(url);
        
        // Also remove from priority
        Set<String> priorityUrls = new HashSet<>(prefs.getStringSet(KEY_PRIORITY_URLS, new HashSet<>()));
        priorityUrls.remove(url);
        
        // Remove path mapping, type mapping and apply all changes
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(KEY_OFFLINE_URLS, offlineUrls);
        editor.putStringSet(KEY_PRIORITY_URLS, priorityUrls);
        editor.remove(KEY_OFFLINE_PATHS + "_" + url.hashCode());
        editor.remove(KEY_OFFLINE_TYPES + "_" + url.hashCode());
        editor.apply();
    }

    /**
     * Mark resource as offline priority (won't be auto-deleted)
     */
    public void markAsOfflinePriority(String url) {
        Set<String> priorityUrls = new HashSet<>(prefs.getStringSet(KEY_PRIORITY_URLS, new HashSet<>()));
        priorityUrls.add(url);
        prefs.edit().putStringSet(KEY_PRIORITY_URLS, priorityUrls).apply();
    }

    /**
     * Check if a resource is marked as priority
     */
    public boolean isPriority(String url) {
        Set<String> priorityUrls = prefs.getStringSet(KEY_PRIORITY_URLS, new HashSet<>());
        return priorityUrls.contains(url);
    }

    /**
     * Get all offline URLs for a specific resource type
     */
    public List<String> getOfflineUrls(ResourceType type) {
        Set<String> allUrls = prefs.getStringSet(KEY_OFFLINE_URLS, new HashSet<>());
        List<String> filteredUrls = new ArrayList<>();
        
        android.util.Log.d("OfflineResourceMgr", "🔍 Filtering offline URLs for type: " + type.name());
        android.util.Log.d("OfflineResourceMgr", "   Total tracked URLs: " + allUrls.size());
        
        for (String url : allUrls) {
            // Get stored resource type (if available)
            String storedTypeName = prefs.getString(KEY_OFFLINE_TYPES + "_" + url.hashCode(), null);
            ResourceType urlType;
            
            if (storedTypeName != null) {
                try {
                    urlType = ResourceType.valueOf(storedTypeName);
                } catch (IllegalArgumentException e) {
                    urlType = detectResourceType(url);
                }
            } else {
                // Fallback to detection for legacy data
                urlType = detectResourceType(url);
            }
            
            android.util.Log.d("OfflineResourceMgr", "   URL type: " + urlType.name() + " (target: " + type.name() + ")");
            
            if (urlType == type && isAvailableOffline(url)) {
                filteredUrls.add(url);
                android.util.Log.d("OfflineResourceMgr", "     ✅ Matched and added");
            } else {
                android.util.Log.d("OfflineResourceMgr", "     ❌ Skipped (type mismatch or not available)");
            }
        }
        
        android.util.Log.d("OfflineResourceMgr", "   Filtered result: " + filteredUrls.size() + " URLs");
        return filteredUrls;
    }

    /**
     * Get all offline categories organized by type
     */
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

    /**
     * Get offline directory for a resource type
     * Uses app-specific external storage (Android 10+ compatible)
     */
    public File getOfflineDirectory(ResourceType type) {
        // Use app-specific external files directory (no special permissions needed on Android 10+)
        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir == null) {
            // Fallback to internal storage if external not available
            externalFilesDir = context.getFilesDir();
        }
        
        File offlineRoot = new File(externalFilesDir, "Offline");
        File typeDir = new File(offlineRoot, type.getFolderName());
        
        if (!typeDir.exists()) {
            boolean created = typeDir.mkdirs();
            android.util.Log.d("OfflineResourceMgr", "📁 Created directory: " + typeDir.getAbsolutePath() + " (success: " + created + ")");
        }
        
        return typeDir;
    }

    /**
     * Calculate total offline storage used
     */
    public long getTotalOfflineSize() {
        long totalSize = 0;
        
        for (ResourceType type : ResourceType.values()) {
            File dir = getOfflineDirectory(type);
            totalSize += calculateDirectorySize(dir);
        }
        
        return totalSize;
    }

    /**
     * Get offline storage size for a specific type
     */
    public long getOfflineSize(ResourceType type) {
        File dir = getOfflineDirectory(type);
        return calculateDirectorySize(dir);
    }

    /**
     * Clear all offline resources (except priority ones)
     */
    public void clearOfflineCache(boolean includePriority) {
        Set<String> priorityUrls = prefs.getStringSet(KEY_PRIORITY_URLS, new HashSet<>());
        Set<String> offlineUrls = new HashSet<>(prefs.getStringSet(KEY_OFFLINE_URLS, new HashSet<>()));
        
        for (String url : offlineUrls) {
            if (!includePriority && priorityUrls.contains(url)) {
                continue; // Skip priority files
            }
            
            File file = getOfflineFile(url);
            if (file != null && file.exists()) {
                file.delete();
            }
            
            removeOfflineStatus(url);
        }
    }

    /**
     * Clear offline resources older than specified days
     */
    public void clearOldOfflineResources(int daysOld) {
        Set<String> priorityUrls = prefs.getStringSet(KEY_PRIORITY_URLS, new HashSet<>());
        Set<String> offlineUrls = new HashSet<>(prefs.getStringSet(KEY_OFFLINE_URLS, new HashSet<>()));
        long cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L);
        
        for (String url : offlineUrls) {
            if (priorityUrls.contains(url)) {
                continue; // Skip priority files
            }
            
            File file = getOfflineFile(url);
            if (file != null && file.exists() && file.lastModified() < cutoffTime) {
                file.delete();
                removeOfflineStatus(url);
            }
        }
    }

    // Helper methods

    private String generateFilenameFromUrl(String url) {
        // Extract filename from URL or generate hash
        String filename = url.substring(url.lastIndexOf('/') + 1);
        
        // If no filename in URL, use hash
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

    private List<OfflineCategory> getOfflineCategoriesForType(ResourceType type) {
        // This will be populated by OfflineDownloadManager
        // For now, return empty list
        return new ArrayList<>();
    }

    /**
     * Model class for offline category
     */
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

    /**
     * Format file size for display
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
