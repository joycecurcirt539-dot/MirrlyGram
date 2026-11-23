package org.telegram.messenger.mirrly;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.app.Notification;
import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Vibrator;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

/**
 * Central vibration and haptic gatekeeper (kept <= API 23 class references for pre-Oreo ART/Dalvik verification).
 */
public final class MirrlyHaptics {

    public static final long[] EMPTY_VIBRATION = new long[]{0, 0};

    private static volatile boolean everDisabledViewTree = false;

    private MirrlyHaptics() {
    }

    public static boolean isDisabled() {
        return MirrlyConfig.isDisableAllVibrations();
    }

    public static void cancelActiveVibration() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 31) {
            MirrlyHapticsApi31.cancelAll(ctx);
        }
        try {
            Vibrator vibrator = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.cancel();
            }
        } catch (Throwable ignore) {
        }
    }

    public static void onDisableAllVibrationsChanged(boolean disabled) {
        if (disabled) {
            cancelActiveVibration();
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (LaunchActivity.instance != null) {
                syncWindowHaptics(LaunchActivity.instance.getWindow());
            }
        });
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            try {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    NotificationsController.getInstance(a).onMirrlyVibrationPolicyChanged(disabled);
                }
            } catch (Throwable ignore) {
            }
        }
    }

    public static void syncWindowHaptics(@Nullable Window window) {
        if (window == null) {
            return;
        }
        View decor = null;
        try {
            decor = window.peekDecorView();
            if (decor == null) {
                decor = window.getDecorView();
            }
        } catch (Throwable ignore) {
        }
        syncViewTreeHaptics(decor);
    }

    public static void syncDialogHaptics(@Nullable Dialog dialog) {
        if (dialog == null) {
            return;
        }
        try {
            syncWindowHaptics(dialog.getWindow());
        } catch (Throwable ignore) {
        }
    }

    public static void syncViewTreeHaptics(@Nullable View root) {
        if (root == null) {
            return;
        }
        final boolean disabled = isDisabled();
        if (!disabled && !everDisabledViewTree) {
            return;
        }
        if (disabled) {
            everDisabledViewTree = true;
        }
        ArrayList<View> stack = new ArrayList<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            View v = stack.remove(stack.size() - 1);
            if (v == null) {
                continue;
            }
            if (disabled) {
                if (v.getTag(R.id.mirrly_haptic_prev_tag) == null) {
                    v.setTag(R.id.mirrly_haptic_prev_tag, v.isHapticFeedbackEnabled());
                }
                if (v.isHapticFeedbackEnabled()) {
                    v.setHapticFeedbackEnabled(false);
                }
            } else {
                Object prev = v.getTag(R.id.mirrly_haptic_prev_tag);
                if (prev instanceof Boolean) {
                    v.setHapticFeedbackEnabled((Boolean) prev);
                    v.setTag(R.id.mirrly_haptic_prev_tag, null);
                }
            }
            if (v instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) v;
                for (int i = vg.getChildCount() - 1; i >= 0; i--) {
                    View child = vg.getChildAt(i);
                    if (child != null) {
                        stack.add(child);
                    }
                }
            }
        }
    }

    public static boolean performHapticFeedback(View view, int feedbackConstant) {
        if (view == null || isDisabled()) {
            return false;
        }
        boolean performed = view.performHapticFeedback(feedbackConstant);
        if (performed) {
            MirrlyPowerTelemetry.recordHapticPulse(14L);
        }
        return performed;
    }

    public static boolean performHapticFeedback(View view, int feedbackConstant, int flags) {
        if (view == null || isDisabled()) {
            return false;
        }
        boolean performed = view.performHapticFeedback(feedbackConstant, flags);
        if (performed) {
            MirrlyPowerTelemetry.recordHapticPulse(14L);
        }
        return performed;
    }

    public static void setHapticFeedbackEnabled(View view, boolean enabled) {
        if (view == null) {
            return;
        }
        if (isDisabled()) {
            everDisabledViewTree = true;
            view.setTag(R.id.mirrly_haptic_prev_tag, enabled);
            view.setHapticFeedbackEnabled(false);
        } else {
            view.setTag(R.id.mirrly_haptic_prev_tag, null);
            view.setHapticFeedbackEnabled(enabled);
        }
    }

    public static void vibrate(Vibrator vibrator, long milliseconds) {
        if (vibrator == null || isDisabled()) {
            return;
        }
        try {
            vibrator.vibrate(milliseconds);
            MirrlyPowerTelemetry.recordHapticPulse(milliseconds);
        } catch (Throwable ignore) {
        }
    }

    @TargetApi(21)
    public static void vibrate(Vibrator vibrator, long milliseconds, AudioAttributes attributes) {
        if (vibrator == null || isDisabled()) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                vibrator.vibrate(milliseconds, attributes);
            } else {
                vibrator.vibrate(milliseconds);
            }
            MirrlyPowerTelemetry.recordHapticPulse(milliseconds);
        } catch (Throwable ignore) {
        }
    }

    public static void vibrate(Vibrator vibrator, long[] pattern, int repeat) {
        if (vibrator == null || pattern == null || isDisabled()) {
            return;
        }
        try {
            vibrator.vibrate(pattern, repeat);
            MirrlyPowerTelemetry.recordHapticPulse(sumPatternActiveMs(pattern));
        } catch (Throwable ignore) {
        }
    }

    @TargetApi(21)
    public static void vibrate(Vibrator vibrator, long[] pattern, int repeat, AudioAttributes attributes) {
        if (vibrator == null || pattern == null || isDisabled()) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                vibrator.vibrate(pattern, repeat, attributes);
            } else {
                vibrator.vibrate(pattern, repeat);
            }
            MirrlyPowerTelemetry.recordHapticPulse(sumPatternActiveMs(pattern));
        } catch (Throwable ignore) {
        }
    }

    private static long sumPatternActiveMs(long[] pattern) {
        long total = 0L;
        for (int i = 1; i < pattern.length; i += 2) {
            if (pattern[i] > 0) {
                total += pattern[i];
            }
        }
        return total > 0 ? total : 25L;
    }

    public static NotificationCompat.Builder setVibrate(NotificationCompat.Builder builder, long[] pattern) {
        if (builder == null) {
            return null;
        }
        return builder.setVibrate(isDisabled() ? EMPTY_VIBRATION : pattern);
    }

    public static Notification.Builder setVibrate(Notification.Builder builder, long[] pattern) {
        if (builder == null) {
            return null;
        }
        return builder.setVibrate(isDisabled() ? EMPTY_VIBRATION : pattern);
    }

    public static NotificationCompat.Builder setDefaults(NotificationCompat.Builder builder, int defaults) {
        if (builder == null) {
            return null;
        }
        if (isDisabled()) {
            defaults &= ~NotificationCompat.DEFAULT_VIBRATE;
            builder.setVibrate(EMPTY_VIBRATION);
        }
        return builder.setDefaults(defaults);
    }

    public static Notification.Builder setDefaults(Notification.Builder builder, int defaults) {
        if (builder == null) {
            return null;
        }
        if (isDisabled()) {
            defaults &= ~Notification.DEFAULT_VIBRATE;
            builder.setVibrate(EMPTY_VIBRATION);
        }
        return builder.setDefaults(defaults);
    }
}
