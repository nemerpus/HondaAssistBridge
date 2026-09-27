package com.nemerpus.hondaassistbridge;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class AboutActivity extends Activity {
    @Override public void onCreate(Bundle x) {
        super.onCreate(x);
        UiKit.applySystemBars(this);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(UiKit.BG);
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(22), dp(22), dp(22), dp(34));
        UiInsets.protectTop(this, r);
        sv.addView(r);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(82), dp(82));
        ip.gravity = Gravity.CENTER_HORIZONTAL;
        r.addView(icon, ip);

        TextView over = UiKit.overline(this, "RIDE CONTROL");
        over.setTextColor(UiKit.RED);
        over.setGravity(Gravity.CENTER);
        over.setPadding(0, dp(18), 0, dp(4));
        r.addView(over);
        TextView h = UiKit.title(this, "Honda Assist Bridge");
        h.setGravity(Gravity.CENTER);
        r.addView(h);
        TextView ver = UiKit.text(this, "v0.12.1 · Ride Control UI", 13, UiKit.MUTED);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(6), 0, dp(24));
        r.addView(ver);

        LinearLayout c = UiKit.card(this);
        TextView ch = UiKit.text(this, "Diseñado para la ruta", 18, UiKit.TEXT);
        ch.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        c.addView(ch);
        TextView body = UiKit.body(this, "Controla acciones de Android desde los mandos compatibles de tu motocicleta con una experiencia local, rápida y orientada a minimizar distracciones.\n\nProyecto independiente. La identidad visual de esta versión toma referencias del lenguaje de diseño deportivo de la CB650R e‑Clutch sin presentarse como producto oficial de Honda.");
        body.setTextColor(UiKit.TEXT);
        body.setPadding(0, dp(9), 0, 0);
        c.addView(body);
        r.addView(c);
        setContentView(sv);
    }

    int dp(int x) { return UiKit.dp(this, x); }
}
