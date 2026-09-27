package com.nemerpus.hondaassistbridge;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class HondaBleService extends Service {
    public static final String ACTION_UPDATE = "com.nemerpus.hondaassistbridge.UPDATE";
    public static final String EXTRA_KIND = "kind";
    public static final String KIND_STATUS = "status";
    public static final String KIND_EVENT = "event";

    public static final String ACTION_START = "com.nemerpus.hondaassistbridge.START";
    public static final String ACTION_STOP = "com.nemerpus.hondaassistbridge.STOP";
    public static final String ACTION_RECONNECT = "com.nemerpus.hondaassistbridge.RECONNECT";
    public static final String ACTION_COMPANION_PRESENT = "com.nemerpus.hondaassistbridge.COMPANION_PRESENT";
    public static final String ACTION_COMPANION_ABSENT = "com.nemerpus.hondaassistbridge.COMPANION_ABSENT";

    public static final String STATE_STOPPED = "stopped";
    public static final String STATE_WAITING = "waiting";
    public static final String STATE_CONNECTING = "connecting";
    public static final String STATE_ACTIVE = "active";
    public static final String STATE_ERROR = "error";

    private static final String TAG = "HondaAssistBridge";
    private static final String CHANNEL_ID = "honda_ble_listener_v2";
    private static final int NOTIFICATION_ID = 1001;

    private static final UUID SERVICE_UUID = UUID.fromString("8536e103-6771-4d4b-9702-287cb5e1340f");
    private static final UUID EVENT_UUID = UUID.fromString("6c060578-d1d9-460a-b86f-eb97f01b2227");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final long CONNECT_TIMEOUT_MS = 90_000L;
    private static final long DISCOVERY_TIMEOUT_MS = 10_000L;
    private static final long SUBSCRIBE_TIMEOUT_MS = 8_000L;
    private static final long[] RECONNECT_DELAYS_MS = {2_000L,4_000L,8_000L,15_000L,30_000L};
    private static final int MAX_COMPANION_RECOVERY_ATTEMPTS=5;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CommandEngine commandEngine = new CommandEngine();

    private BluetoothGatt gatt;
    private boolean subscribed;
    private boolean stopping;
    private boolean foregroundStarted;
    private int reconnectAttempt;
    private int companionRecoveryAttempts;
    private PowerManager.WakeLock wakeLock;

    public static void requestStart(Context context, boolean userInitiated) {
        AppConfig.setListeningEnabled(context,true);
        if(!hasBluetoothPermission(context)){AppConfig.setLastStatus(context,STATE_ERROR,"Abre Honda Assist Bridge para conceder permiso Bluetooth");return;}
        if(AppConfig.isCompanionEnabled(context)&&!AppConfig.isCompanionPresent(context)&&!userInitiated){AppConfig.setLastStatus(context,STATE_WAITING,"Esperando HONDA BTU · modo Companion");return;}
        startPlainService(context,ACTION_START);
    }
    public static void requestStartFromCompanion(Context context){AppConfig.setCompanionPresent(context,true);startPlainService(context,ACTION_COMPANION_PRESENT);}
    public static void requestStopForAbsence(Context context){AppConfig.setCompanionPresent(context,false);startPlainService(context,ACTION_COMPANION_ABSENT);}
    private static void startPlainService(Context c,String action){try{c.startService(new Intent(c,HondaBleService.class).setAction(action));}catch(Throwable t){Log.e(TAG,"start BLE "+action,t);AppConfig.setLastStatus(c,STATE_WAITING,"Esperando HONDA BTU");}}
    public static void requestStop(Context context) {
        AppConfig.setListeningEnabled(context, false);
        Intent service = new Intent(context, HondaBleService.class).setAction(ACTION_STOP);
        try {
            context.startService(service);
        } catch (Throwable ignored) {
            context.stopService(new Intent(context, HondaBleService.class));
        }
    }

    public static void requestReconnect(Context context) {
        if (!AppConfig.isListeningEnabled(context)) AppConfig.setListeningEnabled(context, true);
        Intent service = new Intent(context, HondaBleService.class).setAction(ACTION_RECONNECT);
        try {
            context.startService(service);
        } catch (Throwable t) {
            Log.e(TAG, "No se pudo solicitar reconexión", t);
        }
    }

    private static boolean hasBluetoothPermission(Context context) {
        return Build.VERSION.SDK_INT < 31 ||
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED;
    }

    @Override public void onCreate(){
        super.onCreate();createNotificationChannel();registerBluetoothReceiver();
        AppConfig.prefs(this).edit().putBoolean(AppConfig.KEY_SERVICE_RUNNING,true).apply();
        FlightRecorder.log(this,"SERVICE","onCreate background · "+FlightRecorder.powerState(this));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action=intent==null?ACTION_START:intent.getAction();
        if(ACTION_COMPANION_ABSENT.equals(action)){stopping=true;reportStatus(STATE_WAITING,"HONDA BTU fuera de alcance");closeGatt();leaveForeground();releaseWakeLock();stopSelf();return START_NOT_STICKY;}
        if (ACTION_STOP.equals(action)) {
            stopping = true;
            AppConfig.setListeningEnabled(this, false);
            reportStatus(STATE_STOPPED, "Escucha pausada");
            closeGatt();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!AppConfig.isListeningEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        stopping = false;

        if (ACTION_COMPANION_PRESENT.equals(action)) {
            reconnectAttempt=0;handler.removeCallbacks(reconnectRunnable);connectToHonda();
        } else if (ACTION_RECONNECT.equals(action)) {
            reconnectAttempt = 0;
            closeGatt();
            handler.removeCallbacks(reconnectRunnable);
            handler.postDelayed(reconnectRunnable, 150L);
        } else {
            connectToHonda();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopping = true;
        handler.removeCallbacksAndMessages(null);
        closeGatt();
        try { unregisterReceiver(bluetoothReceiver); } catch (Throwable ignored) {}
        AppConfig.prefs(this).edit().putBoolean(AppConfig.KEY_SERVICE_RUNNING, false).apply();
        if (AppConfig.isListeningEnabled(this)) {
            AppConfig.setLastStatus(this, STATE_WAITING, "Esperando HONDA BTU");
        } else {
            AppConfig.setLastStatus(this, STATE_STOPPED, "Escucha pausada");
        }
        FlightRecorder.log(this, "SERVICE", "onDestroy · listening=" + AppConfig.isListeningEnabled(this));
        leaveForeground();releaseWakeLock();super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void connectToHonda() {
        if (stopping || subscribed || gatt != null || !AppConfig.isListeningEnabled(this)) return;

        // Quality gate v0.11.1: on devices with Companion support, GATT is no longer
        // allowed to bypass the Android association/presence lifecycle.
        if (CompanionController.supported(this)) {
            if (!CompanionController.hasAssociation(this)) {
                reportStatus(STATE_WAITING, "Vinculación HONDA BTU requerida");
                FlightRecorder.log(this, "COMPANION", "BLOCK GATT · UNASSOCIATED");
                stopSelf();
                return;
            }
            if (!AppConfig.isCompanionPresent(this)) {
                reportStatus(STATE_WAITING, "Esperando HONDA BTU · Companion");
                FlightRecorder.log(this, "COMPANION", "BLOCK GATT · ABSENT");
                stopSelf();
                return;
            }
        }

        if (!hasBluetoothPermission(this)) {
            reportStatus(STATE_ERROR, "Falta permiso Bluetooth");
            return;
        }

        BluetoothManager manager = getSystemService(BluetoothManager.class);
        if (manager == null) {
            reportStatus(STATE_ERROR, "Bluetooth no disponible en este dispositivo");
            scheduleReconnect();
            return;
        }

        BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            reportStatus(STATE_WAITING, "Bluetooth desactivado · esperando");
            scheduleReconnect(30_000L, false);
            return;
        }

        BluetoothDevice target = findHondaDevice(adapter);
        if (target == null) {
            reportStatus(STATE_WAITING,
                    "Esperando " + AppConfig.getDeviceFilter(this) + " emparejado");
            scheduleReconnect();
            return;
        }

        try {
            String name = safeName(target);
            reportStatus(STATE_CONNECTING, "Conectando a " + name + "…");

            BluetoothGatt newGatt;
            if (Build.VERSION.SDK_INT >= 26) {
                newGatt = target.connectGatt(this, true, callback,
                        BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK);
            } else {
                newGatt = target.connectGatt(this, true, callback);
            }

            if (newGatt == null) {
                failAndReconnect("connectGatt devolvió una conexión nula");
                return;
            }

            gatt = newGatt;
            handler.removeCallbacks(connectTimeoutRunnable);
            // autoConnect=true mantiene la intención de conexión incluso con la moto apagada.
            // El timeout largo sólo recupera stacks GATT realmente atascados.
            handler.postDelayed(connectTimeoutRunnable, CONNECT_TIMEOUT_MS);
        } catch (Throwable t) {
            Log.e(TAG, "connectGatt", t);
            gatt = null;
            failAndReconnect("Error al iniciar conexión BLE: " + t.getClass().getSimpleName());
        }
    }

    private BluetoothDevice findHondaDevice(BluetoothAdapter adapter) {
        String filter = AppConfig.getDeviceFilter(this).toUpperCase(Locale.ROOT);
        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            for (BluetoothDevice device : bonded) {
                String name = safeName(device);
                if (name.toUpperCase(Locale.ROOT).contains(filter)) return device;
            }
        } catch (SecurityException e) {
            reportStatus(STATE_ERROR, "Permiso Bluetooth denegado");
        }
        return null;
    }

    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt bluetoothGatt, int status, int newState) {
            handler.post(() -> handleConnectionStateChange(bluetoothGatt, status, newState));
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt bluetoothGatt, int status) {
            handler.post(() -> handleServicesDiscovered(bluetoothGatt, status));
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt bluetoothGatt,
                                      BluetoothGattDescriptor descriptor, int status) {
            handler.post(() -> handleDescriptorWrite(bluetoothGatt, descriptor, status));
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt bluetoothGatt,
                                            BluetoothGattCharacteristic characteristic,
                                            byte[] value) {
            byte[] safeValue = value == null ? null : Arrays.copyOf(value, value.length);
            handler.post(() -> {
                if (isCurrentGatt(bluetoothGatt) && EVENT_UUID.equals(characteristic.getUuid())) {
                    handleEvent(safeValue);
                }
            });
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onCharacteristicChanged(BluetoothGatt bluetoothGatt,
                                            BluetoothGattCharacteristic characteristic) {
            if (Build.VERSION.SDK_INT < 33) {
                byte[] value = characteristic.getValue();
                byte[] safeValue = value == null ? null : Arrays.copyOf(value, value.length);
                handler.post(() -> {
                    if (isCurrentGatt(bluetoothGatt) && EVENT_UUID.equals(characteristic.getUuid())) {
                        handleEvent(safeValue);
                    }
                });
            }
        }
    };

    private void handleConnectionStateChange(BluetoothGatt bluetoothGatt, int status, int newState) {
        FlightRecorder.log(this, "GATT", "state status=" + status + " newState=" + newState + " · " + FlightRecorder.powerState(this));
        if (!isCurrentGatt(bluetoothGatt)) {
            safeClose(bluetoothGatt);
            return;
        }

        handler.removeCallbacks(connectTimeoutRunnable);

        if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
            enterForeground("BTU conectada · preparando escucha…");acquireWakeLock();
            reportStatus(STATE_CONNECTING, "BTU conectada · leyendo servicios…");
            try {
                boolean started = bluetoothGatt.discoverServices();
                if (!started) {
                    failAndReconnect("No se pudo iniciar el descubrimiento GATT");
                    return;
                }
                handler.removeCallbacks(discoveryTimeoutRunnable);
                handler.postDelayed(discoveryTimeoutRunnable, DISCOVERY_TIMEOUT_MS);
            } catch (Throwable t) {
                Log.e(TAG, "discoverServices", t);
                failAndReconnect("Error descubriendo servicios GATT");
            }
            return;
        }

        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            failAndReconnect("BTU desconectada · estado GATT " + status);
        } else if (status != BluetoothGatt.GATT_SUCCESS) {
            failAndReconnect("Error de conexión GATT " + status);
        }
    }

    private void handleServicesDiscovered(BluetoothGatt bluetoothGatt, int status) {
        if (!isCurrentGatt(bluetoothGatt)) return;
        handler.removeCallbacks(discoveryTimeoutRunnable);

        if (status != BluetoothGatt.GATT_SUCCESS) {
            failAndReconnect("Descubrimiento GATT falló · " + status);
            return;
        }

        BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
        if (service == null) {
            failAndReconnect("Servicio RoadSync no disponible en la BTU");
            return;
        }

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(EVENT_UUID);
        if (characteristic == null) {
            failAndReconnect("Canal de eventos RoadSync no disponible");
            return;
        }

        try {
            reportStatus(STATE_CONNECTING, "Activando eventos de la piña…");
            boolean localOk = bluetoothGatt.setCharacteristicNotification(characteristic, true);
            BluetoothGattDescriptor cccd = characteristic.getDescriptor(CCCD_UUID);
            if (!localOk || cccd == null) {
                failAndReconnect("No se pudo preparar la suscripción BLE");
                return;
            }

            boolean writeStarted;
            if (Build.VERSION.SDK_INT >= 33) {
                writeStarted = bluetoothGatt.writeDescriptor(
                        cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        == BluetoothGatt.GATT_SUCCESS;
            } else {
                //noinspection deprecation
                cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                //noinspection deprecation
                writeStarted = bluetoothGatt.writeDescriptor(cccd);
            }

            if (!writeStarted) {
                failAndReconnect("La BTU rechazó la activación de notificaciones");
                return;
            }

            handler.removeCallbacks(subscribeTimeoutRunnable);
            handler.postDelayed(subscribeTimeoutRunnable, SUBSCRIBE_TIMEOUT_MS);
        } catch (Throwable t) {
            Log.e(TAG, "subscribe", t);
            failAndReconnect("Error activando notificaciones BLE");
        }
    }

    private void handleDescriptorWrite(BluetoothGatt bluetoothGatt,
                                       BluetoothGattDescriptor descriptor, int status) {
        if (!isCurrentGatt(bluetoothGatt) || !CCCD_UUID.equals(descriptor.getUuid())) return;
        handler.removeCallbacks(subscribeTimeoutRunnable);

        if (status != BluetoothGatt.GATT_SUCCESS) {
            failAndReconnect("Error suscribiendo eventos · " + status);
            return;
        }

        subscribed = true;
        reconnectAttempt = 0;
        companionRecoveryAttempts = 0;
        commandEngine.reset();
        reportStatus(STATE_ACTIVE, "Escucha activa · " + CommandRepository.load(this).size() + " comandos");
    }

    private void handleEvent(byte[] value) {
        if (value == null || value.length == 0 || !subscribed) return;
        FlightRecorder.log(this, "BLE_RX", "bytes=" + bytesToHex(value) + " · " + FlightRecorder.powerState(this));

        int code = value[0] & 0xFF;
        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date());
        String raw = timestamp + "  evento " + GestureEngine.hex(code);
        AppConfig.prefs(this).edit().putInt(AppConfig.KEY_LAST_EVENT_CODE, code).apply();
        reportEvent(raw);

        Command command = commandEngine.onEvent(this, code);
        if (command != null) {
            reportEvent("Comando: " + command.name + " · " + command.gestureLabel());
            FlightRecorder.log(this, "COMMAND", command.gestureLabel() + " -> " + command.name + " action=" + command.actionType);
            ActionExecutor.execute(this, command);
        }
    }

    private void failAndReconnect(String reason) {
        if (stopping) return;
        Log.w(TAG, reason);
        subscribed = false;
        commandEngine.reset();
        cancelStageTimeouts();
        closeGatt();leaveForeground();releaseWakeLock();reportStatus(STATE_ERROR,reason);
        if(AppConfig.isCompanionEnabled(this)){
            if(!AppConfig.isCompanionPresent(this)){
                reportStatus(STATE_WAITING,"Esperando HONDA BTU · modo Companion");stopSelf();return;
            }
            companionRecoveryAttempts++;
            FlightRecorder.log(this,"GATT_RECOVERY","attempt="+companionRecoveryAttempts+" reason="+reason);
            if(companionRecoveryAttempts>MAX_COMPANION_RECOVERY_ATTEMPTS){
                reportStatus(STATE_WAITING,"BTU presente pero GATT no responde · esperando nuevo evento de presencia");
                FlightRecorder.log(this,"GATT_RECOVERY","pausado tras "+MAX_COMPANION_RECOVERY_ATTEMPTS+" intentos");
                stopSelf();return;
            }
        }
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        int index = Math.min(reconnectAttempt, RECONNECT_DELAYS_MS.length - 1);
        long delay = RECONNECT_DELAYS_MS[index];
        reconnectAttempt = Math.min(reconnectAttempt + 1, RECONNECT_DELAYS_MS.length - 1);
        scheduleReconnect(delay, true);
    }

    private void scheduleReconnect(long delayMs, boolean announce) {
        if (stopping || !AppConfig.isListeningEnabled(this)) return;
        handler.removeCallbacks(reconnectRunnable);
        if (announce) {
            reportStatus(STATE_WAITING,
                    "Esperando HONDA BTU · reintento en " + Math.max(1L, delayMs / 1000L) + " s");
        }
        handler.postDelayed(reconnectRunnable, delayMs);
    }

    private final Runnable reconnectRunnable = () -> {
        if (gatt == null && !subscribed) connectToHonda();
    };

    private final Runnable connectTimeoutRunnable = () -> {
        if (gatt != null && !subscribed) failAndReconnect("Tiempo de espera agotado conectando a la BTU");
    };

    private final Runnable discoveryTimeoutRunnable = () -> {
        if (gatt != null && !subscribed) failAndReconnect("Tiempo de espera agotado leyendo servicios GATT");
    };

    private final Runnable subscribeTimeoutRunnable = () -> {
        if (gatt != null && !subscribed) failAndReconnect("Tiempo de espera agotado activando eventos BLE");
    };

    private void cancelStageTimeouts() {
        handler.removeCallbacks(connectTimeoutRunnable);
        handler.removeCallbacks(discoveryTimeoutRunnable);
        handler.removeCallbacks(subscribeTimeoutRunnable);
    }

    private boolean isCurrentGatt(BluetoothGatt candidate) {
        return candidate != null && candidate == gatt && !stopping;
    }

    private void closeGatt() {
        cancelStageTimeouts();
        BluetoothGatt local = gatt;
        gatt = null;
        subscribed = false;
        if (local != null) safeClose(local);
    }

    private void safeClose(BluetoothGatt bluetoothGatt) {
        try { bluetoothGatt.disconnect(); } catch (Throwable ignored) {}
        try { bluetoothGatt.close(); } catch (Throwable ignored) {}
    }

    private void enterForeground(String text){
        if(foregroundStarted)return;
        try{Notification n=buildNotification(text);if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);else startForeground(NOTIFICATION_ID,n);foregroundStarted=true;FlightRecorder.log(this,"FGS","ON · BTU conectada");}
        catch(Throwable t){FlightRecorder.log(this,"FGS","error="+t);}
    }
    private void leaveForeground(){if(!foregroundStarted)return;try{stopForeground(STOP_FOREGROUND_REMOVE);}catch(Throwable ignored){}foregroundStarted=false;FlightRecorder.log(this,"FGS","OFF · notificación retirada");}

    private void reportStatus(String state, String message) {
        Log.i(TAG, "[" + state + "] " + message);
        FlightRecorder.log(this, "STATUS", state + " · " + message + " · " + FlightRecorder.powerState(this));
        AppConfig.setLastStatus(this, state, message);
        broadcast(KIND_STATUS);

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && foregroundStarted) {
            nm.notify(NOTIFICATION_ID, buildNotification(message));
        }
    }

    private void reportEvent(String message) {
        Log.i(TAG, "[event] " + message);
        AppConfig.prefs(this).edit().putString(AppConfig.KEY_LAST_EVENT, message).apply();
        broadcast(KIND_EVENT);
    }

    private void broadcast(String kind) {
        Intent update = new Intent(ACTION_UPDATE)
                .setPackage(getPackageName())
                .putExtra(EXTRA_KIND, kind);
        sendBroadcast(update);
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(
                this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent reconnect = new Intent(this, HondaBleService.class).setAction(ACTION_RECONNECT);
        PendingIntent reconnectPi = PendingIntent.getService(
                this, 2, reconnect,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent pause = new Intent(this, HondaBleService.class).setAction(ACTION_STOP);
        PendingIntent pausePi = PendingIntent.getService(
                this, 3, pause,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        GestureEngine.Type gesture = AppConfig.getGestureType(this);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Honda Assist Bridge")
                .setContentText(text)
                .setSubText("Automatización de controles")
                .setContentIntent(openPi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(
                        R.drawable.ic_notification, "Reconectar", reconnectPi).build())
                .addAction(new Notification.Action.Builder(
                        R.drawable.ic_notification, "Pausar", pausePi).build())
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Control Honda Assist",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Mantiene la escucha BLE de HONDA BTU activa en segundo plano.");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void registerBluetoothReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(bluetoothReceiver, filter);
        }
    }

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || stopping) return;
            String action = intent.getAction();

            if (Intent.ACTION_SCREEN_OFF.equals(action) || Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_USER_PRESENT.equals(action)) {
                FlightRecorder.log(HondaBleService.this, "SCREEN", action + " · " + FlightRecorder.powerState(HondaBleService.this));
                return;
            }

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_ON) {
                    reconnectAttempt = 0;
                    closeGatt();
                    handler.removeCallbacks(reconnectRunnable);
                    handler.postDelayed(reconnectRunnable, 500L);
                } else if (state == BluetoothAdapter.STATE_OFF) {
                    closeGatt();leaveForeground();releaseWakeLock();
                    reportStatus(STATE_WAITING, "Bluetooth desactivado · esperando");
                }
                return;
            }

            BluetoothDevice device = getBluetoothDevice(intent);
            if (device == null || !matchesConfiguredDevice(device)) return;

            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action) && !subscribed) {
                // RoadSync suele provocar esta conexión al ponerse en marcha. Si nuestra
                // escucha estaba en backoff, adelantamos el intento inmediatamente.
                reconnectAttempt = 0;
                if (gatt == null) {
                    handler.removeCallbacks(reconnectRunnable);
                    handler.postDelayed(reconnectRunnable, 250L);
                }
            }
        }
    };

    private void acquireWakeLock() {
        try {
            PowerManager pm = getSystemService(PowerManager.class);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HondaAssistBridge:BleListener");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
                FlightRecorder.log(this, "POWER", "PARTIAL_WAKE_LOCK adquirido");
            }
        } catch (Throwable t) { FlightRecorder.log(this, "POWER", "WakeLock error: " + t); }
    }

    private void releaseWakeLock() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Throwable ignored) {}
        wakeLock = null;
    }

    private static String bytesToHex(byte[] value) {
        StringBuilder s = new StringBuilder();
        for (byte b : value) { if (s.length() > 0) s.append(' '); s.append(String.format(Locale.ROOT, "%02X", b & 0xFF)); }
        return s.toString();
    }

    private BluetoothDevice getBluetoothDevice(Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
            }
            //noinspection deprecation
            return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean matchesConfiguredDevice(BluetoothDevice device) {
        if (!hasBluetoothPermission(this)) return false;
        String filter = AppConfig.getDeviceFilter(this).toUpperCase(Locale.ROOT);
        return safeName(device).toUpperCase(Locale.ROOT).contains(filter);
    }

    private String safeName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name == null || name.trim().isEmpty() ? "HONDA BTU" : name;
        } catch (SecurityException ignored) {
            return "HONDA BTU";
        }
    }
}
