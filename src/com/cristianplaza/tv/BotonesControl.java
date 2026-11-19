package com.cristianplaza.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Servicio opcional que permite reasignar botones del control remoto.
 *
 * Por defecto:  Ajustes -> Ajustes del TV   ·   Entradas -> selector de entradas del launcher.
 * El resto se configura desde el launcher (menú ⚙ → Botones del control): se presiona el botón
 * y se elige la app. Solo filtra las teclas asignadas; no lee nada de la pantalla.
 */
public class BotonesControl extends AccessibilityService {

    static final String AJUSTES = "__ajustes__";
    static final String ENTRADAS = "__entradas__";
    /** El botón Inicio del control abre este launcher (aunque no sea el inicio del sistema). */
    static final String PREF_INICIO = "inicio_propio";

    /** Si no es null, la próxima tecla "especial" se entrega aquí en vez de ejecutarse. */
    interface Oyente { void tecla(int codigo); }
    static volatile Oyente escuchando;

    /** Teclas que nunca se capturan (navegación básica). */
    private static boolean esBasica(int k) {
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_VOLUME_UP: case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE: case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_SEARCH: case KeyEvent.KEYCODE_ASSIST:
            case KeyEvent.KEYCODE_VOICE_ASSIST:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                return true;
            default:
                return false;
        }
    }

    // ---------------------------------------------------------------- asignaciones
    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("botones", MODE_PRIVATE);
    }

    /** Tecla -> destino (paquete de app, AJUSTES o ENTRADAS). */
    static Map<Integer, String> asignaciones(Context c) {
        SharedPreferences p = prefs(c);
        Map<Integer, String> m = new LinkedHashMap<>();
        if (!p.getBoolean("inicializado", false)) {
            p.edit().putBoolean("inicializado", true)
                    .putString("t" + KeyEvent.KEYCODE_SETTINGS, AJUSTES)
                    .putString("t" + KeyEvent.KEYCODE_TV_INPUT, ENTRADAS)
                    .apply();
            m.put(KeyEvent.KEYCODE_SETTINGS, AJUSTES);
            m.put(KeyEvent.KEYCODE_TV_INPUT, ENTRADAS);
            return m;
        }
        for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
            if (e.getKey().startsWith("t") && e.getValue() instanceof String) {
                try { m.put(Integer.parseInt(e.getKey().substring(1)), (String) e.getValue()); }
                catch (NumberFormatException ignored) { }
            }
        }
        return m;
    }

    static void asignar(Context c, int tecla, String destino) {
        asignaciones(c); // asegura valores por defecto
        if (destino == null) prefs(c).edit().remove("t" + tecla).apply();
        else prefs(c).edit().putString("t" + tecla, destino).apply();
    }

    static String nombreTecla(int k) {
        String s = KeyEvent.keyCodeToString(k);
        if (s.startsWith("KEYCODE_")) s = s.substring(8);
        switch (k) {
            case KeyEvent.KEYCODE_SETTINGS: return "Ajustes";
            case KeyEvent.KEYCODE_TV_INPUT: return "Entradas";
            default: return "Botón " + s.replace('_', ' ').toLowerCase();
        }
    }

    // ---------------------------------------------------------------- servicio
    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        // Inicio del control → este launcher (si la opción está activa).
        if (k == KeyEvent.KEYCODE_HOME && escuchando == null
                && prefs(this).getBoolean(PREF_INICIO, true)) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                try {
                    startActivity(new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                } catch (Exception ex) { return false; }
            }
            return true;
        }
        if (esBasica(k)) return false;

        Oyente o = escuchando;
        if (o != null) {
            if (e.getAction() == KeyEvent.ACTION_UP) { escuchando = null; o.tecla(k); }
            return true;
        }

        String destino = asignaciones(this).get(k);
        if (destino == null) return false;
        if (e.getAction() == KeyEvent.ACTION_UP) ejecutar(this, destino);
        return true;
    }

    static void ejecutar(Context c, String destino) {
        try {
            if (AJUSTES.equals(destino)) {
                c.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else if (ENTRADAS.equals(destino)) {
                c.startActivity(new Intent(c, MainActivity.class)
                        .putExtra(MainActivity.EXTRA_ENTRADAS, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else {
                Intent i = c.getPackageManager().getLeanbackLaunchIntentForPackage(destino);
                if (i == null) i = c.getPackageManager().getLaunchIntentForPackage(destino);
                if (i == null) return;
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                c.startActivity(i);
            }
        } catch (Exception ignored) { }
    }

    /** ¿Está activo este servicio? */
    static boolean activo(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(c.getPackageName() + "/" + BotonesControl.class.getName());
    }

    /** Lo enciende si se puede (requiere WRITE_SECURE_SETTINGS, que concede el script de instalación). */
    static void intentarActivar(Context c) {
        if (activo(c)) return;
        try {
            String servicio = c.getPackageName() + "/" + BotonesControl.class.getName();
            String actuales = Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String nuevos = (actuales == null || actuales.isEmpty()) ? servicio : actuales + ":" + servicio;
            Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, nuevos);
            Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (Exception ignored) { }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { }

    @Override
    public void onDestroy() {
        escuchando = null;
        super.onDestroy();
    }
}
