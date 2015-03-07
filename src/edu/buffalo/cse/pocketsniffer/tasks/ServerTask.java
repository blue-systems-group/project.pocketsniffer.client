package edu.buffalo.cse.pocketsniffer.tasks;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import edu.buffalo.cse.phonelab.toolkit.android.periodictask.PeriodicParameters;
import edu.buffalo.cse.phonelab.toolkit.android.periodictask.PeriodicState;
import edu.buffalo.cse.phonelab.toolkit.android.periodictask.PeriodicTask;
import edu.buffalo.cse.phonelab.toolkit.android.utils.Utils;
import edu.buffalo.cse.pocketsniffer.interfaces.DeviceInfo;
import edu.buffalo.cse.pocketsniffer.ui.DeviceFragment;
import edu.buffalo.cse.pocketsniffer.utils.LocalUtils;

public class ServerTask extends PeriodicTask<ServerTaskParameters, ServerTaskState> {

    private static final String TAG = LocalUtils.getTag(ServerTask.class);

    private ServerThread mServerThread = null;
    private ServerSocket mServerSock;

    private SnifTask mSnifTask;

    private WakeLock mWakeLock;
    private WifiManager.WifiLock mWifiLock;

    private PowerManager mPowerManager;

    private long mLastRequest = System.currentTimeMillis();

