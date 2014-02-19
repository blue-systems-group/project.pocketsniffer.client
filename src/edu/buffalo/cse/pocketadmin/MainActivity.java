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
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import net.kismetwireless.android.pcapcapture.PcapService;
import net.kismetwireless.android.pcapcapture.UsbSource;

public class MainActivity extends Activity {
    private final String TAG = "PocketAdmin-" + this.getClass().getSimpleName();

    private boolean mIsBound = false;
	private Messenger mService = null;
	private Context mContext;
	private ArrayList<DeferredUsbIntent> mDeferredIntents = new ArrayList<DeferredUsbIntent>();

	public class DeferredUsbIntent {
		UsbDevice device;
		String action;
		boolean extrapermission;
	};



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        mContext = this;

        Intent intent = new Intent(MainActivity.this, AdminService.class);
        mContext.startService(intent);

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

			if (PcapService.ACTION_USB_PERMISSION.equals(action) ||
					UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action) ||
					UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
				synchronized (this) {
					doBindService();

					DeferredUsbIntent di = new DeferredUsbIntent();
					di.device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
					di.action = action;
					di.extrapermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);

					if (mService == null)
						mDeferredIntents.add(di);
					else
						doSendDeferredIntent(di);
				}
			}
		}
    };

	private void doSendDeferredIntent(DeferredUsbIntent i) {
		Message msg;

		Bundle b = new Bundle();

		// Toast.makeText(mContext, "Sending deferred intent", Toast.LENGTH_SHORT).show();

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
