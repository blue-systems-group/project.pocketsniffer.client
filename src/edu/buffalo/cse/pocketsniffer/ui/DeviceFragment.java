package edu.buffalo.cse.pocketsniffer.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;

import android.app.Fragment;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;

import edu.buffalo.cse.pocketsniffer.R;
import edu.buffalo.cse.pocketsniffer.interfaces.Refreshable;
import edu.buffalo.cse.pocketsniffer.tasks.ProximityTask;
import edu.buffalo.cse.pocketsniffer.tasks.ProximityTask.BluetoothDeviceInfo;
import edu.buffalo.cse.pocketsniffer.utils.LocalUtils;


public class DeviceFragment extends Fragment implements Refreshable {

    private static final String TAG = LocalUtils.getTag(DeviceFragment.class);

    public static final String ACTION_INTERESTED_DEVICE_CHANGED = DeviceFragment.class.getName() + ".InterestedDeviceChanged";
    public static final String EXTRA_DEVICE_MAC = DeviceFragment.class.getName() + ".DeviceMAC";
    public static final String EXTRA_IS_INTERESTED = DeviceFragment.class.getName() + ".IsInterested";

    private static final String PROXIMITYTASK_CHECK_INTENT_NAME = "edu.buffalo.cse.pocketsniffer.tasks.ProximityTask.Check";

    private List<BluetoothDeviceInfo> mListData;
    private ListViewAdapter mAdapter;
    private LayoutInflater mInflater;
    private Context mContext;
    private ProgressDialog mDialog;

    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            Log.d(TAG, "Intent fired, action is " + action);

            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                mDialog.dismiss();
            }
            else if (ProximityTask.ACTION_NEIGHBOR_UPDATED.equals(action)) {
                updateListData(intent);
            }
        }
    };

    private void startDiscovery() {
        mContext.sendBroadcast(new Intent(PROXIMITYTASK_CHECK_INTENT_NAME));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mListData = new ArrayList<BluetoothDeviceInfo>();
        mAdapter = new ListViewAdapter();
        mContext = getActivity();
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mDialog = new ProgressDialog(mContext);
        mDialog.setCancelable(false);
        mDialog.setCanceledOnTouchOutside(false);
        mDialog.setMessage("Scanning...");

        refresh();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ProximityTask.ACTION_NEIGHBOR_UPDATED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        mContext.registerReceiver(mBluetoothReceiver, filter);
    }


    private void updateListData(Intent intent) {
        mListData = new ArrayList<BluetoothDeviceInfo>();

        try {
            JSONArray array = new JSONArray(intent.getStringExtra(ProximityTask.EXTRA_NEIGHBOR_DEVICES));
            for (int i = 0; i < array.length(); i++) {
                try {
                    mListData.add(BluetoothDeviceInfo.fromJSONObject(array.getJSONObject(i)));
                }
                catch (JSONException e) {
                    Log.e(TAG, "Failed to decode bluetooth device info.", e);
                }
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to parse neighbor info.", e);
            return;
        }

        Collections.sort(mListData, new Comparator<BluetoothDeviceInfo>() {

            @Override
            public int compare(BluetoothDeviceInfo lhs, BluetoothDeviceInfo rhs) {
                if (lhs.interested != rhs.interested) {
                    return lhs.interested? 1: -1;
                }
                return lhs.name.compareTo(rhs.name);
            }
        });
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.device, container, false);
        ListView lv = (ListView) view.findViewById(R.id.deviceListView);
        lv.setAdapter(mAdapter);
        lv.setOnItemClickListener(mAdapter);

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mContext.unregisterReceiver(mBluetoothReceiver);
    }

    @Override
    public void refresh() {
        startDiscovery();
        mDialog.show();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    final class ListViewAdapter extends BaseAdapter implements AdapterView.OnItemClickListener {

        @Override
        public int getCount() {
            return mListData.size();
        }

        @Override
        public Object getItem(int position) {
            return mListData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.device_list_item, null);
            }
            BluetoothDeviceInfo info = mListData.get(position);

            TextView tv = (TextView) convertView.findViewById(R.id.deviceName);
            tv.setText(info.name);

            tv = (TextView) convertView.findViewById(R.id.deviceMac);
            tv.setText("Bluetooth MAC: " + info.mac);

            tv = (TextView) convertView.findViewById(R.id.deviceRSSI);
            tv.setText("Signal Strength: " + info.rssi + " dBm");

            tv = (TextView) convertView.findViewById(R.id.lastSeen);
            tv.setText("Last Seen: " + DateUtils.getRelativeTimeSpanString(info.lastSeen, System.currentTimeMillis(), 0));

            CheckBox checkBox = (CheckBox) convertView.findViewById(R.id.interested);
            checkBox.setChecked(info.interested);
            checkBox.setTag(info);
            checkBox.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    CheckBox cb = (CheckBox) v;
                    BluetoothDeviceInfo i = (BluetoothDeviceInfo) cb.getTag();
                    i.interested = cb.isChecked();

                    Log.d(TAG, "Button for " +  i.mac + " clicked, checked = " + cb.isChecked());

                    Intent intent = new Intent(ACTION_INTERESTED_DEVICE_CHANGED);
                    intent.putExtra(EXTRA_IS_INTERESTED, cb.isChecked());
                    intent.putExtra(EXTRA_DEVICE_MAC, i.mac);
                    mContext.sendBroadcast(intent);
                }
            });

            return convertView;
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            CheckBox checkBox = (CheckBox) parent.findViewById(R.id.interested);
            String mac = mListData.get(position).mac;
            Log.d(TAG, "Button for " +  mac + " clicked, checked = " + checkBox.isChecked());

            Intent intent = new Intent(ACTION_INTERESTED_DEVICE_CHANGED);
            intent.putExtra(EXTRA_IS_INTERESTED, checkBox.isChecked());
            intent.putExtra(EXTRA_DEVICE_MAC, mac);
            mContext.sendBroadcast(intent);
        }
    }
}
