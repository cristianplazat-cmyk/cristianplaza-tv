package com.cristianplaza.tv;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cristian Plaza TV — launcher minimalista para Android TV / Google TV.
 *
 * - Fondos tecnológicos que rotan cada 5 minutos (se pueden agregar los propios).
 * - Cuadrícula de 4 columnas con tarjetas de vidrio y el logo de cada app en blanco,
 *   generado en el propio TV a partir del banner de la app.
 * - Mantener presionado OK sobre una app: mover u ocultar.
 * - Menú ⚙: ajustes del TV, botones del control, firma, apps ocultas.
 * - "Entradas": selector de HDMI detectado automáticamente.
 */
public class MainActivity extends Activity {

    // ---------------------------------------------------------------- constantes
    private static final long INTERVALO_FONDO_MS = 5 * 60 * 1000L;
    private static final long FUNDIDO_MS = 1800L;
    private static final int COLUMNAS = 4;
    static final String EXTRA_ENTRADAS = "abrir_entradas";
    private static final String ENTRADAS = BotonesControl.ENTRADAS;

    /** Orden inicial sugerido (las que existan). El resto va después, alfabético. */
    private static final List<String> ORDEN_INICIAL = Arrays.asList(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.disney.disneyplus",
            "com.wbd.stream",
            "com.amazon.amazonvideo.livingroom",
            "com.apple.atve.androidtv.appletv",
            "com.spotify.tv.android",
            ENTRADAS,
            "com.android.vending");

    /** Nunca se muestran: este launcher, otros launchers, ajustes y menús de fábrica. */
    private static final Set<String> SIEMPRE_OCULTAS = new HashSet<>(Arrays.asList(
            "com.cristianplaza.tv",
            "com.android.tv.settings",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.spocky.projengmenu",
            "com.mitv.tvhome.atv",
            "com.mediatek.calman",
            "mediatek.factorymenu.ui",
            "com.mediatek.tv.systemupdater"));

    // ---------------------------------------------------------------- estado
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private float dp;

    private FrameLayout raiz;
    private ImageView fondoA, fondoB;
    private boolean mostrandoA = true;
    private final List<File> fondos = new ArrayList<>();
    private int indiceFondo = -1;
    private long ultimoCambio = 0;
    private int reintentos = 0;

    private ScrollView scroll;
    private LinearLayout grilla;
    private View botonMenu;
    private FrameLayout cabecera;
    private ImageView firma;
    private TextView aviso;
    private FrameLayout overlay; // menú / selector abierto, o null

    private final List<App> apps = new ArrayList<>();
    private final Map<String, FrameLayout> tarjetas = new HashMap<>();
    private final Map<String, Bitmap> cacheLogos = new HashMap<>();
    private final Set<String> generando = new HashSet<>();
    private App moviendo;
    private boolean pulsacionLarga = false;
    private long inicioPulsacion = -1;

    private App buscarApp(String paquete) {
        for (App a : apps) if (a.paquete.equals(paquete)) return a;
        return null;
    }
    private int w, h; // tamaño de tarjeta

    private final Runnable rotar = new Runnable() {
        @Override public void run() {
            siguienteFondo();
            ui.postDelayed(this, INTERVALO_FONDO_MS);
        }
    };

    private final Runnable reintentarFondos = new Runnable() {
        @Override public void run() {
            cargarListaFondos();
            if (!fondos.isEmpty()) siguienteFondo();
            else if (++reintentos < 45) ui.postDelayed(this, 4000);
        }
    };

