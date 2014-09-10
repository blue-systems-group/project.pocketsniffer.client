package edu.buffalo.cse.pocketsniffer.interfaces;

import java.util.ArrayList;
import java.util.List;

import android.net.wifi.ScanResult;

import edu.buffalo.cse.pocketsniffer.utils.Utils;


/**
 * A wireless station.
 *
 * Could be AP or device, depending on SSID field.
 */
public class Station {
    public String mac;
    public boolean isAP;
    /* if isAP is true, then this is AP's SSID, otherwise, this is the AP that
     * the device connects to */
    public String SSID;
    public int freq;
    public List<Integer> rssiList;

    public Station(String mac) {
        this.mac = mac.toUpperCase();
        SSID = null;
        freq = -1;
        isAP = false;
        rssiList = new ArrayList<Integer>();
    }

    public Station(ScanResult result) {
        this(result.BSSID);
        isAP = true;
        SSID = result.SSID;
        freq = result.frequency;
        rssiList.add(result.level);
    }

    public int getAvgRSSI() {
        if (rssiList.size() == 0) {
            return 0;
        }
        int sum = 0;
        for (int rssi : rssiList) {
            sum += rssi;
        }
        return sum / rssiList.size();
    }

    @Override
    public String toString() {
        try {
            return Utils.dumpFieldsAsJSON(this).toString();
        }
        catch (Exception e) {
            return "<unknown>";
        }
    }

    public String getKey() {
        return Station.getKey(this.mac);
    }

    public static String getKey(String mac) {
        return mac.toUpperCase();
    }
}
