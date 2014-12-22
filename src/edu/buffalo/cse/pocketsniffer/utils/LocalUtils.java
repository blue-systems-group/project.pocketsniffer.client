package edu.buffalo.cse.pocketsniffer.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import edu.buffalo.cse.phonelab.toolkit.android.utils.Utils;

public class LocalUtils {

    private static String TAG = getTag(LocalUtils.class);

    /** Generate log tag for class.  */
    public static String getTag(Class<?> c) {
        return "PocketSniffer-" + c.getSimpleName();
    }

    public static boolean isIperfAvailable() {
        return (new File("/system/bin/iperf")).exists() || (new File("/system/xbin/iperf")).exists();
    }

    public static JSONObject iperfTest(String host, int port, int sizeMB) throws JSONException {
        JSONObject entry = new JSONObject();

        if (!isIperfAvailable()) {
            entry.put("success", false);
            return entry;
        }

        List<String> cmd = new ArrayList<String>();

        cmd.add("iperf");
        cmd.add("-c");
        cmd.add(host);
        cmd.add("-p");
        cmd.add(port + "");
        cmd.add("-i");
        cmd.add("1");
        cmd.add("-n");
        cmd.add(sizeMB + "");

        Object[] results = Utils.call(cmd, -1 /* no timeout */, false /* no su */);
        int retVal = (Integer) results[0];
        String output = (String) results[1];
        String err = (String) results[2];

        if (retVal != 0) {
            Log.d(TAG, "Failed to do iperf: " + output + err);
            entry.put("success", false);
            return entry;
        }

        List<Double> throughputs = new ArrayList<Double>();
        double overalThroughputs = 0;

        for (String line : output.split("\n")) {
            if (!line.endsWith("sec")) {
                continue;
            }
            String[] parts = line.split(" ");
            double bw = Double.parseDouble(parts[parts.length-2]);
            throughputs.add(bw);
            overalThroughputs = bw;
        }
        throughputs.remove(throughputs.size() - 1);

        try {
            entry.put("success", true);
            entry.put("fileSizeBytes", totalSize);
            entry.put("durationMs", duration);
            entry.put("throughputMbps", Double.valueOf(String.format("%.2f", (double) totalSize/1024/1024/duration*1000)));
            entry.put("maxThroughput", Double.valueOf(String.format("%.2f", max)));
            entry.put("minThroughput", Double.valueOf(String.format("%.2f", min)));
            entry.put("stdDev", Double.valueOf(String.format("%.2f", stdDev)));
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to put download stats.", e);
        }
    }
}
