package edu.buffalo.cse.pocketsniffer.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.TextView;

import edu.buffalo.cse.phonelab.toolkit.android.utils.Utils;
import edu.buffalo.cse.pocketsniffer.R;
import edu.buffalo.cse.pocketsniffer.interfaces.AsyncTaskListener;
import edu.buffalo.cse.pocketsniffer.interfaces.Constants;
import edu.buffalo.cse.pocketsniffer.interfaces.Refreshable;
import edu.buffalo.cse.pocketsniffer.interfaces.Station;
import edu.buffalo.cse.pocketsniffer.interfaces.TrafficFlow;
import edu.buffalo.cse.pocketsniffer.tasks.SnifTask;
import edu.buffalo.cse.pocketsniffer.utils.LocalUtils;
import edu.buffalo.cse.pocketsniffer.utils.OUI;

public class DeviceFragment extends Fragment implements Constants, Refreshable {

    private static final String TAG = LocalUtils.getTag(DeviceFragment.class);

    private List<String> mListHeader;
    private Map<String, List<String>> mListData;
    private Map<String, Station> mStations;
    private Map<String, TrafficFlow> mTraffics;
    private ExpandableListViewAdapter mAdapter;
    private LayoutInflater mInflater;
    private Context mContext;
    private WifiManager mWifiManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mListHeader = new ArrayList<String>();
        mListData = new HashMap<String, List<String>>();

        mStations = new HashMap<String, Station>();
        mTraffics = new HashMap<String, TrafficFlow>();

        mAdapter = new ExpandableListViewAdapter();
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
                mListHeader.clear();
                mListData.clear();
                mStations.clear();
                mTraffics.clear();

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
                    mStations.put(s.getKey(), s);
                }
                for (TrafficFlow t : progress.partialResult.channelTraffic.get(chan)) {
                    mTraffics.put(t.getKey(), t);
                    Station ap, device;
                    if (t.from.isAP) {
                        ap = t.from;
                        device = t.to;
                    }
                    else {
                        ap = t.to;
                        device = t.from;
                    }
                    if (!mListData.containsKey(ap.getKey())) {
                        mListData.put(ap.getKey(), new ArrayList<String>());
                    }
                    if (!mListData.get(ap.getKey()).contains(device.getKey())) {
                        mListData.get(ap.getKey()).add(device.getKey());
                    }
                }
                mListHeader.clear();
                mListHeader.addAll(mListData.keySet());

                Collections.sort(mListHeader, new Comparator<String>() {

                    @Override
                    public int compare(String lhs, String rhs) {
                        Station s1 = mStations.get(lhs);
                        Station s2 = mStations.get(rhs);
                        if (s1.freq != s2.freq) {
                            return (new Integer(s1.freq)).compareTo(s2.freq);
                        }
                        if (s1.SSID != null && s2.SSID == null) {
                            return -1;
                        }
                        if (s1.SSID == null && s2.SSID != null) {
                            return 1;
                        }
                        if (s1.SSID != null && s2.SSID != null && !s1.SSID.equals(s2.SSID)) {
                            return s1.SSID.compareTo(s2.SSID);
                        }
                        return s1.mac.compareTo(s2.mac);
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
        View view = inflater.inflate(R.layout.traffic, container, false);
        ExpandableListView lv = (ExpandableListView) view.findViewById(R.id.trafficListView);
        lv.setAdapter(mAdapter);
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

    final class ExpandableListViewAdapter extends BaseExpandableListAdapter {

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            String header = mListHeader.get(groupPosition);
            return mListData.get(header).get(childPosition);
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition,
                boolean isLastChild, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.device_list_item, null);
            }
            String header = mListHeader.get(groupPosition);

            Station ap = mStations.get(header);
            Station device = mStations.get(mListData.get(header).get(childPosition));

            TrafficFlow downlink = mTraffics.get(TrafficFlow.getKey(ap, device));
            TrafficFlow uplink = mTraffics.get(TrafficFlow.getKey(device, ap));

            TextView tv = (TextView) convertView.findViewById(R.id.deviceMac);
            tv.setText(device.mac);

            tv = (TextView) convertView.findViewById(R.id.deviceManufacturer);
            tv.setText(OUI.lookup(device.mac)[1]);

            StringBuilder sb = new StringBuilder();
            sb.append("Avg RSSI: ");
            sb.append("<font color=red>" + device.getAvgRSSI() + " dBm</font>");
            if (downlink != null) {
                sb.append(" Downlink: " + Utils.readableSize(downlink.totalBytes()));
            }
            if (uplink != null) {
                sb.append(" Uplink: " + Utils.readableSize(uplink.totalBytes()));
            }

            tv = (TextView) convertView.findViewById(R.id.deviceInfo);
            tv.setText(Html.fromHtml(sb.toString()));

            return convertView;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            String header = mListHeader.get(groupPosition);
            return mListData.get(header).size();
        }

        @Override
        public Object getGroup(int groupPosition) {
            return mListHeader.get(groupPosition);
        }

        @Override
        public int getGroupCount() {
            return mListHeader.size();
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded,
                View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.ap_list_item, null);
            }

            String header = mListHeader.get(groupPosition);
            Station ap = mStations.get(header);

            TextView tv = (TextView) convertView.findViewById(R.id.apSSID);
            tv.setText(ap.SSID);

            tv = (TextView) convertView.findViewById(R.id.apBSSID);
            tv.setText(ap.mac);

            tv = (TextView) convertView.findViewById(R.id.apManufacturer);
            tv.setText(OUI.lookup(ap.mac)[1]);

            StringBuilder sb = new StringBuilder();
            sb.append("<font color=blue>CH ");
            try {
                sb.append(String.valueOf(Utils.freqToChannel(ap.freq)));
            }
            catch (Exception e) {
                sb.append(OUI.UNKNOWN);
            }
            sb.append(" (" + ap.freq + " MHz)</font>");
            sb.append(" " + ap.getAvgRSSI() + " dBm");
            sb.append(" " + mListData.get(header).size() + " devices");

            tv = (TextView) convertView.findViewById(R.id.apInfo);
            tv.setText(Html.fromHtml(sb.toString()));

            return convertView;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return false;
        }
    }
}
