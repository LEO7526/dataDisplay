package com.example.datadisplay;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.datadisplay.adapters.ImagePagerAdapter;

import java.util.List;

public class ImagePagerActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private List<String> imageUrls;
    private int startPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_pager);

        // Get extras from intent
        imageUrls = getIntent().getStringArrayListExtra("image_urls");
        startPosition = getIntent().getIntExtra("start_position", 0);

        // Set up ViewPager2 with adapter
        viewPager = findViewById(R.id.viewPager);
        ImagePagerAdapter adapter = new ImagePagerAdapter(this, imageUrls);
        viewPager.setAdapter(adapter);

        // Jump to the tapped image
        viewPager.setCurrentItem(startPosition, false);

        // Preload adjacent pages for smoother swiping
        viewPager.setOffscreenPageLimit(2);
    }
}
