package com.nemerpus.hondaassistbridge;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    static final int REQ = 50;
    LinearLayout root, commands;
    TextView status, vehicle;
    boolean registered;

    final BroadcastReceiver rx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { refresh(); }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        UiKit.applySystemBars(this);
        if (!AppConfig.isSetupCompleted(this)) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        VehicleRepository.load(this);
        setContentView(build());
        requestPermissionsIfNeeded();
        CompanionController.ensureObservation(this);
        if (!CompanionController.supported(this) && hasBt() && AppConfig.isListeningEnabled(this)) {
            HondaBleService.requestStart(this, true);
        }
        FlightRecorder.log(this, "UI", "MainActivity v0.12.1 abierta");
    }

    @Override protected void onResume() {
        super.onResume();
        if (!registered) {
            IntentFilter f = new IntentFilter(HondaBleService.ACTION_UPDATE);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(rx, f, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(rx, f);
            registered = true;
        }
        CompanionController.ensureObservation(this);
        refresh();
    }

    @Override protected void onPause() {
        if (registered) {
            try { unregisterReceiver(rx); } catch (Throwable ignored) {}
            registered = false;
        }
        super.onPause();
    }

    View build() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(UiKit.BG);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(8), dp(20), dp(36));
        UiInsets.protectTop(this, root);
        sv.addView(root);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout brandBlock = new LinearLayout(this);
        brandBlock.setOrientation(LinearLayout.VERTICAL);
        TextView brand = UiKit.text(this, "HONDA ASSIST", 22, UiKit.TEXT);
        brand.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        brand.setLetterSpacing(.035f);
        brandBlock.addView(brand);
        TextView brandSub = UiKit.text(this, "RIDE CONTROL", 10, UiKit.RED);
        brandSub.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        brandSub.setLetterSpacing(.18f);
        brandBlock.addView(brandSub);
        bar.addView(brandBlock, new LinearLayout.LayoutParams(0, dp(64), 1));

        TextView menu = UiKit.iconTile(this, "☰", UiKit.TEXT);
        menu.setTextSize(19);
        menu.setOnClickListener(v -> showMenu());
        bar.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(bar);

        root.addView(UiKit.spacer(this, 4));
        TextView headline = UiKit.title(this, "Tu moto. Tu ruta. Tu control.");
        root.addView(headline);
        TextView sub = UiKit.body(this, "Una interfaz centrada en la conducción, inspirada en la CB650R e‑Clutch y diseñada para operar sin distracciones.");
        sub.setPadding(0, dp(7), 0, 0);
        root.addView(sub);
        root.addView(UiKit.spacer(this, 20));

        LinearLayout hero = UiKit.card(this);
        UiKit.makeClickableCard(this, hero);

        LinearLayout heroTop = new LinearLayout(this);
        heroTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView routeMark = UiKit.iconTile(this, "⌁", UiKit.RED);
        routeMark.setTextSize(25);
        heroTop.addView(routeMark, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(14), 0, 0, 0);
        TextView eyebrow = UiKit.overline(this, "MOTOCICLETA ACTIVA");
        labels.addView(eyebrow);
        vehicle = UiKit.text(this, "Mi motocicleta", 23, UiKit.TEXT);
        vehicle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        vehicle.setPadding(0, dp(3), 0, 0);
        labels.addView(vehicle);
        status = UiKit.text(this, "Comprobando conexión…", 13, UiKit.MUTED);
        status.setPadding(0, dp(5), 0, 0);
        labels.addView(status);
        heroTop.addView(labels, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        hero.addView(heroTop);

        View divider = new View(this);
        divider.setBackgroundColor(UiKit.STROKE);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, dp(1));
        dividerLp.setMargins(0, dp(17), 0, dp(15));
        hero.addView(divider, dividerLp);

        LinearLayout telemetry = new LinearLayout(this);
        telemetry.setGravity(Gravity.CENTER_VERTICAL);
        TextView road = UiKit.chip(this, "ROADSYNC", UiKit.TEXT, UiKit.SURFACE2);
        telemetry.addView(road);
        TextView eclutch = UiKit.chip(this, "e‑CLUTCH STYLE", UiKit.RED, UiKit.RED_SOFT);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(-2, -2);
        chipLp.setMargins(dp(8), 0, 0, 0);
        telemetry.addView(eclutch, chipLp);
        hero.addView(telemetry);

        TextView hint = UiKit.body(this, "Gestiona conexión, emparejamiento y aprendizaje de la piña.");
        hint.setPadding(0, dp(13), 0, 0);
        hero.addView(hint);
        hero.setOnClickListener(v -> open(VehicleActivity.class));
        root.addView(hero);

        root.addView(UiKit.spacer(this, 28));
        root.addView(UiKit.section(this, "COMANDOS DE CONDUCCIÓN"));
        commands = new LinearLayout(this);
        commands.setOrientation(LinearLayout.VERTICAL);
        root.addView(commands);
        root.addView(UiKit.spacer(this, 4));

        Button add = UiKit.primaryButton(this, "＋  Añadir comando");
        add.setOnClickListener(v -> open(AddCommandActivity.class));
        root.addView(add);

        root.addView(UiKit.spacer(this, 28));
        root.addView(UiKit.section(this, "CENTRO DE CONTROL"));
        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.addView(shortcut("◉", "Motocicleta", "Perfil y BTU", VehicleActivity.class), new LinearLayout.LayoutParams(0, dp(122), 1));
        View gap = new View(this);
        quick.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        quick.addView(shortcut("✦", "Diagnóstico", "Estado y registro", DiagnosticsActivity.class), new LinearLayout.LayoutParams(0, dp(122), 1));
        root.addView(quick);
        return sv;
    }

    void refresh() {
        VehicleProfile v = VehicleRepository.active(this);
        vehicle.setText(v == null ? "Mi motocicleta" : v.displayName());
        boolean associated = CompanionController.hasAssociation(this);
        boolean present = AppConfig.isCompanionPresent(this);
        String st = AppConfig.prefs(this).getString(AppConfig.KEY_LAST_STATE, HondaBleService.STATE_WAITING);
        boolean active = HondaBleService.STATE_ACTIVE.equals(st);
        if (!associated) {
            status.setText("● Vinculación requerida");
            status.setTextColor(UiKit.WARN);
        } else if (active) {
            status.setText("● HONDA BTU conectada · escucha activa");
            status.setTextColor(UiKit.GOOD);
        } else if (present) {
            status.setText("● BTU presente · conectando…");
            status.setTextColor(UiKit.WARN);
        } else {
            status.setText("● Esperando HONDA BTU");
            status.setTextColor(UiKit.MUTED);
        }

        commands.removeAllViews();
        List<Command> all = CommandRepository.load(this);
        if (all.isEmpty()) {
            LinearLayout empty = UiKit.card(this);
            TextView h = UiKit.text(this, "Sin comandos todavía", 17, UiKit.TEXT);
            h.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            empty.addView(h);
            TextView e = UiKit.body(this, "Crea un gesto de la piña para abrir Gemini o ejecutar una acción de Android.");
            e.setPadding(0, dp(6), 0, 0);
            empty.addView(e);
            commands.addView(empty);
            commands.addView(UiKit.spacer(this, 10));
        }

        for (Command c : all) {
            LinearLayout row = UiKit.card(this);
            UiKit.makeClickableCard(this, row);
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);

            TextView icon = UiKit.iconTile(this, c.gestureLabel(), UiKit.RED);
            icon.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            line.addView(icon, new LinearLayout.LayoutParams(dp(66), dp(54)));

            LinearLayout tx = new LinearLayout(this);
            tx.setOrientation(LinearLayout.VERTICAL);
            tx.setPadding(dp(14), 0, dp(8), 0);
            TextView n = UiKit.text(this, c.name, 16, UiKit.TEXT);
            n.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            tx.addView(n);
            TextView a = UiKit.text(this, action(c), 13, UiKit.MUTED);
            a.setPadding(0, dp(4), 0, 0);
            tx.addView(a);
            line.addView(tx, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            line.addView(UiKit.text(this, "›", 30, UiKit.MUTED));
            row.addView(line);
            row.setOnClickListener(x -> startActivity(new Intent(this, AddCommandActivity.class).putExtra("id", c.id)));
            commands.addView(row);
            commands.addView(UiKit.spacer(this, 10));
        }
    }

    View shortcut(String icon, String label, String caption, Class<?> c) {
        LinearLayout v = UiKit.card(this);
        UiKit.makeClickableCard(this, v);
        TextView i = UiKit.text(this, icon, 24, UiKit.RED);
        v.addView(i);
        TextView l = UiKit.text(this, label, 15, UiKit.TEXT);
        l.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        l.setPadding(0, dp(12), 0, 0);
        v.addView(l);
        TextView s = UiKit.text(this, caption, 12, UiKit.MUTED);
        s.setPadding(0, dp(3), 0, 0);
        v.addView(s);
        v.setOnClickListener(x -> open(c));
        return v;
    }

    void showMenu() {
        final Dialog d = new Dialog(this);
        LinearLayout m = new LinearLayout(this);
        m.setOrientation(LinearLayout.VERTICAL);
        m.setPadding(dp(22), dp(28), dp(22), dp(28));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiKit.SURFACE);
        bg.setCornerRadii(new float[]{0, 0, dp(28), dp(28), dp(28), dp(28), 0, 0});
        m.setBackground(bg);

        TextView over = UiKit.overline(this, "RIDE CONTROL");
        over.setTextColor(UiKit.RED);
        m.addView(over);
        TextView h = UiKit.text(this, "Honda Assist", 27, UiKit.TEXT);
        h.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        h.setPadding(0, dp(4), 0, 0);
        m.addView(h);
        TextView sub = UiKit.body(this, "Control local para tu motocicleta");
        sub.setPadding(0, dp(5), 0, dp(22));
        m.addView(sub);

        addMenu(m, d, "⌂", "Inicio", MainActivity.class);
        addMenu(m, d, "＋", "Añadir comando", AddCommandActivity.class);
        addMenu(m, d, "◉", "Motocicleta", VehicleActivity.class);
        addMenu(m, d, "⚙", "Configuración", SettingsActivity.class);
        addMenu(m, d, "✦", "Diagnóstico", DiagnosticsActivity.class);
        m.addView(UiKit.spacer(this, 12));
        addMenu(m, d, "ⓘ", "Acerca de", AboutActivity.class);
        addMenu(m, d, "§", "Legal y privacidad", LegalActivity.class);

        d.setContentView(m);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int)(getResources().getDisplayMetrics().widthPixels * .86), WindowManager.LayoutParams.MATCH_PARENT);
            w.setGravity(Gravity.START);
        }
    }

    void addMenu(LinearLayout m, Dialog d, String glyph, String label, Class<?> c) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), 0, dp(10), 0);
        row.setBackground(UiKit.roundRect(this, Color.TRANSPARENT, 16, Color.TRANSPARENT, 0));
        TextView i = UiKit.text(this, glyph, 18, c == MainActivity.class ? UiKit.RED : UiKit.MUTED);
        i.setGravity(Gravity.CENTER);
        row.addView(i, new LinearLayout.LayoutParams(dp(36), dp(54)));
        TextView x = UiKit.text(this, label, 16, UiKit.TEXT);
        x.setGravity(Gravity.CENTER_VERTICAL);
        x.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.addView(x, new LinearLayout.LayoutParams(0, dp(54), 1));
        row.setOnClickListener(v -> {
            d.dismiss();
            if (c != MainActivity.class) open(c);
        });
        m.addView(row, new LinearLayout.LayoutParams(-1, dp(54)));
    }

    void open(Class<?> c) { startActivity(new Intent(this, c)); }

    String action(Command c) {
        return switch (c.actionType) {
            case Command.ACTION_APP -> (c.actionLabel == null || c.actionLabel.isBlank() ? "Aplicación" : c.actionLabel);
            case Command.ACTION_DIAL -> "Marcador";
            case Command.ACTION_NAVIGATION -> "Navegación";
            case Command.ACTION_MEDIA_PLAY_PAUSE -> "Play / Pausa";
            case Command.ACTION_MEDIA_NEXT -> "Siguiente pista";
            case Command.ACTION_MEDIA_PREVIOUS -> "Pista anterior";
            default -> "Asistente de voz";
        };
    }

    void requestPermissionsIfNeeded() {
        ArrayList<String> w = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) w.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) w.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) w.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!w.isEmpty()) requestPermissions(w.toArray(new String[0]), REQ);
    }

    boolean hasBt() {
        return Build.VERSION.SDK_INT < 31 ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
    }

    int dp(int x) { return UiKit.dp(this, x); }
}
