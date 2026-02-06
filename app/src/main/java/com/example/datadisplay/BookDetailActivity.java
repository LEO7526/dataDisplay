package com.example.datadisplay;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

public class BookDetailActivity extends AppCompatActivity {

    TextView bookDetailText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_detail);

        bookDetailText = findViewById(R.id.bookDetailText);

        // Get values passed from BookActivity
        String name = getIntent().getStringExtra("name");
        String author = getIntent().getStringExtra("author");
        String content = getIntent().getStringExtra("content");
        String tag = getIntent().getStringExtra("tag");

        // 🔍 Debug logs
        Log.d("BOOK_DETAIL", "Received name: " + name);
        Log.d("BOOK_DETAIL", "Received author: " + author);
        Log.d("BOOK_DETAIL", "Received tag: " + tag);
        Log.d("BOOK_DETAIL", "Received content: " + content);

        // Build details string
        String details = "Book name: " + name +
                "\nAuthor: " + author +
                "\nTag: " + tag +
                "\n\n" + content;

        // ✅ Ensure multi-line display
        bookDetailText.setSingleLine(false);
        bookDetailText.setText(details);

        Log.d("BOOK_DETAIL", "Displayed details successfully.");

        DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
        NavigationView navigationView = findViewById(R.id.navigationView);

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, HomeActivity.class));
            } else if (id == R.id.nav_books) {
                startActivity(new Intent(this, BookActivity.class));
            } else if (id == R.id.nav_photos) {
                startActivity(new Intent(this, PhotoActivity.class));
            } else if (id == R.id.nav_settings) {

            }
            drawerLayout.closeDrawers();
            return true;
        });
    }
}