package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.mirrly.MirrlyDatacenterController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleActionBarMenu;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

/**
 * Native MirrlyGram screen displaying Telegram datacenter (DC1-DC5) reachability and latency,
 * built on rounded sections, SimpleActionBar glass, AnimatedTextView odometers, and reactive theming.
 */
public class MirrlyDatacenterActivity extends MirrlyBaseSettingsFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final String DATACENTER_DOCS_URL = "https://core.telegram.org/api/datacenter";

    private static final int MENU_REFRESH = 1;

    private static final int ID_INFO_HEADER = 1;
    private static final int ID_DC_BASE = 100;

    private final int dcToHighlight;

    public MirrlyDatacenterActivity() {
        this(0);
    }

    public MirrlyDatacenterActivity(int dcToHighlight) {
        this.dcToHighlight = dcToHighlight;
    }

    @Override
    protected boolean shouldForceActionBarTitle() {
        return true;
    }

    @Override
    protected CharSequence getScreenTitle() {
        return LocaleController.getString(R.string.MirrlyDatacenterStatusTitle);
    }

    @Override
    protected void setupMenu(SimpleActionBarMenu menu) {
        menu.addItem(MENU_REFRESH, R.drawable.msg_retry, getResourceProvider())
                .setContentDescription(LocaleController.getString(R.string.MirrlyDatacenterRefreshAction));
    }

    @Override
    protected void onMenuItemClick(int id) {
        if (id == MENU_REFRESH) {
            if (simpleActionBar != null) {
                AndroidUtilities.vibrateCursor(simpleActionBar);
            }
            MirrlyDatacenterController.checkAll(currentAccount, true);
            syncVisibleDatacenterCells(true);
            BulletinFactory.of(this)
                    .createSimpleBulletin(R.raw.info, LocaleController.getString(R.string.MirrlyDatacenterRefreshingToast))
                    .show();
        }
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyDatacenterCheckDone);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        MirrlyDatacenterController.checkAll(currentAccount, false);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyDatacenterCheckDone);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        if (dcToHighlight >= 1 && dcToHighlight <= MirrlyDatacenterController.DC_COUNT && listView != null) {
            listView.highlightRow(() -> {
                int pos = findDatacenterAdapterPosition(dcToHighlight);
                if (pos >= 0 && listView.getLayoutManager() instanceof LinearLayoutManager) {
                    ((LinearLayoutManager) listView.getLayoutManager()).scrollToPositionWithOffset(pos, dp(120));
                }
                return pos;
            });
        }
        return view;
    }

    private int findDatacenterAdapterPosition(int dcId) {
        if (listView == null || listView.adapter == null) {
            return -1;
        }
        int targetId = ID_DC_BASE + dcId;
        int count = listView.adapter.getItemCount();
        for (int i = 0; i < count; i++) {
            UItem item = listView.adapter.getItem(i);
            if (item != null && item.id == targetId) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(DatacenterHeaderCell.Factory.of(ID_INFO_HEADER));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyDatacenterListSection)));
        int currentDcId = ConnectionsManager.getInstance(currentAccount).getCurrentDatacenterId();
        for (int dcId = 1; dcId <= MirrlyDatacenterController.DC_COUNT; dcId++) {
            MirrlyDatacenterController.DatacenterInfo info = MirrlyDatacenterController.getDatacenter(dcId);
            if (info != null) {
                items.add(DatacenterCell.Factory.of(
                        ID_DC_BASE + dcId,
                        DatacenterSnapshot.from(info, currentDcId)
                ));
            }
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyDatacenterHint)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item == null) {
            return;
        }
        if (item.id > ID_DC_BASE && item.id <= ID_DC_BASE + MirrlyDatacenterController.DC_COUNT) {
            int dcId = item.id - ID_DC_BASE;
            MirrlyDatacenterController.DatacenterInfo info = MirrlyDatacenterController.getDatacenter(dcId);
            if (info == null || info.checking) {
                return;
            }
            if (view != null) {
                AndroidUtilities.vibrateCursor(view);
            }
            MirrlyDatacenterController.checkDatacenter(currentAccount, dcId, true);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.mirrlyDatacenterCheckDone) {
            syncVisibleDatacenterCells(true);
        } else if (id == NotificationCenter.proxySettingsChanged) {
            MirrlyDatacenterController.invalidateCache();
            MirrlyDatacenterController.checkAll(currentAccount, true);
            syncVisibleDatacenterCells(true);
        } else if (id == NotificationCenter.didSetNewTheme && listView != null && listView.adapter != null) {
            listView.adapter.updateWithoutNotify();
            for (int i = 0; i < listView.getChildCount(); i++) {
                View child = listView.getChildAt(i);
                if (child instanceof Theme.Colorable) {
                    ((Theme.Colorable) child).updateColors();
                }
            }
            listView.invalidate();
        }
    }

    private void syncVisibleDatacenterCells(boolean animated) {
        if (listView == null) {
            return;
        }
        int currentDcId = ConnectionsManager.getInstance(currentAccount).getCurrentDatacenterId();
        for (int i = 0, count = listView.getChildCount(); i < count; i++) {
            View child = listView.getChildAt(i);
            if (child instanceof DatacenterCell) {
                DatacenterCell cell = (DatacenterCell) child;
                int dcId = cell.boundDcId;
                MirrlyDatacenterController.DatacenterInfo info = MirrlyDatacenterController.getDatacenter(dcId);
                if (info != null) {
                    cell.bindSnapshot(DatacenterSnapshot.from(info, currentDcId), animated);
                }
            }
        }
        if (listView.adapter != null) {
            listView.adapter.updateWithoutNotify();
        }
    }

    private static CharSequence buildAboutText() {
        String raw = LocaleController.getString(R.string.MirrlyDatacenterStatusAbout);
        SpannableStringBuilder builder = new SpannableStringBuilder(raw);
        int start = raw.indexOf("**");
        int end = raw.lastIndexOf("**");
        if (start >= 0 && end > start + 1) {
            builder.delete(end, end + 2);
            builder.delete(start, start + 2);
            URLSpanNoUnderline span = new URLSpanNoUnderline(DATACENTER_DOCS_URL) {
                @Override
                public void onClick(View widget) {
                    if (widget != null && widget.getContext() != null) {
                        Browser.openUrl(widget.getContext(), DATACENTER_DOCS_URL);
                    }
                }
            };
            builder.setSpan(span, start, end - 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder;
    }

    private static final class DatacenterSnapshot {
        final int dcId;
        final boolean checking;
        final boolean available;
        final long ping;
        final boolean isCurrentDc;
        final String cdnCluster;

        private DatacenterSnapshot(int dcId, boolean checking, boolean available, long ping, boolean isCurrentDc, String cdnCluster) {
            this.dcId = dcId;
            this.checking = checking;
            this.available = available;
            this.ping = ping;
            this.isCurrentDc = isCurrentDc;
            this.cdnCluster = cdnCluster;
        }

        static DatacenterSnapshot from(MirrlyDatacenterController.DatacenterInfo info, int currentDcId) {
            return new DatacenterSnapshot(
                    info.id,
                    info.checking,
                    info.available,
                    info.ping,
                    info.id == currentDcId,
                    MirrlyDatacenterController.getDcCdnClusterName(info.id, false)
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DatacenterSnapshot)) return false;
            DatacenterSnapshot that = (DatacenterSnapshot) o;
            return dcId == that.dcId
                    && checking == that.checking
                    && available == that.available
                    && ping == that.ping
                    && isCurrentDc == that.isCurrentDc
                    && Objects.equals(cdnCluster, that.cdnCluster);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dcId, checking, available, ping, isCurrentDc, cdnCluster);
        }
    }

    /**
     * Top informational card featuring the Telegram placeholder popcorn sticker (with native RLottie fallback)
     * and an interactive link to Telegram's datacenter architecture documentation.
     */
    public static class DatacenterHeaderCell extends LinearLayout implements Theme.Colorable, NotificationCenter.NotificationCenterDelegate {

        private final int currentAccount;
        private final Theme.ResourcesProvider resourcesProvider;
        private final BackupImageView imageView;
        private final LinkSpanDrawable.LinksTextView textView;
        private RLottieDrawable fallbackDrawable;
        private boolean hasStickerDocument = false;

        public DatacenterHeaderCell(
                Context context,
                int currentAccount,
                @Nullable Theme.ResourcesProvider resourcesProvider
        ) {
            super(context);
            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;

            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setPadding(dp(28), dp(18), dp(28), dp(18));

            imageView = new BackupImageView(context);
            imageView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            ScaleStateListAnimator.apply(imageView);
            imageView.setOnClickListener(v -> {
                AndroidUtilities.vibrate(imageView);
                RLottieDrawable lottie = imageView.getImageReceiver().getLottieAnimation();
                if (lottie != null) {
                    if (!lottie.isRunning()) {
                        lottie.setCurrentFrame(0, false);
                        lottie.restart();
                    }
                } else if (fallbackDrawable != null) {
                    fallbackDrawable.setCurrentFrame(0, false);
                    fallbackDrawable.restart();
                }
            });
            addView(imageView, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

            textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView.setText(buildAboutText());
            addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

            updateColors();
            loadSticker();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    heightMeasureSpec
            );
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            loadSticker();
            if (fallbackDrawable != null && !hasStickerDocument) {
                fallbackDrawable.start();
            }
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.diceStickersDidLoad);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (fallbackDrawable != null) {
                fallbackDrawable.stop();
            }
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.diceStickersDidLoad);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.diceStickersDidLoad && args.length > 0) {
                Object name = args[0];
                if (AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME.equals(name)) {
                    loadSticker();
                }
            }
        }

        private void loadSticker() {
            TLRPC.TL_messages_stickerSet set = MediaDataController.getInstance(currentAccount)
                    .getStickerSetByName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME);
            if (set == null) {
                set = MediaDataController.getInstance(currentAccount)
                        .getStickerSetByEmojiOrName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME);
            }
            TLRPC.Document document = (set != null && set.documents != null && set.documents.size() >= 3)
                    ? set.documents.get(2)
                    : null;

            if (document != null) {
                hasStickerDocument = true;
                SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document.thumbs, Theme.key_emptyListPlaceholder, 0.2f);
                if (svgThumb != null) {
                    svgThumb.overrideWidthAndHeight(512, 512);
                }
                ImageLocation imageLocation = ImageLocation.getForDocument(document);
                imageView.setImage(imageLocation, "130_130", "tgs", svgThumb, set);
                imageView.getImageReceiver().setAutoRepeat(2);
                if (fallbackDrawable != null) {
                    fallbackDrawable.recycle(true);
                    fallbackDrawable = null;
                }
            } else {
                if (!hasStickerDocument) {
                    if (fallbackDrawable == null) {
                        fallbackDrawable = new RLottieDrawable(
                                R.raw.utyan_private,
                                dp(120),
                                dp(120)
                        );
                        fallbackDrawable.setAutoRepeat(1);
                    }
                    imageView.setImageDrawable(fallbackDrawable);
                    fallbackDrawable.start();
                }
                MediaDataController.getInstance(currentAccount)
                        .loadStickersByEmojiOrName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME, false, set == null);
            }
        }

        @Override
        public void updateColors() {
            if (textView != null) {
                textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                textView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourcesProvider));
                textView.setHighlightColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkSelection, resourcesProvider));
            }
        }

        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_windowBackgroundWhiteBlackText);
            colors.add(Theme.key_windowBackgroundWhiteLinkText);
            colors.add(Theme.key_windowBackgroundWhiteLinkSelection);
        }

        public static class Factory extends UItem.UItemFactory<DatacenterHeaderCell> {
            static {
                setup(new Factory());
            }

            @Override
            public DatacenterHeaderCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new DatacenterHeaderCell(context, currentAccount, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (view instanceof DatacenterHeaderCell) {
                    ((DatacenterHeaderCell) view).textView.setText(buildAboutText());
                }
            }

            @Override
            public boolean isShadow() {
                return true;
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return equals(a, b);
            }

            public static UItem of(int id) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                return item;
            }
        }
    }

    /**
     * Datacenter status row with animated ping odometer, optional active-DC pill badge,
     * reactive theme colors, and RTL-aware layout/divider.
     */
    public static class DatacenterCell extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final LinearLayout contentColumn;
        private final LinearLayout titleRow;
        private final TextView titleView;
        private final TextView currentBadgeView;
        private final GradientDrawable currentBadgeBg;
        private final AnimatedTextView statusView;

        int boundDcId = -1;
        private int currentStatusColorKey = Theme.key_windowBackgroundWhiteGrayText2;
        private boolean needDivider;

        public DatacenterCell(Context context, @Nullable Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            ScaleStateListAnimator.apply(this, 0.015f, 1.2f);

            contentColumn = new LinearLayout(context);
            contentColumn.setOrientation(LinearLayout.VERTICAL);
            contentColumn.setGravity(Gravity.CENTER_VERTICAL);
            addView(contentColumn, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.MATCH_PARENT,
                    Gravity.FILL,
                    20, 0, 20, 0
            ));

            titleRow = new LinearLayout(context);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            contentColumn.addView(titleRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 3));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setLines(1);
            titleView.setMaxLines(1);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);

            currentBadgeView = new TextView(context);
            currentBadgeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
            currentBadgeView.setTypeface(AndroidUtilities.bold());
            currentBadgeView.setSingleLine(true);
            currentBadgeView.setPadding(dp(7), dp(2), dp(7), dp(2));
            currentBadgeView.setText(LocaleController.getString(R.string.MirrlyDatacenterCurrentBadge));
            currentBadgeBg = new GradientDrawable();
            currentBadgeBg.setCornerRadius(dp(6));
            currentBadgeView.setBackground(currentBadgeBg);
            currentBadgeView.setVisibility(View.GONE);

            if (LocaleController.isRTL) {
                titleRow.addView(currentBadgeView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
                titleRow.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));
            } else {
                titleRow.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
                titleRow.addView(currentBadgeView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
            }

            statusView = new AnimatedTextView(context, false, true, false);
            statusView.setAnimationProperties(0.35f, 0, 220, CubicBezierInterpolator.EASE_OUT_QUINT);
            statusView.setTextSize(dp(13.5f));
            statusView.setEllipsizeByGradient(true);
            statusView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            contentColumn.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 20));

            updateColors();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(64) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY)
            );
        }

        void bind(DatacenterSnapshot snapshot, boolean divider, boolean animated) {
            needDivider = divider;
            setWillNotDraw(!needDivider);
            boolean sameDc = boundDcId == snapshot.dcId;
            boundDcId = snapshot.dcId;
            if (!sameDc) {
                titleView.setText(MirrlyDatacenterController.formatDCTitle(snapshot.dcId));
            }
            currentBadgeView.setVisibility(snapshot.isCurrentDc ? View.VISIBLE : View.GONE);
            bindSnapshot(snapshot, animated && sameDc);
        }

        void bindSnapshot(DatacenterSnapshot snapshot, boolean animated) {
            CharSequence statusText;
            int colorKey;
            String cdn = snapshot.cdnCluster != null ? snapshot.cdnCluster : "";
            if (snapshot.checking) {
                statusText = !cdn.isEmpty()
                        ? LocaleController.formatString(R.string.MirrlyDatacenterCheckingCdn, cdn)
                        : LocaleController.getString(R.string.MirrlyDatacenterChecking);
                colorKey = Theme.key_windowBackgroundWhiteGrayText2;
            } else if (snapshot.available) {
                if (snapshot.ping >= MirrlyDatacenterController.SLOW_PING_THRESHOLD_MS) {
                    statusText = !cdn.isEmpty()
                            ? LocaleController.formatString(R.string.MirrlyDatacenterSlowPingCdn, snapshot.ping, cdn)
                            : LocaleController.formatString(R.string.MirrlyDatacenterSlowPing, snapshot.ping);
                    colorKey = Theme.key_text_RedRegular;
                } else if (snapshot.ping > 0) {
                    statusText = !cdn.isEmpty()
                            ? LocaleController.formatString(R.string.MirrlyDatacenterAvailablePingCdn, snapshot.ping, cdn)
                            : LocaleController.formatString(R.string.MirrlyDatacenterAvailablePing, snapshot.ping);
                    colorKey = Theme.key_windowBackgroundWhiteGreenText;
                } else {
                    statusText = !cdn.isEmpty()
                            ? LocaleController.formatString(R.string.MirrlyDatacenterAvailableCdn, cdn)
                            : LocaleController.getString(R.string.MirrlyDatacenterAvailable);
                    colorKey = Theme.key_windowBackgroundWhiteGreenText;
                }
            } else {
                statusText = !cdn.isEmpty()
                        ? LocaleController.formatString(R.string.MirrlyDatacenterUnavailableCdn, cdn)
                        : LocaleController.getString(R.string.MirrlyDatacenterUnavailable);
                colorKey = Theme.key_text_RedRegular;
            }
            currentStatusColorKey = colorKey;
            statusView.setText(statusText, animated);
            statusView.setTextColor(Theme.getColor(colorKey, resourcesProvider), animated);
        }

        @Override
        public void updateColors() {
            if (titleView == null || statusView == null) {
                return;
            }
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            statusView.setTextColor(Theme.getColor(currentStatusColorKey, resourcesProvider));
            int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);
            currentBadgeView.setTextColor(accent);
            currentBadgeBg.setColor(ColorUtils.setAlphaComponent(accent, 28));
        }

        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_windowBackgroundWhiteBlackText);
            colors.add(Theme.key_windowBackgroundWhiteBlueText4);
            colors.add(Theme.key_windowBackgroundWhiteGrayText2);
            colors.add(Theme.key_windowBackgroundWhiteGreenText);
            colors.add(Theme.key_text_RedRegular);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            if (needDivider) {
                PaintCompat.drawDivider(canvas, getMeasuredWidth(), getMeasuredHeight(), resourcesProvider);
            }
        }

        public static class Factory extends UItem.UItemFactory<DatacenterCell> {
            static {
                setup(new Factory());
            }

            @Override
            public DatacenterCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new DatacenterCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (!(view instanceof DatacenterCell) || !(item.object instanceof DatacenterSnapshot)) {
                    return;
                }
                DatacenterCell cell = (DatacenterCell) view;
                DatacenterSnapshot snapshot = (DatacenterSnapshot) item.object;
                cell.bind(snapshot, divider, view.isAttachedToWindow());
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

            public static UItem of(int id, DatacenterSnapshot snapshot) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = snapshot;
                return item;
            }
        }
    }

    private static final class PaintCompat {
        private PaintCompat() {
        }

        static void drawDivider(Canvas canvas, int width, int height, @Nullable Theme.ResourcesProvider rp) {
            android.graphics.Paint dividerPaint = rp != null ? rp.getPaint(Theme.key_paint_divider) : null;
            if (dividerPaint == null) {
                dividerPaint = Theme.dividerPaint;
            }
            if (dividerPaint == null) {
                return;
            }
            float startX = LocaleController.isRTL ? 0 : dp(20);
            float endX = width - (LocaleController.isRTL ? dp(20) : 0);
            canvas.drawLine(startX, height - 1, endX, height - 1, dividerPaint);
        }
    }
}
