package org.telegram.messenger.mirrly;

import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.ConnectionsManager;

import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Coordinates Telegram datacenter (DC1-DC5) reachability and latency measurements
 * through the active connection route with 2-minute caching and stale-callback guards.
 */
public final class MirrlyDatacenterController {

    public static final int DC_COUNT = 5;
    public static final long CACHE_TTL_MS = 2 * 60 * 1000L;
    public static final long SLOW_PING_THRESHOLD_MS = 1000L;

    public static final class DatacenterInfo {
        public final int id;
        public long pingId;
        public long ping;
        public boolean checking;
        public boolean available;
        public long availableCheckTime;
        public long generation;

        DatacenterInfo(int id) {
            this.id = id;
        }
    }

    private static final DatacenterInfo[] DATACENTERS = new DatacenterInfo[DC_COUNT];

    static {
        for (int i = 0; i < DC_COUNT; i++) {
            DATACENTERS[i] = new DatacenterInfo(i + 1);
        }
    }

    private MirrlyDatacenterController() {
    }

    public static DatacenterInfo getDatacenter(int dcId) {
        if (dcId < 1 || dcId > DC_COUNT) {
            return null;
        }
        return DATACENTERS[dcId - 1];
    }

    public static boolean isAnyChecking() {
        for (DatacenterInfo info : DATACENTERS) {
            if (info.checking) {
                return true;
            }
        }
        return false;
    }

    public static void invalidateCache() {
        for (DatacenterInfo info : DATACENTERS) {
            info.generation++;
            info.checking = false;
            info.availableCheckTime = 0;
        }
    }

    public static void checkAll(int account, boolean force) {
        for (int dcId = 1; dcId <= DC_COUNT; dcId++) {
            checkDatacenter(account, dcId, force);
        }
    }

    public static void checkDatacenter(int account, int dcId, boolean force) {
        DatacenterInfo info = getDatacenter(dcId);
        if (info == null) {
            return;
        }
        if (info.checking) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (!force && info.availableCheckTime > 0 && now - info.availableCheckTime < CACHE_TTL_MS) {
            return;
        }
        info.checking = true;
        final long expectedGeneration = ++info.generation;
        if (force) {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyDatacenterCheckDone, dcId);
        }

        long pingId = ConnectionsManager.getInstance(account).checkDatacenter(dcId, time -> AndroidUtilities.runOnUIThread(() -> {
            if (info.generation != expectedGeneration) {
                return;
            }
            info.availableCheckTime = SystemClock.elapsedRealtime();
            info.checking = false;
            if (time < 0) {
                info.available = false;
                info.ping = 0;
            } else {
                info.available = true;
                info.ping = time;
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyDatacenterCheckDone, dcId);
        }));

