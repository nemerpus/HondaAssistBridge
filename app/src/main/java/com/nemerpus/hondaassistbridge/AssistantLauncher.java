package com.nemerpus.hondaassistbridge;

import android.app.ActivityOptions;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

final class AssistantLauncher {
    private static final String TAG = "HondaAssistBridge";
    private static final String GOOGLE_APP = "com.google.android.googlequicksearchbox";
    private static final String HANDS_FREE_ACTIVITY =
            "com.google.android.voicesearch.handsfree.HandsFreeActivity";

    static void launch(Context context) {
        KeyguardManager km=context.getSystemService(KeyguardManager.class);
        PowerManager pm=context.getSystemService(PowerManager.class);
        boolean locked=km!=null&&km.isKeyguardLocked();
        boolean interactive=pm==null||pm.isInteractive();
        boolean fg=AppVisibilityTracker.isForeground();
        String route=locked?"KEYGUARD_LOCKED":(fg?"APP_FOREGROUND":"BACKGROUND_UNLOCKED");
        boolean associated=CompanionController.hasAssociation(context);
        boolean present=AppConfig.isCompanionPresent(context);
        FlightRecorder.log(context,"ASSIST","context interactive="+interactive+" keyguard="+locked+
                " appForeground="+fg+" companionAssociated="+associated+" companionPresent="+present+
                " associationId="+CompanionController.associationId(context)+" route="+route+
                " bal="+(Build.VERSION.SDK_INT>=36?"ALLOW_ALWAYS":"LEGACY"));
        FlightRecorder.log(context, "ASSIST", "launch solicitado · " + FlightRecorder.powerState(context));

        // Android 16: launch the exact hands-free target directly. The v0.8 path first
        // started our proxy Activity; with keyguard active Android accepted the PendingIntent
        // but deferred that Activity until unlock. Avoiding the intermediary also mirrors the
        // system Bluetooth/HFP route observed in the field trace as closely as a normal app can.
        if (Build.VERSION.SDK_INT >= 36 && launchHandsFreeDirect(context)) {
            return;
        }

        launchViaProxy(context);
    }

    private static boolean launchHandsFreeDirect(Context context) {
        Intent voice = new Intent(Intent.ACTION_VOICE_COMMAND)
                .setComponent(new ComponentName(GOOGLE_APP, HANDS_FREE_ACTIVITY))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            ActivityOptions creatorOptions = ActivityOptions.makeBasic();
            creatorOptions.setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    220,
                    voice,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE,
                    creatorOptions.toBundle());

            ActivityOptions senderOptions = ActivityOptions.makeBasic();
            senderOptions.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS);

            FlightRecorder.log(context, "ASSIST", "DIRECT preparando HandsFreeActivity");
            pendingIntent.send(senderOptions.toBundle());
            FlightRecorder.log(context, "ASSIST", "DIRECT PendingIntent enviado");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Fallo lanzamiento directo de HandsFreeActivity", t);
            FlightRecorder.log(context, "ASSIST", "DIRECT ERROR: " + t);
            return false;
        }
    }

    private static void launchViaProxy(Context context) {
        Intent proxy = new Intent(context, AssistantProxyActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        try {
            if (Build.VERSION.SDK_INT >= 36) {
                ActivityOptions creatorOptions = ActivityOptions.makeBasic();
                creatorOptions.setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS);

                PendingIntent pendingIntent = PendingIntent.getActivity(
                        context,
                        219,
                        proxy,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE,
                        creatorOptions.toBundle());

                ActivityOptions senderOptions = ActivityOptions.makeBasic();
                senderOptions.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS);
                pendingIntent.send(senderOptions.toBundle());
                FlightRecorder.log(context, "ASSIST", "FALLBACK Proxy PendingIntent enviado");
                return;
            }

            if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions creatorOptions = ActivityOptions.makeBasic();
                creatorOptions.setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);

                PendingIntent pendingIntent = PendingIntent.getActivity(
                        context,
                        219,
                        proxy,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE,
                        creatorOptions.toBundle());

                ActivityOptions senderOptions = ActivityOptions.makeBasic();
                senderOptions.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                pendingIntent.send(senderOptions.toBundle());
                return;
            }

            context.startActivity(proxy);
        } catch (Throwable t) {
            Log.e(TAG, "No se pudo lanzar AssistantProxyActivity", t);
            FlightRecorder.log(context, "ASSIST", "ERROR fallback Proxy: " + t);
            try {
                context.startActivity(proxy);
            } catch (Throwable ignored) {
                Log.e(TAG, "Fallback de AssistantProxyActivity bloqueado", ignored);
                FlightRecorder.log(context, "ASSIST", "ERROR fallback startActivity: " + ignored);
            }
        }
    }

    private AssistantLauncher() {}
}
