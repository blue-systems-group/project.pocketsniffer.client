package edu.buffalo.cse.pocketsniffer.tasks;

import org.json.JSONObject;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

import android.content.Context;
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

        if (!Utils.hasNetworkConnection(mContext)) {
            Log.w(TAG, "No network connection. Do not download.");
            return;
        }

        int fileSizeMB = (int) (mParameters.minFileSizeMB + Math.random() * (mParameters.maxFileSizeMB - mParameters.minFileSizeMB));

        Log.d(TAG, "Try iperf with " + mParameters.iperfHost + ":" + mParameters.iperfPort);
        JSONObject json = LocalUtils.iperfTest(mParameters.iperfHost, mParameters.iperfPort, fileSizeMB);
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
    String iperfHost;

    @Element
    Integer iperfPort;

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
        maxIntervalSec = 900L;
        minIntervalSec = 300L;
        maxFileSizeMB = 100;
        minFileSizeMB = 50;
        iperfHost = "192.168.1.1";
        iperfPort = 5001;
    }

    public ThroughputTaskParameters(ThroughputTaskParameters params) {
        super(params);
        this.maxIntervalSec = params.maxIntervalSec;
        this.minIntervalSec = params.minIntervalSec;
        this.maxFileSizeMB = params.maxFileSizeMB;
        this.minFileSizeMB = params.minFileSizeMB;
        this.iperfHost = params.iperfHost;
        this.iperfPort = params.iperfPort;
    }
}

@Root(name = "ThroughputTask")
class ThroughputTaskState extends PeriodicState {
}
