package edu.buffalo.cse.pocketsniffer.tasks;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;

import org.json.JSONArray;
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

    private ActualServerTask mServerTask = null;

    public ServerTask(Context context) {
        super(context, ServerTask.class.getSimpleName());
    }

    public void startServerTask() {
        Log.d(TAG, "Starting server task.");
        if (mServerTask == null || mServerTask.getStatus() == AsyncTask.Status.FINISHED) {
            Log.d(TAG, "Creating server task.");
            mServerTask = new ActualServerTask(mParameters.serverPort);
        }

        if (mServerTask.getStatus() != AsyncTask.Status.RUNNING) {
            mServerTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
        else {
            Log.d(TAG, "Server task already running.");
        }
    }

    public void stopServerTask() {
        Log.d(TAG, "Stopping server task.");
        if (mServerTask != null && mServerTask.getStatus() == AsyncTask.Status.RUNNING) {
            mServerTask.cancel(true);
        }
        mServerTask = null;
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
        // only run server task when connected to PocketSniffer wifi
        if (shouldServerRunning()) {
            startServerTask();
        }
        else {
            stopServerTask();
        }
    }

    @Override
    public boolean parametersUpdated(String manifestString) {
        super.parametersUpdated(manifestString);

        if (mServerTask != null && mParameters.serverPort != mServerTask.port) {
            Log.d(TAG, "Server port changed from " + mServerTask.port + " to " + mParameters.serverPort + ".");
            Log.d(TAG, "Restarting server task...");
            stopServerTask();
            startServerTask();
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

        mServerTask.cancel(true);
    }

    private class ActualServerTask extends AsyncTask<Void, Void, Void> {
        private static final int BUFFER_SIZE = 1024*1024;

        public int port;

        public ActualServerTask(int port) {
            this.port = port;
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (!shouldServerRunning()) {
                return null;
            }

            InetAddress addr;
            try {
                addr = Utils.getIpAddress(mContext);
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to get IP address.", e);
                return null;
            }

            ServerSocket serverSock = null;
            while (!isCancelled() && shouldServerRunning()) {
                // open a new server socket each time, since the current one
                // may be broken when go to monitor mode
                try {
                    serverSock = new ServerSocket(port);
                    serverSock.setReuseAddress(true);
                    serverSock.setSoTimeout(0);
                }
                catch (Exception e) {
                    Log.e(TAG, "Failed to create server socket.", e);
                    try{
                        Thread.sleep(1000);
                    }
                    catch (Exception ex) {
                    }
                    continue;
                }

                Log.d(TAG, "Successfully created TCP socket listening port " + port);

                Socket connection = null;
                try {
                    Log.d(TAG, "Waiting for connection...");
                    connection = serverSock.accept();
                }
                catch (Exception e) {
                    Log.e(TAG, "Failed to receive receivedPacket.", e);
                    continue;
                }

                try {
                    serverSock.close();
                }
                catch (Exception e) {
                    Log.e(TAG, "Failed to close server socket.", e);
                }

                InetAddress remoteAddr = connection.getInetAddress();

                JSONObject reply = null;
                try {
                    String message = Utils.readFull(connection.getInputStream());
                    try {
                        connection.close();
                    }
                    catch (Exception e) {
                        Log.d(TAG, "Failed to close connection.", e);
                    }
                    Log.d(TAG, "Got message: " + message);
                    reply = handle(new JSONObject(message));
                }
                catch (Exception e) {
                    Log.e(TAG, "Failed to handle message.", e);
                    continue;
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
                    }
                }
            }

            try {
                if (serverSock != null) {
                    serverSock.close();
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to close server socket.", e);
            }
            return null;
        }

        private JSONObject handle(JSONObject msg) {
            JSONObject reply = new JSONObject();

            try {
                reply.put("mac", LocalUtils.getMacAddress("wlan0"));

                if (msg.getBoolean("client_scan")) {
                    Log.d(TAG, "Collecting detailed scan result.");
                    reply.put("scanResult", ScanResultTask.getDetailedScanResult());
                }

                if (msg.getBoolean("client_traffic")) {
                    Log.d(TAG, "Collecting traffic condition.");

                    SnifTask.Params params = new SnifTask.Params();
                    if (msg.has("channels")) {
                        JSONArray channels = msg.getJSONArray("channels");
                        for (int i = 0; i < channels.length(); i++) {
                            params.channels.add(channels.getInt(i));
                        }
                    }
                    else {
                        params.channels.add(1);
                        params.channels.add(6);
                        params.channels.add(11);
                    }
                    params.durationSec = msg.optInt("durationSec", 30);
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
            catch (Exception e) {
                Log.e(TAG, "Failed to handle message.", e);
            }

            return reply;
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
