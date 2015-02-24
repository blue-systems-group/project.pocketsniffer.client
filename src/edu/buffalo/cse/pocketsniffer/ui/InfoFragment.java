package edu.buffalo.cse.pocketsniffer.ui;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Fragment;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import edu.buffalo.cse.phonelab.toolkit.android.utils.Utils;
import edu.buffalo.cse.pocketsniffer.R;
import edu.buffalo.cse.pocketsniffer.utils.LocalUtils;

public class InfoFragment extends Fragment {

    private static final String UNKNOWN = "Unknown";
    private static final String TAG = LocalUtils.getTag(InfoFragment.class);

    private static final String PLATFORM_VERISON = "Platform Version";
    private static final String KERNEL_VERSION = "Kernel Version";
    private static final String MONITOR_MODE_SUPPORT = "Monitor Mode Support";
    private static final String MAC_ADDRESS = "Wifi MAC Address";
    private static final String APP_VERSION = "Application Version";
    private static final String SERIAL = "Serial Number";

    private List<String> mListTitle;
    private Map<String, String> mListData;
    private ListViewAdapter mAdapter;
    private LayoutInflater mInflater;
    private Context mContext;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mListTitle = new ArrayList<String>();
        mListData = new HashMap<String, String>();
        mAdapter = new ListViewAdapter();
        mContext = getActivity();
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        updateListData();
    }


    private void updateListData() {


        List<SimpleEntry<String, String>> entries = new ArrayList<SimpleEntry<String, String>>();

        try {
            entries.add(new SimpleEntry<String, String>(APP_VERSION, Utils.getVersionName(mContext) + " (" + Utils.getVersionCode(mContext) + ")"));
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to get application version info.", e);
            entries.add(new SimpleEntry<String, String>(APP_VERSION, UNKNOWN));
        }
        entries.add(new SimpleEntry<String, String>(SERIAL, Build.SERIAL));

        entries.add(new SimpleEntry<String, String>(MONITOR_MODE_SUPPORT, Utils.isPhoneLabDevice(mContext)? "Yes": "No"));
        entries.add(new SimpleEntry<String, String>(PLATFORM_VERISON, Build.DISPLAY));
        entries.add(new SimpleEntry<String, String>(KERNEL_VERSION, System.getProperty("os.version")));
        try {
            entries.add(new SimpleEntry<String, String>(MAC_ADDRESS, Utils.getMacAddress("wlan0")));
        }
        catch (Exception e) {
        }

        mListTitle.clear();
        mListData.clear();
        for (SimpleEntry<String, String> entry : entries) {
            mListTitle.add(entry.getKey());
            mListData.put(entry.getKey(), entry.getValue());
        }
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.info, container, false);
        ListView lv = (ListView) view.findViewById(R.id.infoListView);
        lv.setAdapter(mAdapter);
        lv.setOnItemClickListener(mAdapter);

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    final class ListViewAdapter extends BaseAdapter implements AdapterView.OnItemClickListener {

        @Override
        public int getCount() {
            return mListTitle.size();
        }

        @Override
        public Object getItem(int position) {
            return mListTitle.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.title_value_list_item, null);
            }
            String title = mListTitle.get(position);
            TextView tv = (TextView) convertView.findViewById(R.id.title);
            tv.setText(title);
            tv = (TextView) convertView.findViewById(R.id.value);
            tv.setText(mListData.get(title));

            return convertView;
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        }
    }
}
