package com.nemerpus.hondaassistbridge;

import android.content.Context;
import android.content.SharedPreferences;

final class AppConfig {
    static final String PREFS = "honda_assist_bridge";

    static final String KEY_DEVICE_FILTER = "device_filter";
    static final String KEY_TRIGGER_CODE = "trigger_code"; // migración desde v0.3
    static final String KEY_GESTURE_ID = "gesture_id";
    static final String KEY_GESTURE_CODE_PREFIX = "gesture_code_";
    static final String KEY_LEARN_GESTURE = "learn_gesture";
    static final String KEY_LEARN_GESTURE_STARTED_AT = "learn_gesture_started_at";
    static final String KEY_LISTENING_ENABLED = "listening_enabled";
    static final String KEY_LAST_EVENT = "last_event";
    static final String KEY_LAST_STATUS = "last_status";
    static final String KEY_LAST_STATE = "last_state";
    static final String KEY_LAST_TRIGGER_AT = "last_trigger_at";
    static final String KEY_SERVICE_RUNNING = "service_running";
    static final String KEY_SETUP_COMPLETED = "setup_completed_v1";
    static final String KEY_SETUP_LEARNING = "setup_learning";
    static final String KEY_LAST_EVENT_CODE = "last_event_code";
    static final String KEY_VEHICLE_NAME = "vehicle_name";
    static final String KEY_PROFILE_NAME = "profile_name";
    static final String KEY_COMPANION_ADDRESS = "companion_address";
    static final String KEY_COMPANION_ENABLED = "companion_enabled";
    static final String KEY_COMPANION_PRESENT = "companion_present";
    static final String KEY_COMPANION_ASSOCIATION_ID = "companion_association_id";
    static final String KEY_COMPANION_BLE_PRESENT = "companion_ble_present";
    static final String KEY_COMPANION_BT_CONNECTED = "companion_bt_connected";

    static final String DEFAULT_DEVICE_FILTER = "HONDA BTU";

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String getDeviceFilter(Context context) {
        String value = prefs(context).getString(KEY_DEVICE_FILTER, DEFAULT_DEVICE_FILTER);
        return value == null || value.trim().isEmpty() ? DEFAULT_DEVICE_FILTER : value.trim();
    }

    static boolean isListeningEnabled(Context context) {
        // v0.4 activa la escucha automática por defecto. Tras el primer inicio y permisos,
        // el servicio puede volver automáticamente después de reinicios/actualizaciones.
        return prefs(context).getBoolean(KEY_LISTENING_ENABLED, true);
    }

