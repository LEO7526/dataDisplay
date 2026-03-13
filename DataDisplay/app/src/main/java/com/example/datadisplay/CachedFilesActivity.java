package com.example.datadisplay;

import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CachedFilesActivity extends AppCompatActivity {

    private static final String TAG = "CachedFilesActivity";
    private ListView cachedListView;
    private List<File> cachedFiles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cached_files);

        cachedListView = findViewById(R.id.cachedListView);

        loadCachedFiles();
    }

    private void loadCachedFiles() {
        File cacheDir = getCacheDir();
        File[] files = cacheDir.listFiles();
        cachedFiles = new ArrayList<>();

        if (files != null) {
            cachedFiles = Arrays.asList(files);
        }

        List<String> fileNames = new ArrayList<>();
        for (File f : cachedFiles) {
            fileNames.add(f.getName() + " (" + (f.length() / 1024) + " KB)");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, fileNames);
        cachedListView.setAdapter(adapter);

        cachedListView.setOnItemClickListener((parent, view, position, id) -> {
            File file = cachedFiles.get(position);
            new AlertDialog.Builder(this)
                    .setTitle("Delete cached file?")
                    .setMessage("Do you want to delete " + file.getName() + "?")
                    .setPositiveButton("Delete", (d, w) -> {
                        if (file.delete()) {
                            Toast.makeText(this, "Deleted " + file.getName(), Toast.LENGTH_SHORT).show();
                            loadCachedFiles(); // refresh list
                        } else {
                            Toast.makeText(this, "Failed to delete " + file.getName(), Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }
}