package edu.buffalo.cse.pocketsniffer.tasks;

import org.json.JSONObject;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.util.Log;

import edu.buffalo.cse.phonelab.toolkit.android.periodictask.PeriodicParameters;
import edu.buffalo.cse.phonelab.toolkit.android.periodictask.PeriodicState;
import edu.buffalo.cse.phonelab.toolkit.android.periodictask.PeriodicTask;
import edu.buffalo.cse.phonelab.toolkit.android.utils.Utils;
import edu.buffalo.cse.pocketsniffer.utils.LocalUtils;
import edu.buffalo.cse.pocketsniffer.utils.Logger;


public class ThroughputTask extends PeriodicTask<ThroughputTaskParameters, ThroughputTaskState> {
    private static final String TAG = LocalUtils.getTag(ThroughputTask.class);
    private static final String ACTION = ThroughputTask.class.getName() + ".Throughput";

    private Logger mLogger;

    public ThroughputTask(Context context) {
        super(context, ThroughputTask.class.getSimpleName());

        mLogger = Logger.getInstance(mContext);
    }

    @Override
    protected void check(ThroughputTaskParameters parameters) throws Exception {

        if (!Utils.hasNetworkConnection(mContext, ConnectivityManager.TYPE_WIFI)) {
            Log.w(TAG, "No Wifi connection. Do not download.");
            return;
        }

        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        if (!mParameters.targetSSID.equals(Utils.stripQuotes(wifiInfo.getSSID()))) {
            Log.w(TAG, "Not connected to " + mParameters.targetSSID + ". Do not download.");
            return;
        }

        int fileSizeMB = (int) (mParameters.minFileSizeMB + Math.random() * (mParameters.maxFileSizeMB - mParameters.minFileSizeMB));

        JSONObject json;
        if (Math.random() < mParameters.udpProbability) {
            Log.d(TAG, "Try iperf with " + mParameters.iperfHost + ":" + mParameters.iperfUDPPort);
            json = LocalUtils.iperfTest(mParameters.iperfHost, mParameters.iperfUDPPort, true /* udp */, fileSizeMB);
        }
        else {
            Log.d(TAG, "Try iperf with " + mParameters.iperfHost + ":" + mParameters.iperfTCPPort);
            json = LocalUtils.iperfTest(mParameters.iperfHost, mParameters.iperfTCPPort, false /* not udp */, fileSizeMB);
        }

        json.put(Logger.KEY_ACTION, ACTION);

        Log.i(TAG, json.toString());
        mLogger.log(json);

        long nextInterval = (long) (mParameters.minIntervalSec + Math.random() * (mParameters.maxIntervalSec - mParameters.minIntervalSec));
        Log.d(TAG, "Schedule next download in " + nextInterval + " seconds.");
        startOneShot(nextInterval);
    }

    @Override
    public ThroughputTaskParameters newParameters() {
        return new ThroughputTaskParameters();
    }

    @Override
    public ThroughputTaskParameters newParameters(ThroughputTaskParameters arg0) {
        return new ThroughputTaskParameters(arg0);
    }

    @Override
    public ThroughputTaskState newState() {
        return new ThroughputTaskState();
    }

    @Override
    public Class<ThroughputTaskParameters> parameterClass() {
        return ThroughputTaskParameters.class;
    }

    @Override
    public String getTag() {
        return TAG;
    }

}

@Root(name = "ThroughputTask")
class ThroughputTaskParameters extends PeriodicParameters {

    @Element
    String targetSSID;

    @Element
    String iperfHost;

    @Element
    Integer iperfTCPPort;

    @Element
    Integer iperfUDPPort;

    @Element
    Double udpProbability;

    @Element
    Long maxIntervalSec;

    @Element
    Long minIntervalSec;

    @Element
    Integer maxFileSizeMB;

    @Element
    Integer minFileSizeMB;

    public ThroughputTaskParameters() {
        checkIntervalSec = 300L;
        targetSSID = "PocketSniffer";
        maxIntervalSec = 900L;
        minIntervalSec = 300L;
        minFileSizeMB = 50;
        maxFileSizeMB = 100;
        iperfHost = "192.168.1.1";
        iperfTCPPort = 5001;
        iperfUDPPort = 5002;
        udpProbability = 0.5;
    }

    public ThroughputTaskParameters(ThroughputTaskParameters params) {
        super(params);
        this.targetSSID = params.targetSSID;
        this.maxIntervalSec = params.maxIntervalSec;
        this.minIntervalSec = params.minIntervalSec;
        this.maxFileSizeMB = params.maxFileSizeMB;
        this.minFileSizeMB = params.minFileSizeMB;
        this.iperfHost = params.iperfHost;
        this.iperfTCPPort = params.iperfTCPPort;
        this.iperfUDPPort = params.iperfUDPPort;
        this.udpProbability = params.udpProbability;
    }
}

@Root(name = "ThroughputTask")
class ThroughputTaskState extends PeriodicState {
}
