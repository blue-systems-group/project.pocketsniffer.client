package edu.buffalo.cse.pocketsniffer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

public class SnifTask extends AsyncTask<SnifSpec, SnifProgress, SnifResult> {

    static {
        System.loadLibrary("pcap");
    }

    private final String TAG = Utils.getTag(this.getClass());

    private final static String MONITOR_IFACE = "mon.wlan0";
    private final static int DEFAULT_SNIF_TIME_SEC = 30;

    private Context mContext;
    private File mTmpDir;
    private Runtime mRuntime;

    private List<TrafficFlow> mTrafficFlow;

    public SnifTask(Context context) {
        mContext = context;
        mTmpDir = mContext.getCacheDir();
        mRuntime = Runtime.getRuntime();
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
    }

    private void startParsing() {
        mTrafficFlow = new ArrayList<TrafficFlow>();
    }

    private void gotPacket(Packet pkt) {
        Log.d(TAG, "Packet: " + pkt.addr2 + " -> " + pkt.addr1);
    }

    private boolean ifaceUp() {
        String[] cmd = {"su", "-c", "ifconfig " +  MONITOR_IFACE + " up"};
        Object[] result = Utils.call(cmd, -1);
        int retCode = (Integer) result[0];
        String output = (String) result[1];
        if (((Integer) result[0]) == 0) {
            Log.d(TAG, "Successfully bring up monitor iface.");
            return true;
        }
        else {
            Log.e(TAG, "Failed to bring up monitor interface. " + 
                    "Return code: " + retCode +
                    "Output: " + output);
            return false;
        }
    }

    @Override
    protected SnifResult doInBackground(SnifSpec... params) {
        SnifResult result = new SnifResult();
        for (SnifSpec spec : params) {
            if (!ifaceUp()) {
                return null;
            }
            try {
                File capFile = File.createTempFile("pcap-", ".cap", mTmpDir);
                String[] cmd;

                if (spec.packetCount > 0) {
                    cmd = new String[]{"/system/bin/sh", "-c", "tcpdump", "-i", MONITOR_IFACE, "-n", "-w", capFile.getAbsolutePath(), "-s", "0", "-c", spec.packetCount+""};
                }
                else {
                    cmd = new String[]{"/system/bin/sh", "-c", "tcpdump", "-i", MONITOR_IFACE, "-n", "-w", capFile.getAbsolutePath(), "-s", "0"};
                }

                Object[] res = Utils.call(cmd, spec.durationSec);
                Integer exitValue = (Integer) res[0];
                String output = (String) res[1];
                if (exitValue != 0) {
                    Log.e(TAG, "Failed to call tcpdump (" + exitValue + "): " + output);
                    continue;
                }

                startParsing();
                if (parsePcap(capFile.getAbsolutePath())) {
                    result.channelTraffic.put(spec.channel, mTrafficFlow);
                    capFile.delete();
                }
                else {
                    Log.e(TAG, "Failed to parse cap file.");
                }
            }
            catch (IOException e) {
                Log.e(TAG, "Failed to snif channel " + spec.channel + ".", e);
            }
                

        }
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

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null) {
            return false;
        }
        if (!(object instanceof Station)) {
            return false;
        }

        Station other = (Station) object;

        if (this.mac == null) {
            if (other.mac != null) {
                return false;
            }
        }
        else {
            if (!this.mac.equals(other.mac)) {
                return false;
            }
        }

        return true;
    }
}

class TrafficFlow {
    Station from;
    Station to;
    int avgRSSI;
    int packetCount;

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null) {
            return false;
        }
        if (!(object instanceof TrafficFlow)) {
            return false;
        }

        TrafficFlow other = (TrafficFlow) object;
        
        if (this.from == null) {
            if (other.from != null) {
                return false;
            }
        }
        else {
            if (!this.from.equals(other.from)) {
                return false;
            }
        }

        if (this.to == null) {
            if (other.to != null) {
                return false;
            }
        }
        else {
            if (!this.to.equals(other.to)) {
                return false;
            }
        }

        return true;
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
}

class SnifResult {
    public Map<Integer, List<TrafficFlow>> channelTraffic;

    public SnifResult () {
        channelTraffic = new HashMap<Integer, List<TrafficFlow>>();
    }
}
