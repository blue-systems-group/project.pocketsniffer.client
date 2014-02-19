package edu.buffalo.cse.pocketadmin;

import java.util.ArrayList;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;

import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import net.kismetwireless.android.pcapcapture.PcapService;
import net.kismetwireless.android.pcapcapture.UsbSource;

public class MainActivity extends Activity {
    private final String TAG = "PocketAdmin-" + this.getClass().getSimpleName();

    private boolean mIsBound = false;
	private Messenger mService = null;
	private Context mContext;
	private ArrayList<DeferredUsbIntent> mDeferredIntents = new ArrayList<DeferredUsbIntent>();
	private final Messenger mMessenger = new Messenger(new IncomingServiceHandler());

	public class DeferredUsbIntent {
		UsbDevice device;
		String action;
		boolean extrapermission;
	};

	class IncomingServiceHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			Bundle b;
			boolean updateUi = false;

            /*
			switch (msg.what) {
			case PcapService.MSG_RADIOSTATE:
				b = msg.getData();

				Log.d(LOGTAG, "Got radio state: " + b);

				if (b == null)
					break;

				if (b.getBoolean(UsbSource.BNDL_RADIOPRESENT_BOOL, false)) {
					mUsbPresent = true;

					mUsbType = b.getString(UsbSource.BNDL_RADIOTYPE_STRING, "Unknown");
					mUsbInfo = b.getString(UsbSource.BNDL_RADIOINFO_STRING, "No info available");
					mLastChannel = b.getInt(UsbSource.BNDL_RADIOCHANNEL_INT, 0);
				} else {
					// Turn off logging
					if (mUsbPresent) 
						doUpdateServiceLogs(mLogPath.toString(), false);

					mUsbPresent = false;
					mUsbType = "";
					mUsbInfo = "";
					mLastChannel = 0;
				}

				updateUi = true;

				break;
			case PcapService.MSG_LOGSTATE:
				b = msg.getData();

				if (b == null)
					break;

				if (b.getBoolean(PcapService.BNDL_STATE_LOGGING_BOOL, false)) {
					mLocalLogging = true;
					mLogging = true;

					mLogPath = new File(b.getString(PcapService.BNDL_CMD_LOGFILE_STRING));
					mLogCount = b.getInt(PcapService.BNDL_STATE_LOGFILE_PACKETS_INT, 0);
					mLogSize = b.getLong(PcapService.BNDL_STATE_LOGFILE_SIZE_LONG, 0);
				} else {
					mLocalLogging = false;
					mLogging = false;

					if (mShareOnStop) {
						Intent i = new Intent(Intent.ACTION_SEND); 
						i.setType("application/cap"); 
						i.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + mOldLogPath)); 
						startActivity(Intent.createChooser(i, "Share Pcap file"));
						mShareOnStop = false;
					}
				}

				updateUi = true;

				break;
			default:
				super.handleMessage(msg);
			}

			if (updateUi)
				doUpdateUi();
                */
		}
	}



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);

        Log.d(TAG, "========== PocketAdmin MainActivity Starting ==========");

        setContentView(R.layout.main);

        mContext = this;

        Log.d(TAG, "Starting AdminService ...");
        Intent intent = new Intent(MainActivity.this, AdminService.class);
        mContext.startService(intent);


        Log.d(TAG, "Starting PcapService ...");
		Intent pcapIntent = new Intent(MainActivity.this, PcapService.class);
		mContext.startService(pcapIntent);
		doBindService();

		IntentFilter intentFilter = new IntentFilter(PcapService.ACTION_USB_PERMISSION);
		intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
		intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		mContext.registerReceiver(mUsbReceiver, intentFilter);
    }

	void doBindService() {
		Log.d(TAG, "binding service");

		if (mIsBound) {
			Log.d(TAG, "already bound");
			return;
		}

		bindService(new Intent(MainActivity.this, PcapService.class), mConnection, Context.BIND_AUTO_CREATE);
		mIsBound = true;
	}

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			Log.d(TAG, "mconnection connected");

			mService = new Messenger(service);

			try {
				Message msg = Message.obtain(null, PcapService.MSG_REGISTER_CLIENT);
				msg.replyTo = mMessenger;
				mService.send(msg);

				for (DeferredUsbIntent di : mDeferredIntents) 
					doSendDeferredIntent(di);

			} catch (RemoteException e) {
				// Service has crashed before we can do anything, we'll soon be
				// disconnected and reconnected, do nothing
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			mService = null;
			mIsBound = false;
		}
	};

	private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

            Log.d(TAG, action);

			if (PcapService.ACTION_USB_PERMISSION.equals(action) ||
					UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action) ||
					UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
				synchronized (this) {
					doBindService();

					DeferredUsbIntent di = new DeferredUsbIntent();
					di.device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
					di.action = action;
					di.extrapermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);

					if (mService == null) {
						mDeferredIntents.add(di);
                    }
					else {
						doSendDeferredIntent(di);
                    }

                    Toast.makeText(mContext, action + " " + di.device.toString(), Toast.LENGTH_SHORT);
				}
			}
		}
    };

	private void doSendDeferredIntent(DeferredUsbIntent i) {
		Message msg;

		Bundle b = new Bundle();

		Log.d(TAG, "Sending deferred intent " + i.action);

		msg = Message.obtain(null, PcapService.MSG_USBINTENT);

		b.putParcelable("DEVICE", i.device);
		b.putString("ACTION", i.action);
		b.putBoolean("EXTRA", i.extrapermission);

		msg.setData(b);

		try {
			mService.send(msg);
		} catch (RemoteException e) {
			// nothing
		}
	}
}
