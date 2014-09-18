package edu.buffalo.cse.pocketsniffer.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import edu.buffalo.cse.phonelab.toolkit.android.utils.Utils;
import edu.buffalo.cse.pocketsniffer.R;
import edu.buffalo.cse.pocketsniffer.interfaces.Constants;

public class OUI implements Constants {
    private static final String TAG = LocalUtils.getTag(OUI.class);

    private static final String TABLE_NAME = "oui";
    private static final String COLUMN_NAME_OUI = "oui";
    private static final String COLUMN_NAME_SHORT_NAME = "short_name";
    private static final String COLUMN_NAME_LONG_NAME = "long_name";

    private static final String DB_FILE_NAME = "oui.db";

    private static Map<String, String[]> mCache = new HashMap<String, String[]>();
    private static SQLiteDatabase mDB = null;

    public synchronized static void initDB(Context context) throws Exception {
        if (mDB != null) {
            Log.w(TAG, "Not reinitializing OUI DB.");
            return;
        }

        File dbFile = new File(context.getFilesDir().getAbsolutePath() + File.separator + DB_FILE_NAME);
        if (!dbFile.exists()) {
            InputStream in = new BufferedInputStream(context.getResources().openRawResource(R.raw.oui));
            OutputStream out = new BufferedOutputStream(new FileOutputStream(dbFile));
            Utils.copyStream(in, out);
        }
        mDB = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
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
            shortName = longName = UNKNOWN;
        }
        else {
            //XXX: returned cursor is positioned BEFORE the first entry.
            cursor.moveToFirst();
            shortName = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_SHORT_NAME));
            longName = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_LONG_NAME));
        }
        mCache.put(key, new String[]{shortName, longName});
    }

    public synchronized static String[] lookup(String mac) {
        if (mDB == null) {
            return new String[]{UNKNOWN, UNKNOWN};
        }

        String ouiKey = getOuiKey(mac);
        if (!mCache.containsKey(ouiKey)) {
            queryDB(ouiKey);
        }
        return mCache.get(ouiKey);
    }
}
