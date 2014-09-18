package edu.buffalo.cse.pocketsniffer.ui;

import java.util.ArrayList;
import java.util.List;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;

import edu.buffalo.cse.pocketsniffer.R;
import edu.buffalo.cse.pocketsniffer.interfaces.Refreshable;
import edu.buffalo.cse.pocketsniffer.services.SnifferService;
import edu.buffalo.cse.pocketsniffer.utils.LocalUtils;
import edu.buffalo.cse.pocketsniffer.utils.OUI;

public class MainActivity extends Activity {

    private static final String TAG = LocalUtils.getTag(MainActivity.class); 
    private static final String KEY_TAB = "tab";

    private Context mContext;
    private ActionBar mActionBar;
    private ViewPager mViewPager;
    private TabsAdapter mTabsAdapter;

    private List<TabInfo> mTabInfos;

    class TabInfo {
        public String title;
        public Class<?> cls;
        public Object instance;

        public TabInfo(String title, Class<?> cls) {
            this.title = title;
            this.cls = cls;
            instance = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        super.onCreate(savedInstanceState);

        mContext = this;
        mActionBar = getActionBar();

        mViewPager = new ViewPager(this);
        mViewPager.setId(R.id.pager);
        setContentView(mViewPager);

        mTabInfos = new ArrayList<TabInfo>();
        mTabInfos.add(new TabInfo("Info", InfoFragment.class));
        mTabInfos.add(new TabInfo("Access Points", APFragment.class));
        mTabInfos.add(new TabInfo("Devices", DeviceFragment.class));

        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        mTabsAdapter = new TabsAdapter(getFragmentManager());
        for (TabInfo info : mTabInfos) {
            mTabsAdapter.addTab(mActionBar.newTab().setText(info.title), info);
        }
        mViewPager.setAdapter(mTabsAdapter);
        mViewPager.setOnPageChangeListener(mTabsAdapter);

        try {
            OUI.initDB(this);
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to initialized OUI DB.", e);
        }

        startService(new Intent(this, SnifferService.class));

        if (savedInstanceState != null) {
            mActionBar.setSelectedNavigationItem(savedInstanceState.getInt(KEY_TAB, 0));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings :
                Log.d(TAG, "Settings button clicked.");
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                break;
            case R.id.action_refresh :
                Log.d(TAG, "Refresh button clicked.");
                TabInfo info = mTabInfos.get(getActionBar().getSelectedNavigationIndex());
                if (info.instance != null && info.instance instanceof Refreshable) {
                    ((Refreshable) info.instance).refresh();
                }
                break;
        }
        return true;
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_TAB, mActionBar.getSelectedNavigationIndex());
    }


    final class TabsAdapter extends FragmentPagerAdapter implements ActionBar.TabListener, ViewPager.OnPageChangeListener {

        public TabsAdapter(FragmentManager fm) {
            super(fm);
        }

        public void addTab(Tab tab, TabInfo info) {
            tab.setTag(info);
            tab.setTabListener(this);
            mActionBar.addTab(tab);
            notifyDataSetChanged();
        }


        @Override
        public void onTabReselected(Tab tab, FragmentTransaction ft) {
        }

        @Override
        public void onTabSelected(Tab tab, android.app.FragmentTransaction ft) {
            mViewPager.setCurrentItem(mTabInfos.indexOf(tab.getTag()));
        }

        @Override
        public void onTabUnselected(Tab tab, android.app.FragmentTransaction ft) {
        }

        @Override
        public int getCount() {
            return mTabInfos.size();
        }

        @Override
        public Fragment getItem(int position) {
            TabInfo info = mTabInfos.get(position);
            info.instance = Fragment.instantiate(mContext, info.cls.getName());
            return (Fragment) info.instance;
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            mActionBar.setSelectedNavigationItem(position);
        }

        @Override
        public void onPageScrollStateChanged(int state) {
        }
    }
}
