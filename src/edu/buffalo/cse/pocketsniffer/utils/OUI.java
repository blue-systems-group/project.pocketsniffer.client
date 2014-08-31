package edu.buffalo.cse.pocketsniffer.utils;

import java.util.HashMap;
import java.util.Map;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class OUI {
    private static final String TAG = Utils.getTag(OUI.class);
    private static final int OUI_KEY_LEN = 8;

    private static final String TABLE_NAME = "oui";
    private static final String COLUMN_NAME_OUI = "oui";
    private static final String COLUMN_NAME_SHORT_NAME = "short_name";
    private static final String COLUMN_NAME_LONG_NAME = "long_name";

    private static Map<String, String[]> mCache = new HashMap<String, String[]>();
    private static SQLiteDatabase mDB = null;

    public static void initDB(String dbFile) throws Exception {
        if (mDB != null) {
            Log.w(TAG, "Not reinitializing OUI DB.");
            return;
        }
        mDB = SQLiteDatabase.openDatabase(dbFile, null, SQLiteDatabase.OPEN_READONLY);
    }

    private static String getOuiKey(String mac) {
        return mac.toUpperCase().substring(0, OUI_KEY_LEN);
    }

    private static void queryDB(String key) {
        Cursor cursor = mDB.query(TABLE_NAME,
                new String[]{COLUMN_NAME_SHORT_NAME, COLUMN_NAME_LONG_NAME},
                "oui = ?", /* selection */
                new String[]{key}, /* selection args */
                null /* no groupby */,
                null /* no having */,
                null /* no orderby */
                );
        String shortName, longName;
        if (cursor.getCount() != 1) {
            Log.e(TAG, "Bad OUI key, " + cursor.getCount() + " results.");
            shortName = longName = "UNKNOWN";
        }
        else {
            //XXX: returned cursor is positioned BEFORE the first entry.
            cursor.moveToFirst();
            shortName = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_SHORT_NAME));
            longName = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_LONG_NAME));
        }
        mCache.put(key, new String[]{shortName, longName});
    }

    public static String[] lookup(String mac) {
        String ouiKey = getOuiKey(mac);
        if (!mCache.containsKey(ouiKey)) {
            queryDB(ouiKey);
        }
        return mCache.get(ouiKey);
    }
}
