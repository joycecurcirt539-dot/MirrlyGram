package org.telegram.messenger.mirrly;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsService;

/**
 * Coordinates MirrlyGram's own automatic power-saving (Auto-Eco) profile,
 * adaptive background push keep-alive interval, and lossless backup/restore
 * of user settings when entering or leaving Eco mode.
 */
public final class MirrlyPowerPolicy {

    private static final String KEY_ECO_ACTIVE = "power_eco_active_v1";
    private static final String KEY_SAVED_LIQUID_EVERYWHERE = "power_eco_saved_liquid_everywhere";
    private static final String KEY_SAVED_GLASS_BLUR = "power_eco_saved_glass_blur";
    private static final String KEY_SAVED_VIBRATIONS = "power_eco_saved_vibrations";
    private static final String KEY_APPLIED_MASK = "power_eco_applied_mask";

    private static final int HYSTERESIS_PERCENT = 3;

    private static final Object LOCK = new Object();
    private static volatile boolean ecoActive = false;
    private static volatile boolean loaded = false;

    private MirrlyPowerPolicy() {
    }

    private static void ensureLoadedLocked() {
        if (loaded || ApplicationLoader.applicationContext == null) {
            return;
        }
        try {
            SharedPreferences prefs = MirrlyConfig.getPowerPrefs();
            ecoActive = prefs.getBoolean(KEY_ECO_ACTIVE, false);
            loaded = true;
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static boolean isAutoEcoActive() {
        if (!loaded) {
            synchronized (LOCK) {
                ensureLoadedLocked();
            }
        }
        return ecoActive;
    }

    /**
     * Returns the effective interval (in milliseconds) for {@link NotificationsService}
     * keep-alive alarms, respecting user preference and active Auto-Eco override.
     */
    public static long getEffectivePushKeepAliveIntervalMs() {
        int userMinutes = MirrlyConfig.getPowerPushIntervalMinutes();
        int effectiveMinutes = userMinutes;
        if (isAutoEcoActive() && MirrlyConfig.isPowerEcoFlagEnabled(MirrlyConfig.POWER_ECO_FLAG_PUSH_INTERVAL)) {
            effectiveMinutes = Math.max(userMinutes, 15);
        }
        return effectiveMinutes * 60_000L;
    }

    /**
     * Returns the effective short partial WakeLock timeout (in milliseconds) for
     * {@link NotificationsService} keep-alive ticks.
     */
    public static long getEffectivePushWakeLockTimeoutMs() {
        long intervalMs = getEffectivePushKeepAliveIntervalMs();
        if (intervalMs >= 15 * 60_000L) {
            return 3_000L;
        }
        if (intervalMs >= 10 * 60_000L) {
            return 4_500L;
        }
        return 8_000L;
    }

    public static int getEffectivePushKeepAliveMinutes() {
        return (int) Math.max(1L, getEffectivePushKeepAliveIntervalMs() / 60_000L);
    }

    public static void onPushIntervalConfigChanged() {
        reschedulePushAlarmIfNeeded();
    }

    public static void evaluateAsync() {
        MirrlyPowerTelemetry.SystemPowerSnapshot snap = MirrlyPowerTelemetry.getLatestSystemSnapshot();
        evaluate(snap.batteryLevel, snap.isCharging, snap.systemPowerSaveMode);
    }

    public static void evaluate(int batteryLevel, boolean isCharging, boolean systemPowerSave) {
        if (ApplicationLoader.applicationContext == null) {
            return;
        }
        MirrlyConfig.load();
        int threshold = MirrlyConfig.getPowerAutoEcoLevel();
        boolean followSystem = MirrlyConfig.isPowerFollowSystemSaver();

        boolean changed = false;
        synchronized (LOCK) {
            ensureLoadedLocked();
            boolean shouldBeActive = computeTargetState(threshold, followSystem, batteryLevel, isCharging, systemPowerSave, ecoActive);
            if (shouldBeActive != ecoActive) {
                if (shouldBeActive) {
                    engageEcoLocked();
                } else {
                    disengageEcoLocked();
                }
                changed = true;
            }
        }
        if (changed) {
            reschedulePushAlarmIfNeeded();
            AndroidUtilities.runOnUIThread(() ->
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyPowerPolicyChanged)
            );
        }
    }

    /**
     * Reconciles applied flags if the user toggled individual eco rules while Auto-Eco is active.
     */
    public static void reconcileIfActive() {
        if (ApplicationLoader.applicationContext == null) {
            return;
        }
        boolean changed = false;
        synchronized (LOCK) {
            ensureLoadedLocked();
            if (!ecoActive) {
                return;
            }
            changed = reconcileMaskLocked();
        }
        if (changed) {
            reschedulePushAlarmIfNeeded();
            AndroidUtilities.runOnUIThread(() ->
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyPowerPolicyChanged)
            );
        }
    }

