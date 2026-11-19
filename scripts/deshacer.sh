#!/bin/bash
# Cristian Plaza TV — deshace la instalación y vuelve al inicio original de Google TV.
# Uso:  ./deshacer.sh 192.168.1.50
cd "$(dirname "$0")"
# adb no debe leer el teclado (si no, se "come" las respuestas a las preguntas)
adb_() { command adb "$@" </dev/null; }
PAQ=com.cristianplaza.tv
SERVICIO=$PAQ/$PAQ.BotonesControl
IP=$1
if [ -z "$IP" ]; then read -rp "IP de tu TV: " IP || IP=""; fi
export ANDROID_SERIAL="$IP:5555"
adb_ connect "$ANDROID_SERIAL" >/dev/null; sleep 2

for p in com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.tungsten.setupwraith; do
  adb_ shell pm enable $p >/dev/null 2>&1 && echo "   reactivado: $p"
done
ACT=$(adb_ shell settings get secure enabled_accessibility_services | tr -d '\r')
RESTO=$(echo "$ACT" | tr ':' '\n' | grep -v "$SERVICIO" | paste -sd: -)
if [ -z "$RESTO" ]; then adb_ shell settings delete secure enabled_accessibility_services >/dev/null
else adb_ shell settings put secure enabled_accessibility_services "$RESTO"; fi

# Apps quitadas con limpiar-xiaomi.sh (si se usó)
if [ -f apps-quitadas.txt ]; then
  while read -r p; do [ -n "$p" ] && adb_ shell cmd package install-existing --user 0 "$p" >/dev/null 2>&1 && echo "   restaurada: $p"; done < apps-quitadas.txt
fi

adb_ uninstall $PAQ
adb_ shell input keyevent KEYCODE_HOME
echo "✅ Listo: el TV volvió a su inicio original."
