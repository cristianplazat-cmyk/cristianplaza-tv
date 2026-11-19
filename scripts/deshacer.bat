@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
rem Cristian Plaza TV - deshace la instalacion y vuelve al inicio original de Google TV.
cd /d "%~dp0"
set PAQ=com.cristianplaza.tv
set SERVICIO=%PAQ%/%PAQ%.BotonesControl
set IP=%1
if "%IP%"=="" set /p IP=IP de tu TV: 
set ANDROID_SERIAL=%IP%:5555
adb connect %ANDROID_SERIAL% >nul
timeout /t 2 >nul

for %%p in (com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.tungsten.setupwraith) do (
  adb shell pm enable %%p >nul 2>&1
)
for /f "delims=" %%a in ('adb shell settings get secure enabled_accessibility_services') do set ACT=%%a
set RESTO=!ACT:%SERVICIO%=!
set RESTO=!RESTO:::=:!
if "!RESTO:~0,1!"==":" set RESTO=!RESTO:~1!
if "!RESTO:~-1!"==":" set RESTO=!RESTO:~0,-1!
if "!RESTO!"=="" (adb shell settings delete secure enabled_accessibility_services) else (adb shell settings put secure enabled_accessibility_services "!RESTO!")

if exist apps-quitadas.txt (
  for /f "usebackq delims=" %%p in ("apps-quitadas.txt") do adb shell cmd package install-existing --user 0 %%p
)
adb uninstall %PAQ%
adb shell input keyevent KEYCODE_HOME
echo Listo: el TV volvio a su inicio original.
pause
