package edu.buffalo.cse.pocketadmin;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;


public class AdminService extends Service {
    private final String TAG = "PocketAdmin-" + this.getClass().getSimpleName();

    private final String SSID = "\"PocketAdmin\"";
    private final String PASSWORD = "\"AUDeORc1haIwvHuM6oeJOYhMAFYTffeC\"";
    private final int PRIORITY = 99999999;

    private String checkIntentName = this.getClass().getName() + ".Check";
    private IntentFilter checkIntentFilter = new IntentFilter(checkIntentName);
    private PendingIntent checkPendingIntent;
    private BroadcastReceiver checkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "Checking");

            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo wifi = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

            if (!wifi.isConnected()) {
                Log.v(TAG, "Wifi not connected");
            }

            WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();

            Log.v(TAG, 
                    "dns1: " + int2Addr(dhcpInfo.dns1).toString() + "," + 
                    "dns2: " + int2Addr(dhcpInfo.dns2).toString() + "," + 
                    "Gateway: " + int2Addr(dhcpInfo.gateway).toString() +  "," + 
                    "ipAddress: " + int2Addr(dhcpInfo.ipAddress).toString() + "," + 
                    "netmask " + int2Addr(dhcpInfo.netmask).toString() + "," + 
                    "Server: " + int2Addr(dhcpInfo.serverAddress).toString());

            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo.getSSID().equals(SSID)) {
                Log.v(TAG, "Already connected to PocketAdmin Wifi");
            }
            else {
                Log.v(TAG, "Currently connected to " + wifiInfo.getSSID());

                boolean found = false;

                List<ScanResult> scanResults = wifiManager.getScanResults();
                for (ScanResult result : scanResults) {
                    if (result.SSID.equals(SSID)) {
                        found = true;
                        break;
                    }
                }

                if (found) {
                    Log.v(TAG, "Found PocketAdmin Wifi, connecting...");

                    int networkId = addConfiguration();
                    wifiManager.disconnect();
                    wifiManager.enableNetwork(networkId, true);
                    wifiManager.reconnect();
                }
            }
        }
    };

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

    private int addConfiguration() {
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        List<WifiConfiguration> configurationList = wifiManager.getConfiguredNetworks();
        for (WifiConfiguration config : configurationList) {
            Log.v(TAG, config.SSID + ": " + config.priority);
            if (config.SSID.equals(SSID)) {
                return config.networkId;
            }
        }

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = SSID;
        config.preSharedKey = PASSWORD;
        config.priority = PRIORITY;

        int networkId = wifiManager.addNetwork(config);
        wifiManager.saveConfiguration();

        return networkId;
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        Log.v(TAG, "Service started");

        registerReceiver(checkReceiver, checkIntentFilter);


        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
    }

    @Override
    public void onCreate() {
        // TODO Auto-generated method stub
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

}
