package edu.buffalo.cse.pocketsniffer.tasks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.simpleframework.xml.Root;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.util.Log;

import edu.buffalo.cse.phonelab.toolkit.android.periodictask.PeriodicParameters;
import edu.buffalo.cse.phonelab.toolkit.android.periodictask.PeriodicState;
import edu.buffalo.cse.phonelab.toolkit.android.periodictask.PeriodicTask;
import edu.buffalo.cse.phonelab.toolkit.android.utils.Utils;
import edu.buffalo.cse.pocketsniffer.ui.DeviceFragment;
import edu.buffalo.cse.pocketsniffer.utils.LocalUtils;

public class ProximityTask extends PeriodicTask<ProximityTaskParameters, ProximityTaskState> {

    private static final String TAG = LocalUtils.getTag(ProximityTask.class);

    public static final String ACTION_NEIGHBOR_UPDATED = ProximityTask.class.getName() + ".NeighborUpdated";
    public static final String EXTRA_NEIGHBOR_DEVICES = ProximityTask.class.getName() + ".NeighborDevices";

    private static final String KEY_LAST_UPDATED = ProximityTask.class.getName() + ".LastUpdated";
    private static final String KEY_NEIGHBOR_DEVICES = ProximityTask.class.getName() + ".NeighborDevices";

    private BluetoothAdapter mBluetoothAdapter;
    private SharedPreferences mSharedPreferences;

    private ReentrantLock mPreferenceLock;

    private Map<String, BluetoothDeviceInfo> mBluetoothDeviceCache;

    private boolean previousBluetoothEnabled = true;
    private boolean mScanInProcess = false;

    private long mDiscoveryStarted = 0L;

