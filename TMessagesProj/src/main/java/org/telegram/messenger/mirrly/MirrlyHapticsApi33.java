package org.telegram.messenger.mirrly;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.CombinedVibration;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * Isolated API 33+ {@link VibrationAttributes} helpers for {@link MirrlyHaptics}.
 */
@TargetApi(33)
public final class MirrlyHapticsApi33 {

    private MirrlyHapticsApi33() {
    }

    public static void vibrate(Vibrator vibrator, VibrationEffect vibe, VibrationAttributes attributes) {
        if (vibrator == null || vibe == null || Build.VERSION.SDK_INT < 33 || MirrlyHaptics.isDisabled()) {
            return;
        }
        try {
            vibrator.vibrate(vibe, attributes);
        } catch (Throwable ignore) {
        }
    }

    public static void vibrate(VibratorManager vm, CombinedVibration effect, VibrationAttributes attributes) {
        if (vm == null || effect == null || Build.VERSION.SDK_INT < 33 || MirrlyHaptics.isDisabled()) {
            return;
        }
        try {
            vm.vibrate(effect, attributes);
        } catch (Throwable ignore) {
        }
    }
}
