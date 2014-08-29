package edu.buffalo.cse.pocketsniffer.tasks;

import android.database.sqlite.SQLiteDatabase;


public class OUITask extends Task<OUITask.Params, OUITask.Progress, OUITask.Result> {

    @Override
    protected Result doInBackground(Params... params) {
        SQLiteDatabase db = (new OUIOpenHelper()).getRead
        return null;
    }

    public static class Params {
    }

    public static class Progress {
    }

    public static class Result {
    }
}
