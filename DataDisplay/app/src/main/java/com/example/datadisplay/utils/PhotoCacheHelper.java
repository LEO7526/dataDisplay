package com.example.datadisplay.utils;

import android.content.Context;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

/**
 * Utility class for caching photo JSON and images.
 * - Saves/loads JSON to internal storage
 * - Uses Glide for automatic image caching
 */
public class PhotoCacheHelper {

    private static final String PHOTO_JSON_FILE = "photo_data.json";

    // Save JSON string to internal storage
    public static void saveJsonToCache(Context context, String json) {
        try (FileOutputStream fos = context.openFileOutput(PHOTO_JSON_FILE, Context.MODE_PRIVATE)) {
            fos.write(json.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Load JSON string from internal storage
    public static String loadJsonFromCache(Context context) {
        try (FileInputStream fis = context.openFileInput(PHOTO_JSON_FILE);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis))) {

            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();

        } catch (Exception e) {
            return null; // no cache available
        }
    }

    // Clear cached JSON
    public static void clearJsonCache(Context context) {
        context.deleteFile(PHOTO_JSON_FILE);
    }

    // Load image with Glide (caches automatically)
    public static void loadImage(Context context, String url, android.widget.ImageView imageView) {
        Glide.with(context)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL) // cache original + resized
                .into(imageView);
    }
}