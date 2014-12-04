package edu.buffalo.cse.pocketsniffer.tasks;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.os.AsyncTask;
import android.util.Log;

import edu.buffalo.cse.phonelab.toolkit.android.periodictask.PeriodicParameters;
import edu.buffalo.cse.phonelab.toolkit.android.periodictask.PeriodicState;
import edu.buffalo.cse.phonelab.toolkit.android.periodictask.PeriodicTask;
import edu.buffalo.cse.phonelab.toolkit.android.utils.Utils;
import edu.buffalo.cse.pocketsniffer.utils.LocalUtils;

public class ServerTask extends PeriodicTask<ServerTaskParameters, ServerTaskState> {

    private static final String TAG = LocalUtils.getTag(ServerTask.class);

    private ServerThread mServerThread = null;
    private ServerSocket mServerSock;

    public ServerTask(Context context) {
        super(context, ServerTask.class.getSimpleName());

        addAction(ConnectivityManager.CONNECTIVITY_ACTION);
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
        if (!Utils.hasNetworkConnection(mContext, ConnectivityManager.TYPE_WIFI)) {
            Log.d(TAG, "No wifi connection, server should not run.");
            return false;
        }

        WifiInfo info = mWifiManager.getConnectionInfo();
        if (info != null && Utils.stripQuotes(info.getSSID()).equals(mParameters.targetSSID)) {
            return true;
        }
        Log.d(TAG, "Not connected to PocketSniffer wifi, server should not run.");
        return false;
    }

    @Override
    protected void check(ServerTaskParameters arg0) throws Exception {
        // only run server thread when connected to PocketSniffer wifi
        if (shouldServerRunning()) {
            startServerThread();
        }
        else {
            stopServerThread();
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

    private class ServerThread extends Thread {

        private int port;

        public ServerThread(int port) {
            this.port = port;
        }

        public void run() {
            while (shouldServerRunning()) {
                // open a new server socket each time, since the current one
                // may be broken when go to monitor mode
                try {
                    mServerSock = new ServerSocket(port);
                    mServerSock.setReuseAddress(true);
                    mServerSock.setSoTimeout(0);
                }
                catch (Exception e) {
                    Log.e(TAG, "Failed to create server socket.", e);
                    break;
                }
                Log.d(TAG, "Successfully created server socket on port " + port);

                Socket connection = null;
                try {
                    connection = mServerSock.accept();
                }
                catch (Exception e) {
                    Log.e(TAG, "Failed to receive receivedPacket.", e);
                    break;
                }
                closeServerSocket();

                InetAddress remoteAddr = connection.getInetAddress();
                Log.d(TAG, "Get connection from " + remoteAddr);

                JSONObject reply = null;
                try {
                    String message = Utils.readFull(connection.getInputStream());
                    Log.d(TAG, "Got message: " + message);
                    reply = handle(new JSONObject(message));
                }
                catch (Exception e) {
                    Log.e(TAG, "Failed to handle message.", e);
                    continue;
                }
                finally {
                    try {
                        connection.close();
                    }
                    catch (Exception e) {
                        // ignore
                    }
                }

                if (reply == null) {
                    continue;
                }

                Socket sock = null;
                try {
                    Log.d(TAG, "Sending reply: " + reply.toString());
                    sock = new Socket(remoteAddr, mParameters.serverPort);
                    OutputStream os = new BufferedOutputStream(sock.getOutputStream());
                    os.write(reply.toString().getBytes(Charset.forName("utf-8")));
                    os.flush();
                    os.close();
                }
                catch (Exception e) {
                    Log.e(TAG, "Failed to send msg.", e);
                }
                finally {
                    try {
                        if (sock != null) {
                            sock.close();
                        }
                    }
                    catch (Exception e) {
                        // ignore
                    }
                }
            }

            closeServerSocket();
        }

        private JSONObject getScanResult() {
            JSONObject json = new JSONObject();

            try {
                json.put("timestamp", System.currentTimeMillis());
                if (new File("/system/bin/iw").exists()) {
                    json.put("detailed", true);
                    json.put("output", Utils.call("iw wlan0 scan", -1 /* no timeout */, true /* require su */)[1]);
                }
                else {
                    json.put("detailed", false);
                    json.put("results", Utils.getScanResults(mContext));
                }
            }
            catch (Exception e) {
                // ignore
            }
            return json;
        }

        private void handleCollect(JSONObject request, JSONObject reply) throws JSONException, Exception {
            reply.put("mac", Utils.getMacAddress("wlan0"));

            if (request.optBoolean("client_scan", false)) {
                Log.d(TAG, "Collecting scan result.");
                reply.put("scanResult", getScanResult());
            }

            if (request.optBoolean("client_traffic", false)) {
                if (!Utils.isPhoneLabDevice(mContext)) {
                    Log.w(TAG, "Not PhoneLab devices, ignoring traffic request.");
                }
                else {
                    Log.d(TAG, "Collecting traffic condition.");

                    SnifTask.Params params = new SnifTask.Params();
                    if (request.has("channels")) {
                        JSONArray channels = request.getJSONArray("channels");
                        for (int i = 0; i < channels.length(); i++) {
                            params.channels.add(channels.getInt(i));
                        }
                    }
                    else {
                        params.channels.add(1);
                        params.channels.add(6);
                        params.channels.add(11);
                    }
                    params.durationSec = request.optInt("durationSec", 30);
                    params.packetCount = -1;
                    params.forever = false;

                    SnifTask task = new SnifTask(mContext, null);
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);

                    reply.put("traffic", task.get().toJSONObject());

                    Log.d(TAG, "Waiting for Wifi connection.");
                    while (!Utils.hasNetworkConnection(mContext, ConnectivityManager.TYPE_WIFI)) {
                        Thread.sleep(5);
                    }
                }
            }
        }

        private void handleExecute(JSONObject request, JSONObject reply) {
        }

        private JSONObject handle(JSONObject request) {
            JSONObject reply = new JSONObject();

            try {
                if (request.has("action")) {
                    Log.e(TAG, "No action specified. Ingoring.");
                    reply = null;
                }
                else if ("collect".equals(request.getString("action"))) {
                    handleCollect(request, reply);
                }
                else if ("execute".equals(request.getString("action"))) {
                    handleExecute(request, reply);
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to handle request.", e);
            }
            return reply;
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
    public String targetSSID;

    @Element
    public Integer connectionTimeoutSec;

    public ServerTaskParameters() {
        checkIntervalSec = 60L;
        serverPort = 6543;
        targetSSID = "PocketSniffer";
        connectionTimeoutSec = 30;
    }

    public ServerTaskParameters(ServerTaskParameters params) {
        this.checkIntervalSec = params.checkIntervalSec;
        this.serverPort = params.serverPort;
        this.targetSSID = params.targetSSID;
        this.connectionTimeoutSec = params.connectionTimeoutSec;
    }

}

@Root(name = "ServerTask")
class ServerTaskState extends PeriodicState {
}
