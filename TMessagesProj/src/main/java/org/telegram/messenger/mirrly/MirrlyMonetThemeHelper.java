package org.telegram.messenger.mirrly;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generates and synchronizes Material You (Monet) dynamic {@code .attheme} themes from the
 * Android 12+ system tonal palette (with user seed-color override, palette styles, AMOLED black,
 * glass tinting, and a Material 3 baseline fallback on older Android versions).
 */
public final class MirrlyMonetThemeHelper {

    public static final String LIGHT_THEME_NAME = "Mirrly Monet Light";
    public static final String DARK_THEME_NAME = "Mirrly Monet Dark";

    private static final int MONET_THEME_SCHEMA_VERSION = 2;

    private static final String LIGHT_FILE_NAME = "mirrly_monet_light.attheme";
    private static final String DARK_FILE_NAME = "mirrly_monet_dark.attheme";

    private static final String PREF_PREV_DAY_THEME = "monet_prev_day_theme";
    private static final String PREF_PREV_NIGHT_THEME = "monet_prev_night_theme";
    private static final String PREF_PREV_AUTO_NIGHT_TYPE = "monet_prev_auto_night_type";

    public static final int[] SEED_PRESETS = new int[]{
            0,          // 0 = Auto from system wallpaper
            0xFF3A7AFE, // Ocean Blue
            0xFF6750A4, // Royal Violet
            0xFF0E9F6E, // Emerald Mint
            0xFFE65100, // Sunset Amber
            0xFFD81B60, // Berry Rose
            0xFF00838F  // Deep Teal
    };

    public static final class Palette {
        public final boolean fromSystem;
        public final int style;
        public final int customSeed;

        public final int accent1_50;
        public final int accent1_100;
        public final int accent1_200;
        public final int accent1_300;
        public final int accent1_500;
        public final int accent1_600;
        public final int accent1_700;
        public final int accent1_800;
        public final int accent1_900;

        public final int accent2_50;
        public final int accent2_100;
        public final int accent2_200;
        public final int accent2_500;
        public final int accent2_700;
        public final int accent2_800;
        public final int accent2_900;

        public final int accent3_50;
        public final int accent3_200;
        public final int accent3_500;
        public final int accent3_700;
        public final int accent3_900;

        public final int neutral1_10;
        public final int neutral1_50;
        public final int neutral1_100;
        public final int neutral1_200;
        public final int neutral1_400;
        public final int neutral1_500;
        public final int neutral1_700;
        public final int neutral1_800;
        public final int neutral1_900;

        public final int neutral2_50;
        public final int neutral2_100;
        public final int neutral2_200;
        public final int neutral2_400;
        public final int neutral2_500;
        public final int neutral2_600;
        public final int neutral2_700;
        public final int neutral2_800;
        public final int neutral2_900;

        private Palette(
                boolean fromSystem,
                int style,
                int customSeed,
                int accent1_50, int accent1_100, int accent1_200, int accent1_300,
                int accent1_500, int accent1_600, int accent1_700, int accent1_800, int accent1_900,
                int accent2_50, int accent2_100, int accent2_200, int accent2_500,
                int accent2_700, int accent2_800, int accent2_900,
                int accent3_50, int accent3_200, int accent3_500, int accent3_700, int accent3_900,
                int neutral1_10, int neutral1_50, int neutral1_100, int neutral1_200,
                int neutral1_400, int neutral1_500, int neutral1_700, int neutral1_800, int neutral1_900,
                int neutral2_50, int neutral2_100, int neutral2_200, int neutral2_400,
                int neutral2_500, int neutral2_600, int neutral2_700, int neutral2_800, int neutral2_900
        ) {
            this.fromSystem = fromSystem;
            this.style = style;
            this.customSeed = customSeed;
            this.accent1_50 = accent1_50;
            this.accent1_100 = accent1_100;
            this.accent1_200 = accent1_200;
            this.accent1_300 = accent1_300;
            this.accent1_500 = accent1_500;
            this.accent1_600 = accent1_600;
            this.accent1_700 = accent1_700;
            this.accent1_800 = accent1_800;
            this.accent1_900 = accent1_900;
            this.accent2_50 = accent2_50;
            this.accent2_100 = accent2_100;
            this.accent2_200 = accent2_200;
            this.accent2_500 = accent2_500;
            this.accent2_700 = accent2_700;
            this.accent2_800 = accent2_800;
            this.accent2_900 = accent2_900;
            this.accent3_50 = accent3_50;
            this.accent3_200 = accent3_200;
            this.accent3_500 = accent3_500;
            this.accent3_700 = accent3_700;
            this.accent3_900 = accent3_900;
            this.neutral1_10 = neutral1_10;
            this.neutral1_50 = neutral1_50;
            this.neutral1_100 = neutral1_100;
            this.neutral1_200 = neutral1_200;
            this.neutral1_400 = neutral1_400;
            this.neutral1_500 = neutral1_500;
            this.neutral1_700 = neutral1_700;
            this.neutral1_800 = neutral1_800;
            this.neutral1_900 = neutral1_900;
            this.neutral2_50 = neutral2_50;
            this.neutral2_100 = neutral2_100;
            this.neutral2_200 = neutral2_200;
            this.neutral2_400 = neutral2_400;
            this.neutral2_500 = neutral2_500;
            this.neutral2_600 = neutral2_600;
            this.neutral2_700 = neutral2_700;
            this.neutral2_800 = neutral2_800;
            this.neutral2_900 = neutral2_900;
        }

        public int signature() {
            int h = MONET_THEME_SCHEMA_VERSION;
            h = 31 * h + (fromSystem ? 1 : 0);
            h = 31 * h + style;
            h = 31 * h + customSeed;
            h = 31 * h + accent1_500;
            h = 31 * h + accent1_200;
            h = 31 * h + accent1_600;
            h = 31 * h + accent2_500;
            h = 31 * h + accent3_500;
            h = 31 * h + neutral1_50;
            h = 31 * h + neutral1_900;
            return h;
        }
    }

    public static final class PreviewColors {
        public final int accentPrimary;
        public final int accentSecondary;
        public final int accentTertiary;
        public final int surfaceBackground;
        public final int surfaceCard;
        public final int incomingBubble;
        public final int outgoingBubbleTop;
        public final int outgoingBubbleBottom;
        public final int wallpaperTop;
        public final int wallpaperBottom;
        public final int wallpaper3;
        public final int wallpaper4;
        public final int wallpaperPatternIntensity;
        public final int textPrimary;
        public final int textSecondary;
        public final int textOnOutgoing;
        public final int tabBarTint;

        public PreviewColors(
                int accentPrimary, int accentSecondary, int accentTertiary,
                int surfaceBackground, int surfaceCard,
                int incomingBubble, int outgoingBubbleTop, int outgoingBubbleBottom,
                int wallpaperTop, int wallpaperBottom, int wallpaper3, int wallpaper4, int wallpaperPatternIntensity,
                int textPrimary, int textSecondary, int textOnOutgoing,
                int tabBarTint
        ) {
            this.accentPrimary = accentPrimary;
            this.accentSecondary = accentSecondary;
            this.accentTertiary = accentTertiary;
            this.surfaceBackground = surfaceBackground;
            this.surfaceCard = surfaceCard;
            this.incomingBubble = incomingBubble;
            this.outgoingBubbleTop = outgoingBubbleTop;
            this.outgoingBubbleBottom = outgoingBubbleBottom;
            this.wallpaperTop = wallpaperTop;
            this.wallpaperBottom = wallpaperBottom;
            this.wallpaper3 = wallpaper3;
            this.wallpaper4 = wallpaper4;
            this.wallpaperPatternIntensity = wallpaperPatternIntensity;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.textOnOutgoing = textOnOutgoing;
            this.tabBarTint = tabBarTint;
        }
    }

    @NonNull
    public static int[] computeWallpaperColors(@NonNull Palette p, boolean dark, boolean amoled, boolean themedWallpaper) {
        if (!dark) {
            if (!themedWallpaper) {
                int bgGray = ColorUtils.blendARGB(p.neutral1_50, p.neutral2_50, 0.45f);
                return new int[]{bgGray, bgGray, bgGray, bgGray};
            }
            int w1 = ColorUtils.blendARGB(p.accent1_50, p.neutral1_10, 0.12f);
            int w2 = ColorUtils.blendARGB(p.accent2_100, p.accent1_100, 0.42f);
            int w3 = ColorUtils.blendARGB(p.accent3_50, p.accent3_200, 0.34f);
            int w4 = ColorUtils.blendARGB(p.accent1_100, p.neutral2_100, 0.24f);
            if (p.style == MirrlyConfig.MONET_STYLE_MONOCHROME) {
                w1 = ColorUtils.blendARGB(p.neutral1_10, Color.WHITE, 0.15f);
                w2 = p.neutral1_50;
                w3 = p.neutral2_100;
                w4 = p.neutral1_100;
            }
            return new int[]{w1, w2, w3, w4};
        }
        if (!themedWallpaper) {
            int flat = amoled ? Color.BLACK : ColorUtils.blendARGB(p.neutral1_900, Color.BLACK, 0.36f);
            return new int[]{flat, flat, flat, flat};
        }
        if (amoled) {
            int w1 = ColorUtils.blendARGB(p.accent1_700, Color.BLACK, 0.46f);
            int w2 = ColorUtils.blendARGB(p.accent2_700, Color.BLACK, 0.50f);
            int w3 = ColorUtils.blendARGB(p.accent3_700, Color.BLACK, 0.48f);
            int w4 = ColorUtils.blendARGB(p.accent1_800, Color.BLACK, 0.54f);
            return new int[]{w1, w2, w3, w4};
        }
        int w1 = ColorUtils.blendARGB(p.neutral1_900, Color.BLACK, 0.42f);
        int w2 = ColorUtils.blendARGB(p.accent1_900, p.neutral1_900, 0.48f);
        int w3 = ColorUtils.blendARGB(p.accent3_900, Color.BLACK, 0.46f);
        int w4 = ColorUtils.blendARGB(p.accent2_900, p.neutral2_900, 0.50f);
        return new int[]{w1, w2, w3, w4};
    }

    private MirrlyMonetThemeHelper() {
    }

    public static boolean isDeviceMonetSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static boolean isMonetTheme(@Nullable Theme.ThemeInfo info) {
        if (info == null) {
            return false;
        }
        String key = info.getKey();
        return LIGHT_THEME_NAME.equals(key) || DARK_THEME_NAME.equals(key);
    }

    @NonNull
    public static Palette extractPalette(@Nullable Context context) {
        return extractPalette(context, MirrlyConfig.getMonetStyle(), MirrlyConfig.getMonetCustomSeed());
    }

    @NonNull
    public static Palette extractPalette(@Nullable Context context, int style, int customSeed) {
        Palette base;
        if (customSeed != 0) {
            base = createSeededPalette(false, customSeed);
        } else {
            base = extractSystemOrBaselinePalette(context);
        }
        return applyStyleTransform(base, style);
    }

    @NonNull
    private static Palette extractSystemOrBaselinePalette(@Nullable Context context) {
        Context ctx = context != null ? context : ApplicationLoader.applicationContext;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ctx != null) {
            try {
                Resources res = ctx.getResources();
                int a1_50 = getSystemColor(res, android.R.color.system_accent1_50, 0xFFECEFFD);
                int a1_100 = getSystemColor(res, android.R.color.system_accent1_100, 0xFFD6E2FF);
                int a1_200 = getSystemColor(res, android.R.color.system_accent1_200, 0xFFAEC6FF);
                int a1_300 = getSystemColor(res, android.R.color.system_accent1_300, 0xFF81A8FF);
                int a1_500 = getSystemColor(res, android.R.color.system_accent1_500, 0xFF4277E8);
                int a1_600 = getSystemColor(res, android.R.color.system_accent1_600, 0xFF2B60D0);
                int a1_700 = getSystemColor(res, android.R.color.system_accent1_700, 0xFF1D4BB3);
                int a1_800 = getSystemColor(res, android.R.color.system_accent1_800, 0xFF13368A);
                int a1_900 = getSystemColor(res, android.R.color.system_accent1_900, 0xFF0B225B);

                int a2_50 = getSystemColor(res, android.R.color.system_accent2_50, 0xFFEDF0F8);
                int a2_100 = getSystemColor(res, android.R.color.system_accent2_100, 0xFFD9E1F2);
                int a2_200 = getSystemColor(res, android.R.color.system_accent2_200, 0xFFBDC7DC);
                int a2_500 = getSystemColor(res, android.R.color.system_accent2_500, 0xFF6B7994);
                int a2_700 = getSystemColor(res, android.R.color.system_accent2_700, 0xFF434F66);
                int a2_800 = getSystemColor(res, android.R.color.system_accent2_800, 0xFF2E384C);
                int a2_900 = getSystemColor(res, android.R.color.system_accent2_900, 0xFF1B2333);

                int a3_50 = getSystemColor(res, android.R.color.system_accent3_50, 0xFFF3ECF8);
                int a3_200 = getSystemColor(res, android.R.color.system_accent3_200, 0xFFCFBBE3);
                int a3_500 = getSystemColor(res, android.R.color.system_accent3_500, 0xFF7D5FA0);
                int a3_700 = getSystemColor(res, android.R.color.system_accent3_700, 0xFF533B70);
                int a3_900 = getSystemColor(res, android.R.color.system_accent3_900, 0xFF2B1B40);

                int n1_10 = getSystemColor(res, android.R.color.system_neutral1_10, 0xFFFCFCFF);
                int n1_50 = getSystemColor(res, android.R.color.system_neutral1_50, 0xFFF1F2F6);
                int n1_100 = getSystemColor(res, android.R.color.system_neutral1_100, 0xFFE3E5EB);
                int n1_200 = getSystemColor(res, android.R.color.system_neutral1_200, 0xFFC8CCD4);
                int n1_400 = getSystemColor(res, android.R.color.system_neutral1_400, 0xFF9196A1);
                int n1_500 = getSystemColor(res, android.R.color.system_neutral1_500, 0xFF747985);
                int n1_700 = getSystemColor(res, android.R.color.system_neutral1_700, 0xFF444852);
                int n1_800 = getSystemColor(res, android.R.color.system_neutral1_800, 0xFF2B2E36);
                int n1_900 = getSystemColor(res, android.R.color.system_neutral1_900, 0xFF181A20);

                int n2_50 = getSystemColor(res, android.R.color.system_neutral2_50, 0xFFEEF0F6);
                int n2_100 = getSystemColor(res, android.R.color.system_neutral2_100, 0xFFE0E3EC);
                int n2_200 = getSystemColor(res, android.R.color.system_neutral2_200, 0xFFC5CAD6);
                int n2_400 = getSystemColor(res, android.R.color.system_neutral2_400, 0xFF8D93A2);
                int n2_500 = getSystemColor(res, android.R.color.system_neutral2_500, 0xFF727786);
                int n2_600 = getSystemColor(res, android.R.color.system_neutral2_600, 0xFF5A5F6D);
                int n2_700 = getSystemColor(res, android.R.color.system_neutral2_700, 0xFF424754);
                int n2_800 = getSystemColor(res, android.R.color.system_neutral2_800, 0xFF2B2F3A);
                int n2_900 = getSystemColor(res, android.R.color.system_neutral2_900, 0xFF171A23);

                return new Palette(
                        true,
                        MirrlyConfig.MONET_STYLE_TONAL,
                        0,
                        a1_50, a1_100, a1_200, a1_300, a1_500, a1_600, a1_700, a1_800, a1_900,
                        a2_50, a2_100, a2_200, a2_500, a2_700, a2_800, a2_900,
                        a3_50, a3_200, a3_500, a3_700, a3_900,
                        n1_10, n1_50, n1_100, n1_200, n1_400, n1_500, n1_700, n1_800, n1_900,
                        n2_50, n2_100, n2_200, n2_400, n2_500, n2_600, n2_700, n2_800, n2_900
                );
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
        return createSeededPalette(false, 0xFF3A7AFE);
    }

    @NonNull
    private static Palette createSeededPalette(boolean fromSystem, int seedColor) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(seedColor | 0xFF000000, hsl);
        float h1 = hsl[0];
        float s1 = Math.max(0.45f, Math.min(0.88f, hsl[1]));
        float h2 = h1;
        float s2 = Math.max(0.16f, s1 * 0.34f);
        float h3 = (h1 + 52f) % 360f;
        float s3 = Math.max(0.28f, s1 * 0.62f);
        float sN1 = Math.min(0.08f, s1 * 0.08f);
        float sN2 = Math.min(0.14f, s1 * 0.14f);

        return new Palette(
                fromSystem,
                MirrlyConfig.MONET_STYLE_TONAL,
                seedColor,
                hslColor(h1, s1, 0.95f), hslColor(h1, s1, 0.90f), hslColor(h1, s1, 0.80f), hslColor(h1, s1, 0.70f),
                hslColor(h1, s1, 0.54f), hslColor(h1, s1, 0.44f), hslColor(h1, s1, 0.35f), hslColor(h1, s1, 0.25f), hslColor(h1, s1, 0.15f),
                hslColor(h2, s2, 0.95f), hslColor(h2, s2, 0.90f), hslColor(h2, s2, 0.80f), hslColor(h2, s2, 0.49f),
                hslColor(h2, s2, 0.33f), hslColor(h2, s2, 0.23f), hslColor(h2, s2, 0.14f),
                hslColor(h3, s3, 0.94f), hslColor(h3, s3, 0.80f), hslColor(h3, s3, 0.50f), hslColor(h3, s3, 0.34f), hslColor(h3, s3, 0.16f),
                hslColor(h1, sN1, 0.985f), hslColor(h1, sN1, 0.95f), hslColor(h1, sN1, 0.90f), hslColor(h1, sN1, 0.80f),
                hslColor(h1, sN1, 0.60f), hslColor(h1, sN1, 0.49f), hslColor(h1, sN1, 0.30f), hslColor(h1, sN1, 0.18f), hslColor(h1, sN1, 0.105f),
                hslColor(h1, sN2, 0.94f), hslColor(h1, sN2, 0.89f), hslColor(h1, sN2, 0.78f), hslColor(h1, sN2, 0.58f),
                hslColor(h1, sN2, 0.47f), hslColor(h1, sN2, 0.38f), hslColor(h1, sN2, 0.28f), hslColor(h1, sN2, 0.19f), hslColor(h1, sN2, 0.11f)
        );
    }

