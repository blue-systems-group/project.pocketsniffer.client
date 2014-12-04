package edu.buffalo.cse.pocketsniffer.tasks;

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

public class PingTask extends PeriodicTask<PingTaskParameters, PingTaskState> {
    private static final String TAG = LocalUtils.getTag(PingTask.class);
    private static final String ACTION = PingTask.class.getName() + ".PingResult";

    private Logger mLogger;

    public PingTask(Context context) {
        super(context, PingTask.class.getSimpleName());

        mLogger = Logger.getInstance(mContext);
    }

    private JSONObject parstPingOutput(String output) {
        JSONObject json = new JSONObject();

        for (String line : output.split("\n")) {
            line = line.trim();
            Log.d(TAG, "Parsing line: " + line);

            try {
                if (line.matches("^PING.*$")) {
                    String[] parts = line.split(" ");
                    json.put("host", parts[1]);
                    json.put("IP", parts[2].substring(1,parts[2].length()-1));
                }
                else if (line.matches("^\\d*\\spackets transmitted.*$")) {
                    String[] parts = line.split(" ");
                    json.put("packetTransmitted", Integer.parseInt(parts[0]));
                    json.put("packetReceived", Integer.parseInt(parts[3]));
                }
                else if (line.startsWith("rtt")) {
                    String[] parts = line.split(" ")[3].split("/");
                    json.put("min", Double.parseDouble(parts[0]));
                    json.put("avg", Double.parseDouble(parts[1]));
                    json.put("max", Double.parseDouble(parts[2]));
                    json.put("mdev", Double.parseDouble(parts[3]));
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to parse line: " + line, e);
            }
        }
        return json;
    }

    @Override
    protected void check(PingTaskParameters parameters) throws Exception {

        if (!Utils.hasNetworkConnection(mContext)) {
            Log.w(TAG, "No network connection. Do not ping.");
            return;
        }

        JSONObject json = new JSONObject();
        JSONArray results = new JSONArray();

        json.put(Logger.KEY_ACTION, ACTION);

        for (String host : parameters.hosts) {
            if (!Utils.hasNetworkConnection(mContext)) {
                break;
            }

            JSONObject entry = new JSONObject();
            entry.put("host", host);
            List<String> cmd = new ArrayList<String>();
            cmd.add("ping");
            cmd.add("-c");
            cmd.add(Integer.toString(parameters.packetNum));
            cmd.add(host);
            Log.d(TAG, "Ping " + host + " ...");
            String output = (String) Utils.call(cmd, -1 /* no timeout*/, false /* do not require su */)[1];
            results.put(parstPingOutput(output));
        }
        json.put("results", results);

        Log.d(TAG, json.toString());

        mLogger.log(json);
    }

    @Override
    public PingTaskParameters newParameters() {
        return new PingTaskParameters();
    }

    @Override
    public PingTaskParameters newParameters(PingTaskParameters arg0) {
        return new PingTaskParameters(arg0);
    }

    @Override
    public PingTaskState newState() {
        return new PingTaskState();
    }

    @Override
    public Class<PingTaskParameters> parameterClass() {
        return PingTaskParameters.class;
    }

    @Override
    public String getTag() {
        return TAG;
    }
}

@Root(name = "PingTask")
class PingTaskParameters extends PeriodicParameters {

    @ElementList
    public List<String> hosts;

    @Element
    public Integer packetNum;


    public PingTaskParameters() {
        checkIntervalSec = 300L;
        hosts = new ArrayList<String>();
        packetNum = 10;
    }

    public PingTaskParameters(PingTaskParameters parameters) {
        super(parameters);
        this.hosts = new ArrayList<String>(parameters.hosts);
        this.packetNum = parameters.packetNum;
    }
}

@Root(name = "PingTask")
class PingTaskState extends PeriodicState {
}
