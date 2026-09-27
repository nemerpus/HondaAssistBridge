package com.nemerpus.hondaassistbridge;

import android.content.Context;
import android.os.SystemClock;

import java.util.Locale;

final class GestureEngine {
    static final long DOUBLE_PRESS_WINDOW_MS = 550L;
    static final long TRIGGER_COOLDOWN_MS = 1500L;

    enum Type {
        DOUBLE_LEFT("double_left", "Doble izquierda", "←  ←", 0x03, true,
                "Pulsa IZQUIERDA una vez (pulsación corta)."),
        DOUBLE_DOWN("double_down", "Doble abajo", "↓  ↓", 0x02, true,
                "Pulsa ABAJO una vez (pulsación corta)."),
        DOUBLE_UP("double_up", "Doble arriba", "↑  ↑", 0x01, true,
                "Pulsa ARRIBA una vez (pulsación corta)."),
        DOUBLE_RIGHT("double_right", "Doble derecha", "→  →", 0x04, true,
                "Pulsa DERECHA una vez (pulsación corta)."),
        LONG_LEFT("long_left", "Izquierda larga", "← mantener", 0x09, false,
                "Mantén IZQUIERDA hasta recibir el evento largo."),
        LONG_DOWN("long_down", "Abajo larga", "↓ mantener", 0x08, false,
                "Mantén ABAJO hasta recibir el evento largo."),
        LONG_UP("long_up", "Arriba larga", "↑ mantener", 0x07, false,
                "Mantén ARRIBA hasta recibir el evento largo."),
        LONG_RIGHT("long_right", "Derecha larga", "→ mantener", 0x0A, false,
                "Mantén DERECHA hasta recibir el evento largo.");

        final String id;
        final String label;
        final String symbol;
        final int defaultCode;
        final boolean doublePress;
        final String learningInstruction;

        Type(String id, String label, String symbol, int defaultCode,
             boolean doublePress, String learningInstruction) {
            this.id = id;
            this.label = label;
            this.symbol = symbol;
            this.defaultCode = defaultCode;
            this.doublePress = doublePress;
            this.learningInstruction = learningInstruction;
        }

        static Type fromId(String id) {
            if (id != null) {
                for (Type type : values()) {
                    if (type.id.equals(id)) return type;
                }
            }
            return DOUBLE_LEFT;
        }
    }

    static final class Result {
        final boolean triggerAssistant;
        final String eventMessage;
        final boolean learned;

        Result(boolean triggerAssistant, String eventMessage, boolean learned) {
            this.triggerAssistant = triggerAssistant;
            this.eventMessage = eventMessage;
            this.learned = learned;
        }

        static Result none() {
            return new Result(false, null, false);
        }
    }

    private long firstPressAt;
    private long lastTriggerAt;

    Result onEvent(Context context, int code) {
        final long now = SystemClock.elapsedRealtime();
        final Type type = AppConfig.getGestureType(context);

        if (AppConfig.isLearningGesture(context)) {
            if (isLearningCandidate(type, code)) {
                AppConfig.setGestureCode(context, type, code);
                AppConfig.setLearningGesture(context, false);
                firstPressAt = 0L;
                return new Result(false,
                        "Gesto aprendido: " + type.label + " = " + hex(code), true);
            }

            // Una pulsación larga puede emitir antes su equivalente corto. No lo
            // aprendemos por accidente: esperamos el código largo 07–0A observado.
            if (!type.doublePress && code >= 0x01 && code <= 0x04) {
                return new Result(false,
                        "Aprendiendo " + type.label + " · mantén pulsado…", false);
            }
        }

        final int target = AppConfig.getGestureCode(context, type);

        if (!type.doublePress) {
            firstPressAt = 0L;
            if (code == target && cooldownElapsed(now)) {
                lastTriggerAt = now;
                AppConfig.prefs(context).edit()
                        .putLong(AppConfig.KEY_LAST_TRIGGER_AT, System.currentTimeMillis())
                        .apply();
                return new Result(true,
                        type.label + " detectada (" + hex(code) + ") → asistente", false);
            }
            return Result.none();
        }

        if (code == target) {
            if (firstPressAt > 0L && now - firstPressAt <= DOUBLE_PRESS_WINDOW_MS) {
                firstPressAt = 0L;
                if (cooldownElapsed(now)) {
                    lastTriggerAt = now;
                    AppConfig.prefs(context).edit()
                            .putLong(AppConfig.KEY_LAST_TRIGGER_AT, System.currentTimeMillis())
                            .apply();
                    return new Result(true,
                            type.label + " detectada (" + hex(code) + ") → asistente", false);
                }
                return Result.none();
            }

            firstPressAt = now;
            return new Result(false,
                    "Primera pulsación de " + type.label + " · segunda ≤ "
                            + DOUBLE_PRESS_WINDOW_MS + " ms", false);
        }

        if (firstPressAt > 0L) {
            // Secuencia estricta: otro evento invalida la doble pulsación pendiente.
            firstPressAt = 0L;
        }
        return Result.none();
    }

    void reset() {
        firstPressAt = 0L;
    }

    private boolean cooldownElapsed(long now) {
        return lastTriggerAt == 0L || now - lastTriggerAt > TRIGGER_COOLDOWN_MS;
    }

    private static boolean isLearningCandidate(Type type, int code) {
        if (type.doublePress) return code >= 0x01 && code <= 0x04;
        return code >= 0x07 && code <= 0x0A;
    }

    static String arrowForCode(int code) {
        int c = code & 0xFF;
        if (c == 0x01 || c == 0x07) return "↑";
        if (c == 0x02 || c == 0x08) return "↓";
        if (c == 0x03 || c == 0x09) return "←";
        if (c == 0x04 || c == 0x0A) return "→";
        return "◆";
    }

    static String directionForCode(int code) {
        return switch (arrowForCode(code)) {
            case "↑" -> "arriba"; case "↓" -> "abajo"; case "←" -> "izquierda"; case "→" -> "derecha"; default -> "control";
        };
    }

    static String hex(int value) {
        return String.format(Locale.ROOT, "0x%02X", value & 0xFF);
    }

    GestureEngine() {}
}
