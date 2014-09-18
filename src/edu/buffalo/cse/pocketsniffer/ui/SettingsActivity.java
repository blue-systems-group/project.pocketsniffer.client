package edu.buffalo.cse.pocketsniffer.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.widget.Toast;

import edu.buffalo.cse.phonelab.toolkit.android.utils.Utils;
import edu.buffalo.cse.pocketsniffer.R;

public class SettingsActivity extends PreferenceActivity {
    private Preference.OnPreferenceChangeListener mCheckNumberListener = new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                try {
                    int interval = Integer.parseInt((String) newValue);
                    if (interval == 0) {
                        throw new Exception();
                    }
                }
                catch (Exception e) {
                    Toast.makeText(SettingsActivity.this, "Invalid input.", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);
        getPreferenceManager().findPreference(getString(R.string.pref_key_scan_interval)).setOnPreferenceChangeListener(mCheckNumberListener);
        getPreferenceManager().findPreference(getString(R.string.pref_key_channel_dwell_time)).setOnPreferenceChangeListener(mCheckNumberListener);
        getPreferenceManager().findPreference(getString(R.string.pref_key_channel_packet_count)).setOnPreferenceChangeListener(mCheckNumberListener);

        SharedPreferences sharedPrefrences = PreferenceManager.getDefaultSharedPreferences(this);

        List<String> values = new ArrayList<String>();

        MultiSelectListPreference channel2GHz = (MultiSelectListPreference) getPreferenceManager().findPreference(getString(R.string.pref_key_channel_2GHz));
        values.clear();
        for (int i = 1; i <= 11; i += 5) {
            values.add(i + " (" + Utils.channelToFreq(i) + " MHz)");
        }
        channel2GHz.setEntries(values.toArray(new String[]{}));
        values.clear();
        for (int i = 1; i <= 11; i += 5) {
            values.add(i + "");
        }
        channel2GHz.setEntryValues(values.toArray(new String[]{}));
        if (sharedPrefrences.getStringSet(getString(R.string.pref_key_channel_2GHz), null) == null) {
            channel2GHz.setValues(new HashSet<String>(values));
        }

        MultiSelectListPreference channel5GHz = (MultiSelectListPreference) getPreferenceManager().findPreference(getString(R.string.pref_key_channel_5GHz));
        values.clear();
        for (int i = 36; i <= 48; i += 4) {
            values.add(i + " (" + Utils.channelToFreq(i) + " MHz)");
        }
        for (int i = 149; i <= 165; i += 4) {
            values.add(i + " (" + Utils.channelToFreq(i) + " MHz)");
        }
        channel5GHz.setEntries(values.toArray(new String[]{}));
        values.clear();
        for (int i = 36; i <= 48; i += 4) {
            values.add(i + "");
        }
        for (int i = 149; i <= 165; i += 4) {
            values.add(i + "");
        }
        channel5GHz.setEntryValues(values.toArray(new String[]{}));
        if (sharedPrefrences.getStringSet(getString(R.string.pref_key_channel_5GHz), null) == null) {
            channel5GHz.setValues(new HashSet<String>(values));
        }
    }
}
