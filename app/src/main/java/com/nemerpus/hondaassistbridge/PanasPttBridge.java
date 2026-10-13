package com.nemerpus.hondaassistbridge;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Push-to-talk for PanaS from a long press on the handlebar switch.
 *
 * <p>The BTU reports a long press as one event code (0x07–0x0A). Depending on firmware it then
 * repeats that code while the button stays held, sends another code on release, or sends nothing.
 * This tracker handles the three cases without guessing:</p>
 * <ul>
 *   <li>repeats while held → keep talking, stop {@link #REPEAT_GAP_MS} after the last repeat;</li>
 *   <li>any other code → that is the release, stop at once;</li>
 *   <li>nothing → a second long press stops (toggle), with a {@link #MAX_TALK_MS} safety stop.</li>
 * </ul>
 * Every transition is written to the flight recorder so the real BTU behaviour can be checked.
 */
final class PanasPttBridge {
    static final String PANAS_PERMISSION = "com.nemerpus.panas.permission.PUSH_TO_TALK";
    static final String ACTION = "com.nemerpus.panas.action.PUSH_TO_TALK";
    private static final String[] PANAS_PACKAGES = {"com.nemerpus.panas", "com.nemerpus.panas.debug"};
    static final long REPEAT_WINDOW_MS = 1200L;
    static final long REPEAT_GAP_MS = 700L;
    static final long MAX_TALK_MS = 60_000L;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean talking;
    private boolean repeatsSeen;
    private long lastTriggerAt;
    private int triggerCode = -1;
    private final Runnable repeatTimeout = () -> stop("REPEAT_TIMEOUT");
    private final Runnable safetyTimeout = () -> stop("MAX_DURATION");

    PanasPttBridge(Context context) { this.context = context.getApplicationContext(); }

    /** @return true when the event belongs to push-to-talk and must not reach other commands. */
    boolean onEvent(Command ptt, int code) {
        long now = SystemClock.elapsedRealtime();
        if (ptt == null) { if (talking) stop("COMMAND_REMOVED"); return false; }
        int target = ptt.eventCode & 0xFF;
        if (!talking) {
            if (code != target) return false;
            talking = true; repeatsSeen = false; lastTriggerAt = now; triggerCode = code;
            handler.postDelayed(safetyTimeout, MAX_TALK_MS);
            send(true, "LONG_PRESS");
            return true;
        }
        if (code == triggerCode) {
            if (now - lastTriggerAt <= REPEAT_WINDOW_MS) {
                // Held button: the BTU keeps repeating the long code.
                if (!repeatsSeen) FlightRecorder.log(context, "PANAS_PTT", "BTU repite el código mientras se mantiene");
                repeatsSeen = true; lastTriggerAt = now;
                handler.removeCallbacks(repeatTimeout);
                handler.postDelayed(repeatTimeout, REPEAT_GAP_MS);
            } else {
                stop("SECOND_LONG_PRESS");
            }
            return true;
        }
        stop("RELEASE_CODE_" + GestureEngine.hex(code));
        return true;
    }

    void reset() { if (talking) stop("RESET"); }

    private void stop(String reason) {
        if (!talking) return;
        talking = false;
        handler.removeCallbacks(repeatTimeout);
        handler.removeCallbacks(safetyTimeout);
        send(false, reason);
    }

    private void send(boolean pressed, String reason) {
        FlightRecorder.log(context, "PANAS_PTT", (pressed ? "INICIO " : "FIN ") + reason);
        for (String pkg : PANAS_PACKAGES) {
            try {
                Intent i = new Intent(ACTION).setPackage(pkg)
                        .putExtra("pressed", pressed)
                        .putExtra("source", "honda_btu")
                        .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                context.sendBroadcast(i, null);
            } catch (Throwable t) {
                FlightRecorder.log(context, "PANAS_PTT", "error " + pkg + ": " + t.getClass().getSimpleName());
            }
        }
    }

    static boolean panasInstalled(Context c) {
        for (String pkg : PANAS_PACKAGES) {
            try { c.getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception ignored) { }
        }
        return false;
    }

    static boolean permissionGranted(Context c) {
        return c.checkSelfPermission(PANAS_PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
}
