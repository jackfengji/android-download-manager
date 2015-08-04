package cn.kukool.downloader.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * User: jackfengji
 * Date: 15/6/25
 */
public class NetworkUtil {
    private static final String TAG = "NetworkUtil";
    public static boolean networkAvailable;

    // after ICS, android doesn't allow us to get the proxy, so we do not use it
    public static boolean checkNetwork2(Context context) {
        ConnectivityManager mgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifi = mgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifi != null && wifi.isConnected() && wifi.isAvailable()) {
            networkAvailable = true;
            return true;
        }

        NetworkInfo mobile = mgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        if (mobile != null && mobile.isConnected() && mobile.isAvailable()) {
            networkAvailable = true;
            return true;
        }

        networkAvailable = false;
        return false;
    }

    private static BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!intent.getAction().equalsIgnoreCase(ConnectivityManager.CONNECTIVITY_ACTION))
                return;

            if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)) {
                Log.debug(TAG, "-----> network changed no connectivity");
                networkAvailable = false;
                return;
            }

            checkNetwork2(context);
        }
    };

    public static void registerNetworkCheck(final Context context) {
        IntentFilter netStateIntentFilter = new IntentFilter();
        netStateIntentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(receiver, netStateIntentFilter);
        checkNetwork2(context);
    }

    public static void unRegisterNetworkCheck(Context context) {
        if (receiver != null) {
            context.unregisterReceiver(receiver);
        }
    }
}
