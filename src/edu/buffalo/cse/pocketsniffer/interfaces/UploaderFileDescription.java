package edu.buffalo.cse.pocketsniffer.interfaces;

import java.io.File;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

import edu.buffalo.cse.pocketsniffer.util.Util;

@Root(name="UploaderFileDescription")
public class UploaderFileDescription {
    @Element
    public String src;

    @Element
    public String filename;

    @Element
    public String packagename;

    @Element
    public long completedTimeStamp;

    public long len;
    public UploaderClient uploader;

    public UploaderFileDescription(@Element (name = "src") String src,
            @Element (name = "filename") String filename,
            @Element (name = "packagename") String packagename) throws Exception {
        super();
        this.src = src;
        if (this.exists() == false) {
            throw new Exception("file does not exist");
        }
        this.filename = filename;
        this.packagename = packagename;
        this.len = new File(this.src).length();
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

    public boolean exists() {
        File file = new File(this.src);
        if ((file.exists() == false) || (file.isFile() == false) || (file.canRead() == false)) {
            return false;
        } else {
            return true;
        }
    }
}