        if (pingId == 0) {
            info.availableCheckTime = SystemClock.elapsedRealtime();
            info.checking = false;
            info.available = false;
            info.ping = 0;
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyDatacenterCheckDone, dcId);
        } else {
            info.pingId = pingId;
        }
    }

    public static String getDCName(int dcId) {
        switch (dcId) {
            case 1:
                return "Pluto";
            case 2:
                return "Venus";
            case 3:
                return "Aurora";
            case 4:
                return "Vesta";
            case 5:
                return "Flora";
            default:
                return "Unknown";
        }
    }

    public static String getDCLocation(int dcId) {
        switch (dcId) {
            case 1:
            case 3:
                return "Miami";
            case 2:
            case 4:
                return "Amsterdam";
            case 5:
                return "Singapore";
            default:
                return "Unknown";
        }
    }

    public static String formatDCTitle(int dcId) {
        return String.format(Locale.US, "DC%d %s, %s", dcId, getDCName(dcId), getDCLocation(dcId));
    }

    private static final String[] FLOWSEAL_BASES = new String[]{
            "pclead", "offshor", "cakeisalie", "noskomnadzor", "lovetrue",
            "sorokdva", "pyatdesyatdva", "kartoshka", "sorokodin", "pyatdesyatodin",
            "notelega", "ebally", "nebally", "havegreatday", "pomogite",
            "fixtelega", "sadnews", "onedaychamp", "stopblocking", "nothingthere"
    };

    private static final String[] SANITIZED_CLUSTER_NAMES = new String[]{
            "Edge Phoenix", "Edge Nova", "Edge Vega", "Edge Zenith", "Edge Pulse",
            "Edge Nexus", "Edge Horizon", "Edge Polaris", "Edge Cosmos", "Edge Sirius",
            "Edge Astra", "Edge Apex", "Edge Titan", "Edge Aurora", "Edge Hyper",
            "Edge Quantum", "Edge Eclipse", "Edge Helios", "Edge Vesper", "Edge Centauri"
    };

    public static String getClusterNameForDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return "Edge Core";
        }
        String clean = domain.trim().toLowerCase(Locale.US);
        if (clean.contains("://")) {
            clean = clean.substring(clean.indexOf("://") + 3);
        }
        int slash = clean.indexOf('/');
        if (slash >= 0) {
            clean = clean.substring(0, slash);
        }
        int colon = clean.indexOf(':');
        if (colon >= 0) {
            clean = clean.substring(0, colon);
        }
        if (clean.startsWith("kws") && clean.contains(".")) {
            int dot = clean.indexOf('.');
            String prefix = clean.substring(3, dot);
            boolean isKws = true;
            for (int i = 0; i < prefix.length(); i++) {
                char c = prefix.charAt(i);
                if (!Character.isDigit(c) && c != '-') {
                    isKws = false;
                    break;
                }
            }
            if (isKws) {
                clean = clean.substring(dot + 1);
            }
        }
        if (clean.endsWith(".co.uk")) {
            clean = clean.substring(0, clean.length() - 6);
        } else if (clean.endsWith(".com")) {
            clean = clean.substring(0, clean.length() - 4);
        } else if (clean.endsWith(".net")) {
            clean = clean.substring(0, clean.length() - 4);
        } else if (clean.endsWith(".org")) {
            clean = clean.substring(0, clean.length() - 4);
        }

        for (int i = 0; i < FLOWSEAL_BASES.length; i++) {
            if (clean.equals(FLOWSEAL_BASES[i]) || clean.contains(FLOWSEAL_BASES[i])) {
                return SANITIZED_CLUSTER_NAMES[i];
            }
        }

        int hash = Math.abs(domain.hashCode());
        return "Edge Node " + ((hash % 50) + 1);
    }

    public static String getDcCdnClusterName(int dcId, boolean isMedia) {
        boolean perDc = MirrlyConfig.flowsealPerDcEnabled;
        if (!perDc) {
            String activeDomain = extractActiveRouteDomain(0, false);
            if (activeDomain != null && !activeDomain.isEmpty()) {
                return getClusterNameForDomain(activeDomain);
            }
            return SANITIZED_CLUSTER_NAMES[0];
        }

        String activeDomain = extractActiveRouteDomain(dcId, isMedia);
        if (activeDomain != null && !activeDomain.isEmpty()) {
            return getClusterNameForDomain(activeDomain);
        }

        int index = ((dcId - 1) + (isMedia ? 5 : 0)) % SANITIZED_CLUSTER_NAMES.length;
        if (index < 0) {
            index = 0;
        }
        return SANITIZED_CLUSTER_NAMES[index];
    }

    private static String extractActiveRouteDomain(int targetDcId, boolean targetIsMedia) {
        try {
            String jsonStr = MirrlyEngineController.getFlowsealDcConfigJson();
            if (jsonStr == null || jsonStr.isEmpty() || "{}".equals(jsonStr)) {
                return null;
            }
            JSONObject obj = new JSONObject(jsonStr);
            if (obj.has("active_routes")) {
                JSONArray routes = obj.optJSONArray("active_routes");
                if (routes != null) {
                    for (int i = 0; i < routes.length(); i++) {
                        JSONObject route = routes.optJSONObject(i);
                        if (route != null) {
                            int dcId = route.optInt("dc_id", 0);
                            boolean isMedia = route.optBoolean("is_media", false);
                            String domain = route.optString("domain", null);
                            if (domain != null && !domain.isEmpty()) {
                                if (targetDcId == 0 || (dcId == targetDcId && isMedia == targetIsMedia)) {
                                    return domain;
                                }
                            }
                        }
                    }
                }
            }
            if (targetDcId == 0 && obj.has("available_domains")) {
                JSONArray av = obj.optJSONArray("available_domains");
                if (av != null && av.length() > 0) {
                    return av.optString(0, null);
                }
            }
        } catch (Throwable ignore) {
        }
        return null;
    }
}
