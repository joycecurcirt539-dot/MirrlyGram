/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.LaunchActivity;

public class NotificationsService extends Service {

    private static final String CHANNEL_ID = "mirrly_push_service";
    private static final int FOREGROUND_NOTIFICATION_ID = 7781;
    private static final int KEEPALIVE_ALARM_REQUEST_CODE = 7782;
    private static final long KEEPALIVE_INTERVAL_MS = 3 * 60 * 1000L;
    private static volatile PowerManager.WakeLock serviceWakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        ensureForeground();
        ApplicationLoader.postInitApplication();
        org.telegram.messenger.mirrly.MirrlyEngineController.ensureStarted();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureForeground();
        SharedPreferences preferences = MessagesController.getGlobalNotificationsSettings();
        if (!preferences.getBoolean("pushService", true)) {
            scheduleKeepAliveAlarm(this, false);
            try {
                stopForeground(true);
            } catch (Throwable ignore) {
            }
            stopSelf();
            return START_NOT_STICKY;
        }
        acquireShortWakeLock(8_000L);
        scheduleKeepAliveAlarm(this, true);
        ApplicationLoader.postInitApplication();
        org.telegram.messenger.mirrly.MirrlyEngineController.ensureStarted();
        org.telegram.messenger.mirrly.MirrlyEngineController.notifyNetworkState();
        boolean pushConnPref = preferences.getBoolean("pushConnection", true);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            try {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    ConnectionsManager cm = ConnectionsManager.getInstance(a);
                    if (pushConnPref || cm.isPushConnectionEnabled()) {
                        cm.setPushConnectionEnabled(true);
                        cm.checkConnection();
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
        return START_STICKY;
    }

    private void acquireShortWakeLock(long timeoutMs) {
        try {
            PowerManager.WakeLock wl = serviceWakeLock;
            if (wl == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mirrlygram:push_service");
                    wl.setReferenceCounted(false);
                    serviceWakeLock = wl;
                }
            }
            if (wl != null) {
                wl.acquire(timeoutMs);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static void scheduleKeepAliveAlarm(Context context, boolean enable) {
        if (context == null) {
            return;
        }
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) {
                return;
            }
            Intent alarmIntent = new Intent(context, NotificationsService.class);
            alarmIntent.setPackage(context.getPackageName());
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getService(context, KEEPALIVE_ALARM_REQUEST_CODE, alarmIntent, flags);
            if (!enable) {
                am.cancel(pendingIntent);
                return;
            }
            long triggerAt = SystemClock.elapsedRealtime() + KEEPALIVE_INTERVAL_MS;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
            } else {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        scheduleRestartIfEnabled();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            stopForeground(true);
        } catch (Throwable ignore) {
        }
        scheduleRestartIfEnabled();
    }

    private void scheduleRestartIfEnabled() {
        try {
            SharedPreferences preferences = MessagesController.getGlobalNotificationsSettings();
            if (preferences.getBoolean("pushService", true)) {
                Intent intent = new Intent("org.telegram.start");
                intent.setPackage(getPackageName());
                sendBroadcast(intent);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private String safeString(int resId, String fallback) {
        try {
            String value = LocaleController.getString(resId);
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        } catch (Throwable ignore) {
        }
        try {
            String value = getString(resId);
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        } catch (Throwable ignore) {
        }
        return fallback;
    }

    private void ensureForeground() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                    NotificationChannel channel = new NotificationChannel(
                            CHANNEL_ID,
                            safeString(R.string.MirrlyPushServiceChannel, "Background notifications"),
                            NotificationManager.IMPORTANCE_MIN
                    );
                    channel.setShowBadge(false);
                    channel.enableLights(false);
                    channel.enableVibration(false);
                    channel.setSound(null, null);
                    manager.createNotificationChannel(channel);
                }
            }

            Intent launchIntent = new Intent(this, LaunchActivity.class);
            launchIntent.setAction(Intent.ACTION_MAIN);
            launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent contentIntent = PendingIntent.getActivity(
                    this,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.notification)
                    .setContentTitle(safeString(R.string.AppName, "Mirrlygram"))
                    .setContentText(safeString(R.string.MirrlyPushServiceStatus, "Background connection for notifications is active"))
                    .setContentIntent(contentIntent)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .setSilent(true)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .build();

            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING);
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
