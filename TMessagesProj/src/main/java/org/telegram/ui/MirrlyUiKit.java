package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.mirrly.MirrlyConfig;
import org.telegram.messenger.mirrly.MirrlyEngineController;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FireworksOverlay;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.voip.CellFlickerDrawable;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;

/**
 * Shared native Telegram UI toolkit for Mirrlygram settings, about, and diagnostic surfaces.
 */
public final class MirrlyUiKit {

    private MirrlyUiKit() {
    }

    public static String formatClientVersion() {
        try {
            PackageInfo pInfo = ApplicationLoader.applicationContext
                    .getPackageManager()
                    .getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            int code = pInfo.versionCode / 10;
            return String.format(Locale.US, "v%s (%d)", pInfo.versionName, code);
        } catch (Exception e) {
            FileLog.e(e);
            return "";
        }
    }

    public static void copyTextWithBulletin(
            @Nullable BaseFragment fragment,
            @Nullable CharSequence text,
            String toastMessage,
            @Nullable BottomSheet sheet,
            @Nullable Theme.ResourcesProvider resourcesProvider
    ) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        if (AndroidUtilities.addToClipboard(text)) {
            BulletinFactory factory = sheet != null
                    ? BulletinFactory.of(sheet.getContainer(), resourcesProvider)
                    : (fragment != null ? BulletinFactory.of(fragment) : BulletinFactory.global());
            factory.createSimpleBulletin(R.raw.copy, toastMessage).show();
        }
    }

    public static void confirmDangerAction(
            @Nullable BaseFragment fragment,
            @Nullable Theme.ResourcesProvider resourcesProvider,
            CharSequence title,
            CharSequence message,
            CharSequence positiveLabel,
            Runnable onConfirm
    ) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity(), resourcesProvider);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(positiveLabel, (dialog, which) -> onConfirm.run());
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        fragment.showDialog(dialog);
        TextView button = (TextView) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
        }
    }

    public static ButtonWithCounterView createFlickerButton(
            Context context,
            Theme.ResourcesProvider resourcesProvider,
            boolean highlightFlicker
    ) {
        final CellFlickerDrawable flicker = highlightFlicker ? new CellFlickerDrawable(34, 76, 150) : null;
        if (flicker != null) {
            flicker.drawFrame = false;
            flicker.repeatProgress = 3.8f;
            flicker.animationSpeedScale = 0.9f;
        }
        return new ButtonWithCounterView(context, resourcesProvider) {
            private final Path clipBtnPath = new Path();

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                if (flicker != null) {
                    flicker.setParentWidth(w);
                    clipBtnPath.rewind();
                    AndroidUtilities.rectTmp.set(0, 0, w, h);
                    clipBtnPath.addRoundRect(AndroidUtilities.rectTmp, dp(24), dp(24), Path.Direction.CW);
                }
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (flicker != null && SharedConfig.animationsEnabled() && LiteMode.isEnabled(LiteMode.FLAG_PARTICLES)) {
                    canvas.save();
                    canvas.clipPath(clipBtnPath);
                    AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                    flicker.draw(canvas, AndroidUtilities.rectTmp, dp(24), this);
                    canvas.restore();
                }
            }
        }.setRound();
    }

    public static void showQuickEngineOptions(
            @Nullable BaseFragment fragment,
            @Nullable View anchor,
            @Nullable Drawable scrimBg,
            @Nullable Theme.ResourcesProvider resourcesProvider,
            @Nullable Runnable onActionComplete
    ) {
        if (fragment == null || anchor == null) {
            return;
        }
        boolean mtprotoSelected = MirrlyEngineController.isMtprotoSelected();
        ItemOptions options = ItemOptions.makeOptions(fragment, anchor);
        if (mtprotoSelected) {
            options.add(R.drawable.mirrly_seg_socks5, LocaleController.getString(R.string.MirrlyQuickSwitchToSocks), () -> {
                MirrlyEngineController.switchActiveTransport(false);
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.info, LocaleController.getString(R.string.MirrlyNetworkSocks5Activated)).show();
                if (onActionComplete != null) {
                    onActionComplete.run();
                }
            });
        } else {
            options.add(R.drawable.mirrly_seg_mtproto, LocaleController.getString(R.string.MirrlyQuickSwitchToMtproto), () -> {
                MirrlyEngineController.switchActiveTransport(true);
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.info, LocaleController.getString(R.string.MirrlyNetworkMtprotoActivated)).show();
                if (onActionComplete != null) {
                    onActionComplete.run();
                }
            });
        }
        options.add(R.drawable.msg_retry, LocaleController.getString(R.string.MirrlyNetworkReconnect), () -> {
            MirrlyEngineController.softReconnect();
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.ic_save_to_gallery, LocaleController.getString(R.string.MirrlyNetworkReconnectedToast)).show();
            if (onActionComplete != null) {
                onActionComplete.run();
            }
        });
        options.add(R.drawable.msg_stats, LocaleController.getString(R.string.MirrlyQuickOpenDiagnostics), () ->
                showEngineDiagnosticsSheet(fragment, resourcesProvider, onActionComplete)
        );
        options.add(R.drawable.msg_copy, LocaleController.getString(R.string.MirrlyDiagCopyReport), () ->
                copyTextWithBulletin(
                        fragment,
                        EngineSnapshot.capture().buildClipboardReport(),
                        LocaleController.getString(R.string.MirrlyDiagReportCopied),
                        null,
                        resourcesProvider
                )
        );
        if (scrimBg != null) {
            options.setScrimViewBackground(scrimBg);
        }
        options.show();
    }

    public static void showEngineDiagnosticsSheet(
            @Nullable BaseFragment fragment,
            @Nullable Theme.ResourcesProvider resourcesProvider,
            @Nullable Runnable onAfterReconnect
    ) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        Context context = fragment.getParentActivity();
        BottomSheet.Builder builder = new BottomSheet.Builder(context, false, resourcesProvider);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(10), dp(20), dp(18));

        FrameLayout headerBox = new FrameLayout(context);
        headerBox.setClipChildren(false);
        headerBox.setClipToPadding(false);

        final StarParticlesView particles = createHeaderParticles(context, resourcesProvider, false);
        headerBox.addView(particles, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 98, Gravity.CENTER));

        RLottieImageView iconView = new RLottieImageView(context);
        iconView.setAutoRepeat(true);
        iconView.setAnimation(R.raw.boosts, 72, 72);
        iconView.playAnimation();
        ScaleStateListAnimator.apply(iconView);
        iconView.setOnClickListener(v -> {
            AndroidUtilities.vibrate(iconView);
            iconView.playAnimation();
            particles.flingParticles(110f);
            iconView.animate().cancel();
            iconView.setScaleX(0.88f);
            iconView.setScaleY(0.88f);
            iconView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(400)
                    .setInterpolator(new OvershootInterpolator(2.2f))
                    .start();
        });
        headerBox.addView(iconView, LayoutHelper.createFrame(72, 72, Gravity.CENTER));
        root.addView(headerBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 98, 0, 0, 0, 2));

        TextView titleView = new TextView(context);
        titleView.setText(LocaleController.getString(R.string.MirrlyDiagTitle));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        root.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        LinearLayout metricsCard = new LinearLayout(context);
        metricsCard.setOrientation(LinearLayout.VERTICAL);
        metricsCard.setPadding(dp(16), dp(10), dp(16), dp(10));
        metricsCard.setBackground(Theme.createRoundRectDrawable(
                dp(14),
                Theme.getColor(Theme.key_dialogBackgroundGray, resourcesProvider)
        ));

        AnimatedTextView endpointVal = addDiagRow(metricsCard, context, resourcesProvider, LocaleController.getString(R.string.MirrlyDiagAddressLabel));
        AnimatedTextView uptimeVal = addDiagRow(metricsCard, context, resourcesProvider, LocaleController.getString(R.string.MirrlyDiagUptimeLabel));
        AnimatedTextView connVal = addDiagRow(metricsCard, context, resourcesProvider, LocaleController.getString(R.string.MirrlyDiagConnectionsLabel));
        AnimatedTextView errorsVal = addDiagRow(metricsCard, context, resourcesProvider, LocaleController.getString(R.string.MirrlyDiagErrorsLabel));
        AnimatedTextView trafficVal = addDiagRow(metricsCard, context, resourcesProvider, LocaleController.getString(R.string.MirrlyDiagTrafficLabel));

        root.addView(metricsCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        final BottomSheet[] sheetHolder = new BottomSheet[1];
        final Runnable refreshAction = new Runnable() {
            @Override
            public void run() {
                if (sheetHolder[0] == null || !sheetHolder[0].isShowing()) {
                    return;
                }
                bindDiagSnapshot(
                        EngineSnapshot.capture(),
                        true,
                        endpointVal,
                        uptimeVal,
                        connVal,
                        errorsVal,
                        trafficVal
                );
                AndroidUtilities.runOnUIThread(this, 1000);
            }
        };

        ButtonWithCounterView reconnectBtn = createFlickerButton(context, resourcesProvider, true);
        reconnectBtn.setText(LocaleController.getString(R.string.MirrlyNetworkReconnect), false);
        reconnectBtn.setOnClickListener(v -> {
            AndroidUtilities.vibrateCursor(reconnectBtn);
            MirrlyEngineController.softReconnect();
            particles.flingParticles(125f);
            bindDiagSnapshot(
                    EngineSnapshot.capture(),
                    true,
                    endpointVal,
                    uptimeVal,
                    connVal,
                    errorsVal,
                    trafficVal
            );
            BulletinFactory.of(sheetHolder[0].getContainer(), resourcesProvider)
                    .createSimpleBulletin(R.raw.ic_save_to_gallery, LocaleController.getString(R.string.MirrlyNetworkReconnectedToast))
                    .show();
            if (onAfterReconnect != null) {
                onAfterReconnect.run();
            }
        });
        root.addView(reconnectBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 0, 0, 8));

        ButtonWithCounterView copyBtn = new ButtonWithCounterView(context, resourcesProvider).setRound().setNeutral();
        copyBtn.setText(LocaleController.getString(R.string.MirrlyDiagCopyReport), false);
        copyBtn.setOnClickListener(v -> copyTextWithBulletin(
                fragment,
                EngineSnapshot.capture().buildClipboardReport(),
                LocaleController.getString(R.string.MirrlyDiagReportCopied),
                sheetHolder[0],
                resourcesProvider
        ));
        root.addView(copyBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 0, 0, 0));

        builder.setCustomView(root);
        BottomSheet sheet = builder.create();
        sheetHolder[0] = sheet;

        bindDiagSnapshot(
                EngineSnapshot.capture(),
                false,
                endpointVal,
                uptimeVal,
                connVal,
                errorsVal,
                trafficVal
        );
        sheet.setOnShowListener(d -> AndroidUtilities.runOnUIThread(refreshAction, 1000));
        sheet.setOnDismissListener(d -> AndroidUtilities.cancelRunOnUIThread(refreshAction));
        fragment.showDialog(sheet);
    }

    private static void bindDiagSnapshot(
            EngineSnapshot s,
            boolean animated,
            AnimatedTextView endpointVal,
            AnimatedTextView uptimeVal,
            AnimatedTextView connVal,
            AnimatedTextView errorsVal,
            AnimatedTextView trafficVal
    ) {
        endpointVal.setText(s.getDiagModeAndEndpoint(), animated);
        uptimeVal.setText(LocaleController.formatShortDuration((int) Math.min(Integer.MAX_VALUE, s.uptimeSecs)), animated);
        connVal.setText(LocaleController.formatString(R.string.MirrlyDiagConnRatio, s.activeConn, s.totalConn), animated);
        errorsVal.setText(
                (s.wsErrors == 0 && s.badConn == 0)
                        ? "0"
                        : LocaleController.formatString(R.string.MirrlyDiagErrorsDetail, s.wsErrors, s.badConn),
                animated
        );
        trafficVal.setText(s.getTrafficLine(), animated);
    }

    private static AnimatedTextView addDiagRow(
            LinearLayout card,
            Context context,
            @Nullable Theme.ResourcesProvider resourcesProvider,
            String label
    ) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        labelView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
        labelView.setSingleLine(true);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        labelView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

        AnimatedTextView valView = new AnimatedTextView(context, true, true, true);
        valView.setAnimationProperties(0.35f, 0, 240, CubicBezierInterpolator.EASE_OUT_QUINT);
        valView.setTextSize(dp(13.5f));
        valView.setTypeface(AndroidUtilities.bold());
        valView.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        valView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));

        if (LocaleController.isRTL) {
            row.addView(valView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 20, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
            row.addView(labelView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));
        } else {
            row.addView(labelView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
            row.addView(valView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 20, Gravity.CENTER_VERTICAL));
        }

        card.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return valView;
    }

    public static StarParticlesView createHeaderParticles(
            Context context,
            @Nullable Theme.ResourcesProvider resourcesProvider,
            boolean goldGlitter
    ) {
        return new StarParticlesView(context, 24) {
            @Override
            protected void configure() {
                drawable = new Drawable(24);
                drawable.resourcesProvider = resourcesProvider;
                drawable.colorKey = Theme.key_windowBackgroundWhiteBlueText4;
                drawable.useGradient = true;
                drawable.useBlur = true;
                drawable.useRotate = true;
                drawable.roundEffect = true;
                drawable.useScale = true;
                drawable.startFromCenter = true;
                drawable.checkBounds = true;
                drawable.isCircle = false;
                drawable.goldGlitter = goldGlitter;
                drawable.excludeRadius = dp(32);
                drawable.size1 = 12;
                drawable.size2 = 9;
                drawable.size3 = 6;
                drawable.speedScale = 0.7f;
                drawable.init();
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                if (drawable != null) {
                    int w = right - left;
                    int h = bottom - top;
                    drawable.rect.set(0, 0, w, h);
                    drawable.rect2.set(0, 0, w, h);
                    drawable.excludeRect.set(w / 2f - dp(32), h / 2f - dp(32), w / 2f + dp(32), h / 2f + dp(32));
                }
            }
        };
    }

    /**
     * Immutable telemetry snapshot collected from native mirrlyengine and Telegram proxy settings.
     */
    public static final class EngineSnapshot {
        public final boolean libraryLoaded;
        public final boolean proxyEnabled;
        public final boolean isMtproto;
        public final boolean isSocks5;
        public final boolean tlsEnabled;
        public final int activePort;
        public final long activeConn;
        public final long totalConn;
        public final long wsConn;
        public final long cfProxyConn;
        public final long badConn;
        public final long wsErrors;
        public final long poolHits;
        public final long poolMisses;
        public final long uptimeSecs;
        public final long bytesDown;
        public final long bytesUp;

        private EngineSnapshot(
                boolean libraryLoaded,
                boolean proxyEnabled,
                boolean isMtproto,
                boolean isSocks5,
                boolean tlsEnabled,
                int activePort,
                long activeConn,
                long totalConn,
                long wsConn,
                long cfProxyConn,
                long badConn,
                long wsErrors,
                long poolHits,
                long poolMisses,
                long uptimeSecs,
                long bytesDown,
                long bytesUp
        ) {
            this.libraryLoaded = libraryLoaded;
            this.proxyEnabled = proxyEnabled;
            this.isMtproto = isMtproto;
            this.isSocks5 = isSocks5;
            this.tlsEnabled = tlsEnabled;
            this.activePort = activePort;
            this.activeConn = activeConn;
            this.totalConn = totalConn;
            this.wsConn = wsConn;
            this.cfProxyConn = cfProxyConn;
            this.badConn = badConn;
            this.wsErrors = wsErrors;
            this.poolHits = poolHits;
            this.poolMisses = poolMisses;
            this.uptimeSecs = uptimeSecs;
            this.bytesDown = bytesDown;
            this.bytesUp = bytesUp;
        }

        public static EngineSnapshot capture() {
            MirrlyConfig.load();
            SharedConfig.loadProxyList();
            boolean loaded = MirrlyEngineController.isLibraryLoaded();
            long activeConn = 0;
            long totalConn = 0;
            long wsConn = 0;
            long cfProxyConn = 0;
            long badConn = 0;
            long wsErrors = 0;
            long poolHits = 0;
            long poolMisses = 0;
            long uptimeSecs = 0;
            long bytesDown = 0;
            long bytesUp = 0;

            if (loaded) {
                try {
                    JSONObject obj = new JSONObject(MirrlyEngineController.getStatsJson());
                    activeConn = Math.max(0, obj.optLong("connections_active", 0));
                    totalConn = Math.max(0, obj.optLong("connections_total", 0));
                    wsConn = Math.max(0, obj.optLong("connections_ws", 0));
                    cfProxyConn = Math.max(0, obj.optLong("connections_cfproxy", 0));
                    badConn = Math.max(0, obj.optLong("connections_bad", 0));
                    wsErrors = Math.max(0, obj.optLong("ws_errors", 0));
                    poolHits = Math.max(0, obj.optLong("pool_hits", 0));
                    poolMisses = Math.max(0, obj.optLong("pool_misses", 0));
                    uptimeSecs = Math.max(0, obj.optLong("uptime_secs", 0));
                    bytesDown = Math.max(0, obj.optLong("bytes_down", 0));
                    bytesUp = Math.max(0, obj.optLong("bytes_up", 0));
                } catch (Throwable ignore) {
                }
            }

            boolean enabled = SharedConfig.isProxyEnabled();
            boolean mtp = MirrlyEngineController.isMtprotoSelected();
            boolean socks = MirrlyEngineController.isSocks5Selected();
            int port = mtp ? MirrlyConfig.effectiveMtprotoPort : (socks ? MirrlyConfig.effectiveSocks5Port : 0);

            return new EngineSnapshot(
                    loaded, enabled, mtp, socks, MirrlyConfig.tlsEnabled, port,
                    activeConn, totalConn, wsConn, cfProxyConn, badConn, wsErrors,
                    poolHits, poolMisses, uptimeSecs, bytesDown, bytesUp
            );
        }

        public boolean isLocalOnline() {
            return libraryLoaded && proxyEnabled && (isMtproto || isSocks5);
        }

        public String getShortBadge() {
            if (!libraryLoaded || !proxyEnabled) {
                return LocaleController.getString(R.string.MirrlyAboutEngineOff);
            }
            if (isMtproto) {
                return tlsEnabled ? "MTProto • TLS" : "MTProto";
            }
            if (isSocks5) {
                return tlsEnabled ? "SOCKS5 • TLS" : "SOCKS5";
            }
            return LocaleController.getString(R.string.MirrlyAboutEngineExternal);
        }

        public String getHeroEngineLine() {
            if (!libraryLoaded || !proxyEnabled) {
                return "MirrlyEngine • " + LocaleController.getString(R.string.MirrlyAboutEngineOff);
            }
            if (isMtproto) {
                return tlsEnabled ? "MirrlyEngine • MTProto (TLS)" : "MirrlyEngine • MTProto";
            }
            if (isSocks5) {
                return tlsEnabled ? "MirrlyEngine • SOCKS5 (TLS)" : "MirrlyEngine • SOCKS5";
            }
            return "MirrlyEngine • " + LocaleController.getString(R.string.MirrlyAboutEngineExternal);
        }

        public String getEndpointString() {
            if (!libraryLoaded) {
                return LocaleController.getString(R.string.MirrlyEngineNotLoaded);
            }
            if (!proxyEnabled) {
                return LocaleController.getString(R.string.MirrlyEngineProxyDisabled);
            }
            if (activePort <= 0) {
                return LocaleController.getString(R.string.MirrlyEngineExternalProxy);
            }
            return MirrlyConfig.LOCALHOST + ":" + activePort;
        }

        public String getDiagModeAndEndpoint() {
            if (!isLocalOnline() || activePort <= 0) {
                return getEndpointString();
            }
            String mode = isMtproto ? "MTProto" : "SOCKS5";
            if (tlsEnabled) {
                mode += " (TLS)";
            }
            return mode + " • " + MirrlyConfig.LOCALHOST + ":" + activePort;
        }

        public String getTrafficLine() {
            return String.format(
                    Locale.US,
                    "↓ %s  ↑ %s",
                    AndroidUtilities.formatFileSize(bytesDown),
                    AndroidUtilities.formatFileSize(bytesUp)
            );
        }

        public String getTrafficBannerLine() {
            return String.format(
                    Locale.US,
                    "↓ %s • ↑ %s",
                    AndroidUtilities.formatFileSize(bytesDown),
                    AndroidUtilities.formatFileSize(bytesUp)
            );
        }

        public String getBannerSummaryLine() {
            if (!libraryLoaded) {
                return LocaleController.getString(R.string.MirrlyEngineNotLoaded);
            }
            if (!proxyEnabled) {
                return LocaleController.getString(R.string.MirrlyEngineProxyDisabled);
            }
            if (!isMtproto && !isSocks5) {
                return LocaleController.getString(R.string.MirrlyEngineExternalProxy);
            }
            return getTrafficBannerLine();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EngineSnapshot)) return false;
            EngineSnapshot that = (EngineSnapshot) o;
            return libraryLoaded == that.libraryLoaded
                    && proxyEnabled == that.proxyEnabled
                    && isMtproto == that.isMtproto
                    && isSocks5 == that.isSocks5
                    && tlsEnabled == that.tlsEnabled
                    && activePort == that.activePort
                    && activeConn == that.activeConn
                    && uptimeSecs == that.uptimeSecs
                    && bytesDown == that.bytesDown
                    && bytesUp == that.bytesUp;
        }

        @Override
        public int hashCode() {
            return Objects.hash(libraryLoaded, proxyEnabled, isMtproto, isSocks5, tlsEnabled, activePort, activeConn, uptimeSecs, bytesDown, bytesUp);
        }

        public String getCompactConnTrafficLine() {
            if (!isLocalOnline()) {
                return getHeroEngineLine();
            }
            return (isMtproto ? "MTProto" : "SOCKS5")
                    + (tlsEnabled ? " (TLS)" : "")
                    + " • "
                    + formatConnectionsCount(activeConn);
        }

        public String buildClipboardReport() {
            return "Mirrlygram " + formatClientVersion() + "\n"
                    + "Engine: " + getHeroEngineLine() + "\n"
                    + "Endpoint: " + getEndpointString() + "\n"
                    + "Uptime: " + LocaleController.formatShortDuration((int) Math.min(Integer.MAX_VALUE, uptimeSecs)) + "\n"
                    + "Connections: " + activeConn + " active / " + totalConn + " total\n"
                    + "Channels: WS=" + wsConn + ", CFProxy=" + cfProxyConn + "\n"
                    + "Pool: hits=" + poolHits + ", misses=" + poolMisses + "\n"
                    + "Errors: WS=" + wsErrors + ", dropped=" + badConn + "\n"
                    + "Traffic: " + getTrafficLine();
        }

        public static String formatConnectionsCount(long count) {
            int safeCount = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, count));
            return LocaleController.formatPluralString("MirrlyEngineConn", safeCount);
        }
    }

    /**
     * Specification payload for {@link HeroCardView}.
     */
    public static final class HeroSpec {
        public static final int MODE_BRAND = 0;
        public static final int MODE_BRAND_GLITTER = 1;
        public static final int MODE_LOTTIE = 2;

        public final int mode;
        public final int resId;
        public final boolean edgeToEdge;
        public final CharSequence title;
        public final CharSequence[] subtitles;
        public final CharSequence easterEggToast;

        public HeroSpec(
                int mode,
                int resId,
                boolean edgeToEdge,
                CharSequence title,
                CharSequence[] subtitles,
                CharSequence easterEggToast
        ) {
            this.mode = mode;
            this.resId = resId;
            this.edgeToEdge = edgeToEdge;
            this.title = title;
            this.subtitles = subtitles;
            this.easterEggToast = easterEggToast;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof HeroSpec)) return false;
            HeroSpec that = (HeroSpec) o;
            if (mode != that.mode || resId != that.resId || edgeToEdge != that.edgeToEdge) {
                return false;
            }
            if (!TextUtils.equals(title, that.title) || !TextUtils.equals(easterEggToast, that.easterEggToast)) {
                return false;
            }
            int len = subtitles != null ? subtitles.length : 0;
            int otherLen = that.subtitles != null ? that.subtitles.length : 0;
            if (len != otherLen) {
                return false;
            }
            for (int i = 0; i < len; i++) {
                if (!TextUtils.equals(subtitles[i], that.subtitles[i])) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mode, resId, edgeToEdge, title, Arrays.hashCode(subtitles), easterEggToast);
        }
    }

    /**
     * Floating Telegram-style hero header with ambient radial halo, StarParticlesView,
     * interactive 3D-tilting emblem (branded squircle with CellFlickerDrawable shimmer or RLottie),
     * SuperRipple + FireworksOverlay easter egg, and tappable AnimatedTextView subtitle pill.
     */
    public static class HeroCardView extends FrameLayout implements Theme.Colorable {

        private static final int TOP_BLEED_DP = 96;

        private final Theme.ResourcesProvider resourcesProvider;
        private final WeakReference<BaseFragment> hostRef;
        private final StarParticlesView particlesView;
        private final FrameLayout emblemHolder;
        private final BrandEmblemView brandEmblemView;
        private final RLottieImageView lottieView;
        private final AnimatedTextView titleView;
        private final AnimatedTextView subtitleView;

        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private RadialGradient glowGradient;
        private int lastGlowColor = -1;
        private float lastGlowCx = -1f;
        private float lastGlowCy = -1f;
        private float parallaxX;
        private float parallaxY;

        private int lastLottieRes = -1;
        private CharSequence[] subtitleCycle;
        private int subtitleIndex = 0;
        private CharSequence easterEggToast = "Mirrlygram • GNU GPLv3";

        private int rapidTapCount = 0;
        private long lastTapTimestamp = 0L;

        public HeroCardView(Context context, Theme.ResourcesProvider resourcesProvider, @Nullable BaseFragment host) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            this.hostRef = new WeakReference<>(host);
            setWillNotDraw(false);
            setClipChildren(false);
            setClipToPadding(false);
            RecyclerView.LayoutParams rootLp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rootLp.topMargin = -dp(TOP_BLEED_DP);
            setLayoutParams(rootLp);

            final int particleCount = SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW ? 28 : 56;
            particlesView = new StarParticlesView(context, particleCount) {
                private final Paint edgeFadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private int lastFadeHeight = -1;

                @Override
                protected void configure() {
                    int count = SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW ? 28 : 56;
                    drawable = new Drawable(count);
                    drawable.resourcesProvider = resourcesProvider;
                    drawable.colorKey = Theme.key_windowBackgroundWhiteBlueText4;
                    drawable.useGradient = true;
                    drawable.useBlur = true;
                    drawable.useRotate = true;
                    drawable.roundEffect = true;
                    drawable.useScale = true;
                    drawable.startFromCenter = true;
                    drawable.checkBounds = true;
                    drawable.isCircle = false;
                    drawable.excludeRadius = dp(46);
                    drawable.size1 = 14;
                    drawable.size2 = 10;
                    drawable.size3 = 7;
                    drawable.speedScale = 0.7f;
                    drawable.init();
                }

                @Override
                protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                    super.onSizeChanged(w, h, oldw, oldh);
                    if (h > 0 && h != lastFadeHeight) {
                        lastFadeHeight = h;
                        float topEnd = Math.min(0.28f, dp(36) / (float) h);
                        float botStart = Math.max(0.76f, (h - dp(26)) / (float) h);
                        edgeFadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
                        edgeFadePaint.setShader(new LinearGradient(
                                0, 0, 0, h,
                                new int[]{Color.TRANSPARENT, Color.BLACK, Color.BLACK, Color.TRANSPARENT},
                                new float[]{0f, topEnd, botStart, 1f},
                                Shader.TileMode.CLAMP
                        ));
                    }
                }

                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    super.onLayout(changed, left, top, right, bottom);
                    if (drawable != null) {
                        int w = right - left;
                        int h = bottom - top;
                        drawable.rect.set(0, dp(12), w, h - dp(8));
                        drawable.rect2.set(0, 0, w, h);
                        float cx = w / 2f;
                        float cy = dp(TOP_BLEED_DP + 64);
                        drawable.centerOffsetX = cx - drawable.rect.centerX();
                        drawable.centerOffsetY = cy - drawable.rect.centerY();
                        drawable.excludeRect.set(cx - dp(44), cy - dp(44), cx + dp(44), cy + dp(44));
                    }
                }

                @Override
                protected void onDraw(Canvas canvas) {
                    int w = getWidth();
                    int h = getHeight();
                    if (w <= 0 || h <= 0) {
                        return;
                    }
                    int save = canvas.saveLayer(0, 0, w, h, null);
                    super.onDraw(canvas);
                    if (edgeFadePaint.getShader() != null) {
                        canvas.drawRect(0, 0, w, h, edgeFadePaint);
                    }
                    canvas.restoreToCount(save);
                }
            };
            addView(particlesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 178 + TOP_BLEED_DP, Gravity.CENTER_HORIZONTAL | Gravity.TOP));

            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER_HORIZONTAL);
            content.setClipChildren(false);
            content.setClipToPadding(false);
            content.setPadding(dp(24), dp(TOP_BLEED_DP + 18), dp(24), dp(12));

            emblemHolder = new FrameLayout(context);
            emblemHolder.setClipChildren(false);
            emblemHolder.setCameraDistance(dp(420) * AndroidUtilities.density);

            brandEmblemView = new BrandEmblemView(context, resourcesProvider);
            emblemHolder.addView(brandEmblemView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            lottieView = new RLottieImageView(context);
            lottieView.setAutoRepeat(true);
            emblemHolder.addView(lottieView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            setupInteractiveEmblemTouch();
            content.addView(emblemHolder, LayoutHelper.createLinear(92, 92, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));

            titleView = new AnimatedTextView(context, true, true, false);
            titleView.setAnimationProperties(0.35f, 0, 280, CubicBezierInterpolator.EASE_OUT_QUINT);
            titleView.setTextSize(dp(22));
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setGravity(Gravity.CENTER);
            content.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 28, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6));

            subtitleView = new AnimatedTextView(context, true, true, false);
            subtitleView.adaptWidth = false;
            subtitleView.setAllowCancel(true);
            subtitleView.setAnimationProperties(0.35f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            subtitleView.setTextSize(dp(13));
            subtitleView.setGravity(Gravity.CENTER);
            subtitleView.setPadding(dp(12), dp(4), dp(12), dp(4));
            ScaleStateListAnimator.apply(subtitleView, 0.04f, 1.0f);

            subtitleView.setOnClickListener(v -> cycleSubtitle());
            subtitleView.setOnLongClickListener(v -> {
                BaseFragment activity = hostRef.get();
                if (activity != null && subtitleCycle != null && subtitleIndex >= 0 && subtitleIndex < subtitleCycle.length) {
                    AndroidUtilities.vibrateCursor(subtitleView);
                    copyTextWithBulletin(
                            activity,
                            subtitleCycle[subtitleIndex],
                            LocaleController.getString(R.string.MirrlyAboutTextCopied),
                            null,
                            resourcesProvider
                    );
                    return true;
                }
                return false;
            });

            content.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 28, Gravity.CENTER_HORIZONTAL));

            addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            updateColors();
        }

        private void setupInteractiveEmblemTouch() {
            final int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            final float[] downPos = new float[2];
            final boolean[] movedTooFar = new boolean[1];

            emblemHolder.setOnTouchListener((v, event) -> {
                float w = Math.max(1f, v.getWidth());
                float h = Math.max(1f, v.getHeight());
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downPos[0] = event.getX();
                        downPos[1] = event.getY();
                        movedTooFar[0] = false;
                        v.animate().cancel();
                        applyTilt(event.getX(), event.getY(), w, h, true);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getX() - downPos[0];
                        float dy = event.getY() - downPos[1];
                        if (dx * dx + dy * dy > touchSlop * touchSlop) {
                            movedTooFar[0] = true;
                        }
                        applyTilt(event.getX(), event.getY(), w, h, false);
                        return true;
                    case MotionEvent.ACTION_UP:
                        releaseTilt();
                        if (!movedTooFar[0]) {
                            triggerEmblemTap();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        releaseTilt();
                        return true;
                }
                return false;
            });
        }

        private void applyTilt(float x, float y, float w, float h, boolean animatePress) {
            float nx = Math.max(-1f, Math.min(1f, (x - w / 2f) / (w / 2f)));
            float ny = Math.max(-1f, Math.min(1f, (y - h / 2f) / (h / 2f)));
            float rotY = nx * 16f;
            float rotX = -ny * 16f;
            if (animatePress) {
                emblemHolder.animate()
                        .scaleX(0.93f)
                        .scaleY(0.93f)
                        .rotationX(rotX)
                        .rotationY(rotY)
                        .setDuration(120)
                        .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                        .start();
            } else {
                emblemHolder.setRotationX(rotX);
                emblemHolder.setRotationY(rotY);
            }
        }

        private void releaseTilt() {
            emblemHolder.animate().cancel();
            emblemHolder.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .rotationX(0f)
                    .rotationY(0f)
                    .setDuration(460)
                    .setInterpolator(new OvershootInterpolator(2.2f))
                    .start();
        }

        private void triggerEmblemTap() {
            AndroidUtilities.vibrate(emblemHolder);
            particlesView.flingParticles(110f);
            if (lastLottieRes != 0) {
                lottieView.playAnimation();
            }

            int[] loc = new int[2];
            emblemHolder.getLocationInWindow(loc);
            float cx = loc[0] + emblemHolder.getWidth() / 2f;
            float cy = loc[1] + emblemHolder.getHeight() / 2f;

            long now = System.currentTimeMillis();
            if (now - lastTapTimestamp <= 1800L) {
                rapidTapCount++;
            } else {
                rapidTapCount = 1;
            }
            lastTapTimestamp = now;

            if (rapidTapCount >= 5) {
                rapidTapCount = 0;
                AndroidUtilities.vibrateCursor(emblemHolder);
                particlesView.flingParticles(180f);
                LaunchActivity.makeRipple(cx, cy, 1.35f);
                if (LaunchActivity.instance != null) {
                    FireworksOverlay fireworks = LaunchActivity.instance.getFireworksOverlay();
                    if (fireworks != null) {
                        fireworks.start(true);
                    }
                }
                cycleSubtitle();
                BaseFragment host = hostRef.get();
                if (host != null && !TextUtils.isEmpty(easterEggToast)) {
                    BulletinFactory.of(host)
                            .createSimpleBulletin(R.raw.boosts, easterEggToast)
                            .show();
                }
            } else {
                LaunchActivity.makeRipple(cx, cy, 0.85f);
            }
        }

        private void cycleSubtitle() {
            if (subtitleCycle == null || subtitleCycle.length <= 1) {
                return;
            }
            AndroidUtilities.vibrateCursor(subtitleView);
            subtitleIndex = (subtitleIndex + 1) % subtitleCycle.length;
            subtitleView.setText(subtitleCycle[subtitleIndex], true);
        }

        public void refreshDynamicSubtitles(CharSequence[] subtitles, boolean animated) {
            subtitleCycle = subtitles;
            if (subtitleCycle == null || subtitleCycle.length == 0) {
                return;
            }
            if (subtitleIndex >= subtitleCycle.length) {
                subtitleIndex = 0;
            }
            CharSequence next = subtitleCycle[subtitleIndex];
            if (!TextUtils.equals(subtitleView.getText(), next)) {
                subtitleView.setText(next, animated);
            }
        }

        public void onListScrolled(int dy) {
            if (LiteMode.isEnabled(LiteMode.FLAG_PARTICLES)) {
                particlesView.flingParticles(Math.min(170f, Math.abs(dy) * 2.2f));
            }
        }

        public void setParallaxOffset(float offsetX, float offsetY) {
            this.parallaxX = offsetX;
            this.parallaxY = offsetY;
            particlesView.setTranslationX(offsetX);
            particlesView.setTranslationY(offsetY);
            invalidate();
        }

        public void bind(HeroSpec spec) {
            boolean useBrand = spec.mode != HeroSpec.MODE_LOTTIE;
            boolean useGoldGlitter = spec.mode == HeroSpec.MODE_BRAND_GLITTER;
            this.easterEggToast = spec.easterEggToast;

            brandEmblemView.setVisibility(useBrand ? View.VISIBLE : View.GONE);
            lottieView.setVisibility(useBrand ? View.GONE : View.VISIBLE);
            if (useBrand) {
                int drawRes = spec.resId != 0 ? spec.resId : R.mipmap.mirrly_icon_foreground;
                brandEmblemView.setBrandDrawable(drawRes, spec.edgeToEdge);
                lastLottieRes = 0;
            } else if (lastLottieRes != spec.resId) {
                lottieView.setAnimation(spec.resId, 92, 92);
                lottieView.playAnimation();
                lastLottieRes = spec.resId;
            }

            if (particlesView.drawable != null && particlesView.drawable.goldGlitter != useGoldGlitter) {
                particlesView.drawable.goldGlitter = useGoldGlitter;
                particlesView.drawable.resetPositions();
            }

            if (!TextUtils.equals(titleView.getText(), spec.title)) {
                titleView.setText(spec.title, false);
            }
            refreshDynamicSubtitles(spec.subtitles, isAttachedToWindow());
            if (subtitleCycle != null) {
                boolean interactive = subtitleCycle.length > 1;
                subtitleView.setClickable(interactive);
                subtitleView.setLongClickable(interactive);
            }
            updateColors();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            ViewGroup.LayoutParams lp = getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams && ((ViewGroup.MarginLayoutParams) lp).topMargin != -dp(TOP_BLEED_DP)) {
                ((ViewGroup.MarginLayoutParams) lp).topMargin = -dp(TOP_BLEED_DP);
                requestLayout();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f + parallaxX * 0.35f;
            float cy = dp(TOP_BLEED_DP + 64) + parallaxY * 0.35f;
            int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);
            boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
            int glowColor = ColorUtils.setAlphaComponent(accent, isDark ? 0x36 : 0x22);
            float radius = dp(116);
            if (glowGradient == null || glowColor != lastGlowColor || Math.abs(cx - lastGlowCx) > 0.5f || Math.abs(cy - lastGlowCy) > 0.5f) {
                lastGlowColor = glowColor;
                lastGlowCx = cx;
                lastGlowCy = cy;
                glowGradient = new RadialGradient(
                        cx,
                        cy,
                        radius,
                        new int[]{glowColor, ColorUtils.setAlphaComponent(accent, isDark ? 0x12 : 0x0A), Color.TRANSPARENT},
                        new float[]{0f, 0.55f, 1f},
                        Shader.TileMode.CLAMP
                );
                glowPaint.setShader(glowGradient);
            }
            canvas.drawCircle(cx, cy, radius, glowPaint);
        }

        @Override
        public void updateColors() {
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            int accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            subtitleView.setSizeableBackground(Theme.createRoundRectDrawable(
                    dp(14),
                    ColorUtils.setAlphaComponent(accentColor, 0x16)
            ));
            brandEmblemView.updateColors();
            particlesView.updateColors();
            invalidate();
        }

        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_windowBackgroundWhiteBlackText);
            colors.add(Theme.key_windowBackgroundWhiteGrayText);
            colors.add(Theme.key_windowBackgroundWhiteBlueText4);
            colors.add(Theme.key_windowBackgroundWhite);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    heightMeasureSpec
            );
        }

        public interface HostProvider {
            @Nullable BaseFragment getMirrlyHeroHost();
        }

        public static class Factory extends UItem.UItemFactory<HeroCardView> {
            static {
                setup(new Factory());
            }

            @Override
            public HeroCardView createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                BaseFragment host = listView instanceof HostProvider ? ((HostProvider) listView).getMirrlyHeroHost() : null;
                return new HeroCardView(context, resourcesProvider, host);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (item.object instanceof HeroSpec) {
                    ((HeroCardView) view).bind((HeroSpec) item.object);
                }
            }

            @Override
            public boolean isShadow() {
                return true;
            }

            @Override
            public boolean isClickable() {
                return false;
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                if (a == null || b == null || a.id != b.id || a.viewType != b.viewType) {
                    return false;
                }
                return Objects.equals(a.object, b.object);
            }

            public static UItem of(int id, HeroSpec spec) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = spec;
                return item;
            }
        }
    }

    /**
     * Branded Mirrlygram squircle badge displaying custom artwork with theme-reactive surface,
     * crisp outer border drawn post-clip, and native CellFlickerDrawable shimmer.
     */
    public static class BrandEmblemView extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF cardRect = new RectF();
        private final Path clipPath = new Path();
        private final CellFlickerDrawable flickerDrawable;
        private final ImageView iconView;
        private int lastIconRes;
        private int lastW;
        private int lastH;
        private int lastTopColor;
        private int lastBottomColor;

        public BrandEmblemView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1));

            flickerDrawable = new CellFlickerDrawable(36, 92, 130);
            flickerDrawable.drawFrame = true;
            flickerDrawable.frameInside = true;
            flickerDrawable.setStrokeWidth(dp(1.2f));
            flickerDrawable.repeatProgress = 3.6f;
            flickerDrawable.animationSpeedScale = 0.85f;

            iconView = new ImageView(context);
            setBrandDrawable(R.mipmap.mirrly_icon_foreground, false);
            addView(iconView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            updateColors();
        }

        public void setBrandDrawable(@DrawableRes int resId, boolean edgeToEdge) {
            if (lastIconRes != resId) {
                lastIconRes = resId;
                iconView.setImageResource(resId);
            }
            if (edgeToEdge) {
                iconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iconView.setScaleX(1f);
                iconView.setScaleY(1f);
            } else {
                iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iconView.setScaleX(1.26f);
                iconView.setScaleY(1.26f);
            }
        }

        @Override
        public void updateColors() {
            boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
            int cardBase = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
            int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);
            int topColor = isDark
                    ? ColorUtils.blendARGB(cardBase, accent, 0.16f)
                    : ColorUtils.blendARGB(cardBase, accent, 0.12f);
            int bottomColor = isDark
                    ? ColorUtils.blendARGB(cardBase, Color.BLACK, 0.28f)
                    : ColorUtils.blendARGB(cardBase, accent, 0.04f);

            if (topColor != lastTopColor || bottomColor != lastBottomColor || getWidth() != lastW || getHeight() != lastH) {
                lastTopColor = topColor;
                lastBottomColor = bottomColor;
                lastW = getWidth();
                lastH = getHeight();
                bgPaint.setShader(new LinearGradient(0, 0, 0, Math.max(1, lastH), topColor, bottomColor, Shader.TileMode.CLAMP));
            }
            strokePaint.setColor(ColorUtils.setAlphaComponent(accent, isDark ? 0x45 : 0x33));
            flickerDrawable.setColors(isDark ? Color.WHITE : accent, isDark ? 34 : 26, isDark ? 88 : 64);
            invalidate();
        }

        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_windowBackgroundWhite);
            colors.add(Theme.key_windowBackgroundWhiteBlueText4);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            lastW = w;
            lastH = h;
            flickerDrawable.setParentWidth(w);
            bgPaint.setShader(new LinearGradient(0, 0, 0, Math.max(1, h), lastTopColor, lastBottomColor, Shader.TileMode.CLAMP));
            cardRect.set(dp(1), dp(1), w - dp(1), h - dp(1));
            clipPath.rewind();
            clipPath.addRoundRect(cardRect, dp(26), dp(26), Path.Direction.CW);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float radius = dp(26);
            cardRect.set(dp(1), dp(1), getWidth() - dp(1), getHeight() - dp(1));
            canvas.drawRoundRect(cardRect, radius, radius, bgPaint);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            float radius = dp(26);
            cardRect.set(dp(1), dp(1), getWidth() - dp(1), getHeight() - dp(1));
            canvas.save();
            canvas.clipPath(clipPath);
            super.dispatchDraw(canvas);
            if (SharedConfig.animationsEnabled() && LiteMode.isEnabled(LiteMode.FLAG_PARTICLES)) {
                flickerDrawable.draw(canvas, cardRect, radius, this);
            }
            canvas.restore();
            canvas.drawRoundRect(cardRect, radius, radius, strokePaint);
        }
    }

    /**
     * Compact centered live telemetry banner with pulsing status dot, AnimatedTextView odometer,
     * and semi-transparent prompt subtitle ("Нажми на меня").
     */
    public static class TelemetryBannerCell extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final StatusPulseDot dotView;
        private final AnimatedTextView statusLineView;
        private final TextView hintView;

        public TelemetryBannerCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setPadding(dp(16), dp(11), dp(16), dp(11));
            ScaleStateListAnimator.apply(this, 0.02f, 1.0f);

            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setGravity(Gravity.CENTER_HORIZONTAL);

            LinearLayout topRow = new LinearLayout(context);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER);

            dotView = new StatusPulseDot(context);
            topRow.addView(dotView, LayoutHelper.createLinear(14, 14, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));

            statusLineView = new AnimatedTextView(context, true, true, false);
            statusLineView.adaptWidth = true;
            statusLineView.setAllowCancel(true);
            statusLineView.setAnimationProperties(0.35f, 0, 260, CubicBezierInterpolator.EASE_OUT_QUINT);
            statusLineView.setTextSize(dp(14));
            statusLineView.setTypeface(AndroidUtilities.bold());
            statusLineView.setGravity(Gravity.CENTER);
            topRow.addView(statusLineView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 20, Gravity.CENTER_VERTICAL));

            container.addView(topRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

            hintView = new TextView(context);
            hintView.setText(LocaleController.getString(R.string.MirrlySettingsTelemetryHint));
            hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            hintView.setGravity(Gravity.CENTER);
            hintView.setSingleLine(true);
            container.addView(hintView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 2, 0, 0));

            addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            updateColors();
        }

        public void updateSnapshot(EngineSnapshot snapshot, boolean animated) {
            if (snapshot == null) {
                return;
            }
            int dotColorKey;
            boolean shouldPulse = false;
            if (!snapshot.libraryLoaded) {
                dotColorKey = Theme.key_text_RedRegular;
            } else if (!snapshot.proxyEnabled) {
                dotColorKey = Theme.key_windowBackgroundWhiteGrayText;
            } else if (!snapshot.isMtproto && !snapshot.isSocks5) {
                dotColorKey = Theme.key_windowBackgroundWhiteGrayText;
            } else {
                dotColorKey = Theme.key_color_green;
                shouldPulse = true;
            }
            int dotColor = Theme.getColor(dotColorKey, resourcesProvider);
            dotView.setState(dotColor, shouldPulse);

            String line = snapshot.getBannerSummaryLine();
            if (!TextUtils.equals(statusLineView.getText(), line)) {
                statusLineView.setText(line, animated);
            }
        }

        @Override
        public void updateColors() {
            statusLineView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            int gray = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
            hintView.setTextColor(ColorUtils.setAlphaComponent(gray, 0x8F));
            updateSnapshot(EngineSnapshot.capture(), false);
        }

        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_windowBackgroundWhiteBlackText);
            colors.add(Theme.key_windowBackgroundWhiteGrayText);
            colors.add(Theme.key_color_green);
            colors.add(Theme.key_text_RedRegular);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    heightMeasureSpec
            );
        }

        private static class StatusPulseDot extends View {
            private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private ValueAnimator pulseAnimator;
            private float pulsePhase = 0f;
            private boolean pulsing = false;

            StatusPulseDot(Context context) {
                super(context);
            }

            void setState(int color, boolean pulse) {
                corePaint.setColor(color);
                haloPaint.setColor(color);
                if (this.pulsing != pulse) {
                    this.pulsing = pulse;
                    updateAnimatorState();
                }
                invalidate();
            }

            private void updateAnimatorState() {
                boolean allowAnim = pulsing
                        && isAttachedToWindow()
                        && SharedConfig.animationsEnabled()
                        && LiteMode.isEnabled(LiteMode.FLAG_PARTICLES);
                if (allowAnim) {
                    if (pulseAnimator == null) {
                        pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
                        pulseAnimator.setDuration(1800);
                        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
                        pulseAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                        pulseAnimator.addUpdateListener(a -> {
                            pulsePhase = (float) a.getAnimatedValue();
                            invalidate();
                        });
                    }
                    if (!pulseAnimator.isStarted()) {
                        pulseAnimator.start();
                    }
                } else {
                    if (pulseAnimator != null) {
                        pulseAnimator.cancel();
                        pulseAnimator = null;
                    }
                    pulsePhase = 0f;
                }
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                updateAnimatorState();
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                if (pulseAnimator != null) {
                    pulseAnimator.cancel();
                    pulseAnimator = null;
                }
            }

            @Override
            protected void onDraw(Canvas canvas) {
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f;
                if (pulsing && SharedConfig.animationsEnabled()) {
                    float haloRadius = dp(3.5f) + dp(3.5f) * pulsePhase;
                    int alpha = (int) ((1f - pulsePhase) * 0x66);
                    haloPaint.setAlpha(alpha);
                    canvas.drawCircle(cx, cy, haloRadius, haloPaint);
                }
                canvas.drawCircle(cx, cy, dp(3.5f), corePaint);
            }
        }

        public static class Factory extends UItem.UItemFactory<TelemetryBannerCell> {
            static {
                setup(new Factory());
            }

            @Override
            public TelemetryBannerCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new TelemetryBannerCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                TelemetryBannerCell cell = (TelemetryBannerCell) view;
                EngineSnapshot snapshot = item.object instanceof EngineSnapshot ? (EngineSnapshot) item.object : EngineSnapshot.capture();
                cell.updateSnapshot(snapshot, view.isAttachedToWindow());
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                if (a == null || b == null || a.id != b.id || a.viewType != b.viewType) {
                    return false;
                }
                return Objects.equals(a.object, b.object);
            }

            public static UItem of(int id, EngineSnapshot snapshot) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = snapshot;
                return item;
            }
        }
    }

    /**
     * Support banner row extending SettingsActivity.SettingCell with a subtle CellFlickerDrawable sweep.
     */
    public static class SupportBannerCell extends SettingsActivity.SettingCell {
        private final CellFlickerDrawable rowFlicker;
        private final Theme.ResourcesProvider resProvider;

        public SupportBannerCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);
            this.resProvider = resourcesProvider;
            this.rowFlicker = new CellFlickerDrawable(28, 72, 160);
            this.rowFlicker.drawFrame = false;
            this.rowFlicker.repeatProgress = 4.2f;
            this.rowFlicker.animationSpeedScale = 0.75f;
            ScaleStateListAnimator.apply(this, 0.015f, 1.0f);
            updateFlickerColor();
        }

        private void updateFlickerColor() {
            if (rowFlicker == null) {
                return;
            }
            boolean isDark = resProvider != null ? resProvider.isDark() : Theme.isCurrentThemeDark();
            int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resProvider);
            rowFlicker.setColors(isDark ? Color.WHITE : accent, isDark ? 14 : 10, isDark ? 28 : 20);
        }

        @Override
        public void updateColors() {
            super.updateColors();
            updateFlickerColor();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (rowFlicker != null) {
                rowFlicker.setParentWidth(w);
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (rowFlicker != null && SharedConfig.animationsEnabled() && LiteMode.isEnabled(LiteMode.FLAG_PARTICLES)) {
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                rowFlicker.draw(canvas, AndroidUtilities.rectTmp, 0, this);
            }
        }

        public static class Factory extends UItem.UItemFactory<SupportBannerCell> {
            static {
                setup(new Factory());
            }

            @Override
            public SupportBannerCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new SupportBannerCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                int iconColorTop = (int) item.longValue;
                int iconColorBottom = (int) (item.longValue >>> 32);
                ((SupportBannerCell) view).set(
                        iconColorTop, iconColorBottom, item.iconResId,
                        item.text, item.subtext, item.textValue
                );
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return a != null && b != null
                        && a.id == b.id
                        && a.viewType == b.viewType
                        && a.iconResId == b.iconResId
                        && a.longValue == b.longValue
                        && TextUtils.equals(a.text, b.text)
                        && TextUtils.equals(a.subtext, b.subtext)
                        && TextUtils.equals(a.textValue, b.textValue);
            }

            public static UItem of(
                    int id,
                    int iconColorTop,
                    int iconColorBottom,
                    @DrawableRes int icon,
                    CharSequence title,
                    CharSequence subtitle
            ) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.iconResId = icon;
                item.text = title;
                item.subtext = subtitle;
                item.longValue = ((long) iconColorBottom << 32) | (iconColorTop & 0xFFFFFFFFL);
                return item;
            }
        }
    }

    /**
     * Native TextCell switch row with pixel-aligned 24dp left icon and smooth switch animation.
     */
    public static class IconSwitchCell extends TextCell {
        int boundItemId = Integer.MIN_VALUE;

        public IconSwitchCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, 20, false, true, resourcesProvider);
            textView.setEllipsizeByGradient(true);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int leftInset = imageView.getVisibility() == VISIBLE ? dp(offsetFromImage) : dp(leftPadding);
            int rightReserve = dp(37 + 22 + 16);
            int maxTextWidth = Math.max(0, getMeasuredWidth() - leftInset - rightReserve);
            if (textView.getMeasuredWidth() > maxTextWidth) {
                textView.measure(
                        MeasureSpec.makeMeasureSpec(maxTextWidth, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(dp(20), MeasureSpec.EXACTLY)
                );
            }
        }

        public static class Factory extends UItem.UItemFactory<IconSwitchCell> {
            static {
                setup(new Factory());
            }

            @Override
            public IconSwitchCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new IconSwitchCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                IconSwitchCell cell = (IconSwitchCell) view;
                cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                boolean sameItem = cell.boundItemId == item.id;
                boolean prevChecked = cell.isChecked();
                if (sameItem && prevChecked != item.checked) {
                    cell.setTextAndCheckAndIcon(item.text, prevChecked, item.iconResId, divider);
                    cell.setChecked(item.checked);
                } else {
                    cell.setTextAndCheckAndIcon(item.text, item.checked, item.iconResId, divider);
                }
                cell.setEnabled(item.enabled);
                cell.boundItemId = item.id;
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return a != null && b != null
                        && a.id == b.id
                        && a.viewType == b.viewType
                        && a.iconResId == b.iconResId
                        && a.checked == b.checked
                        && a.enabled == b.enabled
                        && TextUtils.equals(a.text, b.text);
            }

            public static UItem of(int id, @DrawableRes int iconResId, CharSequence title, boolean checked) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.iconResId = iconResId;
                item.text = title;
                item.checked = checked;
                item.enabled = true;
                return item;
            }
        }
    }

    /**
     * Spec payload for {@link SegmentedCardCell}.
     */
    public static final class SegmentedSpec {
        @DrawableRes public final int iconLeftRes;
        public final CharSequence labelLeft;
        @DrawableRes public final int iconRightRes;
        public final CharSequence labelRight;
        public final int selectedIndex;
        public final Utilities.Callback<Integer> onSelected;

        public SegmentedSpec(
                @DrawableRes int iconLeftRes,
                CharSequence labelLeft,
                @DrawableRes int iconRightRes,
                CharSequence labelRight,
                int selectedIndex,
                Utilities.Callback<Integer> onSelected
        ) {
            this.iconLeftRes = iconLeftRes;
            this.labelLeft = labelLeft;
            this.iconRightRes = iconRightRes;
            this.labelRight = labelRight;
            this.selectedIndex = selectedIndex == 1 ? 1 : 0;
            this.onSelected = onSelected;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SegmentedSpec)) return false;
            SegmentedSpec that = (SegmentedSpec) o;
            return iconLeftRes == that.iconLeftRes
                    && iconRightRes == that.iconRightRes
                    && selectedIndex == that.selectedIndex
                    && TextUtils.equals(labelLeft, that.labelLeft)
                    && TextUtils.equals(labelRight, that.labelRight);
        }

        @Override
        public int hashCode() {
            return Objects.hash(iconLeftRes, labelLeft, iconRightRes, labelRight, selectedIndex);
        }
    }

    /**
     * Telegram-styled 2-segment transport selector with aligned icons, AnimatedFloat sliding pill,
     * and dynamic Theme color interpolation.
     */
    public static class SegmentedCardCell extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint trackBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF trackRect = new RectF();
        private final RectF thumbRect = new RectF();
        private final Path clipThumb = new Path();
        private final CellFlickerDrawable thumbFlicker;

        private final AnimatedFloat thumbProgress = new AnimatedFloat(this, 0, 280, CubicBezierInterpolator.EASE_OUT_QUINT);

        private final LinearLayout leftButton;
        private final ImageView leftIcon;
        private final TextView leftLabel;

        private final LinearLayout rightButton;
        private final ImageView rightIcon;
        private final TextView rightLabel;

        private int selectedIndex = 0;
        private int lastLeftIconRes;
        private int lastRightIconRes;
        private int lastLeftTint;
        private int lastRightTint;
        @Nullable private Utilities.Callback<Integer> onSelected;

        public SegmentedCardCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);
            setPadding(dp(14), dp(10), dp(14), dp(10));

            trackBorderPaint.setStyle(Paint.Style.STROKE);
            trackBorderPaint.setStrokeWidth(dp(1));
            thumbBorderPaint.setStyle(Paint.Style.STROKE);
            thumbBorderPaint.setStrokeWidth(dp(1));

            thumbFlicker = new CellFlickerDrawable(30, 74, 160);
            thumbFlicker.drawFrame = false;
            thumbFlicker.repeatProgress = 4.5f;
            thumbFlicker.animationSpeedScale = 0.75f;

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(3), dp(3), dp(3), dp(3));

            leftButton = createSegmentButton(context, 0);
            leftIcon = (ImageView) leftButton.getChildAt(0);
            leftLabel = (TextView) leftButton.getChildAt(1);
            row.addView(leftButton, LayoutHelper.createLinear(0, 40, 1f));

            rightButton = createSegmentButton(context, 1);
            rightIcon = (ImageView) rightButton.getChildAt(0);
            rightLabel = (TextView) rightButton.getChildAt(1);
            row.addView(rightButton, LayoutHelper.createLinear(0, 40, 1f));

            addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 46));
            updateColors();
        }

        private LinearLayout createSegmentButton(Context context, int index) {
            LinearLayout btn = new LinearLayout(context);
            btn.setOrientation(LinearLayout.HORIZONTAL);
            btn.setGravity(Gravity.CENTER);
            ScaleStateListAnimator.apply(btn, 0.03f, 1.0f);

            ImageView icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            btn.addView(icon, LayoutHelper.createLinear(18, 18, Gravity.CENTER_VERTICAL, 0, 0, 7, 0));

            TextView label = new TextView(context);
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
            label.setTypeface(AndroidUtilities.bold());
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            btn.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

            btn.setOnClickListener(v -> {
                if (selectedIndex == index) {
                    return;
                }
                selectedIndex = index;
                thumbProgress.set(selectedIndex, true);
                AndroidUtilities.vibrateCursor(btn);
                invalidate();
                if (onSelected != null) {
                    onSelected.run(index);
                }
            });
            return btn;
        }

        public void bind(SegmentedSpec spec) {
            this.onSelected = spec.onSelected;
            if (lastLeftIconRes != spec.iconLeftRes) {
                lastLeftIconRes = spec.iconLeftRes;
                leftIcon.setImageResource(spec.iconLeftRes);
            }
            if (lastRightIconRes != spec.iconRightRes) {
                lastRightIconRes = spec.iconRightRes;
                rightIcon.setImageResource(spec.iconRightRes);
            }
            leftLabel.setText(spec.labelLeft);
            rightLabel.setText(spec.labelRight);

            if (this.selectedIndex != spec.selectedIndex) {
                this.selectedIndex = spec.selectedIndex;
                thumbProgress.set(this.selectedIndex, isAttachedToWindow());
            } else {
                thumbProgress.set(this.selectedIndex, false);
            }
            updateColors();
            invalidate();
        }

        @Override
        public void updateColors() {
            boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
            int cardWhite = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
            int grayBg = Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider);
            int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);
            int divider = Theme.getColor(Theme.key_divider, resourcesProvider);

            trackPaint.setColor(ColorUtils.blendARGB(cardWhite, grayBg, isDark ? 0.58f : 0.85f));
            trackBorderPaint.setColor(ColorUtils.setAlphaComponent(divider, isDark ? 0x80 : 0x55));
            thumbPaint.setColor(ColorUtils.blendARGB(cardWhite, accent, isDark ? 0.28f : 0.14f));
            thumbBorderPaint.setColor(ColorUtils.setAlphaComponent(accent, isDark ? 0x7A : 0x52));
            thumbFlicker.setColors(isDark ? Color.WHITE : accent, isDark ? 18 : 14, isDark ? 42 : 30);
            lastLeftTint = 0;
            lastRightTint = 0;
            invalidate();
        }

        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_windowBackgroundWhite);
            colors.add(Theme.key_windowBackgroundGray);
            colors.add(Theme.key_windowBackgroundWhiteBlueText4);
            colors.add(Theme.key_windowBackgroundWhiteGrayText);
            colors.add(Theme.key_windowBackgroundWhiteGrayIcon);
            colors.add(Theme.key_divider);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            thumbFlicker.setParentWidth(Math.max(1, w / 2));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float left = getPaddingLeft();
            float top = getPaddingTop();
            float right = getWidth() - getPaddingRight();
            float bottom = getHeight() - getPaddingBottom();

            trackRect.set(left, top, right, bottom);
            float trackRadius = dp(13);
            canvas.drawRoundRect(trackRect, trackRadius, trackRadius, trackPaint);
            canvas.drawRoundRect(trackRect, trackRadius, trackRadius, trackBorderPaint);

            float t = thumbProgress.set(selectedIndex);
            float inset = dp(3);
            float halfW = (trackRect.width() - inset * 2f) / 2f;
            float thumbLeft = trackRect.left + inset + halfW * t;
            thumbRect.set(thumbLeft, trackRect.top + inset, thumbLeft + halfW, trackRect.bottom - inset);

            float thumbRadius = dp(10);
            canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, thumbPaint);
            if (SharedConfig.animationsEnabled() && LiteMode.isEnabled(LiteMode.FLAG_PARTICLES)) {
                canvas.save();
                clipThumb.rewind();
                clipThumb.addRoundRect(thumbRect, thumbRadius, thumbRadius, Path.Direction.CW);
                canvas.clipPath(clipThumb);
                thumbFlicker.draw(canvas, thumbRect, thumbRadius, this);
                canvas.restore();
            }
            canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, thumbBorderPaint);

            applySegmentTint(leftIcon, leftLabel, 1f - t, true);
            applySegmentTint(rightIcon, rightLabel, t, false);
        }

        private void applySegmentTint(ImageView icon, TextView label, float activeFraction, boolean isLeft) {
            int activeText = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);
            int inactiveText = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
            int inactiveIcon = Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider);
            int nextText = ColorUtils.blendARGB(inactiveText, activeText, activeFraction);
            int nextIcon = ColorUtils.blendARGB(inactiveIcon, activeText, activeFraction);
            label.setTextColor(nextText);
            if (isLeft) {
                if (lastLeftTint != nextIcon) {
                    lastLeftTint = nextIcon;
                    icon.setColorFilter(new PorterDuffColorFilter(nextIcon, PorterDuff.Mode.SRC_IN));
                }
            } else {
                if (lastRightTint != nextIcon) {
                    lastRightTint = nextIcon;
                    icon.setColorFilter(new PorterDuffColorFilter(nextIcon, PorterDuff.Mode.SRC_IN));
                }
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    heightMeasureSpec
            );
        }

        public static class Factory extends UItem.UItemFactory<SegmentedCardCell> {
            static {
                setup(new Factory());
            }

            @Override
            public SegmentedCardCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new SegmentedCardCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (item.object instanceof SegmentedSpec) {
                    ((SegmentedCardCell) view).bind((SegmentedSpec) item.object);
                }
            }

            @Override
            public boolean isClickable() {
                return false;
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                if (a == null || b == null || a.id != b.id || a.viewType != b.viewType) {
                    return false;
                }
                return Objects.equals(a.object, b.object);
            }

            public static UItem of(int id, SegmentedSpec spec) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = spec;
                return item;
            }
        }
    }
}
