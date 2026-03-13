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
    private EditText etOcpIndexUrl;
    private EditText etOcpQuestionsUrl;
    private EditText etOcpScriptUrl;
    private EditText etOcpStyleUrl;
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
        etOcpIndexUrl = findViewById(R.id.et_ocp_index_url);
        etOcpQuestionsUrl = findViewById(R.id.et_ocp_questions_url);
        etOcpScriptUrl = findViewById(R.id.et_ocp_script_url);
        etOcpStyleUrl = findViewById(R.id.et_ocp_style_url);
        btnSave = findViewById(R.id.btn_save);
        btnReset = findViewById(R.id.btn_reset);
    }

    private void loadCurrentSettings() {
        etMp3DataUrl.setText(dataUrlManager.getMp3DataUrl());
        etComicDataUrl.setText(dataUrlManager.getComicDataUrl());
        etPhotoDataUrl.setText(dataUrlManager.getPhotoDataUrl());
        etBookDataUrl.setText(dataUrlManager.getBookDataUrl());
        etOcpIndexUrl.setText(dataUrlManager.getOcpIndexDataUrl());
        etOcpQuestionsUrl.setText(dataUrlManager.getOcpQuestionsDataUrl());
        etOcpScriptUrl.setText(dataUrlManager.getOcpScriptDataUrl());
        etOcpStyleUrl.setText(dataUrlManager.getOcpStyleDataUrl());
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
        String ocpIndex = etOcpIndexUrl.getText().toString().trim();
        String ocpQuestions = etOcpQuestionsUrl.getText().toString().trim();
        String ocpScript = etOcpScriptUrl.getText().toString().trim();
        String ocpStyle = etOcpStyleUrl.getText().toString().trim();

        if (validateUrls(mp3Url, comicUrl, photoUrl, bookUrl)) {
            dataUrlManager.setMp3DataUrl(mp3Url);
            dataUrlManager.setComicDataUrl(comicUrl);
            dataUrlManager.setPhotoDataUrl(photoUrl);
            dataUrlManager.setBookDataUrl(bookUrl);
            // Save OCP URLs if provided
            if (!ocpIndex.isEmpty()) dataUrlManager.setOcpIndexDataUrl(ocpIndex);
            if (!ocpQuestions.isEmpty()) dataUrlManager.setOcpQuestionsDataUrl(ocpQuestions);
            if (!ocpScript.isEmpty()) dataUrlManager.setOcpScriptDataUrl(ocpScript);
            if (!ocpStyle.isEmpty()) dataUrlManager.setOcpStyleDataUrl(ocpStyle);
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
        etMp3DataUrl.setText("https://drive.google.com/file/d/1MCuzBSSmeVzsPP9IBy4LdUj02VEeA2FU/view?usp=sharing");
        etComicDataUrl.setText("https://drive.google.com/file/d/1JKKpAczBJ2jxl7yhAY7AVxyYfRembgFo/view?usp=sharing");
        etPhotoDataUrl.setText("https://drive.google.com/file/d/1p0slCvo3j543GWzG85J21Twy7nT49Y2Y/view?usp=sharing");
        etBookDataUrl.setText("https://drive.google.com/file/d/1tjla5WD0elmmpYQkYcIY1ApCoYPcUxQ-/view?usp=sharing");
        etOcpIndexUrl.setText("https://drive.google.com/file/d/13g0FeepW1XYc3qNR0d4kj6bAy2FauL5u/view?usp=sharing");
        etOcpQuestionsUrl.setText("https://drive.google.com/file/d/1HX5ZhT7IICMKciNzYexrDP1fIjRyGzoL/view?usp=sharing");
        etOcpScriptUrl.setText("https://drive.google.com/file/d/1uEdf0gu_rZxPfpt8kV_jEaRYyfVPUDxq/view?usp=sharing");
        etOcpStyleUrl.setText("https://drive.google.com/file/d/1nRg4o9pLWlxeFAYZyJYbCx6JQ2YGpzk9/view?usp=sharing");
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