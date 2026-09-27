package com.nemerpus.hondaassistbridge;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    @Override public void onCreate(Bundle x) {
        super.onCreate(x);
        UiKit.applySystemBars(this);
        setContentView(build());
    }

    View build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(UiKit.BG);
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(20), dp(18), dp(20), dp(34));
        UiInsets.protectTop(this, r);
        sv.addView(r);

        r.addView(UiKit.overline(this, "SISTEMA"));
        r.addView(UiKit.title(this, "Configuración"));
        TextView intro = UiKit.body(this, "Ajustes esenciales de conexión y ejecución en segundo plano.");
        intro.setPadding(0, dp(7), 0, dp(22));
        r.addView(intro);

        LinearLayout c = UiKit.card(this);
        TextView h = UiKit.text(this, "Conexión automática", 18, UiKit.TEXT);
        h.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        c.addView(h);
        TextView copy = UiKit.body(this, "Mantiene la escucha preparada cuando Android detecta la presencia de la motocicleta mediante Companion.");
        copy.setPadding(0, dp(5), 0, dp(12));
        c.addView(copy);

        Switch sw = new Switch(this);
        sw.setText("Escucha automática");
        sw.setTextColor(UiKit.TEXT);
        sw.setTextSize(15);
        sw.setChecked(AppConfig.isListeningEnabled(this));
        sw.setPadding(0, dp(4), 0, dp(4));
        sw.setOnCheckedChangeListener((v, on) -> {
            AppConfig.setListeningEnabled(this, on);
            if (on) HondaBleService.requestStart(this, true);
            else HondaBleService.requestStop(this);
        });
        c.addView(sw);
        r.addView(c);
        return sv;
    }

    int dp(int x) { return UiKit.dp(this, x); }
}
