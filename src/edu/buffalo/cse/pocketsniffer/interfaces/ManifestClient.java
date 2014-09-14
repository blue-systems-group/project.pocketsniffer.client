package edu.buffalo.cse.pocketsniffer.interfaces;

public interface ManifestClient {
    public boolean parametersUpdated(String manifestString);
    public String getState();
}