    private Map<String, DeviceInfo> mInterestedDevices;
    private BroadcastReceiver mInterestedDeviceReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Intent fired, action is " + intent.getAction());
            parseInterestedDevices(intent.getStringExtra(DeviceFragment.EXTRA_INTERESTED_DEVICES));
        }
    };

    public ServerTask(Context context) {
        super(context, ServerTask.class.getSimpleName());
        
        mInterestedDevices = new HashMap<String, DeviceInfo>();
        mContext.registerReceiver(mInterestedDeviceReceiver, new IntentFilter(DeviceFragment.ACTION_INTERESTED_DEVICE_CHANGED));

        SharedPreferences sharedPreferences = mContext.getSharedPreferences(DeviceFragment.PREFERENCES_NAME, Context.MODE_PRIVATE);
        parseInterestedDevices(sharedPreferences.getString(DeviceFragment.KEY_INTERESTED_DEVICES, "[]"));

        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, TAG);
        mWifiLock = mWifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, TAG);

        mWifiManager.startScan();
    }

    private void parseInterestedDevices(String s) {
        try {
            JSONArray array = new JSONArray(s);
            for (int i = 0; i < array.length(); i++) {
                DeviceInfo info = DeviceInfo.fromJSONObject(array.getJSONObject(i));
                mInterestedDevices.put(info.mac, info);
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to parse device info list.", e);
        }
    }

    public void startServerThread() {
        if (mServerThread == null || mServerThread.getState() == Thread.State.TERMINATED) {
            Log.d(TAG, "Creating server thread.");
            mServerThread = new ServerThread(mParameters.serverPort);
        }

        if (mServerThread.getState() == Thread.State.NEW) {
            Log.d(TAG, "Starting server thread.");
            mServerThread.start();
        }
        else {
            Log.d(TAG, "Server thread already started.");
        }
    }

    public void stopServerThread() {
        if (mServerThread != null) {
            Log.d(TAG, "Stopping server thread.");
            mServerThread.interrupt();
            closeServerSocket();
            mServerThread = null;
        }
        else {
            Log.d(TAG, "Server thread already stopped.");
        }
    }

    private boolean shouldServerRunning() {
        if (mSnifTask != null) {
            return true;
        }

        if (!Utils.hasNetworkConnection(mContext, ConnectivityManager.TYPE_WIFI)) {
            Log.d(TAG, "No wifi connection, server should not run.");
            return false;
        }

        WifiInfo info = mWifiManager.getConnectionInfo();
        if (info != null && Utils.stripQuotes(info.getSSID()).startsWith(mParameters.targetSSIDPrefix)) {
            return true;
        }
        Log.d(TAG, "Not connected to PocketSniffer wifi, server should not run.");
        return false;
    }

    @Override
    protected void check(ServerTaskParameters arg0) throws Exception {
        if (!mWifiManager.isWifiEnabled()) {
            Log.d(TAG, "Enabling Wifi.");
            mWifiManager.setWifiEnabled(true);
            mWifiManager.reassociate();
            Utils.safeSleep(1);
        }

        // only run server thread when connected to PocketSniffer wifi
        if (shouldServerRunning()) {
            startServerThread();
        }
        else {
            stopServerThread();
            if ((System.currentTimeMillis() - mLastRequest) > (120 * 1000)) {
                mPowerManager.reboot(null);
            }
        }
    }

    @Override
    public boolean parametersUpdated(String manifestString) {
        super.parametersUpdated(manifestString);

        if (mServerThread != null && mParameters.serverPort != mServerThread.port) {
            Log.d(TAG, "Server port changed from " + mServerThread.port + " to " + mParameters.serverPort + ".");
            Log.d(TAG, "Restarting server thread...");
            stopServerThread();
            startServerThread();
        }

        return true;
    };


    @Override
    public ServerTaskParameters newParameters() {
        return new ServerTaskParameters();
    }

    @Override
    public ServerTaskParameters newParameters(ServerTaskParameters arg0) {
        return new ServerTaskParameters(arg0);
    }

    @Override
    public ServerTaskState newState() {
        return new ServerTaskState();
    }

    @Override
    public Class<ServerTaskParameters> parameterClass() {
        return ServerTaskParameters.class;
    }

    @Override
    public synchronized void stop() {
        super.stop();

        stopServerThread();
    }

    @Override
    public synchronized void start() {
        super.start();

        startServerThread();
    }

    private class HandlerThread extends Thread {

        private Socket connection;

        public HandlerThread(Socket connection) {
            this.connection = connection;
        }


        private JSONObject getScanResult() {
            JSONObject scanResult = new JSONObject();

            try {
                scanResult.put("MAC", Utils.getMacAddress("wlan0"));
                scanResult.put("timestamp", Utils.getDateTimeString());
                if (new File("/system/bin/iw").exists()) {
                    scanResult.put("detailed", true);
                    scanResult.put("iwScanOutput", Utils.call("iw wlan0 scan", -1 /* no timeout */, true /* require su */)[1]);
                }
                else {
                    scanResult.put("detailed", false);
                    scanResult.put("resultList", Utils.getScanResults(mContext));
                }
            }
            catch (Exception e) {
                // ignore
            }
            return scanResult;
        }

        private void parsePingOutput(String output, JSONObject entry) throws JSONException {
            for (String line : output.split("\n")) {
                line = line.trim();
                Log.d(TAG, "Parsing line: " + line);

                if (line.matches("^\\d*\\spackets transmitted.*$")) {
                    String[] parts = line.split(" ");
                    entry.put("packetTransmitted", Integer.parseInt(parts[0]));
                    entry.put("packetReceived", Integer.parseInt(parts[3]));
                }
                else if (line.startsWith("rtt")) {
                    String[] parts = line.split(" ")[3].split("/");
                    entry.put("minRTT", Double.parseDouble(parts[0]));
                    entry.put("avgRTT", Double.parseDouble(parts[1]));
                    entry.put("maxRTT", Double.parseDouble(parts[2]));
                    entry.put("stdDev", Double.parseDouble(parts[3]));
                }
            }
        }
        private void parseIperfOutput(String output, JSONObject entry) throws JSONException {
            JSONArray array = new JSONArray();
            Pattern pattern = Pattern.compile("([\\d\\.]+)\\sMbits/sec");
            double bw = 0;
            for (String line : output.split("\n")) {
                Log.d(TAG, "Parsing " + line);
                Matcher matcher = pattern.matcher(line);
                if (!matcher.find()) {
                    continue;
                }
                bw = Double.parseDouble(matcher.group(1));
                array.put(bw);
            }
            entry.put("bandwidths", array);
            entry.put("overallBandwidth", bw);
        }

        private void collectTraffic(JSONObject request, JSONObject reply) throws JSONException, Exception {
            JSONArray array = new JSONArray();
            reply.put("clientTraffic", array);

            if (!Utils.isPhoneLabDevice(mContext)) {
                Log.w(TAG, "Not PhoneLab devices, ignoring traffic request.");
                return;
            }

            JSONArray targetDevices = request.optJSONArray("clients");
            if (targetDevices == null) {
                Log.w(TAG, "No target devices specified for traffic.");
                return;
            }

            String forDevice = null;
            for (int i = 0; i < targetDevices.length(); i++) {
                String mac = targetDevices.getString(i);
                if (!mInterestedDevices.containsKey(mac)) {
                    Log.d(TAG, "Device " + mac + " is not configured.");
                    continue;
                }
                DeviceInfo info = mInterestedDevices.get(mac);
                if (!info.interested) {
                    Log.d(TAG, "Device " + mac + " is not interested.");
                    continue;
                }
                forDevice = mac;
                break;
            }

            if (forDevice == null) {
                Log.d(TAG, "All target devices are not interested.");
                return;
            }
            Log.d(TAG, "Collecting traffic condition.");

            SnifTask.Params params = new SnifTask.Params();
            if (request.has("trafficChannel")) {
                JSONArray channels = request.getJSONArray("trafficChannel");
                for (int i = 0; i < channels.length(); i++) {
                    params.channels.add(channels.getInt(i));
                }
            }
            else {
                params.channels.add(1);
                params.channels.add(6);
                params.channels.add(11);
            }
            params.durationSec = request.optInt("channelDwellTime", 10);
            params.packetCount = -1;
            params.forever = false;

            mSnifTask = new SnifTask(mContext, null);
            mSnifTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);

            JSONObject result = mSnifTask.get().toJSONObject();
            result.put("forDevice", forDevice);

            array.put(result);
            reply.put("clientTraffic", array);
            mSnifTask = null;
        }


        private void collectLatency(JSONObject request, JSONObject reply) throws JSONException, Exception {
            if (!request.has("pingArgs")) {
                Log.d(TAG, "No arguments for ping.");
                return;
            }
            JSONArray array = new JSONArray();
            JSONObject entry = new JSONObject();

            String pingArgs = request.getString("pingArgs");

            entry.put("timestamp", Utils.getDateTimeString());
            entry.put("MAC", Utils.getMacAddress("wlan0"));
            entry.put("pingArgs", pingArgs);

            String cmd = "ping " + pingArgs;
            String output = (String) Utils.call(cmd, -1 /* no timeout*/, true /* require su */)[1];
            parsePingOutput(output, entry);

            array.put(entry);
            reply.put("clientLatency", array);
        }

        private void collectThroughput(JSONObject request, JSONObject reply) throws JSONException, Exception {
            if (!request.has("iperfArgs")) {
                Log.d(TAG, "No argument for iperf.");
                return;
            }
            String iperfArgs = request.getString("iperfArgs");

            JSONArray array = new JSONArray();
            JSONObject entry = new JSONObject();

            entry.put("timestamp", Utils.getDateTimeString());
            entry.put("MAC", Utils.getMacAddress("wlan0"));
            entry.put("iperfArgs", iperfArgs);

            String cmd = "iperf " + iperfArgs;
            String output = (String) Utils.call(cmd, -1 /* no timeout*/, true /* require su */)[1];
            parseIperfOutput(output, entry);

            array.put(entry);
            reply.put("clientThroughput", array);
        }

        private void collectNearbyDevices(JSONObject request, JSONObject reply) throws JSONException, Exception {
            JSONArray array = new JSONArray();
            JSONObject entry = new JSONObject();

            entry.put("MAC", Utils.getMacAddress("wlan0"));
            entry.put("timestamp", Utils.getDateTimeString());

            JSONArray neighbors = new JSONArray();

            for (DeviceInfo info : mInterestedDevices.values()) {
                JSONObject neighbor = new JSONObject();
                neighbor.put("MAC", info.mac);
                neighbor.put("signalStrength", info.rssi);
                neighbor.put("lastSeen", Utils.getDateTimeString(info.lastSeen));
                neighbor.put("interested", info.interested);
                neighbors.put(neighbor);
            }
            entry.put("neighbors", neighbors);

            array.put(entry);
            reply.put("nearbyDevices", array);
        }

        private void handleCollect(JSONObject request, JSONObject reply) throws JSONException, Exception {

            if (request.optBoolean("phonelabDevice", false)) {
                JSONArray array = new JSONArray();
                if (Utils.isPhoneLabDevice(mContext)) {
                    array.put(Utils.getMacAddress("wlan0"));
                }
                reply.put("phonelabDevice", array);
            }

            if (request.optBoolean("clientScan", false)) {
                Log.d(TAG, "Collecting scan result.");
                JSONArray array = new JSONArray();
                array.put(getScanResult());
                reply.put("clientScan", array);
            }

            if (request.optBoolean("clientTraffic", false)) {
                collectTraffic(request, reply);
            }

            if (request.optBoolean("clientLatency", false)) {
                collectLatency(request, reply);
            }

            if (request.optBoolean("clientThroughput", false)) {
                collectThroughput(request, reply);
            }

            if (request.optBoolean("nearbyDevices", false)) {
                collectNearbyDevices(request, reply);
            }
        }

        private void handleReassoc(JSONObject request, JSONObject reply) throws JSONException {
            String bssid = request.getString("newBSSID").toLowerCase();

            String SSID = null;
            for (ScanResult result : mWifiManager.getScanResults()) {
                if (result.BSSID.toLowerCase().equals(bssid)) {
                    SSID = result.SSID;
                    break;
                }
            }

            if (SSID == null) {
                Log.w(TAG, "AP " + bssid + " not found.");
                return;
            }

            WifiConfiguration config = null;
            for (WifiConfiguration c : mWifiManager.getConfiguredNetworks()) {
                if (Utils.stripQuotes(c.SSID).equals(SSID)) {
                    config = c;
                    break;
                }
            }
            if (config == null) {
                Log.d(TAG, "No AP config for " + bssid + " found.");
            }
            else {
                Log.d(TAG, "Setting PocketSniffer AP config BSSID to " + bssid);
                config.BSSID = bssid.toUpperCase();
                mWifiManager.updateNetwork(config);
                mWifiManager.saveConfiguration();
                mWifiManager.enableNetwork(config.networkId, true);
                mWifiManager.disconnect();
                mWifiManager.reconnect();
            }
        }

        private void handleReboot(JSONObject request, JSONObject reply) throws JSONException {
            Log.d(TAG, "Rebooting!!");
            try {
                mPowerManager.reboot(null);
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to reboot.", e);
            }
        }

        private JSONObject handle(JSONObject request) {
            if (!request.has("action")) {
                Log.e(TAG, "Request does not have an action.");
                return null;
            }

            JSONObject reply = new JSONObject();

            try {
                reply.put("request", request);

                String action = request.getString("action");
                if ("collect".equals(action)) {
                    handleCollect(request, reply);
                }
                else if ("clientReassoc".equals(action)) {
                    handleReassoc(request, reply);
                    reply = null;
                }
                else if ("clientReboot".equals(action)) {
                    handleReboot(request, reply);
                    reply = null;
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to handle request.", e);
                reply = null;
            }

            return reply;
        }

        public void run() {
            mWakeLock.acquire();
            mWifiLock.acquire();

            InetAddress remoteAddr = connection.getInetAddress();
            Log.d(TAG, "Get connection from " + remoteAddr);

            mLastRequest = System.currentTimeMillis();

            JSONObject request = null;
            JSONObject reply = null;

            try {
                String message = Utils.readFull(connection.getInputStream());
                try {
                    message = Utils.decompress(message.getBytes(Charset.forName("UTF-8")), 0, message.length());
                }
                catch (IOException e) {
                }

                Log.d(TAG, "Got message: " + message);

                request = new JSONObject(message);
                reply = handle(request);
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to handle message.", e);
                return;
            }

            if (reply == null) {
                try {
                    connection.close();
                }
                catch (Exception e) {
                }
                return;
            }

            if (request.optString("action").equals("collect") && request.optBoolean("clientTraffic", false)) {
                for (int i = 0; i < 10; i++) {
                    try {
                        connection.close();
                        Log.d(TAG, "Opening new socket.");
                        connection = new Socket();
                        connection.connect(new InetSocketAddress(remoteAddr, mParameters.serverPort), 0);
                        break;
                    }
                    catch (Exception e) {
                        Log.e(TAG, "Failed to connect to router. Retrying", e);

                        Utils.safeSleep(3);
                        continue;
                    }
                }
            }

            try {
                Log.d(TAG, "Sending reply: " + reply.toString());
                OutputStream os = new BufferedOutputStream(connection.getOutputStream());
                os.write(Utils.compress(reply.toString()));
                os.flush();
                os.close();
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to send msg.", e);
            }
            finally {
                try {
                    connection.close();
                }
                catch (Exception e) {
                    // ignore
                }
            }

        }

    }

    private class ServerThread extends Thread {

        private int port;

        public long lastAccept;

        public ServerThread(int port) {
            this.port = port;
        }

        public void run() {
            while (shouldServerRunning()) {
                if (mWakeLock.isHeld()) {
                    mWakeLock.release();
                }
                if (mWifiLock.isHeld()) {
                    mWifiLock.release();
                }

                // open a new server socket each time, since the current one
                // may be broken when go to monitor mode
                try {
                    mServerSock = new ServerSocket(port);
                    mServerSock.setReuseAddress(true);
                    mServerSock.setSoTimeout(mParameters.acceptTimeoutSec*1000);
                }
                catch (Exception e) {
                    Log.e(TAG, "Failed to create server socket.", e);
                    break;
                }
                Log.d(TAG, "Successfully created server socket on port " + port);

                Socket connection = null;
                while (true) {
                    try {
                        lastAccept = System.currentTimeMillis();
                        connection = mServerSock.accept();
                        connection.setKeepAlive(true);
                        break;
                    }
                    catch (InterruptedIOException e) {
                        continue;
                    }
                    catch (Exception e) {
                        Log.e(TAG, "Failed to accept.", e);
                        break;
                    }
                }
                closeServerSocket();

                if (connection == null) {
                    continue;
                }

                (new HandlerThread(connection)).start();
            }

            closeServerSocket();
        }

    }

    private void closeServerSocket() {
        if (mServerSock != null) {
            Log.d(TAG, "Closing server socket.");
            try {
                mServerSock.close();
                mServerSock = null;
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to close server socket.", e);
            }
        }
    }


    @Override
    public String getTag() {
        return TAG;
    }

}



@Root(name = "ServerTask")
class ServerTaskParameters extends PeriodicParameters {

    @Element
    public Integer serverPort;

    @Element
    public String targetSSIDPrefix;

    @Element
    public Integer connectionTimeoutSec;

    @Element
    public Integer acceptTimeoutSec;

    public ServerTaskParameters() {
        checkIntervalSec = 60L;
        serverPort = 7654;
        targetSSIDPrefix = "PocketSniffer";
        connectionTimeoutSec = 30;
        acceptTimeoutSec = 10;
    }

    public ServerTaskParameters(ServerTaskParameters params) {
        this.checkIntervalSec = params.checkIntervalSec;
        this.serverPort = params.serverPort;
        this.targetSSIDPrefix = params.targetSSIDPrefix;
        this.connectionTimeoutSec = params.connectionTimeoutSec;
        this.acceptTimeoutSec = params.acceptTimeoutSec;
    }

}

@Root(name = "ServerTask")
class ServerTaskState extends PeriodicState {
}
