package edu.buffalo.cse.pocketadmin;

import android.util.Log;

import net.kismetwireless.android.pcapcapture.PacketHandler;
import net.kismetwireless.android.pcapcapture.UsbSource;
import net.kismetwireless.android.pcapcapture.Packet;

public class SnifferPacketHandler extends PacketHandler {
    private final String TAG = "PocketAdmin-" + this.getClass().getSimpleName();

    public void handlePacket(UsbSource s, Packet p) {
        Log.v(TAG, "Packet: singal = " + p.signal + ", noise = " + p.noise + ", len = " + p.bytes.length);
    }
    
}
