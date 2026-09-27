# Honda Assist Bridge v0.4

Aplicación complementaria para Honda RoadSync. Escucha en paralelo los eventos BLE de `HONDA BTU` y permite invocar Gemini / Assistant desde un gesto configurable de la piña.

## Cambios principales de v0.4

- **Escucha automática**: tras conceder permisos una vez, el servicio se mantiene activo en segundo plano, usa `START_STICKY` y vuelve a iniciarse tras `BOOT_COMPLETED` o actualización de la app.
- **No hace falta abrir Honda Assist Bridge antes de RoadSync**. La escucha ya está preparada; cuando la BTU aparece o RoadSync provoca la conexión Bluetooth, se adelanta el intento de conexión.
- **Gesto configurable**: doble izquierda, doble abajo, doble arriba, doble derecha, izquierda larga, abajo larga, arriba larga o derecha larga.
- **Aprendizaje por gesto**: selecciona un gesto y pulsa *Aprender este gesto*. La app guarda el código BLE real observado.
- **Interfaz de producción**: panel de estado, selector de gesto, escucha automática, reconexión, configuración del dispositivo e icono propio.
- **Build Windows sin descargas**: usa JDK, Android SDK y Gradle instalados globalmente.

## Importante: RoadSync sigue recibiendo los botones

Honda Assist Bridge es un segundo cliente BLE. Detectar un gesto distinto **no bloquea ni consume** ese gesto dentro de RoadSync. Si eliges `↓ ↓`, RoadSync también procesará esas dos pulsaciones. Hacer el gesto exclusivo requeriría interceptar RoadSync (hook/patch/proxy), algo deliberadamente fuera de esta versión.

## Inicio automático

Android no ofrece un broadcast público y fiable que diga “RoadSync acaba de abrirse”. En vez de pedir Accesibilidad o Acceso de uso para vigilar otra aplicación, v0.4 mantiene un servicio en primer plano de tipo `connectedDevice` y queda esperando a `HONDA BTU`. Es más estable y evita permisos invasivos.

Después del primer inicio:

1. Concede Bluetooth y notificaciones.
2. Deja **Escucha automática** activada.
3. Puedes cerrar Honda Assist Bridge.
4. Al encender la moto / abrir RoadSync, el servicio intentará enlazar con la BTU sin volver a abrir la app.

La notificación persistente de Android permite **Reconectar** o **Pausar**.

## Gestos y códigos por defecto

Captura conocida:

- `01` arriba corto
- `02` abajo corto
- `03` izquierda corto
- `04` derecha corto
- `07` arriba largo
- `08` abajo largo
- `09` izquierda largo
- `0A` derecha largo

La configuración inicial conserva `← ←` / `0x03`. Si un mapeo no coincide con tu BTU, usa **Aprender este gesto**.

Para dobles pulsaciones la ventana es de **550 ms**. Después de una invocación se aplica un cooldown de **1,5 s**.

## Invocación del asistente

Se conserva el flujo validado en v0.3:

```text
android.intent.action.VOICE_COMMAND
→ com.google.android.googlequicksearchbox/
  com.google.android.voicesearch.handsfree.HandsFreeActivity
```

Google abre internamente la interfaz de Gemini. No requiere root ni Shizuku.

## Robustez BLE

v0.4 añade:

- timeout de conexión: 15 s;
- timeout de descubrimiento GATT: 10 s;
- timeout de suscripción CCCD: 8 s;
- backoff de reconexión: 2 / 4 / 8 / 15 / 30 s;
- reconexión inmediata cuando vuelve Bluetooth;
- intento adelantado cuando se detecta conexión ACL de la BTU;
- cierre explícito de GATT antes de reintentar;
- callbacks serializados en el hilo principal para evitar carreras;
- rechazo de callbacks de conexiones GATT antiguas;
- notificación no actualizada por cada pulsación para reducir trabajo innecesario;
- reinicio `START_STICKY` y autoarranque tras reinicio/actualización.

## Compilar en Windows

Requiere una instalación global previa:

```text
JDK 17+
C:\Android\Sdk
C:\Gradle\gradle-9.6.0
```

En PowerShell:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\build-windows.ps1
```

Compilar e instalar:

```powershell
.\build-windows.ps1 -Install
```

El script **no descarga nada**.

## Versiones

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- JDK 17
- compileSdk / targetSdk 36
- minSdk 26
- Build Tools 36.0.0
