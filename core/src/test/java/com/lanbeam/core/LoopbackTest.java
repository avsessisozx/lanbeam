package com.lanbeam.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** End-to-end: two nodes in one process, sender pushes an offer, receiver pulls the stream; contents are compared. */
public final class LoopbackTest {
    public static void main(String[] args) throws Exception {
        File tmp = Files.createTempDirectory("lanbeam-test").toFile();
        File src = new File(tmp, "src");
        File dst = new File(tmp, "dst");
        new File(src, "Klasör/alt").mkdirs();
        Random rnd = new Random(42);
        write(new File(src, "küçük.txt"), rnd, 13);
        write(new File(src, "boş.bin"), rnd, 0);
        write(new File(src, "Klasör/orta.bin"), rnd, 3 * 1024 * 1024 + 7);
        write(new File(src, "Klasör/alt/büyük.bin"), rnd, 300L * 1024 * 1024);
        for (int i = 0; i < 200; i++) write(new File(src, "Klasör/alt/k" + i + ".dat"), rnd, rnd.nextInt(5000));

        final CountDownLatch offered = new CountDownLatch(1);
        final Offer[] got = new Offer[1];
        LanBeamNode receiver = new LanBeamNode("rx-id", "Alıcı", "test", new Adapter() {
            @Override public void onOffer(Offer o) { got[0] = o; offered.countDown(); }
        });
        LanBeamNode sender = new LanBeamNode("tx-id", "Gönderen", "test", new Adapter());
        receiver.start();
        sender.start();
        System.out.println("ports rx=" + receiver.port() + " tx=" + sender.port());

        Share share = sender.publish(LocalFileSource.collect(Arrays.asList(src.listFiles())));
        sender.sendOffer(new Peer("rx-id", "Alıcı", "test", "127.0.0.1", receiver.port()), share);
        check(offered.await(5, TimeUnit.SECONDS), "offer arrived");
        System.out.println("offer: " + got[0].describe());

        final CountDownLatch done = new CountDownLatch(1);
        final boolean[] ok = new boolean[1];
        final double[] speed = new double[1];
        new Receiver(got[0].link, new Receiver.DirectorySink(dst), new Receiver.Listener() {
            @Override public void onStart(Receiver r, int count, long total) { System.out.println("start " + count + " files, " + NetUtil.formatSize(total)); }
            @Override public void onProgress(Receiver r, long d, long t, double bps) { speed[0] = bps; }
            @Override public void onFinished(Receiver r, boolean success, String error, List<String> files) {
                ok[0] = success;
                System.out.println("finished ok=" + success + " err=" + error + " files=" + files.size());
                done.countDown();
            }
        }).startThread();
        check(done.await(120, TimeUnit.SECONDS), "transfer finished");
        check(ok[0], "transfer ok");
        System.out.println("speed " + NetUtil.formatSpeed(speed[0]));

        List<String> a = listRel(src), b = listRel(new File(dst, ""));
        check(a.equals(b), "same file list " + a.size() + " vs " + b.size());
        for (String r : a) check(Arrays.equals(Files.readAllBytes(new File(src, r).toPath()), Files.readAllBytes(new File(dst, r).toPath())), "content " + r);

        // Browser endpoints
        String page = new String(Receiver.get("http://127.0.0.1:" + sender.port() + "/s/" + share.token), "UTF-8");
        check(page.contains("küçük.txt"), "html page lists files");
        byte[] zip = Receiver.get("http://127.0.0.1:" + sender.port() + "/s/" + share.token + "/zip");
        check(zip.length > 300L * 1024 * 1024, "zip size " + zip.length);
        check(Receiver.safePath("../../etc/passwd").equals("etc/passwd"), "path traversal stripped");
        check(Receiver.safePath("C:\\Windows\\x?.txt").equals("C_/Windows/x_.txt"), "windows chars " + Receiver.safePath("C:\\Windows\\x?.txt"));
        ShareLink l = ShareLink.parse("http://192.168.1.5:8081/s/abc?ssid=My%20Net&pass=p%26w");
        check(l.port == 8081 && l.token.equals("abc") && l.ssid.equals("My Net") && l.pass.equals("p&w"), "link parse");
        check(ShareLink.parse(l.toString()).pass.equals("p&w"), "link roundtrip");

        sender.stop();
        receiver.stop();
        System.out.println("ALL TESTS PASSED");
        System.exit(0);
    }

    static class Adapter implements LanBeamNode.Listener {
        @Override public void onPeersChanged(List<Peer> peers) {}
        @Override public void onOffer(Offer offer) {}
        @Override public void onSendProgress(Share s, long sent, long total, double bps) {}
        @Override public void onSendFinished(Share s, boolean ok) {}
        @Override public void onRejected(Share s) {}
    }

    static void write(File f, Random rnd, long size) throws IOException {
        byte[] buf = new byte[1 << 20];
        try (OutputStream o = new FileOutputStream(f)) {
            while (size > 0) {
                rnd.nextBytes(buf);
                int n = (int) Math.min(buf.length, size);
                o.write(buf, 0, n);
                size -= n;
            }
        }
    }

    static List<String> listRel(File root) {
        List<String> out = new ArrayList<>();
        walk(root, "", out);
        java.util.Collections.sort(out);
        return out;
    }

    static void walk(File f, String rel, List<String> out) {
        File[] kids = f.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            String r = rel.isEmpty() ? k.getName() : rel + "/" + k.getName();
            if (k.isDirectory()) walk(k, r, out); else out.add(r);
        }
    }

    static void check(boolean c, String what) {
        if (!c) { System.out.println("FAIL: " + what); System.exit(1); }
    }
}
