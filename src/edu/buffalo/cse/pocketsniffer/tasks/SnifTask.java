package edu.buffalo.cse.pocketsniffer.tasks;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import edu.buffalo.cse.phonelab.toolkit.android.utils.Utils;
import edu.buffalo.cse.pocketsniffer.interfaces.AsyncTaskListener;
import edu.buffalo.cse.pocketsniffer.interfaces.Packet;
import edu.buffalo.cse.pocketsniffer.interfaces.Task;
import edu.buffalo.cse.pocketsniffer.interfaces.TrafficEntry;
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
    private final static String BROADCAST_MAC = "ff:ff:ff:ff:ff:ff";

    private File mDataDir;
    private WifiManager mWifiManager;

    private Map<String, TrafficEntry> mTrafficEntryCache;

    private WakeLock mWakeLock;
    private WifiManager.WifiLock mWifiLock;

    private native boolean parsePcap(String capFile);

    public SnifTask(Context context, AsyncTaskListener<SnifTask.Params, SnifTask.Progress, SnifTask.Result> listener) {
        super(context, listener);
        mDataDir = mContext.getDir("pcap", Context.MODE_PRIVATE);
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mTrafficEntryCache = new HashMap<String, TrafficEntry>();
        
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, TAG);
        mWifiLock = mWifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, TAG);
    }

    /**
     * Before parsing any packets, clear storage, and update scan results.
     */
    private void startParsing() {
        mTrafficEntryCache.clear();
    }

    /**
     * This function get called on each packet.
     */
    private void gotPacket(Packet pkt) {
        if (!pkt.crcOK) {
            return;
        }

        String from = pkt.addr2;
        String to = pkt.addr1;

        // only count data packets for non-broadcast data traffic volumns
        if (pkt.type != FRAME_TYPE_DATA || !(pkt.subtype == FRAME_SUBTYPE_DATA || pkt.subtype == FRAME_SUBTYPE_QOS_DATA)
                || from == null || to == null || to.equals(BROADCAST_MAC)) {
            return;
        }

        from = from.toLowerCase();
        to = to.toLowerCase();

        TrafficEntry entry = null;

        String key = TrafficEntry.getKey(from, to);
        if (!mTrafficEntryCache.containsKey(key)) {
            entry = new TrafficEntry(from, to);
            entry.begin = Utils.getDateTimeString(pkt.tv_sec, pkt.tv_usec);
            mTrafficEntryCache.put(key, entry);
        }
        else {
            entry = mTrafficEntryCache.get(key);
        }

        if (from == entry.from) {
            entry.txBytes += pkt.len;
            entry.putTxRSSI(pkt.rssi);
        }
        else {
            entry.rxBytes += pkt.len;
            entry.putRxRSSI(pkt.rssi);
        }

        entry.channel = Utils.freqToChannel(pkt.freq);
        entry.end = Utils.getDateTimeString(pkt.tv_sec, pkt.tv_usec);
    }

    @Override
    protected SnifTask.Result doInBackground(SnifTask.Params... params) {
        SnifTask.Result result = new SnifTask.Result();
        SnifTask.Progress progress = new SnifTask.Progress();

        List<String> cmdBase = new ArrayList<String>();
        cmdBase.addAll(Arrays.asList(new String[]{"tcpdump", "-i", MONITOR_IFACE, "-n", "-s", "0"}));

        SnifTask.Params param = params[0];

        mWifiManager.disconnect();

        if (param.forever) {
            mWakeLock.acquire();
        }
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

                for (TrafficEntry entry : mTrafficEntryCache.values()) {
                    result.traffics.add(entry);
                }

                progress.partialResult = result;
                publishProgress(progress);
            }
        } while (param.forever);

        if (param.forever) {
            mWakeLock.release();
        }
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
        return result;
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
        public Result partialResult;

        public JSONObject toJSONObject() {
            return partialResult.toJSONObject();
        }
    }

    public static class Result {
        public String timestamp;
        public List<TrafficEntry> traffics;

        public Result () {
            timestamp = Utils.getDateTimeString();
            traffics = new ArrayList<TrafficEntry>();
        }

        public JSONObject toJSONObject() {
            JSONObject json = new JSONObject();

            try {
                json.put("MAC", Utils.getMacAddress(WLAN_IFACE));
                json.put("timestamp", timestamp);
                JSONArray array = new JSONArray();
                for (TrafficEntry entry: traffics) {
                    array.put(entry.toJSONObject());
                }
                json.put("traffics", array);
            }
            catch (Exception e) {
                // ignore
            }

            return json;
        }
    }
}
