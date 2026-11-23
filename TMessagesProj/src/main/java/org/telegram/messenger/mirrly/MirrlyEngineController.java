package org.telegram.messenger.mirrly;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.Keep;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.utils.proxy.ProxySettings;

@Keep
public final class MirrlyEngineController {

    private static final String DEFAULT_DC_IP_LIST =
            "1:149.154.175.50\n" +
            "2:149.154.167.220\n" +
            "3:149.154.175.100\n" +
            "4:149.154.167.92\n" +
            "5:91.108.56.100";

    private static volatile boolean libraryLoaded = false;
    private static volatile boolean engineStarted = false;

    static {
        try {
            System.loadLibrary("mirrlyengine");
            libraryLoaded = true;
        } catch (Throwable t) {
            FileLog.e("Failed to load libmirrlyengine.so", t);
        }
    }

    private MirrlyEngineController() {
    }

    public static boolean isLibraryLoaded() {
        return libraryLoaded;
    }

    public static boolean isPortAvailable(String host, int port) {
        try (java.net.ServerSocket ss = new java.net.ServerSocket()) {
            ss.setReuseAddress(true);
            ss.bind(new java.net.InetSocketAddress(host, port));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static int findAvailablePort(String host, int startPort) {
        for (int p = startPort; p < startPort + 50; p++) {
            if (isPortAvailable(host, p)) {
                return p;
            }
        }
        return startPort;
    }

    public static String generateMtprotoSecret() {
        if (libraryLoaded) {
            try {
                String sec = nativeGenerateSecret("www.google.com");
                if (!TextUtils.isEmpty(sec) && sec.length() >= 34) {
                    return sec;
                }
            } catch (Throwable ignore) {
            }
        }
        java.security.SecureRandom random = new java.security.SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder("ee");
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        sb.append("7777772e676f6f676c652e636f6d");
        return sb.toString();
    }

    public static void ensureStarted() {
        MirrlyConfig.load();
        if (engineStarted) {
            return;
        }
        ensureStartedInternal();
    }

    private static synchronized void ensureStartedInternal() {
        MirrlyConfig.load();
        if (!libraryLoaded) {
            try {
                System.loadLibrary("mirrlyengine");
                libraryLoaded = true;
            } catch (Throwable t) {
                FileLog.e("Retry load libmirrlyengine.so failed", t);
                return;
            }
        }
        if (engineStarted) {
            return;
        }
        try {
            nativeSetTlsEnabled(MirrlyConfig.tlsEnabled);
            nativeSetTlsClientHelloSplit(MirrlyConfig.tlsClientHelloSplit);
            nativeSetTlsFingerprintProfile("chrome124");
            try {
                nativeSetFlowsealPerDcEnabled(MirrlyConfig.flowsealPerDcEnabled);
            } catch (UnsatisfiedLinkError ignore) {
            }

            // CRITICAL: Activate Cloudflare Anycast CDN / Worker upstream tunnel in mirrlyengine!
            // When userDomain is empty, mirrlyengine uses the embedded Anycast CDN pool with Fast Anycast Race.
            // This sets CFPROXY_ENABLED=true in the Rust core so SOCKS5 and MTProto bypass RKN blocking.
            String worker = (MirrlyConfig.cfProxyEnabled && MirrlyConfig.workerDomain != null) ? MirrlyConfig.workerDomain.trim() : "";
            nativeSetCfProxyConfig(worker, "");

            String secret = MirrlyConfig.getEffectiveMtprotoSecret(generateMtprotoSecret());
            nativeSetSecret(secret);

            int targetSocksPort = MirrlyConfig.DEFAULT_SOCKS5_PORT;
            boolean socksBusy = false;
            String socksErr = nativeStartSocks5(MirrlyConfig.LOCALHOST, targetSocksPort);
            if ("PORT_BUSY".equals(socksErr)) {
                socksBusy = true;
                for (int p = targetSocksPort + 1; p < targetSocksPort + 50; p++) {
                    String err = nativeStartSocks5(MirrlyConfig.LOCALHOST, p);
                    if (!"PORT_BUSY".equals(err)) {
                        targetSocksPort = p;
                        break;
                    }
                }
            }
            MirrlyConfig.setEffectiveSocks5Port(targetSocksPort);
            FileLog.d("MirrlyEngine SOCKS5 listening on 127.0.0.1:" + targetSocksPort);

            int targetMtprotoPort = MirrlyConfig.DEFAULT_MTPROTO_PORT;
            boolean mtprotoBusy = false;
            String mtprotoErr = nativeStartMtproto(
                    MirrlyConfig.LOCALHOST,
                    targetMtprotoPort,
                    secret,
                    DEFAULT_DC_IP_LIST
            );
            if ("PORT_BUSY".equals(mtprotoErr)) {
                mtprotoBusy = true;
                for (int p = targetMtprotoPort + 1; p < targetMtprotoPort + 50; p++) {
                    String err = nativeStartMtproto(MirrlyConfig.LOCALHOST, p, secret, DEFAULT_DC_IP_LIST);
                    if (!"PORT_BUSY".equals(err)) {
                        targetMtprotoPort = p;
                        break;
                    }
                }
            }
            MirrlyConfig.setEffectiveMtprotoPort(targetMtprotoPort);
            FileLog.d("MirrlyEngine MTProto listening on 127.0.0.1:" + targetMtprotoPort);

            engineStarted = true;
            notifyNetworkState();

            if (socksBusy || mtprotoBusy) {
                final int sPort = targetSocksPort;
                final boolean sB = socksBusy;
                final int mPort = targetMtprotoPort;
                final boolean mB = mtprotoBusy;
                org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                    try {
                        String msg;
                        if (sB && mB) {
                            msg = org.telegram.messenger.LocaleController.formatString(
                                    org.telegram.messenger.R.string.MirrlyEnginePortsBusyBoth,
                                    sPort,
                                    mPort
                            );
                        } else if (sB) {
                            msg = org.telegram.messenger.LocaleController.formatString(
                                    org.telegram.messenger.R.string.MirrlyEnginePortBusySocks,
                                    MirrlyConfig.DEFAULT_SOCKS5_PORT,
                                    sPort
                            );
                        } else {
                            msg = org.telegram.messenger.LocaleController.formatString(
                                    org.telegram.messenger.R.string.MirrlyEnginePortBusyMtproto,
                                    MirrlyConfig.DEFAULT_MTPROTO_PORT,
                                    mPort
                            );
                        }
                        org.telegram.ui.Components.BulletinFactory.global().createSimpleBulletin(
                                org.telegram.messenger.R.raw.chats_infotip,
                                msg
                        ).show();
                    } catch (Throwable t) {
                        FileLog.e(t);
                    }
                }, 1000);
            }
        } catch (Throwable t) {
            FileLog.e("Failed to start MirrlyEngine", t);
        }
    }

