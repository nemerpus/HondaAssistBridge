package com.nemerpus.hondaassistbridge;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Product-facing picker: show bonded devices immediately, then hand the selected MAC to CDM. */
public class PairDeviceActivity extends Activity {
    private static final int REQ_BT = 811;
    private LinearLayout root;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        UiKit.applySystemBars(this);
        render();
    }

    private void render() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(UiKit.BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(34));
        UiInsets.protectTop(this, root);
        sv.addView(root);
        setContentView(sv);

        root.addView(UiKit.overline(this, "BLUETOOTH / COMPANION"));
        root.addView(UiKit.title(this, "Vincular motocicleta"));
        TextView intro = UiKit.body(this, "Selecciona un dispositivo que Android ya conozca. Después se solicitará la asociación Companion para mantener un enlace fiable con la moto.");
        intro.setPadding(0, dp(7), 0, dp(22));
        root.addView(intro);

        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            LinearLayout permission = UiKit.card(this);
            TextView ph = UiKit.text(this, "Permiso necesario", 18, UiKit.TEXT);
            ph.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            permission.addView(ph);
            TextView pp = UiKit.body(this, "Honda Assist necesita acceso Bluetooth para mostrar los dispositivos emparejados y seleccionar la BTU.");
            pp.setPadding(0, dp(6), 0, dp(14));
            permission.addView(pp);
            Button grant = UiKit.primaryButton(this, "Permitir acceso a Bluetooth");
            grant.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, REQ_BT));
            permission.addView(grant);
            root.addView(permission);
            return;
        }
        showBonded();
    }

    private void showBonded() {
        root.addView(UiKit.section(this, "DISPOSITIVOS EMPAREJADOS"));
        BluetoothManager bm = getSystemService(BluetoothManager.class);
        BluetoothAdapter a = bm == null ? null : bm.getAdapter();
        if (a == null) {
            root.addView(infoCard("Bluetooth no disponible", "Este teléfono no expone un adaptador Bluetooth compatible."));
            return;
        }

        List<BluetoothDevice> all = new ArrayList<>();
        try { all.addAll(a.getBondedDevices()); }
        catch (SecurityException e) {
            root.addView(infoCard("Falta permiso Bluetooth", "Concede el permiso para consultar los dispositivos emparejados."));
            return;
        }
        all.sort((x, y) -> safeName(x).compareToIgnoreCase(safeName(y)));

        int shown = 0;
        for (BluetoothDevice d : all) {
            String name = safeName(d);
            if (!name.toUpperCase(Locale.ROOT).contains("HONDA BTU")) continue;
            addDevice(d, true);
            shown++;
        }
        for (BluetoothDevice d : all) {
            String name = safeName(d);
            if (name.toUpperCase(Locale.ROOT).contains("HONDA BTU")) continue;
            addDevice(d, false);
            shown++;
        }
        if (shown == 0) {
            root.addView(infoCard("Sin dispositivos emparejados", "Empareja primero la BTU desde los ajustes de Android y vuelve a esta pantalla."));
        }
        TextView note = UiKit.body(this, "La BTU puede aparecer aquí aunque ya esté conectada y no esté en modo visible. La asociación Companion se solicita tras seleccionarla.");
        note.setPadding(0, dp(14), 0, 0);
        root.addView(note);
    }

    private void addDevice(BluetoothDevice d, boolean honda) {
        LinearLayout c = UiKit.card(this);
        UiKit.makeClickableCard(this, c);
        String name = safeName(d);
        String addr = safeAddress(d);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView icon = UiKit.iconTile(this, honda ? "🏍" : "◉", honda ? UiKit.RED : UiKit.MUTED);
        top.addView(icon, new LinearLayout.LayoutParams(dp(50), dp(50)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(dp(13), 0, 0, 0);
        TextView n = UiKit.text(this, name, 16, UiKit.TEXT);
        n.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tx.addView(n);
        tx.addView(UiKit.text(this, (honda ? "● HONDA BTU priorizada · " : "Emparejado · ") + mask(addr), 12, honda ? UiKit.GOOD : UiKit.MUTED));
        top.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(UiKit.text(this, "›", 28, UiKit.MUTED));
        c.addView(top);
        c.setOnClickListener(v -> {
            FlightRecorder.log(this, "COMPANION", "bonded selected name=" + name + " address=" + mask(addr));
            CompanionController.requestAssociation(this, addr, info -> { setResult(RESULT_OK); finish(); });
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        root.addView(c, lp);
    }

    private LinearLayout infoCard(String title, String body) {
        LinearLayout c = UiKit.card(this);
        TextView h = UiKit.text(this, title, 17, UiKit.TEXT);
        h.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        c.addView(h);
        TextView p = UiKit.body(this, body);
        p.setPadding(0, dp(6), 0, 0);
        c.addView(p);
        return c;
    }

    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) {
        super.onRequestPermissionsResult(r, p, g);
        if (r == REQ_BT) render();
    }

    private String safeName(BluetoothDevice d) {
        try {
            String n = d.getName();
            return n == null || n.trim().isEmpty() ? "Dispositivo Bluetooth" : n;
        } catch (Throwable t) { return "Dispositivo Bluetooth"; }
    }

    private String safeAddress(BluetoothDevice d) {
        try { return d.getAddress(); } catch (Throwable t) { return ""; }
    }

    private String mask(String x) {
        return x == null || x.length() < 5 ? "Dirección protegida" : "••:••:••:••:" + x.substring(x.length() - 5);
    }

    private int dp(int x) { return UiKit.dp(this, x); }
}
