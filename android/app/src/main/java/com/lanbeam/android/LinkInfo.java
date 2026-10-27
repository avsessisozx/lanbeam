package com.lanbeam.android;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import com.lanbeam.core.Diagnostics;

/** Reads what Android tells us about the Wi-Fi link and the phone (no location permission needed) and feeds the report. */
final class LinkInfo {
    final boolean wifi;
    final int mhz;
    final int linkMbps;
    final int rssi;
    final String text;
    final String hint;

    private LinkInfo(boolean wifi, int mhz, int linkMbps, int rssi, String text, String hint) {
        this.wifi = wifi;
        this.mhz = mhz;
        this.linkMbps = linkMbps;
        this.rssi = rssi;
        this.text = text;
        this.hint = hint;
    }

    @SuppressWarnings("deprecation")
    static LinkInfo read(Context ctx) {
        Diagnostics.info("Telefon", Build.MANUFACTURER + " " + Build.MODEL);
        Diagnostics.info("Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        Diagnostics.info("Desteklenen mimariler", String.valueOf(java.util.Arrays.asList(Build.SUPPORTED_ABIS)));
        WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        boolean connected = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            connected = nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Exception ignored) {
        }
        WifiInfo wi = null;
        try {
            if (wm != null && connected) wi = wm.getConnectionInfo();
        } catch (Exception ignored) {
        }
        if (wi == null || wi.getNetworkId() == -1 && wi.getFrequency() <= 0) {
            Diagnostics.info("Wi-Fi", connected ? "bilgi alınamadı" : "bağlı değil (hotspot sahibi veya mobil veri olabilir)");
            Diagnostics.info("Wi-Fi bandı", null);
            Diagnostics.info("Wi-Fi bağlantı hızı", null);
            Diagnostics.info("Wi-Fi sinyali", null);
            return new LinkInfo(false, 0, 0, 0,
                    connected ? "Wi-Fi bilgisi okunamadı" : "Wi-Fi'ye bağlı değil (hotspot sahibi olabilirsiniz)",
                    "Hotspot açıksa hız, hotspot'un bandına bağlıdır: telefonun hotspot ayarlarında 5 GHz bandını seçin.");
        }
        int mhz = wi.getFrequency();
        int link = wi.getLinkSpeed();
        int rssi = wi.getRssi();
        String band = mhz >= 5925 ? "6 GHz" : mhz >= 4900 ? "5 GHz" : mhz > 0 ? "2,4 GHz" : "?";
        String hint = null;
        if (mhz > 0 && mhz < 3000) hint = "2,4 GHz bağlısınız; hız düşük kalır. Mümkünse yönlendiricide 5 GHz ağına geçin (aynı ağ iki bantta yayın yapıyorsa 5 GHz olanı seçin).";
        else if (rssi != 0 && rssi < -75) hint = "Sinyal zayıf; yönlendiriciye yaklaşın.";
        else if (link > 0 && link < 150) hint = "Bağlantı hızı düşük; yönlendiriciye yaklaşmak hızı artırabilir.";
        String lm = link > 0 ? link + " Mbps" : "?";
        Diagnostics.info("Wi-Fi", "bağlı");
        Diagnostics.info("Wi-Fi bandı", band + " (" + mhz + " MHz)");
        Diagnostics.info("Wi-Fi bağlantı hızı", lm + " (kuramsal üst sınır; gerçek hız genelde yarısı)");
        Diagnostics.info("Wi-Fi sinyali", rssi + " dBm");
        String t = "Wi-Fi " + band + "  •  " + lm + "  •  " + rssi + " dBm";
        return new LinkInfo(true, mhz, link, rssi, t, hint);
    }
}
