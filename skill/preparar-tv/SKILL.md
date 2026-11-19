---
name: preparar-tv
description: Prepara un Android TV / Google TV (Xiaomi, TCL u otra marca) por ADB desde el computador. Quita el rastreo, la publicidad y las apps basura preinstaladas (limpieza básica o profunda, siempre con confirmación y reversible), instala el launcher Cristian Plaza TV como pantalla de inicio, deja listos los botones del control y verifica todo tras reiniciar. Úsala cuando pidan preparar, limpiar, debloatear, quitar rastreadores o personalizar un televisor Android/Google TV.
---

# Preparar un Android TV / Google TV

Procedimiento probado en un **Xiaomi TV (MediaTek, Android 14)** y una **TCL (Amlogic, Android 12)**, ambos con Google TV.
Todo se hace por ADB en la red local. **No requiere root**, no desbloquea nada y **todo es reversible**.
Listas de apps por marca: [listas.md](listas.md). Launcher: https://github.com/cristianplazat-cmyk/cristianplaza-tv

## 0. Reglas (obligatorias)

1. **Nunca quitar nada sin mostrar antes la lista y recibir un "sí" explícito del usuario.** Presentar la lista agrupada
   (rastreo/publicidad, limpieza profunda por grupos) y dejar que elija grupos. No quitar nada fuera de lo aprobado.
2. **Nunca tocar** (aunque parezcan basura): Play Store, Google Play Services (GMS), Chromecast integrado
   (`com.google.android.apps.mediashell`), AirPlay, Bluetooth, teclado, Ajustes, el actualizador del sistema,
   asistente de voz, entradas HDMI / TV en vivo / sintonizador, nada con `mediatek`, `amlogic`, `realtek`, `hardware`,
   `input`, `tvinput`, `systemui`, `provider`, `framework` en el nombre, ni las apps de streaming del usuario
   (Netflix, YouTube, Disney+, Max, Apple TV, Prime Video salvo que lo pida, etc.).
   En la duda, **no se quita**: se oculta en el launcher.
3. No proponer root, desbloqueo de bootloader, cambiar la animación de arranque ni flashear nada (riesgo de brick).
4. No hacer factory reset. No actualizar el firmware sin preguntar.
5. Si el usuario está viendo algo, no lanzar apps ni teclas sin avisar.
6. Guardar respaldo antes de quitar (paso 2) y generar el script para restaurar.

## 1. Conectar

El usuario activa en el TV: *Ajustes → Sistema → Información* → 7 toques en *Compilación* → *Opciones de desarrollador* →
**Depuración USB** y **Depuración por red** (si solo existe *Depuración inalámbrica*, usar `adb pair IP:puerto código`).
Pedirle la IP (*Ajustes → Red e Internet → su Wi-Fi*). Si no la sabe, buscar el TV en la red:

```bash
# macOS
for s in _androidtvremote2._tcp _googlecast._tcp; do dns-sd -B $s local. & P=$!; sleep 4; kill $P; done
# Linux: avahi-browse -rt _googlecast._tcp
nc -z -w 3 <IP> 5555 && echo "ADB abierto"
```

```bash
adb connect <IP>:5555
export ANDROID_SERIAL=<IP>:5555
```
El TV muestra **"¿Permitir depuración?"**: el usuario marca *Permitir siempre*. Esperar con un bucle de `adb get-state`
(hasta 2 min). Si da "No route to host" con el puerto abierto: `adb kill-server` y reconectar.
Sin `adb`: instalar *Android SDK Platform-Tools* (macOS: `brew install --cask android-platform-tools`).

## 2. Inventario y respaldo

```bash
adb shell getprop ro.product.manufacturer; adb shell getprop ro.product.model; adb shell getprop ro.build.version.release
adb shell pm list packages -3      # instaladas por el usuario
adb shell pm list packages -s      # de sistema
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER
adb shell df -h /data
```

Crear una carpeta `tv-<marca>-<modelo>/` en el directorio actual con:
- `paquetes-antes-<fecha>.txt` ← `adb shell pm list packages -f`
- `apps-quitadas.txt` (un paquete por línea, se va llenando al quitar)
- `restaurar.sh`: reconecta y ejecuta `adb shell cmd package install-existing --user 0 <pkg>` por cada línea de
  `apps-quitadas.txt`, reactiva el inicio original (`pm enable com.google.android.apps.tv.launcherx`,
  `pm enable com.google.android.tungsten.setupwraith`, o `com.google.android.tvlauncher` en Android TV clásico) y
  desinstala `com.cristianplaza.tv`.

Identificar la marca y buscar su sección en [listas.md](listas.md). Si la marca no está, usar el **método para marcas
nuevas** de listas.md.

## 3. Proponer y quitar

