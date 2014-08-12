package edu.buffalo.cse.pocketsniffer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.net.ConnectivityManager;
import android.util.Log;

public class ServerTask extends Task<ServerParams, ServerProgress, ServerResult> {

    private static final int BUFFER_SIZE = 1024*1024;

    public ServerTask(Context context, AsyncTaskListener<ServerParams, ServerProgress, ServerResult> listener) {
        super(context, listener);
    }

    @Override
    protected ServerResult doInBackground(ServerParams... params) {
        ServerResult result = new ServerResult();
        result.success = false;

        if (!Utils.hasNetworkConnection(mContext, ConnectivityManager.TYPE_WIFI)) {
            result.reason = "ServerTask started without Wifi connection.";
            Log.w(TAG, result.reason);
            return result;
        }

        InetAddress addr = Utils.getIpAddress(mContext);
        if (addr == null) {
            result.reason = "Failed to get IP address.";
            Log.w(TAG, result.reason);
            return result;
        }

        ServerParams param = params[0];
        DatagramSocket serverSock = null;
        try {
            serverSock = new DatagramSocket(param.port, addr);
            serverSock.setBroadcast(true);
            serverSock.setReuseAddress(true);
            serverSock.setReceiveBufferSize(BUFFER_SIZE);
            serverSock.setSendBufferSize(BUFFER_SIZE);
            serverSock.setSoTimeout(0);
        }
        catch (SocketException e) {
            result.reason = "Failed to create server socket.";
            Log.e(TAG, result.reason, e);
            return result;
        }

        byte[] buffer = new byte[BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        while (!isCancelled()) {
            try {
                serverSock.receive(packet);
            }
            catch (IOException e) {
                Log.e(TAG, "Failed to receive packet.", e);
            }

            handle(new String(buffer, 0, packet.getLength()));
        }
        return null;
    }

    private void handle(String msg) {
        JSONObject json = null;
        
        try {
            json = new JSONObject(msg);
        }
        catch (JSONException e) {
            Log.e(TAG, "Failed to parse message: " + msg, e);
        }
    }
}

class ServerParams {
    int port;
}

class ServerProgress {
}

class ServerResult {
    boolean success;
    String reason;
}
