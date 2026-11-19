/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AppStartReceiver extends BroadcastReceiver {

    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        boolean isBoot = Intent.ACTION_BOOT_COMPLETED.equals(action);
        boolean isRestart = "org.telegram.start".equals(action);
        if (isBoot || isRestart) {
            AndroidUtilities.runOnUIThread(() -> {
                ApplicationLoader.postInitApplication();
                SharedConfig.loadConfig();
                if (isBoot && SharedConfig.passcodeHash.length() > 0) {
                    SharedConfig.appLocked = true;
                    SharedConfig.saveConfig();
                }
                ApplicationLoader.startPushService();
            });
        }
    }
}
