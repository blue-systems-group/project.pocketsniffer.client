package edu.buffalo.cse.pocketsniffer.utils;


public class LocalUtils {

    private static String TAG = getTag(LocalUtils.class);

    /** Generate log tag for class.  */
    public static String getTag(Class<?> c) {
        return "PocketSniffer-" + c.getSimpleName();
    }
}
