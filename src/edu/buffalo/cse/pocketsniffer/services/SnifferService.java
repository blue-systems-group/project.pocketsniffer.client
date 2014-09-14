package edu.buffalo.cse.pocketsniffer.services;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;

import edu.buffalo.cse.pocketsniffer.tasks.ServerTask;
import edu.buffalo.cse.pocketsniffer.utils.Utils;

public class SnifferService extends Service {
    private final String TAG = Utils.getTag(this.getClass());

    private final int LISTEN_PORT = 8000;

    private static final String POCKETSNIFFER_SSID = "PocketSniffer";
    private static final String AP_PASSWORD = "LDR9OXnevs5lBlCjz0MNga2H40DlT2m0";
    private static final int MIN_RSSI_LEVEL = -80;

    private boolean mStarted = false;
    private Context mContext;
    private WifiManager mWifiManager;
    private ServerTask mServerTask;

    /**
     * Make sure the configuration of Pocketsniffer SSID exists, create one if
     * necessary.
     *
     * @return networkId of PocketSniffer network.
     */
    private int getNetworkId() {
        int networkId = -1;
        for (WifiConfiguration config : mWifiManager.getConfiguredNetworks()) {
            if (POCKETSNIFFER_SSID.equals(Utils.stripQuotes(config.SSID))) {
                if (networkId == -1) {
                    // reuse network id from previous configration.
                    networkId = config.networkId;
                }
                else {
                    // remove any duplicate entries
                    mWifiManager.removeNetwork(config.networkId);
                }
            }
        }

        WifiConfiguration target = new WifiConfiguration();
        target.SSID = Utils.addQuotes(POCKETSNIFFER_SSID);
        target.preSharedKey = Utils.addQuotes(AP_PASSWORD);
        target.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK, true);
        target.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN, true);

        if (networkId == -1) {
            networkId = mWifiManager.addNetwork(target);
        }
        else {
            target.networkId = networkId;
            mWifiManager.updateNetwork(target);
        }
        return networkId;
    }

    private void handleScanResult(Intent intent) {
        if (!mWifiManager.isWifiEnabled()) {
            Log.d(TAG, "Wifi disabled by user. Skipping.");
            return;
        }
        int networkId = getNetworkId();
        mWifiManager.enableNetwork(networkId, false /* do not disable others */);
    }


    private BroadcastReceiver mWifiReceiver = new BroadcastReceiver() {

        @Override
        public synchronized void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Intent fired, action is " + action);

            try {
                if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
                    handleScanResult(intent);
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to handle intent.", e);
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mStarted) {
            Log.v(TAG, "Not restarting started service.");
            return START_STICKY;
        }

        super.onStartCommand(intent, flags, startId); 
        Log.v(TAG, "======== Starting PocketSniffer Service ======== ");

        IntentFilter wifiIntentFilter = new IntentFilter();
        wifiIntentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        wifiIntentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        wifiIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        wifiIntentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        registerReceiver(mWifiReceiver, wifiIntentFilter);

        ServerTask.Params params = new ServerTask.Params();
        params.port = LISTEN_PORT;
        mServerTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);

        mStarted = true;
        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "======== Destroying PocketSniffer Service ========");

        unregisterReceiver(mWifiReceiver);
        mServerTask.cancel(false);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.v(TAG, "======== Creating PocketSniffer Service ========");

        mContext = this;
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mServerTask = new ServerTask(mContext, null);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
