package edu.buffalo.cse.pocketsniffer.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import edu.buffalo.cse.pocketsniffer.R;
import edu.buffalo.cse.pocketsniffer.interfaces.AsyncTaskListener;
import edu.buffalo.cse.pocketsniffer.interfaces.Constants;
import edu.buffalo.cse.pocketsniffer.interfaces.Refreshable;
import edu.buffalo.cse.pocketsniffer.interfaces.Station;
import edu.buffalo.cse.pocketsniffer.tasks.SnifTask;
import edu.buffalo.cse.pocketsniffer.utils.OUI;
import edu.buffalo.cse.pocketsniffer.utils.Utils;

public class DeviceFragment extends Fragment implements Constants, Refreshable {

    private static final String TAG = Utils.getTag(DeviceFragment.class);

    private List<Station> mListData;
    private ListViewAdapter mAdapter;
    private LayoutInflater mInflater;
    private Context mContext;
    private WifiManager mWifiManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mListData = new ArrayList<Station>();
        mAdapter = new ListViewAdapter();
        mContext = getActivity();
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
    }


    private void updateListData() {
        SharedPreferences sharedPrefrences = PreferenceManager.getDefaultSharedPreferences(mContext);

        List<Integer> channels = new ArrayList<Integer>();
        for (String chan : sharedPrefrences.getStringSet(getString(R.string.pref_key_channel_2GHz), new HashSet<String>())) {
            channels.add(Integer.parseInt(chan));
        }
        for (String chan : sharedPrefrences.getStringSet(getString(R.string.pref_key_channel_5GHz), new HashSet<String>())) {
            channels.add(Integer.parseInt(chan));
        }

        int channelDwellTime = Integer.parseInt(sharedPrefrences.getString(getString(R.string.pref_key_channel_dwell_time), String.valueOf(DEFAULT_CHANNEL_DWELL_TIME)));
        int channelPacketCount = Integer.parseInt(sharedPrefrences.getString(getString(R.string.pref_key_channel_packet_count), String.valueOf(DEFAULT_CHANNEL_PACKET_COUNT)));

        final ProgressDialog dialog = new ProgressDialog(mContext);
        dialog.setMax(channels.size());
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);

        SnifTask.Params params = new SnifTask.Params(channels, channelDwellTime, channelPacketCount);
        final SnifTask task = new SnifTask(mContext, new AsyncTaskListener<SnifTask.Params, SnifTask.Progress, SnifTask.Result>() {

            @Override
            public void onCancelled(SnifTask.Result result) {
                dialog.dismiss();
            }

            @Override
            public void onPostExecute(SnifTask.Result result) {
                dialog.dismiss();
            }

            @Override
            public void onPreExecute() {
                mListData.clear();
                mAdapter.notifyDataSetChanged();
                dialog.show();
            }

            @Override
            public void onProgressUpdate(SnifTask.Progress... progresses) {
                SnifTask.Progress progress = progresses[0];
                int chan = progress.channelFinished;

                dialog.setMessage("Finished channel " + chan);
                dialog.incrementProgressBy(1);

                for (Station s : progress.partialResult.channelStation.get(chan)) {
                    if (!s.isAP) {
                        mListData.add(s);
                    }
                }
                Collections.sort(mListData, new Comparator<Station>() {

                    @Override
                    public int compare(Station lhs, Station rhs) {
                        if (lhs.SSID != null && rhs.SSID == null) {
                            return -1;
                        }
                        if (lhs.SSID == null && rhs.SSID != null) {
                            return 1;
                        }
                        if (lhs.SSID != null && rhs.SSID != null && !lhs.SSID.equals(rhs.SSID)) {
                            return lhs.SSID.compareTo(rhs.SSID);
                        }
                        if (lhs.freq != rhs.freq) {
                            return (new Integer(lhs.freq)).compareTo(rhs.freq);
                        }
                        return lhs.mac.compareTo(rhs.mac);
                    }

                });
                mAdapter.notifyDataSetChanged();
            }

        });

        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                task.cancel(true);
            }
        });


        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);

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
    }

    @Override
    public void refresh() {
        updateListData();
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
            Station station = mListData.get(position);

            TextView tv = (TextView) convertView.findViewById(R.id.deviceMac);
            tv.setText(station.mac);

            tv = (TextView) convertView.findViewById(R.id.deviceManufacturer);
            tv.setText(OUI.lookup(station.mac)[1]);

            StringBuilder sb = new StringBuilder();
            sb.append("CH ");
            try {
                sb.append(String.valueOf(Utils.freqToChannel(station.freq)));
            }
            catch (Exception e) {
                sb.append(UNKNOWN);
            }
            sb.append(" (" + station.freq + " MHz)");
            sb.append(" " + station.getAvgRSSI() + " dBm");

            tv = (TextView) convertView.findViewById(R.id.deviceInfo);
            tv.setText(sb.toString());

            tv = (TextView) convertView.findViewById(R.id.deviceAP);
            if (station.SSID != null) {
                tv.setText("Connected to " + station.SSID);
                tv.setVisibility(View.VISIBLE);
            }
            else {
                tv.setVisibility(View.GONE);
            }
            
            return convertView;
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                long id) {
        }
    }
}
