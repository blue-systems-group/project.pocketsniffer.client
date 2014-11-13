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
import android.graphics.drawable.BitmapDrawable;
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
import edu.buffalo.cse.pocketsniffer.R;
import edu.buffalo.cse.pocketsniffer.tasks.BatteryTask;
import edu.buffalo.cse.pocketsniffer.tasks.PingTask;
import edu.buffalo.cse.pocketsniffer.tasks.ServerTask;
import edu.buffalo.cse.pocketsniffer.tasks.ThroughputTask;
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

    private SnifferServiceParameters mParameters;
    private Logger mLogger;
    private NotificationManager mNotificationManager;

    // periodic tasks
    private BatteryTask mBatteryTask;
    private ServerTask mServerTask;
    private PingTask mPingTask;

    private ThroughputTask mThroughputTask;


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

        Intent manifestIntent = new Intent(mContext, ManifestService.class);
        manifestIntent.putExtra(ManifestService.EXTRA_MANIFEST_URL, DEFAULT_MANIFEST_URL);
        startService(manifestIntent);

        Intent uploadIntent = new Intent(mContext, UploaderService.class);
        uploadIntent.putExtra(UploaderService.EXTRA_UPLOAD_URL, DEFAULT_UPLOAD_URL);
        startService(uploadIntent);

        startServerTask(mParameters);

        mPingTask.start();
        mBatteryTask.start();
        mThroughputTask.start();

        mStarted = true;
        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "======== Destroying PocketSniffer Service ========");

        mPingTask.stop();
        mBatteryTask.stop();
        mThroughputTask.stop();

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

        mBatteryTask = new BatteryTask(mContext);
        mThroughputTask = new ThroughputTask(mContext);
        mPingTask = new PingTask(mContext);
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
