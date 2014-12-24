package edu.buffalo.cse.pocketsniffer.services;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import edu.buffalo.cse.phonelab.toolkit.android.periodictask.PeriodicTask;
import edu.buffalo.cse.phonelab.toolkit.android.services.ManifestService;
import edu.buffalo.cse.phonelab.toolkit.android.services.UploaderService;
import edu.buffalo.cse.pocketsniffer.utils.LocalUtils;

public class SnifferService extends Service {
    private static final String TAG = LocalUtils.getTag(SnifferService.class);

    public static final String BASE_URL = "http://pocketsniffer.phone-lab.org";

    public static final String DEFAULT_MANIFEST_URL = BASE_URL + "/backend/manifest/";

    public static final String MANIFEST_SERVICE_INTENT = "edu.buffalo.cse.pocketsniffer.services.ManifestService";
    public static final String UPLOADER_SERVICE_INTENT = "edu.buffalo.cse.pocketsniffer.services.UploaderService";

    private boolean mStarted = false;
    private Context mContext;

    // periodic tasks
    private static final String[] TASK_NAMES = {
        "edu.buffalo.cse.pocketsniffer.tasks.BatteryTask",
        "edu.buffalo.cse.pocketsniffer.tasks.PingTask",
        "edu.buffalo.cse.pocketsniffer.tasks.ThroughputTask",
        "edu.buffalo.cse.pocketsniffer.tasks.ScanResultTask",
        "edu.buffalo.cse.pocketsniffer.tasks.ServerTask",
        "edu.buffalo.cse.pocketsniffer.tasks.BuildInfoTask",
        "edu.buffalo.cse.pocketsniffer.tasks.ProximityTask"
    };

    private Map<String, PeriodicTask> mTasks;

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
        uploadIntent.putExtra(UploaderService.EXTRA_INTENT_PREFIX, mContext.getPackageName());
        startService(uploadIntent);

        for (Entry<String, PeriodicTask> entry : mTasks.entrySet()) {
            Log.d(TAG, "Starting " + entry.getKey());
            entry.getValue().start();
        }

        mStarted = true;
        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "======== Destroying PocketSniffer Service ========");

        for (Entry<String, PeriodicTask> entry : mTasks.entrySet()) {
            Log.d(TAG, "Stopping " + entry.getKey());
            entry.getValue().stop();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.v(TAG, "======== Creating PocketSniffer Service ========");

        mContext = this;

        mTasks = new HashMap<String, PeriodicTask>();
        for (String name : TASK_NAMES) {
            try {
                mTasks.put(name, (PeriodicTask) Class.forName(name).getConstructor(Context.class).newInstance(mContext));
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to create " + name, e);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
