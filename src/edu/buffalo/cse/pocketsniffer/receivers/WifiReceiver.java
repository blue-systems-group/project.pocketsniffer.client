package edu.buffalo.cse.pocketsniffer.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import edu.buffalo.cse.pocketsniffer.utils.Utils;

public class WifiReceiver extends BroadcastReceiver {
    public static final String TAG = Utils.getTag(WifiReceiver.class);


    private static final int MIN_RSSI_LEVEL = -80;

    private WifiManager mWifiManager;
    private Context mContext;


    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Intent fired, action is " + action);

        if (mWifiManager == null) {
            mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        }
        if (mContext == null) {
            mContext = context;
        }

        try {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
                handleScanResult(intent);
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to handle intent.", e);
        }
    }
}
