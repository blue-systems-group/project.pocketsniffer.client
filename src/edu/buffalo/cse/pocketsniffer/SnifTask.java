package edu.buffalo.cse.pocketsniffer;

import java.io.File;
import java.io.IOException;
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

    public SnifTask(Context context) {
        mContext = context;
        mTmpDir = mContext.getCacheDir();
        mRuntime = Runtime.getRuntime();
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
    }

    public void gotPacket(Packet pkt) {
    }

    @Override
    protected SnifResult doInBackground(SnifSpec... params) {
        SnifResult result = new SnifResult();
        for (SnifSpec spec : params) {
            try {
                if (mRuntime.exec("su -c ifconfig " + MONITOR_IFACE + " up").waitFor() != 0) {
                    Log.e(TAG, "Failed to bring up monitor interface.");
                    return null;
                }

                File capFile = File.createTempFile("pcap", "cap", mTmpDir);
                String cmd = "tcpdump -i " + MONITOR_IFACE + " -n -w " + capFile.getAbsolutePath();
                if (spec.packetCount > 0) {
                    cmd += " -c " + spec.packetCount;
                }
                Process proc = mRuntime.exec(cmd);
                if (spec.packetCount > 0) {
                    proc.waitFor();
                }
                else {
                    if (spec.durationSec < 0) {
                        spec.durationSec = DEFAULT_SNIF_TIME_SEC;
                    }
                    Thread.sleep(spec.durationSec * 1000);
                    proc.destroy();
                }
                List<TrafficFlow> flows = parsePcap(capFile.getAbsolutePath());
                result.channelTraffic.put(spec.channel, flows);

                capFile.delete();
            }
            catch (InterruptedException e) {
                Log.e(TAG, "Command interrupted.", e);
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

    private native List<TrafficFlow> parsePcap(String capFile);
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
