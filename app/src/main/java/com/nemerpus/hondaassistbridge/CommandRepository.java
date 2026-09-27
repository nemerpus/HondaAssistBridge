package com.nemerpus.hondaassistbridge;

import android.content.Context;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class CommandRepository {
    private static final String KEY = "commands_v1";

    static List<Command> load(Context context) {
        String raw = AppConfig.prefs(context).getString(KEY, null);
        List<Command> out = new ArrayList<>();
        if (raw != null) {
            try {
                JSONArray a = new JSONArray(raw);
                for (int i = 0; i < a.length(); i++) out.add(Command.fromJson(a.getJSONObject(i)));
            } catch (Throwable ignored) {}
        }
        if (out.isEmpty()) {
            Command c = new Command();
            c.id = UUID.randomUUID().toString(); c.name = "Gemini";
            c.doublePress = true; c.eventCode = AppConfig.getGestureCode(context, GestureEngine.Type.DOUBLE_LEFT);
            c.actionType = Command.ACTION_ASSISTANT;
            out.add(c); save(context, out);
        }
        return out;
    }

    static void save(Context context, List<Command> commands) {
        JSONArray a = new JSONArray();
        for (Command c : commands) try { a.put(c.toJson()); } catch (Throwable ignored) {}
        AppConfig.prefs(context).edit().putString(KEY, a.toString()).apply();
    }

    static void upsert(Context context, Command command) {
        List<Command> list = load(context); boolean found = false;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(command.id)) { list.set(i, command); found = true; break; }
        if (!found) list.add(command); save(context, list);
    }

    static void delete(Context context, String id) {
        List<Command> list = load(context); list.removeIf(c -> c.id.equals(id)); save(context, list);
    }
    private CommandRepository() {}
}
