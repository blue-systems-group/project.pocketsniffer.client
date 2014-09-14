package edu.buffalo.cse.pocketsniffer.periodictask;

import java.io.StringWriter;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.util.Log;

import edu.buffalo.cse.pocketsniffer.interfaces.ManifestClient;
import edu.buffalo.cse.pocketsniffer.services.ManifestService;
import edu.buffalo.cse.pocketsniffer.services.ManifestService.ManifestBinder;
import edu.buffalo.cse.pocketsniffer.util.Util;

public abstract class PeriodicTask<P extends PeriodicParameters, S extends PeriodicState> implements ManifestClient {

    public final String PARAMETER_PREFERENCES_NAME = parameterClass().getName();
    public static final String PARAMETER_PREFERENCES_KEY = "parameters";

    protected String TAG = Util.getTag(this.getClass());

    /** Abstract methods. */
    public abstract P newParameters();
    public abstract P newParameters(P parameters);
    public abstract S newState();
    public abstract Class<P> parameterClass();
    protected abstract void check(P parameters);

    protected P mParameters;
    protected S mState = newState();

    protected final ReentrantLock mParameterLock = new ReentrantLock();
    protected final ReentrantLock mStateLock = new ReentrantLock();

    protected Context mContext;
    protected String mKey; 
    protected P mInitialParameters;
    protected NotificationManager mNotificationManager;
    protected ConnectivityManager mConnectivityManager;
    protected WifiManager mWifiManager;
    protected boolean mStarted;

    private PendingIntent mCheckPendingIntent;
    private ExecutorService mCheckExecutor = Executors.newSingleThreadExecutor();

