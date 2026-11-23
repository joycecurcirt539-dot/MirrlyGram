/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.telegram.tgnet.ConnectionsManager;

public class ScreenReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("screen off");
            }
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (a == UserConfig.selectedAccount || UserConfig.getInstance(a).isClientActivated()) {
                    ConnectionsManager.getInstance(a).setAppPaused(true, true);
                }
            }
            ApplicationLoader.isScreenOn = false;
            org.telegram.messenger.mirrly.MirrlyPowerTelemetry.onScreenStateChanged(false);
        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("screen on");
            }
            ApplicationLoader.isScreenOn = true;
            org.telegram.messenger.mirrly.MirrlyPowerTelemetry.onScreenStateChanged(true);
            org.telegram.messenger.mirrly.MirrlyEngineController.notifyScreenWake();
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (a == UserConfig.selectedAccount || UserConfig.getInstance(a).isClientActivated()) {
                    ConnectionsManager.getInstance(a).setAppPaused(false, true);
                }
            }
            if (MessagesController.getGlobalNotificationsSettings().getBoolean("pushService", true)) {
                ApplicationLoader.startPushService();
            }
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.screenStateChanged);
    }
}
