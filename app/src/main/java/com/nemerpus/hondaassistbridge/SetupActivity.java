package com.nemerpus.hondaassistbridge;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

public class SetupActivity extends Activity {
    private static final int REQ = 91;
    private LinearLayout root;
    private TextView status;
    private int learnIndex = -1;
    private int lastSeenCode = -1;

    private final String[] learnNames = {"↑ corto", "↓ corto", "← corto", "→ corto", "↑ mantener", "↓ mantener", "← mantener", "→ mantener"};
    private final GestureEngine.Type[] learnTypes = {
        GestureEngine.Type.DOUBLE_UP, GestureEngine.Type.DOUBLE_DOWN, GestureEngine.Type.DOUBLE_LEFT, GestureEngine.Type.DOUBLE_RIGHT,
        GestureEngine.Type.LONG_UP, GestureEngine.Type.LONG_DOWN, GestureEngine.Type.LONG_LEFT, GestureEngine.Type.LONG_RIGHT
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (!HondaBleService.KIND_EVENT.equals(i.getStringExtra(HondaBleService.EXTRA_KIND))) {
                updateConnectionStatus();
                return;
            }
            if (learnIndex < 0) return;
            int code = AppConfig.prefs(c).getInt(AppConfig.KEY_LAST_EVENT_CODE, -1);
            if (code < 0 || code == lastSeenCode && false) return;
            captureLearning(code);
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        UiKit.applySystemBars(this);
        setContentView(buildWelcome());
    }

