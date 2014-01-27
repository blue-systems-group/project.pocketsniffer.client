package edu.buffalo.cse.pocketadmin;

import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.DhcpInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

public class AdminService extends Service {
    private final String TAG = "PocketAdmin-" + this.getClass().getSimpleName();

    // wifi configuration
    private final String SSID = "PocketAdmin";
    private final String PASSWORD = "AUDeORc1haIwvHuM6oeJOYhMAFYTffeC";
    private final int PRIORITY = 99999999;

    private final int PORT = 1688;
    private final int INTERVAL_MS = 60 * 1000;

    private boolean started = false;

    private String checkIntentName = this.getClass().getName() + ".Check";
    private IntentFilter checkIntentFilter = new IntentFilter(checkIntentName);
    private PendingIntent checkPendingIntent;
    private BroadcastReceiver checkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "Start checking...");

            WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

            if (!wifiManager.isWifiEnabled()) {
                Log.d(TAG, "Wifi not enabled");
                return;
            }


            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            String currentSSID = removeQuotes(wifiInfo.getSSID());

            if (!currentSSID.equals(SSID)) {
                Log.d(TAG, "Currently connected to " + wifiInfo.getSSID());

                boolean found = false;

                List<ScanResult> scanResults = wifiManager.getScanResults();
                for (ScanResult result : scanResults) {
                    if (result.SSID.equals(SSID)) {
                        found = true;
                        break;
                    }
                }

                if (found) {
                    Log.d(TAG, "Found PocketAdmin Wifi, connecting...");

                    int networkId = addConfiguration();
                    wifiManager.disconnect();
                    wifiManager.enableNetwork(networkId, true);
                    wifiManager.reconnect();
                }
                else {
                    Log.v(TAG, "No PocketAdmin Wifi found.");
                }

                /* In both cases, we need to wait for next check interval
                 *  1) If we just initiated reconnection, the device need some
                 *  time to connect
                 *  2) Or we need to wait for PocketAdmin Wifi
                 */

                return;
            }

            /* At this point, we're connected to PocketAdmin Wifi */

            DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
            InetAddress addr = int2Addr(dhcpInfo.gateway);

            Log.d(TAG, "Connected to PocketAdmin Wifi, sending msg to " + addr.toString());

            try {
                Socket socket = new Socket(addr, PORT);
                PrintWriter printWriter = new PrintWriter(socket.getOutputStream());
                printWriter.write(prepareMessage());
                printWriter.flush();
                printWriter.close();
            }
            catch (Exception e) {
                Log.e(TAG, "Faild to send msg to router: " + e.getMessage());
            }
        }
    };


    /* Package Wifi scan results and device's MAC address in to JSON format */
    private String prepareMessage() {
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();

        JSONObject msg = new JSONObject();

        try {
            msg.put("MAC", wifiInfo.getMacAddress());

            JSONArray results = new JSONArray();
            List<ScanResult> scanResults = wifiManager.getScanResults();
            for (ScanResult result : scanResults) {
                JSONObject json = new JSONObject();
                json.put("SSID", result.SSID);
                json.put("BSSID", result.BSSID);
                json.put("frequency", result.frequency);
                json.put("level", result.level);
                json.put("timestamp", result.timestamp);
                results.put(json);

                /* indicate the channel that device is using */
                if (result.BSSID.equals(wifiInfo.getBSSID())) {
                    msg.put("frequency", result.frequency);
                }
            }

            msg.put("results", results);
        }
        catch (JSONException e) {
            Log.e(TAG, "Faild to compose message: " + e.getMessage());
        }

        return msg.toString();
    }


    /* Convert integer to IPv4 address */
    private InetAddress int2Addr(int addr) {
        byte[] bytes = { 
            (byte)(0xff & addr),
            (byte)(0xff & (addr >> 8)),
            (byte)(0xff & (addr >> 16)),
            (byte)(0xff & (addr >> 24)) };

        try {
            return InetAddress.getByAddress(bytes);
        }
        catch (UnknownHostException e) {
            Log.e(TAG, "Faild to get gateway info: " + e.getMessage());
            return null;
        }
    }

    private String removeQuotes(String s) {
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length()-1);
        }
        return s;
    }

    private String addQuotes(String s) {
        return "\"" + s + "\"";
    }

    /* Create wifi configuration for PocketAdmin Wifi if not exists already
     * return its networkId
     */
    private int addConfiguration() {

        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        List<WifiConfiguration> configurationList = wifiManager.getConfiguredNetworks();
        for (WifiConfiguration con : configurationList) {
            if (removeQuotes(con.SSID).equals(SSID)) {
                return con.networkId;
            }
        }

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = addQuotes(SSID);
        config.preSharedKey = addQuotes(PASSWORD);
        config.priority = PRIORITY;

        return wifiManager.addNetwork(config);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (started) {
            Log.v(TAG, "Not restarting started service");
            return START_STICKY;
        }

        Log.v(TAG, "======== Starting PocketAdmin Service ======== ");

        super.onStartCommand(intent, flags, startId);

        registerReceiver(checkReceiver, checkIntentFilter);

        startPeriodic();

        started = true;
        return START_STICKY;
    }

    private void startPeriodic() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + INTERVAL_MS, INTERVAL_MS, checkPendingIntent);
    }

    private void stopAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(checkPendingIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "======== Destroying PocketAdmin Service ========");

        unregisterReceiver(checkReceiver);
        stopAlarm();

        /* remove the PocketAdmin network configration */
        /* TODO when uninstalled, onDestroy is not called, need to figure out
         * how to delete this configuration in that case */
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        List<WifiConfiguration> configurationList = wifiManager.getConfiguredNetworks();
        for (WifiConfiguration con : configurationList) {
            if (removeQuotes(con.SSID).equals(SSID)) {
                wifiManager.removeNetwork(con.networkId);
                wifiManager.disconnect();
                wifiManager.reconnect();
                break;
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.v(TAG, "======== Creating PocketAdmin Service ========");

        checkPendingIntent = PendingIntent.getBroadcast(this, 0, 
                new Intent(checkIntentName), PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }
}
