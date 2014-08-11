package edu.buffalo.cse.pocketsniffer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.util.Log;

public class SnifTask extends AsyncTask<SnifSpec, SnifProgress, SnifResult> {

    static {
        System.loadLibrary("pcap");
    }

    private static final String TAG = Utils.getTag(SnifTask.class);

    private final static String MONITOR_IFACE = "mon.wlan0";
    private final static int DEFAULT_SNIF_TIME_SEC = 30;

    private final static int FRAME_TYPE_DATA = 2;
    private final static int FRAME_SUBTYPE_DATA = 0;
    private final static int FRAME_SUBTYPE_QOS_DATA = 8;

    private Context mContext;
    private File mDataDir;
    private WifiManager mWifiManager;

    private Map<String, TrafficFlow> mTrafficFlow;
    private Map<String, Station> mStations;

    public SnifTask(Context context) {
        mContext = context;
        mDataDir = mContext.getDir("pcap", Context.MODE_PRIVATE);
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
    }

    private void startParsing() {
        Log.d(TAG, "Start parsing ...");
        mTrafficFlow = new HashMap<String, TrafficFlow>();
        mStations = new HashMap<String, Station>();
        for (ScanResult result : mWifiManager.getScanResults()) {
            if (!mStations.containsKey(result.BSSID)) {
                mStations.put(result.BSSID, new Station(result));
            }
        }
    }

    private void gotPacket(Packet pkt) {
        if (pkt.type != FRAME_TYPE_DATA || !(pkt.type == FRAME_SUBTYPE_DATA || pkt.type == FRAME_SUBTYPE_QOS_DATA)) {
            Log.d(TAG, "Ignoring " + pkt.toString());
            return;
        }
        if (!mStations.containsKey(pkt.addr1)) {
            mStations.put(pkt.addr1, new Station(pkt.addr1));
        }
        if (!mStations.containsKey(pkt.addr2)) {
            mStations.put(pkt.addr1, new Station(pkt.addr2));
        }
        Station from = mStations.get(pkt.addr2);
        Station to = mStations.get(pkt.addr1);
        String key = TrafficFlow.getKey(from, to);


        TrafficFlow flow;
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
        if (from.SSID != null) {
            flow.downlink = true;
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

    @Override
    protected SnifResult doInBackground(SnifSpec... params) {
        SnifResult result = new SnifResult();
        List<String> cmdBase = new ArrayList<String>();
        cmdBase.addAll(Arrays.asList(new String[]{"tcpdump", "-i", MONITOR_IFACE, "-n", "-s", "0"}));
        for (SnifSpec spec : params) {
            if (!ifaceUp(true)) {
                return null;
            }
            List<String> cmd = new ArrayList<String>();
            cmd.addAll(cmdBase);

            File capFile = new File(mDataDir, "pcap-" + spec.channel + "-" + Utils.getDateTimeString() + ".cap");
            cmd.add("-w");
            cmd.add(capFile.getAbsolutePath());

            if (spec.packetCount > 0) {
                cmd.add("-c");
                cmd.add(Integer.toString(spec.packetCount));
            }

            Object[] res = Utils.call(cmd.toArray(new String[]{}), spec.durationSec, true);
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
                result.channelTraffic.put(spec.channel, mTrafficFlow.values());
            }
            else {
                Log.e(TAG, "Failed to parse cap file.");
            }
        }
        ifaceUp(false);
        return null;
    }

    @Override
    protected void onProgressUpdate(SnifProgress... values) {
        super.onProgressUpdate(values);
    }

    private native boolean parsePcap(String capFile);
}


class SnifSpec {
    public int channel;
    public int durationSec;
    public int packetCount;

    public SnifSpec() {
        channel = -1;
        durationSec = -1;
        packetCount = -1;
    }
}

class SnifProgress {
    public int channelFinished;
    public int apFound;
    public int deviceFound;
    public int trafficVolumeBytes;
}

class Station {
    public String mac;
    public transient String manufacturer;
    public String SSID;
    public int freq;

    public Station(String mac) {
        this.mac = mac;
        manufacturer = OUI.lookup(mac);
        SSID = null;
        freq = 0;
    }

    public Station(ScanResult result) {
        mac = result.BSSID;
        manufacturer = OUI.lookup(mac);
        SSID = result.SSID;
        freq = result.frequency;
    }

    @Override
    public String toString() {
        return Utils.dumpFieldsAsJSON(this).toString();
    }
}

class TrafficFlow {
    public Station from;
    public Station to;
    public List<Integer> rssiList;
    public List<Integer> packetSizeList;
    public boolean downlink;
    public long startMs;
    public long endMs;

    public TrafficFlow(Station from, Station to) {
        this.from = from;
        this.to = to;
        rssiList = new ArrayList<Integer>();
        packetSizeList = new ArrayList<Integer>();
        downlink = false;
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
}

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

    @Override
    public String toString() {
        return Utils.dumpFieldsAsJSON(this).toString();
    }
}

class SnifResult {
    public Map<Integer, Iterable<TrafficFlow>> channelTraffic;

    public SnifResult () {
        channelTraffic = new HashMap<Integer, Iterable<TrafficFlow>>();
    }
}
