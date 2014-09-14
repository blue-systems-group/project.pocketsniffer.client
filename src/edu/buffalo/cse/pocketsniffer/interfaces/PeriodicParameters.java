package edu.buffalo.cse.pocketsniffer.interfaces;

import org.simpleframework.xml.Element;

import android.app.AlarmManager;

import edu.buffalo.cse.pocketsniffer.utils.Utils;

public abstract class PeriodicParameters {

    protected final String TAG = Utils.getTag(this.getClass());

    @Element
    public Long checkInterval;

    @Element(required=false)
    public Boolean disabled;

    public PeriodicParameters() {
        this.checkInterval = AlarmManager.INTERVAL_HOUR / 1000;
        this.disabled = false;
    }

    public PeriodicParameters(PeriodicParameters parameters) {
        this.checkInterval = parameters.checkInterval;
        this.disabled = parameters.disabled;
    }

    @Override
    public String toString() {
        return Utils.dumpFields(this);
    }

    @Override
    public int hashCode() {
        return Utils.computeHash(this);
    }
    @Override
    public boolean equals(Object obj) {
        return Utils.objectEquals(this, obj);
    }
}
