package com.nemerpus.hondaassistbridge;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

final class FlightRecorder {
    private static final String TAG = "HondaAssistBridge";
    private static final String FILE = "field-diagnostic.log";
    private static final long MAX = 256 * 1024L;

    static synchronized void log(Context c, String area, String message) {
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(new Date())
                + "  " + area + "  " + message;
        Log.i(TAG, "[flight] " + area + " " + message);
        File f = new File(c.getFilesDir(), FILE);
        try {
            if (f.exists() && f.length() > MAX) rotate(f);
            try (FileWriter w = new FileWriter(f, true)) { w.write(line + "\n"); }
        } catch (Throwable t) { Log.w(TAG, "No se pudo escribir diagnóstico", t); }
    }

    static synchronized String read(Context c) {
        File f = new File(c.getFilesDir(), FILE);
        if (!f.exists()) return "Todavía no hay registros de campo.";
        StringBuilder s = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line; while ((line = r.readLine()) != null) s.append(line).append('\n');
        } catch (Throwable t) { return "Error leyendo diagnóstico: " + t; }
        return s.toString();
    }

    static synchronized void clear(Context c) {
        File f = new File(c.getFilesDir(), FILE); if (f.exists()) f.delete();
        log(c, "SYSTEM", "Diagnóstico borrado · nueva sesión");
    }

    static String powerState(Context c) {
        PowerManager pm = c.getSystemService(PowerManager.class);
        if (pm == null) return "power=?";
        boolean interactive = pm.isInteractive();
        boolean idle = Build.VERSION.SDK_INT >= 23 && pm.isDeviceIdleMode();
        return "screen=" + (interactive ? "ON" : "OFF") + " doze=" + idle;
    }

    private static void rotate(File f) throws IOException {
        File old = new File(f.getParentFile(), FILE + ".old");
        if (old.exists()) old.delete();
        if (!f.renameTo(old)) try (FileOutputStream ignored = new FileOutputStream(f, false)) {}
    }
    private FlightRecorder() {}
}
