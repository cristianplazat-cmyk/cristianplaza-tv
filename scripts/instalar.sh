#!/bin/bash
# Cristian Plaza TV — instalador para Mac / Linux
# Uso:  ./instalar.sh 192.168.1.50      (la IP de tu TV)
set -e
cd "$(dirname "$0")"
# adb no debe leer el teclado (si no, se "come" las respuestas a las preguntas)
adb_() { command adb "$@" </dev/null; }
PAQ=com.cristianplaza.tv
SERVICIO=$PAQ/$PAQ.BotonesControl

command -v adb >/dev/null || { echo "❌ Falta ADB. Instálalo: https://developer.android.com/tools/releases/platform-tools"; exit 1; }
IP=$1
if [ -z "$IP" ]; then read -rp "IP de tu TV (Ajustes → Red → tu Wi-Fi): " IP || IP=""; fi
export ANDROID_SERIAL="$IP:5555"

echo "→ Conectando con $IP… (acepta 'Permitir depuración' en el TV)"
adb_ connect "$ANDROID_SERIAL" >/dev/null
for i in $(seq 1 30); do [ "$(adb_ get-state 2>/dev/null)" = "device" ] && break; sleep 2; adb_ connect "$ANDROID_SERIAL" >/dev/null 2>&1; done
[ "$(adb_ get-state 2>/dev/null)" = "device" ] || { echo "❌ No se pudo conectar. Revisa que la depuración por red esté activa."; exit 1; }

APK=$(ls CristianPlazaTV-*.apk ../dist/CristianPlazaTV-*.apk 2>/dev/null | sort -V | tail -1)
[ -n "$APK" ] || { echo "❌ No encuentro CristianPlazaTV-*.apk junto a este script."; exit 1; }
echo "→ Instalando $APK"
adb_ install -r "$APK" | tail -1

echo "→ Activando el servicio de botones del control"
adb_ shell pm grant $PAQ android.permission.WRITE_SECURE_SETTINGS || true
ACT=$(adb_ shell settings get secure enabled_accessibility_services | tr -d '\r')
if [ -z "$ACT" ] || [ "$ACT" = "null" ]; then NUEVO=$SERVICIO
elif echo "$ACT" | grep -q "$SERVICIO"; then NUEVO=$ACT
else NUEVO="$ACT:$SERVICIO"; fi
adb_ shell settings put secure enabled_accessibility_services "$NUEVO"
adb_ shell settings put secure accessibility_enabled 1

echo ""
echo "¿Quieres que Cristian Plaza TV sea tu PANTALLA DE INICIO?"
echo "Esto desactiva el inicio de Google TV (se puede deshacer con ./deshacer.sh)."
read -rp "Escribe s para sí: " R || R=""
if [ "$R" = "s" ] || [ "$R" = "S" ]; then
  for p in com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.tungsten.setupwraith; do
    if adb_ shell pm list packages | tr -d '\r' | grep -qx "package:$p"; then
      adb_ shell pm disable-user --user 0 $p >/dev/null && echo "   desactivado: $p"
    fi
  done
  adb_ shell cmd package set-home-activity --user 0 $PAQ/.MainActivity >/dev/null
fi
adb_ shell input keyevent KEYCODE_HOME
echo ""
echo "✅ Listo. Mantén presionado OK sobre una app para moverla u ocultarla; el ⚙ abre el menú."
