package com.lanbeam.android;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;

import com.lanbeam.core.FileSource;
import com.lanbeam.core.LanBeamNode;
import com.lanbeam.core.NetUtil;
import com.lanbeam.core.Offer;
import com.lanbeam.core.Peer;
import com.lanbeam.core.Receiver;
import com.lanbeam.core.Share;
import com.lanbeam.core.ShareLink;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Keeps the LanBeam node alive (so a PC or phone can push files any time) and runs all transfers. */
public final class LanBeamService extends Service implements LanBeamNode.Listener {
    static final String ACTION_ACCEPT = "com.lanbeam.ACCEPT";
    static final String ACTION_REJECT = "com.lanbeam.REJECT";
    static final String ACTION_STOP = "com.lanbeam.STOP";
    static final String EXTRA_TOKEN = "token";

    private static final String CH_SERVICE = "service";
    private static final String CH_OFFERS = "offers";
    private static final int NOTIF_SERVICE = 1;

    interface UiListener {
        void onStateChanged();

        /** Return true if the UI showed the prompt itself. */
        boolean onOffer(Offer offer);
    }

    interface HotspotCallback {
        void onReady(ShareLink link);

        void onFailed(String reason);
    }

    static final class Transfer {
        final String title;
        final boolean outgoing;
        volatile long done, total;
        volatile double bps;
        volatile String status = "Bekleniyor…";
        volatile boolean finished, ok;
        volatile Runnable cancel;
        boolean locked;

        Transfer(String title, boolean outgoing, long total) {
            this.title = title;
            this.outgoing = outgoing;
            this.total = total;
        }
    }

    private static volatile LanBeamService instance;

    static LanBeamService get() { return instance; }

    final List<Transfer> transfers = new CopyOnWriteArrayList<>();
    private final Map<String, Transfer> sendTransfers = new ConcurrentHashMap<>();
    private final Map<String, Offer> pendingOffers = new ConcurrentHashMap<>();
    private volatile List<Peer> peers = Collections.emptyList();
    private volatile UiListener ui;
    private final Handler main = new Handler(Looper.getMainLooper());

    private LanBeamNode node;
    private WifiManager.MulticastLock multicastLock;
    private WifiManager.WifiLock perfLock, latencyLock;
    private PowerManager.WakeLock wakeLock;
    private int activeTransfers;
    private WifiManager.LocalOnlyHotspotReservation hotspot;

    // ------------------------------------------------------------------ lifecycle