    static void setListeningEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_LISTENING_ENABLED, enabled).apply();
    }

    static GestureEngine.Type getGestureType(Context context) {
        String id = prefs(context).getString(KEY_GESTURE_ID, GestureEngine.Type.DOUBLE_LEFT.id);
        return GestureEngine.Type.fromId(id);
    }

    static void setGestureType(Context context, GestureEngine.Type type) {
        prefs(context).edit().putString(KEY_GESTURE_ID, type.id).apply();
    }

    static int getGestureCode(Context context, GestureEngine.Type type) {
        SharedPreferences p = prefs(context);
        String key = KEY_GESTURE_CODE_PREFIX + type.id;
        if (p.contains(key)) return p.getInt(key, type.defaultCode);

        // Conserva el aprendizaje de ← corto realizado en v0.2/v0.3.
        if (type == GestureEngine.Type.DOUBLE_LEFT && p.contains(KEY_TRIGGER_CODE)) {
            return p.getInt(KEY_TRIGGER_CODE, type.defaultCode);
        }
        return type.defaultCode;
    }

    static void setGestureCode(Context context, GestureEngine.Type type, int code) {
        SharedPreferences.Editor editor = prefs(context).edit()
                .putInt(KEY_GESTURE_CODE_PREFIX + type.id, code & 0xFF);
        if (type == GestureEngine.Type.DOUBLE_LEFT) {
            editor.putInt(KEY_TRIGGER_CODE, code & 0xFF);
        }
        editor.apply();
    }

    static boolean isLearningGesture(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.getBoolean(KEY_LEARN_GESTURE, false)) return false;

        long startedAt = p.getLong(KEY_LEARN_GESTURE_STARTED_AT, 0L);
        if (startedAt <= 0L || System.currentTimeMillis() - startedAt > 30_000L) {
            p.edit()
                    .putBoolean(KEY_LEARN_GESTURE, false)
                    .remove(KEY_LEARN_GESTURE_STARTED_AT)
                    .apply();
            return false;
        }
        return true;
    }

    static void setLearningGesture(Context context, boolean learning) {
        SharedPreferences.Editor editor = prefs(context).edit()
                .putBoolean(KEY_LEARN_GESTURE, learning);
        if (learning) editor.putLong(KEY_LEARN_GESTURE_STARTED_AT, System.currentTimeMillis());
        else editor.remove(KEY_LEARN_GESTURE_STARTED_AT);
        editor.apply();
    }

    static boolean isSetupCompleted(Context context) {
        return prefs(context).getBoolean(KEY_SETUP_COMPLETED, false);
    }

    static void setSetupCompleted(Context context, boolean completed) {
        prefs(context).edit().putBoolean(KEY_SETUP_COMPLETED, completed).apply();
    }

    static void setSetupLearning(Context context, boolean learning) {
        prefs(context).edit().putBoolean(KEY_SETUP_LEARNING, learning).apply();
    }

    static boolean isSetupLearning(Context context) {
        return prefs(context).getBoolean(KEY_SETUP_LEARNING, false);
    }

    static void setVehicle(Context context, String deviceName) {
        String clean = deviceName == null || deviceName.trim().isEmpty() ? DEFAULT_DEVICE_FILTER : deviceName.trim();
        prefs(context).edit()
                .putString(KEY_DEVICE_FILTER, clean)
                .putString(KEY_VEHICLE_NAME, clean)
                .putString(KEY_PROFILE_NAME, "Honda RoadSync BLE")
                .apply();
    }

    static void setLastStatus(Context context, String state, String message) {
        prefs(context).edit()
                .putString(KEY_LAST_STATE, state)
                .putString(KEY_LAST_STATUS, message)
                .apply();
    }

    static String getCompanionAddress(Context context) { return prefs(context).getString(KEY_COMPANION_ADDRESS, ""); }
    static int getCompanionAssociationId(Context context) { return prefs(context).getInt(KEY_COMPANION_ASSOCIATION_ID, -1); }

    static void setCompanion(Context context, String address, boolean enabled) {
        setCompanion(context, address, enabled, enabled ? getCompanionAssociationId(context) : -1);
    }

    static void setCompanion(Context context, String address, boolean enabled, int associationId) {
        SharedPreferences.Editor editor = prefs(context).edit()
                .putString(KEY_COMPANION_ADDRESS, address == null ? "" : address)
                .putBoolean(KEY_COMPANION_ENABLED, enabled)
                .putInt(KEY_COMPANION_ASSOCIATION_ID, enabled ? associationId : -1);
        if (!enabled) {
            editor.putBoolean(KEY_COMPANION_PRESENT, false)
                    .putBoolean(KEY_COMPANION_BLE_PRESENT, false)
                    .putBoolean(KEY_COMPANION_BT_CONNECTED, false);
        }
        editor.apply();
    }

    static boolean isCompanionEnabled(Context context) { return prefs(context).getBoolean(KEY_COMPANION_ENABLED,false); }
    static void setCompanionPresent(Context context, boolean present) { prefs(context).edit().putBoolean(KEY_COMPANION_PRESENT,present).apply(); }
    static boolean isCompanionPresent(Context context) { return prefs(context).getBoolean(KEY_COMPANION_PRESENT,false); }

    static void setCompanionBlePresent(Context context, boolean present) {
        prefs(context).edit().putBoolean(KEY_COMPANION_BLE_PRESENT, present).apply();
    }

    static boolean isCompanionBlePresent(Context context) {
        return prefs(context).getBoolean(KEY_COMPANION_BLE_PRESENT, false);
    }

    static void setCompanionBtConnected(Context context, boolean connected) {
        prefs(context).edit().putBoolean(KEY_COMPANION_BT_CONNECTED, connected).apply();
    }

    static boolean isCompanionBtConnected(Context context) {
        return prefs(context).getBoolean(KEY_COMPANION_BT_CONNECTED, false);
    }

    static void clearCompanionPresenceSources(Context context) {
        prefs(context).edit()
                .putBoolean(KEY_COMPANION_PRESENT, false)
                .putBoolean(KEY_COMPANION_BLE_PRESENT, false)
                .putBoolean(KEY_COMPANION_BT_CONNECTED, false)
                .apply();
    }

    private AppConfig() {}
}
