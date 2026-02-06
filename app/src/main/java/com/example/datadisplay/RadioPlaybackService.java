package com.example.datadisplay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.ComponentName;
import android.media.MediaPlayer;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.List;
import java.util.Random;

public class RadioPlaybackService extends Service {

    private static final String TAG = "RadioPlaybackService";

    public static final String CHANNEL_ID = "RadioPlaybackChannelV2";
    private MediaPlayer mediaPlayer;
    private MediaSessionCompat mediaSession;
    private List<String> allUrls;

    private List<String> allTitles;   // ✅ new

    private String currentUrl;
    private boolean isShuffling = false;
    private boolean isLooping = false;

    private String currentTitle;

    public static final String ACTION_PLAY = "ACTION_PLAY";
    public static final String ACTION_PAUSE = "ACTION_PAUSE";
    public static final String ACTION_SHUFFLE = "ACTION_SHUFFLE";
    public static final String ACTION_LOOP = "ACTION_LOOP";
    public static final String ACTION_STATUS = "ACTION_STATUS";
    public static final String ACTION_PROGRESS = "ACTION_PROGRESS";
    public static final String ACTION_SEEK = "ACTION_SEEK";
    public static final String ACTION_TRACK_CHANGED = "ACTION_TRACK_CHANGED";
    public static final String ACTION_REQUEST_STATUS = "ACTION_REQUEST_STATUS";

    public static final String ACTION_NEXT = "ACTION_NEXT";
    public static final String ACTION_PREVIOUS = "ACTION_PREVIOUS";

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    
    @Override
    public void onCreate() {
        super.onCreate();
        initializeMediaSession();
    }
    