    private static boolean computeTargetState(
            int threshold,
            boolean followSystem,
            int batteryLevel,
            boolean isCharging,
            boolean systemPowerSave,
            boolean currentlyActive
    ) {
        if (threshold >= 100) {
            return true;
        }
        if (followSystem && systemPowerSave) {
            return true;
        }
        if (threshold <= 0 || isCharging || batteryLevel < 0) {
            return false;
        }
        if (currentlyActive) {
            int disengageLevel = Math.min(100, threshold + HYSTERESIS_PERCENT);
            return batteryLevel < disengageLevel;
        }
        return batteryLevel <= threshold;
    }

    private static void engageEcoLocked() {
        SharedPreferences prefs = MirrlyConfig.getPowerPrefs();
        int targetMask = MirrlyConfig.getPowerEcoMask();
        int appliedMask = 0;

        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_ECO_ACTIVE, true);

        if ((targetMask & MirrlyConfig.POWER_ECO_FLAG_LIQUID_EVERYWHERE) != 0) {
            boolean prev = MirrlyConfig.isLiquidGlassEverywhere();
            editor.putBoolean(KEY_SAVED_LIQUID_EVERYWHERE, prev);
            if (prev) {
                MirrlyConfig.setLiquidGlassEverywhere(false);
            }
            appliedMask |= MirrlyConfig.POWER_ECO_FLAG_LIQUID_EVERYWHERE;
        }
        if ((targetMask & MirrlyConfig.POWER_ECO_FLAG_GLASS_BLUR) != 0) {
            boolean prev = MirrlyConfig.isGlassBlurEnabled();
            editor.putBoolean(KEY_SAVED_GLASS_BLUR, prev);
            if (prev) {
                MirrlyConfig.setGlassBlurEnabled(false);
            }
            appliedMask |= MirrlyConfig.POWER_ECO_FLAG_GLASS_BLUR;
        }
        if ((targetMask & MirrlyConfig.POWER_ECO_FLAG_VIBRATIONS) != 0) {
            boolean prev = MirrlyConfig.isDisableAllVibrations();
            editor.putBoolean(KEY_SAVED_VIBRATIONS, prev);
            if (!prev) {
                MirrlyConfig.setDisableAllVibrations(true);
            }
            appliedMask |= MirrlyConfig.POWER_ECO_FLAG_VIBRATIONS;
        }
        if ((targetMask & MirrlyConfig.POWER_ECO_FLAG_PUSH_INTERVAL) != 0) {
            appliedMask |= MirrlyConfig.POWER_ECO_FLAG_PUSH_INTERVAL;
        }

