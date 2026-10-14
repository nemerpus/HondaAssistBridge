package com.nemerpus.hondaassistbridge;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Push-to-talk for PanaS from the handlebar switch. Two modes, chosen by the command's gesture:
 *
 * <p><b>Tap (←, short code 01–04):</b> one tap opens the microphone, the next tap closes it, so the rider talks
 * in intervals, with no delay. If a second tap follows within {@link #DOUBLE_AFTER_TAP_MS} (a double press for
 * Gemini / another command) or the long code within {@link #HOLD_AFTER_TAP_MS} (a hold), the toggle is undone
 * and the presses go to the other commands. Safety stop after {@link #MAX_TAP_TALK_MS}.</p>
 *
 * <p><b>Hold (long code 07–0A):</b></p>
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
    static final long DOUBLE_AFTER_TAP_MS = 600L;
    static final long HOLD_AFTER_TAP_MS = 1_500L;
    static final long MAX_TAP_TALK_MS = 120_000L;

    /** Receives the events a tap turned out not to be (double press, hold) so the other commands still work. */
    interface Forward { void event(int code); }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean talking;
    private boolean repeatsSeen;
    private long lastTriggerAt;
    private int triggerCode = -1;
    private final Runnable repeatTimeout = () -> stop("REPEAT_TIMEOUT");
    private final Runnable safetyTimeout = () -> stop("MAX_DURATION");

    private Forward forward = code -> { };
    private int lastTapCode = -1;
    private long lastTapAt;

    PanasPttBridge(Context context) { this.context = context.getApplicationContext(); }
    PanasPttBridge(Context context, Forward forward) { this(context); this.forward = forward; }

    static boolean isTapCode(int code) { return code >= 0x01 && code <= 0x04; }

    /**
     * Tap mode without delay: every tap opens/closes the microphone at once. If the tap turns out to be the
     * start of a double press or of a hold (the BTU sends the short code first), that toggle is undone and
     * the presses go to the other commands (e.g. Gemini). Worst case: a short open/close beep.
     */
    private boolean onTapEvent(int target, int code) {
        long now = SystemClock.elapsedRealtime();
        boolean recent = lastTapCode >= 0 && now - lastTapAt <= HOLD_AFTER_TAP_MS;
        if (recent && code == target + 6) {                       // it was a hold
            undoTap("mantener a los " + (now - lastTapAt) + " ms"); forward.event(code); return true;
        }
        if (recent && code == target && now - lastTapAt <= DOUBLE_AFTER_TAP_MS && hasDoublePress(code)) {
            undoTap("doble pulsación a los " + (now - lastTapAt) + " ms"); forward.event(target); forward.event(code); return true;
        }
        if (code != target) { lastTapCode = -1; return false; }
        lastTapCode = code; lastTapAt = now;
        toggleTap();
        return true;
    }

    private void toggleTap() {
        if (talking) { stop("TAP"); return; }
        talking = true; triggerCode = -1;
        handler.postDelayed(safetyTimeout, MAX_TAP_TALK_MS);
        send(true, "TAP");
    }

    private void undoTap(String why) {
        lastTapCode = -1;
        FlightRecorder.log(context, "PANAS_PTT", "toque deshecho: " + why);
        toggleTap();
    }

    private boolean hasDoublePress(int code) {
        for (Command c : CommandRepository.load(context)) if (c.enabled && c.doublePress && (c.eventCode & 0xFF) == code) return true;
        return false;
    }

    /** @return true when the event belongs to push-to-talk and must not reach other commands. */
    boolean onEvent(Command ptt, int code) {
        long now = SystemClock.elapsedRealtime();
        if (ptt == null) { if (talking) stop("COMMAND_REMOVED"); return false; }
        int target = ptt.eventCode & 0xFF;
        if (isTapCode(target)) return onTapEvent(target, code);
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

    void reset() { lastTapCode = -1; if (talking) stop("RESET"); }

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
