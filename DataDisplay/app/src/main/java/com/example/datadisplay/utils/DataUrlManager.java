package com.example.datadisplay.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

public class DataUrlManager {
    private static final String TAG = "DataUrlManager";
    private static final String PREF_NAME = "DataSourceSettings";
    private static final String KEY_MP3_URL = "mp3_data_url";
    private static final String KEY_COMIC_URL = "comic_data_url";
    private static final String KEY_PHOTO_URL = "photo_data_url";
    private static final String KEY_BOOK_URL = "book_data_url";
    private static final String KEY_OCP_INDEX_URL = "ocp_index_url";
    private static final String KEY_OCP_QUESTIONS_URL = "ocp_questions_url";
    private static final String KEY_OCP_SCRIPT_URL = "ocp_script_url";
    private static final String KEY_OCP_STYLE_URL = "ocp_style_url";

    // Default Google Drive share links.
    private static final String DEFAULT_MP3_URL = "https://drive.google.com/file/d/1MCuzBSSmeVzsPP9IBy4LdUj02VEeA2FU/view?usp=sharing";
    private static final String DEFAULT_COMIC_URL = "https://drive.google.com/file/d/1JKKpAczBJ2jxl7yhAY7AVxyYfRembgFo/view?usp=sharing";
    private static final String DEFAULT_PHOTO_URL = "https://drive.google.com/file/d/1p0slCvo3j543GWzG85J21Twy7nT49Y2Y/view?usp=sharing";
    private static final String DEFAULT_BOOK_URL = "https://drive.google.com/file/d/1tjla5WD0elmmpYQkYcIY1ApCoYPcUxQ-/view?usp=sharing";
    // Defaults for OCP Quiz files (these were provided by the user as Google Drive share links)
    private static final String DEFAULT_OCP_STYLE_URL = "https://drive.google.com/file/d/1nRg4o9pLWlxeFAYZyJYbCx6JQ2YGpzk9/view?usp=sharing"; // styles.css
    private static final String DEFAULT_OCP_SCRIPT_URL = "https://drive.google.com/file/d/1uEdf0gu_rZxPfpt8kV_jEaRYyfVPUDxq/view?usp=sharing"; // script.js
    private static final String DEFAULT_OCP_QUESTIONS_URL = "https://drive.google.com/file/d/1HX5ZhT7IICMKciNzYexrDP1fIjRyGzoL/view?usp=sharing"; // questions-data.js
    private static final String DEFAULT_OCP_INDEX_URL = "https://drive.google.com/file/d/13g0FeepW1XYc3qNR0d4kj6bAy2FauL5u/view?usp=sharing"; // index.html

    private final SharedPreferences sharedPreferences;

    public DataUrlManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public String getMp3DataUrl() {
        return sharedPreferences.getString(KEY_MP3_URL, DEFAULT_MP3_URL);
    }

    public String getComicDataUrl() {
        return sharedPreferences.getString(KEY_COMIC_URL, DEFAULT_COMIC_URL);
    }

    public String getPhotoDataUrl() {
        return sharedPreferences.getString(KEY_PHOTO_URL, DEFAULT_PHOTO_URL);
    }

    public String getBookDataUrl() {
        return sharedPreferences.getString(KEY_BOOK_URL, DEFAULT_BOOK_URL);
    }

    // OCP Quiz getters
    public String getOcpIndexDataUrl() { return sharedPreferences.getString(KEY_OCP_INDEX_URL, DEFAULT_OCP_INDEX_URL); }
    public String getOcpQuestionsDataUrl() { return sharedPreferences.getString(KEY_OCP_QUESTIONS_URL, DEFAULT_OCP_QUESTIONS_URL); }
    public String getOcpScriptDataUrl() { return sharedPreferences.getString(KEY_OCP_SCRIPT_URL, DEFAULT_OCP_SCRIPT_URL); }
    public String getOcpStyleDataUrl() { return sharedPreferences.getString(KEY_OCP_STYLE_URL, DEFAULT_OCP_STYLE_URL); }

    public String getMp3DownloadUrl() {
        return toDownloadUrl(getMp3DataUrl());
    }

    public String getComicDownloadUrl() {
        return toDownloadUrl(getComicDataUrl());
    }

