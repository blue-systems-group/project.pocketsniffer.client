package edu.buffalo.cse.pocketsniffer.tasks;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
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
        String url = null;
        try {
            url = String.format(mParameters.urlFormat, fileSizeMB);
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to compose download url.", e);
            startOneShot(mParameters.minIntervalSec);
            return;
        }

        JSONObject json = new JSONObject();
        json.put(Logger.KEY_ACTION, ACTION);

        Log.d(TAG, "Try downloading " + url);
        LocalUtils.testThroughput(url, json);

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
    Long maxIntervalSec;

    @Element
    Long minIntervalSec;

    @Element
    Integer maxFileSizeMB;

    @Element
    Integer minFileSizeMB;

    @Element
    String urlFormat;

    public ThroughputTaskParameters() {
        checkIntervalSec = 300L;
        maxIntervalSec = 900L;
        minIntervalSec = 300L;
        maxFileSizeMB = 100;
        minFileSizeMB = 50;
        urlFormat = "http://192.168.1.1:8080/downloads/test-%dM.bin";
    }

    public ThroughputTaskParameters(ThroughputTaskParameters params) {
        super(params);
        this.maxIntervalSec = params.maxIntervalSec;
        this.minIntervalSec = params.minIntervalSec;
        this.maxFileSizeMB = params.maxFileSizeMB;
        this.minFileSizeMB = params.minFileSizeMB;
        this.urlFormat = params.urlFormat;
    }
}

@Root(name = "ThroughputTask")
class ThroughputTaskState extends PeriodicState {
}
