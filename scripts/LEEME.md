# Cristian Plaza TV

Launcher minimalista para **Android TV / Google TV**: tarjetas de vidrio oscuro con el logo de cada app en blanco, fondos tecnológicos que cambian cada 5 minutos, sin anuncios y sin recomendaciones.

## Qué incluye

- **Inicio limpio:** cuadrícula de 4 columnas. Al bajar, la hora y el menú se ocultan.
- **Logos en blanco:** se generan en tu propio TV a partir del logo de cada app que tengas instalada.
- **Fondos:** 18 fondos tecnológicos originales que van rotando. Puedes agregar los tuyos (ver abajo).
- **Entradas HDMI:** detecta tus entradas y muestra el nombre del equipo conectado cuando el TV lo conoce.
- **Organiza tus apps:** mantén presionado **OK** sobre una app para **moverla**, **ocultarla**, **desinstalarla** o **asignarla a un botón del control**.
- **Botones del control:** desde el menú ⚙ puedes hacer que el botón de una app que no usas abra otra. Por ejemplo, el botón de Prime Video puede abrir Max.

## Antes de empezar (una sola vez)

1. **En el TV:** ve a *Ajustes → Sistema → Información* y presiona **7 veces** *Compilación del SO de Android TV*. Con eso se activa el modo desarrollador.
2. Ve a *Ajustes → Sistema → Opciones de desarrollador* y activa **Depuración USB** y **Depuración por red** (o *ADB por red*).
3. Anota la **IP del TV**. Está en *Ajustes → Red e Internet → tu Wi-Fi*.
4. **En tu computador:** descarga [SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools) de Google.
   - En **Windows**, copia `adb.exe` (y los `.dll` que vienen con él) en esta misma carpeta.
   - En **Mac**, también puedes instalarlo con `brew install --cask android-platform-tools`.

El computador y el TV tienen que estar conectados a la **misma red Wi-Fi**.

## Instalar

- **Windows** *(beta, aún sin probar)*: doble clic en `instalar.bat` y escribe la IP del TV.
- **Mac / Linux:** abre una terminal en esta carpeta y ejecuta `./instalar.sh 192.168.1.50` (con la IP de tu TV).

La primera vez, el TV mostrará **"¿Permitir depuración?"**. Marca *Permitir siempre* y acepta.

El instalador te pregunta si quieres que Cristian Plaza TV sea tu **pantalla de inicio**. Si respondes que sí, desactiva el inicio de Google TV. Ajustes, Chromecast, el asistente de voz y todas tus apps siguen funcionando igual.

> **Sin computador:** también puedes instalar solo el APK (con *Downloader* o un pendrive) y abrirlo desde la lista de apps. Funciona igual, pero el botón Inicio del control seguirá abriendo Google TV.

## Opcional: limpiar un TV Xiaomi

`limpiar-xiaomi.bat` / `limpiar-xiaomi.sh` quita el rastreo y la publicidad que Xiaomi y Google traen preinstalados y que no se pueden desinstalar desde el menú. También libera espacio. Antes de quitar nada te muestra la lista y te pide confirmación, y no borra nada del sistema: `deshacer` lo restaura.

## Deshacer todo

`deshacer.bat` / `./deshacer.sh 192.168.1.50` hace lo siguiente:

- reactiva el inicio de Google TV,
- restaura las apps que quitó la limpieza,
- desinstala Cristian Plaza TV.

## Preguntas frecuentes

**¿Cómo agrego mis propios fondos?**
Copia imágenes (JPG o PNG, idealmente de 1920×1080) a `Android/data/com.cristianplaza.tv/files/fondos` del TV. Por ejemplo:
`adb push mi-fondo.jpg /sdcard/Android/data/com.cristianplaza.tv/files/fondos/`

**Una app aparece con su nombre en vez del logo.**
Algunas apps no traen un banner que se pueda convertir a blanco. En ese caso se muestra el nombre, con el mismo estilo.

**Los botones del control dejaron de funcionar.**
Presiona Inicio una vez, porque el launcher reactiva el servicio solo. Si no funciona, actívalo a mano en *Ajustes → Accesibilidad → Botones del control*.

**¿Es seguro?**
Todo lo que hacen los scripts se puede deshacer con `deshacer`. No se modifica el sistema (no hay root ni se desbloquea nada). El servicio de botones solo captura los botones que tú asignas y no lee nada de la pantalla.

---
Úsalo bajo tu propia responsabilidad. Probado en un Xiaomi TV (Android 14) y una TCL (Android 12), ambos con Google TV. En otras marcas, la limpieza opcional y algunos botones del control pueden variar.
