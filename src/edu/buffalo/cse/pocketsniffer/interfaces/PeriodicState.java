package edu.buffalo.cse.pocketsniffer.periodictask;

import java.util.Date;

import org.simpleframework.xml.Element;

import edu.buffalo.cse.pocketsniffer.util.Util;

public class PeriodicState {

    @Element
    Date started;

    @Element
    Date restarted;

    @Element
    Date lastCheck;

    @Element
    Date parameterUpdate;

    @Element(required=false)
    PeriodicParameters parameters;

    public PeriodicState() {
        this.started = new Date(0L);
        this.restarted = new Date(0L);
        this.lastCheck = new Date(0L);
        this.parameterUpdate = new Date(0L);
        this.parameters = null;
    }

    @Override
    public String toString() {
        return Util.dumpFields(this);
    }

    @Override
    public int hashCode() {
        return Util.computeHash(this);
    }

    @Override
    public boolean equals(Object obj) {
        return Util.objectEquals(this, obj);
    }
}