        editor.putInt(KEY_APPLIED_MASK, appliedMask);
        editor.apply();
        ecoActive = true;
    }

    private static void disengageEcoLocked() {
        SharedPreferences prefs = MirrlyConfig.getPowerPrefs();
        int appliedMask = prefs.getInt(KEY_APPLIED_MASK, 0);

        if ((appliedMask & MirrlyConfig.POWER_ECO_FLAG_GLASS_BLUR) != 0 && prefs.contains(KEY_SAVED_GLASS_BLUR)) {
            boolean saved = prefs.getBoolean(KEY_SAVED_GLASS_BLUR, true);
            MirrlyConfig.setGlassBlurEnabled(saved);
        }
        if ((appliedMask & MirrlyConfig.POWER_ECO_FLAG_LIQUID_EVERYWHERE) != 0 && prefs.contains(KEY_SAVED_LIQUID_EVERYWHERE)) {
            boolean saved = prefs.getBoolean(KEY_SAVED_LIQUID_EVERYWHERE, true);
            MirrlyConfig.setLiquidGlassEverywhere(saved);
        }
        if ((appliedMask & MirrlyConfig.POWER_ECO_FLAG_VIBRATIONS) != 0 && prefs.contains(KEY_SAVED_VIBRATIONS)) {
            boolean saved = prefs.getBoolean(KEY_SAVED_VIBRATIONS, false);
            MirrlyConfig.setDisableAllVibrations(saved);
        }

        prefs.edit()
                .putBoolean(KEY_ECO_ACTIVE, false)
                .putInt(KEY_APPLIED_MASK, 0)
                .remove(KEY_SAVED_LIQUID_EVERYWHERE)
                .remove(KEY_SAVED_GLASS_BLUR)
                .remove(KEY_SAVED_VIBRATIONS)
                .apply();
        ecoActive = false;
    }

    private static boolean reconcileMaskLocked() {
        SharedPreferences prefs = MirrlyConfig.getPowerPrefs();
        int targetMask = MirrlyConfig.getPowerEcoMask();
        int appliedMask = prefs.getInt(KEY_APPLIED_MASK, 0);
        if (targetMask == appliedMask) {
            return false;
        }

        SharedPreferences.Editor editor = prefs.edit();
        int nextApplied = appliedMask;

        // Liquid glass everywhere
        boolean wantLiquid = (targetMask & MirrlyConfig.POWER_ECO_FLAG_LIQUID_EVERYWHERE) != 0;
        boolean hadLiquid = (appliedMask & MirrlyConfig.POWER_ECO_FLAG_LIQUID_EVERYWHERE) != 0;
        if (wantLiquid && !hadLiquid) {
            boolean prev = MirrlyConfig.isLiquidGlassEverywhere();
            editor.putBoolean(KEY_SAVED_LIQUID_EVERYWHERE, prev);
            if (prev) {
                MirrlyConfig.setLiquidGlassEverywhere(false);
            }
            nextApplied |= MirrlyConfig.POWER_ECO_FLAG_LIQUID_EVERYWHERE;
        } else if (!wantLiquid && hadLiquid) {
            if (prefs.contains(KEY_SAVED_LIQUID_EVERYWHERE)) {
                MirrlyConfig.setLiquidGlassEverywhere(prefs.getBoolean(KEY_SAVED_LIQUID_EVERYWHERE, true));
                editor.remove(KEY_SAVED_LIQUID_EVERYWHERE);
            }
            nextApplied &= ~MirrlyConfig.POWER_ECO_FLAG_LIQUID_EVERYWHERE;
        }

        // Glass blur
        boolean wantBlur = (targetMask & MirrlyConfig.POWER_ECO_FLAG_GLASS_BLUR) != 0;
        boolean hadBlur = (appliedMask & MirrlyConfig.POWER_ECO_FLAG_GLASS_BLUR) != 0;
        if (wantBlur && !hadBlur) {
            boolean prev = MirrlyConfig.isGlassBlurEnabled();
            editor.putBoolean(KEY_SAVED_GLASS_BLUR, prev);
            if (prev) {
                MirrlyConfig.setGlassBlurEnabled(false);
            }
            nextApplied |= MirrlyConfig.POWER_ECO_FLAG_GLASS_BLUR;
        } else if (!wantBlur && hadBlur) {
            if (prefs.contains(KEY_SAVED_GLASS_BLUR)) {
                MirrlyConfig.setGlassBlurEnabled(prefs.getBoolean(KEY_SAVED_GLASS_BLUR, true));
                editor.remove(KEY_SAVED_GLASS_BLUR);
            }
            nextApplied &= ~MirrlyConfig.POWER_ECO_FLAG_GLASS_BLUR;
        }

        // Vibrations
        boolean wantVib = (targetMask & MirrlyConfig.POWER_ECO_FLAG_VIBRATIONS) != 0;
        boolean hadVib = (appliedMask & MirrlyConfig.POWER_ECO_FLAG_VIBRATIONS) != 0;
        if (wantVib && !hadVib) {
            boolean prev = MirrlyConfig.isDisableAllVibrations();
            editor.putBoolean(KEY_SAVED_VIBRATIONS, prev);
            if (!prev) {
                MirrlyConfig.setDisableAllVibrations(true);
            }
            nextApplied |= MirrlyConfig.POWER_ECO_FLAG_VIBRATIONS;
        } else if (!wantVib && hadVib) {
            if (prefs.contains(KEY_SAVED_VIBRATIONS)) {
                MirrlyConfig.setDisableAllVibrations(prefs.getBoolean(KEY_SAVED_VIBRATIONS, false));
                editor.remove(KEY_SAVED_VIBRATIONS);
            }
            nextApplied &= ~MirrlyConfig.POWER_ECO_FLAG_VIBRATIONS;
        }

        // Push interval
        if ((targetMask & MirrlyConfig.POWER_ECO_FLAG_PUSH_INTERVAL) != 0) {
            nextApplied |= MirrlyConfig.POWER_ECO_FLAG_PUSH_INTERVAL;
        } else {
            nextApplied &= ~MirrlyConfig.POWER_ECO_FLAG_PUSH_INTERVAL;
        }

        editor.putInt(KEY_APPLIED_MASK, nextApplied);
        editor.apply();
        return true;
    }

    private static void reschedulePushAlarmIfNeeded() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) {
            return;
        }
        try {
            boolean pushEnabled = MessagesController.getGlobalNotificationsSettings().getBoolean("pushService", true);
            if (pushEnabled) {
                NotificationsService.scheduleKeepAliveAlarm(ctx, true);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
