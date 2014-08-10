package edu.buffalo.cse.pocketsniffer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    /** Add quotes around string. */
    public static String addQuotes(String s) {
        return "\"" + s + "\"";
    }

    /** Read all contents from input stream. */
    public static String readFull(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            sb.append(line + "\n");
        }
        return sb.toString();
    }

    public static String join(String sep, String[] str) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length-1; i++) {
            sb.append(str[i] + sep);
        }
        sb.append(str[str.length-1]);
        return sb.toString();
    }

    /** Call a shell command, get result.
     * ret[0] is sub process's return code, Integer.
     * ret[1] is sub process's output, String.
     */
    public static Object[] call(String[] cmd, int timeoutSec) {
        Process proc = null;
        Integer retval = -1;
        String output = null;
        Log.d(TAG, "Calling " + join(" ", cmd));
        try {
            proc = Runtime.getRuntime().exec(cmd);
            if (timeoutSec > 0) {
                for (int i = 0; i < timeoutSec; i++) {
                    try {
                        retval = proc.exitValue();
                        break;
                    }
                    catch (IllegalThreadStateException e) {
                        Thread.sleep(1000);
                    }
                }
            }
            else {
                retval = proc.waitFor();
            }
            output = readFull(proc.getInputStream());
        }
        catch (InterruptedException e) {
            Log.e(TAG, "Cmd " + cmd[0] + " interrupted.", e);
        }
        catch (IOException e) {
            Log.e(TAG, "", e);
        }
        finally {
            if (proc != null) {
                proc.destroy();
            }
        }
        return new Object[]{retval, output};
    }
}
