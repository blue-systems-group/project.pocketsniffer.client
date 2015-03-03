package edu.buffalo.cse.pocketsniffer.interfaces;

import edu.buffalo.cse.phonelab.toolkit.android.utils.Utils;

/**
 * All the information we care about a packet. 
 */
public class Packet {
    public int type;
    public int subtype;
    public boolean from_ds;
    public boolean to_ds;
    public int tv_sec;
    public int tv_usec;
    public int len;
    public String addr1;
    public String addr2;
    public int rssi;
    public int freq;
    public boolean retry;
    public boolean crcOK;

    @Override
    public String toString() {
        try {
            return Utils.dumpFieldsAsJSON(this).toString();
        }
        catch (Exception e) {
            return "<unknown";
        }
    }
}
