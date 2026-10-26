package com.lanbeam.core;

/** Another LanBeam device seen on the network. */
public final class Peer {
    public final String id;
    public final String name;
    public final String os;
    public final String host;
    public final int port;
    volatile long lastSeen;

    public Peer(String id, String name, String os, String host, int port) {
        this.id = id;
        this.name = name;
        this.os = os;
        this.host = host;
        this.port = port;
        this.lastSeen = System.currentTimeMillis();
    }

    boolean sameAs(Peer o) {
        return o != null && id.equals(o.id) && name.equals(o.name) && host.equals(o.host) && port == o.port;
    }

    public String baseUrl() {
        return "http://" + host + ":" + port;
    }

    @Override public String toString() {
        return name + "  (" + os + ", " + host + ")";
    }
}