    private final BroadcastReceiver cambiosApps = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { cargarApps(null); }
    };

    // ---------------------------------------------------------------- ciclo de vida
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("inicio", MODE_PRIVATE);
        int ancho = getResources().getDisplayMetrics().widthPixels;
        w = (ancho - 2 * px(64) - (COLUMNAS - 1) * px(30)) / COLUMNAS;
        h = Math.round(w * 9f / 16f);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.rgb(8, 10, 16)));
        setContentView(construirUI());

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(cambiosApps, f);

        copiarFondosIncluidos();
        cargarApps(null);
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_ENTRADAS, false)) {
            grilla.post(this::abrirEntradas);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        BotonesControl.intentarActivar(this);
        cargarListaFondos();
        long transcurrido = SystemClock.elapsedRealtime() - ultimoCambio;
        if (indiceFondo < 0 || transcurrido >= INTERVALO_FONDO_MS) {
            siguienteFondo();
            transcurrido = 0;
        }
        ui.removeCallbacks(rotar);
        ui.postDelayed(rotar, INTERVALO_FONDO_MS - transcurrido);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(rotar);
        if (moviendo != null) terminarMover();
        BotonesControl.escuchando = null;
        cerrarOverlay();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(cambiosApps);
        io.shutdownNow();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (moviendo != null) terminarMover();
        if (intent.getBooleanExtra(EXTRA_ENTRADAS, false)) {
            grilla.post(this::abrirEntradas);
            return;
        }
        // Inicio estando ya en el launcher: volver arriba, a la primera app.
        cerrarOverlay();
        scroll.smoothScrollTo(0, 0);
        View primera = apps.isEmpty() ? null : tarjetas.get(apps.get(0).paquete);
        if (primera != null) primera.requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (overlay != null) { BotonesControl.escuchando = null; cerrarOverlay(); return; }
        if (moviendo != null) { terminarMover(); return; }
        // Pantalla de inicio: "Atrás" no hace nada más.
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (moviendo != null) {
            int k = e.getKeyCode();
            boolean flecha = k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
                    || k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN;
            boolean fin = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                    || k == KeyEvent.KEYCODE_BACK;
            if (flecha || fin) {
                if (e.getAction() == KeyEvent.ACTION_DOWN) {
                    if (flecha) mover(k);
                    else if (e.getRepeatCount() == 0) terminarMover();
                }
                return true;
            }
        }
        // Mantener presionado OK sobre una app: solo cuenta si el botón sigue apretado
        // (repeticiones de la tecla), así una tarjeta que se actualiza no dispara el menú sola.
        boolean ok = e.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || e.getKeyCode() == KeyEvent.KEYCODE_ENTER;
        if (ok && overlay == null && moviendo == null) {
            View foco = getCurrentFocus();
            App app = foco != null && foco.getTag() instanceof String ? buscarApp((String) foco.getTag()) : null;
            if (app != null) {
                if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                    pulsacionLarga = false;
                    inicioPulsacion = e.getDownTime();
                    return true;
                }
                if (e.getAction() == KeyEvent.ACTION_DOWN) {
                    if (!pulsacionLarga && (e.getEventTime() - inicioPulsacion >= 600
                            || (e.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0)) {
                        pulsacionLarga = true;
                        menuApp(app);
                    }
                    return true;
                }
                if (e.getAction() == KeyEvent.ACTION_UP) {
                    boolean corta = !pulsacionLarga && inicioPulsacion == e.getDownTime();
                    pulsacionLarga = false;
                    if (corta) foco.performClick();
                    return true;
                }
            }
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            abrirMenu();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ---------------------------------------------------------------- UI base
    private int px(float v) { return Math.round(v * dp); }

    private View construirUI() {
        raiz = new FrameLayout(this);
        raiz.setClipChildren(false);

        fondoA = nuevoFondo();
        fondoB = nuevoFondo();
        fondoB.setAlpha(0f);
        raiz.addView(fondoA, lleno());
        raiz.addView(fondoB, lleno());

        View velo = new View(this);
        velo.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xE0000000, 0x8C000000, 0x00000000}));
        raiz.addView(velo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (getResources().getDisplayMetrics().heightPixels * 0.62f), Gravity.BOTTOM));

        View veloTop = new View(this);
        veloTop.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99000000, 0x00000000}));
        raiz.addView(veloTop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(140), Gravity.TOP));

        int altoPantalla = getResources().getDisplayMetrics().heightPixels;
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setSmoothScrollingEnabled(true);
        scroll.setFillViewport(true);
        grilla = new LinearLayout(this);
        grilla.setOrientation(LinearLayout.VERTICAL);
        grilla.setClipChildren(false);
        grilla.setClipToPadding(false);
        grilla.setPadding(0, (int) (altoPantalla * 0.60f), 0, (int) (altoPantalla * 0.30f));
        scroll.addView(grilla, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        raiz.addView(scroll, lleno());

        cabecera = new FrameLayout(this);
        cabecera.setClipChildren(false);
        cabecera.setClipToPadding(false);
        raiz.addView(cabecera, lleno());

        firma = new ImageView(this);
        firma.setImageResource(R.drawable.firma);
        firma.setScaleType(ImageView.ScaleType.FIT_START);
        firma.setVisibility(prefs.getBoolean("firma", true) ? View.VISIBLE : View.GONE);
        FrameLayout.LayoutParams lpFirma = new FrameLayout.LayoutParams(px(92), px(92),
                Gravity.TOP | Gravity.START);
        lpFirma.setMargins(px(64), px(24), 0, 0);
        cabecera.addView(firma, lpFirma);

        LinearLayout barra = new LinearLayout(this);
        barra.setOrientation(LinearLayout.HORIZONTAL);
        barra.setClipChildren(false);
        barra.setClipToPadding(false);
        barra.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout reloj = new LinearLayout(this);
        reloj.setOrientation(LinearLayout.VERTICAL);
        reloj.setGravity(Gravity.END);
        TextClock hora = new TextClock(this);
        hora.setFormat24Hour("HH:mm");
        hora.setFormat12Hour("h:mm");
        hora.setTextColor(Color.WHITE);
        hora.setTextSize(30);
        hora.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        sombra(hora);
        TextClock fecha = new TextClock(this);
        fecha.setFormat24Hour("EEEE d 'de' MMMM");
        fecha.setFormat12Hour("EEEE d 'de' MMMM");
        fecha.setTextColor(0xCCFFFFFF);
        fecha.setTextSize(13);
        sombra(fecha);
        reloj.addView(hora);
        reloj.addView(fecha);
        barra.addView(reloj);

        botonMenu = crearBotonMenu();
        LinearLayout.LayoutParams lpBoton = new LinearLayout.LayoutParams(px(46), px(46));
        lpBoton.setMargins(px(22), 0, 0, 0);
        barra.addView(botonMenu, lpBoton);

        FrameLayout.LayoutParams lpBarra = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        lpBarra.setMargins(0, px(26), px(64), 0);
        cabecera.addView(barra, lpBarra);

        aviso = new TextView(this);
        aviso.setTextColor(Color.WHITE);
        aviso.setTextSize(14);
        aviso.setPadding(px(18), px(8), px(18), px(8));
        GradientDrawable fa = new GradientDrawable();
        fa.setCornerRadius(px(20));
        fa.setColor(0x99000000);
        fa.setStroke(Math.max(1, px(1)), 0x40FFFFFF);
        aviso.setBackground(fa);
        aviso.setVisibility(View.GONE);
        FrameLayout.LayoutParams lpAviso = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lpAviso.topMargin = px(40);
        raiz.addView(aviso, lpAviso);
        return raiz;
    }

    private ImageView nuevoFondo() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return v;
    }

    private FrameLayout.LayoutParams lleno() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void sombra(TextView t) {
        t.setShadowLayer(px(6), 0, px(1), 0x99000000);
    }

    private void mostrarAviso(String texto) {
        aviso.setText(texto);
        aviso.setVisibility(texto == null ? View.GONE : View.VISIBLE);
        aviso.bringToFront();
    }

    // ---------------------------------------------------------------- vidrio
    /** Estilo "liquid glass": vidrio oscuro translúcido, reflejo, canto de luz y brillo al enfocar. */
    private View.OnFocusChangeListener vidrio(final FrameLayout t, final float radio, final float escalaFoco) {
        final int[] normal = {0x66323642, 0x8C0B0D13};
        final int[] foco = {0x8C474C5C, 0xB3141720};
        final GradientDrawable fondo = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, normal);
        fondo.setCornerRadius(radio);
        fondo.setStroke(Math.max(1, px(1)), 0x33FFFFFF);
        t.setBackground(fondo);
        t.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radio);
            }
        });
        t.setClipToOutline(true);

        final View brillo = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(radio * 4.5f);
        g.setGradientCenter(0.5f, 0.55f);
        g.setColors(new int[]{0x3DFFFFFF, 0x12FFFFFF, 0x00FFFFFF});
        brillo.setBackground(g);
        brillo.setAlpha(0.6f);
        t.addView(brillo, lleno());

        View reflejo = new View(this);
        reflejo.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x26FFFFFF, 0x08FFFFFF, 0x00FFFFFF}));
        t.addView(reflejo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (radio * 2.6f), Gravity.TOP));

        View canto = new View(this);
        canto.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00FFFFFF, 0x80FFFFFF, 0x00FFFFFF}));
        FrameLayout.LayoutParams lpCanto = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(1)), Gravity.TOP);
        lpCanto.setMargins((int) radio, px(1), (int) radio, 0);
        t.addView(canto, lpCanto);

        return (v, tiene) -> {
            fondo.setStroke(Math.max(1, px(tiene ? 2 : 1)), tiene ? 0xB3FFFFFF : 0x33FFFFFF);
            fondo.setColors(tiene ? foco : normal);
            brillo.animate().alpha(tiene ? 1f : 0.6f).setDuration(160).start();
            v.animate().scaleX(tiene ? escalaFoco : 1f).scaleY(tiene ? escalaFoco : 1f)
                    .translationZ(tiene ? px(14) : 0).setDuration(160)
                    .setInterpolator(new DecelerateInterpolator()).start();
        };
    }

    private View crearBotonMenu() {
        FrameLayout b = new FrameLayout(this);
        b.setFocusable(true);
        b.setClickable(true);
        b.setContentDescription("Menú");
        b.setOnFocusChangeListener(vidrio(b, px(23), 1.15f));
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(engranaje());
        b.addView(ic, new FrameLayout.LayoutParams(px(24), px(24), Gravity.CENTER));
        b.setOnClickListener(v -> abrirMenu());
        return b;
    }

    private Drawable engranaje() {
        int s = px(48);
        Bitmap bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        float cx = s / 2f, r = s * 0.30f;
        for (int i = 0; i < 8; i++) {
            c.save();
            c.rotate(i * 45f, cx, cx);
            c.drawRoundRect(cx - s * 0.075f, cx - s * 0.46f, cx + s * 0.075f, cx - r + s * 0.05f,
                    s * 0.03f, s * 0.03f, p);
            c.restore();
        }
        c.drawCircle(cx, cx, r, p);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        c.drawCircle(cx, cx, s * 0.13f, p);
        return new BitmapDrawable(getResources(), bmp);
    }

    /** Ícono de puerto HDMI blanco (para "Entradas"). */
    private Bitmap iconoHdmi(int alto) {
        int s = alto, wi = Math.round(alto * 1.9f);
        Bitmap bmp = Bitmap.createBitmap(wi, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        android.graphics.Path forma = new android.graphics.Path();
        float ch = s * 0.25f;
        forma.moveTo(0, 0); forma.lineTo(wi, 0); forma.lineTo(wi, s - ch);
        forma.lineTo(wi - ch, s); forma.lineTo(ch, s); forma.lineTo(0, s - ch); forma.close();
        c.drawPath(forma, p);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        float m = s * 0.16f;
        android.graphics.Path hueco = new android.graphics.Path();
        hueco.moveTo(m, m); hueco.lineTo(wi - m, m); hueco.lineTo(wi - m, s - ch - m * 0.3f);
        hueco.lineTo(wi - ch - m * 0.4f, s - m); hueco.lineTo(ch + m * 0.4f, s - m);
        hueco.lineTo(m, s - ch - m * 0.3f); hueco.close();
        c.drawPath(hueco, p);
        p.setXfermode(null);
        c.drawRect(wi * 0.22f, s * 0.36f, wi * 0.78f, s * 0.50f, p);
        return bmp;
    }

    // ---------------------------------------------------------------- apps
    private static class App {
        String paquete, nombre;
        ComponentName componente;
        Drawable banner, icono;
    }

    private Set<String> ocultasPorUsuario() {
        return new HashSet<>(prefs.getStringSet("ocultas", new HashSet<>()));
    }

    private List<App> todasLasApps() {
        PackageManager pm = getPackageManager();
        Map<String, App> porPaquete = new LinkedHashMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                ActivityInfo ai = ri.activityInfo;
                if (SIEMPRE_OCULTAS.contains(ai.packageName) || porPaquete.containsKey(ai.packageName)) continue;
                App a = new App();
                a.paquete = ai.packageName;
                a.componente = new ComponentName(ai.packageName, ai.name);
                a.nombre = String.valueOf(ri.loadLabel(pm));
                a.banner = ai.loadBanner(pm);
                a.icono = ai.loadIcon(pm);
                porPaquete.put(a.paquete, a);
            }
        }
        List<App> l = new ArrayList<>(porPaquete.values());
        Collections.sort(l, (x, y) -> x.nombre.compareToIgnoreCase(y.nombre));
        return l;
    }

    private void cargarApps(String enfocar) {
        Map<String, App> porPaquete = new LinkedHashMap<>();
        Set<String> ocultas = ocultasPorUsuario();
        for (App a : todasLasApps()) if (!ocultas.contains(a.paquete)) porPaquete.put(a.paquete, a);
        if (!ocultas.contains(ENTRADAS) && !entradasHdmi().isEmpty()) {
            App e = new App();
            e.paquete = ENTRADAS;
            e.nombre = "Entradas";
            porPaquete.put(ENTRADAS, e);
        }

        String guardado = prefs.getString("orden", null);
        List<String> orden = guardado != null ? Arrays.asList(guardado.split(",")) : ORDEN_INICIAL;
        apps.clear();
        for (String p : orden) {
            App a = porPaquete.remove(p);
            if (a != null) apps.add(a);
        }
        apps.addAll(porPaquete.values()); // nuevas: al final, alfabético

        if (enfocar == null) {
            View actual = getCurrentFocus();
            if (actual != null && actual.getTag() instanceof String) enfocar = (String) actual.getTag();
        }
        dibujarGrilla(enfocar);
    }

    private void guardarOrden() {
        StringBuilder sb = new StringBuilder();
        for (App a : apps) { if (sb.length() > 0) sb.append(','); sb.append(a.paquete); }
        prefs.edit().putString("orden", sb.toString()).apply();
    }

    private void dibujarGrilla(String enfocar) {
        grilla.removeAllViews();
        tarjetas.clear();
        int margen = px(64), separacion = px(30);
        LinearLayout fila = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % COLUMNAS == 0) {
                fila = new LinearLayout(this);
                fila.setOrientation(LinearLayout.HORIZONTAL);
                fila.setClipChildren(false);
                fila.setClipToPadding(false);
                fila.setPadding(margen, 0, margen, 0);
                LinearLayout.LayoutParams lpFila = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lpFila.bottomMargin = px(34);
                grilla.addView(fila, lpFila);
            }
            FrameLayout t = crearTarjeta(apps.get(i));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
            if (i % COLUMNAS != COLUMNAS - 1) lp.rightMargin = separacion;
            fila.addView(t, lp);
            tarjetas.put(apps.get(i).paquete, t);
        }

        if (!apps.isEmpty()) {
            if (botonMenu.getId() == View.NO_ID) botonMenu.setId(View.generateViewId());
            for (int i = 0; i < Math.min(COLUMNAS, apps.size()); i++) {
                tarjetas.get(apps.get(i).paquete).setNextFocusUpId(botonMenu.getId());
            }
            View ultima = tarjetas.get(apps.get(Math.min(COLUMNAS, apps.size()) - 1).paquete);
            if (ultima.getId() == View.NO_ID) ultima.setId(View.generateViewId());
            botonMenu.setNextFocusDownId(ultima.getId());
        }

        View f = enfocar != null ? tarjetas.get(enfocar) : null;
        if (f == null && !apps.isEmpty()) f = tarjetas.get(apps.get(0).paquete);
        if (f != null && overlay == null) { final View ff = f; ff.post(ff::requestFocus); }
    }

    private FrameLayout crearTarjeta(final App a) {
        final FrameLayout t = new FrameLayout(this);
        t.setFocusable(true);
        t.setClickable(true);
        t.setLongClickable(false); // la pulsación larga se detecta en dispatchKeyEvent
        t.setTag(a.paquete);
        t.setContentDescription(a.nombre);
        final View.OnFocusChangeListener efecto = vidrio(t, px(20), 1.10f);
        t.setOnFocusChangeListener((v, tiene) -> {
            efecto.onFocusChange(v, tiene);
            if (tiene) asegurarVisible(v);
        });
        ponerContenido(t, a);
        t.setOnClickListener(v -> {
            if (ENTRADAS.equals(a.paquete)) abrirEntradas();
            else abrir(a.componente);
        });
        return t;
    }

    /** Pone el logo blanco (o el nombre mientras se genera / si no se puede). */
    private void ponerContenido(FrameLayout t, App a) {
        View previo = t.findViewWithTag("contenido");
        if (previo != null) t.removeView(previo);

        Bitmap logo = ENTRADAS.equals(a.paquete) ? logoEntradas() : logoDe(a);
        View v;
        if (logo != null) {
            ImageView img = new ImageView(this);
            img.setImageBitmap(logo);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams lp = lleno();
            lp.setMargins(px(30), px(24), px(30), px(24));
            img.setLayoutParams(lp);
            v = img;
        } else {
            TextView tx = new TextView(this);
            tx.setText(a.nombre);
            tx.setTextColor(Color.WHITE);
            tx.setTextSize(19);
            tx.setGravity(Gravity.CENTER);
            tx.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            tx.setSingleLine(true);
            tx.setEllipsize(TextUtils.TruncateAt.END);
            tx.setPadding(px(12), 0, px(12), 0);
            tx.setShadowLayer(px(8), 0, 0, 0x66FFFFFF);
            tx.setLayoutParams(lleno());
            v = tx;
        }
        v.setTag("contenido");
        t.addView(v);
    }

    private Bitmap logoEntradas() {
        if (cacheLogos.containsKey(ENTRADAS)) return cacheLogos.get(ENTRADAS);
        int alto = px(26);
        Bitmap hdmi = iconoHdmi(alto);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextSize(alto * 1.05f);
        p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        p.setLetterSpacing(0.04f);
        String txt = "ENTRADAS";
        int ancho = hdmi.getWidth() + px(14) + Math.round(p.measureText(txt));
        Bitmap b = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawBitmap(hdmi, 0, 0, null);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(txt, hdmi.getWidth() + px(14), alto / 2f - (fm.ascent + fm.descent) / 2f, p);
        cacheLogos.put(ENTRADAS, b);
        return b;
    }

    /**
     * Logo blanco de una app. Orden: caché en memoria → caché en disco →
     * generar desde el banner (en segundo plano) → ícono monocromo → null (nombre).
     */
    private Bitmap logoDe(final App a) {
        if (cacheLogos.containsKey(a.paquete)) return cacheLogos.get(a.paquete);
        final File archivo = archivoLogo(a.paquete);
        if (archivo != null && archivo.exists()) {
            Bitmap b = archivo.length() > 0 ? BitmapFactory.decodeFile(archivo.getAbsolutePath()) : null;
            if (b == null) b = monocromo(a);
            cacheLogos.put(a.paquete, b);
            return b;
        }
        if (a.banner != null && generando.add(a.paquete)) {
            final Drawable banner = a.banner;
            io.execute(() -> {
                Bitmap b = null;
                try { b = LogoBlanco.desdeBanner(banner); } catch (Throwable ignored) { }
                guardarLogo(archivo, b);
                final Bitmap fin = b;
                ui.post(() -> {
                    generando.remove(a.paquete);
                    cacheLogos.put(a.paquete, fin != null ? fin : monocromo(a));
                    FrameLayout t = tarjetas.get(a.paquete);
                    if (t != null) ponerContenido(t, a);
                });
            });
            return null; // mientras tanto, el nombre
        }
        Bitmap b = a.banner == null ? monocromo(a) : null;
        if (a.banner == null) cacheLogos.put(a.paquete, b);
        return b;
    }

    private File archivoLogo(String paquete) {
        File dir = new File(getFilesDir(), "logos");
        if (!dir.exists() && !dir.mkdirs()) return null;
        long version = 0;
        try {
            version = getPackageManager().getPackageInfo(paquete, 0).getLongVersionCode();
        } catch (Exception ignored) { }
        return new File(dir, paquete + "_" + version + ".png");
    }

    private static void guardarLogo(File archivo, Bitmap b) {
        if (archivo == null) return;
        try (FileOutputStream o = new FileOutputStream(archivo)) {
            if (b != null) b.compress(Bitmap.CompressFormat.PNG, 100, o); // vacío = "sin logo"
        } catch (Exception ignored) { }
    }

    private Bitmap monocromo(App a) {
        if (Build.VERSION.SDK_INT < 33 || !(a.icono instanceof AdaptiveIconDrawable)) return null;
        Drawable m = ((AdaptiveIconDrawable) a.icono).getMonochrome();
        if (m == null) return null;
        Bitmap b = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888);
        Drawable cp = m.getConstantState() != null ? m.getConstantState().newDrawable().mutate() : m.mutate();
        cp.setBounds(-54, -54, 270, 270);
        cp.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        cp.draw(new Canvas(b));
        return b;
    }

    private void asegurarVisible(View v) {
        View fila = (View) v.getParent();
        if (fila == null) return;
        int indiceFila = grilla.indexOfChild(fila);
        mostrarCabecera(indiceFila <= 0);
        if (indiceFila <= 0) { scroll.smoothScrollTo(0, 0); return; }
        int objetivo = fila.getTop() - (scroll.getHeight() - fila.getHeight()) / 2;
        scroll.smoothScrollTo(0, Math.max(0, objetivo));
    }

    private void mostrarCabecera(boolean si) {
        float a = si ? 1f : 0f;
        if (cabecera.getAlpha() == a) return;
        cabecera.animate().alpha(a).setDuration(220).start();
        botonMenu.setFocusable(si);
    }

    // ---------------------------------------------------------------- mover
    private void empezarMover(App a) {
        moviendo = a;
        mostrarAviso("Usa las flechas para mover la app · OK para terminar");
        marcarMoviendo(true);
    }

    private void marcarMoviendo(boolean si) {
        View t = moviendo == null ? null : tarjetas.get(moviendo.paquete);
        if (t == null) return;
        t.animate().cancel();
        if (si) {
            t.setScaleX(1.16f);
            t.setScaleY(1.16f);
            t.setAlpha(0.85f);
            t.setTranslationZ(px(24));
        } else {
            t.setAlpha(1f);
            t.animate().scaleX(1.10f).scaleY(1.10f).setDuration(160).start();
        }
    }

    private void mover(int tecla) {
        int i = apps.indexOf(moviendo);
        int j = i;
        if (tecla == KeyEvent.KEYCODE_DPAD_LEFT) j = i - 1;
        else if (tecla == KeyEvent.KEYCODE_DPAD_RIGHT) j = i + 1;
        else if (tecla == KeyEvent.KEYCODE_DPAD_UP) j = i - COLUMNAS;
        else if (tecla == KeyEvent.KEYCODE_DPAD_DOWN) j = i + COLUMNAS;
        if (j < 0 || j >= apps.size() || j == i) return;
        apps.remove(i);
        apps.add(j, moviendo);
        dibujarGrilla(moviendo.paquete);
        View t = tarjetas.get(moviendo.paquete);
        if (t != null) t.post(() -> { t.requestFocus(); marcarMoviendo(true); });
    }

    private void terminarMover() {
        marcarMoviendo(false);
        moviendo = null;
        mostrarAviso(null);
        guardarOrden();
    }

    // ---------------------------------------------------------------- overlays (menús)
    /** Muestra un contenido a pantalla completa ocultando las apps y la cabecera. */
    private void abrirOverlay(View contenido, View primerFoco) {
        if (overlay != null) raiz.removeView(overlay);
        overlay = new FrameLayout(this);
        overlay.setClipChildren(false);
        overlay.setClickable(true);
        overlay.addView(contenido, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        botonMenu.setFocusable(false);
        scroll.animate().alpha(0f).setDuration(200).start();
        cabecera.animate().alpha(0f).setDuration(200).start();
        raiz.addView(overlay, lleno());
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(220).start();
        if (primerFoco != null) primerFoco.post(primerFoco::requestFocus);
    }

    private void cerrarOverlay() {
        if (overlay == null) return;
        raiz.removeView(overlay);
        overlay = null;
        mostrarAviso(null);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        scroll.animate().alpha(1f).setDuration(200).start();
        cabecera.setAlpha(0f);
        botonMenu.setFocusable(true);
        View actual = apps.isEmpty() ? null : tarjetas.get(apps.get(0).paquete);
        if (actual != null) {
            actual.requestFocus();
            asegurarVisible(actual);
        } else {
            mostrarCabecera(true);
        }
    }

    private TextView titulo(String texto) {
        TextView t = new TextView(this);
        t.setText(texto);
        t.setTextColor(Color.WHITE);
        t.setTextSize(26);
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        sombra(t);
        return t;
    }

    /** Una opción de menú: fila de vidrio con título y subtítulo opcional. */
    private FrameLayout fila(String texto, String detalle, Runnable accion) {
        FrameLayout f = new FrameLayout(this);
        f.setFocusable(true);
        f.setClickable(true);
        f.setOnFocusChangeListener(vidrio(f, px(14), 1.04f));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.setPadding(px(22), 0, px(22), 0);
        TextView t = new TextView(this);
        t.setText(texto);
        t.setTextColor(Color.WHITE);
        t.setTextSize(16);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        col.addView(t);
        if (detalle != null) {
            TextView d = new TextView(this);
            d.setText(detalle);
            d.setTextColor(0x99FFFFFF);
            d.setTextSize(12);
            col.addView(d);
        }
        f.addView(col, lleno());
        f.setOnClickListener(v -> accion.run());
        return f;
    }

    /** Menú vertical (con scroll si es largo). */
    private void abrirLista(String tituloTexto, String nota, List<FrameLayout> filas) {
        LinearLayout caja = new LinearLayout(this);
        caja.setOrientation(LinearLayout.VERTICAL);
        caja.setGravity(Gravity.CENTER_HORIZONTAL);
        caja.setClipChildren(false);
        LinearLayout.LayoutParams lpT = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpT.bottomMargin = px(nota == null ? 24 : 6);
        caja.addView(titulo(tituloTexto), lpT);
        if (nota != null) {
            TextView n = new TextView(this);
            n.setText(nota);
            n.setTextColor(0xB3FFFFFF);
            n.setTextSize(13);
            n.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lpN = new LinearLayout.LayoutParams(px(520),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lpN.bottomMargin = px(20);
            caja.addView(n, lpN);
        }
        // El scroll recorta su contenido (si no, las filas se dibujan sobre el título al desplazarse);
        // el margen interno deja espacio para el leve agrandamiento de la fila enfocada.
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(px(24));
        // Las filas de vidrio tienen su propio recorte; esto obliga al scroll a recortarlas.
        sv.setOutlineProvider(ViewOutlineProvider.BOUNDS);
        sv.setClipToOutline(true);
        LinearLayout lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        lista.setClipChildren(false);
        lista.setClipToPadding(false); // la fila enfocada crece un poco: que no se corte
        lista.setPadding(px(20), px(10), px(20), px(10));
        for (FrameLayout f : filas) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(480), px(58));
            lp.bottomMargin = px(12);
            lista.addView(f, lp);
        }
        sv.addView(lista);
        int maxAlto = (int) (getResources().getDisplayMetrics().heightPixels * 0.62f);
        caja.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                Math.min(maxAlto, filas.size() * px(70) + px(20))));
        abrirOverlay(caja, filas.isEmpty() ? null : filas.get(0));
    }

    // ---------------------------------------------------------------- menú ⚙
    private void abrirMenu() {
        List<FrameLayout> f = new ArrayList<>();
        f.add(fila("Ajustes del TV", null, () -> { cerrarOverlay(); BotonesControl.ejecutar(this, BotonesControl.AJUSTES); }));
        f.add(fila("Botones del control", "Asigna apps a los botones del control remoto", this::menuBotones));
        int nOcultas = ocultasPorUsuario().size();
        f.add(fila("Apps ocultas", nOcultas == 0 ? "No hay apps ocultas" : nOcultas + " oculta(s) · elige cuál mostrar",
                this::menuOcultas));
        boolean conFirma = prefs.getBoolean("firma", true);
        boolean inicio = BotonesControl.prefs(this).getBoolean(BotonesControl.PREF_INICIO, true);
        f.add(fila("Botón Inicio del control", inicio ? "Abre Cristian Plaza TV · presiona para usar el de Google TV"
                : "Abre el inicio de Google TV · presiona para usar Cristian Plaza TV", () -> {
            boolean nuevo = !BotonesControl.prefs(this).getBoolean(BotonesControl.PREF_INICIO, true);
            BotonesControl.prefs(this).edit().putBoolean(BotonesControl.PREF_INICIO, nuevo).apply();
            abrirMenu();
        }));
        f.add(fila("Logo de Cristian Plaza", conFirma ? "Visible · presiona para ocultarlo" : "Oculto · presiona para mostrarlo", () -> {
            boolean nuevo = !prefs.getBoolean("firma", true);
            prefs.edit().putBoolean("firma", nuevo).apply();
            firma.setVisibility(nuevo ? View.VISIBLE : View.GONE);
            abrirMenu();
        }));
        f.add(fila("Restablecer orden de las apps", null, () -> {
            prefs.edit().remove("orden").apply();
            cerrarOverlay();
            cargarApps(null);
        }));
        f.add(fila("Agregar mis propios fondos", "Copia imágenes a Android/data/" + getPackageName() + "/files/fondos", () -> {}));
        abrirLista("Cristian Plaza TV", null, f);
    }

    private void menuOcultas() {
        List<FrameLayout> f = new ArrayList<>();
        Set<String> ocultas = ocultasPorUsuario();
        Map<String, String> nombres = new HashMap<>();
        for (App a : todasLasApps()) nombres.put(a.paquete, a.nombre);
        nombres.put(ENTRADAS, "Entradas");
        for (final String p : ocultas) {
            String n = nombres.containsKey(p) ? nombres.get(p) : p;
            f.add(fila(n, "Presiona para volver a mostrarla", () -> {
                Set<String> s = ocultasPorUsuario();
                s.remove(p);
                prefs.edit().putStringSet("ocultas", s).apply();
                cargarApps(null);
                menuOcultas();
            }));
        }
        if (f.isEmpty()) f.add(fila("No hay apps ocultas", "Mantén presionado OK sobre una app para ocultarla", this::abrirMenu));
        abrirLista("Apps ocultas", null, f);
    }

    /** Mantener presionado OK sobre una app. */
    private void menuApp(final App a) {
        List<FrameLayout> f = new ArrayList<>();
        f.add(fila("Mover", "Cambia su posición con las flechas", () -> {
            cerrarOverlay();
            View t = tarjetas.get(a.paquete);
            if (t != null) t.requestFocus();
            empezarMover(a);
        }));
        f.add(fila("Ocultar", "Puedes volver a mostrarla desde el menú ⚙", () -> {
            Set<String> s = ocultasPorUsuario();
            s.add(a.paquete);
            prefs.edit().putStringSet("ocultas", s).apply();
            apps.remove(a);
            guardarOrden();
            cerrarOverlay();
            cargarApps(null);
        }));
        if (!ENTRADAS.equals(a.paquete)) {
            f.add(fila("Asignar a un botón del control", null, () -> esperarBoton(a.paquete)));
            final boolean propia = sePuedeDesinstalar(a.paquete);
            f.add(fila("Desinstalar", propia ? "Borra la app del TV (te pedirá confirmar)"
                    : "Viene de fábrica: abre su ficha en Ajustes para desinstalarla o desactivarla", () -> {
                cerrarOverlay();
                android.net.Uri uri = android.net.Uri.fromParts("package", a.paquete, null);
                try {
                    startActivity(new Intent(propia ? Intent.ACTION_DELETE
                            : Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) { }
            }));
        }
        abrirLista(a.nombre, null, f);
    }

    /** Solo apps instaladas por el usuario (las de fábrica no se pueden borrar sin ADB). */
    private boolean sePuedeDesinstalar(String paquete) {
        try {
            android.content.pm.ApplicationInfo ai = getPackageManager().getApplicationInfo(paquete, 0);
            return (ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- botones del control
    private String nombreDestino(String destino) {
        if (BotonesControl.AJUSTES.equals(destino)) return "Ajustes del TV";
        if (ENTRADAS.equals(destino)) return "Entradas";
        try {
            return String.valueOf(getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(destino, 0)));
        } catch (Exception e) {
            return destino;
        }
    }

    private void menuBotones() {
        List<FrameLayout> f = new ArrayList<>();
        if (!BotonesControl.activo(this)) {
            f.add(fila("Activar el servicio de botones",
                    "Ajustes → Accesibilidad → \"Botones del control\" → activar", () -> {
                        cerrarOverlay();
                        try {
                            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        } catch (Exception ignored) { }
                    }));
        }
        f.add(fila("+ Asignar un botón", "Presionas el botón y eliges la app", () -> esperarBoton(null)));
        for (final Map.Entry<Integer, String> e : BotonesControl.asignaciones(this).entrySet()) {
            f.add(fila(BotonesControl.nombreTecla(e.getKey()) + "  →  " + nombreDestino(e.getValue()),
                    "Presiona para cambiar o quitar", () -> menuUnBoton(e.getKey())));
        }
        abrirLista("Botones del control",
                BotonesControl.activo(this) ? null : "Para usar esta función hay que activar el servicio una sola vez.", f);
    }

    private void menuUnBoton(final int tecla) {
        List<FrameLayout> f = new ArrayList<>();
        f.add(fila("Cambiar la app", null, () -> elegirDestino(tecla)));
        f.add(fila("Quitar (vuelve a su función original)", null, () -> {
            BotonesControl.asignar(this, tecla, null);
            menuBotones();
        }));
        abrirLista(BotonesControl.nombreTecla(tecla), null, f);
    }

    /** Espera a que se presione un botón del control; si destino != null lo asigna directo. */
    private void esperarBoton(final String destino) {
        if (!BotonesControl.activo(this)) { menuBotones(); return; }
        LinearLayout caja = new LinearLayout(this);
        caja.setOrientation(LinearLayout.VERTICAL);
        caja.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams envolver = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        caja.addView(titulo("Ahora presiona el botón que quieres asignar"), envolver);
        TextView n = new TextView(this);
        n.setText("Usa el control remoto. Por ejemplo, el botón de una app que no usas.\n\nPara salir presiona Back ←");
        n.setTextColor(0xB3FFFFFF);
        n.setTextSize(14);
        n.setGravity(Gravity.CENTER);
        n.setPadding(0, px(12), 0, 0);
        caja.addView(n, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View foco = new View(this);
        foco.setFocusable(true);
        caja.addView(foco, new LinearLayout.LayoutParams(1, 1));
        abrirOverlay(caja, foco);
        BotonesControl.escuchando = codigo -> ui.post(() -> {
            if (destino != null) {
                BotonesControl.asignar(this, codigo, destino);
                menuBotones();
            } else {
                elegirDestino(codigo);
            }
        });
    }

    private void elegirDestino(final int tecla) {
        List<FrameLayout> f = new ArrayList<>();
        f.add(fila("Ajustes del TV", null, () -> { BotonesControl.asignar(this, tecla, BotonesControl.AJUSTES); menuBotones(); }));
        if (!entradasHdmi().isEmpty()) {
            f.add(fila("Entradas (HDMI)", null, () -> { BotonesControl.asignar(this, tecla, ENTRADAS); menuBotones(); }));
        }
        for (final App a : todasLasApps()) {
            f.add(fila(a.nombre, null, () -> { BotonesControl.asignar(this, tecla, a.paquete); menuBotones(); }));
        }
        abrirLista(BotonesControl.nombreTecla(tecla) + " abrirá…", null, f);
    }

    // ---------------------------------------------------------------- entradas HDMI
    private static class Entrada {
        String id, nombre, puerto;
    }

    /** Entradas HDMI físicas del TV, con el nombre del equipo conectado si el TV lo conoce. */
    private List<Entrada> entradasHdmi() {
        List<Entrada> l = new ArrayList<>();
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) return l;
            List<TvInputInfo> todas = tim.getTvInputList();
            for (TvInputInfo i : todas) {
                if (i.getType() != TvInputInfo.TYPE_HDMI || i.getParentId() != null) continue;
                Entrada e = new Entrada();
                e.id = i.getId();
                e.puerto = String.valueOf(i.loadLabel(this));
                CharSequence propio = i.loadCustomLabel(this);
                String nombre = propio != null ? propio.toString() : null;
                // Equipo conectado por HDMI-CEC (p. ej. "Mac", "PlayStation").
                for (TvInputInfo hijo : todas) {
                    if (nombre == null && e.id.equals(hijo.getParentId())) {
                        CharSequence n = hijo.loadCustomLabel(this);
                        if (n == null) n = hijo.loadLabel(this);
                        if (n != null && n.length() > 0) nombre = n.toString();
                    }
                }
                e.nombre = nombre != null ? nombre : e.puerto;
                l.add(e);
            }
            Collections.sort(l, (a, b) -> a.puerto.compareToIgnoreCase(b.puerto));
        } catch (Exception ignored) { }
        return l;
    }

    private void abrirEntradas() {
        List<Entrada> entradas = entradasHdmi();
        if (entradas.isEmpty()) return;
        LinearLayout caja = new LinearLayout(this);
        caja.setOrientation(LinearLayout.VERTICAL);
        caja.setGravity(Gravity.CENTER_HORIZONTAL);
        caja.setClipChildren(false);
        caja.setClipToPadding(false);
        LinearLayout.LayoutParams lpT = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpT.bottomMargin = px(36);
        caja.addView(titulo("Entradas"), lpT);

        LinearLayout filaT = new LinearLayout(this);
        filaT.setOrientation(LinearLayout.HORIZONTAL);
        filaT.setClipChildren(false);
        filaT.setClipToPadding(false);
        View primera = null;
        int max = Math.min(entradas.size(), COLUMNAS);
        for (int i = 0; i < max; i++) {
            final Entrada e = entradas.get(i);
            FrameLayout t = new FrameLayout(this);
            t.setFocusable(true);
            t.setClickable(true);
            t.setContentDescription(e.nombre);
            t.setOnFocusChangeListener(vidrio(t, px(20), 1.10f));

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            ImageView ic = new ImageView(this);
            ic.setImageBitmap(iconoHdmi(px(22)));
            col.addView(ic, new LinearLayout.LayoutParams(Math.round(px(22) * 1.9f), px(22)));
            TextView n = new TextView(this);
            n.setText(e.nombre);
            n.setTextColor(Color.WHITE);
            n.setTextSize(18);
            n.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            n.setSingleLine(true);
            n.setEllipsize(TextUtils.TruncateAt.END);
            n.setPadding(px(10), px(8), px(10), 0);
            n.setShadowLayer(px(8), 0, 0, 0x66FFFFFF);
            n.setGravity(Gravity.CENTER);
            col.addView(n, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            if (!e.nombre.equals(e.puerto)) {
                TextView s = new TextView(this);
                s.setText(e.puerto.toUpperCase());
                s.setTextColor(0x99FFFFFF);
                s.setTextSize(11);
                s.setLetterSpacing(0.08f);
                col.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            t.addView(col, lleno());
            t.setOnClickListener(v -> { cerrarOverlay(); abrirEntrada(e.id); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
            if (i < max - 1) lp.rightMargin = px(30);
            filaT.addView(t, lp);
            if (primera == null) primera = t;
        }
        caja.addView(filaT, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        abrirOverlay(caja, primera);
    }

    private void abrirEntrada(String idEntrada) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(idEntrada));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        } catch (Exception ignored) { }
    }

    private void abrir(ComponentName c) {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).setComponent(c)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------- fondos
    /** La primera vez, copia los fondos incluidos en el APK a la carpeta de fondos. */
    private void copiarFondosIncluidos() {
        final File dir = getExternalFilesDir("fondos");
        if (dir == null || prefs.getBoolean("fondos_copiados", false)) return;
        io.execute(() -> {
            try {
                String[] lista = getAssets().list("fondos");
                if (lista != null) for (String n : lista) {
                    File destino = new File(dir, n);
                    if (destino.exists()) continue;
                    try (InputStream in = getAssets().open("fondos/" + n);
                         OutputStream out = new FileOutputStream(destino)) {
                        byte[] buf = new byte[64 * 1024];
                        int r;
                        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                    }
                }
                prefs.edit().putBoolean("fondos_copiados", true).apply();
                ui.post(() -> { cargarListaFondos(); if (indiceFondo < 0) siguienteFondo(); });
            } catch (Exception ignored) { }
        });
    }

    private void cargarListaFondos() {
        List<File> nuevos = new ArrayList<>();
        File carpeta = getExternalFilesDir("fondos");
        File[] archivos = carpeta == null ? null : carpeta.listFiles((d, n) -> {
            String l = n.toLowerCase();
            return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp");
        });
        if (archivos != null) nuevos.addAll(Arrays.asList(archivos));
        if (nuevos.size() != fondos.size() || !new HashSet<>(nuevos).equals(new HashSet<>(fondos))) {
            Collections.shuffle(nuevos);
            fondos.clear();
            fondos.addAll(nuevos);
            indiceFondo = Math.min(indiceFondo, fondos.size() - 1);
        }
    }

    private void siguienteFondo() {
        if (fondos.isEmpty()) {
            fondoA.setImageDrawable(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.rgb(10, 14, 28), Color.rgb(4, 4, 8)}));
            ultimoCambio = SystemClock.elapsedRealtime();
            ui.removeCallbacks(reintentarFondos);
            ui.postDelayed(reintentarFondos, 4000);
            return;
        }
        indiceFondo = (indiceFondo + 1) % fondos.size();
        if (indiceFondo == 0 && fondos.size() > 2) Collections.shuffle(fondos);
        final File archivo = fondos.get(indiceFondo);
        final int ancho = Math.max(getResources().getDisplayMetrics().widthPixels, 1280);
        ultimoCambio = SystemClock.elapsedRealtime();
        io.execute(() -> {
            Bitmap bmp = decodificar(archivo, ancho);
            if (bmp == null) return;
            ui.post(() -> mostrar(bmp));
        });
    }

    private static Bitmap decodificar(File f, int anchoMax) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int muestra = 1;
            while (o.outWidth / (muestra * 2) >= anchoMax) muestra *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = muestra;
            d.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), d);
        } catch (Throwable t) {
            return null;
        }
    }

    private void mostrar(Bitmap bmp) {
        final ImageView entra = mostrandoA ? fondoB : fondoA;
        final ImageView sale = mostrandoA ? fondoA : fondoB;
        entra.setImageBitmap(bmp);
        raiz.removeView(entra);
        raiz.addView(entra, 1, lleno());
        entra.setAlpha(0f);
        entra.animate().alpha(1f).setDuration(FUNDIDO_MS).withEndAction(() -> {
            sale.setAlpha(0f);
            sale.setImageDrawable(null);
            raiz.removeView(sale);
            raiz.addView(sale, 0, lleno());
        }).start();
        mostrandoA = !mostrandoA;
    }
}
