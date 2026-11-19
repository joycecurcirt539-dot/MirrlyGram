package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerItemsEnterAnimator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Components.WallpaperParallaxEffect;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.utils.Blur3Utils;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.utils.glass.BaseFragmentSimpleGlass;

import java.util.ArrayList;
import java.util.HashSet;

import me.vkryl.core.reference.ReferenceList;

/**
 * Native Telegram-styled "About Mirrlygram" screen featuring glass pill tabs,
 * interactive 3D branded emblem with StarParticlesView, SuperRipple, CellFlickerDrawable,
 * WallpaperParallaxEffect, FireworksOverlay easter egg, and GNU GPLv3 licensing.
 */
public class MirrlyAboutActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public static final int TAB_PROJECT = 0;
    public static final int TAB_SUPPORT = 1;

    private static final int ID_HERO = 1;
    private static final int ID_OPEN_ENGINE_SETTINGS = 3;
    private static final int ID_OPEN_CLIENT_REPO = 4;
    private static final int ID_OPEN_PROXY_REPO = 5;
    private static final int ID_OPEN_CHANNEL = 6;
    private static final int ID_DEV_INFO = 7;
    private static final int ID_OPEN_LICENSE = 8;
    private static final int ID_JUMP_SUPPORT = 9;

    private static final int ID_DONATE_CLOUDTIPS = 21;
    private static final int ID_DONATE_YANDEX = 22;
    private static final int ID_DONATE_DALINK = 23;

    private static final String URL_CLIENT_REPO = "https://github.com/joycecurcirt539-dot/MirrlyGram";
    private static final String URL_PROXY_REPO = "https://github.com/joycecurcirt539-dot/Mirrly-TG-Proxy";
    private static final String URL_TG_CHANNEL = "https://t.me/WhyOKyHb";
    private static final String URL_GITHUB_PROFILE = "https://github.com/joycecurcirt539-dot";
    private static final String URL_GPL_V3 = "https://www.gnu.org/licenses/gpl-3.0.html";
    private static final String URL_CLOUDTIPS = "https://pay.cloudtips.ru/p/e8c24aee";
    private static final String URL_YANDEX_TIPS = "https://tips.yandex.ru/guest/payment/3754149";
    private static final String URL_DALINK = "https://dalink.to/cartneyzix";

    private enum DonationOption {
        CLOUDTIPS(
                ID_DONATE_CLOUDTIPS,
                0xFF5A8FE6,
                0xFF4277D4,
                R.drawable.mirrly_donate_cloudtips,
                R.string.MirrlyDonateCloudtipsTitle,
                R.string.MirrlyDonateCloudtipsDesc,
                URL_CLOUDTIPS,
                "pay.cloudtips.ru",
                "pay.cloudtips.ru/p/e8c24aee"
        ),
        YANDEX_TIPS(
                ID_DONATE_YANDEX,
                0xFFFCDA00,
                0xFFF2C600,
                R.drawable.mirrly_donate_yandex,
                R.string.MirrlyDonateYandexTitle,
                R.string.MirrlyDonateYandexDesc,
                URL_YANDEX_TIPS,
                "tips.yandex.ru",
                "tips.yandex.ru/guest/payment/3754149"
        ),
        DALINK(
                ID_DONATE_DALINK,
                0xFFFA9C05,
                0xFFE86A05,
                R.drawable.mirrly_donate_dalink,
                R.string.MirrlyDonateDalinkTitle,
                R.string.MirrlyDonateDalinkDesc,
                URL_DALINK,
                "dalink.to",
                "dalink.to/cartneyzix"
        );

        final int id;
        final int topColor;
        final int bottomColor;
        @DrawableRes final int iconRes;
        @StringRes final int titleRes;
        @StringRes final int descRes;
        final String url;
        final String shortDomain;
        final String displayUrl;

        DonationOption(
                int id,
                int topColor,
                int bottomColor,
                @DrawableRes int iconRes,
                @StringRes int titleRes,
                @StringRes int descRes,
                String url,
                String shortDomain,
                String displayUrl
        ) {
            this.id = id;
            this.topColor = topColor;
            this.bottomColor = bottomColor;
            this.iconRes = iconRes;
            this.titleRes = titleRes;
            this.descRes = descRes;
            this.url = url;
            this.shortDomain = shortDomain;
            this.displayUrl = displayUrl;
        }

        @Nullable
        static DonationOption byId(int id) {
            for (DonationOption option : values()) {
                if (option.id == id) {
                    return option;
                }
            }
            return null;
        }
    }

    private final int initialTab;
    private ViewPagerFixed pager;
    private ViewPagerFixed.TabsView tabsView;
    private FrameLayout tabsContainer;
    private View listTopOverlay;
    private final ReferenceList<TabListView> listViews = new ReferenceList<>();
    private WallpaperParallaxEffect parallaxEffect;

    private int insetTop;
    private int insetBottom;

    public MirrlyAboutActivity() {
        this(TAB_PROJECT);
    }

    public MirrlyAboutActivity(int initialTab) {
        super();
        this.initialTab = initialTab == TAB_SUPPORT ? TAB_SUPPORT : TAB_PROJECT;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        if (parallaxEffect != null) {
            parallaxEffect.setEnabled(false);
            parallaxEffect.setCallback(null);
        }
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        super.onFragmentDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateParallaxState(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        updateParallaxState(false);
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    protected boolean useFadeDrawableForActionBar() {
        return false;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public View createView(Context context) {
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.MirrlyAboutTitle));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                        MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY)
                );
            }
        };
        ThemeBind.backgroundColor(frameLayout, Theme.key_windowBackgroundGray, getResourceProvider());

        parallaxEffect = new WallpaperParallaxEffect(context);
        parallaxEffect.setCallback((offsetX, offsetY, angle) -> applyHeroParallax(offsetX * 0.45f, offsetY * 0.45f));

        pager = new ViewPagerFixed(context);
        pager.setAdapter(new PageAdapter());
        if (initialTab != TAB_PROJECT) {
            pager.setPosition(initialTab);
        }

        tabsContainer = new FrameLayout(context);
        tabsView = pager.createTabsView(true, ViewPagerFixed.SELECTOR_TYPE_BUBBLE_STYLE);
        frameLayout.addView(pager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        listTopOverlay = new View(context);
        frameLayout.addView(listTopOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.TOP));
        ViewGroup.LayoutParams overlayLp = listTopOverlay.getLayoutParams();
        overlayLp.height = insetTop + ActionBar.getCurrentActionBarHeight() + dp(44);

        tabsContainer.setTranslationY(BaseFragmentSimpleGlass.getPillGlassTop(insetTop) - dp(9.66f));
        tabsContainer.addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36 + 6 + 6));
        tabsContainer.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabsContainer.setBackground(getBaseSimpleGlass().factory
                .create(tabsContainer)
                .setColorProvider(BlurredBackgroundProviderImpl.simpleActionBar(resourceProvider))
                .setPadding(dp(9.66f))
                .setRadius(dp(18))
        );

        getBaseSimpleGlass().setViews(frameLayout, pager, simpleActionBar, resourceProvider);
        getBaseSimpleGlass().setCapture((canvas, position) -> {
            for (int a = 0, n = pager.getChildCount(); a < n; a++) {
                final View child = pager.getChildAt(a);
                if (child instanceof RecyclerListView) {
                    final RecyclerListView rv = (RecyclerListView) child;
                    Blur3Utils.captureRelativeParent(rv, canvas, position, rv, frameLayout);
                }
            }
        });
        getBaseSimpleGlass().setClipView(null);
        frameLayout.addView(tabsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, -4, 0, -4, 0));
        listTopOverlay.setBackground(getBaseSimpleGlass().createActionBarFadeDrawable(listTopOverlay));
        simpleActionBar.setBackground(null);

        return fragmentView = frameLayout;
    }

    private void updateParallaxState(boolean active) {
        if (parallaxEffect == null) {
            return;
        }
        boolean enable = active
                && !finishing
                && SharedConfig.animationsEnabled()
                && LiteMode.isEnabled(LiteMode.FLAG_PARTICLES);
        parallaxEffect.setEnabled(enable);
    }

    private void applyHeroParallax(float offsetX, float offsetY) {
        for (TabListView lv : listViews) {
            for (int i = 0, count = lv.getChildCount(); i < count; i++) {
                View child = lv.getChildAt(i);
                if (child instanceof MirrlyUiKit.HeroCardView) {
                    ((MirrlyUiKit.HeroCardView) child).setParallaxOffset(offsetX, offsetY);
                }
            }
        }
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        if (event != null && event.getY() <= ActionBar.getCurrentActionBarHeight() + dp(48)) {
            return true;
        }
        return pager == null || pager.getCurrentPosition() == 0;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        this.insetTop = top;
        this.insetBottom = bottom;

        if (tabsContainer != null) {
            tabsContainer.setTranslationY(BaseFragmentSimpleGlass.getPillGlassTop(insetTop) - dp(9.66f));
        }
        if (listTopOverlay != null) {
            ViewGroup.LayoutParams lp = listTopOverlay.getLayoutParams();
            lp.height = insetTop + ActionBar.getCurrentActionBarHeight() + dp(44);
            listTopOverlay.requestLayout();
        }
        for (TabListView lv : listViews) {
            lv.checkInsets();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.proxySettingsChanged || id == NotificationCenter.didSetNewTheme) {
            for (TabListView lv : listViews) {
                if (lv.adapter != null) {
                    lv.adapter.update(id == NotificationCenter.proxySettingsChanged);
                }
            }
        }
    }

    private class PageAdapter extends ViewPagerFixed.Adapter {
        @Override
        public int getItemCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        @Override
        public View createView(int viewType) {
            TabListView listView = new TabListView(getContext(), viewType);
            listViews.add(listView);
            return listView;
        }

        @Override
        public void bindView(View view, int position, int viewType) {
            ((TabListView) view).checkInsets();
        }

        @Override
        public String getItemTitle(int position) {
            if (position == TAB_SUPPORT) {
                return LocaleController.getString(R.string.MirrlyAboutTabSupport);
            }
            return LocaleController.getString(R.string.MirrlyAboutTabProject);
        }
    }

    private class TabListView extends UniversalRecyclerView implements MirrlyUiKit.HeroCardView.HostProvider {

        private final RecyclerItemsEnterAnimator enterAnimator;
        private boolean enterAnimated = false;

        TabListView(Context context, int tabType) {
            super(
                    context,
                    currentAccount,
                    classGuid,
                    false,
                    (items, adapter) -> fillItemsForTab(tabType, items),
                    null,
                    null,
                    resourceProvider
            );
            setCaptureSectionsDecoratorAllowed(true);
            adapter.setApplyBackground(false);
            setSections();
            setSectionsDrawBackground(true);
            setClipToPadding(false);
            glassEngine.addScrolledView(this);

            enterAnimator = new RecyclerItemsEnterAnimator(this, true);

            addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    if (Math.abs(dy) < dp(5)) {
                        return;
                    }
                    for (int i = 0, count = getChildCount(); i < count; i++) {
                        View child = getChildAt(i);
                        if (child instanceof MirrlyUiKit.HeroCardView) {
                            ((MirrlyUiKit.HeroCardView) child).onListScrolled(dy);
                        }
                    }
                }
            });

            setOnItemClickListener((view, position, x, y) -> {
                UItem item = adapter.getItem(position);
                if (item != null) {
                    onTabItemClick(this, item, view);
                }
            });
            setOnItemLongClickListener((view, position, x, y) -> {
                UItem item = adapter.getItem(position);
                return item != null && onTabItemLongClick(this, item, view);
            });
            checkInsets();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!enterAnimated && SharedConfig.animationsEnabled()) {
                enterAnimated = true;
                enterAnimator.showItemsAnimated(0);
            }
        }

        void checkInsets() {
            BaseFragmentSimpleGlass.setListViewParams(
                    this,
                    insetTop,
                    insetBottom,
                    ActionBar.getCurrentActionBarHeight() + dp(42),
                    0
            );
        }

        @Nullable
        @Override
        public BaseFragment getMirrlyHeroHost() {
            return MirrlyAboutActivity.this;
        }
    }

    private void fillItemsForTab(int tabType, ArrayList<UItem> items) {
        if (tabType == TAB_SUPPORT) {
            fillSupportItems(items);
        } else {
            fillProjectItems(items);
        }
    }

    private void fillProjectItems(ArrayList<UItem> items) {
        MirrlyUiKit.EngineSnapshot snapshot = MirrlyUiKit.EngineSnapshot.capture();
        String versionLine = MirrlyUiKit.formatClientVersion();
        CharSequence[] heroSubtitles = new CharSequence[]{
                versionLine + " • " + LocaleController.getString(R.string.MirrlyAboutHeroSubtitle),
                snapshot.getHeroEngineLine(),
                "GNU GPLv3 • MirrlyDev (R1Xern)"
        };

        items.add(MirrlyUiKit.HeroCardView.Factory.of(
                ID_HERO,
                new MirrlyUiKit.HeroSpec(
                        MirrlyUiKit.HeroSpec.MODE_BRAND,
                        0,
                        false,
                        LocaleController.getString(R.string.AppName),
                        heroSubtitles,
                        "Mirrlygram • GNU GPLv3"
                )
        ));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyAboutClientHeader)));
        items.add(SettingsActivity.SettingCell.Factory.of(
                ID_OPEN_ENGINE_SETTINGS,
                0xFF8A68F7,
                0xFF5B4CE6,
                R.drawable.mirrly_about_settings,
                LocaleController.getString(R.string.MirrlyAboutOpenEngineSettings),
                LocaleController.getString(R.string.MirrlyAboutOpenEngineSettingsSub),
                snapshot.getShortBadge()
        ));
        items.add(SettingsActivity.SettingCell.Factory.of(
                ID_OPEN_CLIENT_REPO,
                IconBackgroundColors.BLUE_DEEP.top,
                IconBackgroundColors.BLUE_DEEP.bottom,
                R.drawable.mirrly_about_source,
                LocaleController.getString(R.string.MirrlyAboutClientRepoTitle),
                LocaleController.getString(R.string.MirrlyAboutClientRepoSub),
                "GitHub"
        ));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyAboutClientDesc)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyAboutProxyHeader)));
        items.add(SettingsActivity.SettingCell.Factory.of(
                ID_OPEN_PROXY_REPO,
                IconBackgroundColors.CYAN.top,
                IconBackgroundColors.CYAN.bottom,
                R.drawable.mirrly_about_proxy,
                LocaleController.getString(R.string.MirrlyAboutProxyRepoTitle),
                LocaleController.getString(R.string.MirrlyAboutProxyRepoSub),
                "GitHub"
        ));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyAboutProxyDesc)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyAboutDevHeader)));
        items.add(SettingsActivity.SettingCell.Factory.of(
                ID_OPEN_CHANNEL,
                IconBackgroundColors.BLUE_LIGHT.top,
                IconBackgroundColors.BLUE_LIGHT.bottom,
                R.drawable.mirrly_about_channel,
                LocaleController.getString(R.string.MirrlyAboutChannelTitle),
                LocaleController.getString(R.string.MirrlyAboutChannelSub),
                "@WhyOKyHb"
        ));
        items.add(SettingsActivity.SettingCell.Factory.of(
                ID_DEV_INFO,
                0xFF5E6E7E,
                0xFF3E4C5A,
                R.drawable.mirrly_about_github,
                LocaleController.getString(R.string.MirrlyAboutDevTitle),
                LocaleController.getString(R.string.MirrlyAboutDevSub),
                "GitHub"
        ));
        items.add(SettingsActivity.SettingCell.Factory.of(
                ID_OPEN_LICENSE,
                IconBackgroundColors.GREEN.top,
                IconBackgroundColors.GREEN.bottom,
                R.drawable.mirrly_about_license,
                LocaleController.getString(R.string.MirrlyAboutLicenseTitle),
                LocaleController.getString(R.string.MirrlyAboutLicenseSub),
                "GPLv3"
        ));
        items.add(SettingsActivity.SettingCell.Factory.of(
                ID_JUMP_SUPPORT,
                IconBackgroundColors.RED.top,
                IconBackgroundColors.RED.bottom,
                R.drawable.mirrly_about_support,
                LocaleController.getString(R.string.MirrlyAboutSupportJumpTitle),
                LocaleController.getString(R.string.MirrlyAboutSupportJumpSub)
        ));

        items.add(UItem.asCenterShadow(LocaleController.getString(R.string.MirrlyAboutVersionFooter)));
    }

    private void fillSupportItems(ArrayList<UItem> items) {
        CharSequence[] supportSubtitles = new CharSequence[]{
                LocaleController.getString(R.string.MirrlyAboutSupportHeroSub),
                LocaleController.getString(R.string.MirrlyAboutSupportCycle1),
                LocaleController.getString(R.string.MirrlyAboutSupportCycle2)
        };

        items.add(MirrlyUiKit.HeroCardView.Factory.of(
                ID_HERO,
                new MirrlyUiKit.HeroSpec(
                        MirrlyUiKit.HeroSpec.MODE_BRAND_GLITTER,
                        R.drawable.mirrly_support_emblem,
                        true,
                        LocaleController.getString(R.string.MirrlyAboutSupportHeroTitle),
                        supportSubtitles,
                        "Mirrlygram • GNU GPLv3"
                )
        ));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyAboutDonateMethodsHeader)));
        DonationOption[] options = DonationOption.values();
        for (int i = 0; i < options.length; i++) {
            DonationOption option = options[i];
            items.add(DonationMethodCell.Factory.of(
                    option.id,
                    option.topColor,
                    option.bottomColor,
                    option.iconRes,
                    LocaleController.getString(option.titleRes),
                    LocaleController.getString(option.descRes),
                    option.shortDomain,
                    i < options.length - 1
            ));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyAboutSupportDesc)));
    }

    private void onTabItemClick(TabListView listView, UItem item, View view) {
        if (item.id == ID_OPEN_ENGINE_SETTINGS) {
            presentFragment(new MirrlyGramSettingsActivity());
            return;
        }
        if (item.id == ID_JUMP_SUPPORT) {
            if (tabsView != null) {
                tabsView.scrollToTab(TAB_SUPPORT, TAB_SUPPORT);
            } else if (pager != null) {
                pager.setPosition(TAB_SUPPORT);
            }
            return;
        }
        if (item.id == ID_OPEN_LICENSE) {
            showLicenseSheet();
            return;
        }
        DonationOption donation = DonationOption.byId(item.id);
        if (donation != null) {
            showDonateMethodSheet(donation);
            return;
        }
        String url = resolveUrlById(item.id);
        if (!TextUtils.isEmpty(url)) {
            openExternalUrl(url);
        }
    }

    private boolean onTabItemLongClick(TabListView listView, UItem item, View view) {
        String url = resolveUrlById(item.id);
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        ItemOptions.makeOptions(this, view)
                .add(R.drawable.msg_openin, LocaleController.getString(R.string.MirrlyAboutOpenLink), () -> openExternalUrl(url))
                .add(R.drawable.msg_copy, LocaleController.getString(R.string.MirrlyAboutCopyLink), () -> copyUrlWithBulletin(url, null))
                .setScrimViewBackground(listView.getClipBackground(view))
                .show();
        return true;
    }

    @Nullable
    private static String resolveUrlById(int id) {
        DonationOption donation = DonationOption.byId(id);
        if (donation != null) {
            return donation.url;
        }
        switch (id) {
            case ID_OPEN_CLIENT_REPO:
                return URL_CLIENT_REPO;
            case ID_OPEN_PROXY_REPO:
                return URL_PROXY_REPO;
            case ID_OPEN_CHANNEL:
                return URL_TG_CHANNEL;
            case ID_DEV_INFO:
                return URL_GITHUB_PROFILE;
            case ID_OPEN_LICENSE:
                return URL_GPL_V3;
            default:
                return null;
        }
    }

    private void openExternalUrl(String url) {
        if (getParentActivity() == null || TextUtils.isEmpty(url)) {
            return;
        }
        Browser.openUrl(getParentActivity(), url);
    }

    private void copyUrlWithBulletin(String url, @Nullable BottomSheet sheet) {
        MirrlyUiKit.copyTextWithBulletin(
                this,
                url,
                LocaleController.getString(R.string.MirrlyAboutLinkCopied),
                sheet,
                resourceProvider
        );
    }

    private void showDonateMethodSheet(DonationOption option) {
        showUrlActionSheet(
                R.raw.gift,
                LocaleController.getString(option.titleRes),
                option.displayUrl,
                LocaleController.getString(option.descRes),
                LocaleController.getString(R.string.MirrlyDonateProceed),
                option.url,
                true
        );
    }

    private void showLicenseSheet() {
        showUrlActionSheet(
                R.raw.info,
                LocaleController.getString(R.string.MirrlyAboutLicenseSheetTitle),
                "gnu.org/licenses/gpl-3.0",
                LocaleController.getString(R.string.MirrlyAboutLicenseSheetDesc),
                LocaleController.getString(R.string.MirrlyAboutOpenLicenseText),
                URL_GPL_V3,
                false
        );
    }

    private void showUrlActionSheet(
            int lottieRes,
            CharSequence title,
            String displayUrl,
            CharSequence desc,
            CharSequence primaryButtonText,
            String targetUrl,
            boolean highlightCta
    ) {
        if (getParentActivity() == null) {
            return;
        }
        Context context = getParentActivity();

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(14), dp(20), dp(18));

        FrameLayout headerBox = new FrameLayout(context);
        headerBox.setClipChildren(false);
        headerBox.setClipToPadding(false);

        final StarParticlesView sheetParticles = MirrlyUiKit.createHeaderParticles(context, resourceProvider, highlightCta);
        headerBox.addView(sheetParticles, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 108, Gravity.CENTER));

        RLottieImageView iconView = new RLottieImageView(context);
        iconView.setAutoRepeat(true);
        iconView.setAnimation(lottieRes, 76, 76);
        iconView.playAnimation();
        ScaleStateListAnimator.apply(iconView);
        iconView.setOnClickListener(v -> {
            AndroidUtilities.vibrate(iconView);
            iconView.playAnimation();
            sheetParticles.flingParticles(105f);
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
        headerBox.addView(iconView, LayoutHelper.createFrame(76, 76, Gravity.CENTER));
        root.addView(headerBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 108, 0, 0, 0, 2));

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        root.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        final BottomSheet[] sheetHolder = new BottomSheet[1];

        TextView urlBadge = new TextView(context);
        urlBadge.setText(displayUrl);
        urlBadge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        urlBadge.setGravity(Gravity.CENTER);
        urlBadge.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourceProvider));
        urlBadge.setOnClickListener(v -> copyUrlWithBulletin(targetUrl, sheetHolder[0]));
        root.addView(urlBadge, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        TextView descView = new TextView(context);
        descView.setText(desc);
        descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        descView.setLineSpacing(dp(2), 1f);
        descView.setGravity(Gravity.CENTER);
        descView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourceProvider));
        root.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 18));

        ButtonWithCounterView proceedBtn = MirrlyUiKit.createFlickerButton(context, resourceProvider, highlightCta);
        proceedBtn.setText(primaryButtonText, false);
        proceedBtn.setOnClickListener(v -> {
            if (sheetHolder[0] != null) {
                sheetHolder[0].dismiss();
            }
            openExternalUrl(targetUrl);
        });
        root.addView(proceedBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 0, 0, 8));

        ButtonWithCounterView copyBtn = new ButtonWithCounterView(context, resourceProvider).setRound().setNeutral();
        copyBtn.setText(LocaleController.getString(R.string.MirrlyAboutCopyLink), false);
        copyBtn.setOnClickListener(v -> copyUrlWithBulletin(targetUrl, sheetHolder[0]));
        root.addView(copyBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 0, 0, 0));

        BottomSheet sheet = MirrlyUiKit.createFrostedBottomSheet(context, false, resourceProvider, root);
        sheetHolder[0] = sheet;
        showDialog(sheet);
    }

    /**
     * Native SettingCell-inspired donation card with gradient badge,
     * multi-line method description, short domain label, and divider support.
     */
    private static class DonationMethodCell extends LinearLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final SettingsActivity.SettingCell.Background iconBackground;
        private final ImageView iconView;
        private final TextView titleView;
        private final TextView domainView;
        private final TextView descView;
        private boolean needDivider;

        DonationMethodCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setOrientation(HORIZONTAL);
            setWillNotDraw(false);
            setPadding(dp(18), dp(12), dp(18), dp(12));

            FrameLayout iconLayout = new FrameLayout(context);
            iconLayout.setBackground(iconBackground = new SettingsActivity.SettingCell.Background());

            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iconLayout.addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.CENTER));

            LinearLayout textColumn = new LinearLayout(context);
            textColumn.setOrientation(VERTICAL);

            LinearLayout titleRow = new LinearLayout(context);
            titleRow.setOrientation(HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

            domainView = new TextView(context);
            domainView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            domainView.setSingleLine(true);
            domainView.setEllipsize(TextUtils.TruncateAt.END);

            if (LocaleController.isRTL) {
                titleRow.addView(domainView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
                titleRow.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));
            } else {
                titleRow.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));
                titleRow.addView(domainView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));
            }

            textColumn.addView(titleRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 3));

            descView = new TextView(context);
            descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
            descView.setLineSpacing(dp(2), 1f);
            descView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            textColumn.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            if (LocaleController.isRTL) {
                addView(textColumn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
                addView(iconLayout, LayoutHelper.createLinear(28, 28, Gravity.TOP | Gravity.RIGHT, 16, 2, 0, 0));
            } else {
                addView(iconLayout, LayoutHelper.createLinear(28, 28, Gravity.TOP | Gravity.LEFT, 0, 2, 16, 0));
                addView(textColumn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            }
            updateColors();
        }

        void set(int topColor, int bottomColor, int iconRes, CharSequence title, CharSequence desc, CharSequence domain, boolean divider) {
            iconBackground.setColor(topColor, bottomColor);
            iconView.setImageResource(iconRes);
            titleView.setText(title);
            descView.setText(desc);
            domainView.setText(domain);
            needDivider = divider;
            updateColors();
            invalidate();
        }

        @Override
        public void updateColors() {
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            descView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            domainView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider));
            iconBackground.setDrawBorder(resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark());
        }

        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_windowBackgroundWhiteBlackText);
            colors.add(Theme.key_windowBackgroundWhiteGrayText);
            colors.add(Theme.key_windowBackgroundWhiteBlueText4);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(Theme.key_paint_divider) : null;
                if (paint == null) {
                    paint = Theme.dividerPaint;
                }
                int left = LocaleController.isRTL ? 0 : dp(62);
                int right = getMeasuredWidth() - (LocaleController.isRTL ? dp(62) : 0);
                canvas.drawLine(left, getMeasuredHeight() - 1, right, getMeasuredHeight() - 1, paint);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    heightMeasureSpec
            );
        }

        public static class Factory extends UItem.UItemFactory<DonationMethodCell> {
            static {
                setup(new Factory());
            }

            @Override
            public DonationMethodCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new DonationMethodCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                int iconColorTop = (int) item.longValue;
                int iconColorBottom = (int) (item.longValue >>> 32);
                ((DonationMethodCell) view).set(
                        iconColorTop,
                        iconColorBottom,
                        item.iconResId,
                        item.text,
                        item.subtext,
                        item.textValue,
                        !item.hideDivider
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
                        && a.hideDivider == b.hideDivider
                        && TextUtils.equals(a.text, b.text)
                        && TextUtils.equals(a.subtext, b.subtext)
                        && TextUtils.equals(a.textValue, b.textValue);
            }

            public static UItem of(
                    int id,
                    int topColor,
                    int bottomColor,
                    @DrawableRes int iconRes,
                    CharSequence title,
                    CharSequence desc,
                    CharSequence domain,
                    boolean divider
            ) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.iconResId = iconRes;
                item.text = title;
                item.subtext = desc;
                item.textValue = domain;
                item.hideDivider = !divider;
                item.longValue = ((long) bottomColor << 32) | (topColor & 0xFFFFFFFFL);
                return item;
            }
        }
    }
}
