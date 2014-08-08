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
    private final String TAG = Utils.getTag(this.getClass());
    private static final int OUI_KEY_LEN = 8;

    private static OUI mInstance;

    private Map<String, String> mOuiMap;
    private Context mContext;

    private void initOuiMap() {
        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(mContext.getResources().openRawResource(R.raw.oui)));
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
                String[] parts = line.split(" ");
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
    }

    private OUI(Context context) {
        mOuiMap = new HashMap<String, String>();
        mContext = context;

        initOuiMap();
    }

    public synchronized static OUI getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new OUI(context);
        }
        return mInstance;
    }

    private String getOuiKey(String mac) {
        return mac.substring(0, OUI_KEY_LEN);
    }

    public String lookup(String mac) {
        String ouiKey = getOuiKey(mac);
        if (!mOuiMap.containsKey(ouiKey)) {
            mOuiMap.put(ouiKey, "Unknown");
        }
        return mOuiMap.get(getOuiKey(mac));
    }
}
