package com.nemerpus.hondaassistbridge;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class LegalActivity extends Activity {
    @Override public void onCreate(Bundle x) {
        super.onCreate(x);
        UiKit.applySystemBars(this);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(UiKit.BG);
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(20), dp(18), dp(20), dp(34));
        UiInsets.protectTop(this, r);
        sv.addView(r);

        r.addView(UiKit.overline(this, "INFORMACIÓN"));
        r.addView(UiKit.title(this, "Legal y privacidad"));
        TextView intro = UiKit.body(this, "Información esencial sobre datos, conducción y marcas.");
        intro.setPadding(0, dp(7), 0, dp(22));
        r.addView(intro);

        addSection(r, "Privacidad", "Los perfiles, matrícula, comandos y diagnósticos se almacenan localmente. Esta versión no declara permiso de Internet.");
        r.addView(UiKit.spacer(this, 12));
        addSection(r, "Conducción", "Configura y revisa la aplicación con la motocicleta detenida. Evita acciones que requieran mirar o manipular el teléfono mientras conduces.");
        r.addView(UiKit.spacer(this, 12));
        addSection(r, "Marcas", "Proyecto independiente. Honda, Gemini y otras marcas pertenecen a sus respectivos titulares. No existe afiliación ni respaldo oficial.");
        setContentView(sv);
    }

    void addSection(LinearLayout root, String title, String body) {
        LinearLayout c = UiKit.card(this);
        TextView h = UiKit.text(this, title, 18, UiKit.TEXT);
        h.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        c.addView(h);
        TextView p = UiKit.body(this, body);
        p.setTextColor(UiKit.TEXT);
        p.setPadding(0, dp(8), 0, 0);
        c.addView(p);
        root.addView(c);
    }

    int dp(int x) { return UiKit.dp(this, x); }
}
