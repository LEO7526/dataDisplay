package com.example.datadisplay;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

public class BookDetailActivity extends AppCompatActivity {

    private static final String TAG = "BookDetailActivity";

    TextView bookDetailText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_detail);

        bookDetailText = findViewById(R.id.bookDetailText);

        // Get values passed from BookActivity
        String title = getIntent().getStringExtra("title");
        String name = getIntent().getStringExtra("name");
        String author = getIntent().getStringExtra("author");
        String content = getIntent().getStringExtra("content");
        String tag = getIntent().getStringExtra("tag");

        String displayTitle = (title != null && !title.trim().isEmpty()) ? title : name;
        if (displayTitle == null) {
            displayTitle = "Unknown";
        }
        if (author == null) {
            author = "Unknown";
        }
        if (content == null) {
            content = "";
        }
        if (tag == null) {
            tag = "General";
        }

        Log.d(TAG, "🧭 onCreate route entry | title=" + displayTitle + " | author=" + author + " | tag=" + tag);

        // 🔍 Debug logs
        Log.d(TAG, "Received title: " + displayTitle);
        Log.d(TAG, "Received author: " + author);
        Log.d(TAG, "Received tag: " + tag);
        Log.d(TAG, "Received content: " + content);

        // Build details string
        String details = "Book name: " + displayTitle +
                "\nAuthor: " + author +
                "\nTag: " + tag +
                "\n\n" + content;

        // ✅ Ensure multi-line display
        bookDetailText.setSingleLine(false);
        bookDetailText.setText(details);

        Log.d(TAG, "Displayed details successfully.");

        DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
        NavigationView navigationView = findViewById(R.id.navigationView);
        ensureGamesMenuItem(navigationView);

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, HomeActivity.class));
            } else if (id == R.id.nav_books) {
                startActivity(new Intent(this, BookActivity.class));
            } else if (id == R.id.nav_photos) {
                startActivity(new Intent(this, PhotoActivity.class));
            } else if (id == R.id.nav_games) {
                startActivity(new Intent(this, GameHomeActivity.class));
            } else if (id == R.id.nav_settings) {

            }
            drawerLayout.closeDrawers();
            return true;
        });
    }

    private void ensureGamesMenuItem(NavigationView navigationView) {
        if (navigationView == null) {
            return;
        }
        Menu menu = navigationView.getMenu();
        if (menu.findItem(R.id.nav_games) == null) {
            menu.add(Menu.NONE, R.id.nav_games, 50, "Games").setIcon(R.drawable.ic_home);
        }
    }
}