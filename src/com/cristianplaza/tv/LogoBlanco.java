package com.cristianplaza.tv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

/**
 * Convierte el banner de una app en su logo blanco con fondo transparente,
 * directamente en el TV (el APK no incluye logos de ninguna marca).
 *
 * - Fondo claro o de un solo color: es logo todo lo que se aleja del color de fondo.
 * - Fondo oscuro o degradado: es logo lo que brilla sobre su entorno ("top-hat" de luminancia).
 * - Si el resultado es un recuadro lleno (logo dentro de una "baldosa"), se repite el proceso
 *   dentro de ese recuadro, usándolo como nuevo fondo.
 */
final class LogoBlanco {

    private static final int W = 480, H = 270;

    private LogoBlanco() { }

    /** Devuelve el logo blanco recortado, o null si no se pudo separar del fondo. */
    static Bitmap desdeBanner(Drawable banner) {
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Drawable cp = banner.getConstantState() != null
                ? banner.getConstantState().newDrawable().mutate() : banner.mutate();
        cp.setBounds(0, 0, W, H);
        cp.draw(new Canvas(b));
        int[] px = new int[W * H];
        b.getPixels(px, 0, W, 0, 0, W, H);
        b.recycle();
        return segmentar(px, W, H, 0);
    }

    private static Bitmap segmentar(int[] px, int w, int h, int profundidad) {
        int total = w * h;
        float[] r = new float[total], g = new float[total], bl = new float[total], L = new float[total];
        for (int i = 0; i < total; i++) {
            int c = px[i];
            float a = ((c >>> 24) & 0xFF) / 255f; // transparente = negro
            r[i] = ((c >> 16) & 0xFF) * a;
            g[i] = ((c >> 8) & 0xFF) * a;
            bl[i] = (c & 0xFF) * a;
            L[i] = 0.299f * r[i] + 0.587f * g[i] + 0.114f * bl[i];
        }

        // Color de fondo: mediana del borde y si es uniforme.
        int borde = Math.max(2, Math.min(6, Math.min(w, h) / 20)), n = 0;
        float[] br = new float[2 * borde * (w + h)], bg = new float[br.length], bb = new float[br.length];
        for (int y = 0; y < h && n < br.length; y++) for (int x = 0; x < w && n < br.length; x++) {
            if (x >= borde && x < w - borde && y >= borde && y < h - borde) continue;
            int i = y * w + x;
            br[n] = r[i]; bg[n] = g[i]; bb[n] = bl[i]; n++;
        }
        float fr = mediana(br, n), fg = mediana(bg, n), fb = mediana(bb, n);
        boolean liso = desv(br, n) < 12 && desv(bg, n) < 12 && desv(bb, n) < 12;
        float lumFondo = 0.299f * fr + 0.587f * fg + 0.114f * fb;

        float[] m = new float[total];
        if (lumFondo > 170 || liso) {
            for (int i = 0; i < total; i++) {
                float dr = r[i] - fr, dg = g[i] - fg, db = bl[i] - fb;
                float d = (float) Math.sqrt(dr * dr + dg * dg + db * db);
                m[i] = clamp((d - 25f) / 70f);
            }
        } else {
            // Apertura morfológica en 1/4 de resolución para estimar el fondo.
            int w4 = Math.max(1, w / 4), h4 = Math.max(1, h / 4);
            float[] p = new float[w4 * h4];
            for (int y = 0; y < h4; y++) for (int x = 0; x < w4; x++) {
                float s = 0; int c = 0;
                for (int dy = 0; dy < 4; dy++) for (int dx = 0; dx < 4; dx++) {
                    int yy = y * 4 + dy, xx = x * 4 + dx;
                    if (yy < h && xx < w) { s += L[yy * w + xx]; c++; }
                }
                p[y * w4 + x] = c == 0 ? 0 : s / c;
            }
            int radio = Math.max(4, Math.round(17f * w / W));
            float[] ap = filtro(filtro(p, w4, h4, radio, true), w4, h4, radio, false);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                float fondo = muestrear(ap, w4, h4, x / 4f, y / 4f);
                m[y * w + x] = clamp((L[y * w + x] - fondo - 30f) / 90f);
            }
            // Franjas de luz que cruzan todo el ancho no son logo.
            for (int y = 0; y < h; y++) {
                int llenos = 0;
                for (int x = 0; x < w; x++) if (m[y * w + x] > 0.3f) llenos++;
                if (llenos > w * 0.75f || (profundidad == 0 && y >= h * 0.9f)) {
                    for (int x = 0; x < w; x++) m[y * w + x] = 0;
                }
            }
            // Descarta brillos tenues del fondo y deja el logo nítido.
            for (int i = 0; i < total; i++) m[i] = clamp((m[i] - 0.42f) / 0.5f);
        }

