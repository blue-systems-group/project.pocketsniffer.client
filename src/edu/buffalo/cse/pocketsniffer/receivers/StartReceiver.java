package edu.buffalo.cse.pocketsniffer.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import edu.buffalo.cse.pocketsniffer.services.SnifferService;
import edu.buffalo.cse.pocketsniffer.utils.LocalUtils;

public class StartReceiver extends BroadcastReceiver {
    private static final String TAG = LocalUtils.getTag(StartReceiver.class);

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "Starting Sniffer Service from" + this.getClass().getSimpleName());
		context.startService(new Intent(context, SnifferService.class));
	}
}