    @NonNull
    private static Palette applyStyleTransform(@NonNull Palette p, int style) {
        if (style == MirrlyConfig.MONET_STYLE_TONAL) {
            return p;
        }
        float accentSatScale;
        float neutralSatScale;
        float lightShift;
        if (style == MirrlyConfig.MONET_STYLE_VIBRANT) {
            accentSatScale = 1.42f;
            neutralSatScale = 1.25f;
            lightShift = 0.0f;
        } else if (style == MirrlyConfig.MONET_STYLE_PASTEL) {
            accentSatScale = 0.56f;
            neutralSatScale = 0.70f;
            lightShift = 0.03f;
        } else {
            accentSatScale = 0.0f;
            neutralSatScale = 0.0f;
            lightShift = 0.0f;
        }
        return new Palette(
                p.fromSystem,
                style,
                p.customSeed,
                scaleSat(p.accent1_50, accentSatScale, lightShift),
                scaleSat(p.accent1_100, accentSatScale, lightShift),
                scaleSat(p.accent1_200, accentSatScale, lightShift),
                scaleSat(p.accent1_300, accentSatScale, lightShift),
                scaleSat(p.accent1_500, accentSatScale, 0f),
                scaleSat(p.accent1_600, accentSatScale, style == MirrlyConfig.MONET_STYLE_MONOCHROME ? -0.10f : 0f),
                scaleSat(p.accent1_700, accentSatScale, 0f),
                scaleSat(p.accent1_800, accentSatScale, 0f),
                scaleSat(p.accent1_900, accentSatScale, 0f),
                scaleSat(p.accent2_50, accentSatScale, lightShift),
                scaleSat(p.accent2_100, accentSatScale, lightShift),
                scaleSat(p.accent2_200, accentSatScale, lightShift),
                scaleSat(p.accent2_500, accentSatScale, 0f),
                scaleSat(p.accent2_700, accentSatScale, 0f),
                scaleSat(p.accent2_800, accentSatScale, 0f),
                scaleSat(p.accent2_900, accentSatScale, 0f),
                scaleSat(p.accent3_50, accentSatScale, lightShift),
                scaleSat(p.accent3_200, accentSatScale, lightShift),
                scaleSat(p.accent3_500, accentSatScale, 0f),
                scaleSat(p.accent3_700, accentSatScale, 0f),
                scaleSat(p.accent3_900, accentSatScale, 0f),
                scaleSat(p.neutral1_10, neutralSatScale, 0f),
                scaleSat(p.neutral1_50, neutralSatScale, 0f),
                scaleSat(p.neutral1_100, neutralSatScale, 0f),
                scaleSat(p.neutral1_200, neutralSatScale, 0f),
                scaleSat(p.neutral1_400, neutralSatScale, 0f),
                scaleSat(p.neutral1_500, neutralSatScale, 0f),
                scaleSat(p.neutral1_700, neutralSatScale, 0f),
                scaleSat(p.neutral1_800, neutralSatScale, 0f),
                scaleSat(p.neutral1_900, neutralSatScale, 0f),
                scaleSat(p.neutral2_50, neutralSatScale, 0f),
                scaleSat(p.neutral2_100, neutralSatScale, 0f),
                scaleSat(p.neutral2_200, neutralSatScale, 0f),
                scaleSat(p.neutral2_400, neutralSatScale, 0f),
                scaleSat(p.neutral2_500, neutralSatScale, 0f),
                scaleSat(p.neutral2_600, neutralSatScale, 0f),
                scaleSat(p.neutral2_700, neutralSatScale, 0f),
                scaleSat(p.neutral2_800, neutralSatScale, 0f),
                scaleSat(p.neutral2_900, neutralSatScale, 0f)
        );
    }

    private static int scaleSat(int color, float satScale, float lightDelta) {
        if (satScale == 1f && lightDelta == 0f) {
            return color;
        }
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        hsl[1] = Math.max(0f, Math.min(0.95f, hsl[1] * satScale));
        if (lightDelta != 0f) {
            hsl[2] = Math.max(0.06f, Math.min(0.97f, hsl[2] + lightDelta));
        }
        return ColorUtils.HSLToColor(hsl);
    }

    private static int hslColor(float h, float s, float l) {
        return ColorUtils.HSLToColor(new float[]{h, s, l});
    }

    private static int getSystemColor(Resources res, int resId, int fallback) {
        try {
            return res.getColor(resId, null) | 0xFF000000;
        } catch (Throwable ignore) {
            return fallback;
        }
    }