        // Recorte al contenido.
        int x0 = w, y0 = h, x1 = -1, y1 = -1;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (m[y * w + x] > 0.16f) {
                if (x < x0) x0 = x;
                if (x > x1) x1 = x;
                if (y < y0) y0 = y;
                if (y > y1) y1 = y;
            }
        }
        if (x1 < 0 || (x1 - x0) < 8 || (y1 - y0) < 6) return null;
        if ((x1 - x0) > w * 0.97f && (y1 - y0) > h * 0.95f) return null;
        int ow = x1 - x0 + 1, oh = y1 - y0 + 1;

        // ¿Es una "baldosa" llena (el logo vino dentro de un recuadro)? → repetir dentro de ella.
        if (profundidad == 0 && ow > 24 && oh > 24) {
            int llenos = 0, cuenta = 0;
            int mx = Math.max(2, ow / 12), my = Math.max(2, oh / 12);
            for (int y = y0 + my; y <= y1 - my; y += 2) for (int x = x0 + mx; x <= x1 - mx; x += 2) {
                boolean cerca = x - x0 < mx * 2 || x1 - x < mx * 2 || y - y0 < my * 2 || y1 - y < my * 2;
                if (!cerca) continue;
                cuenta++;
                if (m[y * w + x] > 0.5f) llenos++;
            }
            double suma = 0;
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) suma += m[y * w + x];
            boolean casiLleno = suma / (ow * (double) oh) > 0.7;
            if (cuenta > 0 && (llenos > cuenta * 0.85f || casiLleno)) {
                // Recorte interior (dejando fuera las esquinas redondeadas) y nueva pasada.
                int ix0 = x0 + mx, iy0 = y0 + my, iw = ow - 2 * mx, ih = oh - 2 * my;
                int[] sub = new int[iw * ih];
                for (int y = 0; y < ih; y++) System.arraycopy(px, (iy0 + y) * w + ix0, sub, y * iw, iw);
                Bitmap interior = segmentar(sub, iw, ih, 1);
                if (interior != null) return interior;
            }
        }

        int[] out = new int[ow * oh];
        for (int y = 0; y < oh; y++) for (int x = 0; x < ow; x++) {
            int a = Math.round(m[(y + y0) * w + x + x0] * 255f);
            out[y * ow + x] = (a << 24) | 0xFFFFFF;
        }
        return Bitmap.createBitmap(out, ow, oh, Bitmap.Config.ARGB_8888);
    }

    private static float clamp(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    private static float mediana(float[] v, int n) {
        if (n == 0) return 0;
        float[] c = java.util.Arrays.copyOf(v, n);
        java.util.Arrays.sort(c);
        return c[n / 2];
    }

    private static float desv(float[] v, int n) {
        if (n == 0) return 0;
        double s = 0, s2 = 0;
        for (int i = 0; i < n; i++) { s += v[i]; s2 += v[i] * v[i]; }
        double med = s / n;
        return (float) Math.sqrt(Math.max(0, s2 / n - med * med));
    }

    /** Filtro mínimo o máximo separable con radio r. */
    private static float[] filtro(float[] src, int w, int h, int r, boolean minimo) {
        float[] tmp = new float[src.length], out = new float[src.length];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float v = minimo ? Float.MAX_VALUE : -Float.MAX_VALUE;
            for (int k = Math.max(0, x - r); k <= Math.min(w - 1, x + r); k++) {
                float s = src[y * w + k];
                v = minimo ? Math.min(v, s) : Math.max(v, s);
            }
            tmp[y * w + x] = v;
        }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float v = minimo ? Float.MAX_VALUE : -Float.MAX_VALUE;
            for (int k = Math.max(0, y - r); k <= Math.min(h - 1, y + r); k++) {
                float s = tmp[k * w + x];
                v = minimo ? Math.min(v, s) : Math.max(v, s);
            }
            out[y * w + x] = v;
        }
        return out;
    }

    private static float muestrear(float[] a, int w, int h, float x, float y) {
        int xi = Math.min(w - 1, (int) x), yi = Math.min(h - 1, (int) y);
        int xj = Math.min(w - 1, xi + 1), yj = Math.min(h - 1, yi + 1);
        float fx = x - xi, fy = y - yi;
        float top = a[yi * w + xi] * (1 - fx) + a[yi * w + xj] * fx;
        float bot = a[yj * w + xi] * (1 - fx) + a[yj * w + xj] * fx;
        return top * (1 - fy) + bot * fy;
    }
}
