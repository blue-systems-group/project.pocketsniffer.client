package edu.buffalo.cse.pocketsniffer.services;

import java.util.Arrays;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import edu.buffalo.cse.pocketsniffer.tasks.BatteryTask;
import edu.buffalo.cse.pocketsniffer.tasks.SnifTask;
import edu.buffalo.cse.pocketsniffer.utils.LocalUtils;

public class BenchService extends Service {

    private static final String TAG = LocalUtils.getTag(BenchService.class);

    private Context mContext;
    private BatteryTask mBatteryTask;
    private SnifTask mSnifTask;
    private boolean mStarted;

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


        Integer[] channel_2 = new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
        Integer[] channel_5 = new Integer[]{36, 40, 42, 44, 48, 149, 153, 157, 161, 165};

        SnifTask.Params params = new SnifTask.Params();
        params.channels = Arrays.asList(channel_5);

        params.durationSec = 60;
        params.packetCount = -1;
        params.forever = true;

        Toast.makeText(mContext, "Sniffing", Toast.LENGTH_LONG).show();
        mSnifTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);

        mStarted = true;
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mContext = this;
        mBatteryTask = new BatteryTask(mContext);
        mSnifTask = new SnifTask(mContext, null);
    }
}
