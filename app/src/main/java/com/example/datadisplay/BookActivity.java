package com.example.datadisplay;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import android.util.JsonReader;
import android.util.JsonReader;
import android.util.JsonToken;
import java.nio.charset.StandardCharsets;

public class BookActivity extends AppCompatActivity {

    ListView listView;
    ArrayList<String> userList = new ArrayList<>();
    ArrayAdapter<String> adapter;

    JSONArray booksArray;
    FlexboxLayout tagContainer;
    LinearLayout collapsibleTagContainer;
    Button toggleTagsButton;

    Set<String> selectedTags = new HashSet<>();

    ArrayList<JSONObject> displayedBooks = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book);

        listView = findViewById(R.id.listView);
        tagContainer = findViewById(R.id.tagContainer);
        collapsibleTagContainer = findViewById(R.id.collapsibleTagContainer);
        toggleTagsButton = findViewById(R.id.toggleTagsButton);

        adapter = new ArrayAdapter<>(this, R.layout.list_item, userList);
        listView.setAdapter(adapter);

        // ✅ Get the file path passed from HomeActivity
        String jsonPath = getIntent().getStringExtra("json_path");
        if (jsonPath != null) {
            File jsonFile = new File(jsonPath);
            if (jsonFile.exists()) {
                parseJson(jsonFile);   // stream parse directly from file
            } else {
                Log.e("BookActivity", "JSON file not found at " + jsonPath);
            }
        } else {
            Log.e("BookActivity", "No json_path extra received");
        }

        // Toggle show/hide tags
        toggleTagsButton.setOnClickListener(v -> {
            if (collapsibleTagContainer.getVisibility() == View.GONE) {
                collapsibleTagContainer.setVisibility(View.VISIBLE);
                toggleTagsButton.setText("Hide Tags");
            } else {
                collapsibleTagContainer.setVisibility(View.GONE);
                toggleTagsButton.setText("Show Tags");
            }
        });

        // Handle book clicks
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (booksArray != null) {
                try {
                    JSONObject clickedObj = displayedBooks.get(position);

                    String name = clickedObj.optString("name");
                    String author = clickedObj.optString("author");
                    String content = clickedObj.optString("content");

                    if (looksLikeHash(content)) {
                        content = "[HASH] " + content;
                    } else {
                        content = content.replace("\\n", "\n");
                    }

                    String tagValue = "";
                    if (clickedObj.has("tag")) {
                        Object tagObj = clickedObj.get("tag");
                        if (tagObj instanceof JSONArray) {
                            JSONArray tagArray = (JSONArray) tagObj;
                            ArrayList<String> tags = new ArrayList<>();
                            for (int j = 0; j < tagArray.length(); j++) {
                                tags.add(tagArray.optString(j));
                            }
                            tagValue = String.join(", ", tags);
                        } else {
                            tagValue = clickedObj.optString("tag", "General");
                        }
                    } else {
                        tagValue = "General";
                    }

                    Intent intent = new Intent(BookActivity.this, BookDetailActivity.class);
                    intent.putExtra("name", name);
                    intent.putExtra("author", author);
                    intent.putExtra("content", content);
                    intent.putExtra("tag", tagValue);
                    startActivity(intent);

                } catch (Exception e) {
                    Log.e("BOOK_CLICK", "Error handling item click", e);
                }
            }
        });

        DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
        NavigationView navigationView = findViewById(R.id.navigationView);

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, HomeActivity.class));
            } else if (id == R.id.nav_books) {
                drawerLayout.closeDrawers();
            } else if (id == R.id.nav_photos) {
                startActivity(new Intent(this, PhotoActivity.class));
            } else if (id == R.id.nav_settings) {
                // TODO: implement settings
            }
            drawerLayout.closeDrawers();
            return true;
        });
    }

    // Parse JSON directly from file and populate both userList and displayedBooks
    private void parseJson(File jsonFile) {
        try (FileInputStream fis = new FileInputStream(jsonFile);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             JsonReader reader = new JsonReader(isr)) {

            booksArray = new JSONArray();
            userList.clear();
            displayedBooks.clear();
            Set<String> uniqueTags = new HashSet<>();

            reader.beginArray();
            while (reader.hasNext()) {
                reader.beginObject();

                String name = "Unknown";
                String author = "";
                String content = "";
                ArrayList<String> tags = new ArrayList<>();

                while (reader.hasNext()) {
                    String key = reader.nextName();
                    switch (key) {
                        case "name":
                            name = reader.nextString();
                            break;
                        case "author":
                            author = reader.nextString();
                            break;
                        case "content":
                            content = reader.nextString();
                            break;
                        case "tag":
                            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                                reader.beginArray();
                                while (reader.hasNext()) {
                                    String t = reader.nextString();
                                    tags.add(t);
                                    uniqueTags.add(t);
                                }
                                reader.endArray();
                            } else {
                                String t = reader.nextString();
                                tags.add(t);
                                uniqueTags.add(t);
                            }
                            break;
                        default:
                            reader.skipValue();
                    }
                }

                JSONObject obj = new JSONObject();
                obj.put("name", name);
                obj.put("author", author);
                obj.put("content", content);
                if (!tags.isEmpty()) obj.put("tag", new JSONArray(tags));

                booksArray.put(obj);
                userList.add(limitWords(name, 10));
                displayedBooks.add(obj);

                reader.endObject();
            }
            reader.endArray();

            adapter.notifyDataSetChanged();
            setupTags(uniqueTags);

        } catch (Exception e) {
            Log.e("JSON_PARSE", "Error parsing JSON with JsonReader", e);
        }
    }



    private String limitWords(String text, int maxWords) {
        String[] words = text.split("\\s+");
        if (words.length <= maxWords) return text;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxWords; i++) {
            sb.append(words[i]).append(" ");
        }
        return sb.toString().trim() + "...";
    }

    private void setupTags(Set<String> tags) {
        tagContainer.removeAllViews();

        for (String tag : tags) {
            Button tagButton = new Button(this);
            tagButton.setText(tag);
            tagButton.setAllCaps(false);
            tagButton.setBackgroundTintList(ColorStateList.valueOf(Color.LTGRAY));

            FlexboxLayout.LayoutParams params =
                    new FlexboxLayout.LayoutParams(
                            FlexboxLayout.LayoutParams.WRAP_CONTENT,
                            FlexboxLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(8, 8, 8, 8);
            tagButton.setLayoutParams(params);

            tagButton.setOnClickListener(v -> {
                if (selectedTags.contains(tag)) {
                    selectedTags.remove(tag);
                    tagButton.setBackgroundTintList(ColorStateList.valueOf(Color.LTGRAY));
                } else {
                    selectedTags.add(tag);
                    tagButton.setBackgroundTintList(ColorStateList.valueOf(Color.CYAN));
                }
                filterBooksByTags();
            });

            tagContainer.addView(tagButton);
        }

        // "All" button
        Button allButton = new Button(this);
        allButton.setText("All");
        allButton.setAllCaps(false);

        FlexboxLayout.LayoutParams params =
                new FlexboxLayout.LayoutParams(
                        FlexboxLayout.LayoutParams.WRAP_CONTENT,
                        FlexboxLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(8, 8, 8, 8);
        allButton.setLayoutParams(params);

        allButton.setOnClickListener(v -> {
            selectedTags.clear();
            showAllBooks();
        });

        tagContainer.addView(allButton, 0);
    }

    private void filterBooksByTags() {
        userList.clear();
        displayedBooks.clear();

        for (int i = 0; i < booksArray.length(); i++) {
            JSONObject obj = booksArray.optJSONObject(i);
            if (obj != null) {
                try {
                    Set<String> bookTags = new HashSet<>();
                    Object tagObj = obj.opt("tag");

                    if (tagObj instanceof JSONArray) {
                        JSONArray tagArray = (JSONArray) tagObj;
                        for (int j = 0; j < tagArray.length(); j++) {
                            bookTags.add(tagArray.optString(j));
                        }
                    } else if (tagObj != null) {
                        bookTags.add(obj.optString("tag"));
                    }

                    if (selectedTags.isEmpty() || bookTags.containsAll(selectedTags)) {
                        userList.add(obj.optString("name") + " - Author: " + obj.optString("author"));
                        displayedBooks.add(obj);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        adapter.notifyDataSetChanged();
    }

    private void showAllBooks() {
        userList.clear();
        displayedBooks.clear();

        for (int i = 0; i < booksArray.length(); i++) {
            JSONObject obj = booksArray.optJSONObject(i);
            if (obj != null) {
                userList.add(obj.optString("name"));
                displayedBooks.add(obj);
            }
        }
        adapter.notifyDataSetChanged();
    }

    // Helper: detect if string looks like a hash
    private boolean looksLikeHash(String s) {
        return s != null &&
                (
                        s.matches("^[a-fA-F0-9]{32,64}$") ||        // Hex (MD5/SHA-1/SHA-256)
                                s.matches("^[A-Za-z0-9+/=]{20,}$")          // Base64-like
                );
    }


    private String loadJsonFromCache(String filename) {
        try {
            File jsonFile = new File(getCacheDir(), filename);
            if (!jsonFile.exists()) return null;

            FileInputStream fis = new FileInputStream(jsonFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            reader.close();
            return builder.toString();
        } catch (Exception e) {
            Log.e("BookActivity", "Error loading JSON from cache", e);
            return null;
        }
    }
}