Armar la propuesta en dos niveles y preguntar (una pregunta con opciones múltiples sirve):
- **Básica (recomendada):** rastreo, telemetría, publicidad y contenido promocional.
- **Profunda (por grupos, opcional):** apps preinstaladas del fabricante y de Google que no son necesarias
  (tiendas y canales propios, asistentes de terceros, demos, manuales, navegador, reproductores, etc.).
Mostrar solo paquetes que **existen** en el TV, con nombre legible y para qué sirven.

Tras la aprobación, por cada paquete:
```bash
adb shell pm uninstall-system-updates <pkg> </dev/null      # libera la actualización instalada
adb shell pm uninstall --user 0 <pkg> </dev/null && echo <pkg> >> apps-quitadas.txt
```
- Apps instaladas por el usuario: `adb uninstall <pkg>` (no se restauran con install-existing; anotarlas aparte:
  se reinstalan desde Play Store).
- Apps dudosas que el usuario quiere fuera de la vista: **no quitar**, ocultarlas en el launcher (mantener OK → Ocultar).
- **Siempre `</dev/null` en adb dentro de bucles `while read`**: adb consume el stdin y el bucle se corta.
- Mostrar el espacio libre antes y después (`df -h /data`).

## 4. Launcher Cristian Plaza TV como inicio

Preguntar antes si lo quiere como pantalla de inicio (desactiva el inicio de Google TV; Ajustes, Chromecast,
asistente y apps siguen funcionando).

```bash
URL=$(curl -s https://api.github.com/repos/cristianplazat-cmyk/cristianplaza-tv/releases/latest \
      | grep -o 'https://[^"]*\.apk' | head -1)
curl -L -o CristianPlazaTV.apk "$URL"
adb install -r CristianPlazaTV.apk
adb shell pm grant com.cristianplaza.tv android.permission.WRITE_SECURE_SETTINGS
# Servicio de botones: AÑADIR a los existentes, no reemplazar
S=com.cristianplaza.tv/com.cristianplaza.tv.BotonesControl
A=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
[ -z "$A" ] || [ "$A" = "null" ] && N=$S || N="$A:$S"
adb shell settings put secure enabled_accessibility_services "$N"
adb shell settings put secure accessibility_enabled 1
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith   # si no, su RecoveryActivity roba el Inicio
adb shell cmd package set-home-activity --user 0 com.cristianplaza.tv/.MainActivity
adb shell input keyevent KEYCODE_HOME
```
En Android TV clásico (sin Google TV) el inicio es `com.google.android.tvlauncher`. Si el fabricante tiene su propio
inicio (p. ej. PatchWall de Xiaomi), no quitarlo: ocultarlo en el launcher.
Si hay **otro launcher de terceros con servicio de accesibilidad** (p. ej. AT4K), compite por el botón Inicio:
proponer quitarlo.

Botones del control: el usuario los asigna en el TV (⚙ → Botones del control: presiona el botón y elige la app).
Para conocer el código de un botón: que lo presione y leer `adb logcat -d | grep -E "keyCode|GlobalKey" | tail`.

## 5. Verificar

1. `adb reboot`; esperar `adb shell getprop sys.boot_completed` = 1 y 30 s más.
2. `adb shell dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity"` → debe ser `com.cristianplaza.tv`.
3. `adb shell dumpsys accessibility | grep -c BotonesControl` ≥ 1.
4. **Esperar ~3 minutos y volver a listar paquetes**: algunos fabricantes reinstalan apps al arrancar (en TCL lo hace
   `com.tcl.ttvs`). Si algo volvió, buscar el servicio responsable y proponer quitarlo (con permiso).
5. Captura para el usuario: `adb exec-out screencap -p > captura.png` (las entradas HDMI salen negras: contenido protegido).
6. Pedir al usuario que pruebe **con el control físico** el botón Inicio y los botones reasignados.
7. Resumir: qué se quitó, espacio liberado, dónde quedó `restaurar.sh`.

## 6. Trampas conocidas

- Las teclas enviadas con `adb shell input keyevent` **no pasan** por los servicios de accesibilidad: los botones
  reasignados solo se prueban con el control físico.
- Reinstalar el APK **apaga** el servicio de botones; el launcher lo reactiva solo si tiene `WRITE_SECURE_SETTINGS`.
- En algunos Xiaomi el permiso de leer imágenes se pierde al reiniciar: los fondos propios van en
  `/sdcard/Android/data/com.cristianplaza.tv/files/fondos/`.
- Abrir Miracast/"Screen share" puede dejar el TV sin Wi-Fi normal: no lanzarlo en pruebas.
- `uiautomator dump` puede fallar en TCL; usar `adb shell dumpsys activity top`.
- Actualizaciones OTA en Xiaomi necesitan ~1,5 GB libres además de espacio de copia; no forzarlas.
- `pm uninstall --user 0` no borra el APK de la partición del sistema: un factory reset lo devuelve todo.
