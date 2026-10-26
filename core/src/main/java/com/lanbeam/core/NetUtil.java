package com.lanbeam.core;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NetUtil {
    private NetUtil() {}

    public static final class LocalAddress {
        public final String iface;
        public final Inet4Address address;
        public final Inet4Address broadcast;
        public final int prefix;

        LocalAddress(String iface, Inet4Address address, Inet4Address broadcast, int prefix) {
            this.iface = iface;
            this.address = address;
            this.broadcast = broadcast;
            this.prefix = prefix;
        }

        boolean sameSubnet(Inet4Address other) {
            int mask = prefix == 0 ? 0 : -1 << (32 - prefix);
            return (toInt(address) & mask) == (toInt(other) & mask);
        }

        @Override public String toString() { return address.getHostAddress() + " (" + iface + ")"; }
    }

    /** All non-loopback, up IPv4 addresses, private-LAN ones first and virtual adapters last. */
    public static List<LocalAddress> localAddresses() {
        List<LocalAddress> out = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    if (!(ia.getAddress() instanceof Inet4Address)) continue;
                    Inet4Address a = (Inet4Address) ia.getAddress();
                    if (a.isLinkLocalAddress()) continue;
                    Inet4Address b = ia.getBroadcast() instanceof Inet4Address ? (Inet4Address) ia.getBroadcast() : null;
                    out.add(new LocalAddress(ni.getName() + " " + safe(ni.getDisplayName()), a, b, ia.getNetworkPrefixLength()));
                }
            }
        } catch (Exception ignored) {
        }
        Collections.sort(out, (x, y) -> score(y) - score(x));
        return out;
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static int score(LocalAddress a) {
        int s = 0;
        if (a.address.isSiteLocalAddress()) s += 10;
        String n = a.iface.toLowerCase();
        if (n.contains("virtual") || n.contains("vmware") || n.contains("vbox") || n.contains("hyper-v")
                || n.contains("vethernet") || n.contains("wsl") || n.contains("docker") || n.contains("tap")
                || n.contains("tun") || n.contains("vpn") || n.contains("rmnet")) s -= 20;
        return s;
    }

    /** Best local address to give to a peer at the given IP (same subnet if possible). */
    public static String hostFor(String peerIp) {
        List<LocalAddress> all = localAddresses();
        try {
            InetAddress p = InetAddress.getByName(peerIp);
            if (p instanceof Inet4Address) {
                for (LocalAddress a : all) if (a.sameSubnet((Inet4Address) p)) return a.address.getHostAddress();
            }
        } catch (Exception ignored) {
        }
        return all.isEmpty() ? "127.0.0.1" : all.get(0).address.getHostAddress();
    }

    public static String primaryHost() {
        List<LocalAddress> all = localAddresses();
        return all.isEmpty() ? "127.0.0.1" : all.get(0).address.getHostAddress();
    }

    static int toInt(Inet4Address a) {
        byte[] b = a.getAddress();
        return ((b[0] & 0xff) << 24) | ((b[1] & 0xff) << 16) | ((b[2] & 0xff) << 8) | (b[3] & 0xff);
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] u = {"KB", "MB", "GB", "TB"};
        double v = bytes;
        int i = -1;
        do { v /= 1024; i++; } while (v >= 1024 && i < u.length - 1);
        return String.format(java.util.Locale.ROOT, v >= 100 ? "%.0f %s" : "%.1f %s", v, u[i]);
    }

    public static String formatSpeed(double bytesPerSec) {
        return formatSize((long) bytesPerSec) + "/s";
    }
}
