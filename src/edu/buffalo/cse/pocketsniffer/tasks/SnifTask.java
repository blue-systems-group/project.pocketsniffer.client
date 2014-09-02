package edu.buffalo.cse.pocketsniffer.tasks;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.util.Log;

import edu.buffalo.cse.pocketsniffer.interfaces.Station;
import edu.buffalo.cse.pocketsniffer.interfaces.TrafficFlow;
import edu.buffalo.cse.pocketsniffer.utils.Utils;

public class SnifTask extends Task<SnifTask.Params, SnifTask.Progress, SnifTask.Result> {

    static {
        System.loadLibrary("pcap");
    }

    private final static String MONITOR_IFACE = "mon.wlan0";
    private final static String WL_DEV_NAME = "phy0";
    private final static int DEFAULT_SNIF_TIME_SEC = 30;

    private final static int FRAME_TYPE_DATA = 2;
    private final static int FRAME_SUBTYPE_DATA = 0;
    private final static int FRAME_SUBTYPE_QOS_DATA = 8;
    private final static String BROADCAST_MAC = "FF:FF:FF:FF:FF";

    private File mDataDir;
    private WifiManager mWifiManager;

    private Map<String, TrafficFlow> mTrafficFlowCache;
    private Map<String, Station> mStationCache;
    private Integer mPacketCount;
    private Integer mCorruptedPacketCount;
    private Integer mIgnoredPacketCount;

    private Result mLastResult;

    private native boolean parsePcap(String capFile);

    public SnifTask(Context context, AsyncTaskListener<SnifTask.Params, SnifTask.Progress, SnifTask.Result> listener) {
        super(context, listener);
        mDataDir = mContext.getDir("pcap", Context.MODE_PRIVATE);
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mTrafficFlowCache = new HashMap<String, TrafficFlow>();
        mStationCache = new HashMap<String, Station>();
    }

    /**
     * Before parsing any packets, clear storage, and update scan results.
     */
    private void startParsing() {
        mTrafficFlowCache.clear();
        mStationCache.clear();

        mCorruptedPacketCount = 0;
        mPacketCount = 0;
        mIgnoredPacketCount = 0;

        for (ScanResult result : mWifiManager.getScanResults()) {
            String key = Station.getKey(result.BSSID);
            if (!mStationCache.containsKey(key)) {
                mStationCache.put(key, new Station(result));
                Log.d(TAG, "Putting " + result.SSID + " (" + key + ")");
            }
        }
    }

    /**
     * This function get called on each packet.
     */
    private void gotPacket(Packet pkt) {
        mPacketCount++;

        if (!pkt.crcOK) {
            Log.d(TAG, "Corrupted packet detected.");
            mCorruptedPacketCount++;
            return;
        }

        Station from, to;
        TrafficFlow flow;
        String key;

        if (pkt.addr2 != null) {
            key = Station.getKey(pkt.addr2);
            if (!mStationCache.containsKey(key)) {
                from = new Station(pkt.addr2);
                from.freq = pkt.freq;
                mStationCache.put(key, from);
            }
            else {
                from = mStationCache.get(key);
            }
            if (pkt.SSID != null) {
                from.SSID = pkt.SSID;
            }
            from.rssiList.add(pkt.rssi);
        }
        else {
            from = null;
        }

        if (!BROADCAST_MAC.equals(pkt.addr1)) {
            key = Station.getKey(pkt.addr1);
            if (!mStationCache.containsKey(key)) {
                to = new Station(pkt.addr1);
                to.freq = pkt.freq;
                mStationCache.put(key, to);
            }
            else {
                to = mStationCache.get(key);
            }
        }
        else {
            to = null;
        }

        // only count data packets for traffic volumns
        if (pkt.type != FRAME_TYPE_DATA || !(pkt.subtype == FRAME_SUBTYPE_DATA || pkt.subtype == FRAME_SUBTYPE_QOS_DATA) || from == null || to == null) {
            mIgnoredPacketCount++;
            return;
        }

        key = TrafficFlow.getKey(from, to);
        if (!mTrafficFlowCache.containsKey(key)) {
            flow = new TrafficFlow(from, to);
            flow.startMs = pkt.tv_sec * 1000 + pkt.tv_usec / 1000;
            mTrafficFlowCache.put(key, flow);
        }
        else {
            flow = mTrafficFlowCache.get(key);
        }

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
    }