    public static void ensureDefaultProxyPreconfigured() {
        MirrlyConfig.load();
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        SharedPreferences preferences = context.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        boolean seeded = preferences.getBoolean("mirrly_default_proxy_v1", false);
        if (!seeded) {
            ProxySettings socks5Settings = buildSocks5Settings();
            SharedPreferences.Editor editor = preferences.edit();
            socks5Settings.toSharedPreferences(editor);
            editor.putBoolean("proxy_enabled", true);
            editor.putBoolean("mirrly_default_proxy_v1", true);
            editor.apply();
            ConnectionsManager.setProxySettings(true, socks5Settings);
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (!engineStarted) {
                Utilities.globalQueue.postRunnable(() -> {
                    ensureStarted();
                    ensureDefaultProxyPreconfiguredInternal();
                });
                return;
            }
        } else {
            ensureStarted();
        }
        ensureDefaultProxyPreconfiguredInternal();
    }

    private static synchronized void ensureDefaultProxyPreconfiguredInternal() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        SharedPreferences preferences = context.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        boolean seeded = preferences.getBoolean("mirrly_default_proxy_v1", false);
        ProxySettings currentSettings = ProxySettings.fromSharedPreferences(preferences);
        boolean isLocalProxy = MirrlyConfig.LOCALHOST.equals(currentSettings.getAddress());

