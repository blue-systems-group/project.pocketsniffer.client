package edu.buffalo.cse.pocketsniffer.interfaces;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import android.text.TextUtils;

/**
 * Traffic flow between a pair of stations.
 */
public class TrafficEntry {
    public int channel;
    public String from;
    public String to;
    public long txBytes;
    public long rxBytes;
    public String begin;
    public String end;
    private int avgTxRSSI;
    private int avgRxRSSI;

    private List<Integer> rxRSSI;
    private List<Integer> txRSSI;

    public TrafficEntry(String from, String to) {
        this.from = from;
        this.to = to;
        txBytes = 0;
        rxBytes = 0;
        rxRSSI = new ArrayList<Integer>();
        txRSSI = new ArrayList<Integer>();
    }

    public static String getKey(String from, String to) {
        if (from.compareTo(to) < 0) {
            return TextUtils.join("-", new String[]{from, to});
        }
        else {
            return TextUtils.join("-", new String[]{to, from});
        }
    }

    public String getKey() {
        return TrafficEntry.getKey(this.from, this.to);
    }

    public void putRxRSSI(int rssi) {
        rxRSSI.add(rssi);
    }
    
    public void putTxRSSI(int rssi) {
        txRSSI.add(rssi);
    }

    public JSONObject toJSONObject() {
        JSONObject json = new JSONObject();

        try {
            json.put("from", from);
            json.put("to", to);
            json.put("txBytes", txBytes);
            json.put("rxBytes", rxBytes);
            json.put("begin", begin);
            json.put("end", end);
            json.put("avgTxRSSI", avgTxRSSI);
            json.put("avgRxRSSI", avgRxRSSI);
        }
        catch (Exception e) {
            // ignore
        }
        return json;
    }
}
