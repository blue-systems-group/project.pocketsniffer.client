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

    private static final int BUFFER_SIZE = 4096;
    
    private Logger mLogger;

    public ThroughputTask(Context context) {
        super(context, ThroughputTask.class.getSimpleName());

        mLogger = Logger.getInstance(mContext);
    }

    private JSONObject download(String url) {
        JSONObject result = new JSONObject();

        try {
            result.put("URL", url);
        }
        catch (Exception e) {
            // ignore
        }

        long start, end;
        int size, totalSize;


        try {
            URLConnection connection = (new URL(url)).openConnection();
            connection.setConnectTimeout(mParameters.connectionTimeoutSec * 1000);
            connection.setReadTimeout(mParameters.readTimeoutSec * 1000);

            start = System.currentTimeMillis();
            {
                InputStream in = new BufferedInputStream(connection.getInputStream());
                byte[] buffer = new byte[BUFFER_SIZE];
                size = 0;
                totalSize = 0;
                while ((size = in.read(buffer)) != -1) {
                    totalSize += size;
                }
            }
            end = System.currentTimeMillis();
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to download from " + url, e);
            try {
                result.put("success", false);
            }
            catch (Exception ex) {
                // ignore
            }
            return result;
        }

        try {
            result.put("success", true);
            result.put("fileSize", totalSize);
            result.put("durationSec", (end-start)/1000);
            result.put("throughputMBps", Double.valueOf(String.format("%.2f", (double)totalSize/1024/1024/(end-start)*1000)));
        }
        catch (Exception e) {
            // ignore
        }

        return result;
    }

    @Override
    protected void check(ThroughputTaskParameters parameters) throws Exception {
        JSONObject json = new JSONObject();
        JSONArray results = new JSONArray();

        json.put(Logger.KEY_ACTION, ACTION);

        for (String url: parameters.urls) {
            Log.d(TAG, "Try downloading " + url);
            results.put(download(url));
        }
        json.put("results", results);

        Log.d(TAG, json.toString());
        mLogger.log(json);
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

}

@Root(name = "ThroughputTask")
class ThroughputTaskParameters extends PeriodicParameters {

    @ElementList
    List<String> urls;

    @Element
    Integer connectionTimeoutSec;

    @Element
    Integer readTimeoutSec;

    public ThroughputTaskParameters() {
        checkIntervalSec = 300L;

        connectionTimeoutSec = 10;
        readTimeoutSec = 10;

        urls = new ArrayList<String>();
        urls.add("http://192.168.1.1:8080/downloads/test-100M.bin");
    }

    public ThroughputTaskParameters(ThroughputTaskParameters params) {
        super(params);
        this.urls = new ArrayList<String>(params.urls);
        this.connectionTimeoutSec = params.connectionTimeoutSec;
        this.readTimeoutSec = params.readTimeoutSec;
    }
}

@Root(name = "ThroughputTask")
class ThroughputTaskState extends PeriodicState {
}
