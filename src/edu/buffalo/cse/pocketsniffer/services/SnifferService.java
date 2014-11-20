package edu.buffalo.cse.pocketsniffer.services;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import edu.buffalo.cse.phonelab.toolkit.android.interfaces.ManifestClient;
import edu.buffalo.cse.phonelab.toolkit.android.services.ManifestService;
import edu.buffalo.cse.phonelab.toolkit.android.services.UploaderService;
import edu.buffalo.cse.pocketsniffer.tasks.BatteryTask;
import edu.buffalo.cse.pocketsniffer.tasks.PingTask;
import edu.buffalo.cse.pocketsniffer.tasks.ScanResultTask;
import edu.buffalo.cse.pocketsniffer.tasks.ServerTask;
import edu.buffalo.cse.pocketsniffer.tasks.ThroughputTask;
import edu.buffalo.cse.pocketsniffer.utils.LocalUtils;

public class SnifferService extends Service implements ManifestClient {
    private static final String TAG = LocalUtils.getTag(SnifferService.class);

    private static final String DEFAULT_MANIFEST_URL = "http://pocketsniffer.cse.buffalo.edu/manifest/";
    private static final String DEFAULT_UPLOAD_URL = "http://pocketsniffer.cse.buffalo.edu/upload/";

    private static final String MANIFEST_SERVICE_INTENT = "edu.buffalo.cse.pocketsniffer.services.ManifestService";
    private static final String UPLOADER_SERVICE_INTENT = "edu.buffalo.cse.pocketsniffer.services.UploaderService";

    private boolean mStarted = false;
    private Context mContext;

    // periodic tasks
    private BatteryTask mBatteryTask;
    private PingTask mPingTask;
    private ThroughputTask mThroughputTask;
    private ScanResultTask mScanResultTask;
    private ServerTask mServerTask;


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mStarted) {
            Log.v(TAG, "Not restarting started service.");
            return START_STICKY;
        }

        super.onStartCommand(intent, flags, startId); 
        Log.v(TAG, "======== Starting PocketSniffer Service ======== ");

        Intent manifestIntent = new Intent(MANIFEST_SERVICE_INTENT);
        manifestIntent.putExtra(ManifestService.EXTRA_MANIFEST_URL, DEFAULT_MANIFEST_URL);
        manifestIntent.putExtra(ManifestService.EXTRA_TAG_PREFIX, "PocketSniffer");
        manifestIntent.putExtra(ManifestService.EXTRA_INTENT_PREFIX, mContext.getPackageName());
        startService(manifestIntent);

        Intent uploadIntent = new Intent(UPLOADER_SERVICE_INTENT);
        uploadIntent.putExtra(UploaderService.EXTRA_UPLOAD_URL, DEFAULT_UPLOAD_URL);
        uploadIntent.putExtra(UploaderService.EXTRA_INTENT_PREFIX, mContext.getPackageName());
        startService(uploadIntent);

        mPingTask.start();
        mBatteryTask.start();
        mThroughputTask.start();
        mScanResultTask.start();
        mServerTask.start();

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
        mScanResultTask.stop();
        mServerTask.stop();

    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.v(TAG, "======== Creating PocketSniffer Service ========");

        mContext = this;

        mServerTask = new ServerTask(mContext);
        mBatteryTask = new BatteryTask(mContext);
        mThroughputTask = new ThroughputTask(mContext);
        mScanResultTask = new ScanResultTask(mContext);
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
    public boolean parametersUpdated(String arg0) {
        return false;
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
