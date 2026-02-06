package com.example.datadisplay.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class DataUrlManager {
    private static final String PREF_NAME = "DataSourceSettings";
    private static final String KEY_MP3_URL = "mp3_data_url";
    private static final String KEY_COMIC_URL = "comic_data_url";
    private static final String KEY_PHOTO_URL = "photo_data_url";
    private static final String KEY_BOOK_URL = "book_data_url";
    
    // Default URLs from your current GitHub repository
    private static final String DEFAULT_MP3_URL = "https://raw.githubusercontent.com/leowong7527-spec/Data-Storage-/main/mp3_data.json";
    private static final String DEFAULT_COMIC_URL = "https://raw.githubusercontent.com/leowong7527-spec/Data-Storage-/main/comic_data.json";
    private static final String DEFAULT_PHOTO_URL = "https://raw.githubusercontent.com/leowong7527-spec/Data-Storage-/main/photo_data.json";
    private static final String DEFAULT_BOOK_URL = "https://raw.githubusercontent.com/leowong7527-spec/Data-Storage-/main/data.json";
    
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
    
    public void clearCache() {
        // Clear any cached data when URLs are updated
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong("last_update_time", System.currentTimeMillis());
        editor.apply();
    }
}
