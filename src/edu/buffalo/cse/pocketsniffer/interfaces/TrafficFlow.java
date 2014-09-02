package edu.buffalo.cse.pocketsniffer.interfaces;

import java.util.ArrayList;
import java.util.List;

import android.text.TextUtils;

/**
 * Traffic flow between a pair of stations.
 */
public class TrafficFlow {
    public Station from;
    public Station to;
    public List<Integer> packetSizeList;
    public int direction;
    public long startMs;
    public long endMs;

    public static final int DIRECTION_DOWNLINK = 0;
    public static final int DIRECTION_UPLINK = 1;
    public static final int DIRECTION_UNKNOWN = 2;

    public TrafficFlow(Station from, Station to) {
        this.from = from;
        this.to = to;
        packetSizeList = new ArrayList<Integer>();
        direction = DIRECTION_UNKNOWN;
    }

    public int totalBytes() {
        int sum = 0;
        for (int s : packetSizeList) {
            sum += s;
        }
        return sum;
    }

    public static String getKey(Station from, Station to) {
        return TextUtils.join("-", new String[]{from.mac, to.mac});
    }
}
