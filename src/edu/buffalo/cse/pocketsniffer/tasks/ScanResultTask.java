package edu.buffalo.cse.pocketsniffer.tasks;

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
    private int getOrCreateNetworkId() {
        int networkId = -1;
        int priority = -1;
        for (WifiConfiguration config : mWifiManager.getConfiguredNetworks()) {
            priority = Math.max(priority, config.priority);

            if (mParameters.targetSSID.equals(Utils.stripQuotes(config.SSID))) {
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
        target.SSID = Utils.addQuotes(mParameters.targetSSID);
        target.preSharedKey = Utils.addQuotes(mParameters.preSharedKey);
        target.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK, true);
        target.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN, true);
        target.priority = priority+1;

        if (networkId == -1) {
            networkId = mWifiManager.addNetwork(target);
        }
        else {
            target.networkId = networkId;
            mWifiManager.updateNetwork(target);
        }
        return networkId;
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

        int networkId = getOrCreateNetworkId();

        if (!mWifiManager.isWifiEnabled()) {
            Log.d(TAG, "Wifi disabled by user.");
            return;
        }

        WifiInfo info = mWifiManager.getConnectionInfo();
        if (info != null && mParameters.targetSSID.equals(Utils.stripQuotes(info.getSSID()))) {
            Log.d(TAG, "Already connected to " + info.getSSID());
            return;
        }

        boolean found = false;
        for (ScanResult result : mWifiManager.getScanResults()) {
            if (mParameters.targetSSID.equals(Utils.stripQuotes(result.SSID))) {
                if (result.level < mParameters.minRSSI) {
                    Log.d(TAG, "Found " + mParameters.targetSSID + ", yet signal strength is weak (" + result.level + " dBm).");
                }
                else {
                    Log.d(TAG, "Found " + mParameters.targetSSID + " with RSSI = " + result.level + " dBm.");
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            Log.d(TAG, "No PocketSniffer Wifi found.");
            mNotificationManager.cancel(WIFI_NOTIFICATION_ID);

            for (WifiConfiguration config : mWifiManager.getConfiguredNetworks()) {
                mWifiManager.enableNetwork(config.networkId, false);
            }
            if (!Utils.hasNetworkConnection(mContext, ConnectivityManager.TYPE_WIFI)) {
                mWifiManager.reconnect();
            }
            return;
        }

        if (mParameters.forceConnect) {
            Log.d(TAG, "Force connectting to PocketSniffer Wifi.");
            mWifiManager.disconnect();
            mWifiManager.enableNetwork(networkId, true /* disable others */);
            mWifiManager.reconnect();
        }
        else {
            mWifiManager.enableNetwork(networkId, false /* do not disable others */);

            if (mLastPrompt > 0 && (System.currentTimeMillis() - mLastPrompt) < mParameters.promptIntervalSec * 1000) {
                Log.d(TAG, "Postpone SSID check.");
            }
            else {
                Notification.Builder builder = new Notification.Builder(mContext);

                builder.setSmallIcon(R.drawable.ic_launcher);
                builder.setLargeIcon(((BitmapDrawable) mContext.getResources().getDrawable(R.drawable.wifi)).getBitmap());
                builder.setContentTitle("PocketSniffer");
                builder.setTicker("PocketSniffer Wifi available!");
                builder.setContentText("PocketSniffer Wifi found.");
                builder.setSubText("Click to connect.");
                builder.setContentIntent(PendingIntent.getActivity(mContext, 0, new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK), PendingIntent.FLAG_ONE_SHOT));
                builder.setAutoCancel(true);
                mNotificationManager.notify(WIFI_NOTIFICATION_ID, builder.build());
                mLastPrompt = System.currentTimeMillis();
            }
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
        if (info != null && mParameters.targetSSID.equals(Utils.stripQuotes(info.getSSID()))) {
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

        if (!mParameters.forceConnect) {
            for (WifiConfiguration config : mWifiManager.getConfiguredNetworks()) {
                Log.d(TAG, "Reenabling " + config.SSID);
                mWifiManager.enableNetwork(config.networkId, false);
            }
        }

        return true;
    }
}

@Root(name = "ScanResultTask")
class ScanResultTaskParameters extends PeriodicParameters {

    @Element
    public String targetSSID;

    @Element
    public String preSharedKey;

    @Element
    public Integer minRSSI;

    @Element
    public Integer promptIntervalSec;

    @Element
    public Boolean forceConnect;

    public ScanResultTaskParameters() {
        targetSSID = "PocketSniffer";
        preSharedKey = "LDR9OXnevs5lBlCjz0MNga2H40DlT2m0";
        minRSSI = -70;
        promptIntervalSec = 20;
        forceConnect = false;
    }

    public ScanResultTaskParameters(ScanResultTaskParameters params) {
        super(params);

        this.targetSSID = params.targetSSID;
        this.preSharedKey = params.preSharedKey;
        this.minRSSI = params.minRSSI;
        this.promptIntervalSec = params.promptIntervalSec;
        this.forceConnect = params.forceConnect;
    }
}

@Root(name = "ScanResultTask")
class ScanResultTaskState extends PeriodicState {
}
