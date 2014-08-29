package edu.buffalo.cse.pocketsniffer.utils;

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

import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

public class Utils {

    private static String TAG = Utils.getTag(Utils.class);

    /** Generate log tag for class.  */
    public static String getTag(Class<?> c) {
        return "PocketSniffer-" + c.getSimpleName();
    }

    /** Convert integer to IPv4 address. */
    public static InetAddress int2Addr (int addr) throws UnknownHostException { 
        byte[] bytes = { 
            (byte)(0xff & addr),
            (byte)(0xff & (addr >> 8)),
            (byte)(0xff & (addr >> 16)),
            (byte)(0xff & (addr >> 24)),
        };
        return InetAddress.getByAddress(bytes);
    }

    /** Strip (as many as) leading and trailing quotes. */
    public static String stripQuotes(String s) {
        return s.replaceAll("^\"|\"$", "");
    }

    /** Add a pair of double quotes around string. */
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
    public static Object[] call(String cmd, int timeoutSec, boolean su) throws InterruptedException, IOException {
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
            return new Object[]{retval, output, err};
        }
        catch (InterruptedException e) {
            throw e;
        }
        catch (IOException e) {
            throw e;
        }
        finally {
            if (proc != null) {
                proc.destroy();
            }
        }
    }

    /** Wrapper of call function using array of progs. */
    public static Object[] call(String[] cmd, int timeoutSec, boolean su) throws InterruptedException, IOException {
        String cmdOneLine = TextUtils.join(" ", cmd);
        return call(cmdOneLine, timeoutSec, su);
    }

    /** 
     * Get all declared field of a class (and it's super class, except for
     * Object).
     * */
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
     * Private fields are handled properly.
     */
    public static Object getField(Field field, Object object) throws Exception {
        boolean accessible = field.isAccessible();
        Object ret = null;

        if (!accessible) {
            field.setAccessible(true);
        }
        ret = field.get(object);
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
    public static JSONObject dumpFieldsAsJSON(Object object) throws JSONException, Exception {
        JSONObject json = new JSONObject();
        for (Field field : getAllFields(object.getClass())) {
            json.put(field.getName(), getField(field, object));
        }
        return json;
    }

    /**
     * Get a proper string representaion for a time point.
     *
     * @param ms Milliseconds from Unix epoch (1/1/1970).
     *
     * @return ISO8601 date time string.
     */
    public static String getDateTimeString(long ms) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
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

    /**
     * Get a certain package's Linux UID.
     */
    public static int getUid(Context context, String packageName) throws PackageManager.NameNotFoundException {
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo info : apps) {
            if (info.packageName.equals(packageName)) {
                return info.uid;
            }
        }
        throw new PackageManager.NameNotFoundException(packageName);
    }

    /** Get my package's UID. */
    public static int getMyUid(Context context) throws PackageManager.NameNotFoundException {
        return getUid(context, context.getPackageName());
    }

    /** Get my IP address. */
    public static InetAddress getIpAddress(Context context) throws SocketException {
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

    /**
     * Test to see if has any kind of network connection available.
     */
    public static boolean hasNetworkConnection(Context context) {
        return hasNetworkConnection(context, -1);
    }

    /**
     * Get broadcast address within group.
     */
    public static InetAddress getBroadcastAddress(Context context) throws UnknownHostException {
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


    /**
     * Start periodically trigering an pending intent.
     */
    public static void startPeriodic(Context context, PendingIntent intent, long intervalMs) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + intervalMs, intervalMs, intent);
    }

    public static void stopPeriodic(Context context, PendingIntent intent) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(intent);
    }

    public static byte[] compress(String raw) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DeflaterOutputStream dos = new DeflaterOutputStream(bos, new Deflater(Deflater.BEST_COMPRESSION));
        dos.write(raw.getBytes("UTF-8"));
        dos.close();
        return bos.toByteArray();
    }

    public static String decompress(byte[] raw, int offset, int length) throws IOException {
        InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(raw, offset, length));
        return readFull(iis);
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

    public static final String CONDUCTOR_PACKAGE_NAME = "edu.buffalo.cse.phonelab.conductor";
    public static boolean isPhoneLabDevice(Context context) {
        PackageManager pm = context.getPackageManager();
        for (PackageInfo info : pm.getInstalledPackages(0)) {
            if (CONDUCTOR_PACKAGE_NAME.equals(info.packageName)) {
                return true;
            }
        }
        return false;
    }

    /** 
     * Bring iface up or down.
     *
     * @return {@code true} if operation succeed. {@code false} otherwise.
     */
    public static boolean ifaceUp(String iface, boolean up) throws Exception {
        String[] cmd = {"ifconfig", iface, up? "up": "down"};
        Object[] result = Utils.call(cmd, -1, true);

        int exitCode = (Integer) result[0];
        if (exitCode == 0) {
            Log.d(TAG, "Successfully bring " + (up? "up": "down") +  " monitor iface.");
            return true;
        }
        else {
            String err = (String) result[2];
            Log.e(TAG, "Failed to bring up monitor interface (" + exitCode + "): " + err);
            return false;
        }
    }

    /**
     * Set dev's channel.
     *
     * @return {@code true} if operation succeed. {@code false} otherwise.
     */
    public static boolean setChannel(String dev, int chan) throws Exception {
        String[] cmd = new String[]{"iw", dev, "set", "channel", Integer.toString(chan)};
        Object[] result = Utils.call(cmd, -1, true);
        Integer exitCode = (Integer) result[0];
        if (exitCode == 0) {
            return true;
        }
        else {
            String err = (String) result[2];
            Log.e(TAG, "Failed to set channel (" + exitCode + "): " + err);
            return false;
        }
    }

    public static String getVersionName(Context context) throws PackageManager.NameNotFoundException {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        return info.versionName;
    }

    public static int getVersionCode(Context context) throws PackageManager.NameNotFoundException {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        return info.versionCode;
    }

    public static int freqToChannel(int freq) throws IllegalArgumentException {
        if (freq >= 2412 && freq <= 2472) {
            return (freq - 2412) / 5 + 1;
        }
        else if (freq >= 5180 && freq <= 5825) {
            return (freq - 5180) / 5 + 36;
        }
        else {
            throw new IllegalArgumentException();
        }
    }

    public static int channelToFreq(int chan) throws IllegalArgumentException {
        if (chan >= 1 && chan <= 13) {
            return 2412 + (chan-1)*5;
        }
        else if (chan >= 36 && chan <= 165) {
            return 5180 + (chan-36)*5;
        }
        else {
            throw new IllegalArgumentException();
        }
    }
}
