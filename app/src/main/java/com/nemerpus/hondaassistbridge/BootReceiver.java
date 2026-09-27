package com.nemerpus.hondaassistbridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "HondaAssistBridge";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!AppConfig.isListeningEnabled(context)) return;

        String action = intent == null ? "" : intent.getAction();
        Log.i(TAG, "Auto-start receiver: " + action);
        FlightRecorder.log(context, "BOOT", "receiver=" + action);
        CompanionController.ensureObservation(context);
    }
}
