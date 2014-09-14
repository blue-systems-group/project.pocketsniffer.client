package edu.buffalo.cse.pocketsniffer.interfaces;

public interface UploaderClient {
    public void prepare();
    public boolean hasNext();
    public long bytesAvailable();
    public int filesAvailable();
    public UploaderFileDescription next();
    public void complete(UploaderFileDescription uploaderFileDescription, boolean success);
}
