# Listas de apps por marca

**Son propuestas, no órdenes:** mostrarlas al usuario y quitar solo lo que apruebe. Incluir solo los paquetes que
existan en su TV (`adb shell pm list packages | grep <pkg>`).

## Google TV / Android TV (cualquier marca)

| Nivel | Paquete | Qué es |
|---|---|---|
| Básica | `com.google.android.feedback` | Envío de reportes a Google |
| Básica | `com.android.tv.feedbackconsent` | Consentimiento de reportes |
| Profunda | `com.google.android.play.games` | Play Juegos |
| Profunda | `com.google.android.videos` | Google TV / Play Películas (tienda de películas) |
| Profunda | `com.android.printspooler` | Impresión (inútil en un TV) |
| Profunda | `com.google.android.youtube.tvmusic` | YouTube Music |
| Profunda | `com.amazon.amazonvideo.livingroom` | Prime Video (solo si no lo usa) |
| Profunda ⚠️ | `com.google.android.marvin.talkback` | Lector de pantalla: **preguntar** si alguien en casa lo necesita |

Al usar Cristian Plaza TV como inicio: **deshabilitar (no quitar)** `com.google.android.apps.tv.launcherx` y
`com.google.android.tungsten.setupwraith`.

## Xiaomi (Mi TV / Xiaomi TV, MediaTek) — probado

| Nivel | Paquete | Qué es |
|---|---|---|
| Básica | `com.miui.tv.analytics` | Telemetría de Xiaomi |
| Básica | `com.xiaomi.statistic` | Estadísticas de uso |
| Básica | `com.mitv.tvhome.mitvplus` | Mi TV+ (canales con publicidad) |
| Básica | `com.mitv.tvhome.michannel` | Canales promocionados |
| Básica | `com.mitv.tvhome.oemtab` | Pestaña promocional del inicio |
| Básica | `com.xm.webcontent` | Contenido web promocional |
| Básica | `com.xiaomi.mitv.scenemode` | Modos de escena / promoción |
| Profunda | `com.xiaomi.mimusic2` | Reproductor de música |
| Profunda | `com.mitv.gallery` | Galería |

**Ocultar en el launcher, no quitar:** `com.mitv.tvhome.atv` (PatchWall), `com.xiaomi.mitv.tvmanager` (Optimizar TV),
`com.xiaomi.mitv.mediaexplorer` (Media Player), `com.xiaomi.mitv.smartshare` (Miracast), `com.mediatek.tv.systemupdater`,
`com.mediatek.calman`, `mediatek.factorymenu.ui`.
**No tocar:** `mitv.service` (botones del control), `com.mitv.livetv`, `com.xiaomi.tvqs.*` (ajustes rápidos),
`com.xiaomi.mitv.updateservice`.
Códigos del control Xiaomi: Netflix 193 (lo maneja el sistema), YouTube 190, ⭐ 313, Prime Video 194, Xiaomi TV 352, Live TV 351.

## TCL (Google TV, Amlogic) — probado

| Nivel | Paquete | Qué es |
|---|---|---|
| Básica | `com.tcl.logkit` | Registro y envío de logs |
| Básica | `com.tcl.messagebox` | Mensajes y anuncios de TCL |
| Básica | `com.tcl.usercenter` | Cuenta TCL / recolección de datos |
| Básica | `com.tcl.partnercustomizer` | Contenido de socios |
| Básica | `com.tcl.exhibit` | Modo tienda / demo |
| Básica | `com.tcl.esticker` | Etiqueta de tienda en pantalla |
| Básica | `com.tcl.guard` | "Guardia de seguridad" (limpiador) |
| Básica | **`com.tcl.ttvs`** | Servicio que **reinstala** las apps de TCL al arrancar: quitarlo junto con las demás o vuelven |
| Profunda | `com.tcl.waterfall.overseas` | TCL Home (recomendaciones) |
| Profunda | `com.tcl.hotelmenu` | Menú hotel |
| Profunda | `com.tcl.ocean.instructions`, `com.tcl.repairguide` | Manual y guía de reparación |
| Profunda | `com.tcl.channelplus` | TCL Channel |
| Profunda | `com.tcl.smartalexa` | Alexa |
| Profunda | `com.tcl.magiconnectfree` | MagiConnect |
| Profunda | `com.tcl.miracast`, `com.tcl.ui_mediaCenter` | Miracast, Media Center |
| Profunda (usuario) | `com.tcl.browser`, `com.android.mgstv`, `com.overdevs.at4k` | Navegador, Mango TV, AT4K Launcher |

**No tocar:** `com.tcl.tv`, `com.tcl.tvinput`, `com.tcl.dashboard`, `com.tcl.gamebar`, `com.tcl.systemserver`,
`com.tcl.globalkeyoverlay`, `com.amlogic.airplay`, `com.tcl.airplay2`, `com.tcl.hearaid`, `com.tcl.useragreement`.

## Método para marcas nuevas (Sony, Philips, Hisense, Onn, Chromecast, etc.)

Sin probar: proceder con más cuidado y proponer menos.
1. Listar `pm list packages -s` y filtrar por el prefijo del fabricante (`getprop ro.product.manufacturer` ayuda:
   Sony `com.sony.dtv.*`, Philips/TP Vision `org.droidtv.*` / `com.tpvision.*`, Hisense `com.hisense.*`, etc.).
2. Para cada candidato, ver el nombre visible y qué hace:
   `adb shell dumpsys package <pkg> | grep -E "versionName|android.intent.action.MAIN"` y, si tiene ícono, abrirlo
   solo con permiso del usuario.
3. Clasificar:
   - **Básica:** nombres con `analytics`, `statistic`, `telemetry`, `tracking`, `logkit`, `log`, `feedback`, `report`,
     `ad`, `ads`, `promo`, `recommend`, `message`, `notice`, `exhibit`, `demo`, `retail`, `store_mode`, `shop`.
   - **Profunda:** tiendas, canales y portales propios, asistentes de terceros, juegos, manuales, navegador,
     reproductores multimedia, apps de socios.
   - **No tocar:** todo lo de la regla 2 de SKILL.md, más cualquier cosa que el usuario o el sistema usen para
     imagen, sonido, entradas, control remoto, red, actualizaciones o configuración. En la duda, no se toca.
4. Quitar de a pocos, revisar que el TV siga funcionando (inicio, entradas, sonido, control) y repetir el chequeo
   de los 3 minutos tras reiniciar.
5. Si el resultado es bueno, sugerir al usuario aportar la lista al repositorio (issue o pull request).
