package com.lanbeam.core;

public final class Protocol {
    private Protocol() {}

    public static final int VERSION = 1;
    public static final String APP = "lanbeam";
    public static final int HTTP_PORT = 8080;
    public static final int HTTP_PORT_ATTEMPTS = 10;
    public static final int DISCOVERY_PORT = 45454;
    public static final long ANNOUNCE_INTERVAL_MS = 2000;
    public static final long PEER_TIMEOUT_MS = 7000;

    /** Copy buffer: large chunks keep syscalls low so a single TCP stream saturates the link. */
    public static final int COPY_BUFFER = 1024 * 1024;
    public static final int SOCKET_BUFFER = 1024 * 1024;
    public static final long PROGRESS_INTERVAL_MS = 250;
}
