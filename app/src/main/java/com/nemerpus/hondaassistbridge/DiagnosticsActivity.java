package com.nemerpus.hondaassistbridge;

import android.app.Activity;
import android.content.ClipData;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class DiagnosticsActivity extends Activity {
    TextView state, log;

    @Override public void onCreate(Bundle x) {
        super.onCreate(x);
        UiKit.applySystemBars(this);
        setContentView(build());
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    View build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(UiKit.BG);
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(20), dp(18), dp(20), dp(34));
        UiInsets.protectTop(this, r);
        sv.addView(r);

        r.addView(UiKit.overline(this, "SERVICE / STATUS"));
        r.addView(UiKit.title(this, "Diagnóstico"));
        TextView intro = UiKit.body(this, "Comprueba el enlace con la BTU y consulta el registro local de la aplicación.");
        intro.setPadding(0, dp(7), 0, dp(22));
        r.addView(intro);

        LinearLayout c = UiKit.card(this);
        TextView ch = UiKit.text(this, "Estado actual", 18, UiKit.TEXT);
        ch.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        c.addView(ch);
        state = UiKit.text(this, "", 14, UiKit.TEXT);
        state.setLineSpacing(0, 1.22f);
        state.setPadding(0, dp(9), 0, dp(16));
        c.addView(state);

        Button rec = UiKit.primaryButton(this, "Reconectar BTU");
        rec.setOnClickListener(v -> HondaBleService.requestReconnect(this));
        c.addView(rec);
        c.addView(UiKit.spacer(this, 9));
        Button gem = UiKit.secondaryButton(this, "Probar Gemini");
        gem.setOnClickListener(v -> AssistantLauncher.launch(this));
        c.addView(gem);
        c.addView(UiKit.spacer(this, 9));
        Button copy = UiKit.secondaryButton(this, "Copiar diagnóstico");
        copy.setOnClickListener(v -> {
            String s = FlightRecorder.read(this);
            android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Honda Assist Bridge diagnóstico", s));
            Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show();
        });
        c.addView(copy);
        c.addView(UiKit.spacer(this, 9));
        Button clear = UiKit.dangerButton(this, "Borrar registro");
        clear.setOnClickListener(v -> { FlightRecorder.clear(this); refresh(); });
        c.addView(clear);
        r.addView(c);

        r.addView(UiKit.spacer(this, 22));
        r.addView(UiKit.section(this, "REGISTRO LOCAL"));
        LinearLayout logCard = UiKit.card(this);
        log = UiKit.text(this, "", 11, UiKit.MUTED);
        log.setTextIsSelectable(true);
        log.setTypeface(Typeface.MONOSPACE);
        log.setGravity(Gravity.START);
        logCard.addView(log);
        r.addView(logCard);
        return sv;
    }

    void refresh() {
        String st = AppConfig.prefs(this).getString(AppConfig.KEY_LAST_STATUS, "Esperando HONDA BTU");
        state.setText("Companion  ·  " + (AppConfig.isCompanionEnabled(this) ? "asociado" : "no asociado") +
            "\nPresencia     ·  " + AppConfig.isCompanionPresent(this) +
            "\nEstado        ·  " + st +
            "\nApp visible   ·  " + AppVisibilityTracker.isForeground());
        log.setText(FlightRecorder.read(this));
    }

    int dp(int x) { return UiKit.dp(this, x); }
}
