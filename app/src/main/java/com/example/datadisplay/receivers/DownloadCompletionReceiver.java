package com.example.datadisplay.receivers;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.datadisplay.managers.OfflineDownloadManager;

/**
 * BroadcastReceiver to handle download completion events
 */
public class DownloadCompletionReceiver extends BroadcastReceiver {
    
    private static final String TAG = "DownloadCompletion";
    private static OfflineDownloadManager downloadManager;
    
    public static void setDownloadManager(OfflineDownloadManager manager) {
        downloadManager = manager;
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            
            Log.d(TAG, "📥 Download completed - ID: " + downloadId);
            
            if (downloadId != -1 && downloadManager != null) {
                downloadManager.onDownloadComplete(downloadId);
            } else if (downloadManager == null) {
                Log.w(TAG, "⚠️ DownloadManager not set, creating new instance");
                downloadManager = new OfflineDownloadManager(context);
                downloadManager.onDownloadComplete(downloadId);
            }
        }
    }
}
