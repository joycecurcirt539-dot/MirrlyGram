package org.telegram.messenger.mirrly;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.utils.glass.GlassEngine;
import org.telegram.utils.recorder.round.RoundVideoSession;
import org.telegram.utils.settings.SharedSettings;
import org.telegram.messenger.BuildConfig;

import android.os.Build;
import java.util.concurrent.ConcurrentHashMap;

public final class MirrlyConfig {

    private static final String PREFS_NAME = "mirrly_config";
    private static final String KEY_TLS_DEFAULT_MIGRATED_V1 = "tls_default_disabled_migrated_v1";
    private static final String KEY_PORTS_DEFAULT_MIGRATED_V1 = "ports_default_10889_1444_migrated_v1";

    public static final int MAP_PROVIDER_GOOGLE = 0;
    public static final int MAP_PROVIDER_YANDEX = 1;

    public static final int OWN_CHAT_TAG_PILL_ABOVE = 0;
    public static final int OWN_CHAT_TAG_INSIDE_BUBBLE = 1;

    public static final int DEFAULT_SOCKS5_PORT = 10889;
    public static final int DEFAULT_MTPROTO_PORT = 1444;
    public static final String LOCALHOST = "127.0.0.1";

    public static volatile int effectiveSocks5Port = DEFAULT_SOCKS5_PORT;
    public static volatile int effectiveMtprotoPort = DEFAULT_MTPROTO_PORT;

    public static volatile boolean saveEditHistory = true;
    public static volatile boolean saveDeletedMessages = false;
    public static volatile boolean saveDeletedChats = false;
    public static volatile int deletedMessageAlphaPercent = 30;
    public static volatile boolean deletedTrashIconEnabled = true;
    public static volatile int deletedTrashIconColor = 0;
    public static volatile int deletedLocalDeletePolicy = 0;
    public static final int DELETED_LOCAL_POLICY_ASK = 0;
    public static final int DELETED_LOCAL_POLICY_ALWAYS_KEEP = 1;
    public static final int DEFAULT_DELETED_TRASH_COLOR = 0xFFE53935;
    public static final int MIN_DELETED_ALPHA_PERCENT = 15;
    public static final int MAX_DELETED_ALPHA_PERCENT = 100;
    public static volatile boolean confirmMediaSend = false;
    public static volatile boolean roundVideoBackCamera = false;
    public static volatile boolean inChatSendSound = true;
    public static volatile boolean inChatReceiveSound = true;
    public static volatile boolean disableAllVibrations = false;
    public static volatile boolean showOwnChatTag = false;
    public static volatile int ownChatTagStyle = OWN_CHAT_TAG_PILL_ABOVE;
    public static volatile boolean tlsEnabled = false;
    public static volatile boolean tlsClientHelloSplit = true;
    public static volatile boolean flowsealPerDcEnabled = false;
    public static volatile boolean cfProxyEnabled = true;
    public static volatile String workerDomain = "";
    public static volatile String mtprotoSecret = "";
    public static volatile int mapProvider = MAP_PROVIDER_YANDEX;
    public static volatile boolean monetAmoled = false;
    public static volatile boolean monetOutGradient = true;
    public static volatile boolean monetThemedWallpaper = true;
    public static volatile boolean monetGlassTint = true;
    public static volatile int monetStyle = 0;
    public static volatile int monetCustomSeed = 0;
    private static volatile int monetMode = 0;
    private static volatile int monetPaletteSignature = 0;
    private static final ConcurrentHashMap<Long, String> CHAT_TRANSLATE_LANG_CACHE = new ConcurrentHashMap<>();
    public static volatile float glassBlurScale = 1.0f;
    public static volatile float glassOpacityScale = 1.0f;
    public static volatile float glassSaturationScale = 1.0f;
    public static volatile float glassRefractScale = 1.0f;
    public static volatile int glassThicknessDp = 11;
    public static volatile float glassRimScale = 1.0f;
    public static volatile boolean dialogsGlassHeader = true;
    public static volatile boolean glassLiquidEverywhere = true;

    public static final int POWER_PUSH_INTERVAL_3_MIN = 3;
    public static final int POWER_PUSH_INTERVAL_10_MIN = 10;
    public static final int POWER_PUSH_INTERVAL_20_MIN = 20;

    public static final int POWER_ECO_FLAG_PUSH_INTERVAL = 1;
    public static final int POWER_ECO_FLAG_LIQUID_EVERYWHERE = 1 << 1;
    public static final int POWER_ECO_FLAG_GLASS_BLUR = 1 << 2;
    public static final int POWER_ECO_FLAG_VIBRATIONS = 1 << 3;
    public static final int POWER_ECO_MASK_DEFAULT =
            POWER_ECO_FLAG_PUSH_INTERVAL | POWER_ECO_FLAG_LIQUID_EVERYWHERE | POWER_ECO_FLAG_GLASS_BLUR;

    public static volatile int powerAutoEcoLevel = 0;
    public static volatile boolean powerFollowSystemSaver = false;
    public static volatile int powerPushIntervalMinutes = POWER_PUSH_INTERVAL_3_MIN;
    public static volatile int powerEcoMask = POWER_ECO_MASK_DEFAULT;

    private static volatile boolean loaded = false;

    private MirrlyConfig() {
    }

    public static void load() {
        if (!loaded) {
            loadInternal();
        }
        MirrlyInterfaceConfig.load();
    }

