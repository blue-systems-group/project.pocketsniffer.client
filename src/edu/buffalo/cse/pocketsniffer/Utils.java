package edu.buffalo.cse.pocketsniffer;

import java.net.InetAddress;
import java.net.UnknownHostException;

import android.util.Log;

public class Utils {

    private static String TAG = Utils.getTag(Utils.class);

    /** Generate log tag for class.  */
    public static String getTag(Class<?> c) {
        return "PocketSniffer-" + c.getSimpleName();
    }

    /** Convert integer to IPv4 address. */
    public static InetAddress int2Addr(int addr) {
        byte[] bytes = { 
            (byte)(0xff & addr),
            (byte)(0xff & (addr >> 8)),
            (byte)(0xff & (addr >> 16)),
            (byte)(0xff & (addr >> 24)) };

        try {
            return InetAddress.getByAddress(bytes);
        }
        catch (UnknownHostException e) {
            Log.e(TAG, "Faild to get inetaddress from " + addr + ".", e);
            return null;
        }
    }

    /** Strip leading and trailing quotes. */
    public static String stripQuotes(String s) {
        return s.replaceAll("^\"|\"$", "");
    }

    public static String addQuotes(String s) {
        return "\"" + s + "\"";
    }
}
