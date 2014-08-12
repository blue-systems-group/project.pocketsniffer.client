package edu.buffalo.cse.pocketsniffer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.util.Log;

public class SnifTask extends Task<SnifParams, SnifProgress, SnifResult> {

    static {
        System.loadLibrary("pcap");
    }

    private final static String MONITOR_IFACE = "mon.wlan0";
    private final static int DEFAULT_SNIF_TIME_SEC = 30;

    private final static int FRAME_TYPE_DATA = 2;
    private final static int FRAME_SUBTYPE_DATA = 0;
    private final static int FRAME_SUBTYPE_QOS_DATA = 8;
    private final static String BROADCAST_MAC = "FF:FF:FF:FF:FF";

    private File mDataDir;
    private WifiManager mWifiManager;

    private Map<String, TrafficFlow> mTrafficFlow;
    private Map<String, Station> mStations;

    private native boolean parsePcap(String capFile);

    public SnifTask(Context context, AsyncTaskListener<SnifParams, SnifProgress, SnifResult> listener) {
        super(context, listener);
        mDataDir = mContext.getDir("pcap", Context.MODE_PRIVATE);
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
    }

    private void startParsing() {
        mTrafficFlow = new HashMap<String, TrafficFlow>();
        mStations = new HashMap<String, Station>();
        for (ScanResult result : mWifiManager.getScanResults()) {
            String key = Station.getKey(result.BSSID);
            if (!mStations.containsKey(key)) {
                mStations.put(key, new Station(result));
                Log.d(TAG, "Putting " + result.SSID + " (" + key + ")");
            }
        }
    }

    private void gotPacket(Packet pkt) {
        if (!pkt.crcOK) {
            Log.d(TAG, "Corrupted packet detected.");
            return;
        }

        Station from, to;
        TrafficFlow flow;
        String key;

        if (pkt.addr2 != null) {
            key = Station.getKey(pkt.addr2);
            if (!mStations.containsKey(key)) {
                from = new Station(pkt.addr2);
                from.freq = pkt.freq;
                mStations.put(key, from);
            }
            else {
                from = mStations.get(key);
            }
            if (pkt.SSID != null) {
                from.SSID = pkt.SSID;
            }
        }
        else {
            from = null;
        }

        if (!BROADCAST_MAC.equals(pkt.addr1)) {
            key = Station.getKey(pkt.addr1);
            if (!mStations.containsKey(key)) {
                to = new Station(pkt.addr1);
                to.freq = pkt.freq;
                mStations.put(key, to);
            }
            else {
                to = mStations.get(key);
            }
        }
        else {
            to = null;
        }


        if (pkt.type != FRAME_TYPE_DATA || !(pkt.subtype == FRAME_SUBTYPE_DATA || pkt.subtype == FRAME_SUBTYPE_QOS_DATA)) {
            return;
        }

        key = TrafficFlow.getKey(from, to);
        if (!mTrafficFlow.containsKey(key)) {
            flow = new TrafficFlow(from, to);
            flow.startMs = pkt.tv_sec * 1000 + pkt.tv_usec / 1000;
            mTrafficFlow.put(key, flow);
        }
        else {
            flow = mTrafficFlow.get(key);
        }

        flow.rssiList.add(pkt.rssi);
        flow.packetSizeList.add(pkt.len);
        if (flow.direction == TrafficFlow.DIRECTION_UNKNOWN) {
            if (from.SSID != null) {
                flow.direction = TrafficFlow.DIRECTION_DOWNLINK;
            }
            else if (to.SSID != null) {
                flow.direction = TrafficFlow.DIRECTION_UPLINK;
            }
        }
        flow.endMs = pkt.tv_sec * 1000 + pkt.tv_usec / 1000;
        Log.d(TAG, from.toString() + " ===> " + to.toString() + ": " + flow.totalBytes());
    }

    private boolean ifaceUp(boolean up) {
        String[] cmd = {"ifconfig", MONITOR_IFACE, up? "up": "down"};
        Object[] result = Utils.call(cmd, -1, true);
        int exitCode = (Integer) result[0];
        if (exitCode == 0) {
            Log.d(TAG, "Successfully bring " + (up? "up": "down") +  " monitor iface.");
            return true;
        }
        else {
            String err = (String) result[2];
            Log.e(TAG, "Failed to bring up monitor interface (" + exitCode + "): " + err);
            return false;
        }
    }

    private boolean setChannel(int chan) {
        String[] cmd = new String[]{"iw", "phy0", "set", "channel", Integer.toString(chan)};
        Object[] result = Utils.call(cmd, -1, true);
        Integer exitCode = (Integer) result[0];
        if (exitCode == 0) {
            return true;
        }
        else {
            String err = (String) result[2];
            Log.e(TAG, "Failed to set channel (" + exitCode + "): " + err);
            return false;
        }
    }

