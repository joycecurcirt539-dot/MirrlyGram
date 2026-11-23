package org.telegram.messenger.mirrly;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.CombinedVibration;
import android.os.VibratorManager;

/**
 * Isolated API 31+ {@link VibratorManager} helpers for {@link MirrlyHaptics}.
 */
@TargetApi(31)
public final class MirrlyHapticsApi31 {

    private MirrlyHapticsApi31() {
    }

    public static void cancelAll(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 31) {
            return;
        }
        try {
            VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (vm != null) {
                vm.cancel();
            }
        } catch (Throwable ignore) {
        }
    }

    public static void vibrate(VibratorManager vm, CombinedVibration effect) {
        if (vm == null || effect == null || Build.VERSION.SDK_INT < 31 || MirrlyHaptics.isDisabled()) {
            return;
        }
        try {
            vm.vibrate(effect);
        } catch (Throwable ignore) {
        }
    }
}
