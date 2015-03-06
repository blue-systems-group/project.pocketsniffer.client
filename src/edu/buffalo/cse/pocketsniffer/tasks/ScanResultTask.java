package edu.buffalo.cse.pocketsniffer.tasks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONObject;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.BitmapDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import edu.buffalo.cse.phonelab.toolkit.android.periodictask.PeriodicParameters;
import edu.buffalo.cse.phonelab.toolkit.android.periodictask.PeriodicState;
import edu.buffalo.cse.phonelab.toolkit.android.periodictask.PeriodicTask;
import edu.buffalo.cse.phonelab.toolkit.android.utils.Utils;
import edu.buffalo.cse.pocketsniffer.R;
import edu.buffalo.cse.pocketsniffer.utils.LocalUtils;
import edu.buffalo.cse.pocketsniffer.utils.Logger;

public class ScanResultTask extends PeriodicTask<ScanResultTaskParameters, ScanResultTaskState>{
    private static final String TAG = LocalUtils.getTag(ScanResultTask.class);

    private static final int WIFI_NOTIFICATION_ID = 34253;


    private Logger mLogger;
    private long mLastPrompt = 0L;
    private ConnectivityManager mConnectivityManager;

    public ScanResultTask(Context context) {
        super(context, ScanResultTask.class.getSimpleName());

        mLogger = Logger.getInstance(mContext);
        mConnectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    /**
     * Make sure the configuration of Pocketsniffer SSID exists, create one if
     * necessary.
     *
     * @return networkId of PocketSniffer network.
     */
    private int getOrCreateNetworkId(String ssid) {
        WifiConfiguration config = null;
        for (WifiConfiguration cfg : mWifiManager.getConfiguredNetworks()) {
            if (ssid.equals(Utils.stripQuotes(cfg.SSID))) {
                if (config == null) {
                    config = cfg;
                }
                else {
                    mWifiManager.removeNetwork(cfg.networkId);
                }
            }
        }

        if (config != null) {
            WifiInfo info = mWifiManager.getConnectionInfo();
            if (info == null || info.getSSID() == null || !Utils.stripQuotes(info.getSSID()).startsWith(mParameters.targetSSIDPrefix)) {
                Log.d(TAG, "Removing stale Wifi config.");
                mWifiManager.removeNetwork(config.networkId);
                config = new WifiConfiguration();
                config.networkId = -1;
                mWifiManager.setWifiEnabled(false);
                mWifiManager.setWifiEnabled(true);
            }
        }
        else {
            config = new WifiConfiguration();
            config.networkId = -1;
        }

        config.SSID = Utils.addQuotes(ssid);
        config.preSharedKey = Utils.addQuotes(mParameters.preSharedKey);
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK, true);
        config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN, true);

