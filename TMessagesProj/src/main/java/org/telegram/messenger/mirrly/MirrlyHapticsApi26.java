package org.telegram.messenger.mirrly;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Isolated API 26+ vibration and notification-channel helpers for {@link MirrlyHaptics}.
 */
@TargetApi(26)
public final class MirrlyHapticsApi26 {

    private MirrlyHapticsApi26() {
    }

    public static void vibrate(Vibrator vibrator, VibrationEffect vibe) {
        if (vibrator == null || vibe == null || Build.VERSION.SDK_INT < 26 || MirrlyHaptics.isDisabled()) {
            return;
        }
        try {
            vibrator.vibrate(vibe);
            MirrlyPowerTelemetry.recordHapticPulse(22L);
        } catch (Throwable ignore) {
        }
    }

    public static void vibrate(Vibrator vibrator, VibrationEffect vibe, AudioAttributes attributes) {
        if (vibrator == null || vibe == null || Build.VERSION.SDK_INT < 26 || MirrlyHaptics.isDisabled()) {
            return;
        }
        try {
            vibrator.vibrate(vibe, attributes);
            MirrlyPowerTelemetry.recordHapticPulse(22L);
        } catch (Throwable ignore) {
        }
    }

    public static void enableVibration(NotificationChannel channel, boolean enable) {
        if (channel == null || Build.VERSION.SDK_INT < 26) {
            return;
        }
        try {
            channel.enableVibration(!MirrlyHaptics.isDisabled() && enable);
        } catch (Throwable ignore) {
        }
    }

    public static void setVibrationPattern(NotificationChannel channel, long[] pattern) {
        if (channel == null || Build.VERSION.SDK_INT < 26) {
            return;
        }
        try {
            if (MirrlyHaptics.isDisabled()) {
                channel.enableVibration(false);
                channel.setVibrationPattern(MirrlyHaptics.EMPTY_VIBRATION);
            } else {
                channel.setVibrationPattern(pattern);
            }
        } catch (Throwable ignore) {
        }
    }
}
