package org.telegram.messenger.mirrly;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.utils.recorder.round.RoundVideoSession;
import org.telegram.utils.settings.SharedSettings;
import org.telegram.messenger.BuildConfig;

public final class MirrlyConfig {

    private static final String PREFS_NAME = "mirrly_config";
    private static final String KEY_TLS_DEFAULT_MIGRATED_V1 = "tls_default_disabled_migrated_v1";
    private static final String KEY_PORTS_DEFAULT_MIGRATED_V1 = "ports_default_10889_1444_migrated_v1";

    public static final int MAP_PROVIDER_GOOGLE = 0;
    public static final int MAP_PROVIDER_YANDEX = 1;

    public static final int DEFAULT_SOCKS5_PORT = 10889;
    public static final int DEFAULT_MTPROTO_PORT = 1444;
    public static final String LOCALHOST = "127.0.0.1";

    public static volatile int effectiveSocks5Port = DEFAULT_SOCKS5_PORT;
    public static volatile int effectiveMtprotoPort = DEFAULT_MTPROTO_PORT;

    public static volatile boolean saveEditHistory = true;
    public static volatile boolean confirmMediaSend = false;
    public static volatile boolean roundVideoBackCamera = false;
    public static volatile boolean tlsEnabled = false;
    public static volatile boolean tlsClientHelloSplit = true;
    public static volatile boolean cfProxyEnabled = true;
    public static volatile String workerDomain = "";
    public static volatile String mtprotoSecret = "";
    public static volatile int mapProvider = MAP_PROVIDER_YANDEX;

    private static volatile boolean loaded = false;

    private MirrlyConfig() {
    }

    public static synchronized void load() {
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
                    .commit();
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
            editor.commit();
        }
        saveEditHistory = prefs.getBoolean("save_edit_history", true);
        confirmMediaSend = prefs.getBoolean("confirm_media_send", false);
        roundVideoBackCamera = prefs.getBoolean("round_video_back_camera", false);
        tlsEnabled = prefs.getBoolean("tls_enabled", false);
        tlsClientHelloSplit = prefs.getBoolean("tls_client_hello_split", true);
        cfProxyEnabled = prefs.getBoolean("cf_proxy_enabled", true);
        workerDomain = prefs.getString("worker_domain", "");
        mtprotoSecret = prefs.getString("mtproto_secret", "");
        mapProvider = MAP_PROVIDER_YANDEX;
        effectiveSocks5Port = prefs.getInt("effective_socks5_port", DEFAULT_SOCKS5_PORT);
        effectiveMtprotoPort = prefs.getInt("effective_mtproto_port", DEFAULT_MTPROTO_PORT);
        loaded = true;
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
}
