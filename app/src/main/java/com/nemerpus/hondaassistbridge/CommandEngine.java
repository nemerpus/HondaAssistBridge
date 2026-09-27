package com.nemerpus.hondaassistbridge;

import android.content.Context;
import android.os.SystemClock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CommandEngine {
    static final long DOUBLE_MS = 600L;
    static final long COOLDOWN_MS = 1300L;
    private final Map<String, Long> first = new HashMap<>();
    private long lastAction;

    Command onEvent(Context context, int code) {
        if (AppConfig.isSetupLearning(context)) return null;
        long now = SystemClock.elapsedRealtime();
        List<Command> commands = CommandRepository.load(context);
        for (Command c : commands) {
            if (!c.enabled || c.eventCode != (code & 0xFF)) continue;
            if (!c.doublePress) {
                if (now - lastAction >= COOLDOWN_MS) { lastAction = now; first.clear(); return c; }
                return null;
            }
            long p = first.getOrDefault(c.id, 0L);
            if (p > 0 && now - p <= DOUBLE_MS) {
                first.remove(c.id);
                if (now - lastAction >= COOLDOWN_MS) { lastAction = now; first.clear(); return c; }
                return null;
            }
            first.put(c.id, now);
        }
        first.entrySet().removeIf(e -> now - e.getValue() > DOUBLE_MS);
        return null;
    }
    void reset() { first.clear(); }
}
