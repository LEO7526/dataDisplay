// File: PhotoDataCache.java
package com.example.datadisplay;

import com.example.datadisplay.models.PhotoData;

public class PhotoDataCache {
    private static PhotoDataCache instance;
    private PhotoData cachedData;

    private PhotoDataCache() {}

    public static synchronized PhotoDataCache getInstance() {
        if (instance == null) {
            instance = new PhotoDataCache();
        }
        return instance;
    }

    public PhotoData getData() {
        return cachedData;
    }

    public void setData(PhotoData data) {
        this.cachedData = data;
    }

    public void clear() {
        cachedData = null;
    }
}