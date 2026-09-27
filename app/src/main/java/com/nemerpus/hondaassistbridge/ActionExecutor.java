package com.nemerpus.hondaassistbridge;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.view.KeyEvent;

final class ActionExecutor {
    static void execute(Context context, Command c) {
        FlightRecorder.log(context, "ACTION", "Ejecutando " + c.name + " type=" + c.actionType + " · " + FlightRecorder.powerState(context));
        try {
            switch (c.actionType) {
                case Command.ACTION_ASSISTANT -> AssistantLauncher.launch(context);
                case Command.ACTION_APP -> {
                    Intent i = context.getPackageManager().getLaunchIntentForPackage(c.actionValue);
                    if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(i); }
                }
                case Command.ACTION_DIAL -> {
                    Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(c.actionValue)));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(i);
                }
                case Command.ACTION_NAVIGATION -> {
                    Uri uri = Uri.parse("google.navigation:q=" + Uri.encode(c.actionValue));
                    Intent i = new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(i);
                }
                case Command.ACTION_MEDIA_PLAY_PAUSE -> media(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                case Command.ACTION_MEDIA_NEXT -> media(context, KeyEvent.KEYCODE_MEDIA_NEXT);
                case Command.ACTION_MEDIA_PREVIOUS -> media(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            }
        } catch (Throwable t) { android.util.Log.e("HondaAssistBridge", "Acción fallida: " + c.name, t); FlightRecorder.log(context, "ACTION", "ERROR " + c.name + ": " + t); }
    }
    private static void media(Context c, int key) {
        AudioManager a = c.getSystemService(AudioManager.class);
        if (a != null) { a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key)); a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key)); }
    }
    private ActionExecutor() {}
}
