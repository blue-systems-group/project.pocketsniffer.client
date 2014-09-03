package edu.buffalo.cse.pocketsniffer.interfaces;

public interface AsyncTaskListener<Params, Progress, Result> {
    void onPreExecute();
    void onProgressUpdate(Progress... progresses);
    void onPostExecute(Result result);
    void onCancelled(Result result);
}
