package edu.buffalo.cse.pocketsniffer.utils;

import java.net.NetworkInterface;

public class LocalUtils {

    private static String TAG = getTag(LocalUtils.class);

    /** Generate log tag for class.  */
    public static String getTag(Class<?> c) {
        return "PocketSniffer-" + c.getSimpleName();
    }

    public static String getMacAddress(String ifaceName) throws Exception {
        NetworkInterface iface = NetworkInterface.getByName(ifaceName);
        byte[] mac = iface.getHardwareAddress();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            if (i < mac.length - 1) {
                sb.append(String.format("%02x:", mac[i]));
            }
            else {
                sb.append(String.format("%02x", mac[i]));
            }
        }

        return sb.toString();
    }
}
