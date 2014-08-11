package edu.buffalo.cse.pocketsniffer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

public class OUI {
    private static final String TAG = Utils.getTag(OUI.class);
    private static final int OUI_KEY_LEN = 8;

    private static Map<String, String> mOuiMap = new HashMap<String, String>();
    private static boolean mInitialized = false;

    public static void initOuiMap(Context context) {
        if (mInitialized) {
            Log.w(TAG, "Not reinitialize OUI mapping.");
            return;
        }

        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(context.getResources().openRawResource(R.raw.oui)));
        }
        catch (Resources.NotFoundException e) {
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
                mOuiMap.put(parts[0], parts[1]);
            }
        }
        catch (IOException e) {
            Log.e(TAG, "Failed to read OUI file.", e);
            return;
        }
        Log.d(TAG, "Successfully imported " + mOuiMap.size() + " OUI entries.");
        mInitialized = true;
    }

    private OUI() {
    }


    private static String getOuiKey(String mac) {
        return mac.substring(0, OUI_KEY_LEN);
    }

    public static String lookup(String mac) {
        if (!mInitialized) {
            Log.e(TAG, "Look up OUI while not initialized.");
            return "Unknown";
        }

        String ouiKey = getOuiKey(mac);
        if (!mOuiMap.containsKey(ouiKey)) {
            mOuiMap.put(ouiKey, "Unknown");
        }
        return mOuiMap.get(getOuiKey(mac));
    }
}
