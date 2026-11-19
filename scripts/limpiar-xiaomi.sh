#!/bin/bash
# OPCIONAL — Solo para TV Xiaomi con Google TV.
# Quita publicidad, rastreo y contenido promocional de Xiaomi/Google que no se puede desinstalar
# desde el menú. Es reversible: no borra nada del sistema (deshacer.sh lo restaura).
# Uso:  ./limpiar-xiaomi.sh 192.168.1.50
cd "$(dirname "$0")"
# adb no debe leer el teclado (si no, se "come" las respuestas a las preguntas)
adb_() { command adb "$@" </dev/null; }
IP=$1
if [ -z "$IP" ]; then read -rp "IP de tu TV: " IP || IP=""; fi
export ANDROID_SERIAL="$IP:5555"
adb_ connect "$ANDROID_SERIAL" >/dev/null; sleep 2

LISTA="com.miui.tv.analytics
com.xiaomi.statistic
com.mitv.tvhome.mitvplus
com.mitv.tvhome.michannel
com.mitv.tvhome.oemtab
com.xm.webcontent
com.xiaomi.mitv.scenemode
com.google.android.feedback
com.android.tv.feedbackconsent"

echo "Se quitarán estas apps (rastreo y publicidad):"; echo "$LISTA" | sed 's/^/   /'
read -rp "Escribe s para continuar: " R || R=""
[ "$R" = "s" ] || [ "$R" = "S" ] || exit 0
for p in $LISTA; do
  if adb_ shell pm list packages | tr -d '\r' | grep -qx "package:$p"; then
    adb_ shell pm uninstall-system-updates "$p" >/dev/null 2>&1
    R=$(adb_ shell pm uninstall --user 0 "$p" | tr -d '\r')
    echo "   $p: $R"; [ "$R" = "Success" ] && echo "$p" >> apps-quitadas.txt
  fi
done
adb_ shell df -h /data | tail -1 | awk '{print "Espacio libre ahora: "$4}'
