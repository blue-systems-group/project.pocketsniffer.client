package edu.buffalo.cse.pocketsniffer.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import edu.buffalo.cse.pocketsniffer.R;
import edu.buffalo.cse.pocketsniffer.interfaces.DeviceInfo;
import edu.buffalo.cse.pocketsniffer.utils.LocalUtils;
import edu.buffalo.cse.pocketsniffer.utils.OUI;


public class DeviceFragment extends Fragment {

    private static final String TAG = LocalUtils.getTag(DeviceFragment.class);

    public static final String ACTION_INTERESTED_DEVICE_CHANGED = DeviceFragment.class.getName() + ".InterestedDeviceChanged";
    public static final String EXTRA_INTERESTED_DEVICES = DeviceFragment.class.getName() + ".InterestedDevices";

    public static final String PREFERENCES_NAME = DeviceFragment.class.getName() + ".Preferences";
    public static final String KEY_INTERESTED_DEVICES = DeviceFragment.class.getName() + ".InterestedDevices";

    private static final String PROXIMITYTASK_CHECK_INTENT_NAME = "edu.buffalo.cse.pocketsniffer.tasks.ProximityTask.Check";

    private List<DeviceInfo> mListData;
    private ListViewAdapter mAdapter;
    private LayoutInflater mInflater;
    private Context mContext;


    private ReentrantLock mPreferenceLock;
    private SharedPreferences mSharedPreferences;

    private Button mAddDeviceButton;
    private View mAddDeviceDialogView;
    private AlertDialog mAddDeviceDialog;
    private EditText mDeviceNameEditText;
    private EditText mMacAddressEditText;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = getActivity();
        mPreferenceLock = new ReentrantLock();

        mAdapter = new ListViewAdapter();
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mSharedPreferences = mContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);

        mAddDeviceDialogView = mInflater.inflate(R.layout.add_device_dialog, null);
        mDeviceNameEditText = (EditText) mAddDeviceDialogView.findViewById(R.id.deviceNameEditText);
        mMacAddressEditText = (EditText) mAddDeviceDialogView.findViewById(R.id.macAddressEditText);

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext, AlertDialog.THEME_HOLO_LIGHT);

        builder.setTitle("Add an interested device");
        builder.setView(mAddDeviceDialogView);
        builder.setPositiveButton("Add", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        mAddDeviceDialog = builder.create();

        recoverDeviceList();
    }


    private void updateListData() {
        Collections.sort(mListData, new Comparator<DeviceInfo>() {

            @Override
            public int compare(DeviceInfo lhs, DeviceInfo rhs) {
                if (lhs.interested != rhs.interested) {
                    return lhs.interested? -1: 1;
                }
                return lhs.name.compareTo(rhs.name);
            }
        });
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.device, null);
        ListView lv = (ListView) view.findViewById(R.id.deviceListView);
        lv.setAdapter(mAdapter);
        lv.setOnItemClickListener(mAdapter);

        mAddDeviceButton = (Button) view.findViewById(R.id.addDevice);
        mAddDeviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Add device button clicked.");
                mDeviceNameEditText.setText("");
                mMacAddressEditText.setText("");
                mAddDeviceDialog.show();
                mAddDeviceDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        if (mMacAddressEditText.getText().length() == 0) {
                            Toast.makeText(mContext, "Mac address can not be empty.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String deviceName = mDeviceNameEditText.getText().toString().trim();
                        String mac = mMacAddressEditText.getText().toString().trim();

                        if (deviceName.length() == 0) {
                            deviceName = mac;
                        }

                        Pattern pattern = Pattern.compile("([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}");
                        Matcher matcher = pattern.matcher(mac);
                        if (!matcher.matches()) {
                            Toast.makeText(mContext, "Mac address is not valid.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        DeviceInfo info = new DeviceInfo();
                        info.name = deviceName;
                        info.mac = mac.toLowerCase();
                        info.manufacturer = OUI.lookup(mac)[0];
                        info.lastSeen = 0L;
                        info.rssi = -1;
                        info.interested = true;

                        mListData.add(info);
                        updateListData();
                        mAddDeviceDialog.dismiss();

                        persistDeviceList(true);
                    }
                });
            }
        });
        return view;
    }

    private void persistDeviceList(boolean broadcast) {
        String extra = null;
        synchronized (mPreferenceLock) {
            try {
                JSONArray array = new JSONArray();
                for (DeviceInfo info : mListData) {
                    array.put(info.toJSONObject());
                }
                SharedPreferences.Editor editor = mSharedPreferences.edit();
                extra = array.toString();
                editor.putString(KEY_INTERESTED_DEVICES, extra);
                editor.commit();
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to commit changes.", e);
                return;
            }
        }
        if (broadcast) {
            Intent intent = new Intent(ACTION_INTERESTED_DEVICE_CHANGED);
            intent.putExtra(EXTRA_INTERESTED_DEVICES, extra);
            mContext.sendBroadcast(intent);
        }
    }

    private void recoverDeviceList() {
        mListData = new ArrayList<DeviceInfo>();
        synchronized (mPreferenceLock) {
            try {
                String s = mSharedPreferences.getString(KEY_INTERESTED_DEVICES, "[]");
                JSONArray array = new JSONArray(s);
                for (int i = 0; i < array.length(); i++) {
                    DeviceInfo info = DeviceInfo.fromJSONObject(array.getJSONObject(i));
                    mListData.add(info);
                }
            }
            catch (Exception e) {
                Log.d(TAG, "Failed to recover device list.");
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        persistDeviceList(false);
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
            DeviceInfo info = mListData.get(position);

            TextView tv = (TextView) convertView.findViewById(R.id.deviceName);
            tv.setText(info.name);

            tv = (TextView) convertView.findViewById(R.id.deviceMac);
            tv.setText("MAC: " + info.mac);

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
                    DeviceInfo i = (DeviceInfo) cb.getTag();
                    i.interested = cb.isChecked();

                    Log.d(TAG, "Button for " +  i.mac + " clicked, checked = " + cb.isChecked());
                    persistDeviceList(true);
                }
            });

            return convertView;
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            CheckBox checkBox = (CheckBox) parent.findViewById(R.id.interested);
            DeviceInfo info = mListData.get(position);
            Log.d(TAG, "Button for " +  info.mac + " clicked, checked = " + checkBox.isChecked());

            info.interested = checkBox.isChecked();
            persistDeviceList(true);
        }
    }
}
