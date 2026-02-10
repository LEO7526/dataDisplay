package com.example.datadisplay.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

/**
 * Network utility class for checking WiFi connectivity and network status
 */
public class NetworkHelper {

    public enum NetworkType {
        WIFI,
        MOBILE,
        NONE
    }

    /**
     * Check if device is connected to WiFi
     * @param context Application context
     * @return true if connected to WiFi, false otherwise
     */
    public static boolean isWiFiConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;

            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } else {
            android.net.NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
        }
    }

    /**
     * Check if device has any network connection
     * @param context Application context
     * @return true if connected to any network, false otherwise
     */
    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;

            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            );
        } else {
            android.net.NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnected();
        }
    }

    /**
     * Get current network type
     * @param context Application context
     * @return NetworkType enum value
     */
    public static NetworkType getCurrentNetworkType(Context context) {
        if (!isNetworkConnected(context)) {
            return NetworkType.NONE;
        }
        if (isWiFiConnected(context)) {
            return NetworkType.WIFI;
        }
        return NetworkType.MOBILE;
    }

    /**
     * Register a network callback to monitor network changes
     * @param context Application context
     * @param callback Callback interface
     */
    public static void registerNetworkCallback(Context context, NetworkCallback callback) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    NetworkType type = getCurrentNetworkType(context);
                    callback.onNetworkChanged(type);
                }

                @Override
                public void onLost(Network network) {
                    callback.onNetworkChanged(NetworkType.NONE);
                }
            });
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    NetworkType type = getCurrentNetworkType(context);
                    callback.onNetworkChanged(type);
                }

                @Override
                public void onLost(Network network) {
                    callback.onNetworkChanged(NetworkType.NONE);
                }
            });
        }
    }

    /**
     * Callback interface for network changes
     */
    public interface NetworkCallback {
        void onNetworkChanged(NetworkType networkType);
    }

    /**
     * Get network status string for display
     * @param context Application context
     * @return Human-readable network status
     */
    public static String getNetworkStatusString(Context context) {
        NetworkType type = getCurrentNetworkType(context);
        switch (type) {
            case WIFI:
                return "Connected via WiFi";
            case MOBILE:
                return "Connected via Mobile Data";
            case NONE:
            default:
                return "No network connection";
        }
    }
}
