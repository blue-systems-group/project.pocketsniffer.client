package edu.buffalo.cse.pocketadmin;

import android.util.Log;

import java.util.HashMap;
import java.util.Arrays;
import java.util.Map;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import net.kismetwireless.android.pcapcapture.PacketHandler;
import net.kismetwireless.android.pcapcapture.UsbSource;
import net.kismetwireless.android.pcapcapture.Packet;

public class SnifferPacketHandler extends PacketHandler {
    private final String TAG = "PocketAdmin-" + this.getClass().getSimpleName();

    private final static int MAC_ADDR_LEN = 6;

    private final static int IEEE80211_HEADER_OFFSET = 0;

    private final static int IEEE80211_HEADER_FLAG_OFFSET = 0;
    private final static int IEEE80211_HEADER_DURATION_OFFSET = 2;
    private final static int IEEE80211_HEADER_DST_MAC_OFFSET = 4;
    private final static int IEEE80211_HEADER_SRC_MAC_OFFSET = 10;
    private final static int IEEE80211_HEADER_BSSID_MAC_OFFSET = 16;
    private final static int IEEE80211_HEADER_SEQ_OFFSET = 22;

    private final static int IEEE80211_BEACON_SSID_LEN_OFFSET = 37;
    private final static int IEEE80211_BEACON_SSID_OFFSET = 38;

    public enum FrameType {
        BEACON, DATA, UNKNOWN
    }

    private HashMap<String, TrafficInfo> mTraffics;
    private boolean mRunning;
    private int mChannel;

    private static SnifferPacketHandler mInstance;

    public static SnifferPacketHandler getInstance() {
        if (mInstance == null) {
            mInstance = new SnifferPacketHandler();
        }
        return mInstance;
    }

    private String dumpPacket(Packet p) {
        StringBuilder str = new StringBuilder();

        for (int i = 0; i < p.bytes.length; i++) {
            str.append(String.format("%02X ", p.bytes[i]));
        }
        return str.toString();
    }

    private FrameType getFrameType(Packet p) {
        int type = (p.bytes[IEEE80211_HEADER_OFFSET] & 0x0c) >> 2;
        int subType = (p.bytes[IEEE80211_HEADER_OFFSET] & 0xf0) >> 4;

        if ((type == 0) && (subType == 8)) {
            return FrameType.BEACON;
        }
        else if (type == 2) {
            return FrameType.DATA;
        }
        else {
            return FrameType.UNKNOWN;
        }

    }


    private String getSrcMac(Packet p) {
        int base = IEEE80211_HEADER_OFFSET + IEEE80211_HEADER_SRC_MAC_OFFSET;
        StringBuilder mac = new StringBuilder();

        try {
            for (int i = base; i < base + MAC_ADDR_LEN; i++) {
                mac.append(String.format("%02X", p.bytes[i]));
            }
            return mac.toString();
        }
        catch (Exception e) {
            // Log.e(TAG, "Faild to get src MAC: " + dumpPacket(p));
            return null;
        }
    }

    private String getSSID(Packet p) {
        if (getFrameType(p) != FrameType.BEACON) {
            return null;
        }

        int length = p.bytes[IEEE80211_BEACON_SSID_LEN_OFFSET];
        StringBuilder ssid = new StringBuilder();
        for (int i = 0; i < length; i++) {
            ssid.append(String.format("%c", p.bytes[IEEE80211_BEACON_SSID_OFFSET+i]));
        }
        return ssid.toString();
    }

    public class TrafficInfo {
        public int channel;
        public int bytes;
        public String ssid;

        private static final int SIGNAL_WINDOW_SIZE = 10;


        private int tail;
        private int[] signalBuf;

        public TrafficInfo() {
            signalBuf = new int[SIGNAL_WINDOW_SIZE];
            Arrays.fill(signalBuf, 0, signalBuf.length, 0);

            tail = 0;
            channel = 0;
            bytes = 0;
        }

        public int getSignal() {
            int sum = 0;
            for (int i = 0; i < signalBuf.length; i++) {
                sum += signalBuf[i];
            }
            return sum / signalBuf.length;
        }

        public void putSignal(int signal) {
            signalBuf[tail] = signal;
            tail = (tail + 1) % signalBuf.length;
        }

        public String toString() {
            JSONObject json = new JSONObject();
            try {
                json.put("channel", channel);
                json.put("bytes", bytes);
                json.put("signal", getSignal());
                if (ssid != null) {
                    json.put("ssid", ssid);
                }
            }
            catch (JSONException e) {
                Log.e(TAG, "Failed to convert to JSON: " + e.getMessage());
            }

            return json.toString();
        }
    }

    private SnifferPacketHandler() {
        mTraffics = new HashMap<String, TrafficInfo>();
        mRunning = false;
        mChannel = 0;
    }

    public boolean isRunning() {
        return mRunning;
    }

    public void setRunning(boolean r, int channel) {
        mRunning = r;
        if (mRunning) {
            mChannel = channel;
            if (channel < 0 || channel > 11) {
                Log.e(TAG, "Invalid channel " + channel);
                mRunning = false;
            }
            else {
                Log.d(TAG, "Start sniffing on channel " + mChannel);
            }
        }
        else {
            Log.d(TAG, "Stop sniffing on channel " + mChannel);
            mChannel = 0;
            mTraffics.clear();
        }
    }

    public String collect() {
        Log.d(TAG, "Collecting traffic info");

        if (mRunning) {
            Log.e(TAG, "Collect traffic info while running!");
            return "";
        }

        JSONArray array = new JSONArray();

        for (Map.Entry<String, TrafficInfo> entry : mTraffics.entrySet()) {
            JSONObject json = new JSONObject();
            try {
                json.put(entry.getKey(), entry.getValue().toString());
            }
            catch (JSONException e) {
                Log.e(TAG, "Failed to convert JSON: " + e.getMessage());
                continue;
            }
            array.put(json);
        }
        return array.toString();
    }


    public void handlePacket(UsbSource s, Packet p) {
        if (!mRunning) {
            return;
        }

        FrameType frameType = getFrameType(p);

        if (frameType == FrameType.UNKNOWN) {
            return;
        }

        String src = getSrcMac(p);
        if (src == null) {
            return;
        }

        if (mTraffics.containsKey(src)) {
            TrafficInfo info = mTraffics.get(src);
            info.bytes += p.bytes.length;
            info.putSignal(p.signal);
            if (info.ssid == null && frameType == FrameType.BEACON) {
                info.ssid = getSSID(p);
            }
        }
        else {
            TrafficInfo info = new TrafficInfo();
            info.channel = mChannel;
            info.bytes = p.bytes.length;
            info.putSignal(p.signal);
            mTraffics.put(src, info);

            if (frameType == FrameType.BEACON) {
                info.ssid = getSSID(p);
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 32 && i < p.bytes.length; i++) {
                sb.append(String.format("%02X ", p.bytes[i]));
            }
            Log.d(TAG, "new source " + src + (info.ssid == null? "": ("(" + info.ssid + ")")));
            Log.v(TAG, sb.toString());
        }
    }
}
