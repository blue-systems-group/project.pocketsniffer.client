package edu.buffalo.cse.pocketsniffer.tasks;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import org.json.JSONObject;

import android.content.Context;
import android.net.ConnectivityManager;
import android.util.Log;

import edu.buffalo.cse.pocketsniffer.interfaces.AsyncTaskListener;
import edu.buffalo.cse.pocketsniffer.interfaces.Task;
import edu.buffalo.cse.pocketsniffer.utils.Utils;

public class ServerTask extends Task<ServerTask.Params, ServerTask.Progress, ServerTask.Result> {

    private static final int BUFFER_SIZE = 1024*1024;

    public ServerTask(Context context, AsyncTaskListener<ServerTask.Params, ServerTask.Progress, ServerTask.Result> listener) {
        super(context, listener);
    }

    @Override
    protected ServerTask.Result doInBackground(ServerTask.Params... params) {
        ServerTask.Result result = new ServerTask.Result();
        result.success = false;

        if (!Utils.hasNetworkConnection(mContext, ConnectivityManager.TYPE_WIFI)) {
            result.reason = "ServerTask started without Wifi connection.";
            Log.w(TAG, result.reason);
            return result;
        }

        InetAddress addr;
        try {
            addr = Utils.getIpAddress(mContext);
        }
        catch (Exception e) {
            result.reason = "Failed to get IP address.";
            Log.e(TAG, result.reason, e);
            return result;
        }

        ServerTask.Params param = params[0];
        DatagramSocket serverSock = null;
        try {
            serverSock = new DatagramSocket(param.port, addr);
            serverSock.setReuseAddress(true);
            serverSock.setReceiveBufferSize(BUFFER_SIZE);
            serverSock.setSendBufferSize(BUFFER_SIZE);
            serverSock.setSoTimeout(0);
        }
        catch (Exception e) {
            result.reason = "Failed to create server socket.";
            Log.e(TAG, result.reason, e);
            return result;
        }

        Log.d(TAG, "Successfully created server UDP socket on port " + param.port);

        byte[] buffer = new byte[BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        while (!isCancelled() && Utils.hasNetworkConnection(mContext, ConnectivityManager.TYPE_WIFI)) {
            try {
                serverSock.receive(packet);
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to receive packet.", e);
                continue;
            }

            Log.d(TAG, "Receive message:\n" + Utils.dumpHex(buffer, 0, packet.getLength()));

            ServerTask.Progress progress = new ServerTask.Progress();
            progress.sender = packet.getAddress();
            try {
                progress.message = Utils.decompress(buffer, 0, packet.getLength());
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to decompress message.", e);
                continue;
            }
            progress.success = handle(progress.message);

            publishProgress(progress);
        }
        return null;
    }

    private boolean handle(String msg) {
        Log.d(TAG, "Got message " + msg);

        JSONObject json = null;
        
        try {
            json = new JSONObject(msg);
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to parse message: " + msg, e);
        }
        return false;
    }

    public static class Params {
        public int port;
    }

    public static class Progress {
        public InetAddress sender;
        public String message;
        public boolean success;
    }

    public static class Result {
        public boolean success;
        public String reason;
    }
}