        if (config.networkId == -1) {
            mWifiManager.addNetwork(config);
        }
        else {
            mWifiManager.updateNetwork(config);
        }
        mWifiManager.enableNetwork(config.networkId, false);
        return config.networkId;
    }

    private void logScanResult() {
        try {
            JSONObject json = new JSONObject();
            json.put(Logger.KEY_ACTION, WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            json.put("results", Utils.getScanResults(mContext));
            mLogger.log(json);
            Log.i(TAG, json.toString());
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to log scan results.", e);
        }
    }

    private void handleScanResult(Intent intent) {
        Log.d(TAG, "Handling scan results.");

        logScanResult();

        if (!mWifiManager.isWifiEnabled()) {
            Log.d(TAG, "Wifi disabled by user, enabling");
            mWifiManager.setWifiEnabled(true);
            return;
        }

        Set<String> ssids = new HashSet<String>();
        for (ScanResult result : mWifiManager.getScanResults()) {
            if (Utils.stripQuotes(result.SSID).startsWith(mParameters.targetSSIDPrefix)) {
                if (result.level < mParameters.minRSSI) {
                    Log.d(TAG, "Found " + result.SSID + ", yet signal strength is weak (" + result.level + " dBm).");
                }
                else {
                    Log.d(TAG, "Found " + result.SSID + " with RSSI = " + result.level + " dBm.");
                    ssids.add(Utils.stripQuotes(result.SSID));
                }
            }
        }

        for (WifiConfiguration config : mWifiManager.getConfiguredNetworks()) {
            if (!Utils.stripQuotes(config.SSID).startsWith(mParameters.targetSSIDPrefix)) {
                Log.d(TAG, "Disabling SSID " + config.SSID);
                mWifiManager.disableNetwork(config.networkId);
            }
        }

        List<Integer> networkIds = new ArrayList<Integer>();

        if (ssids.size() == 0) {
            Log.d(TAG, "No PocketSniffer Wifi found.");
        }
        else {
            for (String ssid : ssids) {
                Log.d(TAG, "Configuring SSID " + ssid);
                networkIds.add(getOrCreateNetworkId(ssid));
            }
        }

        WifiInfo info = mWifiManager.getConnectionInfo();
        if (info != null && Utils.stripQuotes(info.getSSID()).startsWith(mParameters.targetSSIDPrefix)) {
            Log.d(TAG, "Already connected to " + info.getSSID());
            mNotificationManager.cancel(WIFI_NOTIFICATION_ID);
        }
        else if (networkIds.size() > 0) {
            Log.d(TAG, "Force connectting to PocketSniffer Wifi.");
            mWifiManager.reassociate();
        }
    }

    public void handleRSSIChange(Intent intent) {
        JSONObject json = new JSONObject();

        try {
            json.put(Logger.KEY_ACTION, intent.getAction());
            json.put("wifiInfo", Utils.getWifiInfo(mContext));
            mLogger.log(json);
            Log.i(TAG, json.toString());
        }
        catch (Exception e) {
            // ignore
        }
    }

    public void handleConnectivity(Intent intent) {
        JSONObject json = new JSONObject();
        try {
            json.put(Logger.KEY_ACTION, intent.getAction());
            json.put("failOver", intent.getBooleanExtra(ConnectivityManager.EXTRA_IS_FAILOVER, false));
            json.put("networkType", intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, 0));
            json.put("noConnecitivity", intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false));
            json.put("reason", intent.getStringExtra(ConnectivityManager.EXTRA_REASON));

            NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();
            if (info != null) {
                json.put("connected", info.isConnected());
            }
            else {
                json.put("connected", false);
            }
            mLogger.log(json);
            Log.i(TAG, json.toString());
        }
        catch (Exception e) {
            // ignore
        }

        WifiInfo info = mWifiManager.getConnectionInfo();
        if (info != null && mParameters.targetSSIDPrefix.equals(Utils.stripQuotes(info.getSSID()))) {
            mNotificationManager.cancel(WIFI_NOTIFICATION_ID);
        }
    }

    public static JSONObject getDetailedScanResult() {
        JSONObject json = new JSONObject();

        try {
            json.put("timestamp", System.currentTimeMillis());
            json.put("mac", Utils.getMacAddress("wlan0"));
            json.put("output", Utils.call("iw wlan0 scan", -1 /* no timeout */, true /* require su */)[1]);
        }
        catch (Exception e) {
            // ignore
        }

        return json;
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
                else if (WifiManager.RSSI_CHANGED_ACTION.equals(action)) {
                    handleRSSIChange(intent);
                }
                else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                    handleConnectivity(intent);
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to handle intent.", e);
            }
        }
    };





    @Override
    protected void check(ScanResultTaskParameters arg0) throws Exception {
    }

    @Override
    public ScanResultTaskParameters newParameters() {
        return new ScanResultTaskParameters();
    }

    @Override
    public ScanResultTaskParameters newParameters(ScanResultTaskParameters arg0) {
        return new ScanResultTaskParameters(arg0);
    }

    @Override
    public ScanResultTaskState newState() {
        return new ScanResultTaskState();
    }

    @Override
    public Class<ScanResultTaskParameters> parameterClass() {
        return ScanResultTaskParameters.class;
    }

    @Override
    public synchronized void start() {
        super.start();

        IntentFilter wifiIntentFilter = new IntentFilter();
        wifiIntentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        wifiIntentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        wifiIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        wifiIntentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mContext.registerReceiver(mWifiReceiver, wifiIntentFilter);
    }

    @Override
    public synchronized void stop() {
        super.stop();

        mContext.unregisterReceiver(mWifiReceiver);
    }


    @Override
    public String getTag() {
        return TAG;
    }

    @Override
    public boolean parametersUpdated(String manifestString) {
        super.parametersUpdated(manifestString);

        return true;
    }
}

@Root(name = "ScanResultTask")
class ScanResultTaskParameters extends PeriodicParameters {

    @Element
    public String targetSSIDPrefix;

    @Element
    public String preSharedKey;

    @Element
    public Integer minRSSI;

    @Element
    public Integer promptIntervalSec;

    public ScanResultTaskParameters() {
        targetSSIDPrefix = "PocketSniffer2";
        preSharedKey = "abcd1234";
        minRSSI = -70;
        promptIntervalSec = 20;
    }

    public ScanResultTaskParameters(ScanResultTaskParameters params) {
        super(params);

        this.targetSSIDPrefix = params.targetSSIDPrefix;
        this.preSharedKey = params.preSharedKey;
        this.minRSSI = params.minRSSI;
        this.promptIntervalSec = params.promptIntervalSec;
    }
}

@Root(name = "ScanResultTask")
class ScanResultTaskState extends PeriodicState {
}
