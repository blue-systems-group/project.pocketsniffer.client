package edu.buffalo.cse.pocketsniffer.utils;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONObject;

import android.util.Log;

public class LocalUtils {

    private static String TAG = getTag(LocalUtils.class);

    /** Generate log tag for class.  */
    public static String getTag(Class<?> c) {
        return "PocketSniffer-" + c.getSimpleName();
    }

    public static JSONObject testThroughput(String url) {
        JSONObject entry = new JSONObject();
        testThroughput(url, entry);
        return entry;
    }

    public static void testThroughput(String url, JSONObject entry) {
        final int BUF_SIZE = 1024*1024;
        int pos, size, totalSize;
        long start, end, duration;
        byte[] buffer = new byte[BUF_SIZE];
        List<Double> throughputs = new ArrayList<Double>();

        try {
            URLConnection connection = (new URL(url)).openConnection();
            InputStream in = new BufferedInputStream(connection.getInputStream());
            size = 0;
            totalSize = 0;
            duration = 0;
            while (true) {
                pos = 0;
                start = System.currentTimeMillis();
                while (pos != buffer.length && (size = in.read(buffer, pos, buffer.length-pos)) != -1) {
                    pos += size;
                }
                end = System.currentTimeMillis();

                duration += (end - start);
                totalSize += pos;

                if (size == -1) {
                    break;
                }

                throughputs.add((double) pos / 1024.0 / 1024.0 / (end - start) * 1000.0);
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to download from " + url, e);
            try {
                entry.put("success", false);
            }
            catch (Exception ex) {
                // ignore
            }
            return;
        }

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
        mean = mean / throughputs.size();

        double stdDev = 0;
        for (double d : throughputs) {
            stdDev += Math.pow(d-mean, 2);
        }
        stdDev = Math.sqrt(stdDev);

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
