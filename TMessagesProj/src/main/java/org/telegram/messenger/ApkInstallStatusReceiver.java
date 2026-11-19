/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 */

package org.telegram.messenger;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

public class ApkInstallStatusReceiver extends BroadcastReceiver {

    public static final String ACTION_INSTALL_STATUS = "org.telegram.messenger.APK_INSTALL_STATUS";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || !ACTION_INSTALL_STATUS.equals(intent.getAction())) {
            return;
        }
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Integer.MIN_VALUE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmIntent == null) {
                return;
            }
            try {
                Activity activity = LaunchActivity.instance;
                if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                    activity.startActivity(confirmIntent);
                } else {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirmIntent);
                }
            } catch (Throwable t) {
                FileLog.e(t);
                showInstallFailed();
            }
            return;
        }

        AndroidUtilities.cleanupStagedApkFilesAsync();
        if (status != PackageInstaller.STATUS_SUCCESS && status != PackageInstaller.STATUS_FAILURE_ABORTED) {
            String statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            FileLog.e("ApkInstallStatusReceiver failed status=" + status + " message=" + statusMessage);
            showInstallFailed();
        }
    }

    private static void showInstallFailed() {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                String msg = LocaleController.getString(R.string.ApkInstallFailed);
                BulletinFactory.global().createSimpleBulletin(R.raw.error, msg).show(false);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }
}
