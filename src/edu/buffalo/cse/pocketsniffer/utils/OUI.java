package edu.buffalo.cse.pocketsniffer.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.util.Log;

import edu.buffalo.cse.pocketsniffer.R;

public class OUI {
    private static final String TAG = Utils.getTag(OUI.class);
    private static final int OUI_KEY_LEN = 8;

    private static Map<String, String[]> mOuiMap = new HashMap<String, String[]>();
    private static boolean mInitialized = false;

    public static void initOuiMap(Context context) {
        if (mInitialized) {
            Log.w(TAG, "Not reinitializing OUI mapping.");
            return;
        }

        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(context.getResources().openRawResource(R.raw.oui)));
        }
        catch (Exception e) {
            Log.e(TAG, "Can not found OUI data file.", e);
            return;
        }

        String line = null;
        try {
            while ((line = bufferedReader.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\s");
                if (parts[0].length() != OUI_KEY_LEN) {
                    continue;
                }
                String ouiKey = parts[0];
                String shortName = parts[1];
                parts = line.split("#");
                String longName = shortName;
                if (parts.length >= 2) {
                    longName = parts[1];
                }
                mOuiMap.put(ouiKey, new String[]{shortName, longName});
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to read OUI file.", e);
            return;
        }
        finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                }
                catch (Exception e) {
                }
            }
        }

        Log.d(TAG, "Successfully imported " + mOuiMap.size() + " OUI entries.");
        mInitialized = true;

    }

    private OUI() {
    }


    private static String getOuiKey(String mac) {
        return mac.toUpperCase().substring(0, OUI_KEY_LEN);
    }

    public static String[] lookup(String mac) {
        if (!mInitialized) {
            Log.e(TAG, "Look up OUI while not initialized.");
            return null;
        }
        String ouiKey = getOuiKey(mac);
        if (!mOuiMap.containsKey(ouiKey)) {
            Log.w(TAG, "Unknown OUI " + ouiKey);
            mOuiMap.put(ouiKey, new String[]{"Unknown", "Unknown"});
        }
        return mOuiMap.get(ouiKey);
    }
}