    private static synchronized void loadInternal() {
        if (loaded) {
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_TLS_DEFAULT_MIGRATED_V1, false)) {
            prefs.edit()
                    .putBoolean("tls_enabled", false)
                    .putBoolean(KEY_TLS_DEFAULT_MIGRATED_V1, true)
                    .apply();
        }
        if (!prefs.getBoolean(KEY_PORTS_DEFAULT_MIGRATED_V1, false)) {
            SharedPreferences.Editor editor = prefs.edit();
            if (prefs.getInt("effective_socks5_port", DEFAULT_SOCKS5_PORT) == 10888) {
                editor.putInt("effective_socks5_port", DEFAULT_SOCKS5_PORT);
            }
            if (prefs.getInt("effective_mtproto_port", DEFAULT_MTPROTO_PORT) == 1443) {
                editor.putInt("effective_mtproto_port", DEFAULT_MTPROTO_PORT);
            }
            editor.putBoolean(KEY_PORTS_DEFAULT_MIGRATED_V1, true);
            editor.apply();
        }
        saveEditHistory = prefs.getBoolean("save_edit_history", true);
        saveDeletedMessages = prefs.getBoolean("save_deleted_messages", false);
        saveDeletedChats = prefs.getBoolean("save_deleted_chats", false);
        deletedMessageAlphaPercent = normalizeDeletedAlphaPercent(prefs.getInt("deleted_message_alpha_percent", 30));
        deletedTrashIconEnabled = prefs.getBoolean("deleted_trash_icon_enabled", true);
        deletedTrashIconColor = prefs.getInt("deleted_trash_icon_color", 0);
        deletedLocalDeletePolicy = normalizeDeletedLocalPolicy(prefs.getInt("deleted_local_delete_policy", DELETED_LOCAL_POLICY_ASK));
        confirmMediaSend = prefs.getBoolean("confirm_media_send", false);
        roundVideoBackCamera = prefs.getBoolean("round_video_back_camera", false);
        inChatSendSound = prefs.getBoolean("in_chat_send_sound", true);
        inChatReceiveSound = prefs.getBoolean("in_chat_receive_sound", true);
        disableAllVibrations = prefs.getBoolean("disable_all_vibrations", false);
        showOwnChatTag = prefs.getBoolean("show_own_chat_tag", false);
        ownChatTagStyle = normalizeOwnChatTagStyle(prefs.getInt("own_chat_tag_style", OWN_CHAT_TAG_PILL_ABOVE));
        tlsEnabled = prefs.getBoolean("tls_enabled", false);
        tlsClientHelloSplit = prefs.getBoolean("tls_client_hello_split", true);
        flowsealPerDcEnabled = prefs.getBoolean("flowseal_per_dc_enabled", false);
        cfProxyEnabled = prefs.getBoolean("cf_proxy_enabled", true);
        workerDomain = prefs.getString("worker_domain", "");
        mtprotoSecret = prefs.getString("mtproto_secret", "");
        mapProvider = MAP_PROVIDER_YANDEX;
        effectiveSocks5Port = prefs.getInt("effective_socks5_port", DEFAULT_SOCKS5_PORT);
        effectiveMtprotoPort = prefs.getInt("effective_mtproto_port", DEFAULT_MTPROTO_PORT);
        monetMode = prefs.getInt("monet_mode", MONET_MODE_OFF);
        monetPaletteSignature = prefs.getInt("monet_palette_signature", 0);
        monetAmoled = prefs.getBoolean("monet_amoled", false);
        monetOutGradient = prefs.getBoolean("monet_out_gradient", true);
        monetThemedWallpaper = prefs.getBoolean("monet_themed_wallpaper", true);
        monetGlassTint = prefs.getBoolean("monet_glass_tint", true);
        monetStyle = prefs.getInt("monet_style", MONET_STYLE_TONAL);
        monetCustomSeed = prefs.getInt("monet_custom_seed", 0);
        glassBlurScale = Utilities.clamp(prefs.getFloat("glass_blur_scale", 1.0f), 2.0f, 0.25f);
        glassOpacityScale = Utilities.clamp(prefs.getFloat("glass_opacity_scale", 1.0f), 1.40f, 0.30f);
        glassSaturationScale = Utilities.clamp(prefs.getFloat("glass_saturation_scale", 1.0f), 1.50f, 0.50f);
        glassRefractScale = Utilities.clamp(prefs.getFloat("glass_refract_scale", 1.0f), 2.0f, 0.0f);
        glassThicknessDp = Math.max(4, Math.min(20, prefs.getInt("glass_thickness_dp", 11)));
        glassRimScale = Utilities.clamp(prefs.getFloat("glass_rim_scale", 1.0f), 2.0f, 0.0f);
        dialogsGlassHeader = prefs.getBoolean("dialogs_glass_header", true);
        glassLiquidEverywhere = prefs.getBoolean("glass_liquid_everywhere", true);
        powerAutoEcoLevel = Math.max(0, Math.min(100, prefs.getInt("power_auto_eco_level", 0)));
        powerFollowSystemSaver = prefs.getBoolean("power_follow_system_saver", false);
        powerPushIntervalMinutes = normalizePowerPushInterval(prefs.getInt("power_push_interval_min", POWER_PUSH_INTERVAL_3_MIN));
        powerEcoMask = prefs.getInt("power_eco_mask", POWER_ECO_MASK_DEFAULT);
        loaded = true;
        MirrlyVaultStorage.warmupAsync();
        MirrlyDeletedStorage.warmupAllAccountsAsync();
    }

    public static boolean isMonetAmoled() {
        load();
        return monetAmoled;
    }

    public static final int MONET_MODE_OFF = 0;
    public static final int MONET_MODE_LIGHT = 1;
    public static final int MONET_MODE_DARK = 2;
    public static final int MONET_MODE_AUTO = 3;

    public static final int MONET_STYLE_TONAL = 0;
    public static final int MONET_STYLE_VIBRANT = 1;
    public static final int MONET_STYLE_PASTEL = 2;
    public static final int MONET_STYLE_MONOCHROME = 3;

    public static int getMonetStyle() {
        load();
        return monetStyle;
    }

    public static void setMonetStyle(int style) {
        load();
        monetStyle = Math.max(MONET_STYLE_TONAL, Math.min(MONET_STYLE_MONOCHROME, style));
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putInt("monet_style", monetStyle).apply();
        }
    }

    public static int getMonetCustomSeed() {
        load();
        return monetCustomSeed;
    }

    public static void setMonetCustomSeed(int argbOrZero) {
        load();
        monetCustomSeed = argbOrZero;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putInt("monet_custom_seed", argbOrZero).apply();
        }
    }

    public static boolean isMonetGlassTint() {
        load();
        return monetGlassTint;
    }

    public static void setMonetGlassTint(boolean value) {
        load();
        monetGlassTint = value;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putBoolean("monet_glass_tint", value).apply();
        }
    }

    public static float getGlassBlurScale() {
        load();
        return glassBlurScale;
    }

    public static void setGlassBlurScale(float scale, boolean save) {
        load();
        glassBlurScale = Utilities.clamp(scale, 2.0f, 0.25f);
        if (save && ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putFloat("glass_blur_scale", glassBlurScale).apply();
        }
        GlassEngine.notifyGlassSettingsChanged();
    }

    public static void setGlassBlurScale(float scale) {
        setGlassBlurScale(scale, true);
    }

    public static float getGlassOpacityScale() {
        load();
        return glassOpacityScale;
    }

    public static void setGlassOpacityScale(float scale, boolean save) {
        load();
        glassOpacityScale = Utilities.clamp(scale, 1.40f, 0.30f);
        if (save && ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putFloat("glass_opacity_scale", glassOpacityScale).apply();
        }
        GlassEngine.notifyGlassSettingsChanged();
    }

    public static void setGlassOpacityScale(float scale) {
        setGlassOpacityScale(scale, true);
    }

    public static float getEffectiveGlassAlpha(float baseAlpha) {
        if (baseAlpha <= 0f) {
            return 0f;
        }
        float scale = getGlassOpacityScale();
        if (Math.abs(scale - 1.0f) < 0.001f) {
            return baseAlpha;
        }
        float minAlpha = Math.min(0.06f, baseAlpha * 0.35f);
        return Utilities.clamp(baseAlpha * scale, 0.95f, minAlpha);
    }

    public static float getGlassSaturationScale() {
        load();
        return glassSaturationScale;
    }

    public static void setGlassSaturationScale(float scale, boolean save) {
        load();
        glassSaturationScale = Utilities.clamp(scale, 1.50f, 0.50f);
        if (save && ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putFloat("glass_saturation_scale", glassSaturationScale).apply();
        }
        GlassEngine.notifyGlassSettingsChanged();
    }

    public static void setGlassSaturationScale(float scale) {
        setGlassSaturationScale(scale, true);
    }

    public static float getEffectiveGlassSaturation() {
        return 3.0f * getGlassSaturationScale();
    }

    public static float getGlassRefractScale() {
        load();
        return glassRefractScale;
    }

    public static void setGlassRefractScale(float scale, boolean save) {
        load();
        glassRefractScale = Utilities.clamp(scale, 2.0f, 0.0f);
        if (save && ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putFloat("glass_refract_scale", glassRefractScale).apply();
        }
        GlassEngine.notifyGlassSettingsChanged();
    }

    public static void setGlassRefractScale(float scale) {
        setGlassRefractScale(scale, true);
    }

    public static int getGlassThicknessDp() {
        load();
        return glassThicknessDp;
    }

    public static void setGlassThicknessDp(int dpValue, boolean save) {
        load();
        glassThicknessDp = Math.max(4, Math.min(20, dpValue));
        if (save && ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putInt("glass_thickness_dp", glassThicknessDp).apply();
        }
        GlassEngine.notifyGlassSettingsChanged();
    }

    public static void setGlassThicknessDp(int dpValue) {
        setGlassThicknessDp(dpValue, true);
    }

    public static float getGlassRimScale() {
        load();
        return glassRimScale;
    }

    public static void setGlassRimScale(float scale, boolean save) {
        load();
        glassRimScale = Utilities.clamp(scale, 2.0f, 0.0f);
        if (save && ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putFloat("glass_rim_scale", glassRimScale).apply();
        }
        GlassEngine.notifyGlassSettingsChanged();
    }

    public static void setGlassRimScale(float scale) {
        setGlassRimScale(scale, true);
    }

    public static void saveGlassSettings() {
        load();
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit()
                    .putFloat("glass_blur_scale", glassBlurScale)
                    .putFloat("glass_opacity_scale", glassOpacityScale)
                    .putFloat("glass_saturation_scale", glassSaturationScale)
                    .putFloat("glass_refract_scale", glassRefractScale)
                    .putInt("glass_thickness_dp", glassThicknessDp)
                    .putFloat("glass_rim_scale", glassRimScale)
                    .apply();
        }
    }

    public static boolean isGlassBlurEnabled() {
        return LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR);
    }

    public static void setGlassBlurEnabled(boolean enabled) {
        if (LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR) != enabled) {
            LiteMode.toggleFlag(LiteMode.FLAG_CHAT_BLUR, enabled);
        }
        GlassEngine.notifyGlassSettingsChanged();
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyGlassSettingsDidChange));
    }

    public static boolean isGlassLiquidSupported() {
        return Build.VERSION.SDK_INT >= 33;
    }

    public static boolean isGlassLiquidEnabled() {
        return Build.VERSION.SDK_INT >= 33 && LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS);
    }

    public static void setGlassLiquidEnabled(boolean enabled) {
        if (enabled && !LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR)) {
            LiteMode.toggleFlag(LiteMode.FLAG_CHAT_BLUR, true);
        }
        if (LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) != enabled) {
            LiteMode.toggleFlag(LiteMode.FLAG_LIQUID_GLASS, enabled);
        }
        GlassEngine.notifyGlassSettingsChanged();
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyGlassSettingsDidChange));
    }

    public static boolean isDialogsGlassHeader() {
        load();
        return dialogsGlassHeader;
    }

    public static void setDialogsGlassHeader(boolean enabled) {
        load();
        dialogsGlassHeader = enabled;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putBoolean("dialogs_glass_header", enabled).apply();
        }
        GlassEngine.notifyGlassSettingsChanged();
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyGlassSettingsDidChange));
    }

    public static boolean isLiquidGlassEverywhere() {
        load();
        return glassLiquidEverywhere;
    }

    public static void setLiquidGlassEverywhere(boolean enabled) {
        load();
        glassLiquidEverywhere = enabled;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putBoolean("glass_liquid_everywhere", enabled).apply();
        }
        if (enabled) {
            if (!LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR)) {
                LiteMode.toggleFlag(LiteMode.FLAG_CHAT_BLUR, true);
            }
            if (isGlassLiquidSupported() && !LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS)) {
                LiteMode.toggleFlag(LiteMode.FLAG_LIQUID_GLASS, true);
            }
        }
        GlassEngine.notifyGlassSettingsChanged();
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyGlassSettingsDidChange));
    }

    public static void resetGlassDefaults() {
        load();
        glassBlurScale = 1.0f;
        glassOpacityScale = 1.0f;
        glassSaturationScale = 1.0f;
        glassRefractScale = 1.0f;
        glassThicknessDp = 11;
        glassRimScale = 1.0f;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit()
                    .putFloat("glass_blur_scale", glassBlurScale)
                    .putFloat("glass_opacity_scale", glassOpacityScale)
                    .putFloat("glass_saturation_scale", glassSaturationScale)
                    .putFloat("glass_refract_scale", glassRefractScale)
                    .putInt("glass_thickness_dp", glassThicknessDp)
                    .putFloat("glass_rim_scale", glassRimScale)
                    .apply();
        }
        GlassEngine.notifyGlassSettingsChanged();
    }

    public static int getMonetMode() {
        load();
        return monetMode;
    }

    public static void setMonetMode(int mode) {
        load();
        monetMode = mode;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putInt("monet_mode", mode).apply();
        }
    }

    public static int getMonetPaletteSignature() {
        load();
        return monetPaletteSignature;
    }

    public static void setMonetPaletteSignature(int signature) {
        load();
        monetPaletteSignature = signature;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putInt("monet_palette_signature", signature).apply();
        }
    }

    public static void setMonetAmoled(boolean value) {
        load();
        monetAmoled = value;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putBoolean("monet_amoled", value).apply();
        }
    }

    public static boolean isMonetOutGradient() {
        load();
        return monetOutGradient;
    }

    public static void setMonetOutGradient(boolean value) {
        load();
        monetOutGradient = value;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putBoolean("monet_out_gradient", value).apply();
        }
    }

    public static boolean isMonetThemedWallpaper() {
        load();
        return monetThemedWallpaper;
    }

    public static void setMonetThemedWallpaper(boolean value) {
        load();
        monetThemedWallpaper = value;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putBoolean("monet_themed_wallpaper", value).apply();
        }
    }

    public static void setMapProvider(int provider) {
        mapProvider = MAP_PROVIDER_YANDEX;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putInt("map_provider", MAP_PROVIDER_YANDEX).apply();
        }
    }

    public static String getEffectiveYandexMapKitApiKey() {
        return BuildConfig.YANDEX_MAPKIT_API_KEY != null ? BuildConfig.YANDEX_MAPKIT_API_KEY.trim() : "";
    }

    public static void setEffectiveSocks5Port(int port) {
        effectiveSocks5Port = port;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putInt("effective_socks5_port", port).apply();
        }
    }

    public static void setEffectiveMtprotoPort(int port) {
        effectiveMtprotoPort = port;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putInt("effective_mtproto_port", port).apply();
        }
    }

    public static final int TRANSLATOR_PROVIDER_AUTO = 0;
    public static final int TRANSLATOR_PROVIDER_GOOGLE = 1;
    public static final int TRANSLATOR_PROVIDER_LINGVA = 2;
    public static final int TRANSLATOR_PROVIDER_MYMEMORY = 3;
    public static final int TRANSLATOR_PROVIDER_LIBRE = 4;
    public static final int TRANSLATOR_PROVIDER_DEEPL = 5;
    public static final int TRANSLATOR_PROVIDER_YANDEX = 6;
    public static final int TRANSLATOR_PROVIDER_GEMINI = 7;

    public static final int TRANSLATE_SCOPE_ALL = 0;
    public static final int TRANSLATE_SCOPE_INCOMING = 1;

    public static final int STT_MODE_TRIAL_EXHAUSTED = 0;
    public static final int STT_MODE_ALWAYS = 1;

    public static final int STT_PROVIDER_AUTO = 0;
    public static final int STT_PROVIDER_SYSTEM = 1;
    public static final int STT_PROVIDER_GROQ = 2;
    public static final int STT_PROVIDER_GEMINI = 3;
    public static final int STT_PROVIDER_CUSTOM = 4;

    private static volatile boolean translatorPrefsLoaded = false;
    private static boolean msgTranslateButtonEnabled = false;
    private static int msgTranslateButtonScope = TRANSLATE_SCOPE_ALL;
    private static int translatorProvider = TRANSLATOR_PROVIDER_AUTO;
    private static String translatorTargetLang = "";
    private static String translatorCustomUrl = "";
    private static String translatorDeepLApiKey = "";
    private static String translatorYandexApiKey = "";
    private static String translatorYandexFolderId = "";
    private static String geminiApiKey = "";
    private static boolean translatorShowProviderToast = false;
    private static boolean freeSttEnabled = false;
    private static int freeSttMode = STT_MODE_TRIAL_EXHAUSTED;
    private static int freeSttProvider = STT_PROVIDER_AUTO;
    private static String freeSttLanguage = "auto";
    private static boolean freeSttPreferOffline = false;
    private static String freeSttGroqApiKey = "";
    private static String freeSttCustomUrl = "";
    private static String freeSttCustomApiKey = "";

    private static void ensureTranslatorPrefsLoaded() {
        if (translatorPrefsLoaded || ApplicationLoader.applicationContext == null) {
            return;
        }
        synchronized (MirrlyConfig.class) {
            if (translatorPrefsLoaded) {
                return;
            }
            SharedPreferences prefs = getPrefs();
            msgTranslateButtonEnabled = prefs.getBoolean("mirrly_msg_translate_btn", false);
            msgTranslateButtonScope = Math.max(0, Math.min(1, prefs.getInt("mirrly_msg_translate_scope", TRANSLATE_SCOPE_ALL)));
            translatorProvider = Math.max(0, Math.min(7, prefs.getInt("mirrly_translator_provider", TRANSLATOR_PROVIDER_AUTO)));
            translatorTargetLang = safeTrim(prefs.getString("mirrly_translator_target_lang", ""));
            translatorCustomUrl = safeTrim(prefs.getString("mirrly_translator_custom_url", ""));
            translatorDeepLApiKey = safeTrim(prefs.getString("mirrly_translator_deepl_key", ""));
            translatorYandexApiKey = safeTrim(prefs.getString("mirrly_translator_yandex_key", ""));
            translatorYandexFolderId = safeTrim(prefs.getString("mirrly_translator_yandex_folder", ""));
            geminiApiKey = safeTrim(prefs.getString("mirrly_gemini_api_key", ""));
            translatorShowProviderToast = prefs.getBoolean("mirrly_translator_show_provider", false);
            freeSttEnabled = prefs.getBoolean("mirrly_free_stt_enabled", false);
            freeSttMode = Math.max(0, Math.min(1, prefs.getInt("mirrly_free_stt_mode", STT_MODE_TRIAL_EXHAUSTED)));
            freeSttProvider = Math.max(0, Math.min(4, prefs.getInt("mirrly_free_stt_provider", STT_PROVIDER_AUTO)));
            String sttLang = safeTrim(prefs.getString("mirrly_free_stt_lang", "auto"));
            freeSttLanguage = sttLang.isEmpty() ? "auto" : sttLang;
            freeSttPreferOffline = prefs.getBoolean("mirrly_free_stt_prefer_offline", false);
            freeSttGroqApiKey = safeTrim(prefs.getString("mirrly_free_stt_groq_key", ""));
            freeSttCustomUrl = safeTrim(prefs.getString("mirrly_free_stt_custom_url", ""));
            freeSttCustomApiKey = safeTrim(prefs.getString("mirrly_free_stt_custom_key", ""));
            translatorPrefsLoaded = true;
        }
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static void notifyTranslatorSettingsChanged() {
        org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(
                org.telegram.messenger.NotificationCenter.mirrlyTranslatorSettingsChanged
        );
    }

    public static boolean isMsgTranslateButtonEnabled() {
        ensureTranslatorPrefsLoaded();
        return msgTranslateButtonEnabled;
    }

    public static void setMsgTranslateButtonEnabled(boolean enabled) {
        ensureTranslatorPrefsLoaded();
        msgTranslateButtonEnabled = enabled;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putBoolean("mirrly_msg_translate_btn", enabled).apply();
        }
        notifyTranslatorSettingsChanged();
    }

    public static int getMsgTranslateButtonScope() {
        ensureTranslatorPrefsLoaded();
        return msgTranslateButtonScope;
    }

    public static void setMsgTranslateButtonScope(int scope) {
        ensureTranslatorPrefsLoaded();
        msgTranslateButtonScope = scope == TRANSLATE_SCOPE_INCOMING ? TRANSLATE_SCOPE_INCOMING : TRANSLATE_SCOPE_ALL;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putInt("mirrly_msg_translate_scope", msgTranslateButtonScope).apply();
        }
        notifyTranslatorSettingsChanged();
    }

    public static int getTranslatorProvider() {
        ensureTranslatorPrefsLoaded();
        switch (translatorProvider) {
            case TRANSLATOR_PROVIDER_LIBRE:
                return TextUtils.isEmpty(translatorCustomUrl) ? TRANSLATOR_PROVIDER_AUTO : TRANSLATOR_PROVIDER_LIBRE;
            case TRANSLATOR_PROVIDER_DEEPL:
                return TextUtils.isEmpty(translatorDeepLApiKey) ? TRANSLATOR_PROVIDER_AUTO : TRANSLATOR_PROVIDER_DEEPL;
            case TRANSLATOR_PROVIDER_YANDEX:
                return TextUtils.isEmpty(translatorYandexApiKey) ? TRANSLATOR_PROVIDER_AUTO : TRANSLATOR_PROVIDER_YANDEX;
            case TRANSLATOR_PROVIDER_GEMINI:
                return TextUtils.isEmpty(geminiApiKey) ? TRANSLATOR_PROVIDER_AUTO : TRANSLATOR_PROVIDER_GEMINI;
            default:
                return translatorProvider;
        }
    }

    public static void setTranslatorProvider(int provider) {
        ensureTranslatorPrefsLoaded();
        translatorProvider = Math.max(TRANSLATOR_PROVIDER_AUTO, Math.min(TRANSLATOR_PROVIDER_GEMINI, provider));
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putInt("mirrly_translator_provider", translatorProvider).apply();
        }
        notifyTranslatorSettingsChanged();
    }

    public static String getTranslatorTargetLang() {
        ensureTranslatorPrefsLoaded();
        return translatorTargetLang;
    }

    public static void setTranslatorTargetLang(String lang) {
        ensureTranslatorPrefsLoaded();
        translatorTargetLang = safeTrim(lang);
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putString("mirrly_translator_target_lang", translatorTargetLang).apply();
        }
        notifyTranslatorSettingsChanged();
    }

    public static String getTranslatorCustomUrl() {
        ensureTranslatorPrefsLoaded();
        return translatorCustomUrl;
    }

    public static void setTranslatorCustomUrl(String url) {
        ensureTranslatorPrefsLoaded();
        translatorCustomUrl = safeTrim(url);
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putString("mirrly_translator_custom_url", translatorCustomUrl).apply();
        }
        notifyTranslatorSettingsChanged();
    }

    public static String getTranslatorDeepLApiKey() {
        ensureTranslatorPrefsLoaded();
        return translatorDeepLApiKey;
    }

    public static void setTranslatorDeepLApiKey(String key) {
        ensureTranslatorPrefsLoaded();
        translatorDeepLApiKey = safeTrim(key);
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putString("mirrly_translator_deepl_key", translatorDeepLApiKey).apply();
        }
        notifyTranslatorSettingsChanged();
    }

    public static String getTranslatorYandexApiKey() {
        ensureTranslatorPrefsLoaded();
        return translatorYandexApiKey;
    }

    public static void setTranslatorYandexApiKey(String key) {
        ensureTranslatorPrefsLoaded();
        translatorYandexApiKey = safeTrim(key);
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putString("mirrly_translator_yandex_key", translatorYandexApiKey).apply();
        }
        notifyTranslatorSettingsChanged();
    }

    public static String getTranslatorYandexFolderId() {
        ensureTranslatorPrefsLoaded();
        return translatorYandexFolderId;
    }

    public static void setTranslatorYandexFolderId(String folderId) {
        ensureTranslatorPrefsLoaded();
        translatorYandexFolderId = safeTrim(folderId);
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putString("mirrly_translator_yandex_folder", translatorYandexFolderId).apply();
        }
        notifyTranslatorSettingsChanged();
    }

    public static String getGeminiApiKey() {
        ensureTranslatorPrefsLoaded();
        return geminiApiKey;
    }

    public static void setGeminiApiKey(String key) {
        ensureTranslatorPrefsLoaded();
        geminiApiKey = safeTrim(key);
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putString("mirrly_gemini_api_key", geminiApiKey).apply();
        }
        notifyTranslatorSettingsChanged();
    }

    public static boolean isTranslatorShowProviderToast() {
        ensureTranslatorPrefsLoaded();
        return translatorShowProviderToast;
    }

    public static void setTranslatorShowProviderToast(boolean show) {
        ensureTranslatorPrefsLoaded();
        translatorShowProviderToast = show;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putBoolean("mirrly_translator_show_provider", show).apply();
        }
        notifyTranslatorSettingsChanged();
    }

    public static boolean isFreeSttEnabled() {
        ensureTranslatorPrefsLoaded();
        return freeSttEnabled;
    }

    public static void setFreeSttEnabled(boolean enabled) {
        ensureTranslatorPrefsLoaded();
        freeSttEnabled = enabled;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putBoolean("mirrly_free_stt_enabled", enabled).apply();
        }
        notifyTranslatorSettingsChanged();
    }

    public static int getFreeSttMode() {
        ensureTranslatorPrefsLoaded();
        return freeSttMode;
    }

    public static void setFreeSttMode(int mode) {
        ensureTranslatorPrefsLoaded();
        freeSttMode = mode == STT_MODE_ALWAYS ? STT_MODE_ALWAYS : STT_MODE_TRIAL_EXHAUSTED;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putInt("mirrly_free_stt_mode", freeSttMode).apply();
        }
        notifyTranslatorSettingsChanged();
    }

    public static int getFreeSttProvider() {
        ensureTranslatorPrefsLoaded();
        switch (freeSttProvider) {
            case STT_PROVIDER_GROQ:
                return TextUtils.isEmpty(freeSttGroqApiKey) ? STT_PROVIDER_AUTO : STT_PROVIDER_GROQ;
            case STT_PROVIDER_GEMINI:
                return TextUtils.isEmpty(geminiApiKey) ? STT_PROVIDER_AUTO : STT_PROVIDER_GEMINI;
            case STT_PROVIDER_CUSTOM:
                return TextUtils.isEmpty(freeSttCustomUrl) ? STT_PROVIDER_AUTO : STT_PROVIDER_CUSTOM;
            default:
                return freeSttProvider;
        }
    }

    public static void setFreeSttProvider(int provider) {
        ensureTranslatorPrefsLoaded();
        freeSttProvider = Math.max(STT_PROVIDER_AUTO, Math.min(STT_PROVIDER_CUSTOM, provider));
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putInt("mirrly_free_stt_provider", freeSttProvider).apply();
        }
        notifyTranslatorSettingsChanged();
    }

    public static String getFreeSttLanguage() {
        ensureTranslatorPrefsLoaded();
        return freeSttLanguage;
    }

    public static void setFreeSttLanguage(String lang) {
        ensureTranslatorPrefsLoaded();
        String trimmed = safeTrim(lang);
        freeSttLanguage = trimmed.isEmpty() ? "auto" : trimmed;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putString("mirrly_free_stt_lang", freeSttLanguage).apply();
        }
        notifyTranslatorSettingsChanged();
    }

    public static boolean isFreeSttPreferOffline() {
        ensureTranslatorPrefsLoaded();
        return freeSttPreferOffline;
    }

    public static void setFreeSttPreferOffline(boolean preferOffline) {
        ensureTranslatorPrefsLoaded();
        freeSttPreferOffline = preferOffline;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putBoolean("mirrly_free_stt_prefer_offline", preferOffline).apply();
        }
        notifyTranslatorSettingsChanged();
    }

    public static String getFreeSttGroqApiKey() {
        ensureTranslatorPrefsLoaded();
        return freeSttGroqApiKey;
    }

    public static void setFreeSttGroqApiKey(String key) {
        ensureTranslatorPrefsLoaded();
        freeSttGroqApiKey = safeTrim(key);
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putString("mirrly_free_stt_groq_key", freeSttGroqApiKey).apply();
        }
        notifyTranslatorSettingsChanged();
    }

    public static String getFreeSttCustomUrl() {
        ensureTranslatorPrefsLoaded();
        return freeSttCustomUrl;
    }

    public static void setFreeSttCustomUrl(String url) {
        ensureTranslatorPrefsLoaded();
        freeSttCustomUrl = safeTrim(url);
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putString("mirrly_free_stt_custom_url", freeSttCustomUrl).apply();
        }
        notifyTranslatorSettingsChanged();
    }

    public static String getFreeSttCustomApiKey() {
        ensureTranslatorPrefsLoaded();
        return freeSttCustomApiKey;
    }

    public static void setFreeSttCustomApiKey(String key) {
        ensureTranslatorPrefsLoaded();
        freeSttCustomApiKey = safeTrim(key);
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putString("mirrly_free_stt_custom_key", freeSttCustomApiKey).apply();
        }
        notifyTranslatorSettingsChanged();
    }

    public static String getChatTranslateToLang(long dialogId) {
        if (ApplicationLoader.applicationContext == null || dialogId == 0) {
            return "";
        }
        String cached = CHAT_TRANSLATE_LANG_CACHE.get(dialogId);
        if (cached != null) {
            return cached;
        }
        String value = safeTrim(getPrefs().getString("mirrly_chat_translate_lang_" + dialogId, ""));
        if (CHAT_TRANSLATE_LANG_CACHE.size() > 512) {
            CHAT_TRANSLATE_LANG_CACHE.clear();
        }
        CHAT_TRANSLATE_LANG_CACHE.put(dialogId, value);
        return value;
    }

    public static void setChatTranslateToLang(long dialogId, String lang) {
        if (ApplicationLoader.applicationContext == null || dialogId == 0) {
            return;
        }
        String trimmed = safeTrim(lang);
        if (CHAT_TRANSLATE_LANG_CACHE.size() > 512) {
            CHAT_TRANSLATE_LANG_CACHE.clear();
        }
        CHAT_TRANSLATE_LANG_CACHE.put(dialogId, trimmed);
        SharedPreferences.Editor editor = getPrefs().edit();
        if (trimmed.isEmpty()) {
            editor.remove("mirrly_chat_translate_lang_" + dialogId);
        } else {
            editor.putString("mirrly_chat_translate_lang_" + dialogId, trimmed);
        }
        editor.apply();
    }

    public static String maskSecretKey(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.length() <= 6) {
            return "••••••";
        }
        return "••••" + trimmed.substring(trimmed.length() - 4);
    }

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
    }

    public static void setSaveEditHistory(boolean value) {
        saveEditHistory = value;
        getPrefs().edit().putBoolean("save_edit_history", value).apply();
    }

    public static void setConfirmMediaSend(boolean value) {
        confirmMediaSend = value;
        getPrefs().edit().putBoolean("confirm_media_send", value).apply();
    }

    public static boolean isRoundVideoBackCamera() {
        load();
        return roundVideoBackCamera;
    }

    public static void setRoundVideoBackCamera(boolean value) {
        load();
        roundVideoBackCamera = value;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putBoolean("round_video_back_camera", value).apply();
        }
        SharedSettings.roundVideoLastCamera.set(
                value ? RoundVideoSession.CameraFacing.BACK : RoundVideoSession.CameraFacing.FRONT
        );
    }

    public static boolean isInChatSendSoundEnabled() {
        load();
        return inChatSendSound;
    }

    public static void setInChatSendSound(boolean value) {
        load();
        inChatSendSound = value;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putBoolean("in_chat_send_sound", value).apply();
        }
    }

    public static boolean isInChatReceiveSoundEnabled() {
        load();
        return inChatReceiveSound;
    }

    public static void setInChatReceiveSound(boolean value) {
        load();
        inChatReceiveSound = value;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putBoolean("in_chat_receive_sound", value).apply();
        }
    }

    public static boolean isDisableAllVibrations() {
        load();
        return disableAllVibrations;
    }

    public static void setDisableAllVibrations(boolean value) {
        load();
        if (disableAllVibrations == value) {
            return;
        }
        disableAllVibrations = value;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putBoolean("disable_all_vibrations", value).apply();
        }
        MirrlyHaptics.onDisableAllVibrationsChanged(value);
        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyVibrationSettingsChanged)
        );
    }

    private static int normalizeOwnChatTagStyle(int style) {
        return style == OWN_CHAT_TAG_INSIDE_BUBBLE ? OWN_CHAT_TAG_INSIDE_BUBBLE : OWN_CHAT_TAG_PILL_ABOVE;
    }

    public static boolean isShowOwnChatTagEnabled() {
        load();
        return showOwnChatTag;
    }

    public static void setShowOwnChatTag(boolean value) {
        load();
        if (showOwnChatTag == value) {
            return;
        }
        showOwnChatTag = value;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putBoolean("show_own_chat_tag", value).apply();
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyChatTagSettingsChanged);
    }

    public static int getOwnChatTagStyle() {
        load();
        return normalizeOwnChatTagStyle(ownChatTagStyle);
    }

    public static void setOwnChatTagStyle(int style) {
        load();
        int normalized = normalizeOwnChatTagStyle(style);
        if (ownChatTagStyle == normalized) {
            return;
        }
        ownChatTagStyle = normalized;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putInt("own_chat_tag_style", normalized).apply();
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyChatTagSettingsChanged);
    }

    public static void setTlsEnabled(boolean value) {
        tlsEnabled = value;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putBoolean("tls_enabled", value).apply();
        }
        MirrlyEngineController.applyTlsEnabled(value);
    }

    public static void setTlsClientHelloSplit(boolean value) {
        tlsClientHelloSplit = value;
        getPrefs().edit().putBoolean("tls_client_hello_split", value).apply();
        MirrlyEngineController.applyTlsSplit(value);
    }

    public static void setFlowsealPerDcEnabled(boolean value) {
        flowsealPerDcEnabled = value;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putBoolean("flowseal_per_dc_enabled", value).apply();
        }
        MirrlyEngineController.applyFlowsealPerDcEnabled(value);
        MirrlyEngineController.softReconnect();
        MirrlyDatacenterController.invalidateCache();
        try {
            org.telegram.tgnet.ConnectionsManager.getInstance(org.telegram.messenger.UserConfig.selectedAccount).resumeNetworkMaybe();
        } catch (Throwable ignore) {
        }
        org.telegram.messenger.AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged)
        );
    }

    public static void setWorkerDomain(String domain) {
        workerDomain = domain != null ? domain.trim() : "";
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putString("worker_domain", workerDomain).apply();
            MirrlyEngineController.nativeSetCfProxyConfig(workerDomain, "");
        }
    }

    public static void setCfProxyEnabled(boolean enabled) {
        cfProxyEnabled = enabled;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putBoolean("cf_proxy_enabled", enabled).apply();
            MirrlyEngineController.nativeSetCfProxyConfig(enabled ? (workerDomain != null ? workerDomain : "") : "", "");
        }
    }

    public static synchronized String getOrCreateMtprotoSecret(String generatedFallback) {
        load();
        if (!TextUtils.isEmpty(mtprotoSecret) && mtprotoSecret.length() >= 32) {
            return mtprotoSecret;
        }
        String secret = generatedFallback;
        if (TextUtils.isEmpty(secret)) {
            secret = "ee0123456789abcdef0123456789abcdef7777772e676f6f676c652e636f6d";
        }
        mtprotoSecret = secret;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putString("mtproto_secret", secret).apply();
        }
        return mtprotoSecret;
    }

    public static String extractSecretCore32(String secret) {
        if (TextUtils.isEmpty(secret)) {
            return "0123456789abcdef0123456789abcdef";
        }
        String trimmed = secret.trim().toLowerCase(java.util.Locale.US);
        if ((trimmed.startsWith("ee") || trimmed.startsWith("dd")) && trimmed.length() >= 34) {
            return trimmed.substring(2, 34);
        }
        if (trimmed.length() >= 32) {
            return trimmed.substring(0, 32);
        }
        return "0123456789abcdef0123456789abcdef";
    }

    public static synchronized String getEffectiveMtprotoSecret(String generatedFallback) {
        String baseSecret = getOrCreateMtprotoSecret(generatedFallback);
        if (tlsEnabled) {
            return baseSecret;
        }
        return "dd" + extractSecretCore32(baseSecret);
    }

    private static int normalizeDeletedAlphaPercent(int percent) {
        return Math.max(15, Math.min(100, percent));
    }

    private static int normalizeDeletedLocalPolicy(int policy) {
        return policy == DELETED_LOCAL_POLICY_ALWAYS_KEEP ? DELETED_LOCAL_POLICY_ALWAYS_KEEP : DELETED_LOCAL_POLICY_ASK;
    }

    public static boolean isSaveDeletedMessages() {
        load();
        return saveDeletedMessages;
    }

    public static void setSaveDeletedMessages(boolean value) {
        load();
        if (saveDeletedMessages == value) {
            return;
        }
        saveDeletedMessages = value;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putBoolean("save_deleted_messages", value).apply();
        }
        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyDeletedAppearanceChanged);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyDeletedDidUpdate);
        });
    }

    public static int getDeletedMessageAlphaPercent() {
        load();
        return normalizeDeletedAlphaPercent(deletedMessageAlphaPercent);
    }

    public static float getDeletedMessageAlphaFactor() {
        return getDeletedMessageAlphaPercent() / 100.0f;
    }

    public static void setDeletedMessageAlphaPercent(int percent, boolean save) {
        load();
        int normalized = normalizeDeletedAlphaPercent(percent);
        boolean changed = deletedMessageAlphaPercent != normalized;
        deletedMessageAlphaPercent = normalized;
        if (save && ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putInt("deleted_message_alpha_percent", normalized).apply();
        }
        if (changed) {
            AndroidUtilities.runOnUIThread(() ->
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyDeletedAppearanceChanged)
            );
        }
    }

    public static void setDeletedMessageAlphaPercent(int percent) {
        setDeletedMessageAlphaPercent(percent, true);
    }

    public static boolean isDeletedTrashIconEnabled() {
        load();
        return deletedTrashIconEnabled;
    }

    public static void setDeletedTrashIconEnabled(boolean enabled) {
        load();
        if (deletedTrashIconEnabled == enabled) {
            return;
        }
        deletedTrashIconEnabled = enabled;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putBoolean("deleted_trash_icon_enabled", enabled).apply();
        }
        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyDeletedAppearanceChanged)
        );
    }

    public static int getDeletedTrashIconColor() {
        load();
        return deletedTrashIconColor;
    }

    public static int getEffectiveDeletedTrashIconColor() {
        int c = getDeletedTrashIconColor();
        return c != 0 ? c : DEFAULT_DELETED_TRASH_COLOR;
    }

    public static boolean isDeletedTrashCustomColor() {
        return getDeletedTrashIconColor() != 0;
    }

    public static void setDeletedTrashIconColor(int colorOrZero) {
        load();
        if (deletedTrashIconColor == colorOrZero) {
            return;
        }
        deletedTrashIconColor = colorOrZero;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putInt("deleted_trash_icon_color", colorOrZero).apply();
        }
        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyDeletedAppearanceChanged)
        );
    }

    public static int getDeletedLocalDeletePolicy() {
        load();
        return normalizeDeletedLocalPolicy(deletedLocalDeletePolicy);
    }

    public static void setDeletedLocalDeletePolicy(int policy) {
        load();
        int normalized = normalizeDeletedLocalPolicy(policy);
        if (deletedLocalDeletePolicy == normalized) {
            return;
        }
        deletedLocalDeletePolicy = normalized;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putInt("deleted_local_delete_policy", normalized).apply();
        }
    }

    public static boolean isSaveDeletedChats() {
        load();
        return saveDeletedChats;
    }

    public static void setSaveDeletedChats(boolean value) {
        load();
        if (saveDeletedChats == value) {
            return;
        }
        saveDeletedChats = value;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putBoolean("save_deleted_chats", value).apply();
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyDeletedChatsDidUpdate);
    }

    private static int normalizePowerPushInterval(int minutes) {
        if (minutes >= POWER_PUSH_INTERVAL_20_MIN) {
            return POWER_PUSH_INTERVAL_20_MIN;
        }
        if (minutes >= POWER_PUSH_INTERVAL_10_MIN) {
            return POWER_PUSH_INTERVAL_10_MIN;
        }
        return POWER_PUSH_INTERVAL_3_MIN;
    }

    public static int getPowerAutoEcoLevel() {
        load();
        return powerAutoEcoLevel;
    }

    public static void setPowerAutoEcoLevel(int level) {
        load();
        int clamped = Math.max(0, Math.min(100, level));
        if (powerAutoEcoLevel == clamped) {
            return;
        }
        powerAutoEcoLevel = clamped;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putInt("power_auto_eco_level", clamped).apply();
        }
        MirrlyPowerPolicy.evaluateAsync();
    }

    public static boolean isPowerFollowSystemSaver() {
        load();
        return powerFollowSystemSaver;
    }

    public static void setPowerFollowSystemSaver(boolean enabled) {
        load();
        if (powerFollowSystemSaver == enabled) {
            return;
        }
        powerFollowSystemSaver = enabled;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putBoolean("power_follow_system_saver", enabled).apply();
        }
        MirrlyPowerPolicy.evaluateAsync();
    }

    public static int getPowerPushIntervalMinutes() {
        load();
        return normalizePowerPushInterval(powerPushIntervalMinutes);
    }

    public static void setPowerPushIntervalMinutes(int minutes) {
        load();
        int normalized = normalizePowerPushInterval(minutes);
        if (powerPushIntervalMinutes == normalized) {
            return;
        }
        powerPushIntervalMinutes = normalized;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putInt("power_push_interval_min", normalized).apply();
        }
        MirrlyPowerPolicy.onPushIntervalConfigChanged();
        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyPowerPolicyChanged)
        );
    }

    public static int getPowerEcoMask() {
        load();
        return powerEcoMask;
    }

    public static boolean isPowerEcoFlagEnabled(int flag) {
        load();
        return (powerEcoMask & flag) != 0;
    }

    public static void setPowerEcoFlag(int flag, boolean enabled) {
        load();
        int next = enabled ? (powerEcoMask | flag) : (powerEcoMask & ~flag);
        if (powerEcoMask == next) {
            return;
        }
        powerEcoMask = next;
        if (ApplicationLoader.applicationContext != null) {
            getPrefs().edit().putInt("power_eco_mask", next).apply();
        }
        MirrlyPowerPolicy.reconcileIfActive();
        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyPowerPolicyChanged)
        );
    }

    static SharedPreferences getPowerPrefs() {
        return getPrefs();
    }
}
