package com.example.datadisplay;

import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

public class OcpQuizActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocp_quiz);

        webView = findViewById(R.id.ocp_webview);

        // Enable WebView debugging for easier inspection on emulator
        WebView.setWebContentsDebuggingEnabled(true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        // Enable DOM storage so localStorage works for local files
        settings.setDomStorageEnabled(true);

        // Optional: enable other storage features that some apps rely on
        settings.setDatabaseEnabled(true);

        // Provide a WebChromeClient so console messages and dialogs work
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        // Preferred filename in the app downloads folder (matches HomeActivity ensureFile)
        String filename = "index.html";
        File indexFile = new File(getExternalFilesDir("Downloads"), filename);

        if (indexFile.exists() && indexFile.length() > 0) {
            String url = "file://" + indexFile.getAbsolutePath();
            webView.loadUrl(url);
            return;
        }

        // Try to use intent extra json_path if provided (HomeActivity sometimes passes json_path)
        String extraPath = getIntent().getStringExtra("json_path");
        if (extraPath != null) {
            File extraFile = new File(extraPath);
            if (extraFile.exists()) {
                webView.loadUrl("file://" + extraFile.getAbsolutePath());
                return;
            }
        }

        Toast.makeText(this, "OCP quiz files not ready yet. Please download from Home.", Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

}
