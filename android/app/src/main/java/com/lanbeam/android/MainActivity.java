package com.lanbeam.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.lanbeam.core.FileSource;
import com.lanbeam.core.NetUtil;
import com.lanbeam.core.Offer;
import com.lanbeam.core.Peer;
import com.lanbeam.core.ShareLink;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity implements LanBeamService.UiListener {
    private static final int REQ_FILES = 10;
    private static final int REQ_TREE = 11;
    private static final int REQ_PERMS = 20;
    private static final int REQ_HOTSPOT_PERMS = 21;

    private final List<FileSource> selected = new ArrayList<>();
    private TextView selfInfo, selection, peersEmpty;
    private LinearLayout peersBox, transfersBox;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        selfInfo = findViewById(R.id.self_info);
        selection = findViewById(R.id.selection);
        peersBox = findViewById(R.id.peers);
        peersEmpty = findViewById(R.id.peers_empty);
        transfersBox = findViewById(R.id.transfers);

        findViewById(R.id.pick_files).setOnClickListener(v -> pickFiles());
        findViewById(R.id.pick_folder).setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_TREE));
        findViewById(R.id.clear_selection).setOnClickListener(v -> { selected.clear(); render(); });
        findViewById(R.id.show_qr).setOnClickListener(v -> showLinkQr());
        findViewById(R.id.hotspot).setOnClickListener(v -> startHotspot());
        findViewById(R.id.scan).setOnClickListener(v -> scan());
        findViewById(R.id.from_link).setOnClickListener(v -> enterLink());
        findViewById(R.id.clear_transfers).setOnClickListener(v -> { LanBeamService s = LanBeamService.get(); if (s != null) s.clearFinished(); });
        findViewById(R.id.refresh).setOnClickListener(v -> { LanBeamService s = LanBeamService.get(); if (s != null) s.refreshPeers(); });
        selfInfo.setOnClickListener(v -> rename());

        requestBasicPermissions();
        LanBeamService.start(this);
        handleShareIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShareIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        attach();
    }

    @Override
    protected void onPause() {
        LanBeamService s = LanBeamService.get();
        if (s != null) s.setUi(null);
        super.onPause();
    }

    /** The service may still be starting right after startForegroundService(). */
    private void attach() {
        LanBeamService s = LanBeamService.get();
        if (s == null) {
            selfInfo.postDelayed(this::attach, 200);
            return;
        }
        s.setUi(this);
        render();
    }

    private void requestBasicPermissions() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS);
        if (Build.VERSION.SDK_INT < 29) perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        List<String> missing = new ArrayList<>();
        for (String p : perms) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), REQ_PERMS);
    }

    // ------------------------------------------------------------------ picking files

    private void pickFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, REQ_FILES);
    }

    private void handleShareIntent(Intent intent) {
        if (intent == null) return;
        String a = intent.getAction();
        List<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(a)) {
            Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) uris.add(u);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(a)) {
            ArrayList<Uri> l = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (l != null) uris.addAll(l);
        }
        if (uris.isEmpty()) return;
        addUris(uris);
        Toast.makeText(this, "Dosyalar hazır — bir cihaza dokunun", Toast.LENGTH_LONG).show();
    }

    private void addUris(final List<Uri> uris) {
        new Thread(() -> {
            final List<FileSource> l = new ArrayList<>();
            for (Uri u : uris) l.add(UriFileSource.of(this, u));
            runOnUiThread(() -> { selected.addAll(l); render(); });
        }).start();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        IntentResult scan = IntentIntegrator.parseActivityResult(req, res, data);
        if (scan != null) {
            if (scan.getContents() != null) openLink(scan.getContents());
            return;
        }
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == REQ_FILES) {
            List<Uri> uris = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
            else if (data.getData() != null) uris.add(data.getData());
            for (Uri u : uris) keepPermission(u);
            addUris(uris);
        } else if (req == REQ_TREE && data.getData() != null) {
            final Uri tree = data.getData();
            keepPermission(tree);
            new Thread(() -> {
                final List<FileSource> l = new ArrayList<>();
                UriFileSource.addTree(this, tree, l);
                runOnUiThread(() -> { selected.addAll(l); render(); });
            }).start();
        }
    }

    private void keepPermission(Uri u) {
        try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
    }

    private boolean needFiles() {
        if (!selected.isEmpty()) return false;
        Toast.makeText(this, "Önce dosya veya klasör seçin", Toast.LENGTH_SHORT).show();
        return true;
    }

    // ------------------------------------------------------------------ actions

    private void sendTo(Peer p) {
        LanBeamService s = LanBeamService.get();
        if (s == null || needFiles()) return;
        s.sendTo(p, new ArrayList<>(selected));
        Toast.makeText(this, p.name + " cihazına teklif gönderildi", Toast.LENGTH_SHORT).show();
    }

    private void showLinkQr() {
        LanBeamService s = LanBeamService.get();
        if (s == null || needFiles()) return;
        showQrDialog("Aynı ağdaki cihazla paylaş", s.publishLink(new ArrayList<>(selected)),
                "Diğer telefonda LanBeam → \"QR tara\". Uygulaması olmayanlar bağlantıyı tarayıcıda açabilir.");
    }

    private void startHotspot() {
        if (needFiles()) return;
        String perm = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.NEARBY_WIFI_DEVICES : Manifest.permission.ACCESS_FINE_LOCATION;
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(Build.VERSION.SDK_INT >= 33 ? new String[]{perm}
                    : new String[]{perm, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_HOTSPOT_PERMS);
            return;
        }
        LanBeamService s = LanBeamService.get();
        if (s == null) return;
        Toast.makeText(this, "Hotspot açılıyor…", Toast.LENGTH_SHORT).show();
        s.startHotspot(new ArrayList<>(selected), new LanBeamService.HotspotCallback() {
            @Override public void onReady(ShareLink link) {
                showQrDialog("Router olmadan gönder", link,
                        "Diğer telefonda LanBeam → \"QR tara\". Telefon önce bu hotspot'a bağlanır, sonra dosyaları çeker.\nAğ: " + link.ssid);
            }

            @Override public void onFailed(String reason) {
                new AlertDialog.Builder(MainActivity.this).setTitle("Hotspot").setMessage(reason).setPositiveButton("Tamam", null).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQ_HOTSPOT_PERMS && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startHotspot();
    }

    private void showQrDialog(String title, ShareLink link, String hint) {
        final String text = link.toString();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, 0);
        ImageView img = new ImageView(this);
        try {
            Bitmap bmp = new BarcodeEncoder().encodeBitmap(text, BarcodeFormat.QR_CODE, dp(260), dp(260));
            img.setImageBitmap(bmp);
        } catch (Exception e) {
            img.setVisibility(View.GONE);
        }
        box.addView(img);
        TextView url = new TextView(this);
        url.setText(link.ssid == null ? text : link.baseUrl());
        url.setTextIsSelectable(true);
        url.setPadding(0, dp(8), 0, dp(4));
        box.addView(url);
        TextView h = new TextView(this);
        h.setText(hint);
        h.setAlpha(0.7f);
        box.addView(h);
        new AlertDialog.Builder(this).setTitle(title).setView(box)
                .setPositiveButton("Tamam", null)
                .setNeutralButton("Kopyala", (d, w) -> {
                    ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("LanBeam", text));
                })
                .show();
    }

    private void scan() {
        IntentIntegrator ii = new IntentIntegrator(this);
        ii.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        ii.setPrompt("LanBeam QR kodunu okutun");
        ii.setBeepEnabled(false);
        ii.setOrientationLocked(false);
        ii.initiateScan();
    }

    private void enterLink() {
        final EditText e = new EditText(this);
        e.setHint("http://192.168.1.20:8080/s/…");
        e.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this).setTitle("Bağlantıdan al").setView(e)
                .setPositiveButton("Al", (d, w) -> openLink(e.getText().toString()))
                .setNegativeButton("Vazgeç", null).show();
    }

    private void openLink(String text) {
        ShareLink link;
        try {
            link = ShareLink.parse(text);
        } catch (Exception ex) {
            Toast.makeText(this, "Bu bir LanBeam bağlantısı değil", Toast.LENGTH_LONG).show();
            return;
        }
        LanBeamService s = LanBeamService.get();
        if (s != null) s.receive(link, link.ssid != null ? "Hotspot " + link.ssid : link.host);
    }

    private void rename() {
        final LanBeamService s = LanBeamService.get();
        if (s == null) return;
        final EditText e = new EditText(this);
        e.setText(s.deviceName());
        new AlertDialog.Builder(this).setTitle("Cihaz adı").setView(e)
                .setPositiveButton("Kaydet", (d, w) -> { if (!e.getText().toString().trim().isEmpty()) s.setDeviceName(e.getText().toString().trim()); })
                .setNegativeButton("Vazgeç", null).show();
    }

    // ------------------------------------------------------------------ service callbacks

    @Override public void onStateChanged() {
        render();
    }

    @Override public boolean onOffer(final Offer offer) {
        if (isFinishing()) return false;
        final LanBeamService s = LanBeamService.get();
        new AlertDialog.Builder(this)
                .setTitle("Gelen dosyalar")
                .setMessage(offer.fromName + " size dosya göndermek istiyor:\n\n" + offer.describe()
                        + "\n\nKayıt yeri: " + DownloadsSink.displayRoot())
                .setCancelable(false)
                .setPositiveButton("Kabul et", (d, w) -> s.accept(offer))
                .setNegativeButton("Reddet", (d, w) -> s.reject(offer))
                .show();
        return true;
    }

    // ------------------------------------------------------------------ rendering

    private void render() {
        LanBeamService s = LanBeamService.get();
        if (s == null) return;
        selfInfo.setText(s.deviceName() + "  •  " + s.address() + "   ✎");

        long total = 0;
        for (FileSource f : selected) total += f.size();
        selection.setText(selected.isEmpty() ? "Seçili dosya yok"
                : selected.size() + " dosya seçildi (" + NetUtil.formatSize(total) + ")");

        peersBox.removeAllViews();
        for (final Peer p : s.peers()) {
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            b.setText(("windows".equals(p.os) || "pc".equals(p.os) ? "🖥  " : "📱  ") + p.name + "   (" + p.host + ")");
            b.setOnClickListener(v -> sendTo(p));
            peersBox.addView(b);
        }
        peersEmpty.setVisibility(s.peers().isEmpty() ? View.VISIBLE : View.GONE);

        transfersBox.removeAllViews();
        for (final LanBeamService.Transfer t : s.transfers) transfersBox.addView(transferView(t));
    }

    private View transferView(final LanBeamService.Transfer t) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        TextView title = new TextView(this);
        title.setText(t.title);
        title.setTextSize(15);
        row.addView(title);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgress(t.total > 0 ? (int) (t.done * 1000 / t.total) : (t.finished && t.ok ? 1000 : 0));
        row.addView(bar);
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView status = new TextView(this);
        status.setText(t.status);
        status.setAlpha(0.75f);
        line.addView(status, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        final Runnable cancel = t.cancel;
        if (cancel != null && !t.finished) {
            Button c = new Button(this, null, android.R.attr.borderlessButtonStyle);
            c.setText(t.outgoing && t.status.startsWith("Paylaşımda") || t.title.startsWith("📶") ? "Kapat" : "İptal");
            c.setOnClickListener(v -> cancel.run());
            line.addView(c);
        }
        row.addView(line);
        return row;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
