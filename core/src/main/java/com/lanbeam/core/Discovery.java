package com.lanbeam.core;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Finds other LanBeam devices on the LAN with UDP broadcast announcements. */
public final class Discovery {
    public interface Listener {
        void onPeersChanged(List<Peer> peers);
    }

    private final String selfId;
    private final Listener listener;
    private final Map<String, Peer> peers = new ConcurrentHashMap<>();
    private volatile String announcement;
    private DatagramSocket socket;
    private volatile boolean running;

    public Discovery(String selfId, Listener listener) {
        this.selfId = selfId;
        this.listener = listener;
    }

    public void start(String name, String os, int httpPort) throws java.io.IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("app", Protocol.APP);
        m.put("v", Protocol.VERSION);
        m.put("id", selfId);
        m.put("name", name);
        m.put("os", os);
        m.put("port", httpPort);
        announcement = Json.write(m);

        socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.setBroadcast(true);
        socket.bind(new InetSocketAddress(Protocol.DISCOVERY_PORT));
        running = true;
        Thread rx = new Thread(this::receiveLoop, "lanbeam-discovery-rx");
        rx.setDaemon(true);
        rx.start();
        Thread tx = new Thread(this::announceLoop, "lanbeam-discovery-tx");
        tx.setDaemon(true);
        tx.start();
    }

    public void stop() {
        running = false;
        if (socket != null) socket.close();
    }

    public List<Peer> peers() {
        return new ArrayList<>(peers.values());
    }

    /** Sends an announcement now (e.g. after the network changed). */
    public void announceNow() {
        new Thread(this::broadcast, "lanbeam-announce").start();
    }

    private void announceLoop() {
        while (running) {
            broadcast();
            prune();
            try { Thread.sleep(Protocol.ANNOUNCE_INTERVAL_MS); } catch (InterruptedException e) { return; }
        }
    }

    private void broadcast() {
        byte[] data = announcement.getBytes(HttpServer.UTF8);
        List<InetAddress> targets = new ArrayList<>();
        try { targets.add(InetAddress.getByName("255.255.255.255")); } catch (Exception ignored) {}
        for (NetUtil.LocalAddress a : NetUtil.localAddresses()) if (a.broadcast != null) targets.add(a.broadcast);
        for (InetAddress t : targets) send(data, t);
    }

    private void send(byte[] data, InetAddress to) {
        try {
            socket.send(new DatagramPacket(data, data.length, to, Protocol.DISCOVERY_PORT));
        } catch (Exception ignored) {
        }
    }

    private void receiveLoop() {
        byte[] buf = new byte[4096];
        while (running) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                Map<String, Object> m = Json.parseObject(new String(p.getData(), 0, p.getLength(), HttpServer.UTF8));
                if (!Protocol.APP.equals(m.get("app"))) continue;
                String id = Json.str(m, "id", "");
                if (id.isEmpty() || id.equals(selfId)) continue;
                Peer np = new Peer(id, Json.str(m, "name", "?"), Json.str(m, "os", "?"),
                        p.getAddress().getHostAddress(), (int) Json.num(m, "port", Protocol.HTTP_PORT));
                Peer old = peers.put(id, np);
                if (old == null) {
                    // Answer a newcomer directly so it sees us without waiting for the next tick.
                    send(announcement.getBytes(HttpServer.UTF8), p.getAddress());
                }
                if (!np.sameAs(old)) listener.onPeersChanged(peers());
            } catch (Exception e) {
                if (!running) return;
            }
        }
    }

    private void prune() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Iterator<Peer> it = peers.values().iterator(); it.hasNext(); ) {
            if (now - it.next().lastSeen > Protocol.PEER_TIMEOUT_MS) {
                it.remove();
                changed = true;
            }
        }
        if (changed) listener.onPeersChanged(peers());
    }
}
