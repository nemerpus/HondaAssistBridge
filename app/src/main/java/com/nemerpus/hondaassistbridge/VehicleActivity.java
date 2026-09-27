package com.nemerpus.hondaassistbridge;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class VehicleActivity extends Activity {
    EditText brand, model, reg, nick;

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

        r.addView(UiKit.overline(this, "GARAGE / PERFIL"));
        r.addView(UiKit.title(this, "Motocicleta"));
        TextView intro = UiKit.body(this, "Identidad del vehículo, conexión HONDA BTU y aprendizaje de controles.");
        intro.setPadding(0, dp(7), 0, dp(22));
        r.addView(intro);

        VehicleProfile v = VehicleRepository.active(this);
        if (v == null) v = VehicleRepository.create(this);

        LinearLayout identity = UiKit.card(this);
        TextView ih = UiKit.text(this, "Perfil activo", 18, UiKit.TEXT);
        ih.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        identity.addView(ih);
        TextView hint = UiKit.body(this, "Personaliza cómo aparece tu moto dentro de Honda Assist.");
        hint.setPadding(0, dp(4), 0, dp(16));
        identity.addView(hint);
        brand = e(identity, "MARCA", v.brand);
        model = e(identity, "MODELO", v.model);
        reg = e(identity, "MATRÍCULA", v.registration);
        nick = e(identity, "NOMBRE OPCIONAL", v.nickname);
        r.addView(identity);

        r.addView(UiKit.spacer(this, 16));
        LinearLayout connection = UiKit.card(this);
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView bt = UiKit.iconTile(this, "⌁", UiKit.RED);
        statusRow.addView(bt, new LinearLayout.LayoutParams(dp(50), dp(50)));
        LinearLayout st = new LinearLayout(this);
        st.setOrientation(LinearLayout.VERTICAL);
        st.setPadding(dp(13), 0, 0, 0);
        TextView ch = UiKit.text(this, "HONDA BTU", 17, UiKit.TEXT);
        ch.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        st.addView(ch);
        boolean associated = AppConfig.isCompanionEnabled(this);
        st.addView(UiKit.text(this, associated ? "● Dispositivo complementario vinculado" : "● Pendiente de vincular", 13, associated ? UiKit.GOOD : UiKit.WARN));
        statusRow.addView(st, new LinearLayout.LayoutParams(0, -2, 1));
        connection.addView(statusRow);
        connection.addView(UiKit.spacer(this, 15));

        Button pair = UiKit.secondaryButton(this, "Vincular / cambiar HONDA BTU");
        pair.setOnClickListener(q -> startActivity(new Intent(this, PairDeviceActivity.class)));
        connection.addView(pair);
        connection.addView(UiKit.spacer(this, 10));
        Button controls = UiKit.secondaryButton(this, "Configurar controles de la piña");
        controls.setOnClickListener(q -> startActivity(new Intent(this, SetupActivity.class).putExtra("reconfigure", true)));
        connection.addView(controls);
        r.addView(connection);

        r.addView(UiKit.spacer(this, 16));
        Button save = UiKit.primaryButton(this, "Guardar perfil");
        VehicleProfile fv = v;
        save.setOnClickListener(q -> {
            fv.brand = brand.getText().toString();
            fv.model = model.getText().toString();
            fv.registration = reg.getText().toString().toUpperCase();
            fv.nickname = nick.getText().toString();
            fv.deviceAddress = AppConfig.getCompanionAddress(this);
            VehicleRepository.save(this, fv);
            Toast.makeText(this, "Perfil guardado", Toast.LENGTH_SHORT).show();
        });
        r.addView(save);
        r.addView(UiKit.spacer(this, 10));
        Button add = UiKit.secondaryButton(this, "＋ Añadir otra motocicleta");
        add.setOnClickListener(q -> {
            VehicleRepository.create(this);
            Toast.makeText(this, "Nuevo perfil creado", Toast.LENGTH_SHORT).show();
            recreate();
        });
        r.addView(add);
        return sv;
    }

    EditText e(LinearLayout c, String label, String val) {
        TextView l = UiKit.overline(this, label);
        l.setPadding(0, dp(6), 0, dp(7));
        c.addView(l);
        EditText e = new EditText(this);
        e.setText(val == null ? "" : val);
        UiKit.field(this, e);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.bottomMargin = dp(10);
        c.addView(e, lp);
        return e;
    }

    int dp(int x) { return UiKit.dp(this, x); }
}
