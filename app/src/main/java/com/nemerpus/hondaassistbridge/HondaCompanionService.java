package com.nemerpus.hondaassistbridge;

import android.companion.AssociationInfo;
import android.companion.CompanionDeviceService;
import android.companion.DevicePresenceEvent;
import android.os.Build;

public class HondaCompanionService extends CompanionDeviceService {
    private int trackedAssociationId = -1;
    private Boolean lastAggregatePresence;

    @Override
    public void onCreate() {
        super.onCreate();
        trackedAssociationId = CompanionController.associationId(this);
        FlightRecorder.log(this, "COMPANION",
                "service bound · associationId=" + trackedAssociationId
                        + " ble=" + AppConfig.isCompanionBlePresent(this)
                        + " bt=" + AppConfig.isCompanionBtConnected(this));
    }

    @Override
    public void onDevicePresenceEvent(DevicePresenceEvent event) {
        if (Build.VERSION.SDK_INT < 36 || event == null) return;

        final int associationId = event.getAssociationId();
        final int expectedAssociationId = CompanionController.associationId(this);
        final int type = event.getEvent();

        if (expectedAssociationId >= 0 && associationId != expectedAssociationId) {
            FlightRecorder.log(this, "COMPANION",
                    "IGNORE stale associationId=" + associationId
                            + " expected=" + expectedAssociationId
                            + " event=" + eventName(type));
            return;
        }

        if (trackedAssociationId != associationId) {
            trackedAssociationId = associationId;
            lastAggregatePresence = null;
        }

        switch (type) {
            case DevicePresenceEvent.EVENT_BLE_APPEARED:
                AppConfig.setCompanionBlePresent(this, true);
                break;
            case DevicePresenceEvent.EVENT_BLE_DISAPPEARED:
                AppConfig.setCompanionBlePresent(this, false);
                break;
            case DevicePresenceEvent.EVENT_BT_CONNECTED:
                AppConfig.setCompanionBtConnected(this, true);
                break;
            case DevicePresenceEvent.EVENT_BT_DISCONNECTED:
                AppConfig.setCompanionBtConnected(this, false);
                break;
            case DevicePresenceEvent.EVENT_SELF_MANAGED_APPEARED:
                handleAggregatePresence(associationId, type, true);
                return;
            case DevicePresenceEvent.EVENT_SELF_MANAGED_DISAPPEARED:
                handleAggregatePresence(associationId, type, false);
                return;
            default:
                FlightRecorder.log(this, "COMPANION",
                        "UNKNOWN associationId=" + associationId + " event=" + type);
                return;
        }

        // Android 16 keeps BLE-nearby and Bluetooth-connected presence as independent
        // sources. Losing only one source must not tear down a still-valid connection.
        boolean present = AppConfig.isCompanionBlePresent(this)
                || AppConfig.isCompanionBtConnected(this);
        handleAggregatePresence(associationId, type, present);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onDeviceAppeared(AssociationInfo info) {
        if (Build.VERSION.SDK_INT < 36) present(-1, -1, "LEGACY_APPEARED");
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onDeviceDisappeared(AssociationInfo info) {
        if (Build.VERSION.SDK_INT < 36) absent(-1, -1, "LEGACY_DISAPPEARED");
    }

    private void handleAggregatePresence(int associationId, int source, boolean present) {
        boolean ble = AppConfig.isCompanionBlePresent(this);
        boolean bt = AppConfig.isCompanionBtConnected(this);
        String name = eventName(source);

        FlightRecorder.log(this, "COMPANION",
                "id=" + associationId
                        + " event=" + name
                        + " ble=" + ble
                        + " bt=" + bt
                        + " present=" + present);

        if (lastAggregatePresence != null && lastAggregatePresence == present) {
            if (present && source == DevicePresenceEvent.EVENT_BLE_DISAPPEARED && bt) {
                FlightRecorder.log(this, "COMPANION",
                        "KEEP GATT · BLE disappeared but Bluetooth remains connected");
            } else if (present && source == DevicePresenceEvent.EVENT_BT_DISCONNECTED && ble) {
                FlightRecorder.log(this, "COMPANION",
                        "KEEP GATT · Bluetooth disconnected but BLE remains nearby");
            }
            return;
        }

        lastAggregatePresence = present;
        if (present) present(associationId, source, name);
        else absent(associationId, source, name);
    }

    private void present(int associationId, int source, String name) {
        AppConfig.setCompanionPresent(this, true);
        FlightRecorder.log(this, "COMPANION",
                "PRESENT id=" + associationId + " source=" + name);
        if (AppConfig.isListeningEnabled(this)) {
            HondaBleService.requestStartFromCompanion(this);
        }
    }

    private void absent(int associationId, int source, String name) {
        AppConfig.setCompanionPresent(this, false);
        FlightRecorder.log(this, "COMPANION",
                "ABSENT id=" + associationId + " source=" + name + " · STOP GATT");
        HondaBleService.requestStopForAbsence(this);
    }

    private static String eventName(int event) {
        if (Build.VERSION.SDK_INT < 36) return String.valueOf(event);
        switch (event) {
            case DevicePresenceEvent.EVENT_BLE_APPEARED: return "BLE_APPEARED";
            case DevicePresenceEvent.EVENT_BLE_DISAPPEARED: return "BLE_DISAPPEARED";
            case DevicePresenceEvent.EVENT_BT_CONNECTED: return "BT_CONNECTED";
            case DevicePresenceEvent.EVENT_BT_DISCONNECTED: return "BT_DISCONNECTED";
            case DevicePresenceEvent.EVENT_SELF_MANAGED_APPEARED: return "SELF_MANAGED_APPEARED";
            case DevicePresenceEvent.EVENT_SELF_MANAGED_DISAPPEARED: return "SELF_MANAGED_DISAPPEARED";
            default: return "UNKNOWN(" + event + ")";
        }
    }
}