    private void initializeMediaSession() {
        ComponentName mediaButtonReceiver = new ComponentName(getApplicationContext(), MediaButtonReceiver.class);
        mediaSession = new MediaSessionCompat(this, "RadioPlaybackService", mediaButtonReceiver, null);
        
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                    mediaPlayer.start();
                    broadcastStatus(true);
                    progressHandler.post(progressRunnable);
                    showMediaNotification(true);
                }
            }
            
            @Override
            public void onPause() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    broadcastStatus(false);
                    progressHandler.removeCallbacks(progressRunnable);
                    showMediaNotification(false);
                }
            }
            
            @Override
            public void onSkipToNext() {
                playNextTrack();
            }
            
            @Override
            public void onSkipToPrevious() {
                playPreviousTrack();
            }
            
            @Override
            public void onSeekTo(long position) {
                if (mediaPlayer != null) {
                    mediaPlayer.seekTo((int) position);
                }
            }
        });
        
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setActive(true);
        updatePlaybackState();
    }
    
    private void updatePlaybackState() {
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY |
                           PlaybackStateCompat.ACTION_PAUSE |
                           PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                           PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                           PlaybackStateCompat.ACTION_SEEK_TO);
        
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING,
                    mediaPlayer.getCurrentPosition(), 1.0f);
        } else {
            stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED,
                    mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0, 1.0f);
        }
        
        mediaSession.setPlaybackState(stateBuilder.build());
    }
    
    private void updateMediaMetadata() {
        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, 
                          currentTitle != null ? currentTitle : "Unknown")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Radio Stream")
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, currentUrl);
                
        if (mediaPlayer != null) {
            metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 
                                   mediaPlayer.getDuration());
        }
        
        mediaSession.setMetadata(metadataBuilder.build());
    }

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                int pos = mediaPlayer.getCurrentPosition();
                int dur = mediaPlayer.getDuration();
                Intent progressIntent = new Intent(ACTION_PROGRESS);
                progressIntent.setPackage(getPackageName());
                progressIntent.putExtra("position", pos);
                progressIntent.putExtra("duration", dur);
                sendBroadcast(progressIntent);
                progressHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        // 🔒 Defensive check
        if (intent == null) {
            // Service was restarted by the system with no intent
            // Decide what to do: keep playing, or just stay alive
            return START_STICKY;
        }

        String action = intent.getAction();
        if (action != null) {
            switch (action) {
                case ACTION_PLAY:
                    if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                        mediaPlayer.start();
                        broadcastStatus(true);
                        progressHandler.post(progressRunnable);
                        showMediaNotification(true);
                        updatePlaybackState();
                    }
                    return START_STICKY;

                case ACTION_PAUSE:
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        mediaPlayer.pause();
                        broadcastStatus(false);
                        progressHandler.removeCallbacks(progressRunnable);
                        showMediaNotification(false);
                        updatePlaybackState();
                    }
                    return START_STICKY;

                case "ACTION_TOGGLE_PLAYBACK":
                    if (mediaPlayer != null) {
                        if (mediaPlayer.isPlaying()) {
                            mediaPlayer.pause();
                            broadcastStatus(false);
                            progressHandler.removeCallbacks(progressRunnable);
                            showMediaNotification(false);
                        } else {
                            mediaPlayer.start();
                            broadcastStatus(true);
                            progressHandler.post(progressRunnable);
                            showMediaNotification(true);
                        }
                        updatePlaybackState();
                    }
                    return START_STICKY;

                case ACTION_LOOP:
                    isLooping = intent.getBooleanExtra("loop", false);
                    return START_STICKY;

                case ACTION_SHUFFLE:
                    isShuffling = intent.getBooleanExtra("shuffle", false);
                    return START_STICKY;

                case ACTION_SEEK:
                    int newPos = intent.getIntExtra("position", 0);
                    if (mediaPlayer != null) {
                        mediaPlayer.seekTo(newPos);
                    }
                    return START_STICKY;

                case ACTION_REQUEST_STATUS:
                    broadcastStatus(mediaPlayer != null && mediaPlayer.isPlaying());
                    if (currentUrl != null) {
                        broadcastTrackChanged(currentUrl);
                    }
                    showMediaNotification(mediaPlayer != null && mediaPlayer.isPlaying());
                    return START_STICKY;

                case ACTION_NEXT:
                    playNextTrack();
                    return START_STICKY;

                case ACTION_PREVIOUS:
                    playPreviousTrack();
                    return START_STICKY;
            }
        }

        // Initial playback setup (only runs if intent != null)
        currentUrl = intent.getStringExtra("url");
        currentTitle = intent.getStringExtra("title");
        allUrls = intent.getStringArrayListExtra("allUrls");
        allTitles = intent.getStringArrayListExtra("allTitles"); // ✅
        isShuffling = intent.getBooleanExtra("shuffle", false);
        isLooping = intent.getBooleanExtra("loop", false);

        playTrack(currentUrl);
        return START_STICKY;
    }

    private void playNextTrack() {
        if (isShuffling) {
            playNextRandomTrack();
            return;
        }
        if (allUrls == null || allUrls.isEmpty()) return;

        int currentIndex = allUrls.indexOf(currentUrl);
        if (currentIndex == -1) currentIndex = 0;

        int nextIndex = (currentIndex + 1) % allUrls.size();
        currentUrl = allUrls.get(nextIndex);

        // ✅ Update title
        if (allTitles != null && nextIndex < allTitles.size()) {
            currentTitle = allTitles.get(nextIndex);
        }

        playTrack(currentUrl);
    }

    private void playPreviousTrack() {
        if (isShuffling) {
            playNextRandomTrack();
            return;
        }
        if (allUrls == null || allUrls.isEmpty()) return;

        int currentIndex = allUrls.indexOf(currentUrl);
        if (currentIndex == -1) currentIndex = 0;

        int prevIndex = (currentIndex - 1 + allUrls.size()) % allUrls.size();
        currentUrl = allUrls.get(prevIndex);

        // ✅ Update title
        if (allTitles != null && prevIndex < allTitles.size()) {
            currentTitle = allTitles.get(prevIndex);
        }

        playTrack(currentUrl);
    }

    private void playNextRandomTrack() {
        if (allUrls == null || allUrls.isEmpty()) return;
        
        // Avoid infinite loop if only 1 track
        if (allUrls.size() == 1) {
            playTrack(allUrls.get(0));
            return;
        }
        
        int randomIndex;
        String nextUrl;
        int attempts = 0;
        final int MAX_ATTEMPTS = 10;
        do {
            randomIndex = new Random().nextInt(allUrls.size());
            nextUrl = allUrls.get(randomIndex);
            attempts++;
        } while (nextUrl.equals(currentUrl) && attempts < MAX_ATTEMPTS);

        currentUrl = nextUrl;

        // ✅ Update title
        if (allTitles != null && randomIndex < allTitles.size()) {
            currentTitle = allTitles.get(randomIndex);
        }

        playTrack(nextUrl);
    }






    private void playTrack(String url) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.reset();
            } else {
                mediaPlayer = new MediaPlayer();
            }

            mediaPlayer.setDataSource(url);
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                broadcastStatus(true);
                broadcastTrackChanged(url);
                updatePlaybackState();
                updateMediaMetadata();
                progressHandler.removeCallbacks(progressRunnable);
                progressHandler.post(progressRunnable);
                showMediaNotification(true);
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                broadcastStatus(false);
                progressHandler.removeCallbacks(progressRunnable);

                if (isShuffling && allUrls != null && allUrls.size() > 1) {
                    playNextRandomTrack();
                } else if (isLooping) {
                    mediaPlayer.seekTo(0);
                    mediaPlayer.start();
                    broadcastStatus(true);
                    progressHandler.post(progressRunnable);
                    showMediaNotification(true);
                } else {
                    showMediaNotification(false);
                }
            });
            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            Log.e(TAG, "Error playing track: " + e.getMessage(), e);
            broadcastStatus(false);
            showMediaNotification(false);
            if (isShuffling && allUrls != null && allUrls.size() > 1) {
                playNextRandomTrack();
            }
        }
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Radio Playback Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void showMediaNotification(boolean isPlaying) {
        Log.d(TAG, "showMediaNotification called. isPlaying=" + isPlaying
                + ", currentUrl=" + currentUrl);

        PendingIntent prevIntent = PendingIntent.getService(
                this, 0,
                new Intent(this, RadioPlaybackService.class).setAction(ACTION_PREVIOUS),
                PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent playPauseIntent = PendingIntent.getService(
                this, 1,
                new Intent(this, RadioPlaybackService.class).setAction(isPlaying ? ACTION_PAUSE : ACTION_PLAY),
                PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent nextIntent = PendingIntent.getService(
                this, 2,
                new Intent(this, RadioPlaybackService.class).setAction(ACTION_NEXT),
                PendingIntent.FLAG_IMMUTABLE
        );

        // ✅ Use the real title if available
        String title = (currentTitle != null && !currentTitle.isEmpty())
                ? currentTitle
                : "Unknown";

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Now Playing")
                .setContentText(title)
                .setSmallIcon(R.drawable.ic_radio)
                .addAction(R.drawable.ic_previous, "Previous", prevIntent)
                .addAction(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play,
                        isPlaying ? "Pause" : "Play", playPauseIntent)
                .addAction(R.drawable.ic_next, "Next", nextIntent)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(true)
                .build();

        startForeground(1, notification);
        Log.d(TAG, "Notification posted to foreground service.");
    }

    private void broadcastStatus(boolean isPlaying) {
        // Send broadcast to your activity
        Intent statusIntent = new Intent(ACTION_STATUS);
        statusIntent.setPackage(getPackageName());
        statusIntent.putExtra("isPlaying", isPlaying);
        sendBroadcast(statusIntent);

        // Also update the notification at the same time
        showMediaNotification(isPlaying);
    }

    private void broadcastTrackChanged(String url) {
        Intent intent = new Intent(ACTION_TRACK_CHANGED);
        intent.setPackage(getPackageName());
        intent.putExtra("url", url);
        intent.putExtra("title", currentTitle != null ? currentTitle : "Unknown"); // 🔑
        sendBroadcast(intent);

        boolean playing = mediaPlayer != null && mediaPlayer.isPlaying();
        showMediaNotification(playing);
    }

    @Override
    public void onDestroy() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        progressHandler.removeCallbacks(progressRunnable);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}