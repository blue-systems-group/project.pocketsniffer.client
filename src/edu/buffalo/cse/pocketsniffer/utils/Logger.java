package edu.buffalo.cse.pocketsniffer.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONObject;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import edu.buffalo.cse.phonelab.toolkit.android.interfaces.UploaderClient;
import edu.buffalo.cse.phonelab.toolkit.android.interfaces.UploaderFileDescription;
import edu.buffalo.cse.phonelab.toolkit.android.services.UploaderService;
import edu.buffalo.cse.phonelab.toolkit.android.services.UploaderService.LoggerBinder;
import edu.buffalo.cse.phonelab.toolkit.android.utils.Utils;

public class Logger implements UploaderClient {
    public static final String LOGGER_FORMAT = "1.0";
    public static final String ACTION_LOG_FILE = Logger.class.getName() + ".LogFile";
    public static final String KEY_ACTION = "action";

    private static final String TAG = LocalUtils.getTag(Logger.class);
    private static final int FLUSH_LINES = 16;
    private static final int RORATE_LINES = 1024;
    private static final String DEFAULT_UPLOAD_URL = "";

    private static Logger mInstance;
    private File mFileDir;
    private File mCurrentFile;
    private Writer mWriter;
    private int mCurrentLine;
    private Context mContext;

    private Map<String, File> mFiles;

    private UploaderService mUploaderService;
    private ServiceConnection mUploaderServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LoggerBinder binder = (LoggerBinder) service;
            mUploaderService = binder.getService();
            mUploaderService.registerLogger(Logger.this, UploaderService.PRIORITY_HIGH);
            Log.d(TAG, "Connected to uploader service.");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mUploaderService = null;
        }

    };

    public List<File> getLogFiles() {
        List<File> files = new ArrayList<File>(mFiles.values());
        Collections.sort(files, new Comparator<File>() {

            @Override
            public int compare(File lhs, File rhs) {
                return Long.valueOf(lhs.lastModified()).compareTo(rhs.lastModified());
            }
        });
        return files;
    }


    private Logger(Context context) {
        mContext = context;

        mFileDir = mContext.getDir(Logger.class.getSimpleName(), Context.MODE_PRIVATE);
        mFiles = new ConcurrentHashMap<String, File>();

        for (File f : mFileDir.listFiles()) {
            Log.d(TAG, "Adding " + f.getAbsolutePath());
            mFiles.put(f.getAbsolutePath(), f);
        }

        try {
            mCurrentFile = new File(mFileDir, getLogFileName());
            mWriter = new BufferedWriter(new FileWriter(mCurrentFile));
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to create log file.", e);
            mCurrentFile = null;
            mWriter = null;
        }
        mCurrentLine = 0;

        Intent intent = new Intent(mContext, UploaderService.class);
        intent.putExtra(UploaderService.EXTRA_UPLOAD_URL, DEFAULT_UPLOAD_URL);
        mContext.bindService(intent, mUploaderServiceConnection, Context.BIND_AUTO_CREATE);

        mContext.sendBroadcast(new Intent(ACTION_LOG_FILE));
    }

    private String getLogFileName() {
        return Utils.getDateTimeString() + ".log";
    }

    public static Logger getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new Logger(context);
        }
        return mInstance;
    }

    public synchronized boolean log(JSONObject json) {
        if (!json.has(KEY_ACTION)) {
            Log.w(TAG, "No action specified.");
        }

        try {
            json.put("timestamp", Utils.getDateTimeString());
            json.put("format", LOGGER_FORMAT);
            json.put("deviceType", "Android");
            json.put("deviceID", Utils.getDeviceID(mContext));
        }
        catch (Exception e) {
            Log.d(TAG, "Failed to add timestamp and format.", e);
            return false;
        }

        if (mCurrentFile == null) {
            Log.w(TAG, "No current file to write. Trying to create.");
            try {
                mCurrentFile = new File(mFileDir, getLogFileName());
                mWriter = new BufferedWriter(new FileWriter(mCurrentFile));
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to create log file.", e);
                mCurrentFile = null;
                mWriter = null;
                return false;
            }
        }

        try {
            mWriter.write(json.toString() + "\n");
            mCurrentLine++;
            if (mCurrentLine % FLUSH_LINES == 0) {
                mWriter.flush();
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to write line: " + json.toString(), e);
            return false;
        }

        if (mCurrentLine % RORATE_LINES == 0) {
            try {
                mWriter.flush();
                mWriter.close();
                mFiles.put(mCurrentFile.getAbsolutePath(), mCurrentFile);

                mCurrentFile = new File(mFileDir, getLogFileName());
                mWriter = new BufferedWriter(new FileWriter(mCurrentFile));
                mCurrentLine = 0;
                mContext.sendBroadcast(new Intent(ACTION_LOG_FILE));
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to create log file.", e);
                mWriter = null;
                return false;
            }
        }
        return true;
    }

    @Override
    public long bytesAvailable() {
        long bytes = 0;
        for (File f : mFiles.values()) {
            bytes += f.length();
        }
        return bytes;
    }

    @Override
    public void complete(UploaderFileDescription f, boolean success) {
        File file = new File(f.src);
        if (success) {
            Log.d(TAG, "Uploaded file " + file.getAbsolutePath());
            file.delete();
            mFiles.remove(file.getAbsolutePath());
        }
        else {
            Log.e(TAG, "Failed to upload file " + file.getAbsolutePath());
        }
    }

    @Override
    public int filesAvailable() {
        return mFiles.size();
    }

    @Override
    public boolean hasNext() {
        return !mFiles.isEmpty();
    }

    @Override
    public UploaderFileDescription next() {
        for (Map.Entry<String, File> entry : mFiles.entrySet()) {
            File f = entry.getValue();
            try {
                return new UploaderFileDescription(f.getAbsolutePath(), f.getName(), Utils.getPackageName(mContext));
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to add file " + f.getAbsolutePath(), e);
            }
        }
        return null;
    }

    @Override
    public void prepare() {
        for (File f : mFileDir.listFiles()) {
            mFiles.put(f.getAbsolutePath(), f);
        }
        if (mCurrentFile != null) {
            mFiles.remove(mCurrentFile.getAbsolutePath());
        }
        mContext.sendBroadcast(new Intent(ACTION_LOG_FILE));
    }
}
