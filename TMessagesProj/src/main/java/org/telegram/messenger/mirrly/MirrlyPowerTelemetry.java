package org.telegram.messenger.mirrly;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.util.LongSparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Passive, zero-WakeLock real power analytics engine for MirrlyGram's own subsystems:
 * MirrlyEngine (Rust Tokio {@code mirrly-rt}), background push keep-alive ({@code NotificationsService}),
 * Glass/Liquid Glass/Monet rendering, local Vault/Deleted interceptors, Translator/STT workers,
 * and Mirrly Haptics/Chats/Interface features.
 * <p>
 * Combines real Android {@link BatteryManager}/{@link PowerManager} hardware counters,
 * per-thread Linux {@code /proc/self/task/&#42;/stat} CPU accounting, and zero-allocation
 * subsystem hooks calibrated against the device's physical coulomb counter ({@code CHARGE_COUNTER}).
 */
public final class MirrlyPowerTelemetry {

    public static final int MODULE_ENGINE = 0;
    public static final int MODULE_PUSH = 1;
    public static final int MODULE_GLASS = 2;
    public static final int MODULE_VAULT = 3;
    public static final int MODULE_TRANSLATOR = 4;
    public static final int MODULE_INTERFACE = 5;
    public static final int MODULE_COUNT = 6;

    public static final int PERIOD_SESSION = 0;
    public static final int PERIOD_24H = 1;
    public static final int PERIOD_7D = 2;

    public static final int VAULT_KIND_DELETED_MSG = 0;
    public static final int VAULT_KIND_DELETED_CHAT = 1;
    public static final int VAULT_KIND_EDIT_HISTORY = 2;
    public static final int VAULT_KIND_NOTE = 3;

    public static final int NET_NONE = 0;
    public static final int NET_WIFI = 1;
    public static final int NET_CELLULAR = 2;

    public static final int REC_PUSH_INTERVAL = 1;
    public static final int REC_LIQUID_EVERYWHERE = 2;
    public static final int REC_FLOWSEAL_PER_DC = 3;
    public static final int REC_MONET_AMOLED = 4;
    public static final int REC_SYSTEM_UNRESTRICTED = 5;

    private static final String DB_NAME = "mirrly_power_stats.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE_HOURLY = "hourly_buckets";
    private static final String TABLE_SESSION = "session_buckets";

    private static final String PREF_CALIBRATED_CAP_MAH = "power_calibrated_cap_mah_v1";
    private static final double DEFAULT_CAPACITY_MAH = 4500.0;
    private static final long PASSIVE_SAMPLE_MIN_INTERVAL_MS = 45_000L;
    private static final long SESSION_BUCKET_MS = 2 * 60_000L; // 2-minute resolution for quick live feedback
    private static final long HOUR_MS = 3_600_000L;
    private static final long DAY_MS = 24 * HOUR_MS;
    private static final long RETENTION_MS = 7 * DAY_MS;

    private static final int[] MODULE_COLOR_KEYS = new int[]{
            Theme.key_statisticChartLine_blue,
            Theme.key_statisticChartLine_red,
            Theme.key_statisticChartLine_purple,
            Theme.key_statisticChartLine_green,
            Theme.key_statisticChartLine_cyan,
            Theme.key_statisticChartLine_golden
    };

    private static final String[] MODULE_COLOR_TOKENS = new String[]{
            "blue#32A7F5",
            "red#F34C44",
            "purple#9B6BF2",
            "green#4BD964",
            "cyan#32C0CE",
            "golden#F5C143"
    };

    private static final DispatchQueue POWER_QUEUE = new DispatchQueue("mirrlyPowerQueue");
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final Object DB_LOCK = new Object();

    // Zero-allocation live atomic accumulators incremented directly by Mirrly subsystems
    private static final AtomicLong ACC_PUSH_WAKEUPS_ON = new AtomicLong();
    private static final AtomicLong ACC_PUSH_WAKEUPS_OFF = new AtomicLong();
    private static final AtomicLong ACC_PUSH_WAKELOCK_MS_ON = new AtomicLong();
    private static final AtomicLong ACC_PUSH_WAKELOCK_MS_OFF = new AtomicLong();
    private static final AtomicLong ACC_PUSH_ACCOUNTS_LAST = new AtomicLong(1);

    private static final AtomicLong ACC_GLASS_REBUILDS = new AtomicLong();
    private static final AtomicLong ACC_GLASS_WORK_NS = new AtomicLong();
    private static final AtomicLong ACC_GLASS_LIQUID_FRAMES = new AtomicLong();
    private static final AtomicLong ACC_GLASS_EVERYWHERE_FRAMES = new AtomicLong();
    private static final AtomicLong ACC_GLASS_VISIBLE_SUM = new AtomicLong();

    private static final AtomicLong ACC_VAULT_OPS = new AtomicLong();
    private static final AtomicLong ACC_VAULT_BYTES = new AtomicLong();
    private static final AtomicLong ACC_VAULT_WORK_NS = new AtomicLong();
    private static final AtomicLong ACC_VAULT_DEL_MSG_OPS = new AtomicLong();
    private static final AtomicLong ACC_VAULT_DEL_CHAT_OPS = new AtomicLong();
    private static final AtomicLong ACC_VAULT_EDIT_OPS = new AtomicLong();
    private static final AtomicLong ACC_VAULT_NOTE_OPS = new AtomicLong();

    private static final AtomicLong ACC_TRANSLATOR_OPS = new AtomicLong();
    private static final AtomicLong ACC_TRANSLATOR_BYTES = new AtomicLong();
    private static final AtomicLong ACC_STT_OPS = new AtomicLong();
    private static final AtomicLong ACC_STT_BYTES = new AtomicLong();

    private static final AtomicLong ACC_HAPTIC_PULSES = new AtomicLong();
    private static final AtomicLong ACC_HAPTIC_MS = new AtomicLong();
    private static final AtomicLong ACC_CHAT_SOUNDS = new AtomicLong();

    private static volatile boolean currentScreenOn = true;
    private static volatile boolean currentAppForeground = false;
    private static volatile long lastScreenToggleElapsedMs = 0L;
    private static volatile long accumulatedScreenOnMs = 0L;
    private static volatile long accumulatedScreenOffMs = 0L;

    private static volatile long sessionStartWallMs = System.currentTimeMillis();
    private static volatile long lastSampleElapsedMs = 0L;
    private static volatile long lastPassiveTriggerElapsedMs = 0L;
    private static volatile long lastPruneWallMs = 0L;

    // Previous engine & hardware baselines (accessed exclusively on POWER_QUEUE)
    private static long prevEngineBytesDown = -1L;
    private static long prevEngineBytesUp = -1L;
    private static long prevEngineTotalConn = -1L;
    private static long prevEngineBadAndWsErr = -1L;
    private static long prevEnginePoolMisses = -1L;

    private static long prevChargeCounterUah = -1L;
    private static boolean prevCharging = false;
    private static double calibratedCapacityMah = DEFAULT_CAPACITY_MAH;

    private static final LongSparseArray<Long> PREV_THREAD_TICKS = new LongSparseArray<>();
    private static long linuxClockTicksPerSec = 100L;
    private static final byte[] PROC_READ_BUF = new byte[512];

    private static volatile SystemPowerSnapshot latestSystemSnapshot = SystemPowerSnapshot.empty();
    private static volatile PowerReport cached24hBadgeReport;

    private static PowerDbHelper dbHelper;

    private MirrlyPowerTelemetry() {
    }

    // -------------------------------------------------------------------------
    // Public Lifecycle & Zero-Allocation Subsystem Instrumentation Hooks
    // -------------------------------------------------------------------------

    public static void init(@Nullable Context context) {
        if (context == null || !INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        final Context appCtx = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        sessionStartWallMs = System.currentTimeMillis();
        long nowEl = SystemClock.elapsedRealtime();
        lastScreenToggleElapsedMs = nowEl;
        lastSampleElapsedMs = nowEl;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                long tps = Os.sysconf(OsConstants._SC_CLK_TCK);
                if (tps > 0 && tps <= 10_000) {
                    linuxClockTicksPerSec = tps;
                }
            }
        } catch (Throwable ignore) {
        }

        POWER_QUEUE.postRunnable(() -> {
            try {
                MirrlyConfig.load();
                SharedPreferences prefs = MirrlyConfig.getPowerPrefs();
                float savedCap = prefs.getFloat(PREF_CALIBRATED_CAP_MAH, (float) DEFAULT_CAPACITY_MAH);
                if (savedCap >= 1500f && savedCap <= 12000f) {
                    calibratedCapacityMah = savedCap;
                }
                currentScreenOn = readInteractiveState(appCtx);
                seedBaselinesLocked(appCtx);
                sampleInternalLocked(appCtx, true);
                MirrlyPowerPolicy.evaluate(
                        latestSystemSnapshot.batteryLevel,
                        latestSystemSnapshot.isCharging,
                        latestSystemSnapshot.systemPowerSaveMode
                );
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });

        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
            }
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent intent) {
                    if (intent == null) {
                        return;
                    }
                    String action = intent.getAction();
                    if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                        handleBatteryBroadcast(appCtx, intent);
                    } else {
                        schedulePassiveSample(appCtx, true);
                    }
                }
            };
            Intent sticky;
            if (Build.VERSION.SDK_INT >= 33) {
                sticky = appCtx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                sticky = appCtx.registerReceiver(receiver, filter);
            }
            if (sticky != null) {
                handleBatteryBroadcast(appCtx, sticky);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static void onScreenStateChanged(boolean screenOn) {
        long now = SystemClock.elapsedRealtime();
        long prev = lastScreenToggleElapsedMs;
        if (prev > 0 && now > prev) {
            long dt = Math.min(now - prev, 6 * HOUR_MS);
            if (currentScreenOn) {
                accumulatedScreenOnMs += dt;
            } else {
                accumulatedScreenOffMs += dt;
            }
        }
        currentScreenOn = screenOn;
        lastScreenToggleElapsedMs = now;
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx != null && INITIALIZED.get()) {
            schedulePassiveSample(ctx, false);
        }
    }

    public static void onAppForegroundChanged(boolean foreground) {
        currentAppForeground = foreground;
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx != null && INITIALIZED.get()) {
            schedulePassiveSample(ctx, false);
        }
    }

    public static void recordPushWakeup(boolean screenOn, int activeAccounts) {
        if (screenOn) {
            ACC_PUSH_WAKEUPS_ON.incrementAndGet();
        } else {
            ACC_PUSH_WAKEUPS_OFF.incrementAndGet();
        }
        if (activeAccounts > 0) {
            ACC_PUSH_ACCOUNTS_LAST.set(activeAccounts);
        }
    }

    public static void recordPushWakeLockMs(long durationMs, boolean screenOn) {
        if (durationMs <= 0) {
            return;
        }
        long clamped = Math.min(durationMs, 60_000L);
        if (screenOn) {
            ACC_PUSH_WAKELOCK_MS_ON.addAndGet(clamped);
        } else {
            ACC_PUSH_WAKELOCK_MS_OFF.addAndGet(clamped);
        }
    }

    public static void sampleFromServiceAsync() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx != null && INITIALIZED.get()) {
            schedulePassiveSample(ctx, false);
        }
    }

    public static void recordGlassRebuild(int visibleDrawables, long workNs, boolean liquidActive, boolean everywhereActive) {
        if (visibleDrawables <= 0) {
            return;
        }
        ACC_GLASS_REBUILDS.incrementAndGet();
        ACC_GLASS_VISIBLE_SUM.addAndGet(Math.min(visibleDrawables, 32));
        if (workNs > 0) {
            ACC_GLASS_WORK_NS.addAndGet(Math.min(workNs, 200_000_000L));
        }
        if (liquidActive) {
            ACC_GLASS_LIQUID_FRAMES.incrementAndGet();
            if (everywhereActive) {
                ACC_GLASS_EVERYWHERE_FRAMES.incrementAndGet();
            }
        }
    }

    public static void recordVaultIo(int kind, long bytesWritten, long workNs) {
        ACC_VAULT_OPS.incrementAndGet();
        if (bytesWritten > 0) {
            ACC_VAULT_BYTES.addAndGet(bytesWritten);
        }
        if (workNs > 0) {
            ACC_VAULT_WORK_NS.addAndGet(Math.min(workNs, 5_000_000_000L));
        }
        switch (kind) {
            case VAULT_KIND_DELETED_MSG:
                ACC_VAULT_DEL_MSG_OPS.incrementAndGet();
                break;
            case VAULT_KIND_DELETED_CHAT:
                ACC_VAULT_DEL_CHAT_OPS.incrementAndGet();
                break;
            case VAULT_KIND_EDIT_HISTORY:
                ACC_VAULT_EDIT_OPS.incrementAndGet();
                break;
            case VAULT_KIND_NOTE:
                ACC_VAULT_NOTE_OPS.incrementAndGet();
                break;
        }
    }

    public static void recordTranslatorWork(long bytesTransferred, long durationMs) {
        ACC_TRANSLATOR_OPS.incrementAndGet();
        if (bytesTransferred > 0) {
            ACC_TRANSLATOR_BYTES.addAndGet(bytesTransferred);
        }
    }

    public static void recordSttWork(long audioBytes, long durationMs) {
        ACC_STT_OPS.incrementAndGet();
        if (audioBytes > 0) {
            ACC_STT_BYTES.addAndGet(audioBytes);
        }
    }

    public static void recordHapticPulse(long durationMs) {
        ACC_HAPTIC_PULSES.incrementAndGet();
        long ms = durationMs > 0 ? Math.min(durationMs, 2000L) : 18L;
        ACC_HAPTIC_MS.addAndGet(ms);
    }

    public static void recordChatSound() {
        ACC_CHAT_SOUNDS.incrementAndGet();
    }

    @NonNull
    public static SystemPowerSnapshot getLatestSystemSnapshot() {
        return latestSystemSnapshot;
    }

    /**
     * Compact right-side badge for MirrlyGramSettingsActivity hub row (<= 6 chars).
     */
    @NonNull
    public static String getHubShortBadge() {
        if (MirrlyPowerPolicy.isAutoEcoActive()) {
            return LocaleController.getString(R.string.MirrlyPowerHubBadgeEco);
        }
        PowerReport cached = cached24hBadgeReport;
        if (cached != null && cached.totalBatteryPercent >= 0.05) {
            return formatPercentCompact(cached.totalBatteryPercent);
        }
        SystemPowerSnapshot sys = latestSystemSnapshot;
        if (sys.batteryLevel >= 0) {
            return sys.batteryLevel + "%";
        }
        return "";
    }

    public static void sampleNowAndBuildReportAsync(int period, @NonNull Utilities.Callback<PowerReport> callback) {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) {
            return;
        }
        if (!INITIALIZED.get()) {
            init(ctx);
        }
        POWER_QUEUE.postRunnable(() -> {
            try {
                sampleInternalLocked(ctx, true);
                PowerReport report = buildReportLocked(ctx, period);
                if (period == PERIOD_24H || cached24hBadgeReport == null) {
                    cached24hBadgeReport = period == PERIOD_24H ? report : buildReportLocked(ctx, PERIOD_24H);
                }
                AndroidUtilities.runOnUIThread(() -> callback.run(report));
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    public static void refreshLiveSensorsAsync(@NonNull Utilities.Callback<SystemPowerSnapshot> callback) {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) {
            return;
        }
        POWER_QUEUE.postRunnable(() -> {
            try {
                SystemPowerSnapshot snap = readSystemPowerSnapshot(ctx, null);
                latestSystemSnapshot = snap;
                AndroidUtilities.runOnUIThread(() -> callback.run(snap));
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    public static void resetAllStatsAsync(@Nullable Runnable onDone) {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) {
            return;
        }
        POWER_QUEUE.postRunnable(() -> {
            try {
                synchronized (DB_LOCK) {
                    SQLiteDatabase db = getDbLocked(ctx);
                    if (db != null) {
                        db.delete(TABLE_HOURLY, null, null);
                        db.delete(TABLE_SESSION, null, null);
                    }
                }
                drainAllAtomics();
                sessionStartWallMs = System.currentTimeMillis();
                long nowEl = SystemClock.elapsedRealtime();
                lastSampleElapsedMs = nowEl;
                lastScreenToggleElapsedMs = nowEl;
                accumulatedScreenOnMs = 0L;
                accumulatedScreenOffMs = 0L;
                cached24hBadgeReport = null;
                seedBaselinesLocked(ctx);
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyPowerStatsUpdated);
                    if (onDone != null) {
                        onDone.run();
                    }
                });
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Passive Sampling & Hardware Calibration (POWER_QUEUE only)
    // -------------------------------------------------------------------------

    private static void handleBatteryBroadcast(@NonNull Context ctx, @NonNull Intent intent) {
        final Intent copy = new Intent(intent);
        long now = SystemClock.elapsedRealtime();
        boolean forceSample = (now - lastPassiveTriggerElapsedMs) >= PASSIVE_SAMPLE_MIN_INTERVAL_MS;
        int level = copy.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = copy.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = (level >= 0 && scale > 0) ? Math.max(0, Math.min(100, Math.round(level * 100f / scale))) : -1;
        int status = copy.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int plugged = copy.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        boolean charging = plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;

        SystemPowerSnapshot prev = latestSystemSnapshot;
        boolean stateEdge = (pct >= 0 && pct != prev.batteryLevel) || charging != prev.isCharging;
        if (!forceSample && !stateEdge) {
            return;
        }
        lastPassiveTriggerElapsedMs = now;
        POWER_QUEUE.postRunnable(() -> {
            try {
                sampleInternalLocked(ctx, false, copy);
                MirrlyPowerPolicy.evaluate(
                        latestSystemSnapshot.batteryLevel,
                        latestSystemSnapshot.isCharging,
                        latestSystemSnapshot.systemPowerSaveMode
                );
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    private static void schedulePassiveSample(@NonNull Context ctx, boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && (now - lastPassiveTriggerElapsedMs) < PASSIVE_SAMPLE_MIN_INTERVAL_MS) {
            return;
        }
        lastPassiveTriggerElapsedMs = now;
        POWER_QUEUE.postRunnable(() -> {
            try {
                sampleInternalLocked(ctx, force, null);
                MirrlyPowerPolicy.evaluate(
                        latestSystemSnapshot.batteryLevel,
                        latestSystemSnapshot.isCharging,
                        latestSystemSnapshot.systemPowerSaveMode
                );
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    private static void seedBaselinesLocked(@NonNull Context ctx) {
        EngineCounters ec = readEngineCounters();
        prevEngineBytesDown = ec.bytesDown;
        prevEngineBytesUp = ec.bytesUp;
        prevEngineTotalConn = ec.totalConn;
        prevEngineBadAndWsErr = ec.badConn + ec.wsErrors;
        prevEnginePoolMisses = ec.poolMisses;

        PREV_THREAD_TICKS.clear();
        readThreadCpuDeltasLocked();

        SystemPowerSnapshot sys = readSystemPowerSnapshot(ctx, null);
        latestSystemSnapshot = sys;
        prevChargeCounterUah = sys.chargeCounterUah;
        prevCharging = sys.isCharging;
    }

    private static void sampleInternalLocked(@NonNull Context ctx, boolean force) {
        sampleInternalLocked(ctx, force, null);
    }

    private static void sampleInternalLocked(@NonNull Context ctx, boolean force, @Nullable Intent batteryIntent) {
        long nowEl = SystemClock.elapsedRealtime();
        long nowWall = System.currentTimeMillis();
        long dtMs = lastSampleElapsedMs > 0 ? Math.max(0L, nowEl - lastSampleElapsedMs) : 0L;
        if (!force && dtMs < 5_000L) {
            latestSystemSnapshot = readSystemPowerSnapshot(ctx, batteryIntent);
            return;
        }
        lastSampleElapsedMs = nowEl;

        // Flush screen ON/OFF elapsed split
        long screenDt = lastScreenToggleElapsedMs > 0 ? Math.max(0L, nowEl - lastScreenToggleElapsedMs) : 0L;
        if (screenDt > 0) {
            long clampedScreenDt = Math.min(screenDt, 6 * HOUR_MS);
            if (currentScreenOn) {
                accumulatedScreenOnMs += clampedScreenDt;
            } else {
                accumulatedScreenOffMs += clampedScreenDt;
            }
            lastScreenToggleElapsedMs = nowEl;
        }
        long dScreenOnMs = accumulatedScreenOnMs;
        long dScreenOffMs = accumulatedScreenOffMs;
        accumulatedScreenOnMs = 0L;
        accumulatedScreenOffMs = 0L;
        if (dScreenOnMs + dScreenOffMs == 0 && dtMs > 0) {
            long capped = Math.min(dtMs, 30 * 60_000L);
            if (currentScreenOn) {
                dScreenOnMs = capped;
            } else {
                dScreenOffMs = capped;
            }
        }
        long clampedTotalMs = Math.min(dScreenOnMs + dScreenOffMs, 30 * 60_000L);
        if (clampedTotalMs < dScreenOnMs + dScreenOffMs && (dScreenOnMs + dScreenOffMs) > 0) {
            double scale = (double) clampedTotalMs / (double) (dScreenOnMs + dScreenOffMs);
            dScreenOnMs = Math.round(dScreenOnMs * scale);
            dScreenOffMs = Math.max(0L, clampedTotalMs - dScreenOnMs);
        }

        // 1. Hardware snapshot
        SystemPowerSnapshot sys = readSystemPowerSnapshot(ctx, batteryIntent);
        latestSystemSnapshot = sys;

        long observedDeviceDrainUah = 0L;
        if (prevChargeCounterUah > 0 && sys.chargeCounterUah > 0 && !sys.isCharging && !prevCharging) {
            long deltaUah = prevChargeCounterUah - sys.chargeCounterUah;
            if (deltaUah > 0 && deltaUah < 2_000_000L) {
                observedDeviceDrainUah = deltaUah;
            }
        }
        prevChargeCounterUah = sys.chargeCounterUah;
        prevCharging = sys.isCharging;

        // 2. Thread CPU deltas from /proc/self/task/*/stat
        ThreadCpuDelta cpu = readThreadCpuDeltasLocked();

        // 3. MirrlyEngine JSON deltas
        EngineCounters ec = readEngineCounters();
        long dEngBytesDown = prevEngineBytesDown >= 0 && ec.bytesDown >= prevEngineBytesDown ? (ec.bytesDown - prevEngineBytesDown) : 0L;
        long dEngBytesUp = prevEngineBytesUp >= 0 && ec.bytesUp >= prevEngineBytesUp ? (ec.bytesUp - prevEngineBytesUp) : 0L;
        long dEngConn = prevEngineTotalConn >= 0 && ec.totalConn >= prevEngineTotalConn ? (ec.totalConn - prevEngineTotalConn) : 0L;
        long curErr = ec.badConn + ec.wsErrors;
        long dEngErr = prevEngineBadAndWsErr >= 0 && curErr >= prevEngineBadAndWsErr ? (curErr - prevEngineBadAndWsErr) : 0L;
        long dEngMiss = prevEnginePoolMisses >= 0 && ec.poolMisses >= prevEnginePoolMisses ? (ec.poolMisses - prevEnginePoolMisses) : 0L;
        prevEngineBytesDown = ec.bytesDown;
        prevEngineBytesUp = ec.bytesUp;
        prevEngineTotalConn = ec.totalConn;
        prevEngineBadAndWsErr = curErr;
        prevEnginePoolMisses = ec.poolMisses;
        long dEngBytes = dEngBytesDown + dEngBytesUp;

        // 4. Drain instrumentation atomics
        long dPushWakeOn = ACC_PUSH_WAKEUPS_ON.getAndSet(0L);
        long dPushWakeOff = ACC_PUSH_WAKEUPS_OFF.getAndSet(0L);
        long dPushWlOn = ACC_PUSH_WAKELOCK_MS_ON.getAndSet(0L);
        long dPushWlOff = ACC_PUSH_WAKELOCK_MS_OFF.getAndSet(0L);
        long pushAccounts = Math.max(1L, ACC_PUSH_ACCOUNTS_LAST.get());

        long dGlassRebuilds = ACC_GLASS_REBUILDS.getAndSet(0L);
        long dGlassWorkNs = ACC_GLASS_WORK_NS.getAndSet(0L);
        long dGlassLiquidFrames = ACC_GLASS_LIQUID_FRAMES.getAndSet(0L);
        long dGlassEverywhereFrames = ACC_GLASS_EVERYWHERE_FRAMES.getAndSet(0L);
        long dGlassVisibleSum = ACC_GLASS_VISIBLE_SUM.getAndSet(0L);

        long dVaultOps = ACC_VAULT_OPS.getAndSet(0L);
        long dVaultBytes = ACC_VAULT_BYTES.getAndSet(0L);
        long dVaultWorkNs = ACC_VAULT_WORK_NS.getAndSet(0L);
        ACC_VAULT_DEL_MSG_OPS.set(0L);
        ACC_VAULT_DEL_CHAT_OPS.set(0L);
        ACC_VAULT_EDIT_OPS.set(0L);
        ACC_VAULT_NOTE_OPS.set(0L);

        long dTrOps = ACC_TRANSLATOR_OPS.getAndSet(0L);
        long dTrBytes = ACC_TRANSLATOR_BYTES.getAndSet(0L);
        long dSttOps = ACC_STT_OPS.getAndSet(0L);
        long dSttBytes = ACC_STT_BYTES.getAndSet(0L);

        long dHapticPulses = ACC_HAPTIC_PULSES.getAndSet(0L);
        long dHapticMs = ACC_HAPTIC_MS.getAndSet(0L);
        long dChatSounds = ACC_CHAT_SOUNDS.getAndSet(0L);

        // 5. Physical Power Model (in microampere-hours, uAh: 1000 uAh = 1 mAh)
        int perfClass = SharedConfig.getDevicePerformanceClass();
        double cpuCoreMa = perfClass == SharedConfig.PERFORMANCE_CLASS_HIGH ? 240.0
                : (perfClass == SharedConfig.PERFORMANCE_CLASS_AVERAGE ? 195.0 : 145.0);
        double radioMul = sys.networkType == NET_CELLULAR ? 2.65
                : (sys.networkType == NET_WIFI ? 1.0 : 0.2);

        // --- MODULE 0: MirrlyEngine ---
        double engineUah = (cpu.engineCpuMs / 3600.0) * cpuCoreMa;
        if (dEngBytes > 0) {
            double mb = dEngBytes / (1024.0 * 1024.0);
            double perMbUah = 18.0
                    + (MirrlyConfig.tlsEnabled ? 12.0 : 0.0)
                    + (MirrlyConfig.tlsEnabled && MirrlyConfig.tlsClientHelloSplit ? 4.0 : 0.0)
                    + (MirrlyConfig.flowsealPerDcEnabled ? 7.0 : 0.0);
            engineUah += mb * perMbUah * radioMul;
        }
        engineUah += dEngConn * 28.0 * radioMul;
        engineUah += dEngMiss * 42.0 * radioMul;
        engineUah += dEngErr * 88.0 * radioMul;
        if (MirrlyEngineController.isLocalProxyActive() && clampedTotalMs > 0) {
            double idleMa = MirrlyEngineController.isMtprotoSelected() ? 1.05 : 0.85;
            if (MirrlyConfig.flowsealPerDcEnabled) {
                idleMa += 0.35;
            }
            if (MirrlyConfig.tlsEnabled) {
                idleMa += 0.15;
            }
            engineUah += ((dScreenOnMs + dScreenOffMs * 0.45) / 3600.0) * idleMa * (sys.networkType == NET_CELLULAR ? 1.35 : 1.0);
        }

        // --- MODULE 1: Background Notifications Service (NotificationsService) ---
        double pushUah = 0.0;
        boolean pushServiceEnabled = true;
        boolean pushConnEnabled = true;
        try {
            SharedPreferences notifPrefs = MessagesController.getGlobalNotificationsSettings();
            pushServiceEnabled = notifPrefs.getBoolean("pushService", true);
            pushConnEnabled = notifPrefs.getBoolean("pushConnection", true);
        } catch (Throwable ignore) {
        }
        pushUah += (dPushWlOff / 3600.0) * 95.0; // AP SoC awake rail during screen-off partial WakeLock
        pushUah += (dPushWlOn / 3600.0) * 14.0;  // incremental overhead when screen already on
        pushUah += dPushWakeOff * 46.0 * radioMul * (1.0 + 0.22 * Math.max(0L, pushAccounts - 1L));
        pushUah += dPushWakeOn * 10.0 * radioMul;
        if ((pushServiceEnabled || pushConnEnabled) && clampedTotalMs > 0) {
            double keepMa = 0.0;
            if (pushServiceEnabled && pushConnEnabled) {
                keepMa = 1.55;
            } else if (pushServiceEnabled) {
                keepMa = 0.95;
            } else {
                keepMa = 0.70;
            }
            // Adjust for effective keep-alive frequency (3 min baseline vs 10 / 20 min)
            int effMin = MirrlyPowerPolicy.getEffectivePushKeepAliveMinutes();
            double freqFactor = effMin <= 3 ? 1.0 : (effMin <= 10 ? 0.58 : 0.40);
            double dozeFactor = sys.ignoringBatteryOptimizations ? 1.20 : 0.62;
            pushUah += (dScreenOffMs / 3600.0) * keepMa * freqFactor * dozeFactor * (sys.networkType == NET_CELLULAR ? 1.4 : 1.0);
            pushUah += (dScreenOnMs / 3600.0) * (keepMa * 0.25);
        }

        // --- MODULE 2: Glass, Liquid Glass & Monet ---
        double glassUah = (dGlassWorkNs / 3_600_000_000.0) * cpuCoreMa;
        if (dGlassRebuilds > 0 && MirrlyConfig.isGlassBlurEnabled()) {
            float blurScale = MirrlyConfig.getGlassBlurScale();
            float refractScale = MirrlyConfig.getGlassRefractScale();
            double avgVisible = Math.max(1.0, Math.min(6.0, (double) dGlassVisibleSum / (double) dGlassRebuilds));
            double perFrameBlurUah = 1.15 * (0.65 + 0.35 * blurScale) * (0.75 + 0.25 * avgVisible);
            glassUah += dGlassRebuilds * perFrameBlurUah;
            if (dGlassLiquidFrames > 0) {
                glassUah += dGlassLiquidFrames * (1.65 * (0.60 + 0.40 * refractScale));
            }
            if (dGlassEverywhereFrames > 0) {
                glassUah += dGlassEverywhereFrames * 0.95;
            }
        }
        // Attribute a real bounded slice of RenderThread CPU when glass is actively rebuilding
        if (cpu.renderThreadCpuMs > 0 && dGlassRebuilds > 0 && MirrlyConfig.isGlassBlurEnabled()) {
            double renderShare = MirrlyConfig.isGlassLiquidEnabled()
                    ? (MirrlyConfig.isLiquidGlassEverywhere() ? 0.26 : 0.16)
                    : 0.09;
            glassUah += (cpu.renderThreadCpuMs * renderShare / 3600.0) * (cpuCoreMa * 0.85);
        }

        // --- MODULE 3: Vault & Deleted Interceptors ---
        double vaultUah = (dVaultWorkNs / 3_600_000_000.0) * cpuCoreMa;
        vaultUah += dVaultOps * 9.0;
        if (dVaultBytes > 0) {
            vaultUah += (dVaultBytes / (1024.0 * 1024.0)) * 45.0;
        }
        boolean anyVaultActive = MirrlyConfig.isSaveDeletedMessages()
                || MirrlyConfig.isSaveDeletedChats()
                || MirrlyConfig.saveEditHistory;
        if (anyVaultActive && dScreenOnMs > 0 && currentAppForeground) {
            double hookMa = (MirrlyConfig.isSaveDeletedMessages() ? 0.06 : 0.0)
                    + (MirrlyConfig.isSaveDeletedChats() ? 0.05 : 0.0)
                    + (MirrlyConfig.saveEditHistory ? 0.03 : 0.0);
            vaultUah += (dScreenOnMs / 3600.0) * hookMa;
        }

        // --- MODULE 4: Translator & STT ---
        double translatorUah = ((cpu.translatorCpuMs + cpu.sttCpuMs) / 3600.0) * cpuCoreMa;
        translatorUah += dTrOps * 26.0 * radioMul;
        translatorUah += dSttOps * 170.0;
        if (dTrBytes + dSttBytes > 0) {
            translatorUah += ((dTrBytes + dSttBytes) / (1024.0 * 1024.0)) * 80.0 * radioMul;
        }

        // --- MODULE 5: Haptics, In-Chat Audio & Interface Customizations ---
        double interfaceUah = (dHapticMs / 3600.0) * 180.0;
        interfaceUah += dChatSounds * 14.0;
        if (dScreenOnMs > 0 && currentAppForeground && MirrlyConfig.isShowOwnChatTagEnabled()) {
            interfaceUah += (dScreenOnMs / 3600.0) * 0.08;
        }

        // --- Positive Power Savings (OLED AMOLED, Vibration Mute, Hidden Stories) ---
        double savedUah = 0.0;
        if (dScreenOnMs > 0 && currentAppForeground) {
            if (MirrlyConfig.isMonetAmoled() && Theme.isCurrentThemeDark()) {
                savedUah += (dScreenOnMs / 3600.0) * 14.0;
            }
            if (MirrlyConfig.isDisableAllVibrations()) {
                savedUah += (dScreenOnMs / 3600.0) * 0.45;
            }
            if (MirrlyInterfaceConfig.isHideTopStories()) {
                savedUah += (dScreenOnMs / 3600.0) * 0.65;
            }
        }

        // 6. Physical Calibration Guard against real battery coulomb counter
        double[] modUah = new double[]{engineUah, pushUah, glassUah, vaultUah, translatorUah, interfaceUah};
        double rawSum = 0.0;
        for (double v : modUah) {
            rawSum += v;
        }
        if (observedDeviceDrainUah >= 500L && rawSum > observedDeviceDrainUah * 0.85) {
            double clampFactor = (observedDeviceDrainUah * 0.85) / rawSum;
            for (int i = 0; i < MODULE_COUNT; i++) {
                modUah[i] *= clampFactor;
            }
        }

        long[] modUahLong = new long[MODULE_COUNT];
        for (int i = 0; i < MODULE_COUNT; i++) {
            modUahLong[i] = Math.max(0L, Math.round(modUah[i]));
        }
        long savedUahLong = Math.max(0L, Math.round(savedUah));

        // Split Mirrly energy between screen-on and screen-off for the 24h clock profile
        long mirrlyTotalLong = 0L;
        for (long v : modUahLong) {
            mirrlyTotalLong += v;
        }
        long screenOnUah = 0L;
        long screenOffUah = 0L;
        if (clampedTotalMs > 0 && mirrlyTotalLong > 0) {
            // Push wakeups/wakelocks off-screen belong strictly to screen-off; rest splits by time
            long explicitOff = Math.min(modUahLong[MODULE_PUSH], Math.round((dPushWlOff / 3600.0) * 95.0 + dPushWakeOff * 46.0 * radioMul));
            long remainder = Math.max(0L, mirrlyTotalLong - explicitOff);
            double onRatio = (double) dScreenOnMs / (double) clampedTotalMs;
            screenOnUah = Math.round(remainder * onRatio);
            screenOffUah = Math.max(0L, mirrlyTotalLong - screenOnUah);
        }

        // Compute Mirrly instantaneous average power in mW over this window
        int mirrlyAvgMw = 0;
        if (clampedTotalMs >= 1_000L && mirrlyTotalLong > 0) {
            double hours = clampedTotalMs / 3_600_000.0;
            double avgMa = (mirrlyTotalLong / 1000.0) / hours;
            double volts = (sys.voltageMv > 0 ? sys.voltageMv : 3850) / 1000.0;
            mirrlyAvgMw = (int) Math.min(25_000L, Math.round(avgMa * volts));
        }

        // 7. Persist into Session bucket (2-min) and Hourly bucket (1-hour)
        long sessionBucketTs = (nowWall / SESSION_BUCKET_MS) * SESSION_BUCKET_MS;
        long hourBucketTs = (nowWall / HOUR_MS) * HOUR_MS;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(nowWall);
        int hourOfDay = cal.get(Calendar.HOUR_OF_DAY);

        upsertBucketLocked(
                ctx,
                TABLE_SESSION,
                sessionBucketTs,
                hourOfDay,
                sys,
                dScreenOnMs,
                dScreenOffMs,
                observedDeviceDrainUah,
                modUahLong,
                screenOnUah,
                screenOffUah,
                mirrlyAvgMw,
                savedUahLong,
                dPushWakeOn + dPushWakeOff,
                dPushWlOn + dPushWlOff,
                dEngBytes,
                cpu.engineCpuMs,
                dEngErr + dEngMiss,
                dGlassRebuilds,
                dVaultOps,
                dVaultBytes,
                dTrOps,
                dSttOps,
                dHapticMs,
                dChatSounds
        );

        upsertBucketLocked(
                ctx,
                TABLE_HOURLY,
                hourBucketTs,
                hourOfDay,
                sys,
                dScreenOnMs,
                dScreenOffMs,
                observedDeviceDrainUah,
                modUahLong,
                screenOnUah,
                screenOffUah,
                mirrlyAvgMw,
                savedUahLong,
                dPushWakeOn + dPushWakeOff,
                dPushWlOn + dPushWlOff,
                dEngBytes,
                cpu.engineCpuMs,
                dEngErr + dEngMiss,
                dGlassRebuilds,
                dVaultOps,
                dVaultBytes,
                dTrOps,
                dSttOps,
                dHapticMs,
                dChatSounds
        );

        if (nowWall - lastPruneWallMs > 30 * 60_000L) {
            lastPruneWallMs = nowWall;
            pruneOldBucketsLocked(ctx, nowWall);
        }
    }

    private static void drainAllAtomics() {
        ACC_PUSH_WAKEUPS_ON.set(0L);
        ACC_PUSH_WAKEUPS_OFF.set(0L);
        ACC_PUSH_WAKELOCK_MS_ON.set(0L);
        ACC_PUSH_WAKELOCK_MS_OFF.set(0L);
        ACC_GLASS_REBUILDS.set(0L);
        ACC_GLASS_WORK_NS.set(0L);
        ACC_GLASS_LIQUID_FRAMES.set(0L);
        ACC_GLASS_EVERYWHERE_FRAMES.set(0L);
        ACC_GLASS_VISIBLE_SUM.set(0L);
        ACC_VAULT_OPS.set(0L);
        ACC_VAULT_BYTES.set(0L);
        ACC_VAULT_WORK_NS.set(0L);
        ACC_TRANSLATOR_OPS.set(0L);
        ACC_TRANSLATOR_BYTES.set(0L);
        ACC_STT_OPS.set(0L);
        ACC_STT_BYTES.set(0L);
        ACC_HAPTIC_PULSES.set(0L);
        ACC_HAPTIC_MS.set(0L);
        ACC_CHAT_SOUNDS.set(0L);
    }

    private static boolean readInteractiveState(@NonNull Context ctx) {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH ? pm.isInteractive() : pm.isScreenOn();
            }
        } catch (Throwable ignore) {
        }
        return ApplicationLoader.isScreenOn;
    }

    @NonNull
    private static SystemPowerSnapshot readSystemPowerSnapshot(@NonNull Context ctx, @Nullable Intent stickyOverride) {
        Intent batteryIntent = stickyOverride;
        if (batteryIntent == null) {
            try {
                batteryIntent = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            } catch (Throwable ignore) {
            }
        }

        int level = -1;
        int voltageMv = 0;
        int tempTenthsC = 0;
        int status = BatteryManager.BATTERY_STATUS_UNKNOWN;
        int plugged = 0;
        int health = BatteryManager.BATTERY_HEALTH_UNKNOWN;

        if (batteryIntent != null) {
            int rawLevel = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (rawLevel >= 0 && scale > 0) {
                level = Math.max(0, Math.min(100, Math.round(rawLevel * 100f / scale)));
            }
            int rawVoltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            if (rawVoltage > 100_000) {
                voltageMv = rawVoltage / 1000;
            } else if (rawVoltage > 0 && rawVoltage < 20) {
                voltageMv = rawVoltage * 1000;
            } else {
                voltageMv = Math.max(0, rawVoltage);
            }
            tempTenthsC = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
            plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            health = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);
        }

        boolean isCharging = plugged != 0
                || status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;

        float currentNowMa = 0f;
        float currentAvgMa = 0f;
        long chargeCounterUah = -1L;

        try {
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                if (level < 0) {
                    int cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                    if (cap >= 0 && cap <= 100) {
                        level = cap;
                    }
                }
                int rawNow = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                int rawAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
                currentNowMa = normalizeCurrentMa(rawNow, isCharging);
                currentAvgMa = normalizeCurrentMa(rawAvg, isCharging);
                if (currentNowMa == 0f && currentAvgMa != 0f) {
                    currentNowMa = currentAvgMa;
                }
                long cc = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                if (cc > 0 && cc != Long.MIN_VALUE && cc < 20_000_000L) {
                    chargeCounterUah = cc;
                    if (level >= 15 && level <= 98) {
                        double estMah = (cc / 1000.0) / (level / 100.0);
                        if (estMah >= 1500.0 && estMah <= 12000.0) {
                            calibratedCapacityMah = calibratedCapacityMah * 0.8 + estMah * 0.2;
                            try {
                                MirrlyConfig.getPowerPrefs().edit()
                                        .putFloat(PREF_CALIBRATED_CAP_MAH, (float) calibratedCapacityMah)
                                        .apply();
                            } catch (Throwable ignore) {
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignore) {
        }

        int systemPowerMw = 0;
        if (currentNowMa != 0f) {
            float v = (voltageMv > 0 ? voltageMv : 3850) / 1000f;
            systemPowerMw = Math.round(Math.abs(currentNowMa) * v);
        }

        boolean powerSave = false;
        boolean ignoringOpt = false;
        boolean deviceIdle = false;
        boolean interactive = currentScreenOn;
        int thermalStatus = 0;
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    powerSave = pm.isPowerSaveMode();
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    ignoringOpt = pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
                    deviceIdle = pm.isDeviceIdleMode();
                }
                interactive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH ? pm.isInteractive() : pm.isScreenOn();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    thermalStatus = pm.getCurrentThermalStatus();
                }
            }
        } catch (Throwable ignore) {
        }

        int netType = readNetworkType(ctx);

        return new SystemPowerSnapshot(
                System.currentTimeMillis(),
                level,
                isCharging,
                plugged,
                status,
                health,
                voltageMv,
                tempTenthsC,
                currentNowMa,
                currentAvgMa,
                systemPowerMw,
                chargeCounterUah,
                calibratedCapacityMah,
                powerSave,
                ignoringOpt,
                deviceIdle,
                interactive,
                thermalStatus,
                netType
        );
    }

    private static float normalizeCurrentMa(int rawValue, boolean isCharging) {
        if (rawValue == Integer.MIN_VALUE || rawValue == Integer.MAX_VALUE || rawValue == 0) {
            return 0f;
        }
        int abs = Math.abs(rawValue);
        float magMa = abs > 10_000 ? (abs / 1000f) : (float) abs;
        if (magMa > 15_000f) {
            return 0f;
        }
        return isCharging ? magMa : -magMa;
    }

    private static int readNetworkType(@NonNull Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return NET_NONE;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network active = cm.getActiveNetwork();
                NetworkCapabilities caps = active != null ? cm.getNetworkCapabilities(active) : null;
                if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                            || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        return NET_WIFI;
                    }
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        return NET_CELLULAR;
                    }
                }
            }
            NetworkInfo info = cm.getActiveNetworkInfo();
            if (info != null && info.isConnectedOrConnecting()) {
                if (info.getType() == ConnectivityManager.TYPE_WIFI || info.getType() == ConnectivityManager.TYPE_ETHERNET) {
                    return NET_WIFI;
                }
                if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
                    return NET_CELLULAR;
                }
            }
        } catch (Throwable ignore) {
        }
        return NET_NONE;
    }

    // -------------------------------------------------------------------------
    // Real Linux /proc/self/task/*/stat Per-Thread CPU Reader
    // -------------------------------------------------------------------------

    private static final class ThreadCpuDelta {
        long engineCpuMs;
        long translatorCpuMs;
        long sttCpuMs;
        long renderThreadCpuMs;
        long totalAppCpuMs;
    }

    @NonNull
    private static ThreadCpuDelta readThreadCpuDeltasLocked() {
        ThreadCpuDelta delta = new ThreadCpuDelta();
        File taskDir = new File("/proc/self/task");
        File[] entries = taskDir.listFiles();
        if (entries == null || entries.length == 0) {
            return delta;
        }

        long tps = Math.max(1L, linuxClockTicksPerSec);
        LongSparseArray<Long> seen = new LongSparseArray<>(entries.length);

        for (File tidDir : entries) {
            long tid;
            try {
                tid = Long.parseLong(tidDir.getName());
            } catch (Throwable ignore) {
                continue;
            }
            File statFile = new File(tidDir, "stat");
            String line = readSmallProcFileLocked(statFile);
            if (line == null) {
                continue;
            }
            int lp = line.indexOf('(');
            int rp = line.lastIndexOf(')');
            if (lp < 0 || rp <= lp || rp + 2 >= line.length()) {
                continue;
            }
            String comm = line.substring(lp + 1, rp);
            long ticks = parseUtimeStimeTicks(line, rp + 2);
            if (ticks < 0) {
                continue;
            }

            seen.put(tid, ticks);
            Long prevTicks = PREV_THREAD_TICKS.get(tid);
            long dTicks = prevTicks != null && ticks >= prevTicks ? (ticks - prevTicks) : 0L;
            if (dTicks <= 0L) {
                continue;
            }
            long dMs = (dTicks * 1000L) / tps;
            delta.totalAppCpuMs += dMs;

            if (comm.startsWith("mirrly-rt")) {
                delta.engineCpuMs += dMs;
            } else if (comm.startsWith("MirrlyTranslat")) {
                delta.translatorCpuMs += dMs;
            } else if (comm.startsWith("MirrlyStt")) {
                delta.sttCpuMs += dMs;
            } else if ("RenderThread".equals(comm)) {
                delta.renderThreadCpuMs += dMs;
            }
        }

        PREV_THREAD_TICKS.clear();
        for (int i = 0; i < seen.size(); i++) {
            PREV_THREAD_TICKS.put(seen.keyAt(i), seen.valueAt(i));
        }
        return delta;
    }

    @Nullable
    private static String readSmallProcFileLocked(@NonNull File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            int n = fis.read(PROC_READ_BUF);
            if (n <= 0) {
                return null;
            }
            return new String(PROC_READ_BUF, 0, n);
        } catch (Throwable ignore) {
            return null;
        }
    }

    /**
     * Parses utime (index 11 after ") ") and stime (index 12 after ") ") without regex or String.split allocations.
     */
    private static long parseUtimeStimeTicks(@NonNull String line, int startIdx) {
        int len = line.length();
        int field = 0;
        int i = startIdx;
        long utime = -1L;
        long stime = -1L;
        while (i < len && field <= 12) {
            while (i < len && line.charAt(i) == ' ') {
                i++;
            }
            int tokenStart = i;
            while (i < len && line.charAt(i) != ' ' && line.charAt(i) != '\n') {
                i++;
            }
            if (field == 11) {
                utime = parsePositiveLong(line, tokenStart, i);
            } else if (field == 12) {
                stime = parsePositiveLong(line, tokenStart, i);
                break;
            }
            field++;
        }
        if (utime < 0 || stime < 0) {
            return -1L;
        }
        return utime + stime;
    }

    private static long parsePositiveLong(@NonNull String s, int start, int end) {
        if (start >= end) {
            return -1L;
        }
        long val = 0L;
        for (int i = start; i < end; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return -1L;
            }
            val = val * 10L + (c - '0');
        }
        return val;
    }

    private static final class EngineCounters {
        long bytesDown;
        long bytesUp;
        long totalConn;
        long activeConn;
        long badConn;
        long wsErrors;
        long poolMisses;
        long uptimeSecs;
    }

    @NonNull
    private static EngineCounters readEngineCounters() {
        EngineCounters out = new EngineCounters();
        if (!MirrlyEngineController.isLibraryLoaded()) {
            return out;
        }
        try {
            String raw = MirrlyEngineController.getStatsJson();
            if (raw != null && raw.length() > 2) {
                JSONObject obj = new JSONObject(raw);
                out.bytesDown = Math.max(0L, obj.optLong("bytes_down", 0L));
                out.bytesUp = Math.max(0L, obj.optLong("bytes_up", 0L));
                out.totalConn = Math.max(0L, obj.optLong("connections_total", 0L));
                out.activeConn = Math.max(0L, obj.optLong("connections_active", 0L));
                out.badConn = Math.max(0L, obj.optLong("connections_bad", 0L));
                out.wsErrors = Math.max(0L, obj.optLong("ws_errors", 0L));
                out.poolMisses = Math.max(0L, obj.optLong("pool_misses", 0L));
                out.uptimeSecs = Math.max(0L, obj.optLong("uptime_secs", 0L));
            }
        } catch (Throwable ignore) {
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // SQLite WAL Persistence (mirrly_power_stats.db)
    // -------------------------------------------------------------------------

    private static final class PowerDbHelper extends SQLiteOpenHelper {
        PowerDbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
            setWriteAheadLoggingEnabled(true);
        }

        private static String createTableSql(String name) {
            return "CREATE TABLE IF NOT EXISTS " + name + " ("
                    + "bucket_ts INTEGER PRIMARY KEY,"
                    + "hour_of_day INTEGER NOT NULL DEFAULT 0,"
                    + "batt_level INTEGER NOT NULL DEFAULT -1,"
                    + "temp_tenths INTEGER NOT NULL DEFAULT 0,"
                    + "mirrly_mw_sum INTEGER NOT NULL DEFAULT 0,"
                    + "sample_count INTEGER NOT NULL DEFAULT 0,"
                    + "screen_on_ms INTEGER NOT NULL DEFAULT 0,"
                    + "screen_off_ms INTEGER NOT NULL DEFAULT 0,"
                    + "device_drain_uah INTEGER NOT NULL DEFAULT 0,"
                    + "m0_uah INTEGER NOT NULL DEFAULT 0,"
                    + "m1_uah INTEGER NOT NULL DEFAULT 0,"
                    + "m2_uah INTEGER NOT NULL DEFAULT 0,"
                    + "m3_uah INTEGER NOT NULL DEFAULT 0,"
                    + "m4_uah INTEGER NOT NULL DEFAULT 0,"
                    + "m5_uah INTEGER NOT NULL DEFAULT 0,"
                    + "screen_on_uah INTEGER NOT NULL DEFAULT 0,"
                    + "screen_off_uah INTEGER NOT NULL DEFAULT 0,"
                    + "saved_uah INTEGER NOT NULL DEFAULT 0,"
                    + "push_wakeups INTEGER NOT NULL DEFAULT 0,"
                    + "push_wakelock_ms INTEGER NOT NULL DEFAULT 0,"
                    + "engine_bytes INTEGER NOT NULL DEFAULT 0,"
                    + "engine_cpu_ms INTEGER NOT NULL DEFAULT 0,"
                    + "engine_reconn INTEGER NOT NULL DEFAULT 0,"
                    + "glass_rebuilds INTEGER NOT NULL DEFAULT 0,"
                    + "vault_ops INTEGER NOT NULL DEFAULT 0,"
                    + "vault_bytes INTEGER NOT NULL DEFAULT 0,"
                    + "tr_ops INTEGER NOT NULL DEFAULT 0,"
                    + "stt_ops INTEGER NOT NULL DEFAULT 0,"
                    + "haptic_ms INTEGER NOT NULL DEFAULT 0,"
                    + "sound_ops INTEGER NOT NULL DEFAULT 0"
                    + ")";
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(createTableSql(TABLE_HOURLY));
            db.execSQL(createTableSql(TABLE_SESSION));
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_HOURLY);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_SESSION);
            onCreate(db);
        }
    }

    @Nullable
    private static SQLiteDatabase getDbLocked(@NonNull Context ctx) {
        try {
            if (dbHelper == null) {
                dbHelper = new PowerDbHelper(ctx.getApplicationContext());
            }
            return dbHelper.getWritableDatabase();
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    private static void upsertBucketLocked(
            @NonNull Context ctx,
            @NonNull String table,
            long bucketTs,
            int hourOfDay,
            @NonNull SystemPowerSnapshot sys,
            long screenOnMs,
            long screenOffMs,
            long deviceDrainUah,
            @NonNull long[] modUah,
            long screenOnUah,
            long screenOffUah,
            int mirrlyAvgMw,
            long savedUah,
            long pushWakeups,
            long pushWakeLockMs,
            long engineBytes,
            long engineCpuMs,
            long engineReconn,
            long glassRebuilds,
            long vaultOps,
            long vaultBytes,
            long trOps,
            long sttOps,
            long hapticMs,
            long soundOps
    ) {
        synchronized (DB_LOCK) {
            SQLiteDatabase db = getDbLocked(ctx);
            if (db == null) {
                return;
            }
            try {
                // Ensure row exists, then accumulate deltas atomically
                ContentValues init = new ContentValues();
                init.put("bucket_ts", bucketTs);
                init.put("hour_of_day", hourOfDay);
                init.put("batt_level", sys.batteryLevel);
                init.put("temp_tenths", sys.tempTenthsC);
                db.insertWithOnConflict(table, null, init, SQLiteDatabase.CONFLICT_IGNORE);

                String sql = "UPDATE " + table + " SET "
                        + "batt_level = CASE WHEN ? >= 0 THEN ? ELSE batt_level END,"
                        + "temp_tenths = CASE WHEN ? > 0 THEN ? ELSE temp_tenths END,"
                        + "mirrly_mw_sum = mirrly_mw_sum + ?,"
                        + "sample_count = sample_count + 1,"
                        + "screen_on_ms = screen_on_ms + ?,"
                        + "screen_off_ms = screen_off_ms + ?,"
                        + "device_drain_uah = device_drain_uah + ?,"
                        + "m0_uah = m0_uah + ?,"
                        + "m1_uah = m1_uah + ?,"
                        + "m2_uah = m2_uah + ?,"
                        + "m3_uah = m3_uah + ?,"
                        + "m4_uah = m4_uah + ?,"
                        + "m5_uah = m5_uah + ?,"
                        + "screen_on_uah = screen_on_uah + ?,"
                        + "screen_off_uah = screen_off_uah + ?,"
                        + "saved_uah = saved_uah + ?,"
                        + "push_wakeups = push_wakeups + ?,"
                        + "push_wakelock_ms = push_wakelock_ms + ?,"
                        + "engine_bytes = engine_bytes + ?,"
                        + "engine_cpu_ms = engine_cpu_ms + ?,"
                        + "engine_reconn = engine_reconn + ?,"
                        + "glass_rebuilds = glass_rebuilds + ?,"
                        + "vault_ops = vault_ops + ?,"
                        + "vault_bytes = vault_bytes + ?,"
                        + "tr_ops = tr_ops + ?,"
                        + "stt_ops = stt_ops + ?,"
                        + "haptic_ms = haptic_ms + ?,"
                        + "sound_ops = sound_ops + ? "
                        + "WHERE bucket_ts = ?";
                db.execSQL(sql, new Object[]{
                        sys.batteryLevel, sys.batteryLevel,
                        sys.tempTenthsC, sys.tempTenthsC,
                        mirrlyAvgMw,
                        screenOnMs,
                        screenOffMs,
                        deviceDrainUah,
                        modUah[0], modUah[1], modUah[2], modUah[3], modUah[4], modUah[5],
                        screenOnUah,
                        screenOffUah,
                        savedUah,
                        pushWakeups,
                        pushWakeLockMs,
                        engineBytes,
                        engineCpuMs,
                        engineReconn,
                        glassRebuilds,
                        vaultOps,
                        vaultBytes,
                        trOps,
                        sttOps,
                        hapticMs,
                        soundOps,
                        bucketTs
                });
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }

    private static void pruneOldBucketsLocked(@NonNull Context ctx, long nowWall) {
        synchronized (DB_LOCK) {
            SQLiteDatabase db = getDbLocked(ctx);
            if (db == null) {
                return;
            }
            try {
                db.delete(TABLE_HOURLY, "bucket_ts < ?", new String[]{String.valueOf(nowWall - RETENTION_MS - DAY_MS)});
                db.delete(TABLE_SESSION, "bucket_ts < ?", new String[]{String.valueOf(nowWall - 6 * HOUR_MS)});
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Report & Native Telegram Chart Payload Builder
    // -------------------------------------------------------------------------

    private static final class BucketRow {
        long bucketTs;
        int hourOfDay;
        int battLevel;
        int avgMirrlyMw;
        long screenOnMs;
        long screenOffMs;
        long deviceDrainUah;
        final long[] modUah = new long[MODULE_COUNT];
        long screenOnUah;
        long screenOffUah;
        long savedUah;
        long pushWakeups;
        long pushWakeLockMs;
        long engineBytes;
        long engineCpuMs;
        long engineReconn;
        long glassRebuilds;
        long vaultOps;
        long vaultBytes;
        long trOps;
        long sttOps;
        long hapticMs;
        long soundOps;

        long totalMirrlyUah() {
            return modUah[0] + modUah[1] + modUah[2] + modUah[3] + modUah[4] + modUah[5];
        }
    }

    @NonNull
    private static List<BucketRow> queryBucketsLocked(@NonNull Context ctx, @NonNull String table, long fromTs, long toTs) {
        ArrayList<BucketRow> result = new ArrayList<>();
        synchronized (DB_LOCK) {
            SQLiteDatabase db = getDbLocked(ctx);
            if (db == null) {
                return result;
            }
            String sql = "SELECT bucket_ts,hour_of_day,batt_level,mirrly_mw_sum,sample_count,"
                    + "screen_on_ms,screen_off_ms,device_drain_uah,"
                    + "m0_uah,m1_uah,m2_uah,m3_uah,m4_uah,m5_uah,"
                    + "screen_on_uah,screen_off_uah,saved_uah,"
                    + "push_wakeups,push_wakelock_ms,engine_bytes,engine_cpu_ms,engine_reconn,"
                    + "glass_rebuilds,vault_ops,vault_bytes,tr_ops,stt_ops,haptic_ms,sound_ops "
                    + "FROM " + table + " WHERE bucket_ts >= ? AND bucket_ts <= ? ORDER BY bucket_ts ASC";
            try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(fromTs), String.valueOf(toTs)})) {
                while (c.moveToNext()) {
                    BucketRow r = new BucketRow();
                    r.bucketTs = c.getLong(0);
                    r.hourOfDay = c.getInt(1);
                    r.battLevel = c.getInt(2);
                    long mwSum = c.getLong(3);
                    long sc = Math.max(1L, c.getLong(4));
                    r.avgMirrlyMw = (int) Math.round((double) mwSum / (double) sc);
                    r.screenOnMs = c.getLong(5);
                    r.screenOffMs = c.getLong(6);
                    r.deviceDrainUah = c.getLong(7);
                    for (int i = 0; i < MODULE_COUNT; i++) {
                        r.modUah[i] = c.getLong(8 + i);
                    }
                    r.screenOnUah = c.getLong(14);
                    r.screenOffUah = c.getLong(15);
                    r.savedUah = c.getLong(16);
                    r.pushWakeups = c.getLong(17);
                    r.pushWakeLockMs = c.getLong(18);
                    r.engineBytes = c.getLong(19);
                    r.engineCpuMs = c.getLong(20);
                    r.engineReconn = c.getLong(21);
                    r.glassRebuilds = c.getLong(22);
                    r.vaultOps = c.getLong(23);
                    r.vaultBytes = c.getLong(24);
                    r.trOps = c.getLong(25);
                    r.sttOps = c.getLong(26);
                    r.hapticMs = c.getLong(27);
                    r.soundOps = c.getLong(28);
                    result.add(r);
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
        return result;
    }

    @NonNull
    private static PowerReport buildReportLocked(@NonNull Context ctx, int period) {
        long nowWall = System.currentTimeMillis();
        SystemPowerSnapshot sys = latestSystemSnapshot;
        double capUah = Math.max(1_500_000.0, sys.effectiveCapacityMah * 1000.0);

        long windowStart;
        long prevWindowStart;
        long prevWindowEnd;
        String table;
        long stepMs;

        if (period == PERIOD_SESSION) {
            windowStart = Math.max(sessionStartWallMs - SESSION_BUCKET_MS, nowWall - 6 * HOUR_MS);
            long span = Math.max(SESSION_BUCKET_MS * 2L, nowWall - windowStart);
            prevWindowStart = windowStart - span;
            prevWindowEnd = windowStart - 1L;
            table = TABLE_SESSION;
            stepMs = SESSION_BUCKET_MS;
        } else if (period == PERIOD_7D) {
            windowStart = nowWall - RETENTION_MS;
            prevWindowStart = windowStart - RETENTION_MS;
            prevWindowEnd = windowStart - 1L;
            table = TABLE_HOURLY;
            stepMs = DAY_MS;
        } else {
            period = PERIOD_24H;
            windowStart = nowWall - DAY_MS;
            prevWindowStart = windowStart - DAY_MS;
            prevWindowEnd = windowStart - 1L;
            table = TABLE_HOURLY;
            stepMs = HOUR_MS;
        }

        List<BucketRow> rows = queryBucketsLocked(ctx, table, windowStart, nowWall + stepMs);
        List<BucketRow> prevRows = queryBucketsLocked(ctx, table, prevWindowStart, prevWindowEnd);
        List<BucketRow> hourlyAll7d = period == PERIOD_7D
                ? rows
                : queryBucketsLocked(ctx, TABLE_HOURLY, nowWall - RETENTION_MS, nowWall + HOUR_MS);

        long[] modUah = new long[MODULE_COUNT];
        long totalUah = 0L;
        long totalSavedUah = 0L;
        long totalDeviceDrainUah = 0L;
        long totalScreenOnMs = 0L;
        long totalScreenOffMs = 0L;
        long totalPushWakeups = 0L;
        long totalPushWlMs = 0L;
        long totalEngBytes = 0L;
        long totalEngCpuMs = 0L;
        long totalEngReconn = 0L;
        long totalGlassRebuilds = 0L;
        long totalVaultOps = 0L;
        long totalVaultBytes = 0L;
        long totalTrOps = 0L;
        long totalSttOps = 0L;
        long totalHapticMs = 0L;
        long totalSoundOps = 0L;

        for (BucketRow r : rows) {
            for (int i = 0; i < MODULE_COUNT; i++) {
                modUah[i] += r.modUah[i];
                totalUah += r.modUah[i];
            }
            totalSavedUah += r.savedUah;
            totalDeviceDrainUah += r.deviceDrainUah;
            totalScreenOnMs += r.screenOnMs;
            totalScreenOffMs += r.screenOffMs;
            totalPushWakeups += r.pushWakeups;
            totalPushWlMs += r.pushWakeLockMs;
            totalEngBytes += r.engineBytes;
            totalEngCpuMs += r.engineCpuMs;
            totalEngReconn += r.engineReconn;
            totalGlassRebuilds += r.glassRebuilds;
            totalVaultOps += r.vaultOps;
            totalVaultBytes += r.vaultBytes;
            totalTrOps += r.trOps;
            totalSttOps += r.sttOps;
            totalHapticMs += r.hapticMs;
            totalSoundOps += r.soundOps;
        }

        long prevTotalUah = 0L;
        for (BucketRow r : prevRows) {
            prevTotalUah += r.totalMirrlyUah();
        }

        long observedMs = Math.max(0L, totalScreenOnMs + totalScreenOffMs);
        double observedHours = observedMs / 3_600_000.0;
        double totalBatteryPct = (totalUah / capUah) * 100.0;
        double prevBatteryPct = (prevTotalUah / capUah) * 100.0;
        boolean hasPrevComparison = !prevRows.isEmpty() && prevTotalUah > 0;
        double deltaBatteryPct = hasPrevComparison ? (totalBatteryPct - prevBatteryPct) : 0.0;
        double ratePctPerHour = observedHours >= (1.0 / 60.0) ? (totalBatteryPct / observedHours) : 0.0;
        double avgMirrlyMa = observedHours >= (1.0 / 60.0) ? ((totalUah / 1000.0) / observedHours) : 0.0;
        int avgMirrlyMw = (int) Math.round(avgMirrlyMa * ((sys.voltageMv > 0 ? sys.voltageMv : 3850) / 1000.0));
        double savedBatteryPct = (totalSavedUah / capUah) * 100.0;

        // Build per-module detailed reports
        ModuleReport[] modules = new ModuleReport[MODULE_COUNT];
        for (int i = 0; i < MODULE_COUNT; i++) {
            double mBattPct = (modUah[i] / capUah) * 100.0;
            double mSharePct = totalUah > 0 ? (modUah[i] * 100.0 / (double) totalUah) : 0.0;
            double mRatePctHr = observedHours >= (1.0 / 60.0) ? (mBattPct / observedHours) : 0.0;
            modules[i] = buildModuleReport(
                    i,
                    modUah[i],
                    mBattPct,
                    mSharePct,
                    mRatePctHr,
                    totalEngCpuMs,
                    totalEngBytes,
                    totalEngReconn,
                    totalPushWakeups,
                    totalPushWlMs,
                    totalGlassRebuilds,
                    totalVaultOps,
                    totalVaultBytes,
                    totalTrOps,
                    totalSttOps,
                    totalHapticMs,
                    totalSoundOps,
                    totalSavedUah,
                    sys
            );
        }

        // Ranked order (highest consumption first)
        ModuleReport[] ranked = Arrays.copyOf(modules, MODULE_COUNT);
        Arrays.sort(ranked, (a, b) -> {
            int cmp = Long.compare(b.uah, a.uah);
            return cmp != 0 ? cmp : Integer.compare(a.moduleId, b.moduleId);
        });

        // Build native Telegram Chart JSONs (evenly spaced grid!)
        GridSeries grid = buildGridSeries(period, rows, nowWall, stepMs, sys.batteryLevel);
        JSONObject stackBarJson = buildModulesStackJson(grid, false);
        JSONObject stackShareJson = buildModulesStackJson(grid, true);
        JSONObject batteryVsPowerJson = buildBatteryVsPowerJson(grid);
        JSONObject clock24hJson = build24HourClockJson(hourlyAll7d);

        // Smart actionable recommendations (only when real threshold crossed)
        List<EcoRecommendation> recs = buildRecommendations(modules, totalUah, totalPushWakeups, sys);

        long startWall = !rows.isEmpty() ? rows.get(0).bucketTs : windowStart;
        long endWall = !rows.isEmpty() ? rows.get(rows.size() - 1).bucketTs : nowWall;

        return new PowerReport(
                period,
                startWall,
                endWall,
                observedMs,
                rows.size(),
                totalUah,
                prevTotalUah,
                totalSavedUah,
                totalDeviceDrainUah,
                totalBatteryPct,
                deltaBatteryPct,
                hasPrevComparison,
                savedBatteryPct,
                ratePctPerHour,
                avgMirrlyMa,
                avgMirrlyMw,
                totalScreenOnMs,
                totalScreenOffMs,
                totalPushWakeups,
                totalPushWlMs,
                modules,
                ranked,
                sys,
                recs,
                stackBarJson,
                stackShareJson,
                batteryVsPowerJson,
                clock24hJson,
                grid.realPointCount
        );
    }

    @NonNull
    private static ModuleReport buildModuleReport(
            int moduleId,
            long uah,
            double batteryPct,
            double sharePct,
            double ratePctHr,
            long engCpuMs,
            long engBytes,
            long engReconn,
            long pushWakeups,
            long pushWlMs,
            long glassRebuilds,
            long vaultOps,
            long vaultBytes,
            long trOps,
            long sttOps,
            long hapticMs,
            long soundOps,
            long totalSavedUah,
            @NonNull SystemPowerSnapshot sys
    ) {
        ArrayList<SettingItem> settings = new ArrayList<>();
        boolean active = false;
        String telemetryLine = "";

        switch (moduleId) {
            case MODULE_ENGINE: {
                active = MirrlyEngineController.isLocalProxyActive();
                String transport = MirrlyEngineController.isMtprotoSelected() ? "MTProto"
                        : (MirrlyEngineController.isSocks5Selected() ? "SOCKS5"
                        : LocaleController.getString(R.string.MirrlyAboutEngineOff));
                telemetryLine = LocaleController.formatString(
                        R.string.MirrlyPowerModEngineTelemetry,
                        formatCpuDuration(engCpuMs),
                        AndroidUtilities.formatFileSize(engBytes),
                        engReconn
                );
                settings.add(new SettingItem(
                        LocaleController.getString(R.string.MirrlyNetworkModeHeader),
                        transport,
                        active,
                        false
                ));
                settings.add(new SettingItem(
                        LocaleController.getString(R.string.MirrlyNetworkTlsEnable),
                        MirrlyConfig.tlsEnabled
                                ? (MirrlyConfig.tlsClientHelloSplit ? "TLS + Split" : "TLS")
                                : LocaleController.getString(R.string.MirrlyPowerStateOff),
                        MirrlyConfig.tlsEnabled,
                        false
                ));
                settings.add(new SettingItem(
                        LocaleController.getString(R.string.MirrlyNetworkFlowsealPerDcEnable),
                        MirrlyConfig.flowsealPerDcEnabled
                                ? LocaleController.getString(R.string.MirrlyPowerStateOn)
                                : LocaleController.getString(R.string.MirrlyPowerStateOff),
                        MirrlyConfig.flowsealPerDcEnabled,
                        false
                ));
                break;
            }
            case MODULE_PUSH: {
                SharedPreferences np = MessagesController.getGlobalNotificationsSettings();
                boolean pushSvc = np.getBoolean("pushService", true);
                boolean pushConn = np.getBoolean("pushConnection", true);
                active = pushSvc || pushConn;
                int effMin = MirrlyPowerPolicy.getEffectivePushKeepAliveMinutes();
                telemetryLine = LocaleController.formatString(
                        R.string.MirrlyPowerModPushTelemetry,
                        pushWakeups,
                        formatShortMs(pushWlMs),
                        effMin
                );
                settings.add(new SettingItem(
                        LocaleController.getString(R.string.NotificationsService),
                        pushSvc ? LocaleController.formatString(R.string.MirrlyPowerIntervalMinutesShort, effMin)
                                : LocaleController.getString(R.string.MirrlyPowerStateOff),
                        pushSvc,
                        false
                ));
                settings.add(new SettingItem(
                        LocaleController.getString(R.string.NotificationsServiceConnection),
                        pushConn ? LocaleController.getString(R.string.MirrlyPowerStateOn)
                                : LocaleController.getString(R.string.MirrlyPowerStateOff),
                        pushConn,
                        false
                ));
                settings.add(new SettingItem(
                        LocaleController.getString(R.string.MirrlyBatteryUnrestricted),
                        sys.ignoringBatteryOptimizations
                                ? LocaleController.getString(R.string.MirrlyPowerStateUnrestricted)
                                : LocaleController.getString(R.string.MirrlyPowerStateOptimized),
                        sys.ignoringBatteryOptimizations,
                        false
                ));
                break;
            }
            case MODULE_GLASS: {
                boolean blurOn = MirrlyConfig.isGlassBlurEnabled();
                boolean liquidOn = MirrlyConfig.isGlassLiquidEnabled();
                boolean everywhereOn = liquidOn && MirrlyConfig.isLiquidGlassEverywhere();
                boolean dialogsHeader = MirrlyConfig.isDialogsGlassHeader();
                boolean amoled = MirrlyConfig.isMonetAmoled() && Theme.isCurrentThemeDark();
                active = blurOn || liquidOn || dialogsHeader;
                telemetryLine = LocaleController.formatString(
                        R.string.MirrlyPowerModGlassTelemetry,
                        glassRebuilds,
                        Math.round(MirrlyConfig.getGlassBlurScale() * 100f)
                );
                settings.add(new SettingItem(
                        LocaleController.getString(R.string.MirrlyGlassBlurToggle),
                        blurOn ? (Math.round(MirrlyConfig.getGlassBlurScale() * 100f) + "%")
                                : LocaleController.getString(R.string.MirrlyPowerStateOff),
                        blurOn,
                        false
                ));
                if (MirrlyConfig.isGlassLiquidSupported()) {
                    settings.add(new SettingItem(
                            LocaleController.getString(R.string.MirrlyGlassEverywhereToggle),
                            everywhereOn ? LocaleController.getString(R.string.MirrlyPowerStateOn)
                                    : (liquidOn ? LocaleController.getString(R.string.MirrlyPowerStatePartial)
                                    : LocaleController.getString(R.string.MirrlyPowerStateOff)),
                            everywhereOn || liquidOn,
                            false
                    ));
                }
                settings.add(new SettingItem(
                        LocaleController.getString(R.string.MirrlyGlassDialogsHeader),
                        dialogsHeader ? LocaleController.getString(R.string.MirrlyPowerStateOn)
                                : LocaleController.getString(R.string.MirrlyPowerStateOff),
                        dialogsHeader,
                        false
                ));
                settings.add(new SettingItem(
                        LocaleController.getString(R.string.MirrlyPowerSettingMonetAmoled),
                        amoled ? LocaleController.getString(R.string.MirrlyPowerSavingOledTag)
                                : LocaleController.getString(R.string.MirrlyPowerStateOff),
                        amoled,
                        amoled
                ));
                break;
            }
            case MODULE_VAULT: {
                boolean delMsg = MirrlyConfig.isSaveDeletedMessages();
                boolean delChat = MirrlyConfig.isSaveDeletedChats();
                boolean editHist = MirrlyConfig.saveEditHistory;
                active = delMsg || delChat || editHist;
                telemetryLine = LocaleController.formatString(
                        R.string.MirrlyPowerModVaultTelemetry,
                        vaultOps,
                        AndroidUtilities.formatFileSize(vaultBytes)
                );
                settings.add(new SettingItem(
                        LocaleController.getString(R.string.MirrlyVaultSaveDeleted),
                        delMsg ? LocaleController.getString(R.string.MirrlyPowerStateOn)
                                : LocaleController.getString(R.string.MirrlyPowerStateOff),
                        delMsg,
                        false
                ));
                settings.add(new SettingItem(
                        LocaleController.getString(R.string.MirrlyVaultSaveDeletedChats),
                        delChat ? LocaleController.getString(R.string.MirrlyPowerStateOn)
                                : LocaleController.getString(R.string.MirrlyPowerStateOff),
                        delChat,
                        false
                ));
                settings.add(new SettingItem(
                        LocaleController.getString(R.string.MirrlyVaultSaveHistory),
                        editHist ? LocaleController.getString(R.string.MirrlyPowerStateOn)
                                : LocaleController.getString(R.string.MirrlyPowerStateOff),
                        editHist,
                        false
                ));
                break;
            }
            case MODULE_TRANSLATOR: {
                boolean msgBtn = MirrlyConfig.isMsgTranslateButtonEnabled();
                boolean sttOn = MirrlyConfig.isFreeSttEnabled();
                active = msgBtn || sttOn || trOps > 0 || sttOps > 0;
                telemetryLine = LocaleController.formatString(
                        R.string.MirrlyPowerModTranslatorTelemetry,
                        trOps,
                        sttOps
                );
                settings.add(new SettingItem(
                        LocaleController.getString(R.string.MirrlyTranslatorProviderRow),
                        MirrlyTranslatorEngine.getProviderShortName(MirrlyConfig.getTranslatorProvider()),
                        msgBtn || trOps > 0,
                        false
                ));
                settings.add(new SettingItem(
                        LocaleController.getString(R.string.MirrlySttEnableToggle),
                        sttOn ? MirrlySttEngine.getProviderShortName(MirrlyConfig.getFreeSttProvider())
                                : LocaleController.getString(R.string.MirrlyPowerStateOff),
                        sttOn,
                        false
                ));
                break;
            }
            case MODULE_INTERFACE:
            default: {
                boolean vibOff = MirrlyConfig.isDisableAllVibrations();
                boolean soundsOn = MirrlyConfig.isInChatSendSoundEnabled() || MirrlyConfig.isInChatReceiveSoundEnabled();
                boolean hideStories = MirrlyInterfaceConfig.isHideTopStories();
                active = !vibOff || soundsOn || MirrlyConfig.isShowOwnChatTagEnabled();
                telemetryLine = LocaleController.formatString(
                        R.string.MirrlyPowerModInterfaceTelemetry,
                        formatShortMs(hapticMs),
                        soundOps
                );
                settings.add(new SettingItem(
                        LocaleController.getString(R.string.MirrlyVibrationDisableAll),
                        vibOff ? LocaleController.getString(R.string.MirrlyPowerSavingTag)
                                : LocaleController.getString(R.string.MirrlyPowerStateActive),
                        !vibOff,
                        vibOff
                ));
                settings.add(new SettingItem(
                        LocaleController.getString(R.string.MirrlyChatsSoundsHeader),
                        soundsOn ? LocaleController.getString(R.string.MirrlyPowerStateOn)
                                : LocaleController.getString(R.string.MirrlyPowerStateOff),
                        soundsOn,
                        false
                ));
                settings.add(new SettingItem(
                        LocaleController.getString(R.string.MirrlyInterfaceHideStories),
                        hideStories ? LocaleController.getString(R.string.MirrlyPowerSavingTag)
                                : LocaleController.getString(R.string.MirrlyPowerStateOff),
                        hideStories,
                        hideStories
                ));
                break;
            }
        }

        return new ModuleReport(
                moduleId,
                MODULE_COLOR_KEYS[moduleId],
                uah,
                batteryPct,
                sharePct,
                ratePctHr,
                active,
                telemetryLine,
                Collections.unmodifiableList(settings)
        );
    }

    // -------------------------------------------------------------------------
    // Evenly Spaced Grid & Native ChartData JSON Builders
    // -------------------------------------------------------------------------

    private static final class GridSeries {
        final long[] x;
        final long[][] modTenthsMah; // [MODULE_COUNT][N] in 0.1 mAh units
        final long[] battLevel;      // [N] in 0..100 %
        final long[] mirrlyMw;       // [N] in mW
        final int realPointCount;

        GridSeries(long[] x, long[][] modTenthsMah, long[] battLevel, long[] mirrlyMw, int realPointCount) {
            this.x = x;
            this.modTenthsMah = modTenthsMah;
            this.battLevel = battLevel;
            this.mirrlyMw = mirrlyMw;
            this.realPointCount = realPointCount;
        }
    }

    @NonNull
    private static GridSeries buildGridSeries(
            int period,
            @NonNull List<BucketRow> rows,
            long nowWall,
            long stepMs,
            int fallbackBattLevel
    ) {
        if (rows.size() < 2) {
            return new GridSeries(new long[0], new long[MODULE_COUNT][0], new long[0], new long[0], rows.size());
        }

        long firstAligned;
        long lastAligned;
        if (period == PERIOD_7D) {
            firstAligned = alignToLocalDay(rows.get(0).bucketTs);
            lastAligned = alignToLocalDay(nowWall);
            if (lastAligned <= firstAligned) {
                firstAligned = lastAligned - DAY_MS;
            }
        } else {
            firstAligned = (rows.get(0).bucketTs / stepMs) * stepMs;
            lastAligned = (rows.get(rows.size() - 1).bucketTs / stepMs) * stepMs;
            if (lastAligned <= firstAligned) {
                lastAligned = firstAligned + stepMs;
            }
        }

        int slots = (int) ((lastAligned - firstAligned) / stepMs) + 1;
        slots = Math.max(2, Math.min(slots, 96));
        firstAligned = lastAligned - (slots - 1L) * stepMs;

        long[] x = new long[slots];
        long[][] modUahSum = new long[MODULE_COUNT][slots];
        long[] battSum = new long[slots];
        int[] battCnt = new int[slots];
        long[] mwSum = new long[slots];
        int[] mwCnt = new int[slots];

        for (int i = 0; i < slots; i++) {
            x[i] = firstAligned + i * stepMs;
        }

        for (BucketRow r : rows) {
            long ts = period == PERIOD_7D ? alignToLocalDay(r.bucketTs) : ((r.bucketTs / stepMs) * stepMs);
            int idx = (int) ((ts - firstAligned) / stepMs);
            if (idx < 0 || idx >= slots) {
                continue;
            }
            for (int m = 0; m < MODULE_COUNT; m++) {
                modUahSum[m][idx] += r.modUah[m];
            }
            if (r.battLevel >= 0) {
                battSum[idx] += r.battLevel;
                battCnt[idx]++;
            }
            if (r.avgMirrlyMw > 0) {
                mwSum[idx] += r.avgMirrlyMw;
                mwCnt[idx]++;
            }
        }

        long[][] modTenthsMah = new long[MODULE_COUNT][slots];
        long[] batt = new long[slots];
        long[] mw = new long[slots];
        int lastKnownBatt = fallbackBattLevel >= 0 ? fallbackBattLevel : 50;
        for (int i = 0; i < slots; i++) {
            if (battCnt[i] > 0) {
                lastKnownBatt = (int) Math.round((double) battSum[i] / (double) battCnt[i]);
                break;
            }
        }

        for (int i = 0; i < slots; i++) {
            for (int m = 0; m < MODULE_COUNT; m++) {
                // Convert uAh to 0.1 mAh (100 uAh = 1 unit); preserve tiny non-zero values as 1 unit so visible
                long uah = modUahSum[m][i];
                modTenthsMah[m][i] = uah > 0 ? Math.max(1L, Math.round(uah / 100.0)) : 0L;
            }
            if (battCnt[i] > 0) {
                lastKnownBatt = (int) Math.round((double) battSum[i] / (double) battCnt[i]);
            }
            batt[i] = Math.max(0, Math.min(100, lastKnownBatt));
            mw[i] = mwCnt[i] > 0 ? Math.round((double) mwSum[i] / (double) mwCnt[i]) : 0L;
        }

        return new GridSeries(x, modTenthsMah, batt, mw, rows.size());
    }

    private static long alignToLocalDay(long wallMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(wallMs);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    @Nullable
    private static JSONObject buildModulesStackJson(@NonNull GridSeries grid, boolean percentageShare) {
        if (grid.x.length < 2) {
            return null;
        }
        try {
            boolean anyPositive = false;
            boolean[] include = new boolean[MODULE_COUNT];
            for (int m = 0; m < MODULE_COUNT; m++) {
                long sum = 0L;
                for (long v : grid.modTenthsMah[m]) {
                    sum += v;
                }
                if (sum > 0) {
                    include[m] = true;
                    anyPositive = true;
                }
            }
            if (!anyPositive) {
                return null;
            }

            JSONArray columns = new JSONArray();
            JSONArray xCol = new JSONArray();
            xCol.put("x");
            for (long t : grid.x) {
                xCol.put(t);
            }
            columns.put(xCol);

            JSONObject colors = new JSONObject();
            JSONObject names = new JSONObject();
            int yIdx = 0;
            for (int m = 0; m < MODULE_COUNT; m++) {
                if (!include[m]) {
                    continue;
                }
                String key = "y" + yIdx++;
                JSONArray yCol = new JSONArray();
                yCol.put(key);
                for (long v : grid.modTenthsMah[m]) {
                    yCol.put(v);
                }
                columns.put(yCol);
                colors.put(key, MODULE_COLOR_TOKENS[m]);
                names.put(key, getModuleShortTitle(m));
            }

            JSONObject root = new JSONObject();
            root.put("columns", columns);
            root.put("colors", colors);
            root.put("names", names);
            String fmt = percentageShare ? "ENERGY_MAH_TENTHS" : "ENERGY_MAH_TENTHS";
            root.put("yTickFormatter", fmt);
            root.put("yTooltipFormatter", fmt);
            root.put("xTickFormatter", "");
            root.put("xTooltipFormatter", "");
            return root;
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    @Nullable
    private static JSONObject buildBatteryVsPowerJson(@NonNull GridSeries grid) {
        if (grid.x.length < 2) {
            return null;
        }
        try {
            JSONArray columns = new JSONArray();
            JSONArray xCol = new JSONArray();
            xCol.put("x");
            for (long t : grid.x) {
                xCol.put(t);
            }
            columns.put(xCol);

            JSONArray battCol = new JSONArray();
            battCol.put("y0");
            for (long b : grid.battLevel) {
                battCol.put(b);
            }
            columns.put(battCol);

            JSONArray mwCol = new JSONArray();
            mwCol.put("y1");
            long maxMw = 0L;
            for (long p : grid.mirrlyMw) {
                if (p > maxMw) {
                    maxMw = p;
                }
                mwCol.put(p);
            }
            columns.put(mwCol);

            JSONObject colors = new JSONObject();
            colors.put("y0", "green#4BD964");
            colors.put("y1", "orange#F79E39");

            JSONObject names = new JSONObject();
            names.put("y0", LocaleController.getString(R.string.MirrlyPowerChartSeriesBattery));
            names.put("y1", LocaleController.getString(R.string.MirrlyPowerChartSeriesPowerMw));

            JSONObject units = new JSONObject();
            units.put("y0", "BATTERY_PCT");
            units.put("y1", "ENERGY_MW");

            JSONObject root = new JSONObject();
            root.put("columns", columns);
            root.put("colors", colors);
            root.put("names", names);
            root.put("units", units);
            root.put("yTickFormatter", "DUAL_UNIT");
            root.put("yTooltipFormatter", "DUAL_UNIT");
            root.put("xTickFormatter", "");
            root.put("xTooltipFormatter", "");
            return root;
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    @Nullable
    private static JSONObject build24HourClockJson(@NonNull List<BucketRow> hourlyRows) {
        if (hourlyRows.size() < 2) {
            return null;
        }
        long[] onByHour = new long[24];
        long[] offByHour = new long[24];
        boolean anyPositive = false;
        for (BucketRow r : hourlyRows) {
            int h = Math.max(0, Math.min(23, r.hourOfDay));
            onByHour[h] += r.screenOnUah;
            offByHour[h] += r.screenOffUah;
            if (r.screenOnUah > 0 || r.screenOffUah > 0) {
                anyPositive = true;
            }
        }
        if (!anyPositive) {
            return null;
        }
        try {
            JSONArray columns = new JSONArray();
            JSONArray xCol = new JSONArray();
            xCol.put("x");
            JSONArray yOn = new JSONArray();
            yOn.put("y0");
            JSONArray yOff = new JSONArray();
            yOff.put("y1");
            for (int h = 0; h < 24; h++) {
                xCol.put(h);
                yOn.put(onByHour[h] > 0 ? Math.max(1L, Math.round(onByHour[h] / 100.0)) : 0L);
                yOff.put(offByHour[h] > 0 ? Math.max(1L, Math.round(offByHour[h] / 100.0)) : 0L);
            }
            columns.put(xCol);
            columns.put(yOn);
            columns.put(yOff);

            JSONObject colors = new JSONObject();
            colors.put("y0", "blue#32A7F5");
            colors.put("y1", "purple#9B6BF2");

            JSONObject names = new JSONObject();
            names.put("y0", LocaleController.getString(R.string.MirrlyPowerChartSeriesActive));
            names.put("y1", LocaleController.getString(R.string.MirrlyPowerChartSeriesBackground));

            JSONObject root = new JSONObject();
            root.put("columns", columns);
            root.put("colors", colors);
            root.put("names", names);
            root.put("yTickFormatter", "ENERGY_MAH_TENTHS");
            root.put("yTooltipFormatter", "ENERGY_MAH_TENTHS");
            root.put("xTickFormatter", "");
            root.put("xTooltipFormatter", "");
            return root;
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    @NonNull
    private static List<EcoRecommendation> buildRecommendations(
            @NonNull ModuleReport[] modules,
            long totalUah,
            long pushWakeups,
            @NonNull SystemPowerSnapshot sys
    ) {
        ArrayList<EcoRecommendation> list = new ArrayList<>();
        SharedPreferences notifPrefs = MessagesController.getGlobalNotificationsSettings();
        boolean pushSvc = notifPrefs.getBoolean("pushService", true);

        if (pushSvc
                && MirrlyConfig.getPowerPushIntervalMinutes() <= MirrlyConfig.POWER_PUSH_INTERVAL_3_MIN
                && !MirrlyPowerPolicy.isAutoEcoActive()
                && ((totalUah > 2_000L && modules[MODULE_PUSH].sharePercent >= 32.0) || pushWakeups >= 24)) {
            list.add(new EcoRecommendation(
                    REC_PUSH_INTERVAL,
                    LocaleController.getString(R.string.MirrlyPowerRecPushTitle),
                    LocaleController.formatString(R.string.MirrlyPowerRecPushDesc, pushWakeups),
                    LocaleController.getString(R.string.MirrlyPowerRecPushAction)
            ));
        }

        if (MirrlyConfig.isGlassLiquidSupported()
                && MirrlyConfig.isGlassLiquidEnabled()
                && MirrlyConfig.isLiquidGlassEverywhere()
                && (sys.thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
                || (sys.batteryLevel >= 0 && sys.batteryLevel <= 25 && !sys.isCharging)
                || (totalUah > 2_000L && modules[MODULE_GLASS].sharePercent >= 30.0))) {
            list.add(new EcoRecommendation(
                    REC_LIQUID_EVERYWHERE,
                    LocaleController.getString(R.string.MirrlyPowerRecGlassTitle),
                    LocaleController.getString(R.string.MirrlyPowerRecGlassDesc),
                    LocaleController.getString(R.string.MirrlyPowerRecDisableAction)
            ));
        }

        if (MirrlyConfig.flowsealPerDcEnabled
                && totalUah > 2_000L
                && modules[MODULE_ENGINE].sharePercent >= 45.0) {
            list.add(new EcoRecommendation(
                    REC_FLOWSEAL_PER_DC,
                    LocaleController.getString(R.string.MirrlyPowerRecFlowsealTitle),
                    LocaleController.getString(R.string.MirrlyPowerRecFlowsealDesc),
                    LocaleController.getString(R.string.MirrlyPowerRecDisableAction)
            ));
        }

        if (Theme.isCurrentThemeDark() && !MirrlyConfig.isMonetAmoled() && MirrlyConfig.getMonetMode() != MirrlyConfig.MONET_MODE_OFF) {
            list.add(new EcoRecommendation(
                    REC_MONET_AMOLED,
                    LocaleController.getString(R.string.MirrlyPowerRecAmoledTitle),
                    LocaleController.getString(R.string.MirrlyPowerRecAmoledDesc),
                    LocaleController.getString(R.string.MirrlyPowerRecEnableAction)
            ));
        }

        return list;
    }

    // -------------------------------------------------------------------------
    // Formatting Helpers
    // -------------------------------------------------------------------------

    @NonNull
    public static String getModuleTitle(int moduleId) {
        switch (moduleId) {
            case MODULE_ENGINE:
                return LocaleController.getString(R.string.MirrlyPowerModEngine);
            case MODULE_PUSH:
                return LocaleController.getString(R.string.MirrlyPowerModPush);
            case MODULE_GLASS:
                return LocaleController.getString(R.string.MirrlyPowerModGlass);
            case MODULE_VAULT:
                return LocaleController.getString(R.string.MirrlyPowerModVault);
            case MODULE_TRANSLATOR:
                return LocaleController.getString(R.string.MirrlyPowerModTranslator);
            case MODULE_INTERFACE:
            default:
                return LocaleController.getString(R.string.MirrlyPowerModInterface);
        }
    }

    @NonNull
    public static String getModuleShortTitle(int moduleId) {
        switch (moduleId) {
            case MODULE_ENGINE:
                return LocaleController.getString(R.string.MirrlyPowerModEngineShort);
            case MODULE_PUSH:
                return LocaleController.getString(R.string.MirrlyPowerModPushShort);
            case MODULE_GLASS:
                return LocaleController.getString(R.string.MirrlyPowerModGlassShort);
            case MODULE_VAULT:
                return LocaleController.getString(R.string.MirrlyPowerModVaultShort);
            case MODULE_TRANSLATOR:
                return LocaleController.getString(R.string.MirrlyPowerModTranslatorShort);
            case MODULE_INTERFACE:
            default:
                return LocaleController.getString(R.string.MirrlyPowerModInterfaceShort);
        }
    }

    @NonNull
    public static String formatPercentCompact(double pct) {
        if (pct <= 0.004) {
            return "0%";
        }
        if (pct < 0.1) {
            return "<0.1%";
        }
        if (pct < 10.0) {
            return String.format(Locale.US, "%.1f%%", pct);
        }
        return String.format(Locale.US, "%d%%", Math.round(pct));
    }

    @NonNull
    public static String formatMah(long uah) {
        double mah = uah / 1000.0;
        String unit = LocaleController.getString(R.string.MirrlyPowerUnitMah);
        if (mah < 0.1) {
            return "0 " + unit;
        }
        if (mah < 10.0) {
            return String.format(Locale.US, "%.1f %s", mah, unit);
        }
        return String.format(Locale.US, "%d %s", Math.round(mah), unit);
    }

    @NonNull
    public static String formatCpuDuration(long cpuMs) {
        if (cpuMs < 1000L) {
            return cpuMs + " " + LocaleController.getString(R.string.MirrlyPowerUnitMs);
        }
        if (cpuMs < 60_000L) {
            return String.format(Locale.US, "%.1f %s", cpuMs / 1000.0, LocaleController.getString(R.string.MirrlyPowerUnitSec));
        }
        return LocaleController.formatShortDuration((int) Math.min(Integer.MAX_VALUE, cpuMs / 1000L));
    }

    @NonNull
    public static String formatShortMs(long ms) {
        if (ms <= 0L) {
            return "0 " + LocaleController.getString(R.string.MirrlyPowerUnitSec);
        }
        if (ms < 1000L) {
            return ms + " " + LocaleController.getString(R.string.MirrlyPowerUnitMs);
        }
        return LocaleController.formatShortDuration((int) Math.min(Integer.MAX_VALUE, Math.round(ms / 1000.0)));
    }

    @NonNull
    public static String formatThermalLabel(int thermalStatus) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return LocaleController.getString(R.string.MirrlyPowerThermalNormal);
        }
        switch (thermalStatus) {
            case PowerManager.THERMAL_STATUS_LIGHT:
                return LocaleController.getString(R.string.MirrlyPowerThermalLight);
            case PowerManager.THERMAL_STATUS_MODERATE:
                return LocaleController.getString(R.string.MirrlyPowerThermalModerate);
            case PowerManager.THERMAL_STATUS_SEVERE:
            case PowerManager.THERMAL_STATUS_CRITICAL:
            case PowerManager.THERMAL_STATUS_EMERGENCY:
            case PowerManager.THERMAL_STATUS_SHUTDOWN:
                return LocaleController.getString(R.string.MirrlyPowerThermalHigh);
            case PowerManager.THERMAL_STATUS_NONE:
            default:
                return LocaleController.getString(R.string.MirrlyPowerThermalNormal);
        }
    }

    // -------------------------------------------------------------------------
    // Immutable Data Models
    // -------------------------------------------------------------------------

    public static final class SystemPowerSnapshot {
        public final long timestampMs;
        public final int batteryLevel;
        public final boolean isCharging;
        public final int pluggedType;
        public final int batteryStatus;
        public final int batteryHealth;
        public final int voltageMv;
        public final int tempTenthsC;
        public final float currentNowMa;
        public final float currentAvgMa;
        public final int systemPowerMw;
        public final long chargeCounterUah;
        public final double effectiveCapacityMah;
        public final boolean systemPowerSaveMode;
        public final boolean ignoringBatteryOptimizations;
        public final boolean deviceIdleMode;
        public final boolean screenOn;
        public final int thermalStatus;
        public final int networkType;

        SystemPowerSnapshot(
                long timestampMs,
                int batteryLevel,
                boolean isCharging,
                int pluggedType,
                int batteryStatus,
                int batteryHealth,
                int voltageMv,
                int tempTenthsC,
                float currentNowMa,
                float currentAvgMa,
                int systemPowerMw,
                long chargeCounterUah,
                double effectiveCapacityMah,
                boolean systemPowerSaveMode,
                boolean ignoringBatteryOptimizations,
                boolean deviceIdleMode,
                boolean screenOn,
                int thermalStatus,
                int networkType
        ) {
            this.timestampMs = timestampMs;
            this.batteryLevel = batteryLevel;
            this.isCharging = isCharging;
            this.pluggedType = pluggedType;
            this.batteryStatus = batteryStatus;
            this.batteryHealth = batteryHealth;
            this.voltageMv = voltageMv;
            this.tempTenthsC = tempTenthsC;
            this.currentNowMa = currentNowMa;
            this.currentAvgMa = currentAvgMa;
            this.systemPowerMw = systemPowerMw;
            this.chargeCounterUah = chargeCounterUah;
            this.effectiveCapacityMah = effectiveCapacityMah;
            this.systemPowerSaveMode = systemPowerSaveMode;
            this.ignoringBatteryOptimizations = ignoringBatteryOptimizations;
            this.deviceIdleMode = deviceIdleMode;
            this.screenOn = screenOn;
            this.thermalStatus = thermalStatus;
            this.networkType = networkType;
        }

        static SystemPowerSnapshot empty() {
            return new SystemPowerSnapshot(
                    0L, -1, false, 0, BatteryManager.BATTERY_STATUS_UNKNOWN, BatteryManager.BATTERY_HEALTH_UNKNOWN,
                    0, 0, 0f, 0f, 0, -1L, DEFAULT_CAPACITY_MAH, false, false, false, true, 0, NET_NONE
            );
        }

        public float getTempCelsius() {
            return tempTenthsC / 10f;
        }
    }

    public static final class SettingItem {
        public final String title;
        public final String status;
        public final boolean active;
        public final boolean isSaving;

        public SettingItem(String title, String status, boolean active, boolean isSaving) {
            this.title = title;
            this.status = status;
            this.active = active;
            this.isSaving = isSaving;
        }
    }

    public static final class ModuleReport {
        public final int moduleId;
        public final int colorKey;
        public final long uah;
        public final double batteryPercent;
        public final double sharePercent;
        public final double ratePctPerHour;
        public final boolean active;
        public final String telemetryLine;
        public final List<SettingItem> settings;

        ModuleReport(
                int moduleId,
                int colorKey,
                long uah,
                double batteryPercent,
                double sharePercent,
                double ratePctPerHour,
                boolean active,
                String telemetryLine,
                List<SettingItem> settings
        ) {
            this.moduleId = moduleId;
            this.colorKey = colorKey;
            this.uah = uah;
            this.batteryPercent = batteryPercent;
            this.sharePercent = sharePercent;
            this.ratePctPerHour = ratePctPerHour;
            this.active = active;
            this.telemetryLine = telemetryLine;
            this.settings = settings;
        }
    }

    public static final class EcoRecommendation {
        public final int id;
        public final String title;
        public final String description;
        public final String actionLabel;

        EcoRecommendation(int id, String title, String description, String actionLabel) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.actionLabel = actionLabel;
        }
    }

    public static final class PowerReport {
        public final int period;
        public final long startWallMs;
        public final long endWallMs;
        public final long observedDurationMs;
        public final int bucketCount;
        public final long totalMirrlyUah;
        public final long prevMirrlyUah;
        public final long totalSavedUah;
        public final long totalDeviceDrainUah;
        public final double totalBatteryPercent;
        public final double deltaBatteryPercent;
        public final boolean hasPrevComparison;
        public final double savedBatteryPercent;
        public final double drainRatePctPerHour;
        public final double avgMirrlyMa;
        public final int avgMirrlyMw;
        public final long screenOnMs;
        public final long screenOffMs;
        public final long pushWakeups;
        public final long pushWakeLockMs;
        public final ModuleReport[] modulesById;
        public final ModuleReport[] rankedModules;
        public final SystemPowerSnapshot system;
        public final List<EcoRecommendation> recommendations;
        @Nullable public final JSONObject stackBarChartJson;
        @Nullable public final JSONObject stackShareChartJson;
        @Nullable public final JSONObject batteryVsPowerChartJson;
        @Nullable public final JSONObject clock24hChartJson;
        public final int chartPointCount;

        PowerReport(
                int period,
                long startWallMs,
                long endWallMs,
                long observedDurationMs,
                int bucketCount,
                long totalMirrlyUah,
                long prevMirrlyUah,
                long totalSavedUah,
                long totalDeviceDrainUah,
                double totalBatteryPercent,
                double deltaBatteryPercent,
                boolean hasPrevComparison,
                double savedBatteryPercent,
                double drainRatePctPerHour,
                double avgMirrlyMa,
                int avgMirrlyMw,
                long screenOnMs,
                long screenOffMs,
                long pushWakeups,
                long pushWakeLockMs,
                ModuleReport[] modulesById,
                ModuleReport[] rankedModules,
                SystemPowerSnapshot system,
                List<EcoRecommendation> recommendations,
                @Nullable JSONObject stackBarChartJson,
                @Nullable JSONObject stackShareChartJson,
                @Nullable JSONObject batteryVsPowerChartJson,
                @Nullable JSONObject clock24hChartJson,
                int chartPointCount
        ) {
            this.period = period;
            this.startWallMs = startWallMs;
            this.endWallMs = endWallMs;
            this.observedDurationMs = observedDurationMs;
            this.bucketCount = bucketCount;
            this.totalMirrlyUah = totalMirrlyUah;
            this.prevMirrlyUah = prevMirrlyUah;
            this.totalSavedUah = totalSavedUah;
            this.totalDeviceDrainUah = totalDeviceDrainUah;
            this.totalBatteryPercent = totalBatteryPercent;
            this.deltaBatteryPercent = deltaBatteryPercent;
            this.hasPrevComparison = hasPrevComparison;
            this.savedBatteryPercent = savedBatteryPercent;
            this.drainRatePctPerHour = drainRatePctPerHour;
            this.avgMirrlyMa = avgMirrlyMa;
            this.avgMirrlyMw = avgMirrlyMw;
            this.screenOnMs = screenOnMs;
            this.screenOffMs = screenOffMs;
            this.pushWakeups = pushWakeups;
            this.pushWakeLockMs = pushWakeLockMs;
            this.modulesById = modulesById;
            this.rankedModules = rankedModules;
            this.system = system;
            this.recommendations = recommendations;
            this.stackBarChartJson = stackBarChartJson;
            this.stackShareChartJson = stackShareChartJson;
            this.batteryVsPowerChartJson = batteryVsPowerChartJson;
            this.clock24hChartJson = clock24hChartJson;
            this.chartPointCount = chartPointCount;
        }

        @NonNull
        public String buildClipboardReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("MirrlyGram Power Analytics\n");
            sb.append("Period: ").append(period == PERIOD_SESSION ? "Session" : (period == PERIOD_7D ? "7d" : "24h")).append("\n");
            sb.append("Mirrly Impact: ").append(formatPercentCompact(totalBatteryPercent))
                    .append(" (").append(String.format(Locale.US, "%.1f mAh", totalMirrlyUah / 1000.0))
                    .append(", ").append(String.format(Locale.US, "%.2f%%/h", drainRatePctPerHour)).append(")\n");
            if (totalSavedUah > 0) {
                sb.append("Estimated Savings: ").append(String.format(Locale.US, "%.1f mAh", totalSavedUah / 1000.0)).append("\n");
            }
            sb.append("System Battery: ").append(system.batteryLevel).append("%")
                    .append(system.isCharging ? " (charging)" : " (discharging)")
                    .append(", ").append(Math.round(system.currentNowMa)).append(" mA")
                    .append(", ").append(system.systemPowerMw).append(" mW")
                    .append(", ").append(String.format(Locale.US, "%.1f C", system.getTempCelsius())).append("\n");
            sb.append("System Flags: powerSave=").append(system.systemPowerSaveMode)
                    .append(", doze=").append(system.deviceIdleMode)
                    .append(", unrestricted=").append(system.ignoringBatteryOptimizations)
                    .append(", thermal=").append(system.thermalStatus)
                    .append(", autoEco=").append(MirrlyPowerPolicy.isAutoEcoActive()).append("\n");
            sb.append("Push KeepAlive: ").append(MirrlyPowerPolicy.getEffectivePushKeepAliveMinutes())
                    .append("m, wakeups=").append(pushWakeups)
                    .append(", wakeLockMs=").append(pushWakeLockMs).append("\n");
            sb.append("Modules:\n");
            for (ModuleReport m : rankedModules) {
                sb.append("  - ").append(getModuleTitle(m.moduleId)).append(": ")
                        .append(formatPercentCompact(m.batteryPercent))
                        .append(" (share ").append(formatPercentCompact(m.sharePercent))
                        .append(", ").append(String.format(Locale.US, "%.1f mAh", m.uah / 1000.0)).append(")\n");
            }
            return sb.toString();
        }
    }
}
