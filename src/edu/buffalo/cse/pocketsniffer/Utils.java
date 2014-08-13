package edu.buffalo.cse.pocketsniffer;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import org.json.JSONObject;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
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

    /** Join strings together using sep */
    public static String join(String sep, String[] str) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length-1; i++) {
            sb.append(str[i] + sep);
        }
        sb.append(str[str.length-1]);
        return sb.toString();
    }


    /** 
     * Call a shell command using su, get result.
     *
     * @param cmd Command in one line.
     * @param timoutSec If positive, wait at most this many seconds. 0 means
     * infite time out.
     * @param su If {@code true}, run command using su, otherwise, use normal
     * shell.
     *
     * @return
     *  - ret[0] is sub process's return code, Integer.
     *  - ret[1] is sub process's output, String.
     *  - ret[2] is sub process's err output, String.
     */
    public static Object[] call(String cmd, int timeoutSec, boolean su) {
        Log.d(TAG, "Calling " + cmd);

        Process proc = null;
        Integer retval = -1;
        String output = null;
        String err = null;

        String shell = su? "su" : "/system/bin/sh";

        try {
            proc = Runtime.getRuntime().exec(shell);
            DataOutputStream stdin = new DataOutputStream(proc.getOutputStream());
            stdin.writeBytes(cmd + "\n");
            stdin.writeBytes("exit\n");
            stdin.flush();
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
            err = readFull(proc.getErrorStream());
        }
        catch (InterruptedException e) {
            Log.e(TAG, "Cmd " + cmd + " interrupted.", e);
        }
        catch (IOException e) {
            Log.e(TAG, "", e);
        }
        finally {
            if (proc != null) {
                proc.destroy();
            }
        }
        return new Object[]{retval, output, err};
    }

    /** Wrapper of call function using array of progs. */
    public static Object[] call(String[] cmd, int timeoutSec, boolean su) {
        String cmdOneLine = join(" ", cmd);
        return call(cmdOneLine, timeoutSec, su);
    }

    /** Get all declared field of a class. */
    public static List<Field> getAllFields(Class<?> c) {
        List<Field> fields = new ArrayList<Field>();

        while (c != null && c != Object.class) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
            c = c.getSuperclass();
        }

        return fields;
    }

    /** 
     * Get a certain field of an object.
     *
     * Exceptions and private fields are handled properly.
     */
    public static Object safeGet(Field field, Object object) {
        boolean accessible = field.isAccessible();
        Object ret = null;

        if (!accessible) {
            field.setAccessible(true);
        }
        try {
            ret = field.get(object);
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to get " + object.getClass().getName() + "." + field.getName(), e);
        }
        if (!accessible) {
            field.setAccessible(false);
        }
        return ret;
    }

    /** 
     * Dump an object's all field as JSONObject.
     *
     * Similar to {@code obj.__dict__} in python.
     */
    public static JSONObject dumpFieldsAsJSON(Object object) {
        JSONObject json = new JSONObject();
        for (Field field : getAllFields(object.getClass())) {
            try {
                json.put(field.getName(), safeGet(field, object));
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to convert to JSON: " + object.getClass().getName() + "." + field.getName(), e);
            }

        }
        return json;
    }

    /**
     * Get a proper string representaion for a time point.
     *
     * @param ms Milliseconds from Unix epoch (1/1/1970).
     *
     * @return Data time string without space, suitable for using in file name.
     */
    public static String getDateTimeString(long ms) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss.SSSZ", Locale.US);
        return sdf.format(new Date(ms));
    }

    /** Get date time string from {@code struct timeval}. */
    public static String getDateTimeString(int sec, int usec) {
        return getDateTimeString(sec * 1000 + usec / 1000);
    }

    /** Get date time string of now. */
    public static String getDateTimeString() {
        return getDateTimeString(System.currentTimeMillis());
    }

    /** Get a certain package's Linux UID. */
    public static int getUid(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo info : apps) {
            if (info.packageName.equals(packageName)) {
                return info.uid;
            }
        }
        return -1;
    }

    /** Get my package's UID. */
    public static int getMyUid(Context context) {
        return getUid(context, context.getPackageName());
    }

    /** Get my IP address. */
    public static InetAddress getIpAddress(Context context) {
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr;
                    }
                }
            }
        }
        catch (SocketException e) {
            Log.e(TAG, "Failed to get IP address.", e);
        }
        return null;
    }

    /** Test if has connectivity.
     *
     * @param type One of the network types defined in {@code
     * ConnectivityManager}. Or -1 if don't care.
     */
    public static boolean hasNetworkConnection(Context context, int type) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        if (info != null && info.isConnected() ) {
            if (type > 0) {
                return info.getType() == type;
            }
            else {
                return true;
            }
        }
        return false;
    }

    public static boolean hasNetworkConnection(Context context) {
        return hasNetworkConnection(context, -1);
    }

    public static InetAddress getBroadcastAddress(Context context) {
        if (!hasNetworkConnection(context, ConnectivityManager.TYPE_WIFI)) {
            return null;
        }
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
        if (dhcpInfo == null) {
            return null;
        }
        int broadcast = (dhcpInfo.ipAddress & dhcpInfo.netmask) | ~dhcpInfo.netmask;
        return int2Addr(broadcast);
    }


    public static void startPeriodic(Context context, PendingIntent intent, long intervalMs) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + intervalMs, intervalMs, intent);
    }

    public static void stopPeriodic(Context context, PendingIntent intent) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(intent);
    }

    public static byte[] compress(String raw) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            DeflaterOutputStream dos = new DeflaterOutputStream(bos, new Deflater(Deflater.BEST_COMPRESSION));
            dos.write(raw.getBytes("UTF-8"));
            dos.close();
        }
        catch (IOException e) {
            Log.e(TAG, "Failed to compress string: " + raw, e);
            return null;
        }
        return bos.toByteArray();
    }

    public static String decompress(byte[] raw, int offset, int length) {
        try {
            InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(raw, offset, length));
            return readFull(iis);
        }
        catch (IOException e) {
            Log.e(TAG, "Failed to decompress: " + raw.toString(), e);
            return null;
        }
    }

    public static String dumpHex(byte[] array, int offset, int length) {
        Formatter formatter = new Formatter();
        for (int i = 0; i < length; i++) {
            if (i % 16 == 0) {
                formatter.format("[%04X] ", i);
            }
            if (i % 16 == 15) {
                formatter.format("%02X\n", array[offset+i]);
            }
            else {
                formatter.format("%02X ", array[offset+i]);
            }
        }
        String hex = formatter.toString();
        formatter.close();
        return hex;
    }
}
