package edu.buffalo.cse.pocketsniffer.services;

import java.util.ArrayList;

import org.json.JSONObject;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.widget.Toast;

import edu.buffalo.cse.pocketsniffer.interfaces.AsyncTaskListener;
import edu.buffalo.cse.pocketsniffer.tasks.BatteryTask;
import edu.buffalo.cse.pocketsniffer.tasks.SnifTask;
import edu.buffalo.cse.pocketsniffer.tasks.SnifTask.Progress;
import edu.buffalo.cse.pocketsniffer.tasks.SnifTask.Result;
import edu.buffalo.cse.pocketsniffer.utils.LocalUtils;
import edu.buffalo.cse.pocketsniffer.utils.Logger;

public class BenchService extends Service {

    private static final String TAG = LocalUtils.getTag(BenchService.class);

    private Context mContext;
    private BatteryTask mBatteryTask;
    private SnifTask mSnifTask;
    private boolean mStarted;

    private Logger mLogger;

    private WifiManager mWifiManager;
    private WakeLock mWakeLock;
    private WifiManager.WifiLock mWifiLock;


    private AsyncTaskListener mSnifListener = new AsyncTaskListener<SnifTask.Params, SnifTask.Progress, SnifTask.Result>() {

        @Override
        public void onCancelled(Result result) {
        }

        @Override
        public void onPostExecute(Result result) {
        }

        @Override
        public void onPreExecute() {
        }

        @Override
        public void onProgressUpdate(Progress... progresses) {
            JSONObject json = progresses[0].toJSONObject();
            try {
                json.put("action", "edu.buffalo.cse.pocketsniffer.tasks.SnifTask.ProgressUpdate");
            }
            catch (Exception e) {
            }
            mLogger.log(json);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mStarted) {
            Log.w(TAG, "Not starting BenchService.");
            return START_STICKY;
        }

        super.onStartCommand(intent, flags, startId);

        Log.d(TAG, "======== Starting BenchService =============");

        mBatteryTask.start();

        boolean snif = true;

        if (snif) {
            Integer[] channel_2 = new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
            Integer[] channel_5 = new Integer[]{36, 40, 42, 44, 48, 149, 153, 157, 161, 165};

            SnifTask.Params params = new SnifTask.Params();
            params.channels = new ArrayList<Integer>();
            for (int c : channel_2) {
                params.channels.add(c);
            }
            for (int c : channel_5) {
                params.channels.add(c);
            }

            params.durationSec = 60;
            params.packetCount = -1;
            params.forever = true;
            mSnifTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
            Toast.makeText(mContext, "Sniffing", Toast.LENGTH_LONG).show();
        }
        else {
            mWakeLock.acquire();
            mWifiLock.acquire();
            Toast.makeText(mContext, "Awaking", Toast.LENGTH_LONG).show();
        }


        mStarted = true;
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mContext = this;
        mBatteryTask = new BatteryTask(mContext);
        mSnifTask = new SnifTask(mContext, mSnifListener);
        mLogger = Logger.getInstance(mContext);
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);


        PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, TAG);
        mWifiLock = mWifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, TAG);
    }
}
