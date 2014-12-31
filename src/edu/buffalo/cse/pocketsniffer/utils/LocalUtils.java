package edu.buffalo.cse.pocketsniffer.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import edu.buffalo.cse.phonelab.toolkit.android.utils.Utils;

public class LocalUtils {

    private static String TAG = getTag(LocalUtils.class);

    public static final int MB = 1024*1024;

    /** Generate log tag for class.  */
    public static String getTag(Class<?> c) {
        return "PocketSniffer-" + c.getSimpleName();
    }

    public static boolean isIperfAvailable() {
        return (new File("/system/bin/iperf")).exists() || (new File("/system/xbin/iperf")).exists();
    }

    public static JSONObject iperfTest(String host, int port, boolean udp, int sizeMB) throws JSONException {
        JSONObject entry = new JSONObject();

        entry.put("host", host);
        entry.put("port", port);
        entry.put("type", udp? "UDP": "TCP");

        if (!isIperfAvailable()) {
            entry.put("success", false);
            return entry;
        }

        List<String> cmd = new ArrayList<String>();

        cmd.add("iperf");
        cmd.add("-c"); cmd.add(host);
        cmd.add("-p"); cmd.add(port + "");
        cmd.add("-i"); cmd.add("1");
        cmd.add("-n"); cmd.add(sizeMB + "M");
        if (udp) {
            cmd.add("-u");
            cmd.add("-b"); cmd.add("72M");
        }

        long start = 0, end = 0;

        Object[] results = null;
        int retVal = 0;
        String output = "";
        String err = "";

        try {
            start = System.currentTimeMillis();
            results = Utils.call(cmd, -1 /* no timeout */, false /* no su */);
            end = System.currentTimeMillis();
            retVal = (Integer) results[0];
            output = (String) results[1];
            err = (String) results[2];
        }
        catch (Exception e) {
            retVal = -1;
            output = Log.getStackTraceString(e);
        }

        if (retVal != 0) {
            Log.d(TAG, "Failed to do iperf: " + output + err);
            entry.put("success", false);
            return entry;
        }

        double duration = end - start;
        entry.put("success", true);
        entry.put("fileSizeBytes", sizeMB*MB);
        entry.put("durationMs", duration);

        if (!udp) {
            List<Double> throughputs = new ArrayList<Double>();
            double overallThroughputs = 0;

            for (String line : output.split("\n")) {
                Log.d(TAG, "Parsing " + line);
                if (!line.endsWith("sec")) {
                    continue;
                }
                String[] parts = line.split("\\s");
                double bw = Double.parseDouble(parts[parts.length-2]);
                throughputs.add(bw);
                overallThroughputs = bw;
            }
            throughputs.remove(throughputs.size() - 1);

            double min = -1, max = -1, mean = 0;
            for (double d : throughputs) {
                if (min == -1 || d < min) {
                    min = d;
                }
                if (max == -1 || d > max) {
                    max = d;
                }
                mean += d;
            }
            mean /= throughputs.size();

            double stdDev = 0;
            for (double d : throughputs) {
                stdDev += Math.pow(d-mean, 2);
            }
            stdDev /= throughputs.size();
            stdDev = Math.sqrt(stdDev);

            entry.put("throughputMbps", overallThroughputs);
            entry.put("maxThroughput", max);
            entry.put("minThroughput", min);
            entry.put("stdDev", stdDev);
        }
        else {
            for (String line : output.split("\n")) {
                Log.d(TAG, "Parsing " + line);
                if (!line.endsWith("%)")) {
                    continue;
                }
                double bytes, bw, jitter;
                Pattern pattern = Pattern.compile("([\\d\\.]+)\\sMBytes\\s*([\\d\\.]+)\\sMbits/sec\\s*([\\d\\.]+)\\sms");
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    Log.d(TAG, "Match found.");
                    bytes = Double.parseDouble(matcher.group(1));
                    bw = Double.parseDouble(matcher.group(2));
                    jitter = Double.parseDouble(matcher.group(3));
                    entry.put("actualBytes", bytes);
                    entry.put("throughputMbps", bw);
                    entry.put("jitter", jitter);
                }
                else {
                    Log.d(TAG, "Match not found.");
                    entry.put("success", false);
                }
                break;
            }
        }

        return entry;
    }
}