    public static boolean resolveEffectiveDark(@Nullable Context context, int mode) {
        if (mode == MirrlyConfig.MONET_MODE_DARK) {
            return true;
        }
        if (mode == MirrlyConfig.MONET_MODE_LIGHT) {
            return false;
        }
        if (mode == MirrlyConfig.MONET_MODE_AUTO) {
            if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SYSTEM) {
                return isSystemDark(context);
            }
            return Theme.isCurrentThemeNight();
        }
        return Theme.isCurrentThemeNight();
    }

    public static boolean isSystemDark(@Nullable Context context) {
        Context ctx = context != null ? context : ApplicationLoader.applicationContext;
        if (ctx == null) {
            return Theme.isCurrentThemeNight();
        }
        int uiMode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }

    @NonNull
    public static PreviewColors getPreviewColors(
            @Nullable Context context,
            boolean dark,
            boolean amoled,
            boolean outGradient,
            boolean themedWallpaper
    ) {
        return getPreviewColors(
                context,
                MirrlyConfig.getMonetStyle(),
                MirrlyConfig.getMonetCustomSeed(),
                dark,
                amoled,
                outGradient,
                themedWallpaper,
                MirrlyConfig.isMonetGlassTint()
        );
    }

    @NonNull
    public static PreviewColors getPreviewColors(
            @Nullable Context context,
            int style,
            int customSeed,
            boolean dark,
            boolean amoled,
            boolean outGradient,
            boolean themedWallpaper,
            boolean glassTint
    ) {
        Palette p = extractPalette(context, style, customSeed);
        int[] wall = computeWallpaperColors(p, dark, amoled, themedWallpaper);
        if (!dark) {
            int bg = ColorUtils.blendARGB(p.neutral1_10, Color.WHITE, 0.25f);
            int card = ColorUtils.blendARGB(p.neutral1_50, p.neutral2_50, 0.45f);
            int inBubble = Color.WHITE;
            int outBottom = p.accent1_600;
            int outTop = outGradient ? ColorUtils.blendARGB(p.accent1_500, p.accent3_500, 0.35f) : outBottom;
            int tabTint = glassTint ? ColorUtils.blendARGB(bg, p.accent1_50, 0.50f) : bg;
            return new PreviewColors(
                    p.accent1_600,
                    p.accent2_500,
                    p.accent3_500,
                    bg,
                    card,
                    inBubble,
                    outTop,
                    outBottom,
                    wall[0],
                    wall[1],
                    wall[2],
                    wall[3],
                    themedWallpaper ? 38 : 0,
                    p.neutral1_900,
                    p.neutral2_600,
                    contrastTextFor(outBottom),
                    tabTint
            );
        }

        int surface = amoled ? Color.BLACK : ColorUtils.blendARGB(p.neutral1_900, Color.BLACK, 0.16f);
        int card = amoled ? 0xFF0A0A0D : ColorUtils.blendARGB(p.neutral1_900, Color.BLACK, 0.45f);
        int inBubble = amoled
                ? ColorUtils.blendARGB(p.neutral1_900, p.neutral1_800, 0.42f)
                : ColorUtils.blendARGB(p.neutral1_800, p.neutral2_800, 0.35f);
        int outBottom = ColorUtils.blendARGB(p.accent1_700, p.neutral1_800, 0.14f);
        int outTop = outGradient ? ColorUtils.blendARGB(p.accent1_600, p.accent3_700, 0.30f) : outBottom;
        int tabTint = glassTint
                ? (amoled ? ColorUtils.blendARGB(0xFF08080B, p.accent1_900, 0.24f) : ColorUtils.blendARGB(surface, p.accent2_900, 0.28f))
                : (amoled ? 0xFF0A0A0D : surface);
        return new PreviewColors(
                p.accent1_200,
                p.accent2_200,
                p.accent3_200,
                surface,
                card,
                inBubble,
                outTop,
                outBottom,
                wall[0],
                wall[1],
                wall[2],
                wall[3],
                themedWallpaper ? (amoled ? -52 : 32) : 0,
                p.neutral1_50,
                p.neutral2_400,
                contrastTextFor(outBottom),
                tabTint
        );
    }

    public static void applyMonetMode(@Nullable Context context, int mode, boolean animate, @Nullable View revealAnchor) {
        Context ctx = context != null ? context : ApplicationLoader.applicationContext;
        if (ctx == null) {
            return;
        }
        int previousMode = MirrlyConfig.getMonetMode();
        if (mode != MirrlyConfig.MONET_MODE_OFF && previousMode == MirrlyConfig.MONET_MODE_OFF) {
            capturePreviousNonMonetThemes();
        }
        MirrlyConfig.setMonetMode(mode);

        if (mode == MirrlyConfig.MONET_MODE_OFF) {
            restorePreviousThemes(ctx, animate, revealAnchor);
            return;
        }

        Palette palette = extractPalette(ctx);
        MirrlyConfig.setMonetPaletteSignature(palette.signature());
        boolean amoled = MirrlyConfig.isMonetAmoled();
        boolean outGradient = MirrlyConfig.isMonetOutGradient();
        boolean themedWallpaper = MirrlyConfig.isMonetThemedWallpaper();
        boolean glassTint = MirrlyConfig.isMonetGlassTint();

        Theme.ThemeInfo lightInfo = buildAndRegisterTheme(palette, false, false, outGradient, themedWallpaper, glassTint);
        Theme.ThemeInfo darkInfo = buildAndRegisterTheme(palette, true, amoled, outGradient, themedWallpaper, glassTint);
        if (lightInfo == null || darkInfo == null) {
            return;
        }

        if (mode == MirrlyConfig.MONET_MODE_LIGHT) {
            if (Theme.selectedAutoNightType != Theme.AUTO_NIGHT_TYPE_NONE) {
                Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_NONE;
                Theme.saveAutoNightThemeConfig();
            }
            applyResolvedTheme(lightInfo, false, animate, revealAnchor);
        } else if (mode == MirrlyConfig.MONET_MODE_DARK) {
            if (Theme.selectedAutoNightType != Theme.AUTO_NIGHT_TYPE_NONE) {
                Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_NONE;
                Theme.saveAutoNightThemeConfig();
            }
            applyResolvedTheme(darkInfo, false, animate, revealAnchor);
        } else {
            Theme.mirrlySetDayNightThemes(lightInfo, darkInfo);
            saveDayNightKeys(lightInfo.getKey(), darkInfo.getKey());
            if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_NONE) {
                Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_SYSTEM;
            }
            Theme.saveAutoNightThemeConfig();

            boolean shouldBeNight = resolveEffectiveDark(ctx, MirrlyConfig.MONET_MODE_AUTO);
            Theme.ThemeInfo target = shouldBeNight ? darkInfo : lightInfo;
            applyResolvedTheme(target, shouldBeNight, animate, revealAnchor);
        }
    }

    public static void regenerateActiveMonet(@Nullable Context context) {
        int mode = MirrlyConfig.getMonetMode();
        if (mode == MirrlyConfig.MONET_MODE_OFF) {
            return;
        }
        applyMonetMode(context, mode, false, null);
    }

    private static final AtomicBoolean resumeSyncInFlight = new AtomicBoolean(false);
    private static volatile boolean resumeSyncPending = false;

    public static boolean onConfigurationOrResume(@Nullable Context context) {
        final int mode = MirrlyConfig.getMonetMode();
        if (mode == MirrlyConfig.MONET_MODE_OFF) {
            return false;
        }
        if (!isMonetStillBound(mode)) {
            MirrlyConfig.setMonetMode(MirrlyConfig.MONET_MODE_OFF);
            return false;
        }

        final Context ctx = context != null ? context : ApplicationLoader.applicationContext;
        if (ctx == null) {
            return false;
        }
        final Context appCtx = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        final boolean shouldBeNight = resolveEffectiveDark(ctx, mode);

        if (!resumeSyncInFlight.compareAndSet(false, true)) {
            resumeSyncPending = true;
            return false;
        }
        scheduleResumeSync(appCtx, mode, shouldBeNight);
        return false;
    }

    private static void scheduleResumeSync(Context appCtx, int mode, boolean shouldBeNight) {
        Utilities.globalQueue.postRunnable(() -> {
            boolean willApplyOnUi = false;
            try {
                final int latestMode = MirrlyConfig.getMonetMode();
                if (latestMode == MirrlyConfig.MONET_MODE_OFF) {
                    return;
                }
                final Palette palette = extractPalette(appCtx);
                final int newSignature = palette.signature();
                final int savedSignature = MirrlyConfig.getMonetPaletteSignature();
                final File lightFile = getThemeFile(false);
                final File darkFile = getThemeFile(true);
                final boolean filesMissing = !lightFile.exists() || !darkFile.exists();
                final boolean needsRebuild = newSignature != savedSignature || filesMissing;

                final boolean amoled = MirrlyConfig.isMonetAmoled();
                final boolean outGradient = MirrlyConfig.isMonetOutGradient();
                final boolean themedWallpaper = MirrlyConfig.isMonetThemedWallpaper();
                final boolean glassTint = MirrlyConfig.isMonetGlassTint();

                if (needsRebuild) {
                    final String lightContent = generateAtThemeContent(palette, false, false, outGradient, themedWallpaper, glassTint);
                    final String darkContent = generateAtThemeContent(palette, true, amoled, outGradient, themedWallpaper, glassTint);
                    if (!writeThemeFile(lightFile, lightContent) || !writeThemeFile(darkFile, darkContent)) {
                        return;
                    }
                    MirrlyConfig.setMonetPaletteSignature(newSignature);
                }

                willApplyOnUi = true;
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        applySyncedMonetIfNeeded(appCtx, needsRebuild);
                    } finally {
                        finishResumeSync(appCtx);
                    }
                });
            } catch (Throwable t) {
                FileLog.e(t);
            } finally {
                if (!willApplyOnUi) {
                    finishResumeSync(appCtx);
                }
            }
        });
    }

    private static void finishResumeSync(Context appCtx) {
        if (resumeSyncPending) {
            resumeSyncPending = false;
            final int nextMode = MirrlyConfig.getMonetMode();
            if (nextMode != MirrlyConfig.MONET_MODE_OFF) {
                final boolean nextNight = resolveEffectiveDark(appCtx, nextMode);
                scheduleResumeSync(appCtx, nextMode, nextNight);
                return;
            }
        }
        resumeSyncInFlight.set(false);
    }

    private static void applySyncedMonetIfNeeded(Context appCtx, boolean rebuilt) {
        final int mode = MirrlyConfig.getMonetMode();
        if (mode == MirrlyConfig.MONET_MODE_OFF || !isMonetStillBound(mode)) {
            return;
        }
        final boolean shouldBeNight = resolveEffectiveDark(appCtx, mode);
        final boolean autoNightMismatch = mode == MirrlyConfig.MONET_MODE_AUTO
                && Theme.getCurrentTheme() != (shouldBeNight ? Theme.getTheme(DARK_THEME_NAME) : Theme.getTheme(LIGHT_THEME_NAME));
        if (!rebuilt && !autoNightMismatch) {
            return;
        }

        final Theme.ThemeInfo lightInfo = Theme.mirrlyRegisterLocalThemeFile(getThemeFile(false), LIGHT_THEME_NAME);
        final Theme.ThemeInfo darkInfo = Theme.mirrlyRegisterLocalThemeFile(getThemeFile(true), DARK_THEME_NAME);
        if (lightInfo == null || darkInfo == null) {
            return;
        }

        if (mode == MirrlyConfig.MONET_MODE_LIGHT) {
            if (Theme.selectedAutoNightType != Theme.AUTO_NIGHT_TYPE_NONE) {
                Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_NONE;
                Theme.saveAutoNightThemeConfig();
            }
            applyResolvedTheme(lightInfo, false, false, null);
        } else if (mode == MirrlyConfig.MONET_MODE_DARK) {
            if (Theme.selectedAutoNightType != Theme.AUTO_NIGHT_TYPE_NONE) {
                Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_NONE;
                Theme.saveAutoNightThemeConfig();
            }
            applyResolvedTheme(darkInfo, false, false, null);
        } else {
            Theme.mirrlySetDayNightThemes(lightInfo, darkInfo);
            saveDayNightKeys(lightInfo.getKey(), darkInfo.getKey());
            if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_NONE) {
                Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_SYSTEM;
            }
            Theme.saveAutoNightThemeConfig();

            final Theme.ThemeInfo target = shouldBeNight ? darkInfo : lightInfo;
            applyResolvedTheme(target, shouldBeNight, false, null);
        }
    }

    private static boolean isMonetStillBound(int mode) {
        Theme.ThemeInfo current = Theme.getCurrentTheme();
        if (mode == MirrlyConfig.MONET_MODE_LIGHT) {
            return current == null || LIGHT_THEME_NAME.equals(current.getKey());
        }
        if (mode == MirrlyConfig.MONET_MODE_DARK) {
            return current == null || DARK_THEME_NAME.equals(current.getKey());
        }
        if (mode == MirrlyConfig.MONET_MODE_AUTO) {
            Theme.ThemeInfo day = Theme.mirrlyGetDayTheme();
            Theme.ThemeInfo night = Theme.getCurrentNightTheme();
            boolean dayOk = day == null || LIGHT_THEME_NAME.equals(day.getKey());
            boolean nightOk = night == null || DARK_THEME_NAME.equals(night.getKey());
            return (current == null || isMonetTheme(current)) && (dayOk || nightOk);
        }
        return false;
    }

    private static void applyResolvedTheme(Theme.ThemeInfo target, boolean nightTheme, boolean animate, @Nullable View revealAnchor) {
        Theme.ThemeInfo current = Theme.getCurrentTheme();
        if (animate && current != target) {
            Theme.switchThemeWithReveal(target, nightTheme, -1, revealAnchor);
        } else {
            Theme.applyTheme(target, true, nightTheme);
        }
        if (!nightTheme) {
            saveCurrentThemeKey(target.getKey());
        }
    }

    private static void capturePreviousNonMonetThemes() {
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("mirrly_config", Activity.MODE_PRIVATE);
        SharedPreferences.Editor ed = prefs.edit();
        Theme.ThemeInfo day = Theme.mirrlyGetDayTheme();
        if (day == null) {
            day = Theme.getCurrentTheme();
        }
        if (day != null && !isMonetTheme(day)) {
            ed.putString(PREF_PREV_DAY_THEME, day.getKey());
        }
        Theme.ThemeInfo night = Theme.getCurrentNightTheme();
        if (night != null && !isMonetTheme(night)) {
            ed.putString(PREF_PREV_NIGHT_THEME, night.getKey());
        }
        ed.putInt(PREF_PREV_AUTO_NIGHT_TYPE, Theme.selectedAutoNightType);
        ed.apply();
    }

    private static void restorePreviousThemes(Context ctx, boolean animate, @Nullable View revealAnchor) {
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("mirrly_config", Activity.MODE_PRIVATE);
        String prevDayKey = prefs.getString(PREF_PREV_DAY_THEME, "Blue");
        String prevNightKey = prefs.getString(PREF_PREV_NIGHT_THEME, "Dark Blue");
        int prevAutoNightType = prefs.getInt(PREF_PREV_AUTO_NIGHT_TYPE, Theme.AUTO_NIGHT_TYPE_NONE);

        Theme.ThemeInfo dayInfo = resolveFallbackTheme(prevDayKey, "Blue");
        Theme.ThemeInfo nightInfo = resolveFallbackTheme(prevNightKey, "Dark Blue");

        Theme.mirrlySetDayNightThemes(dayInfo, nightInfo);
        Theme.selectedAutoNightType = prevAutoNightType;
        saveDayNightKeys(dayInfo.getKey(), nightInfo.getKey());
        Theme.saveAutoNightThemeConfig();

        boolean useNight;
        if (prevAutoNightType == Theme.AUTO_NIGHT_TYPE_NONE) {
            useNight = Theme.isCurrentThemeNight();
        } else if (prevAutoNightType == Theme.AUTO_NIGHT_TYPE_SYSTEM) {
            useNight = isSystemDark(ctx);
        } else {
            useNight = Theme.isCurrentThemeNight();
        }
        Theme.ThemeInfo target = useNight ? nightInfo : dayInfo;
        applyResolvedTheme(target, useNight && prevAutoNightType != Theme.AUTO_NIGHT_TYPE_NONE, animate, revealAnchor);
    }

    private static Theme.ThemeInfo resolveFallbackTheme(String preferredKey, String defaultKey) {
        Theme.ThemeInfo info = preferredKey != null ? Theme.getTheme(preferredKey) : null;
        if (info == null || isMonetTheme(info)) {
            info = Theme.getTheme(defaultKey);
        }
        if (info == null) {
            info = Theme.mirrlyGetDefaultTheme();
        }
        return info;
    }

    private static void saveDayNightKeys(String dayKey, String nightKey) {
        try {
            SharedPreferences themeConfig = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
            themeConfig.edit()
                    .putString("lastDayTheme", dayKey)
                    .putString("lastDarkTheme", nightKey)
                    .apply();
            saveCurrentThemeKey(dayKey);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private static void saveCurrentThemeKey(String key) {
        try {
            SharedPreferences mainPrefs = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            mainPrefs.edit().putString("theme", key).apply();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    @Nullable
    private static Theme.ThemeInfo buildAndRegisterTheme(
            Palette palette,
            boolean dark,
            boolean amoled,
            boolean outGradient,
            boolean themedWallpaper,
            boolean glassTint
    ) {
        File file = getThemeFile(dark);
        String content = generateAtThemeContent(palette, dark, amoled, outGradient, themedWallpaper, glassTint);
        if (!writeThemeFile(file, content)) {
            return null;
        }
        return Theme.mirrlyRegisterLocalThemeFile(file, dark ? DARK_THEME_NAME : LIGHT_THEME_NAME);
    }

    @NonNull
    private static File getThemeFile(boolean dark) {
        return new File(ApplicationLoader.getFilesDirFixed(), dark ? DARK_FILE_NAME : LIGHT_FILE_NAME);
    }

    private static boolean writeThemeFile(File file, String content) {
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp, false)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.getFD().sync();
        } catch (Throwable t) {
            FileLog.e(t);
            if (tmp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
            return false;
        }
        if (file.exists() && !file.delete()) {
            // Fall through to direct overwrite
        }
        if (tmp.renameTo(file)) {
            return true;
        }
        try (FileOutputStream fos = new FileOutputStream(file, false)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        } finally {
            if (tmp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }
    }

    @NonNull
    public static String generateAtThemeContent(
            @NonNull Palette p,
            boolean dark,
            boolean amoled,
            boolean outGradient,
            boolean themedWallpaper,
            boolean glassTint
    ) {
        StringBuilder sb = new StringBuilder(6400);
        if (dark) {
            emitDarkTheme(sb, p, amoled, outGradient, themedWallpaper, glassTint);
        } else {
            emitLightTheme(sb, p, outGradient, themedWallpaper, glassTint);
        }
        return sb.toString();
    }

    private static void emitLightTheme(StringBuilder sb, Palette p, boolean outGradient, boolean themedWallpaper, boolean glassTint) {
        int bgSurface = ColorUtils.blendARGB(p.neutral1_10, Color.WHITE, 0.25f);
        int bgGray = ColorUtils.blendARGB(p.neutral1_50, p.neutral2_50, 0.45f);
        int panelSurface = glassTint ? ColorUtils.blendARGB(bgSurface, p.accent1_50, 0.42f) : bgSurface;
        int glassTabsBg = glassTint ? ColorUtils.blendARGB(bgSurface, p.accent1_50, 0.52f) : bgSurface;
        int graySectionBg = p.neutral2_50;
        int accentPrimary = p.accent1_600;
        int accentDeep = p.accent1_700;
        int accentSoft = p.accent1_100;
        int accentPressed = p.accent1_700;
        int onAccent = contrastTextFor(accentPrimary);
        int textBlack = p.neutral1_900;
        int textGray = p.neutral2_600;
        int textGray2 = p.neutral2_500;
        int textHint = p.neutral2_400;
        int dividerColor = alpha(p.neutral2_200, 0x80);
        int ripple = alpha(p.neutral1_900, 0x10);
        int linkSelection = alpha(accentPrimary, 0x24);
        int redDestructive = 0xFFD93F3C;

        put(sb, "windowBackgroundWhite", bgSurface);
        put(sb, "windowBackgroundGray", bgGray);
        put(sb, "windowBackgroundGrayShadow", alpha(Color.BLACK, 0x10));
        put(sb, "windowBackgroundUnchecked", p.neutral1_100);
        put(sb, "windowBackgroundChecked", accentPrimary);
        put(sb, "windowBackgroundCheckText", onAccent);
        put(sb, "progressCircle", accentPrimary);
        put(sb, "listSelectorSDK21", ripple);
        put(sb, "emptyListPlaceholder", textGray2);
        put(sb, "divider", dividerColor);
        put(sb, "graySection", graySectionBg);
        put(sb, "key_graySectionText", textGray);

        put(sb, "windowBackgroundWhiteBlackText", textBlack);
        put(sb, "windowBackgroundWhiteGrayText", textGray);
        put(sb, "windowBackgroundWhiteGrayText2", textGray2);
        put(sb, "windowBackgroundWhiteGrayText3", textGray2);
        put(sb, "windowBackgroundWhiteGrayText4", textGray);
        put(sb, "windowBackgroundWhiteGrayText5", textGray2);
        put(sb, "windowBackgroundWhiteGrayText6", textGray);
        put(sb, "windowBackgroundWhiteGrayText7", textGray);
        put(sb, "windowBackgroundWhiteGrayText8", textGray2);
        put(sb, "windowBackgroundWhiteHintText", textHint);
        put(sb, "windowBackgroundWhiteValueText", accentPrimary);
        put(sb, "windowBackgroundWhiteLinkText", accentDeep);
        put(sb, "windowBackgroundWhiteLinkSelection", linkSelection);
        put(sb, "windowBackgroundWhiteBlueHeader", accentPrimary);
        put(sb, "windowBackgroundWhiteBlueText", accentPrimary);
        put(sb, "windowBackgroundWhiteBlueText2", accentPrimary);
        put(sb, "windowBackgroundWhiteBlueText3", accentPrimary);
        put(sb, "windowBackgroundWhiteBlueText4", accentPrimary);
        put(sb, "windowBackgroundWhiteBlueText5", accentPrimary);
        put(sb, "windowBackgroundWhiteBlueText6", accentPrimary);
        put(sb, "windowBackgroundWhiteBlueText7", accentPrimary);
        put(sb, "windowBackgroundWhiteBlueButton", accentPrimary);
        put(sb, "windowBackgroundWhiteBlueIcon", accentPrimary);
        put(sb, "windowBackgroundWhiteGrayIcon", p.neutral2_500);
        put(sb, "windowBackgroundWhiteGreenText", accentPrimary);
        put(sb, "windowBackgroundWhiteGreenText2", accentDeep);
        put(sb, "windowBackgroundWhiteInputField", p.neutral2_200);
        put(sb, "windowBackgroundWhiteInputFieldActivated", accentPrimary);

        put(sb, "switchTrack", p.neutral2_400);
        put(sb, "switchTrackChecked", accentPrimary);
        put(sb, "switchTrackBlue", p.neutral2_400);
        put(sb, "switchTrackBlueChecked", accentPrimary);
        put(sb, "switchTrackBlueThumb", Color.WHITE);
        put(sb, "switchTrackBlueThumbChecked", Color.WHITE);
        put(sb, "switchTrackBlueSelector", alpha(p.neutral2_500, 0x1C));
        put(sb, "switchTrackBlueSelectorChecked", alpha(accentPrimary, 0x24));
        put(sb, "switch2Track", p.neutral2_200);
        put(sb, "switch2TrackChecked", accentPrimary);
        put(sb, "checkbox", accentPrimary);
        put(sb, "checkboxDisabled", p.neutral2_200);
        put(sb, "checkboxCheck", onAccent);
        put(sb, "checkboxSquareBackground", accentPrimary);
        put(sb, "checkboxSquareCheck", onAccent);
        put(sb, "checkboxSquareUnchecked", p.neutral2_500);
        put(sb, "checkboxSquareDisabled", p.neutral2_200);
        put(sb, "radioBackground", p.neutral2_400);
        put(sb, "radioBackgroundChecked", accentPrimary);
        put(sb, "fastScrollActive", accentPrimary);
        put(sb, "fastScrollInactive", p.neutral2_200);
        put(sb, "fastScrollText", onAccent);
        put(sb, "text_RedRegular", redDestructive);
        put(sb, "text_RedBold", redDestructive);
        put(sb, "fill_RedNormal", redDestructive);
        put(sb, "fill_RedDark", 0xFFB82E2B);

        put(sb, "actionBarDefault", panelSurface);
        put(sb, "actionBarDefaultSelector", ripple);
        put(sb, "actionBarWhiteSelector", ripple);
        put(sb, "actionBarDefaultIcon", p.neutral1_700);
        put(sb, "actionBarDefaultTitle", textBlack);
        put(sb, "actionBarDefaultSubtitle", textGray);
        put(sb, "actionBarDefaultSearch", textBlack);
        put(sb, "actionBarDefaultSearchPlaceholder", textHint);
        put(sb, "actionBarDefaultSubmenuBackground", bgSurface);
        put(sb, "actionBarDefaultSubmenuItem", textBlack);
        put(sb, "actionBarDefaultSubmenuItemIcon", p.neutral2_600);
        put(sb, "actionBarDefaultSubmenuSeparator", bgGray);
        put(sb, "actionBarActionModeDefault", panelSurface);
        put(sb, "actionBarActionModeDefaultTop", bgGray);
        put(sb, "actionBarActionModeDefaultIcon", textBlack);
        put(sb, "actionBarActionModeDefaultSelector", ripple);
        put(sb, "actionBarActionModeReaction", p.accent1_100);
        put(sb, "actionBarActionModeReactionText", textBlack);
        put(sb, "actionBarActionModeReactionDot", accentPrimary);
        put(sb, "actionBarTabActiveText", accentPrimary);
        put(sb, "actionBarTabUnactiveText", textGray);
        put(sb, "actionBarTabLine", accentPrimary);
        put(sb, "actionBarTabSelector", ripple);
        put(sb, "actionBarDefaultArchived", bgGray);
        put(sb, "actionBarDefaultArchivedSelector", ripple);
        put(sb, "actionBarDefaultArchivedIcon", textBlack);
        put(sb, "actionBarDefaultArchivedTitle", textBlack);
        put(sb, "actionBarDefaultArchivedSearch", textBlack);
        put(sb, "actionBarDefaultSearchArchivedPlaceholder", textHint);
        put(sb, "actionBarBrowser", panelSurface);

        put(sb, "glass_targetMainTabs", glassTabsBg);
        put(sb, "glass_targetMainTopPanel", glassTabsBg);
        put(sb, "glass_tabSelected", accentPrimary);
        put(sb, "glass_tabSelectedText", accentDeep);
        put(sb, "glass_tabUnselected", textBlack);
        put(sb, "glass_defaultIcon", textGray);
        put(sb, "glass_defaultText", textGray);

        put(sb, "dialogBackground", bgSurface);
        put(sb, "dialogBackgroundGray", bgGray);
        put(sb, "dialogTextBlack", textBlack);
        put(sb, "dialogTextLink", accentDeep);
        put(sb, "dialogLinkSelection", linkSelection);
        put(sb, "dialogTextBlue", accentPrimary);
        put(sb, "dialogTextBlue2", accentPrimary);
        put(sb, "dialogTextBlue4", accentPrimary);
        put(sb, "dialogTextGray", textGray);
        put(sb, "dialogTextGray2", textGray2);
        put(sb, "dialogTextGray3", textGray2);
        put(sb, "dialogTextGray4", p.neutral2_200);
        put(sb, "dialogTextHint", textHint);
        put(sb, "dialogInputField", p.neutral2_200);
        put(sb, "dialogInputFieldActivated", accentPrimary);
        put(sb, "dialogCheckboxSquareBackground", accentPrimary);
        put(sb, "dialogCheckboxSquareCheck", onAccent);
        put(sb, "dialogCheckboxSquareUnchecked", p.neutral2_500);
        put(sb, "dialogCheckboxSquareDisabled", p.neutral2_200);
        put(sb, "dialogRoundCheckBox", accentPrimary);
        put(sb, "dialogRoundCheckBoxCheck", onAccent);
        put(sb, "dialogRadioBackground", p.neutral2_400);
        put(sb, "dialogRadioBackgroundChecked", accentPrimary);
        put(sb, "dialogLineProgress", accentPrimary);
        put(sb, "dialogLineProgressBackground", p.neutral2_100);
        put(sb, "dialogButton", accentPrimary);
        put(sb, "dialogButtonSelector", alpha(accentPrimary, 0x18));
        put(sb, "dialogIcon", p.neutral2_500);
        put(sb, "dialogGrayLine", dividerColor);
        put(sb, "dialogShadowLine", dividerColor);
        put(sb, "dialogTopBackground", accentPrimary);
        put(sb, "dialogFloatingButton", accentPrimary);
        put(sb, "dialogFloatingButtonPressed", accentPressed);
        put(sb, "dialogFloatingIcon", onAccent);
        put(sb, "dialogSearchBackground", bgGray);
        put(sb, "dialogSearchHint", textHint);
        put(sb, "dialogSearchIcon", textGray2);
        put(sb, "dialogSearchText", textBlack);
        put(sb, "dialogEmptyImage", textGray2);
        put(sb, "dialogEmptyText", textGray);
        put(sb, "dialogSwipeRemove", redDestructive);
        put(sb, "dialogReactionMentionBackground", alpha(accentPrimary, 0x26));

        put(sb, "chats_onlineCircle", accentPrimary);
        put(sb, "chats_unreadCounter", accentPrimary);
        put(sb, "chats_unreadCounterMuted", p.neutral2_200);
        put(sb, "chats_unreadCounterText", onAccent);
        put(sb, "chats_name", textBlack);
        put(sb, "chats_nameArchived", p.neutral1_700);
        put(sb, "chats_secretName", accentDeep);
        put(sb, "chats_secretIcon", accentPrimary);
        put(sb, "chats_pinnedIcon", p.neutral2_400);
        put(sb, "chats_pinnedOverlay", alpha(p.accent2_100, 0x35));
        put(sb, "chats_tabletSelectedOverlay", alpha(accentPrimary, 0x16));
        put(sb, "chats_archiveBackground", accentPrimary);
        put(sb, "chats_archivePinBackground", p.neutral2_200);
        put(sb, "chats_archiveIcon", onAccent);
        put(sb, "chats_archiveText", onAccent);
        put(sb, "chats_message", textGray);
        put(sb, "chats_messageArchived", textGray2);
        put(sb, "chats_message_threeLines", textGray);
        put(sb, "chats_draft", redDestructive);
        put(sb, "chats_nameMessage", accentDeep);
        put(sb, "chats_nameMessageArchived", p.neutral2_600);
        put(sb, "chats_nameMessage_threeLines", textBlack);
        put(sb, "chats_nameMessageArchived_threeLines", p.neutral1_700);
        put(sb, "chats_attachMessage", accentDeep);
        put(sb, "chats_actionMessage", accentDeep);
        put(sb, "chats_date", textGray2);
        put(sb, "chats_date_bold", accentPrimary);
        put(sb, "chats_sentCheck", accentPrimary);
        put(sb, "chats_sentReadCheck", accentPrimary);
        put(sb, "chats_sentClock", textGray2);
        put(sb, "chats_verifiedBackground", accentPrimary);
        put(sb, "chats_verifiedCheck", onAccent);
        put(sb, "chats_muteIcon", textHint);
        put(sb, "chats_mentionIcon", onAccent);
        put(sb, "chats_menuBackground", bgSurface);
        put(sb, "chats_menuItemText", textBlack);
        put(sb, "chats_menuItemCheck", accentPrimary);
        put(sb, "chats_menuItemIcon", p.neutral2_600);
        put(sb, "chats_menuName", onAccent);
        put(sb, "chats_menuPhone", alpha(onAccent, 0xCC));
        put(sb, "chats_menuPhoneCats", alpha(onAccent, 0xCC));
        put(sb, "chats_menuTopBackground", accentPrimary);
        put(sb, "chats_menuTopBackgroundCats", accentPrimary);
        put(sb, "chats_actionIcon", onAccent);
        put(sb, "chats_actionBackground", accentPrimary);
        put(sb, "chats_actionPressedBackground", accentPressed);
        put(sb, "chats_archivePullDownBackground", p.neutral2_200);
        put(sb, "chats_archivePullDownBackgroundActive", accentPrimary);
        put(sb, "chats_tabUnreadActiveBackground", accentPrimary);
        put(sb, "chats_tabUnreadUnactiveBackground", p.neutral2_200);

        int inBubble = Color.WHITE;
        int inBubbleSel = ColorUtils.blendARGB(Color.WHITE, accentSoft, 0.55f);
        int outBottom = accentPrimary;
        int outTop = outGradient ? ColorUtils.blendARGB(p.accent1_500, p.accent3_500, 0.35f) : outBottom;
        int outSelected = ColorUtils.blendARGB(outBottom, Color.BLACK, 0.14f);
        int outText = contrastTextFor(outBottom);
        int outSub = alpha(outText, 0xD0);
        int outMuted = alpha(outText, 0x99);

        put(sb, "chat_inBubble", inBubble);
        put(sb, "chat_inBubbleSelected", inBubbleSel);
        put(sb, "chat_inBubbleSelectedOverlay", alpha(accentPrimary, 0x16));
        put(sb, "chat_inBubbleShadow", alpha(p.neutral2_700, 0x35));
        put(sb, "chat_outBubble", outBottom);
        if (outGradient) {
            put(sb, "chat_outBubbleGradient", outTop);
        }
        put(sb, "chat_outBubbleSelected", outSelected);
        put(sb, "chat_outBubbleSelectedOverlay", alpha(Color.BLACK, 0x14));
        put(sb, "chat_outBubbleShadow", alpha(p.accent1_900, 0x3A));

        put(sb, "chat_messageTextIn", textBlack);
        put(sb, "chat_messageTextOut", outText);
        put(sb, "chat_messageLinkIn", accentDeep);
        put(sb, "chat_messageLinkOut", outText);
        put(sb, "chat_inQuote", accentPrimary);
        put(sb, "chat_outQuote", outText);
        put(sb, "chat_inReplyLine", accentPrimary);
        put(sb, "chat_outReplyLine", alpha(outText, 0xAA));
        put(sb, "chat_outReplyLine2", alpha(outText, 0xAA));
        put(sb, "chat_inReplyNameText", accentDeep);
        put(sb, "chat_outReplyNameText", outText);
        put(sb, "chat_inReplyMessageText", textBlack);
        put(sb, "chat_outReplyMessageText", outSub);
        put(sb, "chat_inReplyMediaMessageText", textGray);
        put(sb, "chat_outReplyMediaMessageText", outSub);
        put(sb, "chat_inReplyMediaMessageSelectedText", textGray);
        put(sb, "chat_outReplyMediaMessageSelectedText", outSub);
        put(sb, "chat_inForwardedNameText", accentDeep);
        put(sb, "chat_outForwardedNameText", outText);
        put(sb, "chat_inViaBotNameText", accentDeep);
        put(sb, "chat_outViaBotNameText", outText);
        put(sb, "chat_inSiteNameText", accentDeep);
        put(sb, "chat_outSiteNameText", outText);
        put(sb, "chat_inPreviewLine", accentPrimary);
        put(sb, "chat_outPreviewLine", alpha(outText, 0xAA));
        put(sb, "chat_inTimeText", textGray2);
        put(sb, "chat_inTimeSelectedText", accentDeep);
        put(sb, "chat_outTimeText", outSub);
        put(sb, "chat_outTimeSelectedText", outText);
        put(sb, "chat_inViews", textGray2);
        put(sb, "chat_inViewsSelected", accentDeep);
        put(sb, "chat_outViews", outSub);
        put(sb, "chat_outViewsSelected", outText);
        put(sb, "chat_inMenu", p.neutral2_400);
        put(sb, "chat_inMenuSelected", accentDeep);
        put(sb, "chat_outMenu", outSub);
        put(sb, "chat_outMenuSelected", outText);
        put(sb, "chat_inInstant", accentDeep);
        put(sb, "chat_inInstantSelected", accentDeep);
        put(sb, "chat_outInstant", outText);
        put(sb, "chat_outInstantSelected", outText);
        put(sb, "chat_outSentCheck", outSub);
        put(sb, "chat_outSentCheckSelected", outText);
        put(sb, "chat_outSentCheckRead", outText);
        put(sb, "chat_outSentCheckReadSelected", outText);
        put(sb, "chat_outSentClock", outSub);
        put(sb, "chat_outSentClockSelected", outText);
        put(sb, "chat_inSentClock", textGray2);
        put(sb, "chat_inSentClockSelected", accentDeep);
        put(sb, "chat_selectedBackground", alpha(accentPrimary, 0x22));
        put(sb, "chat_textSelectBackground", alpha(accentPrimary, 0x2E));
        put(sb, "chat_linkSelectBackground", alpha(accentPrimary, 0x24));
        put(sb, "chat_outLinkSelectBackground", alpha(outText, 0x24));
        put(sb, "chat_inCodeBackground", alpha(p.accent2_200, 0x45));
        put(sb, "chat_outCodeBackground", alpha(outText, 0x1A));
        put(sb, "chat_inPollCorrectAnswer", accentPrimary);
        put(sb, "chat_outPollCorrectAnswer", outText);
        put(sb, "chat_outReactionButtonBackground", alpha(outText, 0x22));
        put(sb, "chat_inReactionButtonBackground", alpha(accentPrimary, 0x1A));
        put(sb, "chat_outReactionButtonText", outText);
        put(sb, "chat_inReactionButtonText", accentDeep);
        put(sb, "chat_inReactionButtonTextSelected", onAccent);
        put(sb, "chat_outReactionButtonTextSelected", accentDeep);

        put(sb, "chat_inVoiceSeekbar", alpha(accentPrimary, 0x3D));
        put(sb, "chat_inVoiceSeekbarFill", accentPrimary);
        put(sb, "chat_inVoiceSeekbarSelected", accentDeep);
        put(sb, "chat_outVoiceSeekbar", outMuted);
        put(sb, "chat_outVoiceSeekbarFill", outText);
        put(sb, "chat_outVoiceSeekbarSelected", outText);
        put(sb, "chat_inAudioSeekbar", alpha(accentPrimary, 0x3D));
        put(sb, "chat_inAudioSeekbarFill", accentPrimary);
        put(sb, "chat_outAudioSeekbar", outMuted);
        put(sb, "chat_outAudioSeekbarFill", outText);
        put(sb, "chat_inAudioProgress", Color.WHITE);
        put(sb, "chat_outAudioProgress", outBottom);
        put(sb, "chat_inFileProgress", p.neutral2_100);
        put(sb, "chat_outFileProgress", outBottom);
        put(sb, "chat_inFileBackground", p.accent1_50);
        put(sb, "chat_outFileBackground", alpha(outText, 0x20));
        put(sb, "chat_inFileNameText", accentDeep);
        put(sb, "chat_outFileNameText", outText);
        put(sb, "chat_inFileInfoText", textGray2);
        put(sb, "chat_outFileInfoText", outSub);
        put(sb, "chat_inMediaIcon", onAccent);
        put(sb, "chat_outMediaIcon", outBottom);
        put(sb, "chat_inMediaIconSelected", onAccent);
        put(sb, "chat_outMediaIconSelected", outSelected);
        put(sb, "chat_inAudioDurationText", textGray2);
        put(sb, "chat_outAudioDurationText", outSub);
        put(sb, "chat_inAudioTitleText", textBlack);
        put(sb, "chat_outAudioTitleText", outText);
        put(sb, "chat_inAudioPerfomerText", textBlack);
        put(sb, "chat_outAudioPerfomerText", outText);
        put(sb, "chat_inLoader", accentPrimary);
        put(sb, "chat_inLoaderSelected", accentDeep);
        put(sb, "chat_outLoader", outText);
        put(sb, "chat_outLoaderSelected", outText);
        put(sb, "chat_inContactBackground", accentPrimary);
        put(sb, "chat_inContactIcon", onAccent);
        put(sb, "chat_outContactBackground", alpha(outText, 0x26));
        put(sb, "chat_outContactIcon", outText);

        put(sb, "chat_status", accentPrimary);
        put(sb, "chat_addContact", accentPrimary);
        put(sb, "chat_messagePanelBackground", panelSurface);
        put(sb, "chat_messagePanelShadow", alpha(Color.BLACK, 0x16));
        put(sb, "chat_messagePanelText", textBlack);
        put(sb, "chat_messagePanelHint", textHint);
        put(sb, "chat_messagePanelCursor", accentPrimary);
        put(sb, "chat_messagePanelIcons", p.neutral2_500);
        put(sb, "chat_messagePanelSend", accentPrimary);
        put(sb, "chat_messagePanelVoicePressed", onAccent);
        put(sb, "chat_messagePanelVoiceBackground", accentPrimary);
        put(sb, "chat_topPanelBackground", panelSurface);
        put(sb, "chat_topPanelClose", p.neutral2_500);
        put(sb, "chat_topPanelLine", accentPrimary);
        put(sb, "chat_topPanelTitle", accentDeep);
        put(sb, "chat_topPanelMessage", textGray);
        put(sb, "chat_replyPanelIcons", accentPrimary);
        put(sb, "chat_replyPanelClose", p.neutral2_500);
        put(sb, "chat_replyPanelName", accentDeep);
        put(sb, "chat_replyPanelLine", accentPrimary);
        put(sb, "chat_searchPanelIcons", accentPrimary);
        put(sb, "chat_searchPanelText", accentDeep);
        put(sb, "chat_emojiPanelBackground", bgGray);
        put(sb, "chat_emojiSearchBackground", bgSurface);
        put(sb, "chat_emojiSearchIcon", textHint);
        put(sb, "chat_emojiPanelShadowLine", dividerColor);
        put(sb, "chat_emojiPanelEmptyText", textGray2);
        put(sb, "chat_emojiPanelIcon", p.neutral2_500);
        put(sb, "chat_emojiBottomPanelIcon", p.neutral2_600);
        put(sb, "chat_emojiPanelIconSelected", accentPrimary);
        put(sb, "chat_emojiPanelStickerPackSelector", alpha(accentPrimary, 0x1E));
        put(sb, "chat_emojiPanelStickerPackSelectorLine", accentPrimary);
        put(sb, "chat_emojiPanelBackspace", p.neutral2_500);
        put(sb, "chat_emojiPanelTrendingTitle", textBlack);
        put(sb, "chat_emojiPanelStickerSetName", textGray);
        put(sb, "chat_emojiPanelStickerSetNameHighlight", accentPrimary);
        put(sb, "chat_emojiPanelStickerSetNameIcon", p.neutral2_400);
        put(sb, "chat_emojiPanelNewTrending", accentPrimary);
        put(sb, "chat_emojiPanelTrendingDescription", textGray2);

        int[] wall = computeWallpaperColors(p, false, false, themedWallpaper);
        if (themedWallpaper) {
            put(sb, "chat_wallpaper", wall[0]);
            put(sb, "chat_wallpaper_gradient_to", wall[1]);
            put(sb, "key_chat_wallpaper_gradient_to2", wall[2]);
            put(sb, "key_chat_wallpaper_gradient_to3", wall[3]);
            put(sb, "chat_wallpaper_gradient_rotation", 45);
        } else {
            put(sb, "chat_wallpaper", wall[0]);
        }
        put(sb, "chat_serviceBackground", alpha(ColorUtils.blendARGB(p.neutral2_200, p.accent1_200, 0.35f), 0xA8));
        put(sb, "chat_serviceBackgroundSelected", alpha(ColorUtils.blendARGB(p.neutral2_200, p.accent1_200, 0.5f), 0xCC));
        put(sb, "chat_serviceBackgroundSelector", alpha(accentPrimary, 0x22));
        put(sb, "chat_serviceText", textBlack);
        put(sb, "chat_serviceLink", accentDeep);
        put(sb, "chat_serviceIcon", textBlack);

        put(sb, "avatar_backgroundInProfileBlue", accentPrimary);
        put(sb, "avatar_backgroundActionBarBlue", accentPrimary);
        put(sb, "avatar_actionBarSelectorBlue", accentPressed);
        put(sb, "avatar_actionBarIconBlue", onAccent);
        put(sb, "avatar_subtitleInProfileBlue", alpha(onAccent, 0xCC));
        put(sb, "avatar_backgroundSaved", accentPrimary);
        put(sb, "avatar_background2Saved", p.accent3_500);
        put(sb, "profile_creatorIcon", accentPrimary);
        put(sb, "profile_title", textBlack);
        put(sb, "profile_actionIcon", onAccent);
        put(sb, "profile_actionBackground", accentPrimary);
        put(sb, "profile_actionPressedBackground", accentPressed);
        put(sb, "profile_verifiedBackground", accentPrimary);
        put(sb, "profile_verifiedCheck", onAccent);
        put(sb, "profile_status", alpha(accentPrimary, 0xCC));
        put(sb, "profile_tabText", textGray);
        put(sb, "profile_tabSelectedText", accentPrimary);
        put(sb, "profile_tabSelectedLine", accentPrimary);
        put(sb, "profile_tabSelector", ripple);
        put(sb, "featuredStickers_addButton", accentPrimary);
        put(sb, "featuredStickers_addButton2", accentPrimary);
        put(sb, "featuredStickers_addButtonPressed", accentPressed);
        put(sb, "featuredStickers_buttonText", onAccent);
        put(sb, "featuredStickers_unread", accentPrimary);
        put(sb, "featuredStickers_addedIcon", accentPrimary);
        put(sb, "featuredStickers_buttonProgress", onAccent);
        put(sb, "inappPlayerBackground", bgSurface);
        put(sb, "inappPlayerTitle", textBlack);
        put(sb, "inappPlayerPerformer", textGray);
        put(sb, "inappPlayerPlayPause", accentPrimary);
        put(sb, "inappPlayerClose", p.neutral2_500);
        put(sb, "player_background", bgSurface);
        put(sb, "player_time", textGray2);
        put(sb, "player_progressBackground", p.neutral2_100);
        put(sb, "key_player_progressCachedBackground", p.neutral2_200);
        put(sb, "player_progress", accentPrimary);
        put(sb, "player_button", p.neutral1_700);
        put(sb, "player_buttonActive", accentPrimary);
        put(sb, "undo_background", p.neutral1_900);
        put(sb, "undo_cancelColor", p.accent1_200);
        put(sb, "undo_infoColor", Color.WHITE);
        put(sb, "stories_circle1", accentPrimary);
        put(sb, "stories_circle2", p.accent3_500);
        put(sb, "stories_circle_dialog1", accentPrimary);
        put(sb, "stories_circle_dialog2", p.accent3_500);

        emitSupplementalThemeColors(
                sb, p, false,
                bgSurface, bgGray, panelSurface,
                accentPrimary, accentPrimary, accentDeep, onAccent,
                textBlack, textGray, textGray2, textHint,
                dividerColor, ripple, redDestructive,
                inBubble, inBubbleSel, outSelected, outText, outSub, outMuted, outText
        );
    }

    private static void emitDarkTheme(
            StringBuilder sb,
            Palette p,
            boolean amoled,
            boolean outGradient,
            boolean themedWallpaper,
            boolean glassTint
    ) {
        int bgSurface = amoled ? Color.BLACK : ColorUtils.blendARGB(p.neutral1_900, Color.BLACK, 0.16f);
        int bgGray = amoled ? 0xFF0A0A0D : ColorUtils.blendARGB(p.neutral1_900, Color.BLACK, 0.45f);
        int bgElevated = amoled
                ? ColorUtils.blendARGB(p.neutral1_900, Color.BLACK, 0.50f)
                : ColorUtils.blendARGB(p.neutral1_800, p.neutral2_900, 0.45f);
        int actionBarBg = amoled
                ? (glassTint ? ColorUtils.blendARGB(Color.BLACK, p.accent1_900, 0.18f) : Color.BLACK)
                : (glassTint ? ColorUtils.blendARGB(bgSurface, p.accent2_900, 0.30f) : ColorUtils.blendARGB(bgSurface, p.neutral1_800, 0.35f));
        int glassTabsBg = amoled
                ? (glassTint ? ColorUtils.blendARGB(0xFF08080B, p.accent1_900, 0.24f) : 0xFF0A0A0D)
                : (glassTint ? ColorUtils.blendARGB(bgSurface, p.accent2_900, 0.28f) : bgSurface);

        int accentText = p.accent1_200;
        int accentFill = p.accent1_300;
        int accentFillPressed = p.accent1_200;
        int onAccentFill = contrastTextFor(accentFill);

        int textWhite = p.neutral1_50;
        int textGray = p.neutral2_400;
        int textGray2 = p.neutral2_500;
        int textHint = p.neutral2_600;
        int dividerColor = amoled ? alpha(p.neutral2_700, 0x55) : alpha(p.neutral2_700, 0x70);
        int ripple = alpha(Color.WHITE, 0x12);
        int linkSelection = alpha(accentText, 0x26);
        int redDestructive = 0xFFFF6B66;

        put(sb, "windowBackgroundWhite", bgSurface);
        put(sb, "windowBackgroundGray", bgGray);
        put(sb, "windowBackgroundGrayShadow", alpha(Color.BLACK, 0x35));
        put(sb, "windowBackgroundUnchecked", bgElevated);
        put(sb, "windowBackgroundChecked", accentFill);
        put(sb, "windowBackgroundCheckText", onAccentFill);
        put(sb, "progressCircle", accentText);
        put(sb, "listSelectorSDK21", ripple);
        put(sb, "emptyListPlaceholder", textGray2);
        put(sb, "divider", dividerColor);
        put(sb, "graySection", bgElevated);
        put(sb, "key_graySectionText", textGray);

        put(sb, "windowBackgroundWhiteBlackText", textWhite);
        put(sb, "windowBackgroundWhiteGrayText", textGray);
        put(sb, "windowBackgroundWhiteGrayText2", textGray2);
        put(sb, "windowBackgroundWhiteGrayText3", textGray2);
        put(sb, "windowBackgroundWhiteGrayText4", textGray);
        put(sb, "windowBackgroundWhiteGrayText5", textGray2);
        put(sb, "windowBackgroundWhiteGrayText6", textGray);
        put(sb, "windowBackgroundWhiteGrayText7", textGray);
        put(sb, "windowBackgroundWhiteGrayText8", textGray2);
        put(sb, "windowBackgroundWhiteHintText", textHint);
        put(sb, "windowBackgroundWhiteValueText", accentText);
        put(sb, "windowBackgroundWhiteLinkText", accentText);
        put(sb, "windowBackgroundWhiteLinkSelection", linkSelection);
        put(sb, "windowBackgroundWhiteBlueHeader", accentText);
        put(sb, "windowBackgroundWhiteBlueText", accentText);
        put(sb, "windowBackgroundWhiteBlueText2", accentText);
        put(sb, "windowBackgroundWhiteBlueText3", accentText);
        put(sb, "windowBackgroundWhiteBlueText4", accentText);
        put(sb, "windowBackgroundWhiteBlueText5", accentText);
        put(sb, "windowBackgroundWhiteBlueText6", accentText);
        put(sb, "windowBackgroundWhiteBlueText7", accentText);
        put(sb, "windowBackgroundWhiteBlueButton", accentText);
        put(sb, "windowBackgroundWhiteBlueIcon", accentText);
        put(sb, "windowBackgroundWhiteGrayIcon", textGray);
        put(sb, "windowBackgroundWhiteGreenText", accentText);
        put(sb, "windowBackgroundWhiteGreenText2", accentText);
        put(sb, "windowBackgroundWhiteInputField", p.neutral2_700);
        put(sb, "windowBackgroundWhiteInputFieldActivated", accentText);

        put(sb, "switchTrack", p.neutral2_700);
        put(sb, "switchTrackChecked", accentFill);
        put(sb, "switchTrackBlue", p.neutral2_700);
        put(sb, "switchTrackBlueChecked", accentFill);
        put(sb, "switchTrackBlueThumb", bgSurface);
        put(sb, "switchTrackBlueThumbChecked", onAccentFill);
        put(sb, "switchTrackBlueSelector", alpha(Color.WHITE, 0x16));
        put(sb, "switchTrackBlueSelectorChecked", alpha(accentFill, 0x26));
        put(sb, "switch2Track", p.neutral2_700);
        put(sb, "switch2TrackChecked", accentFill);
        put(sb, "checkbox", accentFill);
        put(sb, "checkboxDisabled", p.neutral2_700);
        put(sb, "checkboxCheck", onAccentFill);
        put(sb, "checkboxSquareBackground", accentFill);
        put(sb, "checkboxSquareCheck", onAccentFill);
        put(sb, "checkboxSquareUnchecked", textGray);
        put(sb, "checkboxSquareDisabled", p.neutral2_700);
        put(sb, "radioBackground", p.neutral2_500);
        put(sb, "radioBackgroundChecked", accentText);
        put(sb, "fastScrollActive", accentFill);
        put(sb, "fastScrollInactive", p.neutral2_700);
        put(sb, "fastScrollText", onAccentFill);
        put(sb, "text_RedRegular", redDestructive);
        put(sb, "text_RedBold", redDestructive);
        put(sb, "fill_RedNormal", 0xFFD94A47);
        put(sb, "fill_RedDark", 0xFFB03431);

        put(sb, "actionBarDefault", actionBarBg);
        put(sb, "actionBarDefaultSelector", ripple);
        put(sb, "actionBarWhiteSelector", ripple);
        put(sb, "actionBarDefaultIcon", textWhite);
        put(sb, "actionBarDefaultTitle", textWhite);
        put(sb, "actionBarDefaultSubtitle", textGray);
        put(sb, "actionBarDefaultSearch", textWhite);
        put(sb, "actionBarDefaultSearchPlaceholder", textHint);
        put(sb, "actionBarDefaultSubmenuBackground", bgElevated);
        put(sb, "actionBarDefaultSubmenuItem", textWhite);
        put(sb, "actionBarDefaultSubmenuItemIcon", textGray);
        put(sb, "actionBarDefaultSubmenuSeparator", bgGray);
        put(sb, "actionBarActionModeDefault", bgElevated);
        put(sb, "actionBarActionModeDefaultTop", bgGray);
        put(sb, "actionBarActionModeDefaultIcon", textWhite);
        put(sb, "actionBarActionModeDefaultSelector", ripple);
        put(sb, "actionBarActionModeReaction", p.accent1_800);
        put(sb, "actionBarActionModeReactionText", textWhite);
        put(sb, "actionBarActionModeReactionDot", accentText);
        put(sb, "actionBarTabActiveText", accentText);
        put(sb, "actionBarTabUnactiveText", textGray);
        put(sb, "actionBarTabLine", accentText);
        put(sb, "actionBarTabSelector", ripple);
        put(sb, "actionBarDefaultArchived", bgGray);
        put(sb, "actionBarDefaultArchivedSelector", ripple);
        put(sb, "actionBarDefaultArchivedIcon", textWhite);
        put(sb, "actionBarDefaultArchivedTitle", textWhite);
        put(sb, "actionBarDefaultArchivedSearch", textWhite);
        put(sb, "actionBarDefaultSearchArchivedPlaceholder", textHint);
        put(sb, "actionBarBrowser", actionBarBg);

        put(sb, "glass_targetMainTabs", glassTabsBg);
        put(sb, "glass_targetMainTopPanel", glassTabsBg);
        put(sb, "glass_tabSelected", accentFill);
        put(sb, "glass_tabSelectedText", accentText);
        put(sb, "glass_tabUnselected", textWhite);
        put(sb, "glass_defaultIcon", textGray);
        put(sb, "glass_defaultText", textGray);

        int dialogBg = amoled ? 0xFF0E0E12 : bgSurface;
        put(sb, "dialogBackground", dialogBg);
        put(sb, "dialogBackgroundGray", bgGray);
        put(sb, "dialogTextBlack", textWhite);
        put(sb, "dialogTextLink", accentText);
        put(sb, "dialogLinkSelection", linkSelection);
        put(sb, "dialogTextBlue", accentText);
        put(sb, "dialogTextBlue2", accentText);
        put(sb, "dialogTextBlue4", accentText);
        put(sb, "dialogTextGray", textGray);
        put(sb, "dialogTextGray2", textGray2);
        put(sb, "dialogTextGray3", textGray2);
        put(sb, "dialogTextGray4", p.neutral2_700);
        put(sb, "dialogTextHint", textHint);
        put(sb, "dialogInputField", p.neutral2_700);
        put(sb, "dialogInputFieldActivated", accentText);
        put(sb, "dialogCheckboxSquareBackground", accentFill);
        put(sb, "dialogCheckboxSquareCheck", onAccentFill);
        put(sb, "dialogCheckboxSquareUnchecked", textGray);
        put(sb, "dialogCheckboxSquareDisabled", p.neutral2_700);
        put(sb, "dialogRoundCheckBox", accentFill);
        put(sb, "dialogRoundCheckBoxCheck", onAccentFill);
        put(sb, "dialogRadioBackground", p.neutral2_500);
        put(sb, "dialogRadioBackgroundChecked", accentText);
        put(sb, "dialogLineProgress", accentText);
        put(sb, "dialogLineProgressBackground", p.neutral2_800);
        put(sb, "dialogButton", accentText);
        put(sb, "dialogButtonSelector", alpha(accentText, 0x1E));
        put(sb, "dialogIcon", textGray);
        put(sb, "dialogGrayLine", dividerColor);
        put(sb, "dialogShadowLine", dividerColor);
        put(sb, "dialogTopBackground", accentFill);
        put(sb, "dialogFloatingButton", accentFill);
        put(sb, "dialogFloatingButtonPressed", accentFillPressed);
        put(sb, "dialogFloatingIcon", onAccentFill);
        put(sb, "dialogSearchBackground", bgElevated);
        put(sb, "dialogSearchHint", textHint);
        put(sb, "dialogSearchIcon", textGray);
        put(sb, "dialogSearchText", textWhite);
        put(sb, "dialogEmptyImage", textGray2);
        put(sb, "dialogEmptyText", textGray);
        put(sb, "dialogSwipeRemove", redDestructive);
        put(sb, "dialogReactionMentionBackground", alpha(accentText, 0x26));

        put(sb, "chats_onlineCircle", accentFill);
        put(sb, "chats_unreadCounter", accentFill);
        put(sb, "chats_unreadCounterMuted", p.neutral2_700);
        put(sb, "chats_unreadCounterText", onAccentFill);
        put(sb, "chats_name", textWhite);
        put(sb, "chats_nameArchived", textWhite);
        put(sb, "chats_secretName", accentText);
        put(sb, "chats_secretIcon", accentText);
        put(sb, "chats_pinnedIcon", textGray2);
        put(sb, "chats_pinnedOverlay", alpha(p.accent2_800, 0x35));
        put(sb, "chats_tabletSelectedOverlay", alpha(accentFill, 0x1E));
        put(sb, "chats_archiveBackground", p.accent2_700);
        put(sb, "chats_archivePinBackground", p.neutral2_700);
        put(sb, "chats_archiveIcon", textWhite);
        put(sb, "chats_archiveText", textWhite);
        put(sb, "chats_message", textGray);
        put(sb, "chats_messageArchived", textGray2);
        put(sb, "chats_message_threeLines", textGray);
        put(sb, "chats_draft", redDestructive);
        put(sb, "chats_nameMessage", textWhite);
        put(sb, "chats_nameMessageArchived", textGray);
        put(sb, "chats_nameMessage_threeLines", textWhite);
        put(sb, "chats_nameMessageArchived_threeLines", textWhite);
        put(sb, "chats_attachMessage", accentText);
        put(sb, "chats_actionMessage", accentText);
        put(sb, "chats_date", textGray2);
        put(sb, "chats_date_bold", accentText);
        put(sb, "chats_sentCheck", accentText);
        put(sb, "chats_sentReadCheck", accentText);
        put(sb, "chats_sentClock", textGray2);
        put(sb, "chats_verifiedBackground", accentFill);
        put(sb, "chats_verifiedCheck", onAccentFill);
        put(sb, "chats_muteIcon", textHint);
        put(sb, "chats_mentionIcon", onAccentFill);
        put(sb, "chats_menuBackground", bgSurface);
        put(sb, "chats_menuItemText", textWhite);
        put(sb, "chats_menuItemCheck", accentText);
        put(sb, "chats_menuItemIcon", textGray);
        put(sb, "chats_menuName", textWhite);
        put(sb, "chats_menuPhone", textGray);
        put(sb, "chats_menuPhoneCats", textGray);
        put(sb, "chats_menuTopBackground", amoled ? Color.BLACK : p.accent2_900);
        put(sb, "chats_menuTopBackgroundCats", amoled ? Color.BLACK : p.accent2_900);
        put(sb, "chats_actionIcon", onAccentFill);
        put(sb, "chats_actionBackground", accentFill);
        put(sb, "chats_actionPressedBackground", accentFillPressed);
        put(sb, "chats_archivePullDownBackground", p.neutral2_800);
        put(sb, "chats_archivePullDownBackgroundActive", accentFill);
        put(sb, "chats_tabUnreadActiveBackground", accentFill);
        put(sb, "chats_tabUnreadUnactiveBackground", p.neutral2_700);

        int inBubble = amoled
                ? ColorUtils.blendARGB(p.neutral1_900, p.neutral1_800, 0.42f)
                : ColorUtils.blendARGB(p.neutral1_800, p.neutral2_800, 0.35f);
        int inBubbleSel = ColorUtils.blendARGB(inBubble, accentFill, 0.20f);
        int outBottom = ColorUtils.blendARGB(p.accent1_700, p.neutral1_800, 0.14f);
        int outTop = outGradient ? ColorUtils.blendARGB(p.accent1_600, p.accent3_700, 0.30f) : outBottom;
        int outSelected = ColorUtils.blendARGB(outBottom, Color.WHITE, 0.12f);
        int outText = contrastTextFor(outBottom);
        int outSub = alpha(outText, 0xCC);
        int outMuted = alpha(outText, 0x8C);
        int outAccent = p.accent1_100;

        put(sb, "chat_inBubble", inBubble);
        put(sb, "chat_inBubbleSelected", inBubbleSel);
        put(sb, "chat_inBubbleSelectedOverlay", alpha(accentFill, 0x1E));
        put(sb, "chat_inBubbleShadow", alpha(Color.BLACK, 0x45));
        put(sb, "chat_outBubble", outBottom);
        if (outGradient) {
            put(sb, "chat_outBubbleGradient", outTop);
        }
        put(sb, "chat_outBubbleSelected", outSelected);
        put(sb, "chat_outBubbleSelectedOverlay", alpha(Color.WHITE, 0x16));
        put(sb, "chat_outBubbleShadow", alpha(Color.BLACK, 0x45));

        put(sb, "chat_messageTextIn", textWhite);
        put(sb, "chat_messageTextOut", outText);
        put(sb, "chat_messageLinkIn", accentText);
        put(sb, "chat_messageLinkOut", outAccent);
        put(sb, "chat_inQuote", accentText);
        put(sb, "chat_outQuote", outAccent);
        put(sb, "chat_inReplyLine", accentText);
        put(sb, "chat_outReplyLine", outAccent);
        put(sb, "chat_outReplyLine2", outAccent);
        put(sb, "chat_inReplyNameText", accentText);
        put(sb, "chat_outReplyNameText", outAccent);
        put(sb, "chat_inReplyMessageText", textWhite);
        put(sb, "chat_outReplyMessageText", outSub);
        put(sb, "chat_inReplyMediaMessageText", textGray);
        put(sb, "chat_outReplyMediaMessageText", outSub);
        put(sb, "chat_inReplyMediaMessageSelectedText", textGray);
        put(sb, "chat_outReplyMediaMessageSelectedText", outSub);
        put(sb, "chat_inForwardedNameText", accentText);
        put(sb, "chat_outForwardedNameText", outAccent);
        put(sb, "chat_inViaBotNameText", accentText);
        put(sb, "chat_outViaBotNameText", outAccent);
        put(sb, "chat_inSiteNameText", accentText);
        put(sb, "chat_outSiteNameText", outAccent);
        put(sb, "chat_inPreviewLine", accentText);
        put(sb, "chat_outPreviewLine", outAccent);
        put(sb, "chat_inTimeText", textGray);
        put(sb, "chat_inTimeSelectedText", accentText);
        put(sb, "chat_outTimeText", outSub);
        put(sb, "chat_outTimeSelectedText", outText);
        put(sb, "chat_inViews", textGray);
        put(sb, "chat_inViewsSelected", accentText);
        put(sb, "chat_outViews", outSub);
        put(sb, "chat_outViewsSelected", outText);
        put(sb, "chat_inMenu", textGray);
        put(sb, "chat_inMenuSelected", accentText);
        put(sb, "chat_outMenu", outSub);
        put(sb, "chat_outMenuSelected", outText);
        put(sb, "chat_inInstant", accentText);
        put(sb, "chat_inInstantSelected", accentText);
        put(sb, "chat_outInstant", outAccent);
        put(sb, "chat_outInstantSelected", outAccent);
        put(sb, "chat_outSentCheck", outSub);
        put(sb, "chat_outSentCheckSelected", outText);
        put(sb, "chat_outSentCheckRead", outAccent);
        put(sb, "chat_outSentCheckReadSelected", outText);
        put(sb, "chat_outSentClock", outSub);
        put(sb, "chat_outSentClockSelected", outText);
        put(sb, "chat_inSentClock", textGray);
        put(sb, "chat_inSentClockSelected", accentText);
        put(sb, "chat_selectedBackground", alpha(accentFill, 0x24));
        put(sb, "chat_textSelectBackground", alpha(accentFill, 0x33));
        put(sb, "chat_linkSelectBackground", alpha(accentFill, 0x26));
        put(sb, "chat_outLinkSelectBackground", alpha(outText, 0x24));
        put(sb, "chat_inCodeBackground", alpha(p.neutral2_600, 0x35));
        put(sb, "chat_outCodeBackground", alpha(outText, 0x1A));
        put(sb, "chat_inPollCorrectAnswer", accentText);
        put(sb, "chat_outPollCorrectAnswer", outAccent);
        put(sb, "chat_outReactionButtonBackground", alpha(outText, 0x22));
        put(sb, "chat_inReactionButtonBackground", alpha(accentFill, 0x20));
        put(sb, "chat_outReactionButtonText", outText);
        put(sb, "chat_inReactionButtonText", accentText);
        put(sb, "chat_inReactionButtonTextSelected", onAccentFill);
        put(sb, "chat_outReactionButtonTextSelected", outBottom);

        put(sb, "chat_inVoiceSeekbar", alpha(accentText, 0x45));
        put(sb, "chat_inVoiceSeekbarFill", accentText);
        put(sb, "chat_inVoiceSeekbarSelected", accentText);
        put(sb, "chat_outVoiceSeekbar", outMuted);
        put(sb, "chat_outVoiceSeekbarFill", outText);
        put(sb, "chat_outVoiceSeekbarSelected", outText);
        put(sb, "chat_inAudioSeekbar", alpha(accentText, 0x45));
        put(sb, "chat_inAudioSeekbarFill", accentText);
        put(sb, "chat_outAudioSeekbar", outMuted);
        put(sb, "chat_outAudioSeekbarFill", outText);
        put(sb, "chat_inAudioProgress", inBubble);
        put(sb, "chat_outAudioProgress", outBottom);
        put(sb, "chat_inFileProgress", p.neutral2_700);
        put(sb, "chat_outFileProgress", outBottom);
        put(sb, "chat_inFileBackground", p.accent2_800);
        put(sb, "chat_outFileBackground", alpha(outText, 0x22));
        put(sb, "chat_inFileNameText", accentText);
        put(sb, "chat_outFileNameText", outText);
        put(sb, "chat_inFileInfoText", textGray);
        put(sb, "chat_outFileInfoText", outSub);
        put(sb, "chat_inMediaIcon", onAccentFill);
        put(sb, "chat_outMediaIcon", outBottom);
        put(sb, "chat_inMediaIconSelected", onAccentFill);
        put(sb, "chat_outMediaIconSelected", outSelected);
        put(sb, "chat_inAudioDurationText", textGray);
        put(sb, "chat_outAudioDurationText", outSub);
        put(sb, "chat_inAudioTitleText", textWhite);
        put(sb, "chat_outAudioTitleText", outText);
        put(sb, "chat_inAudioPerfomerText", textWhite);
        put(sb, "chat_outAudioPerfomerText", outText);
        put(sb, "chat_inLoader", accentFill);
        put(sb, "chat_inLoaderSelected", accentFillPressed);
        put(sb, "chat_outLoader", outText);
        put(sb, "chat_outLoaderSelected", outText);
        put(sb, "chat_inContactBackground", accentFill);
        put(sb, "chat_inContactIcon", onAccentFill);
        put(sb, "chat_outContactBackground", alpha(outText, 0x26));
        put(sb, "chat_outContactIcon", outText);

        put(sb, "chat_status", accentText);
        put(sb, "chat_addContact", accentText);
        put(sb, "chat_messagePanelBackground", amoled ? Color.BLACK : actionBarBg);
        put(sb, "chat_messagePanelShadow", alpha(Color.BLACK, 0x45));
        put(sb, "chat_messagePanelText", textWhite);
        put(sb, "chat_messagePanelHint", textHint);
        put(sb, "chat_messagePanelCursor", accentText);
        put(sb, "chat_messagePanelIcons", textGray);
        put(sb, "chat_messagePanelSend", accentText);
        put(sb, "chat_messagePanelVoicePressed", onAccentFill);
        put(sb, "chat_messagePanelVoiceBackground", accentFill);
        put(sb, "chat_topPanelBackground", amoled ? Color.BLACK : actionBarBg);
        put(sb, "chat_topPanelClose", textGray);
        put(sb, "chat_topPanelLine", accentText);
        put(sb, "chat_topPanelTitle", accentText);
        put(sb, "chat_topPanelMessage", textGray);
        put(sb, "chat_replyPanelIcons", accentText);
        put(sb, "chat_replyPanelClose", textGray);
        put(sb, "chat_replyPanelName", accentText);
        put(sb, "chat_replyPanelLine", accentText);
        put(sb, "chat_searchPanelIcons", accentText);
        put(sb, "chat_searchPanelText", accentText);
        put(sb, "chat_emojiPanelBackground", amoled ? 0xFF0A0A0D : bgGray);
        put(sb, "chat_emojiSearchBackground", bgElevated);
        put(sb, "chat_emojiSearchIcon", textHint);
        put(sb, "chat_emojiPanelShadowLine", dividerColor);
        put(sb, "chat_emojiPanelEmptyText", textGray2);
        put(sb, "chat_emojiPanelIcon", textGray);
        put(sb, "chat_emojiBottomPanelIcon", textGray);
        put(sb, "chat_emojiPanelIconSelected", accentText);
        put(sb, "chat_emojiPanelStickerPackSelector", alpha(accentText, 0x22));
        put(sb, "chat_emojiPanelStickerPackSelectorLine", accentText);
        put(sb, "chat_emojiPanelBackspace", textGray);
        put(sb, "chat_emojiPanelTrendingTitle", textWhite);
        put(sb, "chat_emojiPanelStickerSetName", textGray);
        put(sb, "chat_emojiPanelStickerSetNameHighlight", accentText);
        put(sb, "chat_emojiPanelStickerSetNameIcon", textGray2);
        put(sb, "chat_emojiPanelNewTrending", accentFill);
        put(sb, "chat_emojiPanelTrendingDescription", textGray2);

        int[] wall = computeWallpaperColors(p, true, amoled, themedWallpaper);
        if (themedWallpaper) {
            put(sb, "chat_wallpaper", wall[0]);
            put(sb, "chat_wallpaper_gradient_to", wall[1]);
            put(sb, "key_chat_wallpaper_gradient_to2", wall[2]);
            put(sb, "key_chat_wallpaper_gradient_to3", wall[3]);
            put(sb, "chat_wallpaper_gradient_rotation", 45);
        } else {
            put(sb, "chat_wallpaper", wall[0]);
        }
        put(sb, "chat_serviceBackground", alpha(ColorUtils.blendARGB(p.neutral2_800, p.accent1_900, 0.40f), 0xB8));
        put(sb, "chat_serviceBackgroundSelected", alpha(ColorUtils.blendARGB(p.neutral2_700, p.accent1_800, 0.45f), 0xD8));
        put(sb, "chat_serviceBackgroundSelector", alpha(accentText, 0x26));
        put(sb, "chat_serviceText", textWhite);
        put(sb, "chat_serviceLink", accentText);
        put(sb, "chat_serviceIcon", textWhite);

        put(sb, "avatar_backgroundInProfileBlue", p.accent1_700);
        put(sb, "avatar_backgroundActionBarBlue", p.accent1_700);
        put(sb, "avatar_actionBarSelectorBlue", ripple);
        put(sb, "avatar_actionBarIconBlue", textWhite);
        put(sb, "avatar_subtitleInProfileBlue", alpha(textWhite, 0xCC));
        put(sb, "avatar_backgroundSaved", p.accent1_600);
        put(sb, "avatar_background2Saved", p.accent3_700);
        put(sb, "profile_creatorIcon", accentText);
        put(sb, "profile_title", textWhite);
        put(sb, "profile_actionIcon", onAccentFill);
        put(sb, "profile_actionBackground", accentFill);
        put(sb, "profile_actionPressedBackground", accentFillPressed);
        put(sb, "profile_verifiedBackground", accentFill);
        put(sb, "profile_verifiedCheck", onAccentFill);
        put(sb, "profile_status", alpha(accentText, 0xCC));
        put(sb, "profile_tabText", textGray);
        put(sb, "profile_tabSelectedText", accentText);
        put(sb, "profile_tabSelectedLine", accentText);
        put(sb, "profile_tabSelector", ripple);
        put(sb, "featuredStickers_addButton", accentFill);
        put(sb, "featuredStickers_addButton2", accentFill);
        put(sb, "featuredStickers_addButtonPressed", accentFillPressed);
        put(sb, "featuredStickers_buttonText", onAccentFill);
        put(sb, "featuredStickers_unread", accentFill);
        put(sb, "featuredStickers_addedIcon", accentText);
        put(sb, "featuredStickers_buttonProgress", onAccentFill);
        put(sb, "inappPlayerBackground", bgElevated);
        put(sb, "inappPlayerTitle", textWhite);
        put(sb, "inappPlayerPerformer", textGray);
        put(sb, "inappPlayerPlayPause", accentText);
        put(sb, "inappPlayerClose", textGray);
        put(sb, "player_background", bgElevated);
        put(sb, "player_time", textGray2);
        put(sb, "player_progressBackground", p.neutral2_800);
        put(sb, "key_player_progressCachedBackground", p.neutral2_700);
        put(sb, "player_progress", accentText);
        put(sb, "player_button", textWhite);
        put(sb, "player_buttonActive", accentText);
        put(sb, "undo_background", p.neutral1_800);
        put(sb, "undo_cancelColor", accentText);
        put(sb, "undo_infoColor", textWhite);
        put(sb, "stories_circle1", accentFill);
        put(sb, "stories_circle2", p.accent3_200);
        put(sb, "stories_circle_dialog1", accentFill);
        put(sb, "stories_circle_dialog2", p.accent3_200);

        emitSupplementalThemeColors(
                sb, p, true,
                bgSurface, bgGray, bgElevated,
                accentText, accentFill, accentFillPressed, onAccentFill,
                textWhite, textGray, textGray2, textHint,
                dividerColor, ripple, redDestructive,
                inBubble, inBubbleSel, outSelected, outText, outSub, outMuted, outAccent
        );
    }

    private static void emitSupplementalThemeColors(
            StringBuilder sb,
            Palette p,
            boolean dark,
            int bgSurface,
            int bgGray,
            int surfaceElevated,
            int accentPrimary,
            int accentFill,
            int accentDeepOrPressed,
            int onAccent,
            int textPrimary,
            int textGray,
            int textGray2,
            int textHint,
            int dividerColor,
            int ripple,
            int redDestructive,
            int inBubble,
            int inBubbleSel,
            int outSelected,
            int outText,
            int outSub,
            int outMuted,
            int outAccent
    ) {
        emitChatPanelAndVoiceColors(
                sb, p, dark, surfaceElevated, accentPrimary, accentFill, accentDeepOrPressed, onAccent,
                textPrimary, textGray, textGray2, textHint, ripple, redDestructive
        );
        emitChatCompactColors(
                sb, dark, bgSurface, textPrimary, textGray, textGray2
        );
        emitChatMessageAndIvButtonColors(
                sb, p, dark, bgSurface, bgGray, surfaceElevated, accentPrimary, accentFill, accentDeepOrPressed,
                onAccent, textPrimary, textGray, textGray2, dividerColor, redDestructive,
                inBubble, inBubbleSel, outSelected, outText, outSub, outMuted, outAccent
        );
        emitSupplementalScreenAndChartColors(
                sb, p, dark, bgSurface, bgGray, surfaceElevated, accentPrimary, accentFill, accentDeepOrPressed,
                onAccent, textPrimary, textGray, textGray2, dividerColor, ripple, redDestructive, outText
        );
    }

    private static void emitChatPanelAndVoiceColors(
            StringBuilder sb,
            Palette p,
            boolean dark,
            int surfaceElevated,
            int accentPrimary,
            int accentFill,
            int accentDeepOrPressed,
            int onAccent,
            int textPrimary,
            int textGray,
            int textGray2,
            int textHint,
            int ripple,
            int redDestructive
    ) {
        // Floating chat banners, hint bars, scroll-to-bottom button, bot keyboard
        put(sb, "chat_unreadMessagesStartBackground", surfaceElevated);
        put(sb, "chat_unreadMessagesStartText", dark ? alpha(textPrimary, 0xE8) : accentPrimary);
        put(sb, "chat_unreadMessagesStartArrowIcon", dark ? textGray : textGray2);
        put(sb, "chat_stickersHintPanel", surfaceElevated);
        put(sb, "chat_goDownButton", surfaceElevated);
        put(sb, "chat_goDownButtonCounter", onAccent);
        put(sb, "chat_goDownButtonCounterBackground", accentFill);

        put(sb, "chat_botKeyboardButtonBackground", dark
                ? ColorUtils.blendARGB(surfaceElevated, p.neutral1_800, 0.45f)
                : alpha(p.neutral2_200, 0xA6));
        put(sb, "chat_botKeyboardButtonBackgroundPressed", dark
                ? ColorUtils.blendARGB(surfaceElevated, p.neutral2_700, 0.55f)
                : alpha(p.neutral2_400, 0x66));
        put(sb, "chat_botKeyboardButtonText", dark ? textPrimary : alpha(textPrimary, 0xF0));
        put(sb, "chat_botSwitchToInlineText", dark ? accentPrimary : accentDeepOrPressed);
        put(sb, "chat_botButtonText", textPrimary);
        put(sb, "botKeyboard_button_danger", redDestructive);
        put(sb, "botKeyboard_button_primary", accentFill);
        put(sb, "botKeyboard_button_success", dark ? 0xFF47C15E : 0xFF40B135);

        put(sb, "chat_muteIcon", dark ? alpha(textGray, 0x99) : textGray2);
        put(sb, "chat_lockIcon", textPrimary);
        put(sb, "chat_fieldOverlayText", accentPrimary);
        put(sb, "chat_mediaMenu", Color.WHITE);
        put(sb, "chat_emojiPanelBadgeBackground", accentFill);
        put(sb, "stickers_menu", dark ? textGray2 : p.neutral2_400);
        put(sb, "stickers_menuSelector", ripple);
        put(sb, "chat_stickerNameText", Color.WHITE);
        put(sb, "chat_stickerViaBotNameText", Color.WHITE);
        put(sb, "chat_stickerReplyLine", accentPrimary);
        put(sb, "chat_stickerReplyNameText", dark ? accentPrimary : accentDeepOrPressed);
        put(sb, "chat_stickerReplyMessageText", textGray);
        put(sb, "chat_editMediaButton", accentFill);

        // Voice recording & lock badge (use deeper tonal stop in dark so white labels stay legible)
        put(sb, "chat_recordedVoiceBackground", dark ? p.accent1_600 : accentFill);
        put(sb, "chat_recordedVoiceDarkerBackground", p.accent1_700);
        put(sb, "chat_recordedVoicePlayPause", Color.WHITE);
        put(sb, "chat_recordedVoiceProgress", p.accent1_200);
        put(sb, "chat_recordedVoiceProgressInner", Color.WHITE);
        put(sb, "chat_recordedVoiceDot", redDestructive);
        put(sb, "chat_recordVoiceCancel", accentPrimary);
        put(sb, "chat_recordTime", dark ? alpha(textPrimary, 0xAA) : textGray2);
        put(sb, "chat_messagePanelVoiceDelete", dark ? textPrimary : textGray);
        put(sb, "chat_messagePanelVoiceDuration", Color.WHITE);
        put(sb, "chat_messagePanelCancelInlineBot", dark ? alpha(textPrimary, 0x65) : textHint);
        put(sb, "key_chat_messagePanelVoiceLock", dark ? textPrimary : 0xFFA4A4A4);
        put(sb, "key_chat_messagePanelVoiceLockBackground", surfaceElevated);
        put(sb, "key_chat_messagePanelVoiceLockShadow", dark ? 0 : Color.BLACK);
    }

    private static void emitChatCompactColors(
            StringBuilder sb,
            boolean dark,
            int bgSurface,
            int textPrimary,
            int textGray,
            int textGray2
    ) {
        int opaqueSurface = bgSurface | 0xFF000000;
        put(sb, "chat_compact_background", bgSurface);
        put(sb, "chat_compact_backgroundGradient", dark ? 0 : alpha(textPrimary, 0x08));
        put(sb, "chat_compact_glassOutline", dark ? 0 : alpha(textPrimary, 0x24));
        put(sb, "chat_compact_glassShadow", dark ? 0 : alpha(Color.BLACK, 0x14));
        put(sb, "chat_compact_glassHairline", dark ? 0 : alpha(Color.BLACK, 0x0D));
        put(sb, "chat_compact_topicSeparatorText", textPrimary);
        put(sb, "chat_compact_inputGlassOutline", dark ? 0 : alpha(textPrimary, 0x24));
        put(sb, "chat_compact_buttonGlassOutline", dark ? 0 : alpha(textPrimary, 0x24));
        put(sb, "chat_compact_fadeEdge", bgSurface);
        put(sb, "chat_compact_fadeMiddle", alpha(bgSurface, 0xA3));
        put(sb, "chat_compact_actionBackground", alpha(textPrimary, dark ? 0x1B : 0x0D));
        put(sb, "chat_compact_actionText", textPrimary);
        put(sb, "chat_compact_actionIcon", textPrimary);
        put(sb, "chat_compact_botButtonText", textPrimary);
        put(sb, "chat_compact_pullingDownText", textPrimary);
        put(sb, "chat_compact_topicEmptyText", textPrimary);
        put(sb, "chat_compact_progress", textPrimary);
        put(sb, "chat_compact_outBackground", alpha(textPrimary, dark ? 0x1B : 0x0F));
        put(sb, "chat_compact_outTimeText", dark ? alpha(textPrimary, 0x80) : textGray2);
        put(sb, "chat_compact_outCheck", textPrimary);
        put(sb, "chat_compact_outReplyBackground", alpha(textPrimary, dark ? 0x0E : 0x0D));
        put(sb, "chat_compact_outReplyLine", textPrimary);
        put(sb, "chat_compact_outReplyText", textPrimary);
        put(sb, "chat_compact_inTimeText", alpha(dark ? textGray : textGray2, 0x80));
        put(sb, "chat_compact_replyText", dark ? alpha(textPrimary, 0x80) : textGray2);
        put(sb, "chat_compact_sideButtonIcon", textPrimary);
        put(sb, "chat_compact_inAudioDuration", textPrimary);
        put(sb, "chat_compact_inWaveform", textPrimary);
        put(sb, "chat_compact_inWaveformBackground", alpha(textPrimary, 0x4D));
        put(sb, "chat_compact_inTranscribeButton", textPrimary);
        put(sb, "chat_compact_outAccent", textPrimary);
        put(sb, "chat_compact_outAccentFill", textPrimary);
        put(sb, "chat_compact_outAccentIcon", opaqueSurface);
        put(sb, "chat_compact_outSecondaryText", dark ? alpha(textPrimary, 0x80) : textGray2);
        put(sb, "chat_compact_outAccentLight", alpha(textPrimary, dark ? 0x20 : 0x1A));
        put(sb, "chat_compact_outAccentBorder", alpha(textPrimary, dark ? 0x34 : 0x26));
        put(sb, "chat_compact_reactionBackground", alpha(textPrimary, dark ? 0x1B : 0x0D));
        put(sb, "chat_compact_reactionText", textPrimary);
        put(sb, "chat_compact_reactionBackgroundSelected", textPrimary);
        put(sb, "chat_compact_reactionTextSelected", opaqueSurface);
    }

    private static void emitChatMessageAndIvButtonColors(
            StringBuilder sb,
            Palette p,
            boolean dark,
            int bgSurface,
            int bgGray,
            int surfaceElevated,
            int accentPrimary,
            int accentFill,
            int accentDeepOrPressed,
            int onAccent,
            int textPrimary,
            int textGray,
            int textGray2,
            int dividerColor,
            int redDestructive,
            int inBubble,
            int inBubbleSel,
            int outSelected,
            int outText,
            int outSub,
            int outMuted,
            int outAccent
    ) {
        int inSelectedAccent = dark
                ? ColorUtils.blendARGB(textPrimary, accentPrimary, 0.45f)
                : textPrimary;
        int inSelectedSub = dark
                ? ColorUtils.blendARGB(textPrimary, accentPrimary, 0.45f)
                : textGray;
        int nameHighlight = dark ? accentPrimary : accentDeepOrPressed;

        put(sb, "chat_outBubbleGradientSelectedOverlay", dark ? alpha(Color.WHITE, 0x15) : alpha(Color.BLACK, 0x14));
        put(sb, "chat_inTextSelectionHighlight", dark ? alpha(accentFill, 0x5A) : alpha(accentPrimary, 0x50));
        put(sb, "chat_outTextSelectionHighlight", dark ? alpha(accentPrimary, 0x7E) : alpha(outSub, 0x2E));
        put(sb, "chat_TextSelectionCursor", accentPrimary);
        put(sb, "chat_outTextSelectionCursor", accentPrimary);
        put(sb, "chat_inBubbleLocationPlaceholder", dark ? alpha(textPrimary, 0x18) : alpha(textGray, 0x1E));
        put(sb, "chat_outBubbleLocationPlaceholder", alpha(outSub, 0x1E));
        put(sb, "chat_BlurAlpha", 0xB2000000);
        put(sb, "chat_BlurAlphaSlow", 0xC1000000);

        put(sb, "chat_mediaSentCheck", Color.WHITE);
        put(sb, "chat_mediaSentClock", alpha(Color.WHITE, 0xB3));
        put(sb, "chat_mediaTimeBackground", 0x66000000);
        put(sb, "chat_mediaViews", Color.WHITE);
        put(sb, "chat_mediaTimeText", Color.WHITE);
        put(sb, "chat_mediaProgress", Color.WHITE);
        put(sb, "chat_mediaInfoText", Color.WHITE);
        put(sb, "chat_previewDurationText", Color.WHITE);
        put(sb, "chat_previewGameText", Color.WHITE);
        put(sb, "chat_mediaLoaderPhoto", 0x66000000);
        put(sb, "chat_mediaLoaderPhotoSelected", 0x7F000000);
        put(sb, "chat_mediaLoaderPhotoIcon", Color.WHITE);
        put(sb, "chat_mediaLoaderPhotoIconSelected", 0xFFD9D9D9);

        put(sb, "chat_sentError", redDestructive);
        put(sb, "chat_sentErrorIcon", Color.WHITE);
        put(sb, "chat_inPreviewInstantText", nameHighlight);
        put(sb, "chat_outPreviewInstantText", outAccent);
        put(sb, "chat_secretTimeText", dark ? 0xFFCDCFD0 : 0xFFE4E2E0);
        put(sb, "chat_secretChatStatusText", dark ? alpha(textPrimary, 0x6F) : textGray2);
        put(sb, "chat_inPsaNameText", dark ? 0xFF7AD46A : 0xFF5A9C39);
        put(sb, "chat_outPsaNameText", dark ? outAccent : 0xFF5A9C39);
        put(sb, "chat_inContactNameText", nameHighlight);
        put(sb, "chat_outContactNameText", outText);
        put(sb, "chat_inContactPhoneText", dark ? textGray : textPrimary);
        put(sb, "chat_inContactPhoneSelectedText", inSelectedAccent);
        put(sb, "chat_outContactPhoneText", outSub);
        put(sb, "chat_outContactPhoneSelectedText", outSub);
        put(sb, "chat_inVenueInfoText", dark ? textGray : textGray2);
        put(sb, "chat_inVenueInfoSelectedText", inSelectedSub);
        put(sb, "chat_outVenueInfoText", outSub);
        put(sb, "chat_outVenueInfoSelectedText", outSub);
        put(sb, "chat_adminText", dark ? textGray : textGray2);
        put(sb, "chat_adminSelectedText", inSelectedSub);
        put(sb, "chat_outAdminText", outSub);
        put(sb, "chat_outAdminSelectedText", outSub);

        put(sb, "chat_inAudioSelectedProgress", dark ? textPrimary : inBubbleSel);
        put(sb, "chat_outAudioSelectedProgress", dark ? outText : outSelected);
        put(sb, "chat_inAudioPerfomerSelectedText", inSelectedAccent);
        put(sb, "chat_outAudioPerfomerSelectedText", outSub);
        put(sb, "chat_inAudioDurationSelectedText", inSelectedSub);
        put(sb, "chat_outAudioDurationSelectedText", outSub);
        put(sb, "chat_inAudioCacheSeekbar", dark ? alpha(textGray2, 0x55) : alpha(accentPrimary, 0x3F));
        put(sb, "chat_outAudioCacheSeekbar", dark ? alpha(outText, 0x55) : alpha(outMuted, 0x55));
        put(sb, "chat_inAudioSeekbarSelected", dark ? ColorUtils.blendARGB(inBubbleSel, accentPrimary, 0.35f) : alpha(accentPrimary, 0x44));
        put(sb, "chat_outAudioSeekbarSelected", dark ? alpha(outSub, 0x66) : alpha(outSub, 0x55));
        put(sb, "chat_inFileProgressSelected", dark ? textPrimary : p.accent1_100);
        put(sb, "chat_outFileProgressSelected", dark ? outText : outSelected);
        put(sb, "chat_inFileInfoSelectedText", inSelectedSub);
        put(sb, "chat_outFileInfoSelectedText", outSub);
        put(sb, "chat_inFileBackgroundSelected", dark ? ColorUtils.blendARGB(inBubbleSel, accentFill, 0.35f) : alpha(accentPrimary, 0x20));
        put(sb, "chat_outFileBackgroundSelected", dark ? alpha(outText, 0x20) : alpha(outText, 0x24));
        put(sb, "chat_inLoaderPhoto", dark ? ColorUtils.blendARGB(surfaceElevated, p.neutral1_800, 0.40f) : alpha(p.neutral2_200, 0xCC));
        put(sb, "chat_inLocationBackground", dark ? ColorUtils.blendARGB(inBubble, p.neutral1_800, 0.40f) : bgGray);
        put(sb, "chat_inLocationIcon", dark ? textPrimary : textGray2);
        put(sb, "chat_outLocationIcon", dark ? outText : outSub);
        put(sb, "chat_inDownCall", dark ? 0xFF4CD964 : 0xFF00C853);
        put(sb, "chat_outUpCall", outText);
        put(sb, "chat_inPollWrongAnswer", redDestructive);
        put(sb, "chat_outPollWrongAnswer", redDestructive);
        put(sb, "chat_inlineResultIcon", nameHighlight);
        put(sb, "chat_gifSaveHintText", dark ? textPrimary : Color.WHITE);
        put(sb, "chat_gifSaveHintBackground", dark ? 0xE21F2329 : 0xE21F2B38);
        put(sb, "chat_reactionServiceButtonBackgroundSelected", dark ? textPrimary : Color.WHITE);
        put(sb, "chat_reactionServiceButtonTextSelected", dark ? (bgSurface | 0xFF000000) : Color.BLACK);
        put(sb, "chat_tagAdmin", dark ? 0xFF5AD440 : 0xFF40A920);
        put(sb, "chat_tagCreator", dark ? 0xFFB288FF : 0xFF955CDB);

        // Message table / divider / article code blocks
        put(sb, "table_background", dark ? alpha(textPrimary, 0x0B) : ColorUtils.blendARGB(bgSurface, bgGray, 0.65f));
        put(sb, "table_border", dark ? alpha(textPrimary, 0x1A) : dividerColor);
        put(sb, "chat_inTableBackground", alpha(textPrimary, dark ? 0x0A : 0x06));
        put(sb, "chat_outTableBackground", alpha(outText, 0x10));
        put(sb, "chat_inTableBorder", dark ? alpha(textPrimary, 0x1A) : dividerColor);
        put(sb, "chat_outTableBorder", alpha(outText, 0x26));
        put(sb, "chat_inDivider", dark ? alpha(textPrimary, 0x1A) : dividerColor);
        put(sb, "chat_outDivider", alpha(outText, 0x26));
        put(sb, "chat_inArticleCodeBackground", alpha(textPrimary, dark ? 0x10 : 0x06));
        put(sb, "chat_outArticleCodeBackground", alpha(outText, 0x12));
        put(sb, "chat_inArticleCodeScrollbarBackground", alpha(textPrimary, dark ? 0x1F : 0x14));
        put(sb, "chat_inArticleCodeScrollbar", alpha(textPrimary, dark ? 0x38 : 0x29));
        put(sb, "chat_outArticleCodeScrollbarBackground", alpha(outText, dark ? 0x1F : 0x18));
        put(sb, "chat_outArticleCodeScrollbar", alpha(outText, dark ? 0x38 : 0x2E));
        put(sb, "chat_inArticleDetailsArrow", dark ? alpha(textPrimary, 0x9E) : textGray2);
        put(sb, "chat_outArticleDetailsArrow", alpha(outText, 0x9E));
        put(sb, "chat_inArticleDetailsLine", dark ? alpha(textPrimary, 0x2E) : dividerColor);
        put(sb, "chat_outArticleDetailsLine", alpha(outText, 0x2E));

        // Bot / IV / Action buttons inside incoming & outgoing bubbles
        int btnDefaultIn = dark ? ColorUtils.blendARGB(inBubble, p.neutral1_800, 0.55f) : bgGray;
        int btnDefaultInPressed = dark ? ColorUtils.blendARGB(inBubble, p.neutral2_700, 0.65f) : p.neutral2_100;
        int btnPrimaryOutBg = dark ? accentFill : accentDeepOrPressed;
        put(sb, "chat_msgIvButtonDefaultIn", btnDefaultIn);
        put(sb, "chat_msgIvButtonDefaultInPressed", btnDefaultInPressed);
        put(sb, "chat_msgIvButtonDefaultInText", textPrimary);
        put(sb, "chat_msgIvButtonPrimaryIn", accentFill);
        put(sb, "chat_msgIvButtonPrimaryInPressed", accentDeepOrPressed);
        put(sb, "chat_msgIvButtonPrimaryInText", onAccent);
        put(sb, "chat_msgIvButtonDangerIn", dark ? 0x2AFF6D6D : 0xFFF8EAE9);
        put(sb, "chat_msgIvButtonDangerInPressed", dark ? 0x40FF6D6D : 0xFFF2D8D6);
        put(sb, "chat_msgIvButtonDangerInText", dark ? 0xFFFF6D6D : 0xFFCC5049);
        put(sb, "chat_msgIvButtonSuccessIn", dark ? 0x2A78FF7B : 0xFFE8F4E4);
        put(sb, "chat_msgIvButtonSuccessInPressed", dark ? 0x4078FF7B : 0xFFD9EBD2);
        put(sb, "chat_msgIvButtonSuccessInText", dark ? 0xFF78FF7B : 0xFF40A920);
        put(sb, "chat_msgIvButtonDefaultInlineIn", alpha(accentPrimary, dark ? 0x19 : 0x1A));
        put(sb, "chat_msgIvButtonDefaultInlineInPressed", alpha(accentPrimary, 0x28));
        put(sb, "chat_msgIvButtonDefaultInlineInText", accentPrimary);

        put(sb, "chat_msgIvButtonDefaultOut", alpha(outText, dark ? 0x1C : 0x18));
        put(sb, "chat_msgIvButtonDefaultOutPressed", alpha(outText, dark ? 0x2C : 0x28));
        put(sb, "chat_msgIvButtonDefaultOutText", outText);
        put(sb, "chat_msgIvButtonPrimaryOut", btnPrimaryOutBg);
        put(sb, "chat_msgIvButtonPrimaryOutPressed", accentDeepOrPressed);
        put(sb, "chat_msgIvButtonPrimaryOutText", dark ? onAccent : contrastTextFor(btnPrimaryOutBg));
        put(sb, "chat_msgIvButtonDangerOut", dark ? 0x2AFF6D6D : alpha(0xFFCC5049, 0x18));
        put(sb, "chat_msgIvButtonDangerOutPressed", dark ? 0x40FF6D6D : alpha(0xFFCC5049, 0x28));
        put(sb, "chat_msgIvButtonDangerOutText", dark ? 0xFFFF6D6D : 0xFFCC5049);
        put(sb, "chat_msgIvButtonSuccessOut", dark ? 0x2A78FF7B : alpha(0xFF40A920, 0x18));
        put(sb, "chat_msgIvButtonSuccessOutPressed", dark ? 0x4078FF7B : alpha(0xFF40A920, 0x28));
        put(sb, "chat_msgIvButtonSuccessOutText", dark ? 0xFF78FF7B : 0xFF2D8814);
        put(sb, "chat_msgIvButtonDefaultInlineOut", alpha(outText, dark ? 0x1C : 0x18));
        put(sb, "chat_msgIvButtonDefaultInlineOutPressed", alpha(outText, dark ? 0x2C : 0x28));
        put(sb, "chat_msgIvButtonDefaultInlineOutText", outText);
    }

    private static void emitSupplementalScreenAndChartColors(
            StringBuilder sb,
            Palette p,
            boolean dark,
            int bgSurface,
            int bgGray,
            int surfaceElevated,
            int accentPrimary,
            int accentFill,
            int accentDeepOrPressed,
            int onAccent,
            int textPrimary,
            int textGray,
            int textGray2,
            int dividerColor,
            int ripple,
            int redDestructive,
            int outText
    ) {
        // Attach sheet & pickers
        put(sb, "chat_attachCheckBoxCheck", onAccent);
        put(sb, "chat_attachCheckBoxBackground", accentFill);
        put(sb, "chat_attachPhotoBackground", alpha(dark ? Color.WHITE : Color.BLACK, 0x0C));
        put(sb, "chat_attachActiveTab", accentPrimary);
        put(sb, "chat_attachUnactiveTab", textGray2);
        put(sb, "chat_attachPermissionImage", textPrimary);
        put(sb, "chat_attachPermissionMark", redDestructive);
        put(sb, "chat_attachPermissionText", textGray);
        put(sb, "chat_attachEmptyImage", dark ? p.neutral2_700 : p.neutral2_200);
        put(sb, "chat_attachIcon", Color.WHITE);
        put(sb, "chat_attachGalleryBackground", dark ? 0xFF4D94F5 : 0xFF459DF5);
        put(sb, "chat_attachAudioBackground", dark ? 0xFFF66F6B : 0xFFEB6060);
        put(sb, "chat_attachContactBackground", dark ? 0xFFDEB045 : 0xFFF2C04B);
        put(sb, "chat_attachContactText", dark ? 0xFFDEB045 : 0xFFDFA000);
        put(sb, "chat_attachLocationBackground", dark ? 0xFF56C35A : 0xFF60C255);
        put(sb, "chat_attachLocationText", dark ? 0xFF56C35A : 0xFF3CAE2F);
        put(sb, "chat_attachPollBackground", dark ? 0xFFDEB045 : 0xFFF2C04B);

        // Instant View & syntax highlighting
        put(sb, "iv_background", bgSurface);
        put(sb, "iv_backgroundGray", bgGray);
        put(sb, "iv_navigationBackground", dark ? bgSurface : bgGray);
        put(sb, "iv_ab_progress", accentPrimary);
        put(sb, "code_keyword", dark ? 0xFFFF9380 : 0xFFE05356);
        put(sb, "code_operator", dark ? 0x7FFFFFFF : 0xFF4DBBFF);
        put(sb, "code_constant", dark ? 0xFFFF9380 : 0xFF7F79F3);
        put(sb, "code_string", dark ? 0xFF88E498 : 0xFF37C123);
        put(sb, "code_number", dark ? 0xFF59DDF7 : 0xFF327FE5);
        put(sb, "code_comment", dark ? 0x7FFFFFFF : 0x80000000);
        put(sb, "code_function", dark ? 0xFFFF9380 : 0xFFF28C39);

        // Share sheet, bottom sheets, media picker, files, player, groupcreate
        put(sb, "share_icon", dark ? textPrimary : textGray);
        put(sb, "share_linkText", dark ? alpha(textPrimary, 0xB8) : textPrimary);
        put(sb, "share_linkBackground", alpha(dark ? Color.WHITE : Color.BLACK, dark ? 0x15 : 0x0F));
        put(sb, "key_sheet_scrollUp", dark ? alpha(textPrimary, 0x26) : p.neutral2_200);
        put(sb, "key_sheet_other", dark ? alpha(textPrimary, 0x44) : p.neutral2_200);
        put(sb, "picker_enabledButton", accentFill);
        put(sb, "picker_disabledButton", dark ? p.neutral2_700 : p.neutral2_400);
        put(sb, "picker_badge", accentFill);
        put(sb, "picker_badgeText", onAccent);
        put(sb, "files_folderIcon", Color.WHITE);
        put(sb, "files_folderIconBackground", accentFill);
        put(sb, "files_iconText", Color.WHITE);
        put(sb, "groupcreate_hintText", dark ? textGray : textGray2);
        put(sb, "groupcreate_cursor", accentPrimary);
        put(sb, "groupcreate_sectionShadow", Color.BLACK);
        put(sb, "groupcreate_sectionText", dark ? alpha(textPrimary, 0x99) : textGray);
        put(sb, "groupcreate_spanText", textPrimary);
        put(sb, "groupcreate_spanBackground", dark ? surfaceElevated : bgGray);
        put(sb, "groupcreate_spanDelete", dark ? textPrimary : Color.WHITE);
        put(sb, "player_actionBarSelector", ripple);
        put(sb, "player_actionBarTitle", textPrimary);
        put(sb, "player_actionBarSubtitle", dark ? alpha(textPrimary, 0x65) : textGray2);
        put(sb, "player_actionBarItems", dark ? textPrimary : textGray2);

        // Dialogs, location, calls, shared media, progress spinners, misc UI
        put(sb, "dialogScrollGlow", dark ? bgSurface : bgGray);
        put(sb, "dialogCardShadow", dark ? alpha(Color.BLACK, 0x40) : alpha(Color.BLACK, 0x17));
        put(sb, "dialog_inlineProgressBackground", alpha(dark ? surfaceElevated : bgGray, 0xF6));
        put(sb, "dialog_inlineProgress", textGray);
        put(sb, "dialog_liveLocationProgress", accentFill);
        put(sb, "dialogGiftsBackground", bgGray);
        put(sb, "dialogGiftsTabText", textGray);
        put(sb, "chats_sentError", redDestructive);
        put(sb, "chats_sentErrorIcon", Color.WHITE);
        put(sb, "chats_menuTopShadow", dark ? alpha(bgSurface, 0xA4) : 0x2915385A);
        put(sb, "chats_menuTopShadowCats", dark ? 0 : 0x002C9EBF);

        put(sb, "location_sendLocationBackground", accentFill);
        put(sb, "location_sendLocationIcon", dark ? onAccent : Color.WHITE);
        put(sb, "location_sendLocationText", dark ? accentPrimary : accentDeepOrPressed);
        put(sb, "location_sendLiveLocationBackground", dark ? 0xFF47B84F : 0xFF4FC244);
        put(sb, "location_sendLiveLocationIcon", Color.WHITE);
        put(sb, "location_sendLiveLocationText", dark ? 0xFF66DE6E : 0xFF36AB24);
        put(sb, "location_liveLocationProgress", accentFill);
        put(sb, "location_placeLocationBackground", accentFill);
        put(sb, "location_actionIcon", textPrimary);
        put(sb, "location_actionActiveIcon", accentPrimary);
        put(sb, "location_actionBackground", surfaceElevated);
        put(sb, "location_actionPressedBackground", dark ? surfaceElevated : bgGray);

        put(sb, "returnToCallBackground", dark ? ColorUtils.blendARGB(accentFill, p.accent1_700, 0.55f) : accentFill);
        put(sb, "returnToCallMutedBackground", dark ? p.neutral2_700 : p.neutral2_500);
        put(sb, "returnToCallText", Color.WHITE);
        put(sb, "contextProgressInner1", dark ? alpha(accentPrimary, 0x5B) : p.accent1_200);
        put(sb, "contextProgressOuter1", accentFill);
        put(sb, "contextProgressInner2", dark ? alpha(textPrimary, 0x55) : p.accent1_200);
        put(sb, "contextProgressOuter2", dark ? textPrimary : Color.WHITE);
        put(sb, "contextProgressInner3", dark ? p.neutral2_600 : p.neutral2_400);
        put(sb, "contextProgressOuter3", dark ? textPrimary : Color.WHITE);
        put(sb, "contextProgressInner4", dark ? p.neutral2_700 : p.neutral2_200);
        put(sb, "contextProgressOuter4", textPrimary);

        put(sb, "sharedMedia_startStopLoadIcon", accentPrimary);
        put(sb, "sharedMedia_linkPlaceholder", dark ? surfaceElevated : bgGray);
        put(sb, "sharedMedia_linkPlaceholderText", textGray2);
        put(sb, "sharedMedia_photoPlaceholder", dark ? surfaceElevated : bgGray);
        put(sb, "featuredStickers_removeButtonText", dark ? accentPrimary : accentDeepOrPressed);
        put(sb, "buttonNeutral", dark ? surfaceElevated : p.neutral2_100);
        put(sb, "buttonNeutralText", textPrimary);
        put(sb, "changephoneinfo_image2", accentFill);
        put(sb, "contacts_inviteBackground", dark ? 0xFF47B84F : 0xFF55BE61);
        put(sb, "contacts_inviteText", Color.WHITE);
        put(sb, "login_progressInner", dark ? p.neutral2_800 : p.accent1_100);
        put(sb, "login_progressOuter", accentFill);
        put(sb, "sessions_devicesImage", dark ? textGray : textGray2);
        put(sb, "calls_callReceivedGreenIcon", dark ? 0xFF4CD964 : 0xFF00C853);
        put(sb, "calls_callReceivedRedIcon", redDestructive);
        put(sb, "bot_loadingIcon", dark ? alpha(textPrimary, 0x1F) : p.neutral1_100);
        put(sb, "gift_ribbon", dark ? 0xFF4164FF : 0xFF46A4F2);
        put(sb, "gift_ribbon_soldout", redDestructive);
        put(sb, "pollCreateIcons", dark ? textGray : textGray2);
        put(sb, "wallet_comment", dark ? surfaceElevated : bgGray);
        put(sb, "wallet_out_commentBorder", dark ? alpha(outText, 0x26) : p.neutral2_200);
        put(sb, "wallet_inputCurrency", dark ? 0xFF4AD36D : 0xFF219949);
        put(sb, "passport_authorizeBackground", accentFill);
        put(sb, "passport_authorizeBackgroundSelected", accentDeepOrPressed);
        put(sb, "passport_authorizeText", onAccent);
        put(sb, "reactionStarSelector", 0x40F0AB1F);
        put(sb, "topics_unreadCounter", dark ? 0xFF4CD964 : 0xFF4ECC5E);
        put(sb, "topics_unreadCounterMuted", dark ? p.neutral2_600 : p.neutral2_400);
        put(sb, "voipgroup_topPanelGray", dark ? p.neutral2_600 : 0xFF8599AA);
        put(sb, "voipgroup_windowBackgroundWhiteInputField", dark ? p.neutral2_700 : p.neutral2_200);
        put(sb, "voipgroup_windowBackgroundWhiteInputFieldActivated", accentPrimary);
        put(sb, "telegram_color", accentFill);
        put(sb, "telegram_color_text", accentPrimary);
        put(sb, "telegram_color_dialogsLogo", dark ? accentPrimary : accentDeepOrPressed);

        // Avatars & close-friends/live story rings
        put(sb, "avatar_text", Color.WHITE);
        put(sb, "avatar_backgroundArchived", dark ? ColorUtils.blendARGB(surfaceElevated, p.neutral1_800, 0.35f) : 0xFFB8C2CC);
        put(sb, "avatar_backgroundArchivedHidden", dark ? ColorUtils.blendARGB(p.accent1_700, surfaceElevated, 0.35f) : accentFill);
        put(sb, "avatar_backgroundRed", dark ? 0xFFDC7F63 : 0xFFFF845E);
        put(sb, "avatar_backgroundOrange", dark ? 0xFFF2BC64 : 0xFFFEBB5B);
        put(sb, "avatar_backgroundViolet", 0xFFB694F9);
        put(sb, "avatar_backgroundGreen", 0xFF9AD164);
        put(sb, "avatar_backgroundCyan", 0xFF5BCBE3);
        put(sb, "avatar_backgroundBlue", 0xFF5CAFFA);
        put(sb, "avatar_backgroundPink", 0xFFFF8AAC);
        put(sb, "avatar_background2Red", dark ? 0xFFD9614F : 0xFFD45246);
        put(sb, "avatar_background2Orange", dark ? 0xFFE58943 : 0xFFF68136);
        put(sb, "avatar_background2Violet", 0xFF6C61DF);
        put(sb, "avatar_background2Green", 0xFF46BA43);
        put(sb, "avatar_background2Cyan", 0xFF359AD4);
        put(sb, "avatar_background2Blue", 0xFF408ACF);
        put(sb, "avatar_background2Pink", 0xFFD95574);
        put(sb, "avatar_nameInMessageRed", dark ? 0xFFE88878 : 0xFFCC5049);
        put(sb, "avatar_nameInMessageOrange", dark ? 0xFFE8AA6A : 0xFFD67722);
        put(sb, "avatar_nameInMessageViolet", dark ? 0xFF8C8CD2 : 0xFF955CDB);
        put(sb, "avatar_nameInMessageGreen", dark ? 0xFF7DD24F : 0xFF40A920);
        put(sb, "avatar_nameInMessageCyan", dark ? 0xFF53C9E6 : 0xFF309EBA);
        put(sb, "avatar_nameInMessageBlue", dark ? 0xFF5CAFFA : 0xFF368AD1);
        put(sb, "avatar_nameInMessagePink", dark ? 0xFFE8759A : 0xFFC7508B);
        put(sb, "stories_circle_closeFriends1", dark ? 0xFFC9EB48 : 0xFF81CE2D);
        put(sb, "stories_circle_closeFriends2", dark ? 0xFF09C167 : 0xFF18BD36);
        put(sb, "stories_circle_live1", 0xFFFF6B5B);
        put(sb, "stories_circle_live2", 0xFFFA4874);

        // Statistics charts & palette colors
        put(sb, "statisticChartSignature", dark ? alpha(textPrimary, 0x8C) : 0x7F252529);
        put(sb, "statisticChartSignatureAlpha", dark ? alpha(textPrimary, 0x8C) : 0x7F252529);
        put(sb, "statisticChartHintLine", dark ? alpha(textPrimary, 0x1A) : 0x1A182D3B);
        put(sb, "statisticChartActiveLine", dark ? alpha(textPrimary, 0x33) : 0x33000000);
        put(sb, "statisticChartInactivePickerChart", dark ? 0xD8313033 : 0x99E2EEF9);
        put(sb, "statisticChartActivePickerChart", dark ? 0xD8577279 : 0xD8BACCD9);
        put(sb, "statisticChartRipple", dark ? 0x2CA39EB2 : 0x2C7E9DB7);
        put(sb, "statisticChartBackZoomColor", dark ? accentPrimary : 0xFF108BE3);
        put(sb, "statisticChartChevronColor", dark ? textGray2 : 0xFFD2D5D7);
        put(sb, "statisticChartLineEmpty", dark ? dividerColor : 0xFFEEEEEE);
        put(sb, "statisticChartLine_blue", dark ? 0xFF529EFF : 0xFF327FE5);
        put(sb, "statisticChartLine_green", dark ? 0xFF3DC33F : 0xFF61C752);
        put(sb, "statisticChartLine_red", dark ? 0xFFF34C44 : 0xFFE05356);
        put(sb, "statisticChartLine_golden", dark ? 0xFFDEAC2F : 0xFFEBA52D);
        put(sb, "statisticChartLine_lightblue", dark ? 0xFF3CA3CC : 0xFF58A8ED);
        put(sb, "statisticChartLine_lightgreen", dark ? 0xFF8FD049 : 0xFF8FCF39);
        put(sb, "statisticChartLine_orange", 0xFFF28C39);
        put(sb, "statisticChartLine_indigo", dark ? 0xFF875CE5 : 0xFF7F79F3);
        put(sb, "statisticChartLine_purple", dark ? 0xFFAC84F0 : 0xFF9F79E8);
        put(sb, "statisticChartLine_cyan", 0xFF40D0CA);
        put(sb, "color_lightblue", dark ? 0xFF3CA3CC : 0xFF58A8ED);
        put(sb, "color_blue", dark ? 0xFF529EFF : 0xFF327FE5);
        put(sb, "color_green", dark ? 0xFF3DC33F : 0xFF61C752);
        put(sb, "color_lightgreen", dark ? 0xFF8FD049 : 0xFF8FCF39);
        put(sb, "color_red", dark ? 0xFFF34C44 : 0xFFE05356);
        put(sb, "color_orange", 0xFFF28C39);
        put(sb, "color_yellow", dark ? 0xFFDEAC2F : 0xFFEBA52D);
        put(sb, "color_purple", dark ? 0xFF875CE5 : 0xFF9F79E8);
        put(sb, "color_cyan", 0xFF40D0CA);

        // Premium headers (overlay stays white because it renders title/icons atop dark/vivid gradients)
        put(sb, "premiumGradient0", 0xFF4ACD43);
        put(sb, "premiumGradient1", 0xFF55A5FF);
        put(sb, "premiumGradient2", 0xFFA767FF);
        put(sb, "premiumGradient3", 0xFFDB5C9D);
        put(sb, "premiumGradient4", 0xFFF38926);
        put(sb, "premiumGradientBackground1", dark ? ColorUtils.blendARGB(bgSurface, p.accent1_900, 0.35f) : 0xFF55A5FF);
        put(sb, "premiumGradientBackground2", dark ? ColorUtils.blendARGB(bgSurface, p.accent2_900, 0.35f) : 0xFFA767FF);
        put(sb, "premiumGradientBackground3", dark ? bgSurface : 0xFFDB5C9D);
        put(sb, "premiumGradientBackground4", dark ? bgSurface : 0xFFF38926);
        put(sb, "premiumGradientBackgroundOverlay", Color.WHITE);
        put(sb, "premiumStartSmallStarsColor", dark ? alpha(p.accent1_200, 0xFF) : alpha(Color.WHITE, 90));
        put(sb, "premiumStartSmallStarsColor2", alpha(Color.WHITE, 90));
        put(sb, "premiumStarGradient1", dark ? 0xFFDC415A : Color.WHITE);
        put(sb, "premiumStarGradient2", dark ? 0xFF678BFF : 0xFFE3ECFA);
        put(sb, "premiumCoinGradient1", 0xFF1477DF);
        put(sb, "premiumCoinGradient2", 0xFFC067BA);
        put(sb, "premiumGradientBottomSheet1", 0xFF5B9DE7);
        put(sb, "premiumGradientBottomSheet2", 0xFFAB87DD);
        put(sb, "premiumGradientBottomSheet3", 0xFFE794BE);
    }

    private static void put(StringBuilder sb, String key, int value) {
        sb.append(key).append('=').append(value).append('\n');
    }

    private static int alpha(int color, int alpha) {
        return ColorUtils.setAlphaComponent(color, alpha);
    }

    private static int contrastTextFor(int backgroundColor) {
        double lum = ColorUtils.calculateLuminance(backgroundColor);
        return lum > 0.40 ? 0xFF101318 : Color.WHITE;
    }
}
