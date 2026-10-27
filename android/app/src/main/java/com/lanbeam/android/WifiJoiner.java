package com.lanbeam.android;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.RouteInfo;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;

import java.net.Inet4Address;
import java.net.InetAddress;

/**
 * Joins the sender's local-only hotspot (Android 10+) and binds the app to it,
 * so the normal HTTP receiver works without a router.
 */
final class WifiJoiner {
    interface Callback {
        /** gateway = the hotspot owner's address (the sender), or null if unknown. */
        void onJoined(String gateway);

        void onFailed(String reason);
    }

    /** Long enough to approve the system "connect to this network" prompt; short enough not to look stuck. */
    static final int TIMEOUT_MS = 30000;

    private final ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback callback;

    WifiJoiner(Context ctx) {
        cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    static boolean supported() {
        return Build.VERSION.SDK_INT >= 29;
    }

    @TargetApi(29)
    void join(String ssid, String pass, final Callback cb) {
        WifiNetworkSpecifier.Builder spec = new WifiNetworkSpecifier.Builder().setSsid(ssid);
        if (pass != null && !pass.isEmpty()) spec.setWpa2Passphrase(pass);
        NetworkRequest req = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(spec.build())
                .build();
        callback = new ConnectivityManager.NetworkCallback() {
            boolean done;

            @Override public void onAvailable(Network network) {
                if (done) return;
                done = true;
                cm.bindProcessToNetwork(network);
                cb.onJoined(gateway(network));
            }

            @Override public void onUnavailable() {
                if (done) return;
                done = true;
                cb.onFailed("Ağa bağlanılamadı (" + ssid + ")");
            }
        };
        cm.requestNetwork(req, callback, TIMEOUT_MS);
    }

    private String gateway(Network network) {
        LinkProperties lp = cm.getLinkProperties(network);
        if (lp == null) return null;
        for (RouteInfo r : lp.getRoutes()) {
            InetAddress g = r.getGateway();
            if (g instanceof Inet4Address && !g.isAnyLocalAddress()) return g.getHostAddress();
        }
        return null;
    }

    void release() {
        try {
            cm.bindProcessToNetwork(null);
            if (callback != null) cm.unregisterNetworkCallback(callback);
        } catch (Exception ignored) {
        }
        callback = null;
    }
}
