package edu.buffalo.cse.pocketsniffer.interfaces;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import edu.buffalo.cse.pocketsniffer.utils.LocalUtils;

public class DeviceInfo {

    private static final String TAG = LocalUtils.getTag(DeviceInfo.class);

    public String mac;
    public String name;
    public String manufacturer;
    public int rssi;
    public long lastSeen;
    public boolean interested;

    public DeviceInfo() {
        interested = false;
    }

    public JSONObject toJSONObject() {
        JSONObject json = new JSONObject();
        try {
            json.put("MAC", mac);
            json.put("name", name);
            json.put("rssi", rssi);
            json.put("lastSeen", lastSeen);
            json.put("interested", interested);
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to encode as JSONObject.", e);
        }
        return json;
    }

    public static DeviceInfo fromJSONObject(JSONObject json) throws JSONException {
        DeviceInfo info = new DeviceInfo();
        info.mac = json.getString("MAC");
        info.name = json.getString("name");
        info.rssi = json.getInt("rssi");
        info.lastSeen = json.getLong("lastSeen");
        info.interested = json.getBoolean("interested");
        return info;
    }
}
