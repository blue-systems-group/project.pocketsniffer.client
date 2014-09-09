package edu.buffalo.cse.pocketsniffer.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import edu.buffalo.cse.pocketsniffer.R;
import edu.buffalo.cse.pocketsniffer.interfaces.Refreshable;
import edu.buffalo.cse.pocketsniffer.utils.OUI;
import edu.buffalo.cse.pocketsniffer.utils.Utils;

public class APFragment extends Fragment implements Refreshable {

    private static final String TAG = Utils.getTag(APFragment.class);

    private List<ScanResult> mListData;
    private ListViewAdapter mAdapter;
    private LayoutInflater mInflater;
    private Context mContext;
    private WifiManager mWifiManager;
    private ProgressDialog mDialog;

    private BroadcastReceiver mScanResultReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Intent fired, action is " + intent.getAction());
            updateListData();
            mDialog.dismiss();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mListData = new ArrayList<ScanResult>();
        mAdapter = new ListViewAdapter();
        mContext = getActivity();
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);

        mDialog = new ProgressDialog(mContext);
        mDialog.setCancelable(false);
        mDialog.setCanceledOnTouchOutside(false);
        mDialog.setMessage("Scanning...");

        updateListData();

        mContext.registerReceiver(mScanResultReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }


    private void updateListData() {
        mListData = mWifiManager.getScanResults();
        Collections.sort(mListData, new Comparator<ScanResult>() {

            @Override
            public int compare(ScanResult lhs, ScanResult rhs) {
                if (lhs.frequency != rhs.frequency) {
                    return new Integer(lhs.frequency).compareTo(rhs.frequency);
                }
                return lhs.SSID.compareTo(rhs.SSID);
            }
        });
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.ap, container, false);
        ListView lv = (ListView) view.findViewById(R.id.apListView);
        lv.setAdapter(mAdapter);
        lv.setOnItemClickListener(mAdapter);

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mContext.unregisterReceiver(mScanResultReceiver);
    }

    @Override
    public void refresh() {
        if (!mWifiManager.isWifiEnabled()) {
            mListData = new ArrayList<ScanResult>();
            mAdapter.notifyDataSetChanged();
            Toast.makeText(mContext, "Wifi is disabled.", Toast.LENGTH_SHORT).show();
        }
        else {
            mWifiManager.startScan();
            mDialog.show();
        }
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
                convertView = mInflater.inflate(R.layout.ap_list_item, null);
            }
            ScanResult result = mListData.get(position);

            TextView tv = (TextView) convertView.findViewById(R.id.apSSID);
            tv.setText(result.SSID);

            tv = (TextView) convertView.findViewById(R.id.apBSSID);
            tv.setText(result.BSSID);

            tv = (TextView) convertView.findViewById(R.id.apManufacturer);
            tv.setText(OUI.lookup(result.BSSID)[1]);


            StringBuilder sb = new StringBuilder();
            sb.append("CH ");
            try {
                sb.append(String.valueOf(Utils.freqToChannel(result.frequency)));
            }
            catch (Exception e) {
                sb.append(OUI.UNKNOWN);
            }
            sb.append(" (" + result.frequency + " MHz)");
            sb.append(" " + result.level + " dBm");

            tv = (TextView) convertView.findViewById(R.id.apInfo);
            tv.setText(sb.toString());

            return convertView;
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                long id) {
        }
    }
}