    @Override
    protected SnifTask.Result doInBackground(SnifTask.Params... params) {
        SnifTask.Result result = new SnifTask.Result();
        SnifTask.Progress progress = new SnifTask.Progress();

        List<String> cmdBase = new ArrayList<String>();
        cmdBase.addAll(Arrays.asList(new String[]{"tcpdump", "-i", MONITOR_IFACE, "-n", "-s", "0"}));

        SnifTask.Params param = params[0];

        mWifiManager.disconnect();

        for (int chan : param.channels) {
            if (isCancelled()) {
                Log.d(TAG, "Task cancelled.");
                break;
            }

            try {
                if (!Utils.ifaceUp(MONITOR_IFACE, true)) {
                    continue;
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to bring up iface " + MONITOR_IFACE + ".", e);
                continue;
            }

            try {
                if (!Utils.setChannel(WL_DEV_NAME, chan)) {
                    continue;
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to set channel", e);
                continue;
            }

            Log.d(TAG, "Sniffing channel " + chan + " ...");

            List<String> cmd = new ArrayList<String>();
            cmd.addAll(cmdBase);

            File capFile = new File(mDataDir, "pcap-" + chan + "-" + Utils.getDateTimeString() + ".cap");
            cmd.add("-w");
            cmd.add(capFile.getAbsolutePath());

            if (param.packetCount > 0) {
                cmd.add("-c");
                cmd.add(Integer.toString(param.packetCount));
            }

            Object[] res;
            try {
                res = Utils.call(cmd, param.durationSec, true);
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to snif.", e);
                continue;
            }
            Integer exitValue = (Integer) res[0];
            String err = (String) res[2];
            if (exitValue != 0) {
                Log.e(TAG, "Failed to call tcpdump (" + exitValue + "): " + err);
                continue;
            }


            cmd.clear();
            cmd.add("chown");
            try {
                cmd.add(Integer.toString(Utils.getMyUid(mContext)));
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to get my uid.", e);
                continue;
            }
            cmd.add(capFile.getAbsolutePath());
            try {
                Utils.call(cmd, -1, true);
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to change file ownership.", e);
                continue;
            }

            startParsing();
            if (!parsePcap(capFile.getAbsolutePath())) {
                Log.e(TAG, "Failed to parse cap file.");
                continue;
            }

            progress.channelFinished = chan;
            progress.apFound = 0;
            progress.deviceFound = 0;
            for (Station station : mStationCache.values()) {
                if (station.freq != chan) {
                    continue;
                }
                if (station.SSID != null) {
                    progress.apFound++;
                }
                else {
                    progress.deviceFound++;
                }
            }
            progress.trafficVolumeBytes = 0;
            for (TrafficFlow flow : mTrafficFlowCache.values()) {
                progress.trafficVolumeBytes += flow.totalBytes();
            }
            progress.totalPackets = mPacketCount;
            progress.corruptedPackets = mCorruptedPacketCount;
            progress.ignoredPackets = mIgnoredPacketCount;

            publishProgress(progress);

            result.channelTraffic.put(chan, mTrafficFlowCache.values());
            result.channelStation.put(chan, mStationCache.values());
            result.totalPackets += mPacketCount;
            result.corruptedPackets += mCorruptedPacketCount;
            result.ignoredPackets += mIgnoredPacketCount;
        }
        try {
            Utils.ifaceUp(MONITOR_IFACE, false);
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to bring down iface " + MONITOR_IFACE + ".", e);
        }
        mWifiManager.reconnect();
        result.updated = System.currentTimeMillis();
        mLastResult = result;
        return result;
    }

    public Result getLastResult() {
        return mLastResult;
    }

    public static class Params {
        public int[] channels;
        public int durationSec;
        public int packetCount;

        public Params(int[] channels) {
            this.channels = channels;
            durationSec = -1;
            packetCount = -1;
        }
    }

    public static class Progress {
        public int channelFinished;
        public int apFound;
        public int deviceFound;
        public int trafficVolumeBytes;
        public int totalPackets;
        public int corruptedPackets;
        public int ignoredPackets;
    }

    public static class Result {
        public Map<Integer, Iterable<TrafficFlow>> channelTraffic;
        public Map<Integer, Iterable<Station>> channelStation;
        public int totalPackets;
        public int corruptedPackets;
        public int ignoredPackets;
        public long updated;

        public Result () {
            channelTraffic = new HashMap<Integer, Iterable<TrafficFlow>>();
            channelStation = new HashMap<Integer, Iterable<Station>>();
            totalPackets = 0;
            corruptedPackets = 0;
            ignoredPackets = 0;
            updated = 0L;
        }
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
        try {
            return Utils.dumpFieldsAsJSON(this).toString();
        }
        catch (Exception e) {
            return "<unknown";
        }
    }
}



