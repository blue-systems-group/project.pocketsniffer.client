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
    public String src;
    public long packets;                // total good packet number
    public long retryPackets;           // total retry packet number
    public long corruptedPackets;       // total bad packet number
    public long bytes;
    public String begin;
    public String end;

    private List<Integer> rssi;

    public TrafficEntry(String src) {
        this.src = src;
        packets = 0L;
        retryPackets = 0L;
        corruptedPackets = 0L;
        bytes = 0L;
        rssi = new ArrayList<Integer>();
    }

    public void putRSSI(int r) {
        rssi.add(r);
    }
    
    public int getAvgRSSI() {
        int sum = 0;
        for (int r : rssi) {
            sum += r;
        }
        if (rssi.size() == 0) {
            return 0;
        }
        else {
            return sum / rssi.size();
        }
    }

    public JSONObject toJSONObject() {
        JSONObject json = new JSONObject();

        try {
            json.put("channel", channel);
            json.put("src", src);
            json.put("packets", packets);
            json.put("retryPackets", retryPackets);
            json.put("corruptedPackets", corruptedPackets);
            json.put("bytes", bytes);
            json.put("begin", begin);
            json.put("end", end);
            json.put("avgRSSI", getAvgRSSI());
        }
        catch (Exception e) {
            // ignore
        }
        return json;
    }
}
