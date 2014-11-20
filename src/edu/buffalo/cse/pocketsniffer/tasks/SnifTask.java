package edu.buffalo.cse.pocketsniffer.tasks;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import edu.buffalo.cse.phonelab.toolkit.android.utils.Utils;
import edu.buffalo.cse.pocketsniffer.interfaces.AsyncTaskListener;
import edu.buffalo.cse.pocketsniffer.interfaces.Packet;
import edu.buffalo.cse.pocketsniffer.interfaces.Station;
import edu.buffalo.cse.pocketsniffer.interfaces.Task;
import edu.buffalo.cse.pocketsniffer.interfaces.TrafficFlow;
import edu.buffalo.cse.pocketsniffer.utils.LocalUtils;

public class SnifTask extends Task<SnifTask.Params, SnifTask.Progress, SnifTask.Result> {

    static {
        System.loadLibrary("pcap");
    }

    private static final String TAG = LocalUtils.getTag(SnifTask.class);
    private static final String ACTION = SnifTask.class.getName() + ".SnifDone";

    private final static String WLAN_IFACE = "wlan0";
    private final static String MONITOR_IFACE = "mon.wlan0";
    private final static String WL_DEV_NAME = "phy0";
    private final static int DEFAULT_SNIF_TIME_SEC = 30;

    private final static int FRAME_TYPE_DATA = 2;
    private final static int FRAME_SUBTYPE_DATA = 0;
    private final static int FRAME_SUBTYPE_QOS_DATA = 8;
    private final static String BROADCAST_MAC = "FF:FF:FF:FF:FF:FF";

    private File mDataDir;
    private WifiManager mWifiManager;

    private Map<String, TrafficFlow> mTrafficFlowCache;
    private Map<String, Station> mStationCache;
    private Integer mPacketCount;
    private Integer mCorruptedPacketCount;
    private Integer mIgnoredPacketCount;

    private Result mLastResult;

    private WakeLock mWakeLock;
    private WifiManager.WifiLock mWifiLock;

    private native boolean parsePcap(String capFile);

    public SnifTask(Context context, AsyncTaskListener<SnifTask.Params, SnifTask.Progress, SnifTask.Result> listener) {
        super(context, listener);
        mDataDir = mContext.getDir("pcap", Context.MODE_PRIVATE);
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mTrafficFlowCache = new HashMap<String, TrafficFlow>();
        mStationCache = new HashMap<String, Station>();
        
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, TAG);
        mWifiLock = mWifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, TAG);
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
                from.isAP = true;
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

        if (from.isAP && from.SSID != null && !to.isAP) {
            to.SSID = from.SSID;
        }
        if (to.isAP && to.SSID != null && !from.isAP) {
            from.SSID = to.SSID;
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

        mWakeLock.acquire();
        mWifiLock.acquire();

        do {
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
                if (param.durationSec < 0 && exitValue != 0) {
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

                // delete cap file if we're running forever
                if (param.forever) {
                    capFile.delete();
                }

                result.channelTraffic.put(chan, mTrafficFlowCache.values());
                result.channelStation.put(chan, mStationCache.values());
                result.totalPackets += mPacketCount;
                result.corruptedPackets += mCorruptedPacketCount;
                result.ignoredPackets += mIgnoredPacketCount;

                progress.channelFinished = chan;
                progress.totalPackets = mPacketCount;
                progress.corruptedPackets = mCorruptedPacketCount;
                progress.ignoredPackets = mIgnoredPacketCount;
                progress.partialResult = result;

                publishProgress(progress);
            }
        } while (param.forever);

        mWakeLock.release();
        mWifiLock.release();

        try {
            Utils.ifaceUp(MONITOR_IFACE, false);
            // bring down wlan0, wpa_supplicant will bring it up, and set it up
            // properly.
            Utils.ifaceUp(WLAN_IFACE, false);
        }
        catch (Exception e) {
            // ignore
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
        public List<Integer> channels;
        // channel dwell time
        public int durationSec;
        // max number of packets for each channel.
        public int packetCount;

        // sniffing forever, for battery benchmarking
        public boolean forever;

        public Params() {
            this.channels = new ArrayList<Integer>();
            durationSec = 60;
            packetCount = 1000;
            forever = false;
        }


        public Params(List<Integer> channels, int durationSec, int packetCount) {
            this.channels = channels;
            this.durationSec = durationSec;
            this.packetCount = packetCount;
            this.forever = false;
        }

    }

    public static class Progress {
        public int channelFinished;
        public int totalPackets;
        public int corruptedPackets;
        public int ignoredPackets;
        public Result partialResult;

        public JSONObject toJSONObject() {
            JSONObject json = new JSONObject();
            try {
                json.put("channelFinished", channelFinished);
                json.put("totalPackets", totalPackets);
                json.put("corruptedPackets", corruptedPackets);
                json.put("ignoredPackets", ignoredPackets);
            }
            catch (Exception e) {
            }

            return json;
        }
    }

    public static class Result {
        public Map<Integer, Collection<TrafficFlow>> channelTraffic;
        public Map<Integer, Collection<Station>> channelStation;
        public int totalPackets;
        public int corruptedPackets;
        public int ignoredPackets;
        public long updated;

        public Result () {
            channelTraffic = new HashMap<Integer, Collection<TrafficFlow>>();
            channelStation = new HashMap<Integer, Collection<Station>>();
            totalPackets = 0;
            corruptedPackets = 0;
            ignoredPackets = 0;
            updated = 0L;
        }

        public JSONObject toJSONObject() {
            JSONObject json = new JSONObject();

            try {
                JSONObject channelTrafficJSON = new JSONObject();
                for (Entry<Integer, Collection<TrafficFlow>> entry : channelTraffic.entrySet()) {
                    JSONArray array = new JSONArray();
                    for(TrafficFlow flow : entry.getValue()) {
                        array.put(flow.toJSONObject());
                    }
                    channelTrafficJSON.put(entry.getKey().toString(), array);
                }
                json.put("channelTraffic", channelTrafficJSON);

                JSONObject channelStationJSON = new JSONObject();
                for (Entry<Integer, Collection<Station>> entry : channelStation.entrySet()) {
                    JSONArray array = new JSONArray();
                    for (Station station : entry.getValue()) {
                        array.put(station.toJSONObject());
                    }
                    channelStationJSON.put(entry.getKey().toString(), array);
                }
                json.put("channelStation", channelStationJSON);
                
                json.put("totalPackets", totalPackets);
                json.put("corruptedPackets", corruptedPackets);
                json.put("ignoredPackets", ignoredPackets);
            }
            catch (Exception e) {
                // ignore
            }

            return json;
        }
    }
}
