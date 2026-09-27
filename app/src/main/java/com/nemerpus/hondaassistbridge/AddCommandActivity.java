package com.nemerpus.hondaassistbridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;

public class AddCommandActivity extends Activity {
    Command command;
    TextView gesture, choice;
    Spinner action;
    EditText value, name;
    Button learn;
    boolean learning;
    int firstCode = -1;
    long firstAt;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (!learning || !HondaBleService.KIND_EVENT.equals(i.getStringExtra(HondaBleService.EXTRA_KIND))) return;
            int code = AppConfig.prefs(c).getInt(AppConfig.KEY_LAST_EVENT_CODE, -1);
            if (code >= 0) capture(code);
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        UiKit.applySystemBars(this);
        String id = getIntent().getStringExtra("id");
        if (id != null) {
            for (Command c : CommandRepository.load(this)) if (c.id.equals(id)) command = c;
        }
        if (command == null) {
            command = new Command();
            command.id = UUID.randomUUID().toString();
            command.name = "Nuevo comando";
        }
        setContentView(ui());
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

    private View ui() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(UiKit.BG);
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(20), dp(18), dp(20), dp(34));
        sv.addView(r);
        UiInsets.protectTop(this, r);

        r.addView(UiKit.overline(this, "GESTOS / ACCIONES"));
        r.addView(UiKit.title(this, getIntent().getStringExtra("id") == null ? "Nuevo comando" : "Editar comando"));
        TextView intro = UiKit.body(this, "Asocia un gesto de la piña con una acción. El flujo BLE existente no cambia.");
        intro.setPadding(0, dp(7), 0, dp(22));
        r.addView(intro);

        LinearLayout identity = UiKit.card(this);
        identity.addView(label("NOMBRE"));
        name = new EditText(this);
        name.setText(command.name);
        name.setHint("Nombre del comando");
        UiKit.field(this, name);
        identity.addView(name, new LinearLayout.LayoutParams(-1, dp(54)));
        r.addView(identity);

        r.addView(UiKit.spacer(this, 14));
        LinearLayout gestureCard = UiKit.card(this);
        LinearLayout gestureTop = new LinearLayout(this);
        gestureTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = UiKit.iconTile(this, "↕", UiKit.RED);
        gestureTop.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout gestureCopy = new LinearLayout(this);
        gestureCopy.setOrientation(LinearLayout.VERTICAL);
        gestureCopy.setPadding(dp(14), 0, 0, 0);
        gestureCopy.addView(UiKit.overline(this, "GESTO ASIGNADO"));
        gesture = UiKit.text(this, command.gestureLabel(), 24, UiKit.TEXT);
        gesture.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        gestureCopy.addView(gesture);
        TextView gd = UiKit.text(this, command.gestureDescription(), 13, UiKit.MUTED);
        gd.setPadding(0, dp(3), 0, 0);
        gestureCopy.addView(gd);
        gestureTop.addView(gestureCopy, new LinearLayout.LayoutParams(0, -2, 1));
        gestureCard.addView(gestureTop);
        gestureCard.addView(UiKit.spacer(this, 15));
        learn = UiKit.secondaryButton(this, "Escuchar gesto");
        learn.setOnClickListener(v -> {
            learning = true;
            firstCode = -1;
            gesture.setText("…");
            learn.setText("Pulsa el control en la moto");
        });
        gestureCard.addView(learn);
        r.addView(gestureCard);

        r.addView(UiKit.spacer(this, 14));
        LinearLayout actionCard = UiKit.card(this);
        TextView ah = UiKit.text(this, "Acción", 18, UiKit.TEXT);
        ah.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        actionCard.addView(ah);
        TextView ap = UiKit.body(this, "Elige qué debe ocurrir cuando se detecte el gesto.");
        ap.setPadding(0, dp(5), 0, dp(12));
        actionCard.addView(ap);

        String[] actions = {"Asistente de voz", "Abrir aplicación", "Llamar / marcador", "Abrir navegación", "Multimedia · Play/Pausa", "Multimedia · Siguiente", "Multimedia · Anterior"};
        action = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, actions);
        action.setAdapter(adapter);
        action.setSelection(actionPos(command.actionType));
        UiKit.spinner(this, action);
        actionCard.addView(action, new LinearLayout.LayoutParams(-1, dp(54)));

        choice = UiKit.text(this, "", 14, UiKit.TEXT);
        choice.setPadding(dp(2), dp(12), dp(2), dp(12));
        actionCard.addView(choice);
        value = new EditText(this);
        value.setText(command.actionValue);
        UiKit.field(this, value);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(-1, dp(54));
        valueLp.topMargin = dp(10);
        actionCard.addView(value, valueLp);

        action.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { configureAction(pos); }
        });
        configureAction(action.getSelectedItemPosition());
        r.addView(actionCard);

        r.addView(UiKit.spacer(this, 16));
        Button save = UiKit.primaryButton(this, "Guardar comando");
        save.setOnClickListener(v -> {
            command.name = name.getText().toString().trim();
            command.actionType = actionType(action.getSelectedItemPosition());
            if (command.actionType.equals(Command.ACTION_NAVIGATION) || command.actionType.equals(Command.ACTION_DIAL)) {
                command.actionValue = value.getText().toString().trim();
            }
            CommandRepository.upsert(this, command);
            HondaBleService.requestReconnect(this);
            finish();
        });
        r.addView(save);

        if (getIntent().getStringExtra("id") != null) {
            r.addView(UiKit.spacer(this, 10));
            Button del = UiKit.dangerButton(this, "Eliminar comando");
            del.setOnClickListener(v -> { CommandRepository.delete(this, command.id); finish(); });
            r.addView(del);
        }
        return sv;
    }

    private TextView label(String s) {
        TextView v = UiKit.overline(this, s);
        v.setPadding(0, 0, 0, dp(8));
        return v;
    }

    private void configureAction(int p) {
        if (choice == null) return;
        choice.setVisibility(View.GONE);
        value.setVisibility(View.GONE);
        if (p == 1) {
            choice.setVisibility(View.VISIBLE);
            choice.setText((command.actionLabel == null || command.actionLabel.isBlank()) ? "Seleccionar aplicación\nToca para elegir entre las aplicaciones instaladas" : "📱  " + command.actionLabel + "\nToca para cambiar");
            choice.setOnClickListener(v -> pickApp());
        } else if (p == 2) {
            value.setVisibility(View.VISIBLE);
            value.setHint("Número de teléfono");
        } else if (p == 3) {
            value.setVisibility(View.VISIBLE);
            value.setHint("Destino: Casa, trabajo, dirección…");
        }
    }

    private void pickApp() {
        final ArrayList<AppEntry> apps = new ArrayList<>();
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            CharSequence l = ri.loadLabel(pm);
            apps.add(new AppEntry(l == null ? pkg : l.toString(), pkg));
        }
        apps.sort(Comparator.comparing(a -> a.label, String.CASE_INSENSITIVE_ORDER));
        String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i).label;
        new AlertDialog.Builder(this)
            .setTitle("Selecciona una aplicación")
            .setItems(labels, (d, w) -> {
                AppEntry a = apps.get(w);
                command.actionValue = a.pkg;
                command.actionLabel = a.label;
                choice.setText("📱  " + a.label + "\nToca para cambiar");
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void capture(int code) {
        long now = SystemClock.elapsedRealtime();
        if (code >= 7 && code <= 10) {
            command.doublePress = false;
            command.eventCode = code;
            finishLearn();
            return;
        }
        if (code < 1 || code > 4) return;
        if (firstCode == code && now - firstAt <= 900) {
            command.doublePress = true;
            command.eventCode = code;
            finishLearn();
        } else {
            firstCode = code;
            firstAt = now;
            gesture.setText(GestureEngine.arrowForCode(code) + "  …");
            learn.setText("Repite la pulsación para gesto doble");
        }
    }

    private void finishLearn() {
        learning = false;
        gesture.setText(command.gestureLabel());
        learn.setText("Cambiar gesto");
        Toast.makeText(this, command.gestureDescription() + " aprendido", Toast.LENGTH_SHORT).show();
    }

    private int actionPos(String s) {
        return switch (s) {
            case Command.ACTION_APP -> 1;
            case Command.ACTION_DIAL -> 2;
            case Command.ACTION_NAVIGATION -> 3;
            case Command.ACTION_MEDIA_PLAY_PAUSE -> 4;
            case Command.ACTION_MEDIA_NEXT -> 5;
            case Command.ACTION_MEDIA_PREVIOUS -> 6;
            default -> 0;
        };
    }

    private String actionType(int p) {
        return switch (p) {
            case 1 -> Command.ACTION_APP;
            case 2 -> Command.ACTION_DIAL;
            case 3 -> Command.ACTION_NAVIGATION;
            case 4 -> Command.ACTION_MEDIA_PLAY_PAUSE;
            case 5 -> Command.ACTION_MEDIA_NEXT;
            case 6 -> Command.ACTION_MEDIA_PREVIOUS;
            default -> Command.ACTION_ASSISTANT;
        };
    }

    private int dp(int v) { return UiKit.dp(this, v); }

    static final class AppEntry {
        final String label, pkg;
        AppEntry(String l, String p) { label = l; pkg = p; }
    }
}
