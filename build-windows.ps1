param(
    [switch]$Clean,
    [switch]$Install
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $projectRoot) {
    throw "Este archivo debe ejecutarse como script .ps1, no pegarse línea a línea."
}
Set-Location $projectRoot

Write-Host ""
Write-Host "=== Honda Assist Bridge: build Windows ===" -ForegroundColor Cyan
Write-Host "Proyecto: $projectRoot" -ForegroundColor DarkGray

# JAVA / JDK 17+
$java = Get-Command java -ErrorAction SilentlyContinue
$javac = Get-Command javac -ErrorAction SilentlyContinue
if (-not $java -or -not $javac) {
    throw "JDK 17+ no está instalado o no está disponible en PATH."
}

$javacVersion = (& $javac.Source -version | Out-String).Trim()
if ($javacVersion -notmatch 'javac\s+(\d+)') {
    throw "No se pudo detectar la versión del JDK: $javacVersion"
}
if ([int]$Matches[1] -lt 17) {
    throw "Se necesita JDK 17 o superior. Detectado: $javacVersion"
}

$javaBin = Split-Path $java.Source -Parent
$env:JAVA_HOME = Split-Path $javaBin -Parent
Write-Host "Java:        $javacVersion" -ForegroundColor Green
Write-Host "JAVA_HOME:   $env:JAVA_HOME" -ForegroundColor DarkGray

# ANDROID SDK GLOBAL. No usa .android-sdk local ni descarga nada.
$sdkRoot = "C:\Android\Sdk"
if (-not (Test-Path $sdkRoot)) {
    if ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) {
        $sdkRoot = $env:ANDROID_SDK_ROOT
    } elseif ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) {
        $sdkRoot = $env:ANDROID_HOME
    } else {
        throw "No se encontró Android SDK global. Ruta esperada: C:\Android\Sdk"
    }
}

$androidJar = Join-Path $sdkRoot "platforms\android-36\android.jar"
$buildTools = Join-Path $sdkRoot "build-tools\36.0.0\aapt2.exe"
$adbExe = Join-Path $sdkRoot "platform-tools\adb.exe"
if (-not (Test-Path $androidJar)) { throw "Falta Android Platform 36: $androidJar" }
if (-not (Test-Path $buildTools)) { throw "Faltan Android Build Tools 36.0.0: $buildTools" }
if (-not (Test-Path $adbExe)) { throw "Faltan Android Platform Tools: $adbExe" }

$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
Write-Host "Android SDK: $sdkRoot" -ForegroundColor Green
Write-Host "Android 36:  OK" -ForegroundColor Green

# GRADLE GLOBAL. Primero PATH, luego la instalación fija de este equipo.
$gradle = Get-Command gradle -ErrorAction SilentlyContinue
$gradleExe = if ($gradle) { $gradle.Source } else { "C:\Gradle\gradle-9.6.0\bin\gradle.bat" }
if (-not (Test-Path $gradleExe)) {
    throw "No se encontró Gradle 9.6.0. Ruta esperada: C:\Gradle\gradle-9.6.0\bin\gradle.bat"
}

$gradleOutput = (& $gradleExe --version | Out-String)
if ($gradleOutput -notmatch 'Gradle\s+9\.6\.0') {
    Write-Warning "Este proyecto está validado con Gradle 9.6.0."
} else {
    Write-Host "Gradle:      9.6.0 OK" -ForegroundColor Green
}

if (-not (Test-Path (Join-Path $projectRoot "settings.gradle"))) {
    throw "No existe settings.gradle en $projectRoot"
}
if (-not (Test-Path (Join-Path $projectRoot "build.gradle"))) {
    throw "No existe build.gradle en $projectRoot"
}
Write-Host "Proyecto Gradle: OK" -ForegroundColor Green
Write-Host ""

if ($Clean) {
    Write-Host "Limpiando proyecto..." -ForegroundColor Yellow
    & $gradleExe --no-daemon clean
    if ($LASTEXITCODE -ne 0) { throw "Gradle clean falló." }
}

Write-Host "Compilando APK debug..." -ForegroundColor Cyan
& $gradleExe --no-daemon assembleDebug
if ($LASTEXITCODE -ne 0) { throw "La compilación Gradle falló." }

$sourceApk = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
$outputApk = Join-Path $projectRoot "HondaAssistBridge-debug.apk"
if (-not (Test-Path $sourceApk)) { throw "No se encontró el APK generado: $sourceApk" }
Copy-Item -Path $sourceApk -Destination $outputApk -Force

$hash = (Get-FileHash -Path $outputApk -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host ""
Write-Host "=========================================" -ForegroundColor Green
Write-Host " BUILD COMPLETADO" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
Write-Host "APK:    $outputApk"
Write-Host "SHA256: $hash"

if ($Install) {
    Write-Host ""
    Write-Host "Comprobando dispositivo ADB..." -ForegroundColor Cyan
    $devices = & $adbExe devices
    $connected = $devices | Select-String -Pattern '\sdevice$'
    if (-not $connected) { throw "No hay ningún dispositivo Android conectado por ADB." }

    Write-Host "Instalando APK..." -ForegroundColor Cyan
    & $adbExe install -r $outputApk
    if ($LASTEXITCODE -ne 0) { throw "ADB install falló." }
    Write-Host "APK instalada correctamente." -ForegroundColor Green
}

Write-Host ""
Write-Host "Sin descargas: se han usado JDK, Android SDK y Gradle globales." -ForegroundColor DarkGray