    public String getPhotoDownloadUrl() {
        return toDownloadUrl(getPhotoDataUrl());
    }

    public String getBookDownloadUrl() {
        return toDownloadUrl(getBookDataUrl());
    }

    // OCP Quiz download URL helpers
    public String getOcpIndexDownloadUrl() { return toDownloadUrl(getOcpIndexDataUrl()); }
    public String getOcpQuestionsDownloadUrl() { return toDownloadUrl(getOcpQuestionsDataUrl()); }
    public String getOcpScriptDownloadUrl() { return toDownloadUrl(getOcpScriptDataUrl()); }
    public String getOcpStyleDownloadUrl() { return toDownloadUrl(getOcpStyleDataUrl()); }

    public void setMp3DataUrl(String url) {
        sharedPreferences.edit().putString(KEY_MP3_URL, url).apply();
    }

    public void setComicDataUrl(String url) {
        sharedPreferences.edit().putString(KEY_COMIC_URL, url).apply();
    }

    public void setPhotoDataUrl(String url) {
        sharedPreferences.edit().putString(KEY_PHOTO_URL, url).apply();
    }

    public void setBookDataUrl(String url) {
        sharedPreferences.edit().putString(KEY_BOOK_URL, url).apply();
    }

    // OCP Quiz setters
    public void setOcpIndexDataUrl(String url) { sharedPreferences.edit().putString(KEY_OCP_INDEX_URL, url).apply(); }
    public void setOcpQuestionsDataUrl(String url) { sharedPreferences.edit().putString(KEY_OCP_QUESTIONS_URL, url).apply(); }
    public void setOcpScriptDataUrl(String url) { sharedPreferences.edit().putString(KEY_OCP_SCRIPT_URL, url).apply(); }
    public void setOcpStyleDataUrl(String url) { sharedPreferences.edit().putString(KEY_OCP_STYLE_URL, url).apply(); }

    public void clearCache() {
        sharedPreferences.edit().putLong("last_update_time", System.currentTimeMillis()).apply();
    }

    private String toDownloadUrl(String url) {
        Log.d(TAG, "toDownloadUrl called with: " + url);
        if (url == null) {
            Log.d(TAG, "toDownloadUrl input is null -> returning empty string");
            return "";
        }

        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            Log.d(TAG, "toDownloadUrl input trimmed is empty -> returning empty string");
            return trimmed;
        }

        if (!trimmed.contains("drive.google.com")) {
            Log.d(TAG, "toDownloadUrl: not a drive URL, returning unchanged: " + trimmed);
            return trimmed;
        }

        if (trimmed.contains("uc?export=download")) {
            Log.d(TAG, "toDownloadUrl: already a download URL: " + trimmed);
            return trimmed;
        }

        String fileId = extractGoogleDriveFileId(trimmed);
        if (fileId == null || fileId.isEmpty()) {
            Log.w(TAG, "toDownloadUrl: failed to extract fileId from: " + trimmed);
            return trimmed;
        }

        String result = "https://drive.google.com/uc?export=download&id=" + fileId;
        Log.d(TAG, "toDownloadUrl: converted to direct download URL: " + result);
        return result;
    }

    private String extractGoogleDriveFileId(String url) {
        Log.d(TAG, "extractGoogleDriveFileId called with: " + url);
        try {
            Uri uri = Uri.parse(url);
            String path = uri.getPath();

            if (path != null) {
                String marker = "/file/d/";
                int markerIndex = path.indexOf(marker);
                if (markerIndex >= 0) {
                    int start = markerIndex + marker.length();
                    int end = path.indexOf('/', start);
                    String id = end > start ? path.substring(start, end) : path.substring(start);
                    Log.d(TAG, "extractGoogleDriveFileId: found id in path: " + id);
                    return id;
                }
            }

            String queryId = uri.getQueryParameter("id");
            if (queryId != null && !queryId.isEmpty()) {
                Log.d(TAG, "extractGoogleDriveFileId: found id in query: " + queryId);
                return queryId;
            }
        } catch (Exception ignored) {
            Log.w(TAG, "extractGoogleDriveFileId: exception parsing URL: " + url + " -> " + ignored.getMessage());
            // Keep original URL when parsing fails.
        }

        return null;
    }
}
