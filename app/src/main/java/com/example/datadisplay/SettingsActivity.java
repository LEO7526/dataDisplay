package com.example.datadisplay;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.datadisplay.utils.DataUrlManager;

public class SettingsActivity extends AppCompatActivity {
    
    private EditText etMp3DataUrl;
    private EditText etComicDataUrl;
    private EditText etPhotoDataUrl;
    private EditText etBookDataUrl;
    private Button btnSave;
    private Button btnReset;
    
    private DataUrlManager dataUrlManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        dataUrlManager = new DataUrlManager(this);
        
        setupToolbar();
        initViews();
        loadCurrentSettings();
        setupClickListeners();
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("JSON Data Sources");
        }
    }
    
    private void initViews() {
        etMp3DataUrl = findViewById(R.id.et_mp3_data_url);
        etComicDataUrl = findViewById(R.id.et_comic_data_url);
        etPhotoDataUrl = findViewById(R.id.et_photo_data_url);
        etBookDataUrl = findViewById(R.id.et_book_data_url);
        btnSave = findViewById(R.id.btn_save);
        btnReset = findViewById(R.id.btn_reset);
    }
    
    private void loadCurrentSettings() {
        etMp3DataUrl.setText(dataUrlManager.getMp3DataUrl());
        etComicDataUrl.setText(dataUrlManager.getComicDataUrl());
        etPhotoDataUrl.setText(dataUrlManager.getPhotoDataUrl());
        etBookDataUrl.setText(dataUrlManager.getBookDataUrl());
    }
    
    private void setupClickListeners() {
        btnSave.setOnClickListener(v -> saveSettings());
        btnReset.setOnClickListener(v -> resetToDefaults());
    }
    
    private void saveSettings() {
        String mp3Url = etMp3DataUrl.getText().toString().trim();
        String comicUrl = etComicDataUrl.getText().toString().trim();
        String photoUrl = etPhotoDataUrl.getText().toString().trim();
        String bookUrl = etBookDataUrl.getText().toString().trim();
        
        if (validateUrls(mp3Url, comicUrl, photoUrl, bookUrl)) {
            dataUrlManager.setMp3DataUrl(mp3Url);
            dataUrlManager.setComicDataUrl(comicUrl);
            dataUrlManager.setPhotoDataUrl(photoUrl);
            dataUrlManager.setBookDataUrl(bookUrl);
            dataUrlManager.clearCache();
            
            Toast.makeText(this, "Settings saved successfully! Please restart app to refresh data.", Toast.LENGTH_LONG).show();
            finish();
        }
    }
    
    private boolean validateUrls(String... urls) {
        for (String url : urls) {
            if (url.isEmpty() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                Toast.makeText(this, "Please enter valid URLs starting with http:// or https://", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        return true;
    }
    
    private void resetToDefaults() {
        DataUrlManager tempManager = new DataUrlManager(this);
        etMp3DataUrl.setText("https://drive.google.com/file/d/1MCuzBSSmeVzsPP9IBy4LdUj02VEeA2FU/view?usp=sharing");
        etComicDataUrl.setText("https://drive.google.com/file/d/1JKKpAczBJ2jxl7yhAY7AVxyYfRembgFo/view?usp=sharing");
        etPhotoDataUrl.setText("https://drive.google.com/file/d/1p0slCvo3j543GWzG85J21Twy7nT49Y2Y/view?usp=sharing");
        etBookDataUrl.setText("https://drive.google.com/file/d/1tjla5WD0elmmpYQkYcIY1ApCoYPcUxQ-/view?usp=sharing");
        Toast.makeText(this, "Reset to default URLs", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}