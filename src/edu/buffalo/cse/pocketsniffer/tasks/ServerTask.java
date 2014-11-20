package edu.buffalo.cse.pocketsniffer.tasks;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.Charset;

import org.json.JSONArray;
import org.json.JSONObject;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

import android.content.Context;
import android.net.ConnectivityManager;
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
        if (mServerTask == null || mServerTask.getStatus() == AsyncTask.Status.FINISHED) {
            mServerTask = new ActualServerTask(mParameters.serverPort);
        }

        switch (mServerTask.getStatus()) {
            case PENDING:
            case FINISHED:
                Log.d(TAG, "Starting server task...");
                mServerTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                break;
            case RUNNING:
                Log.d(TAG, "Server task already running.");
                break;
        }
    }



    @Override
    protected void check(ServerTaskParameters arg0) throws Exception {
        startServerTask();
    }


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

        private int port;

        public ActualServerTask(int port) {
            this.port = port;
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (!Utils.hasNetworkConnection(mContext, ConnectivityManager.TYPE_WIFI)) {
                Log.w(TAG, "ServerTask started without Wifi connection.");
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

            DatagramSocket serverSock = null;
            try {
                serverSock = new DatagramSocket(port, addr);
                serverSock.setReuseAddress(true);
                serverSock.setReceiveBufferSize(BUFFER_SIZE);
                serverSock.setSendBufferSize(BUFFER_SIZE);
                serverSock.setSoTimeout(0);
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to create server socket.", e);
                return null;
            }

            Log.d(TAG, "Successfully created server UDP socket on port " + port);

            DatagramPacket receivedPacket = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);

            while (!isCancelled() && Utils.hasNetworkConnection(mContext, ConnectivityManager.TYPE_WIFI)) {
                try {
                    serverSock.receive(receivedPacket);
                }
                catch (Exception e) {
                    Log.e(TAG, "Failed to receive receivedPacket.", e);
                    continue;
                }

                JSONObject reply = null;
                try {
                    reply = handle(new JSONObject(Utils.decompress(receivedPacket.getData(), 0, receivedPacket.getLength())));
                }
                catch (Exception e) {
                    Log.e(TAG, "Failed to handle message.", e);
                    continue;
                }
                if (reply != null) {
                    byte[] data = reply.toString().getBytes(Charset.forName("UTF-8"));
                    try {
                        (new DatagramSocket(receivedPacket.getPort(), receivedPacket.getAddress())).send(new DatagramPacket(data, data.length));
                    }
                    catch (Exception e) {
                        Log.e(TAG, "Failed to send message to " + receivedPacket.getAddress() + " at port " + receivedPacket.getPort(), e);
                    }
                }
            }
            return null;
        }

        private JSONObject handle(JSONObject msg) {
            Log.d(TAG, "Got message " + msg.toString());

            JSONObject reply = new JSONObject();

            try {
                reply.put("mac", LocalUtils.getMacAddress("wlan0"));

                if (msg.getBoolean("collectScanResult")) {
                    Log.d(TAG, "Collecting detailed scan result.");
                    reply.put("scanResult", ScanResultTask.getDetailedScanResult());
                }

                if (msg.getBoolean("collectTraffic")) {
                    Log.d(TAG, "Collecting traffic condition.");

                    SnifTask.Params params = new SnifTask.Params();
                    JSONArray channels = msg.getJSONArray("channels");
                    for (int i = 0; i < channels.length(); i++) {
                        params.channels.add(channels.getInt(i));
                    }
                    params.durationSec = msg.optInt("durationSec", 30);
                    params.packetCount = -1;
                    params.forever = false;

                    SnifTask task = new SnifTask(mContext, null);
                    task.execute(params);

                    reply.put("traffic", task.get().toJSONObject());
                }
            }
            catch (Exception e) {
                return null;
            }

            return reply;
        }
    }
}



@Root(name = "ServerTask")
class ServerTaskParameters extends PeriodicParameters {

    @Element
    public Integer serverPort;

    public ServerTaskParameters() {
        checkIntervalSec = 60L;
        serverPort = 12345;
    }

    public ServerTaskParameters(ServerTaskParameters params) {
        this.checkIntervalSec = params.checkIntervalSec;
        this.serverPort = params.serverPort;
    }

}

@Root(name = "ServerTask")
class ServerTaskState extends PeriodicState {
}
