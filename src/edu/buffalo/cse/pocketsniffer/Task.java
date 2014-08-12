package edu.buffalo.cse.pocketsniffer;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

public abstract class Task<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {

    protected final String TAG = Utils.getTag(this.getClass());

    protected AsyncTaskListener<Params, Progress, Result> mListener;
    protected Context mContext;

    public Task() {
        Log.d(TAG, "========== Creating " + this.getClass().getSimpleName() + " ==========");
    }

    public Task(Context context) {
        Log.d(TAG, "========== Creating " + this.getClass().getSimpleName() + " ==========");
        mContext = context;
        mListener = null;
    }

    public Task(Context context, AsyncTaskListener<Params, Progress, Result> listener) {
        Log.d(TAG, "========== Creating " + this.getClass().getSimpleName() + " ==========");
        mContext = context;
        mListener = listener;
    }

    @Override
    protected void onCancelled(Result result) {
        Log.d(TAG, "Cancelling " + this.getClass().getSimpleName() + " ...");
        if (mListener != null) {
            mListener.onCancelled(result);
        }
    }

    @Override
    protected void onPreExecute() {
        if (mListener != null) {
            mListener.onPreExecute();
        }
    }


    @Override
    protected void onProgressUpdate(Progress... values) {
        Log.d(TAG, "Progress update of " + this.getClass().getSimpleName());
        if (mListener != null) {
            mListener.onProgressUpdate(values);
        }
    }

    @Override
    protected void onPostExecute(Result result) {
        if (mListener != null) {
            mListener.onPostExecute(result);
        }
    }

}
