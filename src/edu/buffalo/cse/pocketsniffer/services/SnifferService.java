package edu.buffalo.cse.pocketsniffer.services;

import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Persister;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;

import edu.buffalo.cse.phonelab.toolkit.android.interfaces.ManifestClient;
import edu.buffalo.cse.phonelab.toolkit.android.services.ManifestService;
import edu.buffalo.cse.phonelab.toolkit.android.services.UploaderService;
import edu.buffalo.cse.phonelab.toolkit.android.utils.Utils;
import edu.buffalo.cse.pocketsniffer.tasks.ServerTask;
import edu.buffalo.cse.pocketsniffer.utils.LocalUtils;
import edu.buffalo.cse.pocketsniffer.utils.Logger;

public class SnifferService extends Service implements ManifestClient {
    private static final String TAG = LocalUtils.getTag(SnifferService.class);

    private static final int WIFI_NOTIFICATION_ID = 7;

    // TODO : decide these
    private static final String DEFAULT_MANIFEST_URL = "TODO";
    private static final String DEFAULT_UPLOAD_URL = "TODO";

    private boolean mStarted = false;
    private Context mContext;
    private WifiManager mWifiManager;
    private ServerTask mServerTask;
    private SnifferServiceParameters mParameters;
    private Logger mLogger;
    private NotificationManager mNotificationManager;

    /**
     * Make sure the configuration of Pocketsniffer SSID exists, create one if
     * necessary.
     *
     * @return networkId of PocketSniffer network.
     */
    private int getNetworkId(SnifferServiceParameters parameters) {
        int networkId = -1;
        for (WifiConfiguration config : mWifiManager.getConfiguredNetworks()) {
            if (parameters.ssid.equals(Utils.stripQuotes(config.SSID))) {
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
        target.SSID = Utils.addQuotes(parameters.ssid);
        target.preSharedKey = Utils.addQuotes(parameters.key);
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

    private void logScanResult() {
        try {
            JSONObject json = new JSONObject();
            JSONArray array = new JSONArray();

            for (ScanResult result : mWifiManager.getScanResults()) {
                JSONObject r = new JSONObject();
                r.put("SSID", result.SSID);
                r.put("BSSID", result.BSSID);
                r.put("capabilities", result.capabilities);
                r.put("level", result.level);
                r.put("frequency", result.frequency);
                array.put(r);
            }
            json.put(Logger.KEY_ACTION, WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            json.put("results", array);
            mLogger.log(json);
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to log scan results.", e);
        }
    }

    private void handleScanResult(Intent intent) {
        logScanResult();

        int networkId = getNetworkId(mParameters);
        mWifiManager.enableNetwork(networkId, false /* do not disable others */);

        if (!mWifiManager.isWifiEnabled()) {
            Log.d(TAG, "Wifi disabled by user.");
            return;
        }

        WifiInfo info = mWifiManager.getConnectionInfo();
        if (info != null && mParameters.ssid.equals(Utils.stripQuotes(info.getSSID()))) {
            Log.d(TAG, "Already connected to " + info.getSSID());
            return;
        }

        boolean found = false;
        for (ScanResult result : mWifiManager.getScanResults()) {
            if (mParameters.ssid.equals(Utils.stripQuotes(result.SSID)) && result.level >= mParameters.minRSSI) {
                found = true;
            }
        }
        if (!found) {
            Log.d(TAG, "No PocketSniffer Wifi found, or signal is too weak.");
            return;
        }

        Notification.Builder builder = new Notification.Builder(mContext);

        builder.setContentTitle("PocketSniffer");
        builder.setTicker("PocketSniffer Wifi available!");
        builder.setContentText("PocketSniffer Wifi found. Click to connect.");
        builder.setContentIntent(PendingIntent.getActivity(mContext, 0, new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK), PendingIntent.FLAG_ONE_SHOT));
        mNotificationManager.notify(WIFI_NOTIFICATION_ID, builder.build());
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

    private void startServerTask(SnifferServiceParameters parameters) {
        if (mServerTask.getStatus() == AsyncTask.Status.RUNNING) {
            mServerTask.cancel(true);
            try {
                mServerTask.get(30, TimeUnit.SECONDS);
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to cancel server task.", e);
            }
        }
        ServerTask.Params params = new ServerTask.Params();
        params.port = parameters.listeningPort;
        mServerTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
    }

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

        Intent manifestIntent = new Intent(mContext, ManifestService.class);
        manifestIntent.putExtra(ManifestService.EXTRA_MANIFEST_URL, DEFAULT_MANIFEST_URL);
        startService(manifestIntent);

        Intent uploadIntent = new Intent(mContext, UploaderService.class);
        uploadIntent.putExtra(UploaderService.EXTRA_UPLOAD_URL, DEFAULT_UPLOAD_URL);
        startService(uploadIntent);

        startServerTask(mParameters);

        mStarted = true;
        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "======== Destroying PocketSniffer Service ========");

        unregisterReceiver(mWifiReceiver);
        mServerTask.cancel(true);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.v(TAG, "======== Creating PocketSniffer Service ========");

        mContext = this;
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mServerTask = new ServerTask(mContext, null);
        mParameters = new SnifferServiceParameters();
        mLogger = Logger.getInstance(mContext);
        mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public String getState() {
        return "";
    }

    @Override
    public boolean parametersUpdated(String s) {
        try {
            SnifferServiceParameters newParameters = new Persister().read(SnifferServiceParameters.class, s);
            if (!mParameters.ssid.equals(newParameters.ssid) || !mParameters.key.equals(newParameters.key)) {
                getNetworkId(newParameters);
            }
            if (!mParameters.listeningPort.equals(newParameters.listeningPort)) {
                startServerTask(newParameters);
            }
            mParameters = newParameters;
            return true;
        }
        catch (Exception e) {
            Log.e(TAG, "Faield to deserialize parameter string: " + s, e);
            return false;
        }
    }
}

@Root(name="SnifferService", strict=false)
class SnifferServiceParameters {

    @Element(required=false)
    public Integer listeningPort;

    @Element(required=false)
    public String ssid;

    @Element(required=false)
    public String key;

    @Element(required=false)
    public Integer minRSSI;

    public SnifferServiceParameters() {
        listeningPort = 8000;
        ssid = "PocketSniffer";
        key = "LDR9OXnevs5lBlCjz0MNga2H40DlT2m0";
        minRSSI = -70;
    }
}
