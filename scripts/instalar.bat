@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
rem Cristian Plaza TV - instalador para Windows
rem Uso:  instalar.bat 192.168.1.50   (o doble clic y escribe la IP)
cd /d "%~dp0"
set PAQ=com.cristianplaza.tv
set SERVICIO=%PAQ%/%PAQ%.BotonesControl

where adb >nul 2>&1 || (
  echo [X] Falta ADB. Descarga "SDK Platform-Tools" para Windows y copia adb.exe en esta carpeta:
  echo     https://developer.android.com/tools/releases/platform-tools
  pause & exit /b 1
)
set IP=%1
if "%IP%"=="" set /p IP=IP de tu TV (Ajustes - Red - tu Wi-Fi): 
set ANDROID_SERIAL=%IP%:5555

echo Conectando con %IP%... (acepta "Permitir depuracion" en el TV)
for /l %%i in (1,1,30) do (
  adb connect %ANDROID_SERIAL% >nul 2>&1
  for /f %%s in ('adb get-state 2^>nul') do if "%%s"=="device" goto conectado
  timeout /t 2 >nul
)
echo [X] No se pudo conectar. Revisa que la depuracion por red este activa.
pause & exit /b 1

:conectado
for %%f in (CristianPlazaTV-*.apk) do set APK=%%f
echo Instalando %APK%
adb install -r "%APK%"

echo Activando el servicio de botones del control
adb shell pm grant %PAQ% android.permission.WRITE_SECURE_SETTINGS
for /f "delims=" %%a in ('adb shell settings get secure enabled_accessibility_services') do set ACT=%%a
if "!ACT!"=="" set ACT=null
if "!ACT!"=="null" (set NUEVO=%SERVICIO%) else (
  echo !ACT! | find "%SERVICIO%" >nul && (set NUEVO=!ACT!) || (set NUEVO=!ACT!:%SERVICIO%)
)
adb shell settings put secure enabled_accessibility_services "!NUEVO!"
adb shell settings put secure accessibility_enabled 1

echo.
echo Quieres que Cristian Plaza TV sea tu PANTALLA DE INICIO?
echo Esto desactiva el inicio de Google TV (se puede deshacer con deshacer.bat).
set /p R=Escribe s para si: 
if /i "%R%"=="s" (
  for %%p in (com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.tungsten.setupwraith) do (
    adb shell pm list packages | find "package:%%p" >nul && adb shell pm disable-user --user 0 %%p
  )
  adb shell cmd package set-home-activity --user 0 %PAQ%/.MainActivity
)
adb shell input keyevent KEYCODE_HOME
echo.
echo Listo. Manten presionado OK sobre una app para moverla u ocultarla; el engranaje abre el menu.
pause
