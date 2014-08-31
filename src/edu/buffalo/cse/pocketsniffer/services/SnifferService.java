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
import edu.buffalo.cse.pocketsniffer.tasks.SnifTask;
import edu.buffalo.cse.pocketsniffer.utils.Utils;

public class SnifferService extends Service {
    private final String TAG = Utils.getTag(this.getClass());

    // wifi configuration
    private final String POCKETSNIFFER_SSID = "PocketSniffer";
    private final String PASSWORD = "AUDeORc1haIwvHuM6oeJOYhMAFYTffeC";
    private final int PRIORITY = 99999999;
    private final int LISTEN_PORT = 8000;

    private final int MIN_RSSI_LEVEL = -85;

    private boolean mStarted = false;
    private Context mContext;
    private WifiManager mWifiManager;
    private ServerTask mServerTask;

    private BroadcastReceiver mScanResultReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            Log.d(TAG, "Intent fired, action is " + action);

            if (!WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
                Log.d(TAG, "Unrelated intent. Skipping.");
                return;
            }

            if (!mWifiManager.isWifiEnabled()) {
                Log.d(TAG, "Wifi disabled by user. Skipping.");
                return;
            }

            if (Utils.hasNetworkConnection(mContext, ConnectivityManager.TYPE_WIFI)) {
                WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
                String currentSSID = Utils.stripQuotes(wifiInfo.getSSID());
                if (currentSSID.equals(POCKETSNIFFER_SSID)) {
                    Log.d(TAG, "Already connected to SSID " + POCKETSNIFFER_SSID + ". Skipping.");
                    return;
                }
            }

            for (ScanResult result : mWifiManager.getScanResults()) {
                if (!POCKETSNIFFER_SSID.equals(Utils.stripQuotes(result.SSID))) {
                    continue;
                }
                if (result.level <= MIN_RSSI_LEVEL) {
                    Log.d(TAG, "Found SSID " + POCKETSNIFFER_SSID + ", yet signal strength is low (" + result.level + ").");
                    return;
                }
                Log.d(TAG, "Found SSID " + POCKETSNIFFER_SSID + ", signal strength: " + result.level);
                int networkId = addConfiguration();
                mWifiManager.disconnect();
                mWifiManager.enableNetwork(networkId, true);
                mWifiManager.reconnect();
                break;
            }
        }
    };

    private String mSnifIntentName = this.getClass().getName() + ".Snif";
    private IntentFilter mSnifIntentFilter = new IntentFilter(mSnifIntentName);
    private BroadcastReceiver mSnifReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Intent fired, action is " + intent.getAction());

            SnifTask.Params params = new SnifTask.Params(new int[]{1, 6, 11});
            params.packetCount = 500;
            (new SnifTask(mContext, null)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
        }
    };


    /** 
     * Get or create wifi configuration for PocketSniffer Wifi.  
     *
     * @return networkID of Wifi configration.
     * */
    private int addConfiguration() {
        for (WifiConfiguration config : mWifiManager.getConfiguredNetworks()) {
            if (POCKETSNIFFER_SSID.equals(Utils.stripQuotes(config.SSID))) {
                mWifiManager.removeNetwork(config.networkId);
                break;
            }
        }

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = Utils.addQuotes(POCKETSNIFFER_SSID);
        config.priority = PRIORITY;
        return mWifiManager.addNetwork(config);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mStarted) {
            Log.v(TAG, "Not restarting started service.");
            return START_STICKY;
        }

        super.onStartCommand(intent, flags, startId); 
        Log.v(TAG, "======== Starting PocketSniffer Service ======== ");

        registerReceiver(mScanResultReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        registerReceiver(mSnifReceiver, mSnifIntentFilter);

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

        unregisterReceiver(mScanResultReceiver);
        unregisterReceiver(mSnifReceiver);

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