    private void flushDeviceCache() {
        List<String> disappearedDevices = new ArrayList<String>();
        for (BluetoothDeviceInfo info : mBluetoothDeviceCache.values()) {
            if (info.lastSeen < mDiscoveryStarted && !info.interested) {
                disappearedDevices.add(info.mac);
            }
        }
        for (String mac : disappearedDevices) {
            mBluetoothDeviceCache.remove(mac);
        }
        persistBluetoothDevice(true);
    }

    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Intent fired, action is " + action);

            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d(TAG, "Bluetooth discovery finished.");
                if (!previousBluetoothEnabled) {
                    disableBluetooth();
                }
                mScanInProcess = false;
                flushDeviceCache();
                return;
            }

            if (DeviceFragment.ACTION_INTERESTED_DEVICE_CHANGED.equals(action)) {
                String mac = intent.getStringExtra(DeviceFragment.EXTRA_DEVICE_MAC);
                boolean isInterested = intent.getBooleanExtra(DeviceFragment.EXTRA_IS_INTERESTED, false);
                if (mBluetoothDeviceCache.containsKey(mac)) {
                    Log.d(TAG, "Setting device " + mac + " as " + (isInterested? "interested.": "not interested."));
                    mBluetoothDeviceCache.get(mac).interested = isInterested;
                    persistBluetoothDevice(false);
                }
                else {
                    Log.e(TAG, "Device " + mac + " does not exist.");
                }
                return;
            }

            BluetoothDevice device = null;
            int rssi = -1;

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, (short) rssi);
            }
            else if (BluetoothDevice.ACTION_NAME_CHANGED.equals(action)) {
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            }

            updateBluetoothDevice(device, rssi);
        }
    };

    public ProximityTask(Context context) {
        super(context, ProximityTask.class.getSimpleName());

        BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        mSharedPreferences = mContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        mPreferenceLock = new ReentrantLock();

        mBluetoothDeviceCache = new HashMap<String, BluetoothDeviceInfo>();

        String cache = mSharedPreferences.getString(KEY_NEIGHBOR_DEVICES, null);
        if (cache != null) {
            try {
                JSONArray array = new JSONArray(cache);
                for (int i = 0; i < array.length(); i++) {
                    BluetoothDeviceInfo info = BluetoothDeviceInfo.fromJSONObject(array.getJSONObject(i));
                    mBluetoothDeviceCache.put(info.mac, info);
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to restore saved devices.", e);
            }
            mDiscoveryStarted = System.currentTimeMillis();
            flushDeviceCache();
        }
    }

    private boolean enableBluetooth() {
        if (!mBluetoothAdapter.isEnabled()) {
            previousBluetoothEnabled = false;
            Log.d(TAG, "Enabling Bluetooth.");
            if (!mBluetoothAdapter.enable()) {
                Log.d(TAG, "Failed to enable Bluetooth.");
                return false;
            }
            Log.d(TAG, "Waiting for Bluetooth to be enabled.");
            while (!mBluetoothAdapter.isEnabled()) {
                try {
                    Thread.sleep(100);
                }
                catch (Exception e) {
                    Thread.yield();
                }
            }
            Log.d(TAG, "Successfully enabled Bluetooth.");
        }
        else {
            previousBluetoothEnabled = true;
        }
        return true;
    }

    private boolean disableBluetooth() {
        if (mBluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Disabling Bluetooth.");
            if (!mBluetoothAdapter.disable()) {
                Log.d(TAG, "Failed to disable bluetooth.");
                return false;
            }
        }
        return true;
    }

    @Override
    protected void check(ProximityTaskParameters arg0) throws Exception {
        if (mScanInProcess) {
            Log.d(TAG, "Alreadying scanning. Ignore.");
            return;
        }
        // the first bluetooth scan result comes even before startDiscovery.
        mDiscoveryStarted = System.currentTimeMillis();
        if (!enableBluetooth()) {
            return;
        }
        if (!mBluetoothAdapter.startDiscovery()) {
            Log.d(TAG, "Failed to start bluetooth discovery.");
        }
        mScanInProcess = true;
    }

    private void persistBluetoothDevice(boolean broadcast) {
        String extra = null;
        synchronized (mPreferenceLock) {
            try {
                JSONArray array = new JSONArray();
                for (BluetoothDeviceInfo info : mBluetoothDeviceCache.values()) {
                    array.put(info.toJSONObject());
                }
                SharedPreferences.Editor editor = mSharedPreferences.edit();
                extra = array.toString();
                editor.putString(KEY_NEIGHBOR_DEVICES, extra);
                editor.putString(KEY_LAST_UPDATED, Utils.getDateTimeString());
                editor.commit();
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to commit changes.", e);
                return;
            }
        }
        if (broadcast) {
            Intent intent = new Intent(ACTION_NEIGHBOR_UPDATED);
            intent.putExtra(EXTRA_NEIGHBOR_DEVICES, extra);
            mContext.sendBroadcast(intent);
        }
    }


    private void updateBluetoothDevice(BluetoothDevice device, int rssi) {
        BluetoothDeviceInfo info = null;
        String mac = device.getAddress().toLowerCase();

        if (mBluetoothDeviceCache.containsKey(mac)) {
            info = mBluetoothDeviceCache.get(mac);
        }
        else {
            info = new BluetoothDeviceInfo();
        }
        info.mac = device.getAddress().toLowerCase();;
        info.name = device.getName();
        info.lastSeen = System.currentTimeMillis();
        if (rssi != -1) {
            info.rssi = rssi;
        }
        mBluetoothDeviceCache.put(mac, info);
        Log.d(TAG, "Updating bluetooth device: " + info.toJSONObject().toString());

        persistBluetoothDevice(true);
    }


    public final static class BluetoothDeviceInfo {
        public String mac;
        public String name;
        public int rssi;
        public long lastSeen;
        public boolean interested;

        public BluetoothDeviceInfo() {
            interested = false;
        }

        public JSONObject toJSONObject() {
            JSONObject json = new JSONObject();
            try {
                json.put("MAC", mac);
                json.put("name", name);
                json.put("rssi", rssi);
                json.put("lastSeen", lastSeen);
                json.put("interested", interested);
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to encode as JSONObject.", e);
            }
            return json;
        }

        public static BluetoothDeviceInfo fromJSONObject(JSONObject json) throws JSONException {
            BluetoothDeviceInfo info = new BluetoothDeviceInfo();
            info.mac = json.getString("MAC");
            info.name = json.getString("name");
            info.rssi = json.getInt("rssi");
            info.lastSeen = json.getLong("lastSeen");
            info.interested = json.getBoolean("interested");
            return info;
        }
    }



    @Override
    public ProximityTaskParameters newParameters() {
        return new ProximityTaskParameters();
    }

    @Override
    public ProximityTaskParameters newParameters(ProximityTaskParameters arg0) {
        return new ProximityTaskParameters(arg0);
    }

    @Override
    public ProximityTaskState newState() {
        return new ProximityTaskState();
    }

    @Override
    public Class<ProximityTaskParameters> parameterClass() {
        return ProximityTaskParameters.class;
    }

    @Override
    public synchronized void stop() {
        super.stop();

        mContext.unregisterReceiver(mBluetoothReceiver);
    }

    @Override
    public synchronized void start() {
        super.start();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        intentFilter.addAction(DeviceFragment.ACTION_INTERESTED_DEVICE_CHANGED);
        mContext.registerReceiver(mBluetoothReceiver, intentFilter);
    }

    @Override
    public String getTag() {
        return TAG;
    }
}


@Root(name = "ProximityTask")
class ProximityTaskParameters extends PeriodicParameters {

    public ProximityTaskParameters() {
        super();
    }

    public ProximityTaskParameters(ProximityTaskParameters params) {
        super(params);
    }
}

@Root(name = "ProximityTask")
class ProximityTaskState extends PeriodicState {

    public ProximityTaskState() {
    }
}
