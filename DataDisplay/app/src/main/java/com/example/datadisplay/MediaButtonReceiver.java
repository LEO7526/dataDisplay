package com.example.datadisplay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;

public class MediaButtonReceiver extends BroadcastReceiver {
    private static final String TAG = "MediaButtonReceiver";

    // Track timing for single-tap vs double-tap headset button behavior.
    private static long lastClickTime = 0;
    private static final long DOUBLE_CLICK_TIME_DELTA = 300;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            return;
        }

        KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) {
            return;
        }

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

    private void handlePlayPauseButton(Context context) {
        long currentTime = System.currentTimeMillis();

        if ((currentTime - lastClickTime) < DOUBLE_CLICK_TIME_DELTA) {
            Log.d(TAG, "Double tap detected - Next track");
            Intent nextIntent = new Intent(context, RadioPlaybackService.class);
            nextIntent.setAction(RadioPlaybackService.ACTION_NEXT);
            context.startService(nextIntent);
        } else {
            Log.d(TAG, "Single tap detected - Play/Pause");
            Intent requestIntent = new Intent(context, RadioPlaybackService.class);
            requestIntent.setAction(RadioPlaybackService.ACTION_REQUEST_STATUS);
            context.startService(requestIntent);

            android.os.Handler handler = new android.os.Handler();
            handler.postDelayed(() -> {
                if ((System.currentTimeMillis() - lastClickTime) >= DOUBLE_CLICK_TIME_DELTA) {
                    Intent toggleIntent = new Intent(context, RadioPlaybackService.class);
                    toggleIntent.setAction("ACTION_TOGGLE_PLAYBACK");
                    context.startService(toggleIntent);
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