    static void start(Context ctx) {
        Intent i = new Intent(ctx, LanBeamService.class);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
        else ctx.startService(i);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createChannels();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_SERVICE, serviceNotification("Başlatılıyor…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(NOTIF_SERVICE, serviceNotification("Başlatılıyor…"));

        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        multicastLock = wm.createMulticastLock("lanbeam-discovery");
        multicastLock.setReferenceCounted(false);
        multicastLock.acquire();
        perfLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "lanbeam-perf");
        perfLock.setReferenceCounted(false);
        if (Build.VERSION.SDK_INT >= 29) {
            latencyLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "lanbeam-latency");
            latencyLock.setReferenceCounted(false);
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lanbeam:transfer");
        wakeLock.setReferenceCounted(false);

        node = new LanBeamNode(deviceId(), deviceName(), "android", this);
        new Thread(() -> {
            try {
                node.start();
                updateServiceNotification();
            } catch (Exception e) {
                notifyServiceText("Sunucu başlatılamadı: " + e.getMessage());
            }
            changed();
        }, "lanbeam-start").start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(a)) {
            stopSelf();
        } else if (ACTION_ACCEPT.equals(a) || ACTION_REJECT.equals(a)) {
            String token = intent.getStringExtra(EXTRA_TOKEN);
            Offer o = token == null ? null : pendingOffers.remove(token);
            getSystemService(NotificationManager.class).cancel(token == null ? 0 : token.hashCode());
            if (o != null) {
                if (ACTION_ACCEPT.equals(a)) receive(o.link, o.fromName);
                else node.reject(o);
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        instance = null;
        stopHotspot();
        new Thread(node::stop).start();
        if (multicastLock.isHeld()) multicastLock.release();
        releaseTransferLocks(true);
        super.onDestroy();
    }

    // ------------------------------------------------------------------ settings

    private SharedPreferences prefs() { return getSharedPreferences("lanbeam", MODE_PRIVATE); }

    private String deviceId() {
        String id = prefs().getString("id", null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs().edit().putString("id", id).apply();
        }
        return id;
    }

    String deviceName() {
        String def = null;
        try { def = Settings.Global.getString(getContentResolver(), "device_name"); } catch (Exception ignored) {}
        if (def == null || def.isEmpty()) def = Build.MODEL;
        return prefs().getString("name", def);
    }

    void setDeviceName(String name) {
        prefs().edit().putString("name", name).apply();
        // Restart the node so the new name is announced.
        final LanBeamNode old = node;
        node = new LanBeamNode(deviceId(), name, "android", this);
        new Thread(() -> {
            old.stop();
            try { node.start(); } catch (Exception ignored) {}
            updateServiceNotification();
            changed();
        }).start();
    }

    String address() {
        return NetUtil.primaryHost() + ":" + (node == null ? "-" : node.port());
    }

    List<Peer> peers() { return peers; }

    void refreshPeers() { if (node != null) node.refreshPeers(); }

    void setUi(UiListener l) {
        ui = l;
        if (l != null) {
            // Offers that arrived while the UI was closed.
            for (Offer o : new ArrayList<>(pendingOffers.values())) {
                if (l.onOffer(o)) {
                    pendingOffers.remove(o.link.token);
                    getSystemService(NotificationManager.class).cancel(o.link.token.hashCode());
                }
            }
        }
    }

    // ------------------------------------------------------------------ sending

    void sendTo(final Peer peer, List<FileSource> files) {
        final Share share = node.publish(files);
        final Transfer t = track("⬆ " + peer.name + ": " + files.size() + " dosya", true, share.total);
        sendTransfers.put(share.token, t);
        t.cancel = () -> { node.unpublish(share); end(t, false, "İptal edildi"); };
        new Thread(() -> {
            try {
                node.sendOffer(peer, share);
                t.status = "Karşı tarafın onayı bekleniyor…";
            } catch (Exception e) {
                node.unpublish(share);
                end(t, false, "Gönderilemedi: " + e.getMessage());
            }
            changed();
        }).start();
    }

    /** Publishes files for a browser / QR scan on the current network. */
    ShareLink publishLink(List<FileSource> files) {
        Share share = node.publish(files);
        Transfer t = track("🔗 Bağlantı: " + files.size() + " dosya", true, share.total);
        t.status = "Paylaşımda — QR kodu okutun veya bağlantıyı açın";
        sendTransfers.put(share.token, t);
        t.cancel = () -> { node.unpublish(share); end(t, false, "Paylaşım kapatıldı"); };
        changed();
        return node.linkFor(share, NetUtil.primaryHost());
    }

    /** No router: start a local-only hotspot and put its credentials into the share link / QR. */
    @SuppressWarnings("deprecation")
    @SuppressLint("MissingPermission")
    void startHotspot(final List<FileSource> files, final HotspotCallback cb) {
        stopHotspot();
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        try {
            wm.startLocalOnlyHotspot(new WifiManager.LocalOnlyHotspotCallback() {
                @Override public void onStarted(WifiManager.LocalOnlyHotspotReservation r) {
                    hotspot = r;
                    String ssid, pass;
                    if (Build.VERSION.SDK_INT >= 30) {
                        SoftApConfiguration c = r.getSoftApConfiguration();
                        ssid = c.getSsid();
                        pass = c.getPassphrase();
                    } else {
                        WifiConfiguration c = r.getWifiConfiguration();
                        ssid = c == null ? null : c.SSID;
                        pass = c == null ? null : c.preSharedKey;
                    }
                    ssid = unquote(ssid);
                    pass = unquote(pass);
                    Share share = node.publish(files);
                    final Transfer t = track("📶 Hotspot: " + files.size() + " dosya", true, share.total);
                    t.status = "Hotspot açık (" + ssid + ") — diğer telefon QR'ı okutsun";
                    sendTransfers.put(share.token, t);
                    t.cancel = () -> { node.unpublish(share); stopHotspot(); end(t, false, "Kapatıldı"); };
                    changed();
                    cb.onReady(new ShareLink(hotspotHost(), node.port(), share.token, ssid, pass));
                }

                @Override public void onFailed(int reason) {
                    cb.onFailed(reason == ERROR_INCOMPATIBLE_MODE
                            ? "Normal Wi-Fi hotspot açıkken başlatılamıyor; kapatıp tekrar deneyin."
                            : "Hotspot başlatılamadı (kod " + reason + "). Konum/Yakındaki cihazlar izni ve konum servisi açık olmalı.");
                }
            }, main);
        } catch (Exception e) {
            cb.onFailed("Hotspot başlatılamadı: " + e.getMessage());
        }
    }

    void stopHotspot() {
        if (hotspot != null) {
            try { hotspot.close(); } catch (Exception ignored) {}
            hotspot = null;
        }
    }

    private static String unquote(String s) {
        if (s != null && s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
        return s;
    }

    /** Address of the hotspot interface (ap0 / swlan0 / wlan1 …); receivers also fall back to the gateway. */
    private static String hotspotHost() {
        String fallback = null;
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String n = ni.getName();
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address)) continue;
                    if (n.startsWith("ap") || n.startsWith("swlan") || n.startsWith("softap")) return a.getHostAddress();
                    if (a.getHostAddress().endsWith(".1") && fallback == null) fallback = a.getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
        return fallback != null ? fallback : NetUtil.primaryHost();
    }

