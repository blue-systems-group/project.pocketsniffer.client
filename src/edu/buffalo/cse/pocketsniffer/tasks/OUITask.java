package edu.buffalo.cse.pocketsniffer.tasks;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import android.content.Context;
import android.util.Log;

import edu.buffalo.cse.pocketsniffer.R;
import edu.buffalo.cse.pocketsniffer.utils.OUI;


public class OUITask extends Task<OUITask.Params, OUITask.Progress, OUITask.Result> {

    private static final String DB_FILE_NAME = "oui.db";

    private File mDBFile;

    public OUITask(Context context, AsyncTaskListener<OUITask.Params, OUITask.Progress, OUITask.Result> listener) {
        super(context, listener);
        mDBFile = new File(mContext.getFilesDir().getAbsolutePath() + File.separator + DB_FILE_NAME);
    }

    private void copyFile() throws Exception {
        BufferedInputStream in = new BufferedInputStream(mContext.getResources().openRawResource(R.raw.oui));
        BufferedOutputStream out = null;
        try {
            out = new BufferedOutputStream(new FileOutputStream(mDBFile));
            byte[] buffer = new byte[100*1024];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }
        catch (Exception e) {
            throw e;
        }
        finally {
            if (out != null) {
                try {
                    out.close();
                }
                catch (Exception e) {}
            }
        }
    }


    @Override
    protected Result doInBackground(Params... params) {
        Progress progress = new Progress();
        Result result = new Result();
        result.succeed = false;

        if (!mDBFile.exists()) {
            progress.msg = "Copying DB file";
            publishProgress(progress);
            Log.d(TAG, progress.msg);

            try {
                copyFile();
            }
            catch (Exception e) {
                result.msg = "Failed to copy DB file."; 
                Log.e(TAG, result.msg, e);
                return result;
            }
        }

        progress.msg = "Initializing OUI database";
        publishProgress(progress);
        try {
            OUI.initDB(mDBFile.getAbsolutePath());
        }
        catch (Exception e) {
            result.msg = "Failed to initialize OUI database.";
            Log.e(TAG, result.msg, e);
            return result;
        }

        result.msg = "Succefully initialized OUI database.";
        result.succeed = true;
        return result;
    }

    public static class Params {
    }

    public static class Progress {
        public String msg;
    }

    public static class Result {
        public String msg;
        public boolean succeed;
    }
}
