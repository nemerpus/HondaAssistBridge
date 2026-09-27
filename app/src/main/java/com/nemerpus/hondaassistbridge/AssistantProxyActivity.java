package com.nemerpus.hondaassistbridge;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class AssistantProxyActivity extends Activity {
    private static final String TAG = "HondaAssistBridge";

    private static final String GOOGLE_APP = "com.google.android.googlequicksearchbox";
    private static final String HANDS_FREE_ACTIVITY =
            "com.google.android.voicesearch.handsfree.HandsFreeActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setDimAmount(0f);
        if (android.os.Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        FlightRecorder.log(this, "ASSIST", "Proxy abierto · " + FlightRecorder.powerState(this));

        // Esta Activity proxy ya ha sido autorizada para abrirse desde el PendingIntent
        // generado por Honda Assist Bridge. Desde aquí lanzamos EXACTAMENTE la misma
        // entrada pública que utiliza el flujo HFP del intercom: ACTION_VOICE_COMMAND
        // dirigido a Google HandsFreeActivity.
        new Handler(Looper.getMainLooper()).postDelayed(this::openHandsFreeAssistant, 60);
    }

    private void openHandsFreeAssistant() {
        try {
            Intent voice = new Intent(Intent.ACTION_VOICE_COMMAND)
                    .setComponent(new ComponentName(GOOGLE_APP, HANDS_FREE_ACTIVITY))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            Log.i(TAG, "Launching Google HandsFreeActivity via ACTION_VOICE_COMMAND");
            FlightRecorder.log(this, "ASSIST", "HandsFreeActivity iniciada");
            startActivity(voice);
        } catch (Throwable t) {
            Log.e(TAG, "No se pudo abrir Google HandsFreeActivity", t);
            FlightRecorder.log(this, "ASSIST", "HandsFreeActivity ERROR: " + t);

            // Fallback conservador: si Google cambia el nombre del componente en una
            // actualización futura, dejamos que Android resuelva ACTION_VOICE_COMMAND.
            try {
                Intent fallback = new Intent(Intent.ACTION_VOICE_COMMAND)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallback);
                Log.i(TAG, "Fallback ACTION_VOICE_COMMAND lanzado");
            } catch (Throwable fallbackError) {
                Log.e(TAG, "Fallback ACTION_VOICE_COMMAND falló", fallbackError);
            }
        } finally {
            finish();
            overridePendingTransition(0, 0);
        }
    }
}
