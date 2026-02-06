package com.example.datadisplay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import android.util.Log;

public class MediaButtonReceiver extends BroadcastReceiver {
    private static final String TAG = "MediaButtonReceiver";
    
    // Variables to track tap timing for double tap detection
    private static long lastClickTime = 0;
    private static final long DOUBLE_CLICK_TIME_DELTA = 300; // 300ms for double tap
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            
            if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                int keyCode = event.getKeyCode();
                Log.d(TAG, "Media button pressed: " + keyCode);
                
                switch (keyCode) {
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    case KeyEvent.KEYCODE_HEADSETHOOK:
                        handlePlayPauseButton(context);
                        break;
                    case KeyEvent.KEYCODE_MEDIA_NEXT:
                        handleNextButton(context);
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                        handlePreviousButton(context);
                        break;
                    default:
                        Log.d(TAG, "Unhandled media button: " + keyCode);
                        break;
                }
            }
        }
    }
    
    private void handlePlayPauseButton(Context context) {
        long currentTime = System.currentTimeMillis();
        
        if ((currentTime - lastClickTime) < DOUBLE_CLICK_TIME_DELTA) {
            // Double tap detected - skip to next track
            Log.d(TAG, "Double tap detected - Next track");
            Intent nextIntent = new Intent(context, RadioPlaybackService.class);
            nextIntent.setAction(RadioPlaybackService.ACTION_NEXT);
            context.startService(nextIntent);
        } else {
            // Single tap - play/pause
            Log.d(TAG, "Single tap detected - Play/Pause");
            Intent playPauseIntent = new Intent(context, RadioPlaybackService.class);
            playPauseIntent.setAction(RadioPlaybackService.ACTION_REQUEST_STATUS);
            context.startService(playPauseIntent);
            
            // Delay the play/pause action to allow for potential double tap
            android.os.Handler handler = new android.os.Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if ((System.currentTimeMillis() - lastClickTime) >= DOUBLE_CLICK_TIME_DELTA) {
                        // No second tap, execute play/pause
                        Intent toggleIntent = new Intent(context, RadioPlaybackService.class);
                        toggleIntent.setAction("ACTION_TOGGLE_PLAYBACK");
                        context.startService(toggleIntent);
                    }
                }
            }, DOUBLE_CLICK_TIME_DELTA);
        }
        
        lastClickTime = currentTime;
    }
    
    private void handleNextButton(Context context) {
        Log.d(TAG, "Next button pressed");
        Intent intent = new Intent(context, RadioPlaybackService.class);
        intent.setAction(RadioPlaybackService.ACTION_NEXT);
        context.startService(intent);
    }
    
    private void handlePreviousButton(Context context) {
        Log.d(TAG, "Previous button pressed");
        Intent intent = new Intent(context, RadioPlaybackService.class);
        intent.setAction(RadioPlaybackService.ACTION_PREVIOUS);
        context.startService(intent);
    }
}