    @Override
    protected SnifResult doInBackground(SnifParams... params) {
        SnifResult result = new SnifResult();

        List<String> cmdBase = new ArrayList<String>();
        cmdBase.addAll(Arrays.asList(new String[]{"tcpdump", "-i", MONITOR_IFACE, "-n", "-s", "0"}));

        SnifParams param = params[0];

        mWifiManager.disconnect();

        for (int chan : param.channels) {
            if (isCancelled()) {
                Log.d(TAG, "Task cancelled.");
                break;
            }

            if (!ifaceUp(true)) {
                continue;
            }

            if (!setChannel(chan)) {
                continue;
            }

            Log.d(TAG, "Sniffing channel " + chan + "...");

            List<String> cmd = new ArrayList<String>();
            cmd.addAll(cmdBase);

            File capFile = new File(mDataDir, "pcap-" + chan + "-" + Utils.getDateTimeString() + ".cap");
            cmd.add("-w");
            cmd.add(capFile.getAbsolutePath());

            if (param.packetCount > 0) {
                cmd.add("-c");
                cmd.add(Integer.toString(param.packetCount));
            }

            Object[] res = Utils.call(cmd.toArray(new String[]{}), param.durationSec, true);
            Integer exitValue = (Integer) res[0];
            String err = (String) res[2];
            if (exitValue != 0) {
                Log.e(TAG, "Failed to call tcpdump (" + exitValue + "): " + err);
                continue;
            }

            cmd.clear();
            cmd.add("chown");
            cmd.add(Integer.toString(Utils.getMyUid(mContext)));
            cmd.add(capFile.getAbsolutePath());
            Utils.call(cmd.toArray(new String[]{}), -1, true);

            startParsing();
            if (parsePcap(capFile.getAbsolutePath())) {
                result.channelTraffic.put(chan, mTrafficFlow.values());
                result.channelStation.put(chan, mStations.values());
            }
            else {
                Log.e(TAG, "Failed to parse cap file.");
            }
        }
        ifaceUp(false);
        mWifiManager.reconnect();
        return result;
    }
}

/** A wireless station. Could be AP or device. */
class Station {
    public String mac;
    public transient String manufacturerShort;
    public transient String manufacturerLong;
    public String SSID;
    public int freq;

    public Station(String mac) {
        this.mac = mac.toUpperCase();
        String[] manufactureInfo = OUI.lookup(mac);
        manufacturerShort = manufactureInfo[0];
        manufacturerLong = manufactureInfo[1];
        SSID = null;
        freq = -1;
    }

    public Station(ScanResult result) {
        this(result.BSSID);
        SSID = result.SSID;
        freq = result.frequency;
    }

    @Override
    public String toString() {
        return Utils.dumpFieldsAsJSON(this).toString();
    }

    public static String getKey(String mac) {
        return mac.toUpperCase();
    }
}

/** Traffic flow between a pair of stations. */
class TrafficFlow {
    public Station from;
    public Station to;
    public List<Integer> rssiList;
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
        rssiList = new ArrayList<Integer>();
        packetSizeList = new ArrayList<Integer>();
        direction = DIRECTION_UNKNOWN;
    }

    public int avgRSSI() {
        Integer sum = 0;
        for (Integer i : rssiList) {
            sum += i;
        }
        return (int)(sum.doubleValue() / rssiList.size());
    }

    public int totalBytes() {
        Integer sum = 0;
        for (Integer i : packetSizeList) {
            sum += i;
        }
        return sum;
    }

    public static String getKey(Station from, Station to) {
        return Utils.join("-", new String[]{from.mac, to.mac});
    }

    @Override
    public String toString() {
        return null;
    }
}

/** All the information we care about a packet. */
class Packet {
    public int type;
    public int subtype;
    public boolean from_ds;
    public boolean to_ds;
    public int tv_sec;
    public int tv_usec;
    public int len;
    public String addr1;
    public String addr2;
    public int rssi;
    public int freq;
    public boolean crcOK;
    public String SSID;

    @Override
    public String toString() {
        return Utils.dumpFieldsAsJSON(this).toString();
    }
}


/** SnifTask parameters. */
class SnifParams {
    public int[] channels;
    public int durationSec;
    public int packetCount;

    public SnifParams(int[] channels) {
        this.channels = channels;
        durationSec = -1;
        packetCount = -1;
    }
}


/** Progress update of SnifTask. */
class SnifProgress {
    public int channelFinished;
    public int apFound;
    public int deviceFound;
    public int trafficVolumeBytes;
}

/** Result of SnifTask. */
class SnifResult {
    public Map<Integer, Iterable<TrafficFlow>> channelTraffic;
    public Map<Integer, Iterable<Station>> channelStation;

    public SnifResult () {
        channelTraffic = new HashMap<Integer, Iterable<TrafficFlow>>();
        channelStation = new HashMap<Integer, Iterable<Station>>();
    }
}