    // ------------------------------------------------------------------ receiving

    void receive(final ShareLink link, String from) {
        final Transfer t = track("⬇ " + from + " → " + DownloadsSink.displayRoot(), false, 0);
        lock(t);
        if (link.ssid != null && !link.ssid.isEmpty()) {
            if (!WifiJoiner.supported()) {
                t.status = "Önce Wi-Fi ayarlarından \"" + link.ssid + "\" ağına bağlanın (şifre: " + link.pass + ")";
                changed();
                startReceiver(link, t, null);
                return;
            }
            final WifiJoiner joiner = new WifiJoiner(this);
            t.status = "\"" + link.ssid + "\" ağına bağlanılıyor… (onay isteyebilir)";
            t.cancel = () -> { joiner.release(); end(t, false, "İptal edildi"); };
            changed();
            joiner.join(link.ssid, link.pass, new WifiJoiner.Callback() {
                @Override public void onJoined(String gateway) {
                    startReceiver(gateway != null ? link.withHost(gateway) : link, t, joiner);
                }

                @Override public void onFailed(String reason) {
                    joiner.release();
                    end(t, false, reason);
                }
            });
        } else {
            startReceiver(link, t, null);
        }
    }

    private void startReceiver(ShareLink link, final Transfer t, final WifiJoiner joiner) {
        final Receiver rx = new Receiver(link, new DownloadsSink(this), new Receiver.Listener() {
            @Override public void onStart(Receiver r, int count, long total) {
                t.total = total;
                t.status = count + " dosya alınıyor…";
                changed();
            }

            @Override public void onProgress(Receiver r, long done, long total, double bps) {
                t.done = done;
                t.total = total;
                t.bps = bps;
                t.status = NetUtil.formatSize(done) + " / " + NetUtil.formatSize(total) + " • " + NetUtil.formatSpeed(bps);
                changed();
            }

            @Override public void onFinished(Receiver r, boolean ok, String error, List<String> saved) {
                if (joiner != null) joiner.release();
                end(t, ok, ok ? "Tamamlandı — " + saved.size() + " dosya, " + DownloadsSink.displayRoot()
                        + " • ort. " + NetUtil.formatSpeed(t.bps) : "Hata: " + error);
            }
        });
        t.cancel = () -> { rx.cancel(); if (joiner != null) joiner.release(); };
        rx.startThread();
    }

    // ------------------------------------------------------------------ transfer bookkeeping

    private Transfer track(String title, boolean outgoing, long total) {
        Transfer t = new Transfer(title, outgoing, total);
        transfers.add(0, t);
        changed();
        return t;
    }

    private void end(Transfer t, boolean ok, String status) {
        if (t.finished) return;
        t.finished = true;
        t.ok = ok;
        t.status = status;
        t.cancel = null;
        unlock(t);
        changed();
    }

    private synchronized void lock(Transfer t) {
        if (t.locked) return;
        t.locked = true;
        acquireTransferLocks();
    }

    private synchronized void unlock(Transfer t) {
        if (!t.locked) return;
        t.locked = false;
        releaseTransferLocks(false);
    }

    void clearFinished() {
        for (Transfer t : transfers) if (t.finished) transfers.remove(t);
        changed();
    }

    /** Wi-Fi power saving is the main thing that makes phone transfers slow: hold high-perf locks while busy. */
    private synchronized void acquireTransferLocks() {
        activeTransfers++;
        if (!perfLock.isHeld()) perfLock.acquire();
        if (latencyLock != null && !latencyLock.isHeld()) latencyLock.acquire();
        if (!wakeLock.isHeld()) wakeLock.acquire(6 * 60 * 60 * 1000L);
    }