    @Override protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(HondaBleService.ACTION_UPDATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);
    }

    @Override protected void onPause() {
        try { unregisterReceiver(receiver); } catch (Throwable ignored) {}
        super.onPause();
    }

    @Override protected void onDestroy() {
        AppConfig.setSetupLearning(this, false);
        super.onDestroy();
    }

    private View shell() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(UiKit.BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(20), dp(22), dp(36));
        sv.addView(root);
        UiInsets.protectTop(this, root);
        return sv;
    }

    private View buildWelcome() {
        View v = shell();
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(88), dp(88));
        ip.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(icon, ip);

        TextView over = UiKit.overline(this, "RIDE CONTROL SETUP");
        over.setTextColor(UiKit.RED);
        over.setGravity(Gravity.CENTER);
        over.setPadding(0, dp(17), 0, dp(4));
        root.addView(over);
        TextView title = UiKit.title(this, "Honda Assist Bridge");
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView sub = UiKit.body(this, "Vincula la BTU y enseña a la app los controles reales de la piña en tres pasos.");
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(dp(6), dp(7), dp(6), dp(24));
        root.addView(sub);

        LinearLayout c = UiKit.card(this);
        TextView ch = UiKit.text(this, "Configuración guiada", 19, UiKit.TEXT);
        ch.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        c.addView(ch);
        TextView copy = UiKit.body(this, "La aplicación detectará la BTU emparejada y te pedirá cada control. No se presupone el código de las pulsaciones.");
        copy.setPadding(0, dp(7), 0, dp(15));
        c.addView(copy);
        Button start = UiKit.primaryButton(this, "Empezar configuración");
        start.setOnClickListener(x -> requestAndDevices());
        c.addView(start);
        if (AppConfig.isSetupCompleted(this)) {
            c.addView(UiKit.spacer(this, 9));
            Button cancel = UiKit.secondaryButton(this, "Volver sin cambios");
            cancel.setOnClickListener(x -> finish());
            c.addView(cancel);
        }
        root.addView(c);
        return v;
    }

    private void requestAndDevices() {
        ArrayList<String> w = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) w.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) w.add(Manifest.permission.BLUETOOTH_SCAN);
        } else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            w.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) w.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!w.isEmpty()) {
            requestPermissions(w.toArray(new String[0]), REQ);
            return;
        }
        showDeviceStep();
    }

    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) {
        super.onRequestPermissionsResult(r, p, g);
        if (r == REQ && hasBt()) showDeviceStep();
        else if (r == REQ) Toast.makeText(this, "Necesito permiso Bluetooth para escuchar la BTU", Toast.LENGTH_LONG).show();
    }

    private boolean hasBt() {
        return Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void showDeviceStep() {
        setContentView(shell());
        root.addView(header("1 de 3", "Vincular motocicleta", "Android gestionará HONDA BTU como dispositivo complementario para una escucha fiable en segundo plano."));
        LinearLayout c = UiKit.card(this);
        if (CompanionController.hasAssociation(this)) {
            TextView ok = UiKit.text(this, "● HONDA BTU vinculada", 18, UiKit.GOOD);
            ok.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            c.addView(ok);
            TextView p = UiKit.body(this, "La asociación Companion está registrada en Android y la observación de presencia está activa.");
            p.setPadding(0, dp(7), 0, dp(15));
            c.addView(p);
            Button next = UiKit.primaryButton(this, "Continuar");
            next.setOnClickListener(x -> {
                AppConfig.setVehicle(this, AppConfig.DEFAULT_DEVICE_FILTER);
                AppConfig.setListeningEnabled(this, true);
                showLearnIntro();
            });
            c.addView(next);
        } else {
            TextView h = UiKit.text(this, "HONDA BTU", 19, UiKit.TEXT);
            h.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            c.addView(h);
            TextView p = UiKit.body(this, "Enciende la moto y asegúrate de que HONDA BTU está emparejada. La siguiente pantalla mostrará primero los dispositivos que Android ya conoce.");
            p.setPadding(0, dp(7), 0, dp(15));
            c.addView(p);
            Button pair = UiKit.primaryButton(this, "Elegir dispositivo emparejado");
            pair.setOnClickListener(x -> startActivityForResult(new Intent(this, PairDeviceActivity.class), 702));
            c.addView(pair);
        }
        root.addView(c);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 702) {
            if (CompanionController.hasAssociation(this)) {
                AppConfig.setVehicle(this, AppConfig.DEFAULT_DEVICE_FILTER);
                AppConfig.setListeningEnabled(this, true);
                showLearnIntro();
            } else showDeviceStep();
        }
    }

    private void showLearnIntro() {
        setContentView(shell());
        root.addView(header("2 de 3", "Aprender controles", "Con la moto encendida y RoadSync conectado, enseñaremos a la app qué evento corresponde a cada dirección."));
        LinearLayout c = UiKit.card(this);
        status = UiKit.text(this, "Conectando con " + AppConfig.getDeviceFilter(this) + "…", 16, UiKit.TEXT);
        status.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        c.addView(status);
        TextView p = UiKit.body(this, "Cuando indique «Escucha activa», pulsa Empezar. Durante el aprendizaje los eventos no ejecutarán tus comandos.");
        p.setPadding(0, dp(7), 0, dp(15));
        c.addView(p);
        Button start = UiKit.primaryButton(this, "Empezar aprendizaje");
        start.setOnClickListener(x -> {
            learnIndex = 0;
            AppConfig.setSetupLearning(this, true);
            showLearnStep();
        });
        c.addView(start);
        c.addView(UiKit.spacer(this, 9));
        Button skip = UiKit.secondaryButton(this, "Usar mapa actual y continuar");
        skip.setOnClickListener(x -> {
            AppConfig.setSetupLearning(this, false);
            showFinish();
        });
        c.addView(skip);
        root.addView(c);
        updateConnectionStatus();
    }

    private void updateConnectionStatus() {
        if (status == null) return;
        String state = AppConfig.prefs(this).getString(AppConfig.KEY_LAST_STATE, HondaBleService.STATE_WAITING);
        String detail = AppConfig.prefs(this).getString(AppConfig.KEY_LAST_STATUS, "Esperando BTU");
        status.setText((HondaBleService.STATE_ACTIVE.equals(state) ? "● " : "") + detail);
        status.setTextColor(HondaBleService.STATE_ACTIVE.equals(state) ? UiKit.GOOD : UiKit.TEXT);
    }

    private void showLearnStep() {
        if (learnIndex >= learnTypes.length) {
            AppConfig.setSetupLearning(this, false);
            showFinish();
            return;
        }
        setContentView(shell());
        root.addView(header("2 de 3", "Aprender controles", (learnIndex + 1) + " de " + learnTypes.length));
        LinearLayout c = UiKit.card(this);

        TextView counter = UiKit.chip(this, String.format("%02d / %02d", learnIndex + 1, learnTypes.length), UiKit.RED, UiKit.RED_SOFT);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
        cp.gravity = Gravity.CENTER_HORIZONTAL;
        c.addView(counter, cp);

        TextView big = UiKit.text(this, learnNames[learnIndex], 34, UiKit.TEXT);
        big.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        big.setGravity(Gravity.CENTER);
        big.setPadding(0, dp(22), 0, dp(10));
        c.addView(big);
        String ins = learnIndex < 4
            ? "Haz UNA pulsación corta " + learnNames[learnIndex].substring(0, 1) + "."
            : "Mantén pulsado " + learnNames[learnIndex].substring(0, 1) + " hasta que se detecte el evento largo.";
        TextView inst = UiKit.body(this, ins);
        inst.setGravity(Gravity.CENTER);
        inst.setPadding(dp(8), 0, dp(8), dp(17));
        c.addView(inst);
        status = UiKit.text(this, "Esperando evento BLE…", 14, UiKit.RED);
        status.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 0, 0, dp(16));
        c.addView(status);
        Button retry = UiKit.secondaryButton(this, "Omitir este control");
        retry.setOnClickListener(x -> { learnIndex++; showLearnStep(); });
        c.addView(retry);
        root.addView(c);
        lastSeenCode = -1;
    }

    private void captureLearning(int code) {
        boolean shortExpected = learnIndex >= 0 && learnIndex < 4;
        if (shortExpected && (code < 0x01 || code > 0x04)) return;
        if (!shortExpected && (code < 0x07 || code > 0x0A)) return;
        GestureEngine.Type type = learnTypes[learnIndex];
        AppConfig.setGestureCode(this, type, code);
        status.setText("Detectado " + GestureEngine.arrowForCode(code) + " ✓");
        status.setTextColor(UiKit.GOOD);
        learnIndex++;
        root.postDelayed(this::showLearnStep, 650);
    }

    private void showFinish() {
        setContentView(shell());
        root.addView(header("3 de 3", "Listo para rodar", "Perfil Honda RoadSync BLE configurado."));
        LinearLayout c = UiKit.card(this);
        TextView h = UiKit.text(this, "Controles aprendidos", 19, UiKit.TEXT);
        h.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        c.addView(h);
        c.addView(UiKit.spacer(this, 9));
        for (GestureEngine.Type type : learnTypes) {
            TextView row = UiKit.text(this, type.symbol + "   ✓", 14, UiKit.MUTED);
            row.setPadding(0, dp(4), 0, dp(4));
            c.addView(row);
        }
        TextView p = UiKit.body(this, "Puedes reaprenderlos cuando quieras desde «Motocicleta».");
        p.setPadding(0, dp(12), 0, dp(15));
        c.addView(p);
        Button done = UiKit.primaryButton(this, "Finalizar y abrir la app");
        done.setOnClickListener(x -> {
            AppConfig.setSetupCompleted(this, true);
            AppConfig.setSetupLearning(this, false);
            CommandRepository.load(this);
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        });
        c.addView(done);
        root.addView(c);
    }

    private LinearLayout header(String step, String title, String subtitle) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        TextView s = UiKit.overline(this, step);
        s.setTextColor(UiKit.RED);
        h.addView(s);
        TextView t = UiKit.title(this, title);
        t.setPadding(0, dp(4), 0, 0);
        h.addView(t);
        TextView p = UiKit.body(this, subtitle);
        p.setPadding(0, dp(6), 0, dp(21));
        h.addView(p);
        return h;
    }

    private int dp(int v) { return UiKit.dp(this, v); }
}
