package edu.buffalo.cse.pocketsniffer.tasks;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import edu.buffalo.cse.phonelab.toolkit.android.periodictask.PeriodicParameters;
import edu.buffalo.cse.phonelab.toolkit.android.periodictask.PeriodicState;
import edu.buffalo.cse.phonelab.toolkit.android.periodictask.PeriodicTask;
import edu.buffalo.cse.pocketsniffer.utils.LocalUtils;
import edu.buffalo.cse.pocketsniffer.utils.Logger;

public class BatteryTask extends PeriodicTask<BatteryTaskParameters, BatteryTaskState> {

    private static final String TAG = LocalUtils.getTag(BatteryTask.class);

    private Logger mLogger;

    public BatteryTask(Context context) {
        super(context, "BatteryTask");

        mLogger = Logger.getInstance(mContext);
    }

    @Override
    protected void check(BatteryTaskParameters arg0) throws Exception {
        mState.batteryInfo = new BatteryInfo(mContext);
        Log.d(TAG, mState.batteryInfo.toString());
        mLogger.log(mState.batteryInfo.toJSONObject());
    }

    @Override
    public BatteryTaskParameters newParameters() {
        return new BatteryTaskParameters();
    }

    @Override
    public BatteryTaskParameters newParameters(BatteryTaskParameters arg0) {
        return new BatteryTaskParameters(arg0);
    }

    @Override
    public BatteryTaskState newState() {
        return new BatteryTaskState();
    }

    @Override
    public Class<BatteryTaskParameters> parameterClass() {
        return BatteryTaskParameters.class;
    }

    @Override
    public String getState() {
        mState.batteryInfo = new BatteryInfo(mContext);
        return super.getState();
    }

}

@Root(name="BatteryTask")
class BatteryTaskParameters extends PeriodicParameters {

    public BatteryTaskParameters() {
        super();
        this.checkIntervalSec = 300L;
    }

    public BatteryTaskParameters(BatteryTaskParameters parameters) {
        super(parameters);
    }
}

@Root(name="BatteryInfo")
class BatteryInfo {
    public static Map<Integer, String> BATTERY_STATUS;
    public static Map<Integer, String> PLUG;

    static {
        BATTERY_STATUS = new HashMap<Integer, String>();
        BATTERY_STATUS.put(BatteryManager.BATTERY_STATUS_CHARGING, "Charging");
        BATTERY_STATUS.put(BatteryManager.BATTERY_STATUS_DISCHARGING, "Discharging");
        BATTERY_STATUS.put(BatteryManager.BATTERY_STATUS_FULL, "Full");
        BATTERY_STATUS.put(BatteryManager.BATTERY_STATUS_NOT_CHARGING, "NotCharging");
        BATTERY_STATUS.put(BatteryManager.BATTERY_STATUS_UNKNOWN, "Unknown");

        PLUG = new HashMap<Integer, String>();
        PLUG.put(BatteryManager.BATTERY_PLUGGED_AC, "AC");
        PLUG.put(BatteryManager.BATTERY_PLUGGED_USB, "USB");
        PLUG.put(BatteryManager.BATTERY_PLUGGED_WIRELESS, "Wireless");
        PLUG.put(-1, "Unknown");
    }

    @Element
    public String status;

    @Element
    public String plug;

    @Element
    public Double level;

    @Element
    public String tech;

    @Element
    public Integer voltage;

    @Element
    public Boolean present;

    public BatteryInfo() {
        status = BATTERY_STATUS.get(BatteryManager.BATTERY_STATUS_UNKNOWN);
        plug = PLUG.get(-1);
        level = 0.0;
        tech = "Unknown";
        voltage = 0;
        present = false;
    }

    public BatteryInfo(Context context) {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);

        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        this.status = BatteryInfo.BATTERY_STATUS.get(status);

        int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        this.plug = BatteryInfo.PLUG.get(chargePlug);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        this.level = level / (double)scale;

        this.tech = batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
        this.voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        this.present = batteryStatus.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true);
    }

    @Override
    public String toString() {
        return this.toJSONObject().toString();
    }

    public JSONObject toJSONObject() {
        JSONObject json = new JSONObject();
        try {
            json.put("action", Intent.ACTION_BATTERY_CHANGED);
            json.put("status", status);
            json.put("plug", plug);
            json.put("level", level);
            json.put("tech", tech);
            json.put("voltage", voltage);
            json.put("present", present);
        }
        catch (Exception e) {
            }
        return json;
    }
}

@Root(name="BatteryTask")
class BatteryTaskState extends PeriodicState {

    @Element(type=BatteryInfo.class)
    public BatteryInfo batteryInfo;


    public BatteryTaskState() {
        batteryInfo = new BatteryInfo();
    }
}