    private final String CHECK_INTENT_NAME = this.getClass().getName() + ".Check";
    private IntentFilter mCheckIntentFilter;
    private BroadcastReceiver mCheckBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            Log.d(TAG, "Alarm fired, action is " + arg1.getAction());
            scheduleCheckTask();
        }
    };

    private ManifestService mManifestService;
    private ServiceConnection mManifestServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.v(TAG, "Connecting to ManifestService.");
            ManifestBinder binder = (ManifestBinder) service;
            mManifestService = binder.getService();
            mManifestService.receiveManifestUpdates(PeriodicTask.this, PeriodicTask.this.mKey);
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.w(TAG, "Manifest service disconnected. Shutting down.");
            PeriodicTask.this.stop();
        }
    };

    public PeriodicTask(Context context, String key) {
        super();

        mContext = context;
        mKey = key;

        mParameters = null;
        mStarted = false;

        mCheckIntentFilter = new IntentFilter(CHECK_INTENT_NAME);
        mCheckPendingIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(CHECK_INTENT_NAME), PendingIntent.FLAG_UPDATE_CURRENT);


        mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mConnectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);

        SharedPreferences sharedPreferences = mContext.getSharedPreferences(PARAMETER_PREFERENCES_NAME, Context.MODE_PRIVATE);
        String savedParameterString = sharedPreferences.getString(PARAMETER_PREFERENCES_KEY, null);

        if (savedParameterString != null) {
            Log.d(TAG, "Attempting to retrieve parameters from shared preferences.");
            mInitialParameters = deserializeParameters(savedParameterString);
        }
        if (mInitialParameters != null) {
            Log.d(TAG, "Recovered saved parameters.");
        } else {
            Log.w(TAG, "Unable to recover saved parameters. Reinitializing.");
            mInitialParameters = newParameters();
        }
        Log.v(TAG, "=== Created " + this.getClass().getSimpleName() + " task. ===");
    }

    public synchronized void start() {
        if (mStarted) {
            Log.w(TAG, "Not restarting " + this.getClass().getSimpleName());
            return;
        }

        mContext.registerReceiver(mCheckBroadcastReceiver, mCheckIntentFilter);

        Intent manifestServiceIntent = new Intent(mContext, ManifestService.class);
        mContext.bindService(manifestServiceIntent, mManifestServiceConnection, Context.BIND_AUTO_CREATE);


        mStarted = true;
        mState.started = new Date();

        updateParameters(mInitialParameters);

        Log.d(TAG, "=== Started " + this.getClass().getSimpleName() + " task. === ");
    }

    public synchronized void stop() {
        Log.d(TAG, "Stopping alarm.");
        stopAlarm();

        Log.d(TAG, "Deregistering from manifest updates.");
        mManifestService.discardManifestUpdates(mKey);
        mContext.unbindService(mManifestServiceConnection);
        mContext.unregisterReceiver(mCheckBroadcastReceiver);

        Log.d(TAG, "Shutting down background task executor.");
        mCheckExecutor.shutdown();
        try {
            mCheckExecutor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.e(TAG, "Exception while shutting down check task executor:" + e);
        }

        saveParameters();

        Log.d(TAG, "=== Stopped " + this.getClass().getSimpleName() + " task. === ");
    }

    /**
     * Add extra action names for checking.
     */
    public final synchronized boolean addAction(String add) {
        Log.d(TAG, "Trying to add action " + add);
        if (mCheckIntentFilter.hasAction(add)) {
            Log.w(TAG, "Action already in filter. Ignoring.");
            return true;
        }
        if (mStarted) {
            mContext.unregisterReceiver(mCheckBroadcastReceiver);
        }
        mCheckIntentFilter.addAction(add);
        if (mStarted) {
            mContext.registerReceiver(mCheckBroadcastReceiver, mCheckIntentFilter);
        }
        Log.d(TAG, "Added action " + add);
        return true;
    }

    public final synchronized boolean removeAction(String remove) {
        Log.d(TAG, "Trying to remove action " + remove);
        if (!mCheckIntentFilter.hasAction(remove)) {
            Log.w(TAG, "Action not in filter. Ignoring.");
            return true;
        }
        IntentFilter newIntentFilter = new IntentFilter();
        Iterator<String> actionsIterator = mCheckIntentFilter.actionsIterator();
        while (actionsIterator.hasNext()) {
            String action = actionsIterator.next();
            if (action.equals(remove) == false) {
                Log.d(TAG, "Retaining " + action + " in filter.");
                newIntentFilter.addAction(action);
            } else {
                Log.d(TAG, "Removing " + action + " from filter.");
            }
        }

        Log.d(TAG, "Old intent filter has length " + mCheckIntentFilter.countActions());
        Log.d(TAG, "New intent filter has length " + newIntentFilter.countActions());

        if (mStarted) {
            mContext.unregisterReceiver(mCheckBroadcastReceiver);
        }
        mCheckIntentFilter = newIntentFilter;
        if (mStarted) {
            mContext.registerReceiver(mCheckBroadcastReceiver, mCheckIntentFilter);
        }
        Log.d(TAG, "Removed action " + remove);
        return true;
    }

    /**
     * Get called when parameters changed.
     */
    public boolean parametersUpdated(String manifestString) {
        P newParameters;
        Log.d(TAG, "Manifest update: " + manifestString);
        newParameters = deserializeParameters(manifestString);
        if (newParameters == null) {
            return false;
        } else {
            return updateParameters(newParameters);
        }
    }

    private P deserializeParameters(String parameterString) {
        try {
            return new Persister().read(parameterClass(), parameterString);
        } catch (Exception e) {
            Log.e(TAG, "Could not deserialize string " + parameterString + ": " + e.toString());
            return null;
        }
    }

    private String serializeParameters(P parameters) {
        StringWriter stringWriter = new StringWriter();
        Serializer serializer = new Persister();
        try {
            serializer.write(parameters, stringWriter);
        } catch (Exception e) {
            Log.e(TAG, "Problem serializing parameters: " + e);
            return null;
        }
        String xmlState = stringWriter.toString();
        return xmlState;
    }

    /**
     * Get called when collecting states.
     */
    public String getState() {
        StringWriter stringWriter = new StringWriter();
        Serializer serializer = new Persister();
        synchronized (mParameterLock) {
            mState.parameters = newParameters(mParameters);
        }
        synchronized (mStateLock) {
            try {
                serializer.write(mState, stringWriter);
            } catch (Exception e) {
                Log.e(TAG, "Problem serializing state: " + e);
                return null;
            }
        }
        String xmlState = stringWriter.toString();
        return xmlState;
    }

    public boolean updateParameters(P newParameters) {

        if (newParameters.equals(mParameters)) {
            Log.w(TAG, "Parameters have not changed.");
            return true;
        }

        acquireLock(mContext);

        synchronized(mParameterLock) {
            if (mStarted) {
                stopAlarm();
            }
            mParameters = newParameters;
            scheduleCheckTask();
            if (mStarted) {
                startPeriodic(mParameters.checkInterval);
            }
            saveParameters();
        }

        synchronized (mStateLock) {
            mState.parameterUpdate = new Date();
        }

        releaseLock();
        return true;
    }

    protected void saveParameters() {
        synchronized (mParameterLock) {
            SharedPreferences.Editor sharedPreferencesEditor = mContext.getSharedPreferences(PARAMETER_PREFERENCES_NAME, Context.MODE_PRIVATE).edit();
            sharedPreferencesEditor.putString(PARAMETER_PREFERENCES_KEY, serializeParameters(mParameters));
            sharedPreferencesEditor.commit();
        }
    }


    public class Check implements Runnable {
        private P taskParameters;
        public Check(P parameters) {
            super();
            taskParameters = parameters;
        }

        @Override
        public void run() {
            if (taskParameters.disabled) {
                Log.d(TAG, "Task diabled.");
                return;
            }

            Log.d(TAG, "Starting check task.");
            acquireLock(mContext);
            check(taskParameters);
            releaseLock();
            Log.d(TAG, "Check task finished.");
        }
    }

    public void scheduleCheckTask() {
        synchronized (mParameterLock) {
            mCheckExecutor.execute(new Check(mParameters));
        }
    }


    public final synchronized void startPeriodic(long interval) {
        Log.d(TAG, "Starting periodic at interval " + interval + " seconds.");
        long intervalMS = interval * 1000;
        stopAlarm();
        AlarmManager manager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        manager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + intervalMS,
                intervalMS, mCheckPendingIntent);
    }

    public final synchronized void startOneShot(long interval) {
        long intervalMS = interval * 1000;
        stopAlarm();
        AlarmManager manager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        manager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + intervalMS, mCheckPendingIntent);
    }

    public final synchronized void stopAlarm() {
        AlarmManager manager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        manager.cancel(mCheckPendingIntent);
    }

    private static WakeLock lock = null;
    public final synchronized static void acquireLock(Context context) {
        if (lock == null) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, PeriodicTask.class.getName());
            lock.setReferenceCounted(true);
        }
        lock.acquire();
    }

    public final synchronized static void releaseLock() {
        if (lock != null && lock.isHeld()) {
            lock.release();
        }
    }

}
