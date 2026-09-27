package com.nemerpus.hondaassistbridge;

import android.app.Activity;
import android.companion.AssociationInfo;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.companion.ObservingDevicePresenceRequest;
import android.content.Context;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

final class CompanionController {
    private static final int REQ_ASSOCIATE = 701;

    interface AssociationCallback {
        void onAssociated(AssociationInfo info);
    }

    static boolean supported(Context c) {
        return Build.VERSION.SDK_INT >= 31
                && c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP);
    }

    static AssociationInfo association(Context c) {
        if (!supported(c)) return null;
        CompanionDeviceManager manager = c.getSystemService(CompanionDeviceManager.class);
        if (manager == null) return null;
        try {
            List<AssociationInfo> associations = manager.getMyAssociations();
            int rememberedId = AppConfig.getCompanionAssociationId(c);

            if (rememberedId >= 0) {
                for (AssociationInfo association : associations) {
                    if (association.getId() == rememberedId && matches(association, c)) {
                        return association;
                    }
                }
            }

            AssociationInfo best = null;
            for (AssociationInfo candidate : associations) {
                if (!matches(candidate, c)) continue;
                // Existing installations may already contain duplicate associations for the
                // same BTU. Prefer the newest association and persist its ID as canonical.
                if (best == null || candidate.getId() > best.getId()) best = candidate;
            }
            return best;
        } catch (Throwable t) {
            FlightRecorder.log(c, "COMPANION", "associations error=" + t);
            return null;
        }
    }

    static boolean hasAssociation(Context c) {
        AssociationInfo a = association(c);
        if (a == null) {
            AppConfig.setCompanion(c, "", false, -1);
            AppConfig.clearCompanionPresenceSources(c);
            return false;
        }
        rememberAndObserve(c, a);
        return true;
    }

    static int associationId(Context c) {
        AssociationInfo a = association(c);
        return a == null ? -1 : a.getId();
    }

    static void requestAssociation(Activity a) {
        requestAssociation(a, (String) null, null);
    }

    static void requestAssociation(Activity a, AssociationCallback callback) {
        requestAssociation(a, (String) null, callback);
    }

    static void requestAssociation(Activity a, String address, AssociationCallback callback) {
        if (!supported(a)) {
            Toast.makeText(a, "Companion Device Manager no disponible", Toast.LENGTH_LONG).show();
            return;
        }
        CompanionDeviceManager manager = a.getSystemService(CompanionDeviceManager.class);
        if (manager == null) return;

        // The bonded-device picker already gives us the BTU MAC. Reuse an existing
        // association instead of creating associationId 14/15/16 for the same device.
        if (address != null && !address.isEmpty()) {
            AssociationInfo existing = associationForAddress(manager, address);
            if (existing != null) {
                rememberAndObserve(a, existing);
                FlightRecorder.log(a, "COMPANION",
                        "association REUSE id=" + existing.getId()
                                + " address=" + masked(address));
                Toast.makeText(a, "HONDA BTU ya vinculada", Toast.LENGTH_SHORT).show();
                if (callback != null) callback.onAssociated(existing);
                return;
            }
        }

        BluetoothDeviceFilter.Builder filterBuilder = new BluetoothDeviceFilter.Builder();
        if (address != null && !address.isEmpty()) {
            filterBuilder.setAddress(address);
        } else {
            filterBuilder.setNamePattern(Pattern.compile("(?i).*HONDA BTU.*"));
        }

        AssociationRequest request = new AssociationRequest.Builder()
                .addDeviceFilter(filterBuilder.build())
                .setSingleDevice(true)
                .build();

        Executor executor = a.getMainExecutor();
        FlightRecorder.log(a, "COMPANION",
                "association request · " + (address == null ? "HONDA BTU" : "bonded " + masked(address)));

        manager.associate(request, executor, new CompanionDeviceManager.Callback() {
            @Override
            public void onAssociationPending(IntentSender sender) {
                try {
                    a.startIntentSenderForResult(sender, REQ_ASSOCIATE, null, 0, 0, 0);
                } catch (Exception e) {
                    fail(a, e.toString());
                }
            }

            @Override
            public void onAssociationCreated(AssociationInfo info) {
                rememberAndObserve(a, info);
                FlightRecorder.log(a, "COMPANION",
                        "association PASS id=" + info.getId() + " address=" + masked(mac(info)));
                Toast.makeText(a, "HONDA BTU vinculada", Toast.LENGTH_SHORT).show();
                if (callback != null) callback.onAssociated(info);
            }

            @Override
            public void onFailure(CharSequence error) {
                fail(a, error == null ? "Asociación cancelada" : error.toString());
            }
        });
    }

    static void ensureObservation(Context c) {
        AssociationInfo a = association(c);
        if (a != null) {
            rememberAndObserve(c, a);
        } else {
            AppConfig.setCompanion(c, "", false, -1);
            AppConfig.clearCompanionPresenceSources(c);
            FlightRecorder.log(c, "COMPANION", "UNASSOCIATED");
        }
    }

    private static AssociationInfo associationForAddress(CompanionDeviceManager manager, String address) {
        try {
            AssociationInfo best = null;
            for (AssociationInfo candidate : manager.getMyAssociations()) {
                String candidateMac = mac(candidate);
                if (!address.equalsIgnoreCase(candidateMac)) continue;
                if (best == null || candidate.getId() > best.getId()) best = candidate;
            }
            return best;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean matches(AssociationInfo a, Context c) {
        String wanted = AppConfig.getCompanionAddress(c);
        String candidateMac = mac(a);
        if (!wanted.isEmpty()) return wanted.equalsIgnoreCase(candidateMac);
        CharSequence name = a.getDisplayName();
        return name != null && name.toString().toUpperCase().contains("HONDA BTU");
    }

    @SuppressWarnings("deprecation")
    private static void rememberAndObserve(Context c, AssociationInfo a) {
        String mac = mac(a);
        if (mac.isEmpty()) {
            FlightRecorder.log(c, "COMPANION",
                    "association id=" + a.getId() + " without MAC");
            return;
        }

        int previousId = AppConfig.getCompanionAssociationId(c);
        if (previousId >= 0 && previousId != a.getId()) {
            AppConfig.clearCompanionPresenceSources(c);
            FlightRecorder.log(c, "COMPANION",
                    "canonical association changed " + previousId + " -> " + a.getId());
        }
        AppConfig.setCompanion(c, mac, true, a.getId());

        CompanionDeviceManager manager = c.getSystemService(CompanionDeviceManager.class);
        if (manager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 36) {
                ObservingDevicePresenceRequest request = new ObservingDevicePresenceRequest.Builder()
                        .setAssociationId(a.getId())
                        .build();
                manager.startObservingDevicePresence(request);
                FlightRecorder.log(c, "COMPANION",
                        "observation=API36 associationId=" + a.getId()
                                + " address=" + masked(mac));
            } else {
                manager.startObservingDevicePresence(mac);
                FlightRecorder.log(c, "COMPANION",
                        "observation=legacy associationId=" + a.getId()
                                + " address=" + masked(mac));
            }
        } catch (IllegalStateException already) {
            FlightRecorder.log(c, "COMPANION",
                    "observation already active associationId=" + a.getId());
        } catch (Throwable t) {
            FlightRecorder.log(c, "COMPANION",
                    "observe=" + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static String mac(AssociationInfo a) {
        try {
            return a.getDeviceMacAddress() == null ? "" : a.getDeviceMacAddress().toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String masked(String x) {
        return x == null || x.length() < 5 ? "…" : "…" + x.substring(x.length() - 5);
    }

    private static void fail(Context c, String x) {
        FlightRecorder.log(c, "COMPANION", "FAIL " + x);
        Toast.makeText(c, x, Toast.LENGTH_LONG).show();
    }

    private CompanionController() {}
}
