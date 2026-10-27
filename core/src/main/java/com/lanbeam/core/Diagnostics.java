package com.lanbeam.core;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * In-memory diagnostics shared by PC and Android: a short event log, the last transfers
 * (with where the time went) and device/link facts supplied by the platform.
 * The report never contains file names, IP addresses, device names or share tokens.
 */
public final class Diagnostics {
    private Diagnostics() {}

    public static final class TransferStat {
        public boolean sent;
        public long bytes;
        public double seconds;
        public double peakBps;
        /** Seconds spent waiting for the network (receive) / writing to the socket (send). */
        public double netSeconds;
        /** Seconds spent on storage: writing files (receive) / reading files (send). */
        public double diskSeconds;
        public boolean ok;
        public String error;

        double avg() { return seconds > 0 ? bytes / seconds : 0; }
    }

    private static final int MAX_LOG = 80;
    private static final int MAX_TRANSFERS = 8;
    private static final ArrayDeque<String> log = new ArrayDeque<>();
    private static final ArrayDeque<TransferStat> transfers = new ArrayDeque<>();
    private static final Map<String, String> info = new LinkedHashMap<>();
    private static final Map<String, String> tests = new LinkedHashMap<>();
    private static final long T0 = System.currentTimeMillis();

    private static String lastCrash;

    /** Stack trace of the previous crash (kept for the report). */
    public static synchronized void crash(String trace) {
        lastCrash = trace == null ? null : (trace.length() > 4000 ? trace.substring(0, 4000) : trace);
    }

    /** Device/link fact shown in the report ("Wi-Fi bandı" -> "5 GHz"). Null value removes it. */
    public static synchronized void info(String key, String value) {
        if (value == null) info.remove(key); else info.put(key, value);
    }

    /** Result of a measurement ("Ağ testi (indirme)" -> "85.3 MB/s"). */
    public static synchronized void test(String key, String value) {
        tests.put(key, value);
    }

    public static synchronized void log(String msg) {
        if (log.size() >= MAX_LOG) log.removeFirst();
        log.addLast(String.format(Locale.ROOT, "+%ds %s", (System.currentTimeMillis() - T0) / 1000, clean(msg)));
    }

    public static synchronized void transfer(TransferStat t) {
        if (transfers.size() >= MAX_TRANSFERS) transfers.removeFirst();
        transfers.addLast(t);
        log((t.sent ? "gönderim " : "alım ") + (t.ok ? "bitti " : "başarısız ") + NetUtil.formatSize(t.bytes)
                + (t.error != null ? " (" + t.error + ")" : ""));
    }

    /** Removes things that identify people or networks. */
    static String clean(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\b\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?\\b", "<ip>");
        s = s.replaceAll("/s/[A-Za-z0-9_-]+", "/s/<token>");
        s = s.replaceAll("[\\r\\n]+", " ");
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    /** Which part limited the last finished transfer, in plain Turkish. */
    public static synchronized String verdict() {
        TransferStat t = null;
        for (TransferStat x : transfers) if (x.ok && x.bytes >= 4L * 1024 * 1024) t = x;
        if (t == null) return "Henüz değerlendirilecek kadar büyük (4 MB+) başarılı aktarım yok.";
        double total = t.netSeconds + t.diskSeconds;
        if (total <= 0) return "Ölçüm yetersiz.";
        double diskShare = t.diskSeconds / total;
        String who = t.sent ? "okuma (depolama)" : "yazma (depolama)";
        if (diskShare > 0.6) {
            return String.format(Locale.ROOT, "Sınır depolama: sürenin %%%d'i dosya %s işleminde geçti. Ağ bundan hızlı; daha hızlı bir disk/SD kart/USB gerekir.",
                    Math.round(diskShare * 100), t.sent ? "okuma" : "yazma");
        }
        if (diskShare < 0.25) {
            return String.format(Locale.ROOT, "Sınır ağ: sürenin %%%d'i ağ beklemekle geçti, %s hızlıydı. Ağ hızı için 5 GHz Wi-Fi, daha yakın mesafe veya kablo (Ethernet) dene.",
                    Math.round((1 - diskShare) * 100), who);
        }
        return "Ağ ve depolama dengeli; belirgin tek bir darboğaz yok.";
    }

    public static synchronized String report(String appVersion, String platform) {
        StringBuilder sb = new StringBuilder();
        sb.append("LanBeam raporu\n");
        sb.append("Sürüm: ").append(appVersion).append(" (").append(platform).append(")\n");
        sb.append("Java/Çalışma ortamı: ").append(System.getProperty("java.vm.name", "?")).append(' ')
                .append(System.getProperty("java.version", "?")).append('\n');
        sb.append("İşlemci çekirdeği: ").append(Runtime.getRuntime().availableProcessors()).append('\n');
        sb.append("Tampon: kopyalama ").append(Protocol.COPY_BUFFER / 1024).append(" KB, soket ")
                .append(Protocol.SOCKET_BUFFER / 1024).append(" KB (tek TCP akışı)\n");
        if (!info.isEmpty()) {
            sb.append("\n== Cihaz / bağlantı ==\n");
            for (Map.Entry<String, String> e : info.entrySet()) sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        if (!tests.isEmpty()) {
            sb.append("\n== Ölçümler ==\n");
            for (Map.Entry<String, String> e : tests.entrySet()) sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        sb.append("\n== Son aktarımlar ==\n");
        if (transfers.isEmpty()) sb.append("(yok)\n");
        for (TransferStat t : transfers) {
            sb.append(t.sent ? "Gönderim " : "Alım ").append(t.ok ? "OK  " : "HATA").append("  ")
                    .append(NetUtil.formatSize(t.bytes)).append("  ort ").append(NetUtil.formatSpeed(t.avg()))
                    .append("  tepe ").append(NetUtil.formatSpeed(t.peakBps))
                    .append(String.format(Locale.ROOT, "  süre %.1fs (ağ %.1fs, depolama %.1fs)", t.seconds, t.netSeconds, t.diskSeconds));
            if (t.error != null) sb.append("  hata: ").append(clean(t.error));
            sb.append('\n');
        }
        sb.append("\nYorum: ").append(verdict()).append('\n');
        if (lastCrash != null) sb.append("\n== Önceki çökme ==\n").append(lastCrash).append('\n');
        sb.append("\n== Olay günlüğü ==\n");
        if (log.isEmpty()) sb.append("(boş)\n");
        for (String l : log) sb.append(l).append('\n');
        sb.append("\nBu rapor dosya adı, IP adresi, ağ (SSID) adı, verdiğiniz cihaz adı veya paylaşım anahtarı içermez; kendiliğinden hiçbir yere gönderilmez.\n");
        return sb.toString();
    }
}