        if (!seeded) {
            ProxySettings socks5Settings = buildSocks5Settings();
            SharedPreferences.Editor editor = preferences.edit();
            socks5Settings.toSharedPreferences(editor);
            editor.putBoolean("proxy_enabled", true);
            editor.putBoolean("mirrly_default_proxy_v1", true);
            editor.apply();
            ConnectionsManager.setProxySettings(true, socks5Settings);
        } else if (isLocalProxy) {
            ProxySettings updatedSettings = null;
            if (currentSettings.getType() == ProxySettings.Type.MTPROTO) {
                String secret = MirrlyConfig.getEffectiveMtprotoSecret("");
                if (currentSettings.getPort() != MirrlyConfig.effectiveMtprotoPort
                        || !secret.equals(currentSettings.getSecret())) {
                    updatedSettings = buildMtprotoSettings(secret);
                }
            } else if (currentSettings.getType() == ProxySettings.Type.SOCKS5) {
                if (currentSettings.getPort() != MirrlyConfig.effectiveSocks5Port) {
                    updatedSettings = buildSocks5Settings();
                }
            }
            if (updatedSettings != null) {
                SharedPreferences.Editor editor = preferences.edit();
                updatedSettings.toSharedPreferences(editor);
                editor.apply();
                if (preferences.getBoolean("proxy_enabled", false)) {
                    ConnectionsManager.setProxySettings(true, updatedSettings);
                }
            }
        }
    }

    public static synchronized void syncProxyProfilesInSharedConfig() {
        ensureStarted();
        String secret = MirrlyConfig.getEffectiveMtprotoSecret("");

        ProxySettings socks5Settings = buildSocks5Settings();
        ProxySettings mtprotoSettings = buildMtprotoSettings(secret);

        SharedConfig.ProxyInfo existingSocks = findProxyByType(MirrlyConfig.LOCALHOST, ProxySettings.Type.SOCKS5);
        int oldSocksPort = (existingSocks != null && existingSocks.settings != null) ? existingSocks.settings.getPort() : 0;
        SharedConfig.ProxyInfo socks5Info = upsertLocalProxy(ProxySettings.Type.SOCKS5, socks5Settings, 0);

        SharedConfig.ProxyInfo existingMtproto = findProxyByType(MirrlyConfig.LOCALHOST, ProxySettings.Type.MTPROTO);
        int oldMtprotoPort = (existingMtproto != null && existingMtproto.settings != null) ? existingMtproto.settings.getPort() : 0;
        String oldMtprotoSecret = (existingMtproto != null && existingMtproto.settings != null) ? existingMtproto.settings.getSecret() : "";
        int socksIdx = SharedConfig.proxyList.indexOf(socks5Info);
        SharedConfig.ProxyInfo mtprotoInfo = upsertLocalProxy(ProxySettings.Type.MTPROTO, mtprotoSettings, socksIdx >= 0 ? socksIdx + 1 : 0);

        boolean currentUpdated = false;
        if (SharedConfig.currentProxy == null) {
            SharedConfig.currentProxy = socks5Info;
            currentUpdated = true;
        } else if (SharedConfig.currentProxy.settings != null
                && MirrlyConfig.LOCALHOST.equals(SharedConfig.currentProxy.settings.getAddress())) {
            if (SharedConfig.currentProxy.settings.getType() == ProxySettings.Type.SOCKS5) {
                if (oldSocksPort != MirrlyConfig.effectiveSocks5Port || SharedConfig.currentProxy.settings.getPort() != MirrlyConfig.effectiveSocks5Port) {
                    SharedConfig.currentProxy = socks5Info;
                    currentUpdated = true;
                }
            } else if (SharedConfig.currentProxy.settings.getType() == ProxySettings.Type.MTPROTO) {
                if (oldMtprotoPort != MirrlyConfig.effectiveMtprotoPort
                        || !secret.equals(oldMtprotoSecret)
                        || SharedConfig.currentProxy.settings.getPort() != MirrlyConfig.effectiveMtprotoPort
                        || !secret.equals(SharedConfig.currentProxy.settings.getSecret())) {
                    SharedConfig.currentProxy = mtprotoInfo;
                    currentUpdated = true;
                }
            }
        }

        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        int currentPort = preferences.getInt("proxy_port", 0);
        String currentAddress = preferences.getString("proxy_ip", "");
        String currentPrefSecret = preferences.getString("proxy_secret", "");
        if (MirrlyConfig.LOCALHOST.equals(currentAddress) && SharedConfig.currentProxy != null && SharedConfig.currentProxy.settings != null) {
            if (currentPort != SharedConfig.currentProxy.settings.getPort()
                    || (SharedConfig.currentProxy.settings.getType() == ProxySettings.Type.MTPROTO
                    && !secret.equals(currentPrefSecret))) {
                currentUpdated = true;
            }
        }

        boolean listSeeded = preferences.getBoolean("mirrly_proxy_list_seeded_v1", false);
        if (!listSeeded || currentUpdated) {
            if (SharedConfig.currentProxy == null) {
                SharedConfig.currentProxy = socks5Info;
            }
            boolean shouldEnable = !listSeeded || preferences.getBoolean("proxy_enabled", true);
            SharedPreferences.Editor editor = preferences.edit();
            SharedConfig.currentProxy.settings.toSharedPreferences(editor);
            editor.putBoolean("proxy_enabled", shouldEnable);
            editor.putBoolean("mirrly_proxy_list_seeded_v1", true);
            editor.apply();
            if (shouldEnable) {
                ConnectionsManager.setProxySettings(true, SharedConfig.currentProxy.settings);
            }
        }
        SharedConfig.saveProxyList();
    }

    private static ProxySettings buildSocks5Settings() {
        return ProxySettings.builder()
                .setType(ProxySettings.Type.SOCKS5)
                .setAddress(MirrlyConfig.LOCALHOST)
                .setPort(MirrlyConfig.effectiveSocks5Port)
                .setUser("")
                .setPassword("")
                .build();
    }

    private static ProxySettings buildMtprotoSettings(String secret) {
        return ProxySettings.builder()
                .setType(ProxySettings.Type.MTPROTO)
                .setAddress(MirrlyConfig.LOCALHOST)
                .setPort(MirrlyConfig.effectiveMtprotoPort)
                .setSecret(secret)
                .build();
    }

    private static SharedConfig.ProxyInfo upsertLocalProxy(ProxySettings.Type type, ProxySettings targetSettings, int insertIndex) {
        SharedConfig.ProxyInfo keep = null;
        if (SharedConfig.currentProxy != null && SharedConfig.currentProxy.settings != null
                && SharedConfig.currentProxy.settings.getType() == type
                && MirrlyConfig.LOCALHOST.equals(SharedConfig.currentProxy.settings.getAddress())
                && SharedConfig.proxyList.contains(SharedConfig.currentProxy)) {
            keep = SharedConfig.currentProxy;
        } else {
            keep = findProxyByType(MirrlyConfig.LOCALHOST, type);
        }
        if (keep == null) {
            keep = new SharedConfig.ProxyInfo(targetSettings);
            int idx = Math.max(0, Math.min(insertIndex, SharedConfig.proxyList.size()));
            SharedConfig.proxyList.add(idx, keep);
        } else {
            keep.settings = targetSettings;
        }
        for (int i = SharedConfig.proxyList.size() - 1; i >= 0; i--) {
            SharedConfig.ProxyInfo info = SharedConfig.proxyList.get(i);
            if (info != keep && info != null && info.settings != null
                    && info.settings.getType() == type
                    && MirrlyConfig.LOCALHOST.equals(info.settings.getAddress())) {
                if (SharedConfig.currentProxy == info) {
                    SharedConfig.currentProxy = keep;
                }
                SharedConfig.proxyList.remove(i);
            }
        }
        return keep;
    }

    private static SharedConfig.ProxyInfo findProxyByType(String host, ProxySettings.Type type) {
        if (SharedConfig.proxyList == null) {
            return null;
        }
        for (int i = 0; i < SharedConfig.proxyList.size(); i++) {
            SharedConfig.ProxyInfo info = SharedConfig.proxyList.get(i);
            if (info != null && info.settings != null
                    && info.settings.getType() == type
                    && host.equals(info.settings.getAddress())) {
                return info;
            }
        }
        return null;
    }

    public static void switchActiveTransport(boolean useMtproto) {
        SharedConfig.loadProxyList();
        syncProxyProfilesInSharedConfig();
        SharedConfig.ProxyInfo target = useMtproto
                ? findProxyByType(MirrlyConfig.LOCALHOST, ProxySettings.Type.MTPROTO)
                : findProxyByType(MirrlyConfig.LOCALHOST, ProxySettings.Type.SOCKS5);
        if (target == null) {
            return;
        }
        SharedConfig.currentProxy = target;
        SharedPreferences preferences = MessagesControllerPrefs();
        SharedPreferences.Editor editor = preferences.edit();
        target.settings.toSharedPreferences(editor);
        editor.putBoolean("proxy_enabled", true);
        editor.apply();
        SharedConfig.saveProxyList();
        ConnectionsManager.setProxySettings(true, target.settings);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
    }

    public static boolean isLocalProxyActive() {
        SharedConfig.loadProxyList();
        return SharedConfig.isProxyEnabled()
                && SharedConfig.currentProxy != null
                && SharedConfig.currentProxy.settings != null
                && MirrlyConfig.LOCALHOST.equals(SharedConfig.currentProxy.settings.getAddress());
    }

    public static boolean isMtprotoSelected() {
        return isLocalProxyActive()
                && SharedConfig.currentProxy.settings.getType() == ProxySettings.Type.MTPROTO;
    }

    public static boolean isSocks5Selected() {
        return isLocalProxyActive()
                && SharedConfig.currentProxy.settings.getType() == ProxySettings.Type.SOCKS5;
    }

    private static SharedPreferences MessagesControllerPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
    }

    public static void softReconnect() {
        if (!libraryLoaded) {
            return;
        }
        try {
            nativeSoftReconnect();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static String getStatsJson() {
        if (!libraryLoaded) {
            return "{}";
        }
        try {
            String json = nativeGetStatsJson();
            return json != null ? json : "{}";
        } catch (Throwable t) {
            return "{}";
        }
    }

    public static void applyTlsEnabled(boolean enabled) {
        if (!libraryLoaded) {
            return;
        }
        try {
            nativeSetTlsEnabled(enabled);
            String effectiveSecret = MirrlyConfig.getEffectiveMtprotoSecret("");
            nativeSetSecret(effectiveSecret);
        } catch (Throwable t) {
            FileLog.e(t);
        }
        if (ApplicationLoader.applicationContext == null) {
            return;
        }
        try {
            SharedConfig.loadProxyList();
            if (engineStarted) {
                syncProxyProfilesInSharedConfig();
            }
            boolean isLocalActive = SharedConfig.isProxyEnabled()
                    && SharedConfig.currentProxy != null
                    && SharedConfig.currentProxy.settings != null
                    && MirrlyConfig.LOCALHOST.equals(SharedConfig.currentProxy.settings.getAddress());
            if (isLocalActive) {
                ConnectionsManager.setProxySettings(true, SharedConfig.currentProxy.settings);
                org.telegram.messenger.AndroidUtilities.runOnUIThread(() ->
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged)
                );
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static void applyTlsSplit(boolean enabled) {
        if (!libraryLoaded) {
            return;
        }
        try {
            nativeSetTlsClientHelloSplit(enabled);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static void applyFlowsealPerDcEnabled(boolean enabled) {
        if (!libraryLoaded) {
            return;
        }
        try {
            nativeSetFlowsealPerDcEnabled(enabled);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static String getFlowsealDcConfigJson() {
        if (!libraryLoaded) {
            return "{}";
        }
        try {
            String json = nativeGetFlowsealDcConfigJson();
            return json != null ? json : "{}";
        } catch (Throwable t) {
            FileLog.e(t);
            return "{}";
        }
    }

    public static void notifyNetworkState() {
        if (!libraryLoaded || ApplicationLoader.applicationContext == null) {
            return;
        }
        try {
            ConnectivityManager cm = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean connected = false;
            boolean isWifi = false;
            boolean isCellular = false;
            if (cm != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.net.Network activeNetwork = cm.getActiveNetwork();
                android.net.NetworkCapabilities caps = activeNetwork != null ? cm.getNetworkCapabilities(activeNetwork) : null;
                if (caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    connected = true;
                    isWifi = caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                            || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET);
                    isCellular = caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR);
                }
            }
            if (!connected) {
                NetworkInfo info = cm != null ? cm.getActiveNetworkInfo() : null;
                if (info != null && info.isConnectedOrConnecting()) {
                    connected = true;
                    isWifi = info.getType() == ConnectivityManager.TYPE_WIFI || info.getType() == ConnectivityManager.TYPE_ETHERNET;
                    isCellular = info.getType() == ConnectivityManager.TYPE_MOBILE;
                }
            }
            if (!connected && ApplicationLoader.isNetworkOnlineRealtime()) {
                connected = true;
                isWifi = ApplicationLoader.isConnectedOrConnectingToWiFi();
                isCellular = !isWifi;
            }
            nativeOnNetworkStateChanged(connected, isWifi, isCellular);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static void notifyScreenWake() {
        if (!libraryLoaded) {
            return;
        }
        try {
            nativeOnScreenWake();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static native String nativeStartSocks5(String ip, int port);
    public static native String nativeStartMtproto(String ip, int port, String secretHex, String upstreamConfig);
    public static native void nativeStopProxy();
    public static native void nativeSoftReconnect();
    public static native String nativeGetStatsJson();
    public static native String nativeGenerateSecret(String domain);
    public static native int nativeSetSecret(String secretHex);
    public static native long nativeOnNetworkStateChanged(boolean connected, boolean isWifi, boolean isCellular);
    public static native void nativeOnScreenWake();
    public static native void nativeSetCfProxyConfig(String domain, String secret);
    public static native void nativeSetTlsEnabled(boolean enabled);
    public static native boolean nativeIsTlsEnabled();
    public static native void nativeSetTlsClientHelloSplit(boolean enabled);
    public static native int nativeSetTlsFingerprintProfile(String profile);
    public static native void nativeResetNetworkSockets();
    public static native void nativeSetFlowsealPerDcEnabled(boolean enabled);
    public static native boolean nativeIsFlowsealPerDcEnabled();
    public static native boolean nativeSetFlowsealDcDomain(int dcId, int isMedia, String domain);
    public static native boolean nativeSetFlowsealDcDomainsJson(String json);
    public static native void nativeClearFlowsealDcDomains();
    public static native String nativeGetFlowsealDcConfigJson();
}
