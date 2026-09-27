$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$apk = Join-Path $projectRoot 'HondaAssistBridge-debug.apk'
$adb = 'C:\Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw 'ADB global no encontrado en C:\Android\Sdk\platform-tools\adb.exe' }
if (-not (Test-Path $apk)) { throw 'Primero ejecuta .\build-windows.ps1' }
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'adb install falló.' }
