package com.example.datadisplay;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;


public class RadioDetailActivity extends AppCompatActivity {

    private static final String TAG = "RadioDetailActivity";

    private String currentUrl;
    private List<String> allUrls;
    private boolean isShuffling = false;
    private boolean isLooping = false;
    private boolean isPlaying = false;

    private TextView titleText;
    private ImageButton playPauseButton;
    private ImageButton loopButton;
    private ImageButton shuffleButton;
    private SeekBar progressBar;
    private TextView elapsedTimeText;
    private TextView totalTimeText;

    private int trackDuration = 0;

    public static final String ACTION_PLAY = "ACTION_PLAY";
    public static final String ACTION_PAUSE = "ACTION_PAUSE";
    public static final String ACTION_SHUFFLE = "ACTION_SHUFFLE";
    public static final String ACTION_LOOP = "ACTION_LOOP";
    public static final String ACTION_STATUS = "ACTION_STATUS";
    public static final String ACTION_PROGRESS = "ACTION_PROGRESS";
    public static final String ACTION_SEEK = "ACTION_SEEK";
    public static final String ACTION_REQUEST_STATUS = "ACTION_REQUEST_STATUS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_radio_detail);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            } else {
                checkNotificationEnabled();
            }
        } else {
            checkNotificationEnabled();
        }

        titleText = findViewById(R.id.titleText);
        playPauseButton = findViewById(R.id.playPauseButton);
        loopButton = findViewById(R.id.loopButton);
        shuffleButton = findViewById(R.id.shuffleButton);
        progressBar = findViewById(R.id.progressBar);
        elapsedTimeText = findViewById(R.id.elapsedTimeText);
        totalTimeText = findViewById(R.id.totalTimeText);

        String title = getIntent().getStringExtra("title");
        currentUrl = getIntent().getStringExtra("url");
        allUrls = getIntent().getStringArrayListExtra("allUrls");

        titleText.setText(title != null ? title : "Unknown Track");

        loopButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        shuffleButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));

        playPauseButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, RadioPlaybackService.class);
            intent.setAction(isPlaying ? ACTION_PAUSE : ACTION_PLAY);
            startService(intent);
        });

        loopButton.setOnClickListener(v -> {
            isLooping = !isLooping;
            Intent intent = new Intent(this, RadioPlaybackService.class);
            intent.setAction(ACTION_LOOP);
            intent.putExtra("loop", isLooping);
            startService(intent);
            updateLoopUI();
        });

        shuffleButton.setOnClickListener(v -> {
            isShuffling = !isShuffling;
            Intent intent = new Intent(this, RadioPlaybackService.class);
            intent.setAction(ACTION_SHUFFLE);
            intent.putExtra("shuffle", isShuffling);
            startService(intent);
            updateShuffleUI();
        });

        ImageButton nextButton = findViewById(R.id.nextButton);
        nextButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, RadioPlaybackService.class);
            intent.setAction("ACTION_NEXT");
            startService(intent);
        });

        ImageButton previousButton = findViewById(R.id.previousButton);
        previousButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, RadioPlaybackService.class);
            intent.setAction(RadioPlaybackService.ACTION_PREVIOUS);
            startService(intent);
        });

        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            boolean fromUserChange = false;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                fromUserChange = fromUser;
                if (fromUser) {
                    elapsedTimeText.setText(formatTime(progress));
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (fromUserChange) {
                    Intent intent = new Intent(RadioDetailActivity.this, RadioPlaybackService.class);
                    intent.setAction(ACTION_SEEK);
                    intent.putExtra("position", seekBar.getProgress());
                    startService(intent);
                }
            }
        });

        startPlaybackService();
    }

    private void startPlaybackService() {
        Intent serviceIntent = new Intent(this, RadioPlaybackService.class);
        serviceIntent.putExtra("url", currentUrl);
        serviceIntent.putExtra("title", getIntent().getStringExtra("title"));
        serviceIntent.putStringArrayListExtra("allUrls", new ArrayList<>(allUrls));
        serviceIntent.putStringArrayListExtra("allTitles", getIntent().getStringArrayListExtra("allTitles")); // ✅ forward titles
        serviceIntent.putExtra("shuffle", isShuffling);
        serviceIntent.putExtra("loop", isLooping);
        startService(serviceIntent);
    }

    private void updateLoopUI() {
        loopButton.setBackgroundResource(isLooping ?
                R.drawable.bg_oval_highlight :
                android.R.color.transparent);
    }

    private void updateShuffleUI() {
        shuffleButton.setBackgroundResource(isShuffling ?
                R.drawable.bg_oval_highlight :
                android.R.color.transparent);
    }

    private String formatTime(int millis) {
        int seconds = millis / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private final BroadcastReceiver playbackReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_STATUS.equals(intent.getAction())) {
                isPlaying = intent.getBooleanExtra("isPlaying", false);
                playPauseButton.setImageResource(isPlaying ?
                        R.drawable.ic_pause : R.drawable.ic_play);
            }
        }
    };

    private final BroadcastReceiver progressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_PROGRESS.equals(intent.getAction())) {
                int pos = intent.getIntExtra("position", 0);
                int dur = intent.getIntExtra("duration", 0);
                if (dur > 0) {
                    trackDuration = dur;
                    progressBar.setMax(trackDuration);
                    totalTimeText.setText(formatTime(trackDuration));
                }
                progressBar.setProgress(pos);
                elapsedTimeText.setText(formatTime(pos));
            }
        }
    };

    private final BroadcastReceiver trackChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (RadioPlaybackService.ACTION_TRACK_CHANGED.equals(intent.getAction())) {
                String newTitle = intent.getStringExtra("title");
                currentUrl = intent.getStringExtra("url");
                titleText.setText(newTitle);
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(playbackReceiver, new IntentFilter(ACTION_STATUS), Context.RECEIVER_NOT_EXPORTED);
        registerReceiver(progressReceiver, new IntentFilter(ACTION_PROGRESS), Context.RECEIVER_NOT_EXPORTED);
        registerReceiver(trackChangedReceiver, new IntentFilter(RadioPlaybackService.ACTION_TRACK_CHANGED), Context.RECEIVER_NOT_EXPORTED);

        // Request current status when resuming
        Intent request = new Intent(this, RadioPlaybackService.class);
        request.setAction(ACTION_REQUEST_STATUS);
        startService(request);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(playbackReceiver);
        unregisterReceiver(progressReceiver);
        unregisterReceiver(trackChangedReceiver);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "POST_NOTIFICATIONS permission granted");
                checkNotificationEnabled();
            } else {
                Log.d(TAG, "POST_NOTIFICATIONS permission denied");
                checkNotificationEnabled();
            }
        }
    }

    private void checkNotificationEnabled() {
        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        if (!manager.areNotificationsEnabled()) {
            // Show a dialog first, then open settings
            new AlertDialog.Builder(this)
                    .setTitle("Enable Notifications")
                    .setMessage("Notifications are currently blocked. Enable them to see playback controls.")
                    .setPositiveButton("Open Settings", (d, w) -> {
                        Intent intent = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }
}