    private synchronized void releaseTransferLocks(boolean all) {
        activeTransfers = all ? 0 : Math.max(0, activeTransfers - 1);
        if (activeTransfers > 0) return;
        if (perfLock != null && perfLock.isHeld()) perfLock.release();
        if (latencyLock != null && latencyLock.isHeld()) latencyLock.release();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    private long lastNotify;

    private void changed() {
        UiListener l = ui;
        if (l != null) main.post(l::onStateChanged);
        long now = System.currentTimeMillis();
        if (now - lastNotify > 1000) {
            lastNotify = now;
            updateServiceNotification();
        }
    }

    // ------------------------------------------------------------------ node callbacks

    @Override public void onPeersChanged(List<Peer> list) {
        peers = list;
        changed();
    }

    @Override public void onOffer(final Offer offer) {
        pendingOffers.put(offer.link.token, offer);
        main.post(() -> {
            UiListener l = ui;
            if (l != null && l.onOffer(offer)) {
                pendingOffers.remove(offer.link.token);
            } else {
                showOfferNotification(offer);
            }
        });
    }

    @Override public void onSendProgress(Share share, long sent, long total, double bps) {
        Transfer t = sendTransfers.get(share.token);
        if (t == null) return;
        if (sent < total) lock(t);
        t.done = sent;
        t.total = total;
        t.bps = bps;
        t.status = NetUtil.formatSize(sent) + " / " + NetUtil.formatSize(total) + " • " + NetUtil.formatSpeed(bps);
        changed();
    }

    @Override public void onSendFinished(Share share, boolean ok) {
        Transfer t = sendTransfers.get(share.token);
        if (t == null) return;
        unlock(t);
        // A link/QR share may be downloaded again, so it stays published until closed.
        if (ok && t.title.startsWith("⬆")) {
            node.unpublish(share);
            end(t, true, "Tamamlandı — " + NetUtil.formatSize(share.total) + " • ort. " + NetUtil.formatSpeed(t.bps));
            return;
        }
        t.status = ok ? "Tamamlandı (paylaşım açık)" : "Bağlantı kesildi";
        changed();
    }

    @Override public void onRejected(Share share) {
        node.unpublish(share);
        Transfer t = sendTransfers.remove(share.token);
        if (t != null) end(t, false, "Karşı taraf reddetti");
    }

    void accept(Offer o) { receive(o.link, o.fromName); }

    void reject(Offer o) { node.reject(o); }

    // ------------------------------------------------------------------ notifications

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH_SERVICE, "LanBeam hizmeti", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(CH_OFFERS, "Gelen dosyalar", NotificationManager.IMPORTANCE_HIGH));
    }

    private PendingIntent openApp() {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent serviceAction(String action, String token, int req) {
        Intent i = new Intent(this, LanBeamService.class).setAction(action).putExtra(EXTRA_TOKEN, token);
        return PendingIntent.getService(this, req, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @TargetApi(26)
    private Notification serviceNotification(String text) {
        return new Notification.Builder(this, CH_SERVICE)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("LanBeam alıma hazır")
                .setContentText(text)
                .setContentIntent(openApp())
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Durdur", serviceAction(ACTION_STOP, "", 1)).build())
                .build();
    }

    private void updateServiceNotification() {
        String text = deviceName() + " • " + address();
        for (Transfer t : transfers) {
            if (!t.finished && t.done > 0) { text = t.title + " • " + t.status; break; }
        }
        notifyServiceText(text);
    }

    private void notifyServiceText(String text) {
        try { getSystemService(NotificationManager.class).notify(NOTIF_SERVICE, serviceNotification(text)); } catch (Exception ignored) {}
    }

    @TargetApi(26)
    private void showOfferNotification(Offer o) {
        Notification n = new Notification.Builder(this, CH_OFFERS)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Gelen dosyalar: " + o.fromName)
                .setContentText(o.describe())
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(null, "Kabul et", serviceAction(ACTION_ACCEPT, o.link.token, o.link.token.hashCode())).build())
                .addAction(new Notification.Action.Builder(null, "Reddet", serviceAction(ACTION_REJECT, o.link.token, o.link.token.hashCode() + 1)).build())
                .build();
        try { getSystemService(NotificationManager.class).notify(o.link.token.hashCode(), n); } catch (Exception ignored) {}
    }
}
